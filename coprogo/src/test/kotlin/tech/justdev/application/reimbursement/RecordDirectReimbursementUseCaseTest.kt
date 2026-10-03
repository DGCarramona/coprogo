package tech.justdev.application.reimbursement

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.application.support.InMemoryReimbursementRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant

class RecordDirectReimbursementUseCaseTest {
    @Nested
    inner class Invoke {
        @Test
        fun `should persist an accepted reimbursement recorded by its receiver`() =
            runTest {
                val repository = InMemoryReimbursementRepository()
                val useCase = useCase(repository)

                useCase(command())

                assertEquals(
                    listOf(
                        ReimbursementSnapshot(
                            id = testUuid("reimbursement:direct"),
                            paidBy = "bob@example.com",
                            receivedBy = "alice@example.com",
                            amountInCents = 4_200,
                            reimbursedAt = REIMBURSED_AT,
                            declaredBy = "alice@example.com",
                            declaredAt = DECLARED_AT,
                            status = ReimbursementStatus.Accepted(DECLARED_AT),
                        ),
                    ),
                    repository.findByGroup(GROUP).map { reimbursement -> reimbursement.toSnapshot() },
                )
            }

        @Test
        fun `should reject a recorder outside the group before generating an id`() {
            val repository = InMemoryReimbursementRepository()

            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(repository, failingIdGenerator())(
                        command(recordedBy = "outsider"),
                    )
                }
            }
            assertEquals(emptyList<Reimbursement>(), repository.findAll())
        }

        @Test
        fun `should reject a payer outside the group before generating an id`() {
            val repository = InMemoryReimbursementRepository()

            val error =
                assertThrows<IllegalArgumentException> {
                    runTest {
                        useCase(repository, failingIdGenerator())(
                            command(paidBy = "outsider"),
                        )
                    }
                }

            assertEquals(
                "reimbursement payer outsider@example.com is not part of group ${GROUP.toPrimitive()}",
                error.message,
            )
            assertEquals(emptyList<Reimbursement>(), repository.findAll())
        }
    }

    private fun useCase(
        repository: InMemoryReimbursementRepository,
        idGenerator: ReimbursementIdGenerator =
            ReimbursementIdGenerator {
                ReimbursementId(testUuid("reimbursement:direct"))
            },
    ): RecordDirectReimbursementUseCase =
        RecordDirectReimbursementUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(group()))),
            reimbursementIdGenerator = idGenerator,
            reimbursementRepository = repository,
        )

    private fun failingIdGenerator(): ReimbursementIdGenerator =
        ReimbursementIdGenerator { throw AssertionError("reimbursement id should not be generated") }

    private fun command(
        paidBy: String = "bob",
        recordedBy: String = "alice",
    ) = RecordDirectReimbursementCommand(
        group = GROUP,
        paidBy = memberEmail(paidBy),
        recordedBy = memberEmail(recordedBy),
        amountInCents = 4_200,
        reimbursedAt = REIMBURSED_AT,
        recordedAt = DECLARED_AT,
    )

    private fun group(): Group =
        Group
            .create(
                id = GROUP,
                createdBy = memberEmail("alice"),
                createdAt = Instant.parse("2026-04-01T08:00:00Z"),
            ).addMember(
                member = memberEmail("bob"),
                joinedAt = Instant.parse("2026-04-01T09:00:00Z"),
            )

    private fun Reimbursement.toSnapshot() =
        ReimbursementSnapshot(
            id = id.toPrimitive(),
            paidBy = paidBy.toPrimitive(),
            receivedBy = receivedBy.toPrimitive(),
            amountInCents = amount.inCents(),
            reimbursedAt = reimbursedAt,
            declaredBy = declaredBy.toPrimitive(),
            declaredAt = declaredAt,
            status = status,
        )

    private data class ReimbursementSnapshot(
        val id: java.util.UUID,
        val paidBy: String,
        val receivedBy: String,
        val amountInCents: Long,
        val reimbursedAt: Instant,
        val declaredBy: String,
        val declaredAt: Instant,
        val status: ReimbursementStatus,
    )

    private companion object {
        val GROUP = groupId("direct-reimbursement")
        val REIMBURSED_AT: Instant = Instant.parse("2026-04-02T10:00:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-04-03T10:00:00Z")
    }
}
