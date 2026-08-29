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

`idempify` is a modern Java/Spring Boot library that adds idempotency to write operations. 
A client supplies an `Idempotency-Key` (UUID) and the library guarantees the underlying business
operation runs at most once for that key: the first request executes it, the result is stored, and every retry
with the same key replays the stored result instead of re-running it. A retry carrying a *different* request
under the same key is rejected rather than silently answered from the store.

## Key Features

- **Atomic operation** - the first-writer-wins guarantee is implemented as a single atomic statement in the storage backend, not application-level locking.
- **Result caching & replay** - a successful operation's result is serialized and stored; retries with the same
  key replay it without re-executing business logic.
- **Conflict handling** - a request racing another under the same key is routed to a handler the config picks:
  `REJECT`, `WAIT`, or your own.
- **Fingerprint validation** - the request's fingerprint is compared against the one stored with the original request,
  so replaying a key with a different payload is treated as an error rather than silently served from cache.
- **Layered configuration** - a call site's annotation, the named config it points at, and the global
  `idempify.*` properties, in that order of precedence. What a layer leaves unspecified the next one answers.
- **Observability** - provides out-of-the-box metrics integration via Micrometer.
  tagged meter.

## Supported Infrastructure

- **Databases:** PostgreSQL.
- **Transport:** HTTP, WebFlux(**Coming soon**), gRPC(**Coming soon**).
- **Serialization:** Jackson.
- **Response cache:** in-memory (built into core), Redis.

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

4. Supply the one bean no module contributes:

```java
@Configuration
public class IdempotencyBeans {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

> [!TIP]
> `IdempotencyEventListener` is answered for you: core registers `IdempotencyEventListener.NOOP`, and
> `idempify-metrics` replaces it with the micrometer one once you ask for metrics. Declare a bean of your own
> only to send the outcomes somewhere else - see [Observability](#observability).

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

Read more about `@Idempotent`'s attributes in [Configuration → Annotation](#3-annotation---the-most-specific).

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

### Idempotency Guarantees

> [!WARNING]
> Everything below describes the `TRANSACTIONAL` processor - the only one implemented. See
> [Limitations](#limitations).

#### At-Most-Once Execution

Only one request per key ever runs the business method. The winner is decided by the store, not by the
library: `saveIfAbsent` is a single atomic statement, and the row it locks holds every other request for that
key until the first transaction commits. A duplicate therefore **waits** for as long as the original operation
runs, and is answered with its result - it does not get an immediate rejection, and it does not run the method
a second time.

#### Failures Are Not Replayed

The operation's record shares the business transaction, so a rollback takes both away: an operation that
threw leaves no row behind, and the next request with the same key is treated as a first attempt and runs
again. Only a completed operation is replayable. Keeping a failed result on record is what the lock-based
branch is for.

#### Isolation

The transactional branch is correct at `READ COMMITTED`, and only there. Under `REPEATABLE READ` or
`SERIALIZABLE` the duplicate that waited on `ON CONFLICT DO UPDATE` does not see the winner's committed row -
it fails with `could not serialize access due to concurrent update` (SQLSTATE 40001) instead of replaying the
result. Nothing in the library checks the level of the transaction it is given, so run the datasource behind
an idempotent endpoint at `READ COMMITTED` (PostgreSQL's default).

### Idempotency Keys

A key is a UUID, unique across the whole store: the column is the table's primary key, not a pair with the
endpoint. Reusing one key on another endpoint therefore replays the first operation - which is exactly what
[fingerprinting](#fingerprinting) catches, since the path takes part in the fingerprint.

A call site takes its key from one of two sources, and naming both is refused when the metadata is resolved:

| Source | How it is named | Read by |
|---|---|---|
| a request header (default) | `@Idempotent(headerName = "Idempotency-Key")`, or the global `header-name` | the transport's `KeyExtractor`, before the method runs |
| the call's own arguments | `@Idempotent(idempotencyKey = "#dto.id")`, a SpEL expression | the aspect, once the arguments are known |

> [!NOTE]
> With a SpEL key the response cache cannot work at all: the cache is read before the handler runs, and a key
> computed from the arguments does not exist yet at that moment. The filter recognises such a call site by its
> resolved metadata and steps aside, leaving idempotency to the aspect and the store.

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

### Expiry & Cleanup

`expires_at` is written when the operation starts - the moment it was claimed plus the ttl resolved for that
call site (24 hours by default). Past that moment the key is free again: the next request carrying it
overwrites the row and runs the business method as a first attempt. A cached response never outlives the
record it replays, because the writer hands the cache what is left of the operation's lifetime as the entry's
own ttl.

Expired rows are **not** deleted by the library - nothing in it schedules a sweep, and an expired row costs
nothing until its key comes back. Reclaiming the space is the application's job:

```sql
DELETE FROM idempotent_operations WHERE expires_at < now();
```

Run it outside a request, and only against expired rows: deleting a row an operation still owns is what
`OperationStatusMismatchException` reports when the operation tries to complete.

---

## Observability

`idempify-metrics` reports the library through micrometer. Add it next to the starter - it needs a
`MeterRegistry` in the context, which `spring-boot-starter-actuator` contributes:

```xml
<dependency>
    <groupId>io.github.dmitriy-iliyov.idempify</groupId>
    <artifactId>idempify-metrics</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Adding the dependency is not enough on its own - the module stays out of the context until the switch names
