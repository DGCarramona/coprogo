package tech.justdev.interfaces.expense

import io.micronaut.json.JsonMapper
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.expense.CumulativeExpenseTierCommand
import tech.justdev.application.expense.CumulativeTiersExpenseAllocationCommand
import tech.justdev.application.expense.CustomExpenseAllocationCommand
import tech.justdev.application.expense.CustomExpenseParticipationCommand
import tech.justdev.application.expense.EqualSplitExpenseAllocationCommand
import tech.justdev.application.expense.EqualSplitWithCapsExpenseAllocationCommand
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.testsupport.NoDbMicronautTest

@NoDbMicronautTest
class ExpenseProposalRequestTest {
    @Inject
    lateinit var jsonMapper: JsonMapper

    @Nested
    inner class Deserialization {
        @Test
        fun `should deserialize equal allocation by type`() {
            val request =
                readRequest(
                    """
                    {
                      "title": "Boiler repair",
                      "totalAmountInCents": 100,
                      "allocation": {
                        "type": "EQUAL",
                        "participants": ["alice@example.com", "bob@example.com"]
                      }
                    }
                    """.trimIndent(),
                )

            assertInstanceOf(EqualExpenseAllocationRequest::class.java, request.allocation)
            assertEquals(setOf("alice@example.com", "bob@example.com"), (request.allocation as EqualExpenseAllocationRequest).participants)
        }

        @Test
        fun `should deserialize equal with caps allocation by type`() {
            val request =
                readRequest(
                    """
                    {
                      "title": "Boiler repair",
                      "totalAmountInCents": 100,
                      "allocation": {
                        "type": "EQUAL_WITH_CAPS",
                        "participants": ["alice@example.com", "bob@example.com"],
                        "caps": [{"member": "bob@example.com", "maximumAmountInCents": 20}]
                      }
                    }
                    """.trimIndent(),
                )

            assertEquals(
                EqualWithCapsExpenseAllocationRequest(
                    participants = setOf("alice@example.com", "bob@example.com"),
                    caps = listOf(ExpenseAllocationCapRequest("bob@example.com", 20)),
                ),
                request.allocation,
            )
        }

        @Test
        fun `should deserialize cumulative tiers allocation by type`() {
            val request =
                readRequest(
                    """
                    {
                      "title": "Boiler repair",
                      "totalAmountInCents": 100,
                      "allocation": {
                        "type": "CUMULATIVE_TIERS",
                        "tiers": [
                          {"upToAmountInCents": 40, "participants": ["alice@example.com"]},
                          {"upToAmountInCents": 100, "participants": ["alice@example.com", "bob@example.com"]}
                        ]
                      }
                    }
                    """.trimIndent(),
                )

            assertEquals(
                CumulativeTiersExpenseAllocationRequest(
                    tiers =
                        listOf(
                            CumulativeExpenseTierRequest(40, setOf("alice@example.com")),
                            CumulativeExpenseTierRequest(100, setOf("alice@example.com", "bob@example.com")),
                        ),
                ),
                request.allocation,
            )
        }

        @Test
        fun `should deserialize custom allocation by type`() {
            val request =
                readRequest(
                    """
                    {
                      "title": "Boiler repair",
                      "totalAmountInCents": 100,
                      "allocation": {
                        "type": "CUSTOM",
                        "participations": [
                          {"member": "alice@example.com", "amountInCents": 60},
                          {"member": "bob@example.com", "amountInCents": 40}
                        ]
                      }
                    }
                    """.trimIndent(),
                )

            assertEquals(
                CustomExpenseAllocationRequest(
                    participations =
                        listOf(
                            CustomExpenseParticipationRequest("alice@example.com", 60),
                            CustomExpenseParticipationRequest("bob@example.com", 40),
                        ),
                ),
                request.allocation,
            )
        }

        @Test
        fun `should reject an allocation payload tagged with an incoherent type`() {
            assertThrows<Exception> {
                readRequest(
                    """
                    {
                      "title": "Boiler repair",
                      "totalAmountInCents": 100,
                      "allocation": {
                        "type": "CUSTOM",
                        "participants": ["alice@example.com", "bob@example.com"]
                      }
                    }
                    """.trimIndent(),
                )
            }
        }
    }

