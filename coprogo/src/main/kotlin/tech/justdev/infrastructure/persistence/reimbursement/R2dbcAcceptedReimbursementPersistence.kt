package tech.justdev.infrastructure.persistence.reimbursement

import jakarta.inject.Singleton
import tech.justdev.application.reimbursement.AcceptedReimbursementPersistence
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.ledger.event.AcceptedReimbursementLedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository

@Singleton
class R2dbcAcceptedReimbursementPersistence(
    private val transactionRunner: TransactionRunner,
    private val reimbursementRepository: ReimbursementRepository,
    private val ledgerEventRepository: LedgerEventRepository,
) : AcceptedReimbursementPersistence {
    override suspend fun persist(reimbursement: Reimbursement) {
        val ledgerEvent = AcceptedReimbursementLedgerEvent.from(reimbursement)

        transactionRunner.transaction {
            reimbursementRepository.persist(reimbursement)
            ledgerEventRepository.append(ledgerEvent)
        }
    }
}
