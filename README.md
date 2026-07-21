[![CodeFactor](https://www.codefactor.io/repository/github/dmitriy-iliyov/idempify/badge)](https://www.codefactor.io/repository/github/dmitriy-iliyov/idempify)
[![codecov](https://codecov.io/github/dmitriy-iliyov/idempify/branch/main/graph/badge.svg)](https://codecov.io/github/dmitriy-iliyov/idempify)
[![CI](https://github.com/dmitriy-iliyov/idempify/actions/workflows/ci.yml/badge.svg)](https://github.com/dmitriy-iliyov/idempify/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![GitHub Release](https://img.shields.io/github/v/release/dmitriy-iliyov/idempify?include_prereleases)
![GitHub last commit](https://img.shields.io/github/last-commit/dmitriy-iliyov/idempify)

> [!WARNING]
> `idempify` is pre-release (`0.0.1-SNAPSHOT`), not yet published to Maven Central, and several
> pieces described below are explicitly marked **Coming soon**. Treat this README as a snapshot of
> an actively evolving library, not a finished product.

## Overview

`idempify` is a multi-module Java/Spring Boot library that adds idempotency to write operations. 
A client supplies an `Idempotency-Key` (UUID) and the library guarantees the underlying business
operation runs at most once for that key: the first request executes it, the result is cached, and every retry
with the same key replays the cached result instead of re-running it. Concurrent duplicate requests for the same
key are detected and routed through a configurable conflict-handling strategy instead of racing each other.

The core is transport-agnostic; HTTP is the only transport wired up today via `idempify-http` and the `@Idempotent` AOP annotation from `idempify-aop`.

## Key Features

- **Atomic operation start** - the first-writer-wins guarantee is implemented as a single atomic in the storage backend, not application-level locking.
- **Result caching & replay** - a successful operation's result is serialized and stored; retries with the same
  key replay it without re-executing business logic.
- **Concurrent conflict detection** - a second request that arrives while the first is still `IN_PROCESS` is
  detected and handled explicitly instead of silently duplicating work.
- **Fingerprint validation** - the request's fingerprint is compared against the one stored with the original request, 
  so replaying a key with a different payload is treated as an error rather than silently served from cache.
- **Declarative usage via `@Idempotent`** - annotate a method, extract the key via SpEL or the `Idempotency-Key`
  header, and the AOP aspect handles the rest.
- **Pluggable architecture** - each integration module ships a Spring Boot `@AutoConfiguration` that backs
  off via `@ConditionalOnMissingBean` so you can swap in your own implementation.

## Supported Infrastructure

- **Databases:** PostgreSQL.
- **Transport:** HTTP, WebFlux(**Coming soon**), gRPC(**Coming soon**) 
- **Serialization:** Jackson.

## Quick Start

1. Add dependencies.

> [!IMPORTANT]
> `idempify-starter` aggregates `idempify-core` + `idempify-aop` + `idempify-jackson` only. It does **not**
> include a transport module or a storage backend - you must add `idempify-http` (or your own transport
> integration) and `idempify-postgresql` (or your own `OperationRepository`) explicitly.

```xml
<dependency>
    <groupId>io.github.dmitriy-iliyov.idempify</groupId>
    <artifactId>idempify-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.github.dmitriy-iliyov.idempify</groupId>
    <artifactId>idempify-http</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.github.dmitriy-iliyov.idempify</groupId>
    <artifactId>idempify-postgresql</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

2. Create the operations table (run once against your database - see [Storage](#storage) for the full DDL):

```bash
psql -f idempify-postgresql/src/main/resources/idempotent_operations_table.sql "$DATABASE_URL"
```

3. Supply the core beans that `idempify-core` doesn't auto-configure (see [Limitations](#limitations)):

```java
@Configuration
public class IdempotencyConfig {

    @Bean
    ConflictHandler rejectConflictHandler() {
        return new RejectConflictHandler();
    }

    @Bean
    ConflictHandler waitConflictHandler(OperationRepository repository, ResultDeserializer deserializer) {
        return new WaitConflictHandler(repository, deserializer, /* maxAttempt */ 10, /* delay ms */ 100L, /* multiplier */ 2);
    }

    @Bean
    FingerprintManager fingerprintManager(List<FingerprintPolicy> policies) {
        return new DefaultFingerprintManager(policies);
    }

    @Bean
    OperationManager operationManager(OperationMapper mapper, 
                                      OperationRepository repository,
                                      IdempotencyEventListener listener, 
                                      DelegatingConflictHandler conflictHandler,
                                      FingerprintManager fingerprintManager, 
                                      ResultSerializer serializer,
                                      ResultDeserializer deserializer, 
                                      Clock clock) {
        return new DefaultOperationManager(mapper, repository, listener, conflictHandler,
                fingerprintManager, serializer, deserializer, clock);
    }

    @Bean
    IdempotentProcessor idempotentProcessor(TransactionTemplate tx, OperationManager manager,
                                             IdempotencyEventListener listener) {
        return new DefaultIdempotentProcessor(tx, manager, listener);
    }
}
```

4. Annotate a write endpoint with `@Idempotent`:

```java
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payments")
    @Idempotent(onConflict = ConflictHandleStrategy.REJECT, ttl = 24, timeUnit = TimeUnit.HOURS)
    public PaymentResponse pay(@RequestBody PaymentRequest request) {
        return paymentService.charge(request);
    }
}
```

5. Client calls the endpoint with an `Idempotency-Key` header:

```bash
curl -X POST https://api.example.com/payments \
  -H "Idempotency-Key: 6f1c9f0a-2b3e-4a4b-9f0a-1234567890ab" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100, "currency": "USD"}'
```

A retry with the same header and body replays the original response instead of charging again; a retry with the
same header and a **different** body throws `FingerprintMismatchException` by default.

Read more about `@Idempotent`'s attributes [here](#idempotent-annotation).

---

## Example & Test Environment

> [!WARNING]
> **Coming soon.** `idempify-example` exists in the repository but is currently a non-compiling stub - its
> controller references `CustomConflictHandler` and `CustomStripeFingerprintHandler` classes that were never
> implemented, and it isn't wired into the root Maven reactor. Don't use it as a working reference yet.

## Design

### Architecture Overview

> [!WARNING]
> **Coming soon.**

### Processing Flow

The whole request lifecycle runs inside one `TransactionTemplate.execute(...)` call
(`DefaultIdempotentProcessor.process`):

1. `OperationManager.startOrReply(metadata, resultType)`:
   - Attempts `OperationRepository.saveIfAbsent(operation)` - an atomic
     `INSERT ... ON CONFLICT(idempotency_key) DO UPDATE SET is_first_attempt = false RETURNING *`. This is the
     mechanism that guarantees only one concurrent request "wins" for a given key.
   - If the row already existed, is still `IN_PROCESS`, and this call was **not** the first attempt
     (`is_first_attempt=false`), the request is treated as a conflict.
   - If the row is `PROCESSED` but past its TTL, it's overwritten as if new.
2. Based on the outcome:
   - **Conflict** -> delegated to a `ConflictHandler` chosen by `OperationMetadata.getConflictHandleStrategy()`:
     `REJECT` throws `IdempotencyConflictException`; `WAIT` polls `OperationRepository` with exponential backoff
     until `PROCESSED` or throws `WaitTimeoutException`; `CUSTOM` delegates to your own handler, matched by
     `conflictHandlerClass`.
   - **Already processed** -> if `useFingerprint()`, the stored fingerprint is compared against the current
     request's; a mismatch invokes `FingerprintPolicy.handle(...)` (default: throws `FingerprintMismatchException`
     - same key + different request is an error, not a silent replay). Otherwise, the cached result is
     deserialized and returned.
   - **Neither** -> `Optional.empty()`, meaning the caller's business logic actually runs.
3. If the business logic ran, `OperationManager.complete()` serializes the result and does a conditional
   `UPDATE ... WHERE idempotency_key = ? AND state = 'IN_PROCESS'` to flip the row to `PROCESSED`.

### Operation State

An operation's persisted `state` is one of:

```text
IN_PROCESS --complete()--> PROCESSED
```

`CONFLICT` is a third `OperationState` value, but it's a transient, in-memory marker produced by
`Operation.hasConflict()` when a second writer loses the `saveIfAbsent` race while the row is still `IN_PROCESS`
- it drives which `ConflictHandler` runs and is never itself persisted back to the row.

### Conflict Handling

| Strategy           | Behavior                                                                                                                                                                                                                                                                                                                   |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `REJECT` (default) | Throws `IdempotencyConflictException` immediately.                                                                                                                                                                                                                                                                         |
| `WAIT`             | Polls `OperationRepository.findByIdempotencyKey` with exponential backoff (`delay * multiplier ^ attempt`) until the row is `PROCESSED`, then replays it; throws `WaitTimeoutException` after `maxAttempt` tries. Requires you to construct and register `WaitConflictHandler` yourself - see [Limitations](#limitations). |
| `CUSTOM`           | Delegates to your own `ConflictHandler` bean, selected by `OperationMetadata#getConflictHandlerClass()` / `@Idempotent#conflictHandler()`. Multiple `CUSTOM` handlers can coexist.                                                                                                                                         |

### Fingerprinting

The default `FingerprintPolicy` hashes `path:method:body` from `RequestContext` with SHA-256. A mismatch on replay
throws `FingerprintMismatchException` by default, carrying the idempotency key plus the previous and current
fingerprints. Implement `FingerprintPolicy` and reference it via `fingerprintPolicyClass` /
`@Idempotent#fingerprintPolicy()` to customize both generation and mismatch handling.

### Serialization

Result caching is pluggable via `ResultSerializer` / `ResultDeserializer`. `idempify-jackson` provides a
Jackson-backed implementation, auto-configured from the application's `ObjectMapper` bean.

```java
public interface ResultSerializer {
    <T> String serialize(T result);
}

public interface ResultDeserializer {
    <T> T deserialize(String rawResult, Class<T> c);
}
```

### Storage

Any `OperationRepository` implementation must provide the same guarantees the Postgres one does: `saveIfAbsent`
must be atomic on `idempotency_key` (first writer wins), and `update` / `saveResultAndUpdateState` must be
conditional on the row's current `state` (compare-and-swap style), since concurrent requests for the same key race
through this code path inside a transaction.

```sql
CREATE TABLE IF NOT EXISTS idempotent_operations(
    idempotency_key UUID PRIMARY KEY,
    state VARCHAR(20) NOT NULL,
    is_first_attempt BOOLEAN NOT NULL,
    result TEXT,
    fingerprint VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```
---

## Module Layout

```
idempify-core        <- framework/transport-agnostic domain logic (no Spring MVC/web deps, no auto-configuration)
idempify-http         -> depends on core + aop; Spring MVC/Servlet integration (Idempotency-Key header extraction)
idempify-aop          -> depends on core; @Idempotent annotation + AspectJ aspect
idempify-jackson      -> depends on core; JSON (de)serialization of cached results
idempify-postgresql   -> depends on core; Postgres-backed OperationRepository
idempify-starter      -> depends on core + aop + jackson (pure dependency aggregator, no source)
idempify-example      -> depends on aop; demo Spring Boot app - Coming soon (currently non-compiling)
```

## `@Idempotent` Annotation

```yaml
key:              ""                            # SpEL expression for the idempotency key; falls back to the
                                                # Idempotency-Key header when blank/unresolvable
headerName:       "Idempotency-Key"
ttl:              24
timeUnit:         HOURS
onConflict:       REJECT                         # REJECT | WAIT | CUSTOM
conflictHandler:  RejectConflictHandler.class
useFingerprint:   true
fingerprintPolicy: DefaultFingerprintPolicy.class
```

## Configuration

> [!WARNING]
> **Coming soon.** There is no `idempify.*` YAML/properties configuration today - all configuration is either
> `@Idempotent` annotation attributes (above) or a programmatically-built `OperationMetadata`. Named/reusable
> configuration profiles (e.g. `@Idempotent(config = "stripe")` backed by a registered `IdempotencyConfig` bean)
> are on the roadmap but not implemented.

## Observability

**Coming soon.** No metrics integration exists yet.

## Roadmap
- Servlet filter caching
- YAML/properties-based configuration, including named configuration profiles
- Automatic table creation
- Default backoff configuration for `WaitConflictHandler`
- Metrics/observability integration
- `idempify-starter`
- A working `idempify-example` demo application
- Maven Central publishing
- gRPC transport support (`RequestType.GRPC` is reserved but unimplemented) and WebFlux support
- MySQL and Oracle `OperationRepository` implementations

---

## Build & Test

```bash
./mvnw compile                          # build all modules
./mvnw test                             # unit tests, all modules
./mvnw verify                           # tests + jacoco coverage report
./mvnw -pl idempify-core test           # test a single module
./mvnw -pl idempify-postgresql test     # requires Docker (Testcontainers spins up real Postgres)
```
