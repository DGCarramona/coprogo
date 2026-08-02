package tech.justdev.interfaces.expense

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import tech.justdev.application.expense.CumulativeExpenseTierCommand
import tech.justdev.application.expense.CumulativeTiersExpenseAllocationCommand
import tech.justdev.application.expense.CustomExpenseAllocationCommand
import tech.justdev.application.expense.CustomExpenseParticipationCommand
import tech.justdev.application.expense.EqualSplitExpenseAllocationCommand
import tech.justdev.application.expense.EqualSplitWithCapsExpenseAllocationCommand
import tech.justdev.application.expense.ExpenseAllocationCommand
import tech.justdev.domain.group.valueobject.MemberEmail

@Serdeable
data class ProposeExpenseRequest(
    @field:NotBlank
    val title: String,
    @field:Positive
    val totalAmountInCents: Long,
    @field:Valid
    val allocation: ExpenseAllocationRequest,
)

@Serdeable
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = EqualExpenseAllocationRequest::class, name = "EQUAL"),
    JsonSubTypes.Type(value = EqualWithCapsExpenseAllocationRequest::class, name = "EQUAL_WITH_CAPS"),
    JsonSubTypes.Type(value = CumulativeTiersExpenseAllocationRequest::class, name = "CUMULATIVE_TIERS"),
    JsonSubTypes.Type(value = CustomExpenseAllocationRequest::class, name = "CUSTOM"),
)
sealed interface ExpenseAllocationRequest

@Serdeable
data class EqualExpenseAllocationRequest(
    @field:NotEmpty
    val participants: Set<
        @NotBlank @Email
        String,
    >,
) : ExpenseAllocationRequest

@Serdeable
data class EqualWithCapsExpenseAllocationRequest(
    @field:NotEmpty
    val participants: Set<
        @NotBlank @Email
        String,
    >,
    @field:NotEmpty
    @field:Valid
    val caps: List<ExpenseAllocationCapRequest>,
) : ExpenseAllocationRequest

@Serdeable
data class ExpenseAllocationCapRequest(
    @field:NotBlank
    @field:Email
    val member: String,
    @field:Positive
    val maximumAmountInCents: Long,
)

@Serdeable
data class CumulativeTiersExpenseAllocationRequest(
    @field:NotEmpty
    @field:Valid
    val tiers: List<CumulativeExpenseTierRequest>,
) : ExpenseAllocationRequest

@Serdeable
data class CumulativeExpenseTierRequest(
    @field:Positive
    val upToAmountInCents: Long,
    @field:NotEmpty
    val participants: Set<
        @NotBlank @Email
        String,
    >,
)

@Serdeable
data class CustomExpenseAllocationRequest(
    @field:NotEmpty
    @field:Valid
    val participations: List<CustomExpenseParticipationRequest>,
) : ExpenseAllocationRequest

@Serdeable
data class CustomExpenseParticipationRequest(
    @field:NotBlank
    @field:Email
    val member: String,
    @field:Positive
    val amountInCents: Long,
)

fun ExpenseAllocationRequest.toCommand(): ExpenseAllocationCommand =
    when (this) {
        is EqualExpenseAllocationRequest -> {
            EqualSplitExpenseAllocationCommand(
                participants = participants.map(MemberEmail::of).toSet(),
            )
        }

        is EqualWithCapsExpenseAllocationRequest -> {
            val normalizedCaps = caps.map { cap -> MemberEmail.of(cap.member) to cap.maximumAmountInCents }
            require(normalizedCaps.map { (member) -> member }.toSet().size == normalizedCaps.size) {
                "caps must contain unique members"
            }

            EqualSplitWithCapsExpenseAllocationCommand(
                participants = participants.map(MemberEmail::of).toSet(),
                capsInCentsByMember = normalizedCaps.toMap(),
            )
        }

        is CumulativeTiersExpenseAllocationRequest -> {
            CumulativeTiersExpenseAllocationCommand(
                tiers =
                    tiers.map { tier ->
                        CumulativeExpenseTierCommand(
                            upToAmountInCents = tier.upToAmountInCents,
                            participants = tier.participants.map(MemberEmail::of).toSet(),
                        )
                    },
            )
        }

        is CustomExpenseAllocationRequest -> {
            CustomExpenseAllocationCommand(
                participations =
                    participations
                        .map { participation ->
                            CustomExpenseParticipationCommand(
                                member = MemberEmail.of(participation.member),
                                amountInCents = participation.amountInCents,
                            )
                        }.toSet(),
            )
        }
    }
