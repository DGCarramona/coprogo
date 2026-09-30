package tech.justdev.pitest

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pitest.classinfo.ClassName
import org.pitest.mutationtest.engine.Location
import org.pitest.mutationtest.engine.MutationDetails
import org.pitest.mutationtest.engine.MutationIdentifier

class CoprogoEquivalentKotlinMutationFilterTest {
    private val filter = CoprogoEquivalentKotlinMutationFilter(EquivalentMutationWhitelist.load())

    @Test
    fun `filters only the known equivalent Kotlin mutations`() {
        assertTrue(
            filter.isEquivalent(
                className = "tech.justdev.application.expense.ProposeExpenseUseCaseImpl",
                method = "invoke",
                mutator = "org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator",
                instructionIndex = 463,
            ),
        )
        assertTrue(
            filter.isEquivalent(
                className = "tech.justdev.application.group.InviteMemberToGroupUseCase",
                method = "invoke",
                mutator = "org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator",
                instructionIndex = 195,
            ),
        )
        assertTrue(
            filter.isEquivalent(
                className = "tech.justdev.domain.expense.entity.Expense\$Companion",
                method = "allocateEqualSplitWithCaps-ud-FMjM",
                mutator = "org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator",
                instructionIndex = 114,
            ),
        )
        assertTrue(
            filter.isEquivalent(
                className = "tech.justdev.domain.expense.entity.Expense\$Companion",
                method = "proposeCumulativeTiers-EYqWlrs",
                mutator = "org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator",
                instructionIndex = 142,
            ),
        )

        assertFalse(
            filter.isEquivalent(
                className = "tech.justdev.application.expense.ProposeExpenseUseCaseImpl",
                method = "invoke",
                mutator = "org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator",
                instructionIndex = 464,
            ),
        )
        assertFalse(
            filter.isEquivalent(
                className = "tech.justdev.application.expense.ProposeExpenseUseCaseImpl",
                method = "anotherMethod",
                mutator = "org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator",
                instructionIndex = 463,
            ),
        )
    }

    @Test
    fun `filters using the mutation index reported by PIT`() {
        val mutation =
            MutationDetails(
                MutationIdentifier(
                    Location(
                        ClassName.fromString("tech.justdev.application.expense.ProposeExpenseUseCaseImpl"),
                        "invoke",
                        "()V",
                    ),
                    463,
                    "org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator",
                ),
                "ProposeExpenseUseCase.kt",
                "changed conditional boundary",
                209,
                86,
            )

        val remaining = filter.intercept(listOf(mutation), null)

        assertTrue(remaining.isEmpty())
    }
}
