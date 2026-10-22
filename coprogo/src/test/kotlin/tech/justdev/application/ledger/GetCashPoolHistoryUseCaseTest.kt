package tech.justdev.application.ledger

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant

class GetCashPoolHistoryUseCaseTest {
    @Test
    fun `invoke returns group cash-pool operations from newest to oldest`() =
        runTest {
            val income =
                CashPoolIncomeSnapshot(
                    operation = testUuid("income"),
                    occurredAt = Instant.parse("2026-10-01T10:00:00Z"),
                    amountInCents = 10_000,
                    allocations =
                        listOf(
                            CashPoolIncomeAllocationSnapshot("alice@example.com", 6_000),
                            CashPoolIncomeAllocationSnapshot("bob@example.com", 4_000),
                        ),
                )
            val withdrawal =
                CashPoolWithdrawalSnapshot(
                    operation = testUuid("withdrawal"),
                    occurredAt = Instant.parse("2026-10-02T10:00:00Z"),
                    withdrawnBy = "alice@example.com",
                    amountInCents = 7_000,
                    ownRevenueShareConsumedInCents = 6_000,
                )
            val projection = InMemoryCashPoolHistoryProjection(listOf(income, withdrawal))

            val result = useCase(projection)(GetCashPoolHistoryQuery(GROUP, ALICE))

            assertEquals(
                CashPoolHistorySnapshot(GROUP.toPrimitive(), listOf(withdrawal, income)),
                result,
            )
        }

    @Test
    fun `invoke checks requester access before reading history`() {
        val projection = InMemoryCashPoolHistoryProjection()

        assertThrows<GroupAccessDeniedException> {
            runTest {
                useCase(projection)(GetCashPoolHistoryQuery(GROUP, memberEmail("outsider")))
            }
        }

        assertEquals(0, projection.findAttempts)
    }

    private fun useCase(projection: CashPoolHistoryProjection): GetCashPoolHistoryUseCase =
        GetCashPoolHistoryUseCaseImpl(
            groupAccessPolicy =
                GroupAccessPolicy(
                    InMemoryGroupRepository(
                        listOf(Group.create(GROUP, ALICE, Instant.parse("2026-01-01T08:00:00Z"))),
                    ),
                ),
            cashPoolHistoryProjection = projection,
        )

    private class InMemoryCashPoolHistoryProjection(
        private val operations: List<CashPoolOperationSnapshot> = emptyList(),
    ) : CashPoolHistoryProjection {
        var findAttempts = 0

        override suspend fun findByGroup(group: GroupId): List<CashPoolOperationSnapshot> {
            findAttempts += 1
            return operations
        }
    }

    private companion object {
        val GROUP = groupId("cash-pool-history")
        val ALICE = memberEmail("alice")
    }
}
