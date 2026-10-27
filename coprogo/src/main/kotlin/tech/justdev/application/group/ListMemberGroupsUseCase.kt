package tech.justdev.application.group

import jakarta.inject.Singleton
import tech.justdev.domain.group.valueobject.MemberEmail
import java.time.Instant
import java.util.UUID

data class ListMemberGroupsQuery(
    val requestedBy: MemberEmail,
)

data class MemberGroupSnapshot(
    val group: UUID,
    val createdBy: String,
    val createdAt: Instant,
    val name: String? = null,
    val canRename: Boolean = false,
)

interface ListMemberGroupsUseCase {
    suspend operator fun invoke(query: ListMemberGroupsQuery): List<MemberGroupSnapshot>
}

@Singleton
class ListMemberGroupsUseCaseImpl(
    private val projection: MemberGroupsProjection,
) : ListMemberGroupsUseCase {
    override suspend fun invoke(query: ListMemberGroupsQuery): List<MemberGroupSnapshot> =
        projection
            .findByMember(query.requestedBy)
            .map { group -> group.copy(canRename = group.createdBy == query.requestedBy.toPrimitive()) }
}
