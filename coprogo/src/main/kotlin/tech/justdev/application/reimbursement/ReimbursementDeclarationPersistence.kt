package tech.justdev.application.reimbursement

interface ReimbursementDeclarationPersistence {
    suspend fun persist(declaration: DocumentedReimbursementDeclaration)
}