it, so a registry the application already has does not start collecting series nobody asked for:

```yaml
idempify:
  metrics:
    enabled: true
```

> [!NOTE]
> Metric names are given as they are registered - dot-separated, without a `count` suffix, because every
> registry appends the one its own convention asks for. In prometheus a counter above is scraped as
> `idempify_operations_total{outcome="duplicate"}`.

**Counters**

| Metric Name           | Description                                   | Tags                                                                      |
|:----------------------|:----------------------------------------------|:--------------------------------------------------------------------------|
| `idempify.operations` | Idempotent calls, by the outcome they reached | `outcome={duplicate, conflict, fingerprint-mismatch, exception, success}` |
| `idempify.cache.gets` | Response cache lookups, by what they found    | `result={hit, miss}`                                                      |

One meter per subject and the difference in a tag, the way micrometer's own binders report `cache.gets`: the
questions worth asking span the outcomes, so they stay single expressions instead of arithmetic over
separately named counters, and a new outcome does not break a dashboard.

Every series is registered at start-up, so a dashboard reads zero rather than a gap until an outcome first
happens. The tags take a fixed set of values and carry no request data, so they add no cardinality.

`idempify.operations` counts what the processors report through `IdempotencyEventListener`: this module
contributes the micrometer implementation of that hook, and outranks the `IdempotencyEventListener.NOOP` core
registers whenever this module stays out - absent from the classpath, or never switched on. A listener bean of
your own outranks both, and leaves this meter unregistered.

`idempify.cache.gets` counts lookups in the response cache, and only where a backend applies the registered
`ResponseCacheWrapper`s. Writes are left uncounted: one happens per completed operation, which `idempify.operations`
already reports. A lookup that throws counts as neither `hit` nor `miss` - the cache answered nothing, and
charging it to the misses would hide an unreachable store inside a plausible ratio.

## Configuration

A setting is decided by three layers, and each one overrides the one below it:

```text
1. idempify.* properties      the least specific - the answer every call site starts from
          ^ overridden by
2. named IdempotencyConfig    the profile a call site points at with @Idempotent(config = "...")
          ^ overridden by
3. @Idempotent attributes     the most specific - what this one call site says about itself
```

Overriding is per setting, not per layer: a layer decides the settings it names and leaves the rest to the one
below, so a call site that only shortens the ttl keeps every other answer the named config and the properties
gave it.

### 1. Global Properties

`idempify-starter` binds `idempify.*` into the bottom layer. Being the bottom, it answers every setting: a
feature the application leaves out arrives as a disabled section, never as a missing one.

