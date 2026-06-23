package tech.justdev.application.shared

interface TransactionRunner {
    suspend fun <T> transaction(block: suspend () -> T): T
}

object DirectTransactionRunner : TransactionRunner {
    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}
