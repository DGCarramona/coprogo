package tech.justdev.interfaces.ledger

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import tech.justdev.application.ledger.CashPoolHistorySnapshot
import tech.justdev.application.ledger.CashPoolIncomeSnapshot
import tech.justdev.application.ledger.CashPoolOperationSnapshot
import tech.justdev.application.ledger.CashPoolWithdrawalSnapshot
import java.time.Instant
import java.util.UUID

object CashPoolHistoryPresenter {
    operator fun invoke(snapshot: CashPoolHistorySnapshot): CashPoolHistoryResponse =
        CashPoolHistoryResponse(
            group = snapshot.group,
            operations = snapshot.operations.map(CashPoolOperationSnapshot::toResponse),
        )
}

@Serdeable
data class CashPoolHistoryResponse(
    val group: UUID,
    val operations: List<CashPoolOperationResponse>,
)

@Serdeable
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = CashPoolIncomeResponse::class, name = "INCOME"),
    JsonSubTypes.Type(value = CashPoolWithdrawalResponse::class, name = "WITHDRAWAL"),
)
sealed interface CashPoolOperationResponse {
    val type: String
    val operation: UUID
    val occurredAt: Instant
}

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = false)
data class CashPoolIncomeResponse(
    override val operation: UUID,
    override val occurredAt: Instant,
    val amountInCents: Long,
    val allocations: List<CashPoolIncomeAllocationResponse>,
) : CashPoolOperationResponse {
    @get:Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = ["INCOME"])
    override val type: String
        get() = "INCOME"
}

@Serdeable
data class CashPoolIncomeAllocationResponse(
    val member: String,
    val amountInCents: Long,
)

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = false)
data class CashPoolWithdrawalResponse(
    override val operation: UUID,
    override val occurredAt: Instant,
    val withdrawnBy: String,
    val amountInCents: Long,
    val ownRevenueShareConsumedInCents: Long,
) : CashPoolOperationResponse {
    @get:Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = ["WITHDRAWAL"])
    override val type: String
        get() = "WITHDRAWAL"
}

private fun CashPoolOperationSnapshot.toResponse(): CashPoolOperationResponse =
    when (this) {
        is CashPoolIncomeSnapshot ->
            CashPoolIncomeResponse(
                operation = operation,
                occurredAt = occurredAt,
                amountInCents = amountInCents,
                allocations =
                    allocations.map { allocation ->
                        CashPoolIncomeAllocationResponse(
                            member = allocation.member,
                            amountInCents = allocation.amountInCents,
                        )
                    },
            )

        is CashPoolWithdrawalSnapshot ->
            CashPoolWithdrawalResponse(
                operation = operation,
                occurredAt = occurredAt,
                withdrawnBy = withdrawnBy,
                amountInCents = amountInCents,
                ownRevenueShareConsumedInCents = ownRevenueShareConsumedInCents,
            )
    }
