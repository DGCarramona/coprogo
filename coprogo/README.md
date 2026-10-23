## Backend Testing Conventions

## Local runtime environment

The backend uses two Micronaut default environments for local execution:

- `runtime`: shared runtime defaults versioned in `src/main/resources/application-runtime.properties`
- `local`: machine-specific overrides loaded from `src/main/resources/application-local.properties`

This applies to:

- `./gradlew backendRun` from the repository root
- `./gradlew dev` from the repository root
- `./gradlew run` from `coprogo/`

Create `src/main/resources/application-local.properties` from
`src/main/resources/application-local.example.properties` and set your local Google audience:

```properties
coprogo.security.google-id-token.audiences=your-dev-client-id.apps.googleusercontent.com
```

Use a comma-separated list if you want to accept multiple audiences locally.

The shared database defaults live in `application-runtime.properties` and can still be overridden with
the deployed-environment variables `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD`.

## Backend container

Build from the repository root, not from `coprogo/`:

```sh
docker build -f coprogo/Dockerfile -t coprogo-backend .
```

The multi-stage image builds the native executable with GraalVM Java 25 and runs it in a non-root
distroless image. Base images are pinned by digest; update the tag and digest together when upgrading.
The final image contains the executable and its native libraries, without Gradle, a JDK, or a shell.
The Dockerfile-specific context allowlist excludes the frontend, tests, build outputs, machine-local
configuration, dotenv files, and private keys. Supply credentials only at runtime; do not introduce
Docker build arguments containing secrets.

BuildKit caches Gradle downloads and build-cache entries. Build configuration is copied before
application sources so source changes preserve the configuration layer. Packaging uses committed
jOOQ sources and needs neither PostgreSQL nor S3. It does not replace the tests and mutation checks
run by CI. Kotlin compilation uses a 1 GiB Gradle heap, then a separate invocation reduces Gradle's
heap to 512 MiB while native-image uses a 6 GiB heap and two workers. Allow additional memory for
native allocations and compiler processes; the build requires substantially more RAM than runtime.

Runtime defaults are `MICRONAUT_ENVIRONMENTS=runtime`, host `0.0.0.0`, and port `10000`.
The image starts the executable directly, with a 256 MiB heap and a 64 MiB direct-memory limit,
leaving room for other native allocations in a 512 MiB service. These are starting limits, not a
capacity guarantee under load. Override the port with `MICRONAUT_SERVER_PORT`; command arguments
after the image name replace the default memory arguments.

### PostgreSQL-backed tests

When a backend test needs a real database, do not wire a PostgreSQL container manually in the test class.

- Use `@PostgresMicronautTest` for the standard case.

PostgreSQL is provided by Micronaut Test Resources. The shared test support lives under
`src/test/kotlin/tech/justdev/testsupport/` and provides:

- the standard `@PostgresMicronautTest` annotation
- Micronaut Test Resources-backed JDBC and R2DBC datasource configuration
- automatic PostgreSQL container lifecycle management through Micronaut

Example:

```kotlin
@PostgresMicronautTest
class MyRepositoryTest {
    // ...
}
```

### Integration tests without a database

When a backend integration test needs the Micronaut HTTP/application context but does not exercise persistence,
prefer the dedicated no-database test environment instead of wiring datasource overrides in the test class.

- Use `@NoDbMicronautTest` for the standard case.
- The shared no-database configuration lives in `src/test/resources/application-integration-no-db.properties`.
- This profile disables JDBC and Flyway and provides the minimal R2DBC settings needed for Micronaut Data to boot without a real database.

Example:

```kotlin
@NoDbMicronautTest
class MyAuthenticationTest {
    // ...
}
```

### Mutation tests

Run PIT from the repository root to check whether the backend test suite detects deliberate code changes:

```bash
./gradlew backendMutationTest
```

The analysis covers the `domain`, `application`, authentication, document-storage, and project-owned persistence code. It includes integration tests for adapters implementing project-owned ports, including PostgreSQL, authentication, and S3 boundaries. Generated jOOQ classes plus application-workflow, HTTP, and schema integration tests remain excluded.

The configured operators focus on business behavior and source-authored side effects while omitting known Kotlin bytecode noise. The build enforces 100% mutation coverage, 100% test strength, and 90% line coverage over that mutation set. Reports are written to `coprogo/build/reports/pitest/index.html` and `coprogo/build/reports/pitest/mutations.xml`. Use `-Ppitest.threads=<count>` to change the default four workers. For a focused diagnostic run, combine `-Ppitest.targetClasses=<comma-separated-globs>` with `-Ppitest.targetTests=<comma-separated-globs>` so PIT does not inspect unrelated tests.

The `pitest-support` plugin has two independent filters. One removes the compiler-generated condition that checks whether a Kotlin coroutine has suspended, while retaining source-authored conditions in the same method. The other filters only the verified-equivalent mutations declared in [`pitest-equivalent-mutations.json`](pitest-equivalent-mutations.json), matched by exact class, method, mutator, and bytecode index. Every whitelist entry requires a justification, and a compiler or source change invalidates the match. When its result listener prints a surviving mutation, first try to distinguish it with an observable test. If that is impossible, add the reported signature to the whitelist with the equivalence argument; otherwise the test suite must kill it.

## Micronaut 5.0.2 Documentation

- [User Guide](https://docs.micronaut.io/5.0.2/guide/index.html)
- [API Reference](https://docs.micronaut.io/5.0.2/api/index.html)
- [Configuration Reference](https://docs.micronaut.io/5.0.2/guide/configurationreference.html)
- [Micronaut Guides](https://guides.micronaut.io/index.html)
---

- [Micronaut Gradle Plugin documentation](https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/)
- [GraalVM Gradle Plugin documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
- [Shadow Gradle Plugin](https://gradleup.com/shadow/)
## Feature flyway documentation


- [Micronaut Flyway Database Migration documentation](https://micronaut-projects.github.io/micronaut-flyway/latest/guide/index.html)


- [https://flywaydb.org/](https://flywaydb.org/)


## Feature ksp documentation


- [Micronaut Kotlin Symbol Processing (KSP) documentation](https://docs.micronaut.io/latest/guide/#kotlin)


- [https://kotlinlang.org/docs/ksp-overview.html](https://kotlinlang.org/docs/ksp-overview.html)


## Feature security-jwt documentation


- [Micronaut Security JWT documentation](https://micronaut-projects.github.io/micronaut-security/latest/guide/index.html)


## Feature r2dbc documentation


- [Micronaut R2DBC documentation](https://micronaut-projects.github.io/micronaut-r2dbc/latest/guide/)


- [https://r2dbc.io](https://r2dbc.io)


## Feature jdbc-hikari documentation


- [Micronaut Hikari JDBC Connection Pool documentation](https://micronaut-projects.github.io/micronaut-sql/latest/guide/index.html#jdbc)


## Feature test-resources documentation


- [Micronaut Test Resources documentation](https://micronaut-projects.github.io/micronaut-test-resources/latest/guide/)


## Feature micronaut-aot documentation


- [Micronaut AOT documentation](https://micronaut-projects.github.io/micronaut-aot/latest/guide/)


## Feature data-r2dbc documentation


- [Micronaut Data R2DBC documentation](https://micronaut-projects.github.io/micronaut-data/latest/guide/#dbc)


- [https://r2dbc.io](https://r2dbc.io)


## Feature serialization-jackson documentation


- [Micronaut Serialization Jackson Core documentation](https://micronaut-projects.github.io/micronaut-serialization/latest/guide/)
