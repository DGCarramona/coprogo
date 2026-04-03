package tech.justdev.testsupport

import org.testcontainers.postgresql.PostgreSQLContainer

object PostgresTestDatabase {
    private val postgres = PostgreSQLContainer("postgres:17-alpine")
        .withDatabaseName("coprogo")
        .withUsername("coprogo")
        .withPassword("coprogo")

    fun properties(): Map<String, String> {
        if (!postgres.isRunning) {
            postgres.start()
        }

        return mapOf(
            "datasources.default.url" to postgres.jdbcUrl,
            "datasources.default.username" to postgres.username,
            "datasources.default.password" to postgres.password,
            "r2dbc.datasources.default.url" to "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}",
            "r2dbc.datasources.default.username" to postgres.username,
            "r2dbc.datasources.default.password" to postgres.password,
        )
    }
}

