[![CodeFactor](https://www.codefactor.io/repository/github/dmitriy-iliyov/idempify/badge)](https://www.codefactor.io/repository/github/dmitriy-iliyov/idempify)
[![codecov](https://codecov.io/github/dmitriy-iliyov/idempify/branch/main/graph/badge.svg)](https://codecov.io/github/dmitriy-iliyov/idempify)
[![CI](https://github.com/dmitriy-iliyov/idempify/actions/workflows/ci.yml/badge.svg)](https://github.com/dmitriy-iliyov/idempify/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![GitHub Release](https://img.shields.io/github/v/release/dmitriy-iliyov/idempify?include_prereleases)
![GitHub last commit](https://img.shields.io/github/last-commit/dmitriy-iliyov/idempify)

> [!WARNING]
> `idempify` is pre-release (`0.0.1-SNAPSHOT`), not yet published to Maven Central, and several
> pieces described below are explicitly marked **Coming soon**. Treat this README as a snapshot of
> an actively evolving library, not a finished product - see [Limitations](#limitations).

## Overview

`idempify` is a modern Java/Spring Boot library that adds idempotency to write operations. 
A client supplies an `Idempotency-Key` (UUID) and the library guarantees the underlying business
operation runs at most once for that key: the first request executes it, the result is stored, and every retry
with the same key replays the stored result instead of re-running it. The one exception is a business method
run outside the library's transaction (`EXTERNAL_NON_BLOCKING`), which is at-least-once - see
[Processing Branches](#processing-branches). A retry carrying a *different* request
under the same key is rejected rather than silently answered from the store.

## Key Features

- **Atomic claim** - the first-writer-wins guarantee is a single atomic statement in the storage backend, 
  not application-level locking. A duplicate arriving mid-flight waits on it and is answered with the result.
- **Result and response replay** - a completed operation keeps both the serialized result and the answer it                                                                                                                                                                                                                                         
  was given, on one record. A retry with the same key is answered from that record - on HTTP, with the original 
  status, body and headers reproduced as they were. An optional cache in front of that record answers it sooner and 
  never differently.
- **Conflict handling** - a request racing another under the same key is routed to a handler the config picks:
  `REJECT`, `WAIT`, or your own.
- **Fingerprint validation** - the request's fingerprint is compared against the one stored with the original                                                                                                                                                                                                                                       
  request, so replaying a key with a different payload is treated as an error rather than silently served.
- **Bounded lifetime** - a result stays replayable for a configurable ttl counted from completion; past it the                                                                                                                                                                                                                                      
  key is free again and the next request runs as a first attempt.
- **Machine-readable failures** - every refusal the library makes is answered as an RFC 7807 problem with a                                                                                                                                                                                                                                         
  stable `type` URI, in the same shape whether it comes from the filter or from the exception handler.
- **Pluggable by design** - storage, serialization, transport and cache arrive as separate modules, and every                                                                                                                                                                                                                                       
  bean the library registers steps aside for one of your own.
- **Layered configuration** - a call site's annotation, the named config it points at, and the global                                                                                                                                                                                                                                               
  `idempify.*` properties, in that order of precedence. What a layer leaves unspecified the next one answers.
- **Observability** - out-of-the-box metrics via Micrometer.

## Supported Infrastructure

- **Databases:** PostgreSQL.
- **Transport:** HTTP, WebFlux(**Coming soon**), gRPC(**Coming soon**).
- **Serialization:** Jackson.
- **Cache in front of the store:** in-memory (built into core), Redis.

## Limitations

- **A replay is only as literal as the cache allows.** The response lives on the operation's own row, so there
  is nothing for it to go stale against - but the cache in front of that row is asked first and does not fall
  through to the row when its backend is down. A repeat arriving while Redis is unavailable is answered by the
  core from the stored *method result* instead, so its status code and headers are the ones the handler chain
  produces now rather than the ones recorded then.
- **`void` and primitive return types.** The result type travels as a `ResultType`, which keeps type
  arguments, so `List<Order>` and `Map<String, Order>` replay correctly. What it does not settle is a method
  returning `void` or a primitive: the descriptor then carries `void.class` or `int.class` and is handed to
  the deserializer as if it were a value type. `ResponseEntity<Order>` has a second problem of its own -
  Jackson cannot rebuild its status and headers - so it is not replayable either.
- **No automatic table creation** - the DDL is applied by hand.

## Quick Start

1. Add dependencies.

> [!IMPORTANT]
> `idempify-starter` aggregates `idempify-core` + `idempify-aop` + `spring-boot-starter-aop` and binds the
> `idempify.*` properties. It does **not** include serialization, a transport module, a storage backend or a
> cache backend - you must add `idempify-jackson` (or your own `ResultSerializer`/`ResultDeserializer`),
> `idempify-http` (or your own transport integration), `idempify-postgresql` (or your own
> `BlockingOperationRepository` and `ResponseRepository`) and, if you want a distributed cache in front of the
> store, `idempify-cache-redis` explicitly.
>
> An entry is the one part that is not optional. Something has to turn an annotated call into an
> operation, and if no `IdempotentEntry` is registered - `idempify-aop` registers one - the application
> refuses to start instead of running with `@Idempotent` doing nothing. See
> [Start-up Checks](#start-up-checks).

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

3. Select the processor. The global default is `TRANSACTIONAL_NON_BLOCKING`; the setup below asks for
   `TRANSACTIONAL_BLOCKING`, which
   refuses conflict handling, a lock ttl and the keeping of failed outcomes (see
   [Processing Branches](#processing-branches)):

```yaml
idempify:
  processor-type: TRANSACTIONAL_BLOCKING
  conflict:
    enabled: false               # defaults to true; TRANSACTIONAL_BLOCKING rejects it at start-up
```

> [!TIP]
> `IdempotencyEventListener` is answered for you: core registers `IdempotencyEventListener.NOOP`, and
> `idempify-metrics` replaces it with the micrometer one once you ask for metrics. Declare a bean of your own
> only to send the outcomes somewhere else - see [Observability](#observability).

Everything else - metadata resolution and its cache, the fingerprint and conflict providers, the
serialization of both kinds of operation, the lock token generator, the operation and response managers, and
the processors - is declared by `IdempifyCoreAutoConfiguration`. The `TransactionTemplate` it needs is contributed by Spring Boot
itself once a transaction manager is present.

4. Annotate a write endpoint with `@Idempotent`:

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

5. Client calls the endpoint with an `Idempotency-Key` header:

```bash
curl -X POST https://api.example.com/payments \
  -H "Idempotency-Key: 6f1c9f0a-2b3e-4a4b-9f0a-1234567890ab" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100, "currency": "USD"}'
```

A retry with the same header and body replays the original response instead of charging again; a retry with the
same header and a **different** body throws `FingerprintMismatchException` by default.

Read more about `@Idempotent`'s attributes in [Configuration → Annotation](#3-annotation).

---

## Example & Test Environment

> [!WARNING]
> **Coming soon.**

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
   `TRANSACTIONAL_BLOCKING` to `TransactionalBlockingIdempotentProcessor`, `TRANSACTIONAL_NON_BLOCKING` to
   `TransactionalNonBlockingIdempotentProcessor`, `EXTERNAL_NON_BLOCKING` to
   `ExternalNonBlockingIdempotentProcessor` (see [Processing Branches](#processing-branches)).
5. `TransactionalBlockingIdempotentProcessor` opens a transaction of its own and refuses one it did not start
   (`status.isNewTransaction()`, see [Transaction Boundary](#transaction-boundary)), then runs everything
   inside that one `TransactionTemplate.execute(...)`:
   - `BlockingOperationManager.startOrReply(context, metadata)` calls
     `BlockingOperationRepository.saveIfAbsent(operation)` - an atomic
     `INSERT ... ON CONFLICT(idempotency_key) DO UPDATE SET is_first_attempt = false RETURNING *`. This is the
     mechanism that guarantees only one concurrent request "wins" for a given key. A row that is `PROCESSED`
     but past its TTL is overwritten as if new.
   - If the row is `PROCESSED` and the call site fingerprints, `FingerprintMatcher` compares the stored
     fingerprint against the current request's; a mismatch invokes `FingerprintPolicy.handle(...)` (default:
     throws `FingerprintMismatchException` - same key + different request is an error, not a silent replay).
     Otherwise the stored result is deserialized and handed back as a replay.
   - Otherwise `startOrReply` answers with an `OperationDetail` still in `IN_PROCESS`, meaning the caller's
     business logic actually runs.
   - `complete(key, result, ttl)` then serializes the result and performs a conditional
     `UPDATE ... SET result = ?, status = ?, expires_at = clock_timestamp() + ttl WHERE idempotency_key = ? AND
     status = 'IN_PROCESS'` - the expiry is counted by the database's clock from this write. A condition that
     matches no row is not a silent no-op but an `OperationStatusMismatchException`. What it hands back is the
     caller's own result object, not a copy read back through the serializer.
   - An exception from the business method rolls the transaction back and reaches the caller exactly as it was
     thrown, checked or not.
6. Once the transaction has committed - and only then - the processor publishes the operation's state to the
   `OperationStateChannel` and reports the outcome to `IdempotencyEventListener`: `onDuplicate()` for a replay,
   `onSuccess()` for an operation it ran itself.

### Start-up Checks

A library that protects against duplicates is worse off silent than loud, so the two ways of ending up with
`@Idempotent` that does nothing are refused while the context is being built, not discovered in production.

- **No entry at all.** The core refuses when no `IdempotentEntry` bean exists, naming what to add:
  `idempify-aop`, an entry of your own, or `idempify.enabled=false` if the library is meant to be off. This is
  also what answers for `idempify-aop` present but not started - its auto-configuration stands behind
  `@ConditionalOnClass` on AspectJ, so a classpath without it contributes no entry.
- **An endpoint no entry intercepts.** The HTTP registry asks the entry about every endpoint it indexes and
  refuses by name when the answer is no. The answer is read off the bean rather than off the configuration, so
  it covers a handler method that is not public, one that is `final` under a class proxy, and an application
  started with `spring.aop.auto=false`, where the advisor bean exists and weaves nothing.

An endpoint carrying `@Idempotent` on an HTTP method outside `POST`, `PUT`, `PATCH` and `DELETE` is refused by
name as well: the filter would never match it, and idempotency on a read is not something to arrange silently.

### The Recorded Response

The flow above replays the **result of the method**. On HTTP there is a second, shorter path that replays the
**answer itself**, and it is the one a repeat normally takes.

`OperationResponseFilter` sits in front of the `DispatcherServlet` and, for a request whose endpoint carries
`@Idempotent`:

1. reads the key from the header and, when the call site fingerprints, computes the fingerprint;
2. asks `ResponseManager` whether the operation's record already carries a response. If it does - and the
   fingerprint matches - the recorded status, content type, body and headers are written out as they were, and
   the handler chain is never entered;
3. otherwise lets the request through and, once the chain returns, records the answer onto the row the core
   has just committed.

The response is a column on the operation's own row, not a second store: it cannot outlive the record, expire
separately from it, or disagree with it. What `idempify-cache-redis` and the built-in in-memory cache add is
an accelerator in front of that row - a miss falls through to it and changes nothing about the answer.

Two consequences are worth knowing. A repeat that reaches the core instead - because no response was recorded,
or because the lookup failed - is still answered, from the stored result, but by the handler chain's own
serialization rather than byte for byte. And a call site taking its key from SpEL is skipped by the filter
entirely; see [Idempotency Keys](#idempotency-keys).

### Processing Branches

Three processors, told apart by two questions: **can a duplicate that arrives while the first call is still
running wait for it**, and **does the business effect commit together with the record of its outcome?**
Everything else follows from the answers - whether a conflict handler is ever reached, whether a failed
outcome can be kept, what the store has to provide, and what the guarantee is.

|                                 | `TRANSACTIONAL_BLOCKING`                                                       | `TRANSACTIONAL_NON_BLOCKING`                                                                   | `EXTERNAL_NON_BLOCKING`                                                                  |
|---------------------------------|--------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| Guarantee                       | at-most-once                                                                   | at-most-once                                                                                   | **at-least-once**                                                                        |
| Effect and record               | the claim, the effect and the result commit or roll back together             | the effect and the result commit or roll back together; the claim lives apart                  | two separate writes - the effect stays whatever happens to the record                    |
| Transaction boundary            | the processor opens it - a transaction it did not start is refused             | the processor opens one for the business method; a call inside an active transaction is refused | none - a call inside an active transaction is refused                                    |
| The claim                       | the row lock of the insert, held until commit                                  | a lease: a lock token of its own, held for `lockTtl` by the store's clock                      | the same lease                                                                           |
| A duplicate arriving mid-flight | blocks on the store's insert, then replays the result                          | meets the claim and is handed to the call site's `ConflictHandler`                             | the same                                                                                 |
| A failed operation              | leaves no row - it rolls back with the effect, and the next call is a first attempt | the effect rolls back; then `recordFor` decides: a listed exception is kept on the record and thrown to repeats, any other deletes it | the effect stays; `recordFor` decides the same way                                      |
| An outcome that was not recorded | impossible - the result commits with the effect                              | nothing is kept: the effect rolls back with the failed write, and the key is released          | the key stays claimed until the lease runs out, then the next attempt runs the operation again |
| Conflict handling               | refused: a duplicate waits instead of reaching a handler                       | required: switching it off is refused                                                          | required                                                                                 |
| `recordFor`                     | refused when named: an exception rolls the record back, so the key is always released | decides whether a failure stays on the record or releases the key                          | the same                                                                                 |
| `lockTtl`                       | refused                                                                        | required                                                                                       | required, and longer than the longest run of the business method                        |
| Store contract                  | `saveIfAbsent` that holds the key's row until commit, compare-and-swap updates | `lockOrSaveAndLock` under a token, every way out of the claim conditional on that token        | the same                                                                                 |
| Isolation                       | correct at `READ COMMITTED` only, see [Isolation](#isolation)                  | -                                                                                              | -                                                                                        |

What the non-blocking branches are *for* is the mirror image of the blocking one: a claim that is not the
business transaction's is met rather than waited on, which is what gives a conflict handler something to
answer; it outlives a rollback, which is what lets a failed outcome stay on the record; and it can be taken in
a store that offers no transaction-scoped lock.

`EXTERNAL_NON_BLOCKING` is for an effect no transaction can take back - a call to another service. Since the
effect and the record of its outcome are two separate writes, a repeated effect is the application's to
absorb:

- the effect has to be idempotent downstream, for instance by passing the idempotency key on to the service it
  calls - otherwise a repeated effect is a risk it accepts;
- `lockTtl` has to outlast the longest run of the business method, or a duplicate takes over the expired lease
  and runs the method while the first call is still in it.

### Idempotency Guarantees

> [!NOTE]
> Everything below describes the `TRANSACTIONAL_BLOCKING` processor. How the non-blocking ones differ is laid
> out in [Processing Branches](#processing-branches).

#### At-Most-Once Execution

Only one request per key ever runs the business method. The winner is decided by the store, not by the
library: `saveIfAbsent` is a single atomic statement, and the row it locks holds every other request for that
key until the first transaction commits. A duplicate therefore **waits** for as long as the original operation
runs, and is answered with its result - it does not get an immediate rejection, and it does not run the method
a second time.

#### Failures Are Not Replayed

The operation's record shares the business transaction, so a rollback takes both away: an operation that
threw leaves no row behind, and the next request with the same key is treated as a first attempt and runs
again. Only a completed operation is replayable. Keeping a failed outcome on record is what the
non-blocking branches are for.

#### Transaction Boundary

`@Idempotent` belongs on an entry point. The processor opens the transaction itself and refuses one it did not
start: inside `TransactionTemplate.execute(...)` it checks `status.isNewTransaction()` and answers
`IllegalStateException` when the call is already running in somebody else's transaction.

The refusal guards step 6 of the [flow](#processing-flow) - state is published and the outcome reported only
once the transaction has committed - which holds only while the transaction belongs to the processor. Joined
to an outer one, it would return while that transaction is still open, and a rollback further out would leave
a published state and a counted success behind an operation that never existed.

In practice: annotate the endpoint, not a service method that a transactional caller reaches. A method
carrying both `@Idempotent` and `@Transactional` works only when the idempotency advisor wraps the
transactional one, and neither declares an order today, so which wraps which follows registration order -
keep the two apart until that is settled.

The same applies to a `TransactionTemplate` you contribute yourself: it has to be able to start a transaction
of its own, which means `REQUIRES_NEW`, or `REQUIRED` or `NESTED` with no outer transaction around the call. A
template with `MANDATORY`, `SUPPORTS`, `NOT_SUPPORTED` or `NEVER` can never produce one, and is refused when the
processor is built, so the application does not start.

#### Isolation

The blocking branch is correct at `READ COMMITTED`, and only there. Under `REPEATABLE READ` or
`SERIALIZABLE` the duplicate that waited on `ON CONFLICT DO UPDATE` does not see the winner's committed row -
it fails with `could not serialize access due to concurrent update` (SQLSTATE 40001) instead of replaying the
result. Nothing in the library checks the level of the transaction it is given, so run the datasource behind
an idempotent endpoint at `READ COMMITTED` (PostgreSQL's default).

### Idempotency Keys

A key is a UUID, unique across the whole store: the column is the table's primary key, not a pair with the
endpoint or the caller. A key names one logical operation, and the client makes a new one for every new
operation.

- **Another endpoint.** Reusing a key there is a client error, and the library reports it instead of replaying:
  the path takes part in the [fingerprint](#fingerprinting), so the call fails with
  `FingerprintMismatchException`. With the fingerprint switched off the check is gone - the second endpoint is
  answered with the first one's recorded response (a 500 on the path through the core, where the stored result
  does not fit the method's type).
- **Another caller.** The store does not know who sent a key, so whoever presents a key whose operation is
  recorded gets its response, headers included. With random keys (UUID v4) that needs a leaked key. The risk is
  real when keys are derived, say from an order number, in a system where tenants may collide on it: put the
  principal into the key (`@Idempotent(idempotencyKey = "...")` takes a SpEL expression) or into the fingerprint
  (a `FingerprintPolicy` of your own).

A call site takes its key from one of two sources, and naming both is refused when the metadata is resolved:

| Source | How it is named | Read by |
|---|---|---|
| a request header (default) | `@Idempotent(headerName = "Idempotency-Key")`, or the global `header-name` | the transport's `KeyExtractor`, before the method runs |
| the call's own arguments | `@Idempotent(idempotencyKey = "#dto.id")`, a SpEL expression | the aspect, once the arguments are known |

> [!NOTE]
> With a SpEL key the recorded response cannot be used at all: the filter looks it up before the handler runs,
> and a key computed from the arguments does not exist yet at that moment. The filter recognises such a call
> site by its resolved metadata and steps aside, leaving idempotency to the aspect and the store - so a repeat
> is answered from the result, and nothing is recorded to answer it with.

### Operation Status

An operation's persisted `status` is one of:

```text
IN_PROCESS --complete()--------------> PROCESSED
IN_PROCESS --a failure kept on record--> EXCEPTIONALLY_PROCESSED   (non-blocking branches only)
```

Both finished statuses carry an expiry and are replayed until it passes: `PROCESSED` with its result,
`EXCEPTIONALLY_PROCESSED` by throwing the failure again.

`CONFLICT` is a fourth `OperationStatus` value, but it describes an attempt, not a record: the non-blocking
branches assign it in memory when a call finds the key's claim held by another one, hands that call to the
`ConflictHandler`, and never writes it to the store.

### Conflict Handling

> [!NOTE]
> Conflict handling belongs to the non-blocking branches: the blocking processor settles a duplicate on the
> store's own insert - the loser blocks, then replays - so `IdempotencyConfig` rejects an enabled
> `conflictConfig` under `TRANSACTIONAL_BLOCKING`, while `TRANSACTIONAL_NON_BLOCKING` refuses to run without it.

A handler answers with the completed operation the call replays, or throws to refuse it. One that returns
`null` or an operation still in progress is refused with `ConflictHandlerResultException`, answered on HTTP as
a 500 with the `conflict-handler-broken` type.

| Strategy           | Behavior                                                                                                                                                                                                                                                                                                                   |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `REJECT` (default) | Throws `OperationInProgressException` immediately.                                                                                                                                                                                                                                                                         |
| `WAIT`             | Polls `NonBlockingOperationRepository.find` with exponential backoff (`delay * multiplier ^ attempt`, never sleeping past `maxDuration`) until the operation is finished - `PROCESSED` or `EXCEPTIONALLY_PROCESSED` - then replays it. Tuned by `WaitConflictHandlerConfig` (`delay`, `multiplier`, `maxAttempts`, `maxDuration`); gives up with `WaitTimeoutException` when the duration runs out, `WaitAttemptsExhaustedException` when the attempts do and `WaitInterruptedException` when the thread is interrupted - all three are a `WaitAbortedException`. |
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

A row keeps two things that need rendering - the method's result and the recorded response - so the SPI comes
in two pairs. `idempify-jackson` provides all four, auto-configured from the application's `ObjectMapper`
bean.

```java
public interface ResultSerializer {
    String serialize(Object result);
}

public interface ResultDeserializer {
    Object deserialize(String rawResult, ResultType type);
}

public interface ResponseSerializer {
    String serialize(Response response);
}

public interface ResponseDeserializer {
    Response deserialize(String rawResponse);
}
```

None of the four is ever called with `null` and none may answer it: an operation with no result to keep, or
none recorded yet, is written as an empty column, and the core decides that before a serializer is asked.

`ResultType` carries a `java.lang.reflect.Type`, not a `Class`, and that is the whole point: a generic return
type is written into the class file and read back with `Method#getGenericReturnType()`, so `List<Order>`
reaches the deserializer intact. **Implementations should read `resultType.getType()`** - a Jackson-backed one
turns it into a `JavaType` with `mapper.getTypeFactory().constructType(type)`, and other libraries take a
`Type` directly too.

Both sides deal in `Object` rather than a type parameter. The library wraps arbitrary methods, so the concrete
return type is known only at runtime; a `<T>` here would be inferred as `Object` at the one entry point that
matters and would promise a guarantee nobody can keep. A caller that does know its type - an entry point of
your own rather than the `@Idempotent` aspect - casts once at its own boundary.

### Storage

The store contract is split by concurrency model rather than by table:

- `BlockingOperationRepository` - the write side that relies on the store's own atomicity: `saveIfAbsent`
  must let exactly one concurrent request claim a key and hold that claim until the caller's transaction
  ends, and `update` / `saveResultAndUpdateStatus` must be conditional on the row's current `status`, since
  concurrent requests for the same key race through this code path inside a transaction;
- `NonBlockingOperationRepository` - the write side whose claim is a lease: `lockOrSaveAndLock` takes it under
  a lock token for `lockTtl`, and completing, recording a failure, releasing and deleting are all conditional
  on that token - a write that finds the token gone throws `OperationLockLostException`, since the lease ran
  out and another call took the key. `find` reads a row without claiming it;
- `ResponseRepository` - the recorded response, read and written where it lives: on the row the operation
  already occupies. Its write is conditional on that operation having completed, and it hands back the
  replayable part alone - the response, the fingerprint to check it against, and the time the record has
  left. This is also the seam a cache decorates.

All of them deal in rows rather than domain objects: a backend stores the components it is given and returns
the components it holds, and never learns what they decode into. Time is the store's too: it sets the creation
time itself, counts an expiry from the ttl it is handed, and answers every read with how long the row has left
by its own clock - so no instance ever compares a stored moment against a clock of its own.

```sql
CREATE TABLE IF NOT EXISTS idempotent_operations(
    idempotency_key UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    is_first_attempt BOOLEAN NOT NULL,
    result TEXT,
    exception_class_name TEXT,
    response TEXT,
    fingerprint VARCHAR(255),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    lock_token UUID,
    lock_until TIMESTAMPTZ
);
```

### Expiry & Cleanup

`expires_at` is written when the operation **completes** - the moment its result exists, plus the ttl resolved
for that call site (24 hours by default), both counted by the database's `clock_timestamp()` rather than by the
application. Past that moment the key is free again: the next request carrying it overwrites the row and runs
the business method as a first attempt. The recorded response is bounded by the very same column, and a cache
entry standing in for that row takes the time the record has left from it rather than being given a ttl of its
own - which is why no entry can outlive what it replays.

Counting from completion rather than from the claim is what keeps a slow operation replayable: one that ran
longer than its own ttl would otherwise commit a row that is already stale, serve no replay to anybody, and
be unable to record its response either. The price is that a row being worked on right now has **no** expiry at all -
the column is nullable, and `expires_at IS NULL` means "still running", never "expired long ago".

Expired rows are **not** deleted by the library - nothing in it schedules a sweep, and an expired row costs
nothing until its key comes back. Reclaiming the space is the application's job:

```sql
DELETE FROM idempotent_operations WHERE expires_at < now();
```

Run it outside a request, and only against expired rows: deleting a row an operation still owns is what
`OperationStatusMismatchException` reports when the operation tries to complete. Note what this query leaves
behind: a row still in `IN_PROCESS` has a `NULL` expiry, so the predicate never selects it. That is the point
while an operation is running - and a gap once one has died mid-flight, since nothing then resets it. Sweeping
those needs a different predicate, over `created_at` rather than `expires_at`, and a limit the application
picks for how long a claim may plausibly stay open.

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
| `idempify.operations` | Idempotent calls, by the outcome they reached | `outcome={duplicate, conflict, fingerprint-mismatch, business-failure, success, exceptionally-processed, failure}`, `failure={none, misplaced, interrupted, failed-before-run, outcome-not-recorded}` |
| `idempify.cache.gets` | Response cache lookups, by what they found    | `result={hit, miss}`                                                      |

One meter per subject and the difference in a tag, the way micrometer's own binders report `cache.gets`: the
questions worth asking span the outcomes, so they stay single expressions instead of arithmetic over
separately named counters, and a new outcome does not break a dashboard.

`outcome="failure"` is a call that reached no outcome of its own for a reason outside the business method, and
the `failure` tag names it: `misplaced` - `@Idempotent` sits somewhere other than an entry point; `interrupted` -
the thread was stopped; `failed-before-run` - the library failed before the business method ran, so nothing happened;
`outcome-not-recorded` - the business method ran and its outcome was lost, which under `EXTERNAL_NON_BLOCKING`
means a repeat will run it again. Every other outcome carries `failure="none"`, since some registries require
every series of a meter to have the same tag keys.

Every series is registered at start-up, so a dashboard reads zero rather than a gap until an outcome first
happens. The tags take a fixed set of values and carry no request data, so they add no cardinality.

`idempify.operations` counts what the processors report through `IdempotencyEventListener`: this module
contributes the micrometer implementation of that hook, and outranks the `IdempotencyEventListener.NOOP` core
registers whenever this module stays out - absent from the classpath, or never switched on. A listener bean of
your own outranks both, and leaves this meter unregistered.

`idempify.cache.gets` counts lookups in the cache standing in front of the response store, and only where a
backend applies the registered `ResponseRepositoryWrapper`s. Writes are left uncounted: one happens per
completed operation, which `idempify.operations`
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
  lock-ttl: 5m
  processor-type: TRANSACTIONAL_NON_BLOCKING
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
  response:
    record-for: []   # left out: EXTERNAL_NON_BLOCKING keeps every failure, the transactional processors keep none
    included-headers: []
    excluded-headers: []
  cache:
    enabled: false
    type: IN_MEMORY
    name: payments-service
    capacity: 100
  metrics:
    enabled: false
```

| Property                                    | Description                                                                                | Default                   |
|---------------------------------------------|--------------------------------------------------------------------------------------------|---------------------------|
| `enabled`                                   | Switches the library on. `false` keeps every idempify bean out of the context              | `true`                    |
| `header-name`                               | Header the idempotency key is read from                                                    | `Idempotency-Key`         |
| `ttl`                                       | How long a completed operation's result stays replayable                                   | `24h`                     |
| `lock-ttl`                                  | How long a non-blocking claim is held; refused under `TRANSACTIONAL_BLOCKING`              | `5m`                      |
| `processor-type`                            | Which processor runs the operation: `TRANSACTIONAL_BLOCKING`, `TRANSACTIONAL_NON_BLOCKING` or `EXTERNAL_NON_BLOCKING` | `TRANSACTIONAL_NON_BLOCKING` |
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
| `response.record-for`                       | Exceptions kept on the record and thrown to repeats; any other one releases the key        | by processor, see below   |
| `response.included-headers`                 | Only these response headers are recorded; empty keeps every header the answer carries      | empty                     |
| `response.excluded-headers`                 | These response headers are left out; subtracted after the included set                     | empty                     |
| `cache.enabled`                             | Whether a cache stands in front of the response store                                      | `false`                   |
| `cache.type`                                | Which one: `IN_MEMORY` (built into core), `DISTRIBUTED` (a backend module), `CUSTOM`       | `IN_MEMORY`               |
| `cache.name`                                | Name the entries are kept under; required for `DISTRIBUTED`, unused by `IN_MEMORY`         | none                      |
| `cache.capacity`                            | Entries `IN_MEMORY` holds before evicting the oldest; must be positive                     | `100`                     |
| `metrics.enabled`                           | Whether `idempify-metrics` registers its meters; ignored when the module is absent         | `false`                   |

> [!IMPORTANT]
> Recording the response is not the same setting as caching it. `response.*` decides what stays **on the
> record** - which answers are kept and which of their headers survive - and it is always in force. `cache.*`
> only decides whether an accelerator stands in front of that record; switching it off costs latency, never an
> answer.

> [!IMPORTANT]
> The cache is **off until you ask for it**, and a distributed one has to be named: `cache.name` is what keeps
> the entries of one application apart from another's in a store they share, so there is no default worth
> inventing. `cache.type: DISTRIBUTED` without a name fails at start-up with a message naming both ways out,
> and so does a non-positive `cache.capacity` for an in-memory one.

> [!NOTE]
> `response.record-for` names exception types, the way `@Transactional(rollbackFor)` does: an operation that
> throws one of them leaves the outcome on the record, and a repeat gets the same answer; an exception it does
> not name gives its key back, and the next call is a first attempt. Left out, the list is `[Throwable]` for `EXTERNAL_NON_BLOCKING` - its effect is
> not rolled back, so a failure is kept rather than risk running the operation twice - and empty for the
> transactional processors, where a failure rolls the effect back and releases the key. Written empty, it keeps
> no failure whatever the processor. A failure that says something about the call rather than about the
> operation releases it whatever the list says: an `@Idempotent` call made where it does not belong - nested in
> another operation (`NestedIdempotencyException`) or inside a transaction it did not open
> (`ForeignTransactionException`); an interruption - an `InterruptedException`, or any failure thrown while
> the thread's interrupt flag is set; and a failure of the library itself, before the business method ran
> (`EarlyFailureException`) or after it (`OutcomeNotRecordedException`). A type that is not an exception is
> refused at start-up. Only the non-blocking branches can keep a failed outcome, so under
> `TRANSACTIONAL_BLOCKING` a non-empty list is refused.

> [!WARNING]
> Some combinations are refused at start-up rather than at the first request. With
> `processor-type: TRANSACTIONAL_BLOCKING`, `conflict.enabled` must be false - the blocking processor settles duplicates on
> the store's insert, so no conflict is left to handle - and `lock-ttl` and a non-empty `response.record-for` must be left out, since the claim
> lives in the business transaction and a rollback takes a failure away with it. With `processor-type: TRANSACTIONAL_NON_BLOCKING`, `conflict.enabled` must stay true:
> a duplicate meets the unfinished operation there, and only a handler can answer it.

> [!NOTE]
> The cache is governed by properties alone - `@Idempotent` has no attribute for it. A call site allowed to
> switch it on would get metadata promising a cache that does not exist in the context.

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

| Attribute        | Description                                                                      | Unspecified value |
|------------------|----------------------------------------------------------------------------------|-------------------|
| `config`         | Name of a registered `IdempotencyConfig` to layer under this call site           | `""`              |
| `idempotencyKey` | SpEL expression the key is computed from; the header is read when it is blank    | `""`              |
| `headerName`     | Header this call site reads the key from                                         | `""`              |
| `ttl`            | How long the result stays replayable, counted in `timeUnit`                      | `-1`              |
| `lockTtl`        | How long a non-blocking claim is held, counted in `timeUnit`                     | `-1`              |
| `timeUnit`       | Unit `ttl` and `lockTtl` are written in                                          | `HOURS`           |
| `processorType`  | Which processor runs the operation: `TRANSACTIONAL_BLOCKING`, `TRANSACTIONAL_NON_BLOCKING` or `EXTERNAL_NON_BLOCKING` | `UNSELECTED`      |
| `onConflict`     | What a duplicate meeting an operation in flight gets: `REJECT` or `WAIT`         | `UNSELECTED`      |
| `useFingerprint` | Whether a duplicate must match the original request: `ENABLE` or `DISABLE`       | `UNSELECTED`      |
| `useRecordFor`   | Whether a failure may be kept on the record at all: `ENABLE` or `DISABLE`        | `UNSELECTED`      |
| `recordFor`      | Exceptions kept on the record; an empty list leaves the choice to other layers   | `{}`              |

Every attribute is optional and each has a value meaning "not decided here" - an empty string, a negative
`ttl` or `lockTtl`, `UNSELECTED`, an empty `recordFor`; `timeUnit` only qualifies the two durations. That value is not a setting of its own: it hands the decision to
the named config, and failing that to the global properties. A hand-written `ConflictHandler` or
`FingerprintPolicy` is not named here at all: it is an instance on the config, not a class on the annotation.

### Examples

#### Blocking, Header Key

The store decides the races, the key travels in a header, and the one
section the blocking processor refuses is switched off. Error answers stay off the record by default, so
that half needs no line here.

```yaml
idempify:
  processor-type: TRANSACTIONAL_BLOCKING
  header-name: Idempotency-Key
  ttl: 24h
  conflict:
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
  processor-type: TRANSACTIONAL_BLOCKING
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

An endpoint whose clients cannot send a header takes the key from the call's own arguments. The recorded
response does not apply here - a repeat is answered from the result instead, see
[Idempotency Keys](#idempotency-keys).

```java
    @PostMapping("/payments")
    @Idempotent(idempotencyKey = "#request.paymentId")
    public PaymentResponse pay(@RequestBody PaymentRequest request) { ... }
```

## Roadmap
- A store for the non-blocking branches (`TRANSACTIONAL_NON_BLOCKING`, `EXTERNAL_NON_BLOCKING`), and with it conflict
  handling and a record that survives a failed operation
- Named configuration profiles declared in YAML
- Automatic table creation
- A working `idempify-example` demo application
- Maven Central publishing
- gRPC transport support (`RequestType.GRPC` is reserved but unimplemented) and WebFlux support
- MySQL and Oracle store implementations
