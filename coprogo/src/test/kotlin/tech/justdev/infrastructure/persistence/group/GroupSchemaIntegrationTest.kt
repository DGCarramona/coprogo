package tech.justdev.infrastructure.persistence.group

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@PostgresMicronautTest
class GroupSchemaIntegrationTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Nested
    inner class NameColumn {
        @Test
        fun `stores names as nullable varchar with a one hundred character limit`() {
            dataSource.connection.use { connection ->
                connection.metaData.getColumns(null, "public", "groups", "name").use { columns ->
                    check(columns.next())
                    assertEquals(
                        listOf("varchar", "100", "YES"),
                        listOf(columns.getString("TYPE_NAME"), columns.getString("COLUMN_SIZE"), columns.getString("IS_NULLABLE")),
                    )
                }
            }
        }

        @Test
        fun `preserves one hundred character names and allows legacy unnamed groups`() =
            runTest {
                val group = seedGroup("valid")
                val name = "é".repeat(100)

                updateName(group, name)
                assertEquals(name, groupRepository.findById(GroupId(group))?.name?.toPrimitive())

                updateName(group, null)
                assertEquals(null, groupRepository.findById(GroupId(group))?.name)
            }

        @ParameterizedTest
        @ValueSource(strings = ["", " ", " Groupe", "Groupe "])
        fun `rejects blank and untrimmed names`(name: String) =
            runTest {
                val group = seedGroup("invalid-${name.hashCode()}")

                val error = assertThrows<SQLException> { updateName(group, name) }

                assertEquals("23514", error.sqlState)
            }

        @Test
        fun `rejects names over one hundred characters through the column type`() =
            runTest {
                val group = seedGroup("too-long")

                val error = assertThrows<SQLException> { updateName(group, "a".repeat(101)) }

                assertEquals("22001", error.sqlState)
            }
    }

    private suspend fun seedGroup(seed: String): UUID {
        val owner = memberEmail("group-name-schema-$seed")
        memberRepository.persist(Member(owner, Instant.EPOCH))
        return Group
            .create(groupId("$seed-group-name-schema"), owner, Instant.EPOCH)
            .also { groupRepository.persist(it) }
            .id
            .toPrimitive()
    }

    private fun updateName(
        group: UUID,
        name: String?,
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE groups SET name = ? WHERE id = ?").use { statement ->
                statement.setString(1, name)
                statement.setObject(2, group)
                statement.executeUpdate()
            }
        }
    }
}
