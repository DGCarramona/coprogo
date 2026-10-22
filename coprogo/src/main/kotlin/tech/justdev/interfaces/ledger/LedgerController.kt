package tech.justdev.interfaces.ledger

import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Status
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.ledger.CashPoolBalanceSnapshot
import tech.justdev.application.ledger.GetCashPoolBalanceQuery
import tech.justdev.application.ledger.GetCashPoolBalanceUseCase
import tech.justdev.application.ledger.GetGroupBalancesQuery
import tech.justdev.application.ledger.GetGroupBalancesUseCase
import tech.justdev.application.ledger.GetMemberCashPoolSharesQuery
import tech.justdev.application.ledger.GetMemberCashPoolSharesUseCase
import tech.justdev.application.ledger.GetMemberDebtReductionHistoryQuery
import tech.justdev.application.ledger.GetMemberDebtReductionHistoryUseCase
import tech.justdev.application.ledger.GroupBalancesSnapshot
import tech.justdev.application.ledger.GroupMemberBalanceSnapshot
import tech.justdev.application.ledger.GroupMemberCashPoolSharesSnapshot
import tech.justdev.application.ledger.MemberCashPoolShareSnapshot
import tech.justdev.application.ledger.WithdrawFromCashPoolCommand
import tech.justdev.application.ledger.WithdrawFromCashPoolUseCase
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.ApiErrorResponse
import tech.justdev.interfaces.openapi.AuthenticatedApi
import java.time.Instant
import java.util.UUID

@Controller("/api")
@AuthenticatedApi
@Tag(name = "Ledger")
class LedgerController(
    private val authenticatedUserProvider: AuthenticatedUserProvider,
    private val getGroupBalancesUseCase: GetGroupBalancesUseCase,
    private val getCashPoolBalanceUseCase: GetCashPoolBalanceUseCase,
    private val getMemberCashPoolSharesUseCase: GetMemberCashPoolSharesUseCase,
    private val withdrawFromCashPoolUseCase: WithdrawFromCashPoolUseCase,
    private val getMemberDebtReductionHistoryUseCase: GetMemberDebtReductionHistoryUseCase,
) {
    @Get("/groups/{groupId}/balances")
    @Operation(summary = "Get member ledger balances for a group")
    suspend fun getGroupBalances(
        @PathVariable groupId: UUID,
    ): GroupBalancesResponse {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()

        return getGroupBalancesUseCase(
            GetGroupBalancesQuery(
                group = GroupId(groupId),
                requestedBy = authenticatedUser.email,
            ),
        ).toResponse()
    }

    @Get("/groups/{groupId}/cash-pool/balance")
    @Operation(summary = "Get the available common cash-pool balance for a group")
    suspend fun getCashPoolBalance(
        @PathVariable groupId: UUID,
    ): CashPoolBalanceResponse {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()

        return getCashPoolBalanceUseCase(
            GetCashPoolBalanceQuery(
                group = GroupId(groupId),
                requestedBy = authenticatedUser.email,
            ),
        ).toResponse()
    }

    @Get("/groups/{groupId}/cash-pool/shares")
    @Operation(summary = "Get remaining common cash-pool shares by member for a group")
    suspend fun getMemberCashPoolShares(
        @PathVariable groupId: UUID,
    ): GroupMemberCashPoolSharesResponse {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()

        return getMemberCashPoolSharesUseCase(
            GetMemberCashPoolSharesQuery(
                group = GroupId(groupId),
                requestedBy = authenticatedUser.email,
            ),
        ).toResponse()
    }

    @Get("/groups/{groupId}/members/{memberEmail}/debt-reductions")
    @Operation(summary = "Get the operations that reduced a group member's debt")
    suspend fun getMemberDebtReductionHistory(
        @PathVariable groupId: UUID,
        @PathVariable memberEmail: String,
    ): MemberDebtReductionHistoryResponse {
        val group = GroupId(groupId)
        val member = MemberEmail.of(memberEmail)

        return getMemberDebtReductionHistoryUseCase(
            GetMemberDebtReductionHistoryQuery(
                group = group,
                member = member,
                requestedBy = authenticatedUserProvider.currentAuthenticatedUser().email,
            ),
        ).let { reductions -> MemberDebtReductionPresenter(group, member, reductions) }
    }

    @Post("/groups/{groupId}/cash-pool-withdrawals")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(summary = "Record a common cash-pool withdrawal")
    @ApiResponse(responseCode = "204", description = "Cash-pool withdrawal recorded")
    @ApiResponse(
        responseCode = "409",
        description = "Cash-pool balance is insufficient",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
    )
    suspend fun withdrawFromCashPool(
        @PathVariable groupId: UUID,
        @Valid @Body request: CashPoolWithdrawalRequest,
    ) {
        withdrawFromCashPoolUseCase(
            WithdrawFromCashPoolCommand(
                group = GroupId(groupId),
                withdrawnBy = authenticatedUserProvider.currentAuthenticatedUser().email,
                amount = MoneyAmount.ofCents(request.amountInCents),
                occurredAt = request.withdrawnAt,
            ),
        )
    }
}

@Serdeable
data class CashPoolWithdrawalRequest(
    @field:Positive
    val amountInCents: Long,
    val withdrawnAt: Instant,
)

@Serdeable
data class GroupBalancesResponse(
    val group: UUID,
    val balances: List<GroupMemberBalanceResponse>,
)

@Serdeable
data class GroupMemberBalanceResponse(
    val member: String,
    val netAmountInCents: Long,
)

@Serdeable
data class CashPoolBalanceResponse(
    val group: UUID,
    val availableAmountInCents: Long,
)

@Serdeable
data class GroupMemberCashPoolSharesResponse(
    val group: UUID,
    val shares: List<MemberCashPoolShareResponse>,
)

@Serdeable
data class MemberCashPoolShareResponse(
    val member: String,
    val amountInCents: Long,
)

private fun GroupBalancesSnapshot.toResponse(): GroupBalancesResponse =
    GroupBalancesResponse(
        group = group.toPrimitive(),
        balances = balances.map(GroupMemberBalanceSnapshot::toResponse),
    )

private fun GroupMemberBalanceSnapshot.toResponse(): GroupMemberBalanceResponse =
    GroupMemberBalanceResponse(
        member = member,
        netAmountInCents = netAmountInCents,
    )

private fun CashPoolBalanceSnapshot.toResponse(): CashPoolBalanceResponse =
    CashPoolBalanceResponse(
        group = group.toPrimitive(),
        availableAmountInCents = availableAmountInCents,
    )

private fun GroupMemberCashPoolSharesSnapshot.toResponse(): GroupMemberCashPoolSharesResponse =
    GroupMemberCashPoolSharesResponse(
        group = group.toPrimitive(),
        shares = shares.map(MemberCashPoolShareSnapshot::toResponse),
    )

private fun MemberCashPoolShareSnapshot.toResponse(): MemberCashPoolShareResponse =
    MemberCashPoolShareResponse(
        member = member,
        amountInCents = amountInCents,
    )
