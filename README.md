[![CodeFactor](https://www.codefactor.io/repository/github/dmitriy-iliyov/idempify/badge)](https://www.codefactor.io/repository/github/dmitriy-iliyov/idempify)
[![codecov](https://codecov.io/github/dmitriy-iliyov/idempify/branch/main/graph/badge.svg)](https://codecov.io/github/dmitriy-iliyov/idempify)
[![CI](https://github.com/dmitriy-iliyov/idempify/actions/workflows/ci.yml/badge.svg)](https://github.com/dmitriy-iliyov/idempify/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![GitHub Release](https://img.shields.io/github/v/release/dmitriy-iliyov/idempify?include_prereleases)
![GitHub last commit](https://img.shields.io/github/last-commit/dmitriy-iliyov/idempify)

> [!WARNING]
> `idempify` is pre-release (`0.0.1-SNAPSHOT`), not yet published to Maven Central, and several
> pieces described below are explicitly marked **Coming soon**. Treat this README as a snapshot of
> an actively evolving library, not a finished product. Start with [Limitations](#limitations) - one of
> the two processors is still a stub, and that decides what works today.

## Overview

`idempify` is a multi-module Java/Spring Boot library that adds idempotency to write operations. 
A client supplies an `Idempotency-Key` (UUID) and the library guarantees the underlying business
operation runs at most once for that key: the first request executes it, the result is stored, and every retry
with the same key replays the stored result instead of re-running it. A retry carrying a *different* request
under the same key is rejected rather than silently answered from the store.

The core is transport-agnostic and owns everything that decides behaviour - the `@Idempotent` annotation,
metadata resolution, the processors and the store contracts. `idempify-aop` is the entry point that intercepts
annotated methods; `idempify-http` is the only transport wired up today.

## Key Features

- **Atomic operation start** - the first-writer-wins guarantee is implemented as a single atomic statement in the storage backend, not application-level locking.
- **Result caching & replay** - a successful operation's result is serialized and stored; retries with the same
  key replay it without re-executing business logic.
- **Conflict handling** - .
- **Fingerprint validation** - the request's fingerprint is compared against the one stored with the original request,
  so replaying a key with a different payload is treated as an error rather than silently served from cache.
- **Layerd configuration** - a call site's annotation, the named config it points at, and the global
  `idempify.*` properties, in that order of precedence. What a layer leaves unspecified the next one answers.
- **Observability** - coming soon

## Supported Infrastructure

- **Databases:** PostgreSQL.
- **Transport:** HTTP, WebFlux(**Coming soon**), gRPC(**Coming soon**).
- **Serialization:** Jackson.
- **Response cache:** in-memory (built into core), Redis.

## Quick Start

1. Add dependencies.

> [!IMPORTANT]
> `idempify-starter` aggregates `idempify-core` + `idempify-aop` + `spring-boot-starter-aop` and binds the
> `idempify.*` properties. It does **not** include serialization, a transport module, a storage backend or a
> cache backend - you must add `idempify-jackson` (or your own `ResultSerializer`/`ResultDeserializer`),
> `idempify-http` (or your own transport integration), `idempify-postgresql` (or your own
> `TransactionalOperationRepository`) and, if you want response caching, `idempify-cache-redis` explicitly.

```xml
<dependency>
    <groupId>io.github.dmitriy-iliyov.idempify</groupId>
    <artifactId>idempify-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.github.dmitriy-iliyov.idempify</groupId>
    <artifactId>idempify-jackson</artifactId>
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

3. Select the processor. The global default is `LOCK_BASED`, whose processor is still a stub, so a working
   setup has to ask for `TRANSACTIONAL` - and that processor refuses conflict handling and response caching
   (see [Limitations](#limitations)):

```yaml
idempify:
  processor-type: TRANSACTIONAL
  conflict:
    enabled: false               # defaults to true; TRANSACTIONAL rejects it at start-up
```

4. Supply the two beans no module contributes:

```java
@Configuration
public class IdempotencyBeans {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    IdempotencyEventListener idempotencyEventListener() {
        return IdempotencyEventListener.NOOP;   // or your own metrics/audit implementation
    }
}
```

Everything else - metadata resolution and its cache, the fingerprint and conflict providers, the operation
mapper, the transactional manager and the processors - is declared by `IdempifyCoreAutoConfiguration`. The
`TransactionTemplate` it needs is contributed by Spring Boot itself once a transaction manager is present.

5. Annotate a write endpoint with `@Idempotent`:

```java
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments")
    @Idempotent(ttl = 24, timeUnit = TimeUnit.HOURS)
    public PaymentResponse pay(@RequestBody PaymentRequest request) {
        return paymentService.charge(request);
    }
}
```

6. Client calls the endpoint with an `Idempotency-Key` header:

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
> **Coming soon.** `idempify-example` is not part of the Maven reactor, depends on `idempify-aop` alone and
> points at a named config (`@Idempotent(config = "specificConfig")`) that nothing registers. It is a
> placeholder, not a working reference.

## Design

### Architecture Overview

> [!WARNING]
> **Coming soon.**

### Processing Flow

1. `IdempotentAdvisor` (the `@Aspect` in `idempify-aop`) intercepts a public method annotated with
   `@Idempotent`. A non-empty `idempotencyKey` attribute is evaluated as SpEL; otherwise the key is left to the
   transport.
2. `OperationMetadataResolver` returns the resolved `OperationMetadata` for the method - one instance per
   method, shared with any transport module that asks, so the aspect and the HTTP layer cannot disagree about
   settings such as `headerName`.
3. `DefaultIdempotentInterceptor` fills in what is missing: the key via `KeyExtractor` (dispatched by
   `RequestType`, reading the header as a UUID) and, when the call site fingerprints, the fingerprint via
   `FingerprintPolicy.generate(...)`.
4. `DelegatingIdempotentProcessor` routes the call by `metadata.getProcessorType()` -
   `TRANSACTIONAL` to `TransactionalIdempotentProcessor`, `LOCK_BASED` to a stub.
5. `TransactionalIdempotentProcessor` runs everything inside one `TransactionTemplate.execute(...)`:
   - `TransactionalOperationManager.startOrReply(context, metadata)` calls
     `TransactionalOperationRepository.saveIfAbsent(operation)` - an atomic
     `INSERT ... ON CONFLICT(idempotency_key) DO UPDATE SET is_first_attempt = false RETURNING *`. This is the
     mechanism that guarantees only one concurrent request "wins" for a given key. A row that is `PROCESSED`
     but past its TTL is overwritten as if new.
   - If the row is `PROCESSED` and the call site fingerprints, `FingerprintMatcher` compares the stored
     fingerprint against the current request's; a mismatch invokes `FingerprintPolicy.handle(...)` (default:
     throws `FingerprintMismatchException` - same key + different request is an error, not a silent replay).
     Otherwise the stored result is deserialized and returned, and the operation's state is published to the
     `OperationStateChannel` marked as a replay.
   - Otherwise `startOrReply` answers `Optional.empty()`, meaning the caller's business logic actually runs.
   - `complete(key, result)` then serializes the result and performs a conditional
     `UPDATE ... WHERE idempotency_key = ? AND status = 'IN_PROCESS'`. A condition that matches no row is not
     a silent no-op but an `OperationStatusMismatchException`.

### Operation Status

An operation's persisted `status` is one of:

```text
IN_PROCESS --complete()--> PROCESSED
```

`CONFLICT` is a third `OperationStatus` value, but it is a transient, in-memory marker produced by
`Operation.hasConflict()` when a second writer loses the `saveIfAbsent` race while the row is still
`IN_PROCESS` - it decides which `ConflictHandler` runs and is never persisted back to the row.

### Conflict Handling

> [!NOTE]
> Conflict handling belongs to the lock-based branch and is unreachable today: the transactional processor
> settles a duplicate on the store's own insert - the loser blocks, then replays - so `IdempotencyConfig`
> rejects an enabled `conflictConfig` under `TRANSACTIONAL`. The handlers below are implemented and unit-tested,
> waiting for the processor that uses them.

| Strategy           | Behavior                                                                                                                                                                                                                                                                                                                   |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `REJECT` (default) | Throws `IdempotencyConflictException` immediately.                                                                                                                                                                                                                                                                         |
| `WAIT`             | Polls `OperationRepository.findByIdempotencyKey` with exponential backoff (`delay * multiplier ^ attempt`) until the row is `PROCESSED`, then replays it. Tuned by `WaitConflictHandlerConfig` (`delay`, `multiplier`, `maxAttempts`, `maxDuration`); gives up with `WaitTimeoutException` when the duration runs out and `WaitAttemptsExhaustedException` when the attempts do.                                    |
| your own handler   | There is no `CUSTOM` constant: a hand-written `ConflictHandler` reaches the call site as an instance - `ConflictConfig.custom(handler)` - and being supplied is what makes it custom. It outranks the strategy, so no discriminator can disagree with it.                                                                    |

### Fingerprinting

A fingerprint is SHA-256 over `path:method:body`. How the body reaches that hash is a ladder of policies picked
by `BodyHandleStrategy`, in growing order of tolerance and cost:

| Strategy                          | Body is hashed as                                                                                     |
|-----------------------------------|-------------------------------------------------------------------------------------------------------|
| `RAW_BYTES_HASH`                  | the bytes as they arrived - any reordering or reformatting reads as a different request.               |
| `NORMALIZED_BYTES_HASH`           | the bytes with insignificant whitespace removed.                                                       |
| `CANONICALIZED_BODY_HASH` (default) | the body reduced by a `BodyCanonicalizer` - JSON keys sorted, selected fields included or excluded. The JSON implementation comes from `idempify-jackson`. |

An empty body is not a special case inside a policy but a separate `EmptyBodyFallback`: by default it throws,
so a fingerprinted call site cannot quietly fingerprint nothing at all.

Replacing the whole thing is a config-level choice rather than an annotation attribute: pass a
`FingerprintPolicy` instance through `FingerprintConfig.fingerprintPolicy(...)` and it takes over generation,
comparison and mismatch handling. The levels are alternatives, not layers - the builder rejects a mix of two.

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

The type descriptor is a plain `Class<T>` taken from the method signature, so generic return types do not
survive erasure - see [Limitations](#limitations).

### Storage

The store contract is split by concurrency model rather than by table:

- `OperationRepository` - the read side (`findByIdempotencyKey`), shared by everything;
- `TransactionalOperationRepository` - the write side that relies on the store's own atomicity: `saveIfAbsent`
  must let exactly one concurrent request claim a key, and `update` / `saveResultAndUpdateStatus` must be
  conditional on the row's current `status`, since concurrent requests for the same key race through this code
  path inside a transaction;
- `LockBasedOperationRepository` - the write side for a store that gets mutual exclusion from an external lock.
  Declared, not designed.

```sql
CREATE TABLE IF NOT EXISTS idempotent_operations(
    idempotency_key UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
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
idempify-core        <- framework/transport-agnostic domain logic: @Idempotent, metadata resolution,
                        processors, store contracts, and its own auto-configuration
idempify-aop          -> depends on core; the aspect that intercepts @Idempotent methods
idempify-http         -> depends on core; Spring MVC/Servlet integration - endpoint registry,
                         Idempotency-Key extraction, response-caching filter, problem responses
idempify-jackson      -> depends on core; JSON (de)serialization of results and body canonicalization
idempify-postgresql   -> depends on core; Postgres-backed TransactionalOperationRepository
idempify-cache-redis  -> depends on core; Redis-backed ResponseCache
idempify-starter      -> depends on core + aop + spring-boot-starter-aop; binds the idempify.* properties
idempify-example      -> depends on aop; demo Spring Boot app - Coming soon (outside the reactor)
```

Spring dependencies are `provided` in every library module: what reaches your application are its own
`spring-boot-starter-*` artifacts.

## `@Idempotent` Annotation

```yaml
config:           ""                            # name of a registered IdempotencyConfig to layer under this
                                                # call site
idempotencyKey:   ""                            # SpEL expression for the idempotency key; falls back to the
                                                # header when blank
headerName:       ""                            # unspecified; the config or the properties decide
ttl:              -1                            # unspecified; a non-negative value counts in timeUnit
timeUnit:         HOURS
processorType:    UNSELECTED                    # UNSELECTED | TRANSACTIONAL | LOCK_BASED
onConflict:       UNSELECTED                    # UNSELECTED | REJECT | WAIT
useFingerprint:   UNSELECTED                    # UNSELECTED | ENABLE | DISABLE
useCache:         UNSELECTED                    # UNSELECTED | ENABLE | DISABLE
cache4xx:         UNSELECTED                    # UNSELECTED | ENABLE | DISABLE
cache5xx:         UNSELECTED                    # UNSELECTED | ENABLE | DISABLE
```

Every attribute is optional and each has a value meaning "not decided here" - an empty string, a negative
`ttl`, `UNSELECTED`. That value is not a setting of its own: it hands the decision to the named config, and
failing that to the global properties. A hand-written `ConflictHandler` or `FingerprintPolicy` is not named
here at all: it is an instance on the config, not a class on the annotation.

## Configuration

`idempify-starter` binds `idempify.*` into the global configuration layer - the least specific one, under both
named configs and `@Idempotent` attributes. Being the bottom layer, it answers every setting: a feature the
application leaves out arrives as a disabled section, never as a missing one.

```yaml
idempify:
  enabled: true                        # false keeps every idempify bean out of the context
  header-name: Idempotency-Key
  ttl: 24h
  processor-type: LOCK_BASED           # LOCK_BASED | TRANSACTIONAL
  conflict:
    enabled: true
    strategy: REJECT                   # REJECT | WAIT
    wait:
      delay: 5s
      multiplier: 1.5
      max-attempts: 5
      max-duration: 60s
  fingerprint:
    enabled: true
    strategy: CANONICALIZED_BODY_HASH  # RAW_BYTES_HASH | NORMALIZED_BYTES_HASH | CANONICALIZED_BODY_HASH
    empty-body-fallback: THROWING      # THROWING | NOOP
    canonicalizer:
      format: JSON
      strategy: LEXICOGRAPHICAL
      included-fields: []              # mutually exclusive with excluded-fields
      excluded-fields: []
  cache:
    enabled: false
    cache-name: payments-service       # required once enabled
    should-cache4xx: false
    should-cache5xx: false
    in-memory:
      capacity: 100
```

> [!IMPORTANT]
> Response caching is **off until you ask for it**, and asking means naming it - `cacheName` is what keeps the
> entries of one application apart from another's in a store they share, so there is no default worth
> inventing. Turning the cache on without a name fails at start-up with a message naming both ways out.

> [!WARNING]
> Two combinations are refused at start-up rather than at the first request: with
> `processor-type: TRANSACTIONAL`, neither `conflict.enabled` nor `cache.enabled` may be true. The
> transactional processor settles duplicates on the store's insert, so no conflict is left to handle, and it
> writes the response in the same transaction as the operation, so a cache in front of it would serve answers
> a rollback has already taken back.

> [!WARNING]
> Switching the cache off here is **final**: no named config and no `@Idempotent(useCache = ENABLE)` can turn
> it back on for a single endpoint. The same property keeps the cache backend and the caching filter out of the
> context, so there would be nothing to cache into.

Named configuration profiles are the middle layer: build an `IdempotencyConfig` and register it under a name in
the `IdempotencyConfigRegistry` bean, then point a call site at it with `@Idempotent(config = "stripe")`.
Registration is code-only - named profiles cannot be declared in YAML yet.

```java
@Bean
IdempotencyConfigRegistry idempotencyConfigRegistry() {
    IdempotencyConfigRegistry registry = new DefaultIdempotencyConfigRegistry();
    registry.register("stripe", IdempotencyConfig.builder()
            .ttl(Duration.ofHours(48))
            .fingerprint(FingerprintConfig.defaults())
            .build());
    return registry;
}
```

## Limitations

- **`LOCK_BASED` is a stub, and it is the default.** `LockBasedIdempotentProcessor` returns `null`, and the
  core auto-configuration registers only the transactional one, so a call site left at the default fails with
  "no processor found". Set `processor-type: TRANSACTIONAL` explicitly.
- **Response caching has nowhere to run yet.** The cache backends, the HTTP caching filter and the
  `ResponseCacheConfig` layer are implemented, but caching is rejected under `TRANSACTIONAL` and the
  lock-based processor that would allow it does not exist. Consider the whole cache path preview-only.
- **Two sources of truth for a replay.** The repository stores the serialized *method result*; the response
  cache stores the *HTTP response* (status, content type, bytes). They are filled at different levels, expire
  independently and are never reconciled, so a replay through the repository loses the status code and
  `Location` while a replay from the cache keeps them.
- **`Class<T>` as the result type descriptor.** Generic return types - `List<Order>`, `Optional<X>`,
  `ResponseEntity<Order>` - do not survive erasure, and `void` has no sensible descriptor at all. Only
  concrete, non-generic return types are supported today.
- **`IdempotencyEventListener` takes no arguments**, so an implementation can count outcomes but cannot see
  the key, the timing or the operation behind one.
- **No automatic table creation** - the DDL is applied by hand.
- **Not published to Maven Central**, so the artifacts have to be built and installed locally
  (`./mvnw install`).

## Observability

**Coming soon.** `IdempotencyEventListener` is the hook - one method per outcome - but no metrics integration
ships with the library.

## Roadmap
- A working `LOCK_BASED` processor, and with it conflict handling and response caching end to end
- Named configuration profiles declared in YAML
- Automatic table creation
- Metrics/observability integration
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
./mvnw -pl idempify-cache-redis test    # requires Docker (Testcontainers spins up real Redis)
```
