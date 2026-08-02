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
import tech.justdev.application.expense.ExpenseDetailParticipationSnapshot
import tech.justdev.application.expense.ExpenseDetailSnapshot
import tech.justdev.application.expense.ExpenseParticipationDecisionCommand
import tech.justdev.application.expense.ExpenseSnapshot
import tech.justdev.application.expense.GetExpenseDetailQuery
import tech.justdev.application.expense.GetExpenseDetailUseCase
import tech.justdev.application.expense.ListGroupExpensesQuery
import tech.justdev.application.expense.ListGroupExpensesUseCase
import tech.justdev.application.expense.ProposeExpenseCommand
import tech.justdev.application.expense.ProposeExpenseUseCase
import tech.justdev.application.expense.RecordExpenseParticipationDecisionCommand
import tech.justdev.application.expense.RecordExpenseParticipationDecisionUseCase
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
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
    private val getExpenseDetailUseCase: GetExpenseDetailUseCase,
    private val proposeExpenseUseCase: ProposeExpenseUseCase,
    private val recordExpenseParticipationDecisionUseCase: RecordExpenseParticipationDecisionUseCase,
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

    @Get("/groups/{groupId}/expenses/{expenseId}")
    @Operation(summary = "Get expense details")
    suspend fun getExpenseDetail(
        @PathVariable groupId: UUID,
        @PathVariable expenseId: UUID,
    ): ExpenseDetailResponse =
        getExpenseDetailUseCase(
            GetExpenseDetailQuery(
                group = GroupId(groupId),
                id = ExpenseId(expenseId),
                requestedBy = authenticatedUserProvider.currentAuthenticatedUser().email,
            ),
        ).toResponse()

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

    @Post("/groups/{groupId}/expenses/{expenseId}/participation-decisions")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(summary = "Record a participation decision for an expense")
    suspend fun recordParticipationDecision(
        @PathVariable groupId: UUID,
        @PathVariable expenseId: UUID,
        @Body request: ExpenseParticipationDecisionRequest,
    ) {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()
        recordExpenseParticipationDecisionUseCase(
            RecordExpenseParticipationDecisionCommand(
                group = GroupId(groupId),
                id = expenseId,
                member = authenticatedUser.email,
                decision =
                    when (request.decision) {
                        ExpenseParticipationDecisionInput.APPROVE -> ExpenseParticipationDecisionCommand.APPROVE
                        ExpenseParticipationDecisionInput.REFUSE -> ExpenseParticipationDecisionCommand.REFUSE
                    },
                decidedAt = Instant.now(),
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
data class ExpenseParticipationDecisionRequest(
    val decision: ExpenseParticipationDecisionInput,
)

@Serdeable
enum class ExpenseParticipationDecisionInput {
    APPROVE,
    REFUSE,
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

@Serdeable
data class ExpenseDetailParticipationResponse(
    val member: String,
    val amountCents: Long,
    val status: String,
)

@Serdeable
data class ExpenseDetailResponse(
    val id: UUID,
    val title: String,
    val createdBy: String,
    val totalAmountCents: Long,
    val createdAt: Instant,
    val status: String,
    val participations: List<ExpenseDetailParticipationResponse>,
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

private fun ExpenseDetailSnapshot.toResponse(): ExpenseDetailResponse =
    ExpenseDetailResponse(
        id = id.toPrimitive(),
        title = title,
        createdBy = createdBy.toPrimitive(),
        totalAmountCents = totalAmount.inCents(),
        createdAt = createdAt,
        status = status.name,
        participations = participations.map(ExpenseDetailParticipationSnapshot::toResponse),
    )

private fun ExpenseDetailParticipationSnapshot.toResponse(): ExpenseDetailParticipationResponse =
    ExpenseDetailParticipationResponse(
        member = member.toPrimitive(),
        amountCents = amount.inCents(),
        status = status.toResponseStatus(),
    )

private fun ExpenseParticipationStatus.toResponseStatus(): String =
    when (this) {
        ExpenseParticipationStatus.Pending -> "PENDING"
        is ExpenseParticipationStatus.Approved -> "APPROVED"
        is ExpenseParticipationStatus.Refused -> "REFUSED"
    }
