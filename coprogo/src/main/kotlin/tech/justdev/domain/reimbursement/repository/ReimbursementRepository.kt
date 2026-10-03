package tech.justdev.domain.reimbursement.repository

import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.shared.valueobject.GroupId

interface ReimbursementRepository {
    suspend fun findByIdAndGroup(
        id: ReimbursementId,
        group: GroupId,
    ): Reimbursement?

    suspend fun findByGroup(group: GroupId): List<Reimbursement>

    suspend fun persist(reimbursement: Reimbursement)
}
