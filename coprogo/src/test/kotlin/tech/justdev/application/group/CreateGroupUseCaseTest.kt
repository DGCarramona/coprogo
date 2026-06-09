package tech.justdev.application.group

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.application.support.InMemoryMemberRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.testsupport.FixedGroupIdGenerator
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class CreateGroupUseCaseTest {
    @Test
    fun `invoke should create and persist a group for a registered member`() {
        runTest {
            val memberRepository =
                InMemoryMemberRepository(
                    members =
                        listOf(
                            Member(
                                email = memberEmail("alice"),
                                createdAt = Instant.parse("2026-04-14T08:00:00Z"),
                            ),
                        ),
                )
            val groupRepository = InMemoryGroupRepository()
            val useCase =
                CreateGroupUseCase(
                    memberRepository = memberRepository,
                    groupRepository = groupRepository,
                    groupIdGenerator = FixedGroupIdGenerator(listOf(groupId("group-1"))),
                )

            val result =
                useCase(
                    CreateGroupCommand(
                        createdBy = memberEmail("alice"),
                        createdAt = Instant.parse("2026-04-14T09:00:00Z"),
                    ),
                )

            assertEquals(groupUuid("group-1"), result.group)
            assertEquals(
                Group.create(
                    id = groupId("group-1"),
                    createdBy = memberEmail("alice"),
                    createdAt = Instant.parse("2026-04-14T09:00:00Z"),
                ),
                groupRepository.findById(groupId("group-1")),
            )
        }
    }

    @Test
    fun `invoke should auto-register the creator when not present yet`() {
        runTest {
            val memberRepository = InMemoryMemberRepository()
            val groupRepository = InMemoryGroupRepository()
            val useCase =
                CreateGroupUseCase(
                    memberRepository = memberRepository,
                    groupRepository = groupRepository,
                    groupIdGenerator = FixedGroupIdGenerator(listOf(groupId("group-1"))),
                )

            useCase(
                CreateGroupCommand(
                    createdBy = memberEmail("alice"),
                    createdAt = Instant.parse("2026-04-14T09:00:00Z"),
                ),
            )

            assertEquals(
                Member(
                    email = memberEmail("alice"),
                    createdAt = Instant.parse("2026-04-14T09:00:00Z"),
                ),
                memberRepository.findByEmail(memberEmail("alice")),
            )
        }
    }
}
