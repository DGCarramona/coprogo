package tech.justdev.interfaces.expense

import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Status
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.expense.EqualSplitExpenseAllocationCommand
import tech.justdev.application.expense.ExpenseSnapshot
import tech.justdev.application.expense.ListGroupExpensesQuery
import tech.justdev.application.expense.ListGroupExpensesUseCase
import tech.justdev.application.expense.ProposeExpenseCommand
import tech.justdev.application.expense.ProposeExpenseUseCase
import tech.justdev.domain.group.valueobject.MemberEmail
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
    private val proposeExpenseUseCase: ProposeExpenseUseCase,
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

    @Post("/groups/{groupId}/expenses")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(summary = "Propose an expense with an equal split")
    suspend fun proposeEqualSplitExpense(
        @PathVariable groupId: UUID,
        @Valid @Body request: ProposeEqualSplitExpenseRequest,
    ) {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()
        val participants = request.participants.map(MemberEmail::of).toSet()

        proposeExpenseUseCase(
            ProposeExpenseCommand(
                group = GroupId(groupId),
                title = request.title,
                createdBy = authenticatedUser.email,
                totalAmountInCents = request.totalAmountInCents,
                createdAt = Instant.now(),
                allocation = EqualSplitExpenseAllocationCommand(participants),
            ),
        )
    }
}

@Serdeable
data class ProposeEqualSplitExpenseRequest(
    @field:NotBlank
    val title: String,
    @field:Positive
    val totalAmountInCents: Long,
    @field:NotEmpty
    val participants: Set<String>,
)

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
