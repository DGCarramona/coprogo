package tech.justdev.application.group

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.valueobject.GroupName
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class GetGroupUseCaseTest {
    @Test
    fun `invoke should return members with their addition attribution`() =
        runTest {
            val createdAt = Instant.parse("2026-04-14T08:30:00Z")
            val joinedAt = Instant.parse("2026-04-14T09:00:00Z")
            val group =
                Group
                    .create(
                        id = groupId("group-1"),
                        createdBy = memberEmail("alice"),
                        createdAt = createdAt,
                        name = GroupName.of("Appartement familial"),
                    ).addMember(
                        member = memberEmail("bob"),
                        joinedAt = joinedAt,
                        addedBy = memberEmail("alice"),
                    )
            val useCase =
                GetGroupUseCase(
                    groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(group))),
                )

            assertEquals(
                GroupSnapshot(
                    group = groupUuid("group-1"),
                    createdBy = "alice@example.com",
                    name = "Appartement familial",
                    createdAt = createdAt,
                    members =
                        listOf(
                            GroupMemberSnapshot("alice@example.com", createdAt, "alice@example.com"),
                            GroupMemberSnapshot("bob@example.com", joinedAt, "alice@example.com"),
                        ),
                ),
                useCase(
                    GetGroupQuery(
                        group = groupUuid("group-1"),
                        requestedBy = memberEmail("bob"),
                    ),
                ),
            )
        }

    @Test
    fun `invoke should fail when the requester is not part of the group`() {
        val useCase =
            GetGroupUseCase(
                groupAccessPolicy =
                    GroupAccessPolicy(
                        InMemoryGroupRepository(
                            groups =
                                listOf(
                                    Group.create(
                                        id = groupId("group-1"),
                                        createdBy = memberEmail("alice"),
                                        createdAt = Instant.parse("2026-04-14T08:30:00Z"),
                                    ),
                                ),
                        ),
                    ),
            )

        val error =
            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(
                        GetGroupQuery(
                            group = groupUuid("group-1"),
                            requestedBy = memberEmail("bob"),
                        ),
                    )
                }
            }

        assertEquals(
            "member bob@example.com is not part of group ${groupUuid("group-1")}",
            error.message,
        )
    }
}
