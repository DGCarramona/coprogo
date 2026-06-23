package tech.justdev.interfaces.ledger

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.ledger.CashPoolBalanceSnapshot
import tech.justdev.application.ledger.GetCashPoolBalanceQuery
import tech.justdev.application.ledger.GetCashPoolBalanceUseCase
import tech.justdev.application.ledger.GetGroupBalancesQuery
import tech.justdev.application.ledger.GetGroupBalancesUseCase
import tech.justdev.application.ledger.GetMemberCashPoolSharesQuery
import tech.justdev.application.ledger.GetMemberCashPoolSharesUseCase
import tech.justdev.application.ledger.GroupBalancesSnapshot
import tech.justdev.application.ledger.GroupMemberBalanceSnapshot
import tech.justdev.application.ledger.GroupMemberCashPoolSharesSnapshot
import tech.justdev.application.ledger.MemberCashPoolShareSnapshot
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.openapi.AuthenticatedApi
import java.util.UUID

@Controller("/api")
@AuthenticatedApi
@Tag(name = "Ledger")
class LedgerController(
    private val authenticatedUserProvider: AuthenticatedUserProvider,
    private val getGroupBalancesUseCase: GetGroupBalancesUseCase,
    private val getCashPoolBalanceUseCase: GetCashPoolBalanceUseCase,
    private val getMemberCashPoolSharesUseCase: GetMemberCashPoolSharesUseCase,
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
}

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
