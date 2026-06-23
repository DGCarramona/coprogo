package tech.justdev.infrastructure.persistence.jooq

import io.micronaut.context.annotation.Factory
import io.micronaut.context.env.Environment
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.net.URI

@Factory
class R2dbcConnectionFactoryFactory {
    @Named("default")
    @Singleton
    fun connectionFactory(environment: Environment): ConnectionFactory = ConnectionFactories.get(environment.connectionFactoryOptions())
}

private fun Environment.connectionFactoryOptions(): ConnectionFactoryOptions =
    getProperty("datasources.migration.url", String::class.java)
        .map { jdbcUrl -> fromMigrationJdbcUrl(jdbcUrl) }
        .orElseGet { fromDefaultR2dbcUrl() }

private fun Environment.fromDefaultR2dbcUrl(): ConnectionFactoryOptions =
    fromR2dbcUrl(getRequiredProperty("r2dbc.datasources.default.url", String::class.java))

private fun Environment.fromR2dbcUrl(r2dbcUrl: String): ConnectionFactoryOptions {
    val uri = URI(r2dbcUrl.removePrefix("r2dbc:pool:").removePrefix("r2dbc:"))
    return options(
        host = uri.host,
        port = if (uri.port == -1) 5432 else uri.port,
        database = uri.path.removePrefix("/"),
        username = requiredProperty("r2dbc.datasources.default.username"),
        password = requiredProperty("r2dbc.datasources.default.password"),
    )
}

private fun Environment.fromMigrationJdbcUrl(jdbcUrl: String): ConnectionFactoryOptions {
    val uri = URI(jdbcUrl.removePrefix("jdbc:"))
    return options(
        host = uri.host,
        port = if (uri.port == -1) 5432 else uri.port,
        database = uri.path.removePrefix("/"),
        username = requiredProperty("datasources.migration.username"),
        password = requiredProperty("datasources.migration.password"),
    )
}

private fun options(
    host: String,
    port: Int,
    database: String,
    username: String,
    password: String,
): ConnectionFactoryOptions =
    ConnectionFactoryOptions
        .builder()
        .option(ConnectionFactoryOptions.DRIVER, "postgresql")
        .option(ConnectionFactoryOptions.HOST, host)
        .option(ConnectionFactoryOptions.PORT, port)
        .option(ConnectionFactoryOptions.DATABASE, database)
        .option(ConnectionFactoryOptions.USER, username)
        .option(ConnectionFactoryOptions.PASSWORD, password)
        .build()

private fun Environment.requiredProperty(name: String): String = getRequiredProperty(name, String::class.java)
