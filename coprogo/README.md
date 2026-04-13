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

### PostgreSQL-backed tests

When a backend test needs a real database, do not wire a PostgreSQL container manually in the test class.

- Use `@PostgresMicronautTest` for the standard case.
- Use `@MicronautTest(...)` together with `@UsesPostgresTestDatabase` if the test needs custom Micronaut test options.

The shared PostgreSQL test support lives under `src/test/kotlin/tech/justdev/testsupport/` and provides:

- a shared PostgreSQL Testcontainers instance
- JDBC and R2DBC datasource properties for Micronaut tests
- automatic property injection through Micronaut test support

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

## Micronaut 4.10.10 Documentation

- [User Guide](https://docs.micronaut.io/4.10.10/guide/index.html)
- [API Reference](https://docs.micronaut.io/4.10.10/api/index.html)
- [Configuration Reference](https://docs.micronaut.io/4.10.10/guide/configurationreference.html)
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
