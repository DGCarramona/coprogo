package tech.justdev.infrastructure.persistence.reimbursement

import jakarta.inject.Singleton
import tech.justdev.application.reimbursement.DocumentedReimbursementDeclaration
import tech.justdev.application.reimbursement.ReimbursementDeclarationPersistence
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.document.repository.DocumentUploadIntentRepository
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository

@Singleton
class R2dbcReimbursementDeclarationPersistence(
    private val transactionRunner: TransactionRunner,
    private val documentUploadIntentRepository: DocumentUploadIntentRepository,
    private val reimbursementRepository: ReimbursementRepository,
) : ReimbursementDeclarationPersistence {
    override suspend fun persist(declaration: DocumentedReimbursementDeclaration) {
        transactionRunner.transaction {
            documentUploadIntentRepository.persistAll(declaration.consumedUploadIntents)
            reimbursementRepository.persist(declaration.reimbursement)
        }
    }
}