```yaml
idempify:
  enabled: true
  header-name: Idempotency-Key
  ttl: 24h
  processor-type: LOCK_BASED
  conflict:
    enabled: true
    strategy: REJECT
    wait:
      delay: 5s
      multiplier: 1.5
      max-attempts: 5
      max-duration: 60s
  fingerprint:
    enabled: true
    strategy: CANONICALIZED_BODY_HASH
    empty-body-fallback: THROWING
    canonicalizer:
      format: JSON
      strategy: LEXICOGRAPHICAL
      included-fields: []
      excluded-fields: []
  cache:
    enabled: false
    cache-name: payments-service
    should-cache4xx: false
    should-cache5xx: false
    in-memory:
      capacity: 100
  metrics:
    enabled: false
```

| Property                                    | Description                                                                                | Default                   |
|---------------------------------------------|--------------------------------------------------------------------------------------------|---------------------------|
| `enabled`                                   | Switches the library on. `false` keeps every idempify bean out of the context              | `true`                    |
| `header-name`                               | Header the idempotency key is read from                                                    | `Idempotency-Key`         |
| `ttl`                                       | How long a completed operation's result stays replayable                                   | `24h`                     |
| `processor-type`                            | Which processor runs the operation: `LOCK_BASED` or `TRANSACTIONAL`                        | `LOCK_BASED`              |
| `conflict.enabled`                          | Whether a duplicate that meets an operation still in flight is handled                     | `true`                    |
| `conflict.strategy`                         | What such a duplicate gets: `REJECT` answers at once, `WAIT` polls for the result          | `REJECT`                  |
| `conflict.wait.delay`                       | First pause between polls, `WAIT` only                                                     | `5s`                      |
| `conflict.wait.multiplier`                  | Factor each following pause is multiplied by                                               | `1.5`                     |
| `conflict.wait.max-attempts`                | Polls before the wait is given up                                                          | `5`                       |
| `conflict.wait.max-duration`                | Total time the wait may take, whichever limit is reached first                             | `60s`                     |
| `fingerprint.enabled`                       | Whether a duplicate must match the original request before its result is replayed          | `true`                    |
| `fingerprint.strategy`                      | How the body is read: `RAW_BYTES_HASH`, `NORMALIZED_BYTES_HASH`, `CANONICALIZED_BODY_HASH` | `CANONICALIZED_BODY_HASH` |
| `fingerprint.empty-body-fallback`           | What an empty body means: `THROWING` refuses it, `NOOP` fingerprints without it            | `THROWING`                |
| `fingerprint.canonicalizer.format`          | Body format the canonicalizer parses                                                       | `JSON`                    |
| `fingerprint.canonicalizer.strategy`        | Order keys are put in before hashing                                                       | `LEXICOGRAPHICAL`         |
| `fingerprint.canonicalizer.included-fields` | Only these fields take part in the fingerprint; mutually exclusive with the excluded list  | empty                     |
| `fingerprint.canonicalizer.excluded-fields` | These fields are left out of the fingerprint; mutually exclusive with the included list    | empty                     |
| `cache.enabled`                             | Whether responses are cached in front of the store                                         | `false`                   |
| `cache.cache-name`                          | Name the entries are kept under; required once the cache is enabled                        | none                      |
| `cache.should-cache4xx`                     | Whether a `4xx` answer is worth an entry                                                   | `false`                   |
| `cache.should-cache5xx`                     | Whether a `5xx` answer is worth an entry                                                   | `false`                   |
| `cache.in-memory.capacity`                  | Entries the built-in in-memory store holds before evicting the oldest                      | `100`                     |
| `metrics.enabled`                           | Whether `idempify-metrics` registers its meters; ignored when the module is absent         | `false`                   |

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

### 2. Named Configs

The middle layer: build an `IdempotencyConfig`, register it under a name in the `IdempotencyConfigRegistry`
bean, and point a call site at it with `@Idempotent(config = "stripe")`. Registration is code-only - named
profiles cannot be declared in YAML yet.

```java
@Bean
IdempotencyConfig idempotencyConfigRegistry(IdempotencyConfigRegistry registry) {
    registry.register("stripe", IdempotencyConfig.builder()
            .ttl(Duration.ofHours(48))
            .fingerprint(FingerprintConfig.defaults())
            .build());
    return registry;
}
```

