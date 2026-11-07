# Contributing to idempify

Thanks for taking the time to contribute. `idempify` is pre-release, so APIs still move; if you plan anything
larger than a small fix, open an issue first so the shape can be agreed before code is written.

## Reporting bugs and proposing features

- **Bugs** - open an issue with the *Bug report* template. A failing test or a minimal Spring Boot application
  that reproduces the problem is the most useful thing you can attach.
- **Features** - open an issue with the *Feature request* template and describe the problem before the solution.
- **Security vulnerabilities** - do not open a public issue; follow [SECURITY.md](SECURITY.md).

## Building

Requirements: JDK 21 and Docker (the PostgreSQL and Redis modules run their tests against real containers via
Testcontainers). The Maven wrapper is checked in, so no local Maven installation is needed.

```bash
./mvnw compile                                  # all modules
./mvnw test                                     # unit tests of all modules
./mvnw verify                                   # tests + jacoco coverage report
./mvnw -pl idempify-core test                   # a single module
./mvnw -pl idempify-core -Dtest=SomeTest test   # a single test class
```

Do not remove `<parameters>true</parameters>` from the compiler configuration: response deserialization relies on
parameter names being present in the bytecode.

## Project layout

| Module | Purpose |
|---|---|
| `idempify-core` | domain and every contract; independent of transport and framework |
| `idempify-aop` | the `@Idempotent` aspect |
| `idempify-http` | Spring MVC / Servlet integration: response recording and replay, problem responses |
| `idempify-jackson` | JSON serialization of results and responses, body canonicalization |
| `idempify-postgresql` | PostgreSQL storage |
| `idempify-cache-redis` | Redis cache in front of the storage |
| `idempify-metrics` | Micrometer metrics |
| `idempify-starter` | core, entry point and binding of `idempify.*` properties |

Dependencies point towards `idempify-core`; the core must not depend on any other module.

## Code style

There is no formatter or linter in the build, so the conventions below are kept by review:

- 4-space indentation, lines up to ~120 characters.
- No Lombok and no `var`; constructors, getters and `toString` are written by hand.
- No `@Component`, `@Service` or `@Autowired` in library code - beans are declared in the module's
  `@AutoConfiguration` class, each under `@ConditionalOnMissingBean` so applications can replace it.
- Constructor injection only, fields `private final`, required arguments checked with
  `Objects.requireNonNull(x, "x cannot be null")`.
- Value types are `final` classes with a private constructor and a nested `Builder`.
- Library exceptions extend `RuntimeException`; messages say both what is wrong and what was received, e.g.
  `"ttl must be positive, but was %s".formatted(ttl)`.
- Boolean accessors are predicates without `get` (`isEnabled()`, `useFingerprint()`).
- `"...%s".formatted(...)` instead of `String.format`.
- Code, javadoc, test names and exception messages are written in English.

Javadoc documents what the signature does not say - the contract, the meaning of `null`, an invariant. Do not
restate the method name.

## Tests

Every change comes with tests. Test classes are named by level:

- `*UnitTest` - a single class, collaborators mocked;
- `*IntegrationTest` - a class against real infrastructure (PostgreSQL, Redis in Testcontainers);
- `*ComponentTest` - a module's auto-configuration assembled in a Spring context.

Each test method carries a `@DisplayName` describing the case. A bug fix starts with a test that fails without
the fix.

The project aims for at least 80% coverage, measured by Codecov on every pull request.

## Commits and pull requests

Commit messages follow the form `type(what changed, as a statement)`, where `type` is one of `add`, `fix`,
`refactor`, `docs`, `chore`:

```
fix(the interceptor lets a failure out as thrown)
```

Before opening a pull request:

1. Rebase onto the latest `main`.
2. Make sure `./mvnw verify` passes locally.
3. Fill in the pull request template.

By contributing you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