    @Nested
    inner class Serialization {
        @Test
        fun `should serialize exactly one allocation type discriminant`() {
            val json =
                jsonMapper.writeValueAsString(
                    proposal(EqualExpenseAllocationRequest(setOf("alice@example.com", "bob@example.com"))),
                )

            assertEquals(1, Regex("\\\"type\\\"").findAll(json).count())
            assertTrue(Regex("\\\"type\\\"\\s*:\\s*\\\"EQUAL\\\"").containsMatchIn(json))
        }
    }

    @Nested
    inner class Mapping {
        @Test
        fun `should map equal allocation and normalize emails`() {
            assertEquals(
                EqualSplitExpenseAllocationCommand(
                    participants = setOf(MemberEmail.of("alice@example.com"), MemberEmail.of("bob@example.com")),
                ),
                proposal(EqualExpenseAllocationRequest(setOf(" Alice@Example.com ", "bob@example.com"))).allocation.toCommand(),
            )
        }

        @Test
        fun `should map equal with caps allocation and normalize emails`() {
            assertEquals(
                EqualSplitWithCapsExpenseAllocationCommand(
                    participants = setOf(MemberEmail.of("alice@example.com"), MemberEmail.of("bob@example.com")),
                    capsInCentsByMember = mapOf(MemberEmail.of("bob@example.com") to 20),
                ),
                proposal(
                    EqualWithCapsExpenseAllocationRequest(
                        participants = setOf(" Alice@Example.com ", "bob@example.com"),
                        caps = listOf(ExpenseAllocationCapRequest(" BOB@example.com ", 20)),
                    ),
                ).allocation.toCommand(),
            )
        }

        @Test
        fun `should map cumulative tiers allocation and normalize emails`() {
            assertEquals(
                CumulativeTiersExpenseAllocationCommand(
                    tiers =
                        listOf(
                            CumulativeExpenseTierCommand(40, setOf(MemberEmail.of("alice@example.com"))),
                            CumulativeExpenseTierCommand(
                                100,
                                setOf(MemberEmail.of("alice@example.com"), MemberEmail.of("bob@example.com")),
                            ),
                        ),
                ),
                proposal(
                    CumulativeTiersExpenseAllocationRequest(
                        tiers =
                            listOf(
                                CumulativeExpenseTierRequest(40, setOf(" Alice@Example.com ")),
                                CumulativeExpenseTierRequest(100, setOf("alice@example.com", " BOB@example.com ")),
                            ),
                    ),
                ).allocation.toCommand(),
            )
        }

        @Test
        fun `should map custom allocation and normalize emails`() {
            assertEquals(
                CustomExpenseAllocationCommand(
                    participations =
                        setOf(
                            CustomExpenseParticipationCommand(MemberEmail.of("alice@example.com"), 60),
                            CustomExpenseParticipationCommand(MemberEmail.of("bob@example.com"), 40),
                        ),
                ),
                proposal(
                    CustomExpenseAllocationRequest(
                        participations =
                            listOf(
                                CustomExpenseParticipationRequest(" Alice@Example.com ", 60),
                                CustomExpenseParticipationRequest(" BOB@example.com ", 40),
                            ),
                    ),
                ).allocation.toCommand(),
            )
        }

        @Test
        fun `should reject duplicate cap members after email normalization`() {
            val request =
                proposal(
                    EqualWithCapsExpenseAllocationRequest(
                        participants = setOf("alice@example.com", "bob@example.com"),
                        caps =
                            listOf(
                                ExpenseAllocationCapRequest(" Bob@Example.com ", 20),
                                ExpenseAllocationCapRequest("bob@example.com", 30),
                            ),
                    ),
                )

            val error = assertThrows<IllegalArgumentException> { request.allocation.toCommand() }

            assertEquals("caps must contain unique members", error.message)
        }

        @Test
        fun `should reject an invalid member email`() {
            val request = proposal(EqualExpenseAllocationRequest(setOf("not-an-email")))

            assertThrows<IllegalArgumentException> { request.allocation.toCommand() }
        }
    }

    private fun readRequest(json: String): ProposeExpenseRequest =
        requireNotNull(jsonMapper.readValue(json, ProposeExpenseRequest::class.java))

    private fun proposal(allocation: ExpenseAllocationRequest): ProposeExpenseRequest =
        ProposeExpenseRequest(
            title = "Boiler repair",
            totalAmountInCents = 100,
            allocation = allocation,
        )
}
