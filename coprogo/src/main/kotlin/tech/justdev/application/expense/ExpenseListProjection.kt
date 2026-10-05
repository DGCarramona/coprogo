package tech.justdev.application.expense

import tech.justdev.domain.shared.valueobject.GroupId

interface ExpenseListProjection {
    suspend fun findByGroup(group: GroupId): List<ExpenseSnapshot>
}