### 3. Annotation

| Attribute        | Description                                                                   | Unspecified value |
|------------------|-------------------------------------------------------------------------------|-------------------|
| `config`         | Name of a registered `IdempotencyConfig` to layer under this call site        | `""`              |
| `idempotencyKey` | SpEL expression the key is computed from; the header is read when it is blank | `""`              |
| `headerName`     | Header this call site reads the key from                                      | `""`              |
| `ttl`            | How long the result stays replayable, counted in `timeUnit`                   | `-1`              |
| `timeUnit`       | Unit `ttl` is written in; read only when `ttl` is non-negative                | `HOURS`           |
| `processorType`  | Which processor runs the operation: `TRANSACTIONAL` or `LOCK_BASED`           | `UNSELECTED`      |
| `onConflict`     | What a duplicate meeting an operation in flight gets: `REJECT` or `WAIT`      | `UNSELECTED`      |
| `useFingerprint` | Whether a duplicate must match the original request: `ENABLE` or `DISABLE`    | `UNSELECTED`      |
| `useCache`       | Whether the response is cached in front of the store: `ENABLE` or `DISABLE`   | `UNSELECTED`      |
| `cache4xx`       | Whether a `4xx` answer is worth an entry: `ENABLE` or `DISABLE`               | `UNSELECTED`      |
| `cache5xx`       | Whether a `5xx` answer is worth an entry: `ENABLE` or `DISABLE`               | `UNSELECTED`      |

Every attribute is optional and each has a value meaning "not decided here" - an empty string, a negative
`ttl`, `UNSELECTED`. That value is not a setting of its own: it hands the decision to the named config, and
failing that to the global properties. A hand-written `ConflictHandler` or `FingerprintPolicy` is not named
here at all: it is an instance on the config, not a class on the annotation.

### Examples

#### Transactional, Header Key

What a working setup looks like today: the store decides the races, the key travels in a header, and both
sections the transactional processor refuses are switched off.

```yaml
idempify:
  processor-type: TRANSACTIONAL
  header-name: Idempotency-Key
  ttl: 24h
  conflict:
    enabled: false
  cache:
    enabled: false
  fingerprint:
    enabled: true
    strategy: CANONICALIZED_BODY_HASH
```

```java
    @PostMapping("/payments")
    @Idempotent 
    public PaymentResponse pay(@RequestBody PaymentRequest request) { ... }
```

#### Fingerprint Tuned Per Endpoint

Fields that differ between two deliveries of the same request - a trace id, a client timestamp - must stay out
of the fingerprint, or a legitimate retry stops matching. The global block names the general rule, and one
endpoint narrows it through a named config:

```yaml
idempify:
  processor-type: TRANSACTIONAL
  conflict:
    enabled: false
  fingerprint:
    enabled: true
    strategy: CANONICALIZED_BODY_HASH
    canonicalizer:
      excluded-fields:
        - meta.traceId
```

```java
    registry.register("payments", IdempotencyConfig.builder()
            .fingerprint(FingerprintConfig.builder()
                    .bodyCanonicalizerConfig(canonicalizer -> canonicalizer.includedFields("amount", "currency"))
                    .build())
            .build());
```

```java
    @PostMapping("/payments")
    @Idempotent(config = "payments")
    public PaymentResponse pay(@RequestBody PaymentRequest request) { ... }
```

---

#### Key From the Request Body

An endpoint whose clients cannot send a header takes the key from the call's own arguments. The response cache
does not apply here - see [Idempotency Keys](#idempotency-keys).

```java
    @PostMapping("/payments")
    @Idempotent(idempotencyKey = "#request.paymentId")
    public PaymentResponse pay(@RequestBody PaymentRequest request) { ... }
```

## Roadmap
- A working `LOCK_BASED` processor, and with it conflict handling and response caching end to end
- Named configuration profiles declared in YAML
- Automatic table creation
- Metrics/observability integration
- A working `idempify-example` demo application
- Maven Central publishing
- gRPC transport support (`RequestType.GRPC` is reserved but unimplemented) and WebFlux support
- MySQL and Oracle `OperationRepository` implementations
