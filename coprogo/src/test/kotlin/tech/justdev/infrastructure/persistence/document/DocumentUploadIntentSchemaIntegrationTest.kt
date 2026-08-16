package tech.justdev.infrastructure.persistence.document

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.memberEmailString
import tech.justdev.testsupport.testUuid
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Types
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource

@PostgresMicronautTest
class DocumentUploadIntentSchemaIntegrationTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Nested
    inner class Insert {
        @Test
        fun `should persist coherent pending ready and consumed upload intents`() =
            runTest {
                seedGroup("states")

                val rows =
                    listOf(
                        UploadRow(),
                        UploadRow(status = "READY", readyAt = CREATED_AT),
                        UploadRow(status = "CONSUMED", readyAt = CREATED_AT.plusSeconds(30), consumedAt = CREATED_AT.plusSeconds(30)),
                    )
                rows.forEachIndexed { index, row -> insert(seed = "states-$index", groupSeed = "states", row = row) }

                dataSource.connection.use { connection ->
                    connection
                        .prepareStatement("SELECT status FROM document_upload_intents WHERE \"group\" = ? ORDER BY status")
                        .use { statement ->
                            statement.setObject(1, groupUuid("states"))
                            statement.executeQuery().use { rows ->
                                val statuses =
                                    buildList {
                                        while (rows.next()) add(rows.getString("status"))
                                    }
                                assertEquals(listOf("CONSUMED", "PENDING", "READY"), statuses)
                            }
                        }
                }
            }

        @Test
        fun `should reject incoherent state timestamps and expired lifetimes`() =
            runTest {
                seedGroup("constraints")

                listOf(
                    UploadRow(readyAt = CREATED_AT.plusSeconds(30)),
                    UploadRow(status = "READY"),
                    UploadRow(
                        status = "READY",
                        readyAt = CREATED_AT.plusSeconds(30),
                        consumedAt = CREATED_AT.plusSeconds(60),
                    ),
                    UploadRow(status = "CONSUMED", readyAt = CREATED_AT.plusSeconds(30)),
                    UploadRow(expiresAt = CREATED_AT),
                    UploadRow(status = "UNKNOWN"),
                    UploadRow(status = "READY", readyAt = CREATED_AT.plusSeconds(300)),
                    UploadRow(
                        status = "CONSUMED",
                        readyAt = CREATED_AT.plusSeconds(30),
                        consumedAt = CREATED_AT.plusSeconds(300),
                    ),
                    UploadRow(
                        status = "CONSUMED",
                        readyAt = CREATED_AT.plusSeconds(30),
                        consumedAt = CREATED_AT.plusSeconds(29),
                    ),
                ).forEachIndexed { index, row ->
                    val error =
                        assertThrows<SQLException>("row $index") {
                            insert(seed = "constraints-$index", groupSeed = "constraints", row = row)
                        }

                    assertEquals("23514", error.sqlState)
                }
            }

        @Test
        fun `should require the uploader to belong to the group`() =
            runTest {
                seedGroup("membership")
                memberRepository.persist(Member(memberEmail("outsider"), CREATED_AT))

                val error =
                    assertThrows<SQLException> {
                        insert(seed = "outsider", groupSeed = "membership", uploaderSeed = "outsider")
                    }

                assertEquals("23503", error.sqlState)
            }
    }

    private suspend fun seedGroup(seed: String) {
        val uploader = memberEmail("$seed-uploader")
        memberRepository.persist(Member(uploader, CREATED_AT))
        groupRepository.persist(Group.create(groupId(seed), uploader, CREATED_AT))
    }

    private fun insert(
        seed: String,
        groupSeed: String = seed,
        uploaderSeed: String = "$groupSeed-uploader",
        row: UploadRow = UploadRow(),
    ) {
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO document_upload_intents (
                        id, "group", uploader, storage_key, file_name, media_type,
                        expected_size, expected_sha256, created_at, expires_at,
                        status, ready_at, consumed_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, testUuid("intent:$seed"))
                    statement.setObject(2, groupUuid(groupSeed))
                    statement.setString(3, memberEmailString(uploaderSeed))
                    statement.setString(4, "groups/$groupSeed/documents/$seed.pdf")
                    statement.setString(5, "Facture $seed.pdf")
                    statement.setString(6, "application/pdf")
                    statement.setLong(7, 512)
                    statement.setString(8, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                    statement.setInstant(9, row.createdAt)
                    statement.setInstant(10, row.expiresAt)
                    statement.setString(11, row.status)
                    statement.setInstant(12, row.readyAt)
                    statement.setInstant(13, row.consumedAt)
                    statement.executeUpdate()
                }
        }
    }

    private fun PreparedStatement.setInstant(
        index: Int,
        value: Instant?,
    ) {
        if (value == null) {
            setNull(index, Types.TIMESTAMP_WITH_TIMEZONE)
        } else {
            setObject(index, value.atOffset(ZoneOffset.UTC))
        }
    }

    private data class UploadRow(
        val createdAt: Instant = CREATED_AT,
        val expiresAt: Instant = CREATED_AT.plusSeconds(300),
        val status: String = "PENDING",
        val readyAt: Instant? = null,
        val consumedAt: Instant? = null,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-16T10:00:00Z")
    }
}
