package tech.justdev.interfaces.expense

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.expense.ExpenseSnapshot
import tech.justdev.application.expense.ListGroupExpensesQuery
import tech.justdev.application.expense.ListGroupExpensesUseCase
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.openapi.AuthenticatedApi
import java.time.Instant
import java.util.UUID

@Controller("/api")
@AuthenticatedApi
@Tag(name = "Expenses")
class ExpenseController(
    private val authenticatedUserProvider: AuthenticatedUserProvider,
    private val listGroupExpensesUseCase: ListGroupExpensesUseCase,
) {
    @Get("/groups/{groupId}/expenses")
    @Operation(summary = "List expenses for a group")
    suspend fun listGroupExpenses(
        @PathVariable groupId: UUID,
    ): List<ExpenseResponse> =
        listGroupExpensesUseCase(
            ListGroupExpensesQuery(
                group = GroupId(groupId),
                requestedBy = authenticatedUserProvider.currentAuthenticatedUser().email,
            ),
        ).map(ExpenseSnapshot::toResponse)
}

@Serdeable
data class ExpenseResponse(
    val id: UUID,
    val title: String,
    val createdBy: String,
    val totalAmountCents: Long,
    val createdAt: Instant,
    val status: String,
)

private fun ExpenseSnapshot.toResponse(): ExpenseResponse =
    ExpenseResponse(
        id = id,
        title = title,
        createdBy = createdBy,
        totalAmountCents = totalAmountCents,
        createdAt = createdAt,
        status = status,
    )
