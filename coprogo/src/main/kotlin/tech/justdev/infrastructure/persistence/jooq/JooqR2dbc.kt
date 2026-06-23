package tech.justdev.infrastructure.persistence.jooq

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import tech.justdev.application.shared.TransactionRunner
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@Singleton
class R2dbcTransactionRunner(
    @Named("default")
    private val connectionFactory: ConnectionFactory,
) : TransactionRunner {
    override suspend fun <T> transaction(block: suspend () -> T): T = connectionFactory.transaction(block)
}

suspend fun ConnectionFactory.dsl(): DSLContext =
    coroutineContext[JooqTransactionContext]
        ?.let { context -> DSL.using(context.connection, SQLDialect.POSTGRES) }
        ?: DSL.using(this, SQLDialect.POSTGRES)

suspend fun <T> ConnectionFactory.transaction(block: suspend () -> T): T {
    if (coroutineContext[JooqTransactionContext] != null) {
        return block()
    }

    val connection = awaitConnection()
    return try {
        connection.beginTransaction().awaitFirstOrNull()
        val result = withContext(JooqTransactionContext(connection)) { block() }
        connection.commitTransaction().awaitFirstOrNull()
        result
    } catch (exception: Throwable) {
        connection.rollbackTransaction().awaitFirstOrNull()
        throw exception
    } finally {
        connection.close().awaitFirstOrNull()
    }
}

private suspend fun ConnectionFactory.awaitConnection(): Connection = create().awaitSingle()

private class JooqTransactionContext(
    val connection: Connection,
) : AbstractCoroutineContextElement(JooqTransactionContext) {
    companion object Key : CoroutineContext.Key<JooqTransactionContext>
}
