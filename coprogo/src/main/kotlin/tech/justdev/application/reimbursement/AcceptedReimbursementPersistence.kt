package tech.justdev.application.reimbursement

import tech.justdev.domain.reimbursement.entity.Reimbursement

interface AcceptedReimbursementPersistence {
    suspend fun persist(reimbursement: Reimbursement)
}
