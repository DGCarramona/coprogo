package tech.justdev.infrastructure.persistence.reimbursement

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
import java.sql.SQLException
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource

@PostgresMicronautTest
class ReimbursementSchemaIntegrationTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Nested
    inner class Insert {
        @Test
        fun `should insert a directly accepted reimbursement`() =
            runTest {
                seedGroup("direct")

                assertEquals(
                    1,
                    insertReimbursement("direct", "direct", status = "ACCEPTED", declaredBy = receiver("direct"), acceptedAt = ACCEPTED_AT),
                )
            }

        @Test
        fun `should attach several consumed documents to a pending reimbursement`() =
            runTest {
                seedGroup("pending")
                insertReimbursement("pending", "documented", status = "PENDING_REVIEW", declaredBy = payer("pending"))
                listOf("first", "second").forEach { documentSeed ->
                    insertConsumedIntent("pending", documentSeed, payer("pending"))
                    insertRegistry("pending", documentSeed, "REIMBURSEMENT")
                    insertReimbursementDocument("pending", "documented", documentSeed, payer("pending"))
                }

                assertEquals(
                    listOf("first", "second"),
                    reimbursementDocumentSeeds("pending", "documented"),
                )
            }

        @Test
        fun `should reject reusing an upload intent attached to another event type`() =
            runTest {
                seedGroup("unique")
                insertConsumedIntent("unique", "single", payer("unique"))
                insertRegistry("unique", "single", "EXPENSE")

                val error = assertThrows<SQLException> { insertRegistry("unique", "single", "REIMBURSEMENT") }

                assertEquals("23505", error.sqlState)
            }
    }

    @Nested
    inner class EnumTypes {
        @Test
        fun `should expose the exact reimbursement and supporting document enum values`() {
            assertEquals(
                listOf("PENDING_REVIEW", "ACCEPTED"),
                enumLabels("reimbursement_status"),
            )
            assertEquals(
                listOf("EXPENSE", "REIMBURSEMENT"),
                enumLabels("supporting_document_attachment_type"),
            )
        }
    }

    @Nested
    inner class ConstraintNames {
        @Test
        fun `should expose concise reimbursement supporting document foreign key names`() {
            assertEquals(
                listOf(
                    "reimb_docs_intent_uploader_status_fk",
                    "reimb_docs_reimbursement_declarer_fk",
                    "reimbursement_supporting_documents_attachment_fk",
                ),
                reimbursementSupportingDocumentForeignKeyNames(),
            )
        }
    }

    @Nested
    inner class Constraints {
        @Test
        fun `should reject inconsistent reimbursement members dates and statuses`() =
            runTest {
                seedGroup("constraints")
                val errors =
                    listOf(
                        assertThrows<SQLException> {
                            insertReimbursement(
                                "constraints",
                                "outsider",
                                paidBy = outsider("constraints"),
                                declaredBy = receiver("constraints"),
                                status = "ACCEPTED",
                                acceptedAt = ACCEPTED_AT,
                            )
                        },
                        assertThrows<SQLException> {
                            insertReimbursement(
                                "constraints",
                                "same-members",
                                paidBy = payer("constraints"),
                                receivedBy = payer("constraints"),
                                declaredBy = payer("constraints"),
                                status = "ACCEPTED",
                                acceptedAt = ACCEPTED_AT,
                            )
                        },
                        assertThrows<SQLException> {
                            insertReimbursement(
                                "constraints",
                                "late-declaration",
                                reimbursedAt = DECLARED_AT.plusSeconds(1),
                                declaredBy = receiver("constraints"),
                                status = "ACCEPTED",
                                acceptedAt = ACCEPTED_AT,
                            )
                        },
                        assertThrows<SQLException> {
                            insertReimbursement(
                                "constraints",
                                "unrelated-declarer",
                                declaredBy = outsider("constraints"),
                                status = "ACCEPTED",
                                acceptedAt = ACCEPTED_AT,
                            )
                        },
                        assertThrows<SQLException> {
                            insertReimbursement(
                                "constraints",
                                "pending-receiver",
                                declaredBy = receiver("constraints"),
                                status = "PENDING_REVIEW",
                            )
                        },
                        assertThrows<SQLException> {
                            insertReimbursement(
                                "constraints",
                                "pending-accepted-at",
                                declaredBy = payer("constraints"),
                                status = "PENDING_REVIEW",
                                acceptedAt = ACCEPTED_AT,
                            )
                        },
                        assertThrows<SQLException> {
                            insertReimbursement(
                                "constraints",
                                "accepted-missing-time",
                                declaredBy = receiver("constraints"),
                                status = "ACCEPTED",
                            )
                        },
                        assertThrows<SQLException> {
                            insertReimbursement(
                                "constraints",
                                "accepted-too-early",
                                declaredBy = receiver("constraints"),
                                status = "ACCEPTED",
                                acceptedAt = DECLARED_AT.minusSeconds(1),
                            )
                        },
                    )

                assertEquals(
                    listOf("23503", "23514", "23514", "23514", "23514", "23514", "23514", "23514"),
                    errors.map { it.sqlState },
                )
            }

        @Test
        fun `should reject supporting documents with a ready source or inconsistent group and uploader`() =
            runTest {
                seedGroup("documents")
                seedGroup("other-documents")
                insertReimbursement("documents", "pending", status = "PENDING_REVIEW", declaredBy = payer("documents"))
                insertReadyIntent("documents", "ready", payer("documents"))
                insertConsumedIntent("documents", "wrong-uploader", receiver("documents"))
                insertConsumedIntent("documents", "wrong-group", payer("documents"))
                insertRegistry("documents", "wrong-uploader", "REIMBURSEMENT")
                insertRegistry("documents", "wrong-group", "REIMBURSEMENT")

                val errors =
                    listOf(
                        assertThrows<SQLException> { insertRegistry("documents", "ready", "REIMBURSEMENT") },
                        assertThrows<SQLException> {
                            insertReimbursementDocument("documents", "pending", "wrong-uploader", receiver("documents"))
                        },
                        assertThrows<SQLException> {
                            insertReimbursementDocument("other-documents", "pending", "wrong-group", payer("documents"))
                        },
                    )

                assertEquals(List(3) { "23503" }, errors.map { it.sqlState })
            }
    }

    private suspend fun seedGroup(seed: String) {
        clearReimbursementFixture(seed)
        val payer = memberEmail("$seed-payer")
        val receiver = memberEmail("$seed-receiver")
        memberRepository.persist(Member(payer, CREATED_AT))
        memberRepository.persist(Member(receiver, CREATED_AT))
        groupRepository.persist(
            Group
                .create(groupId(seed), payer, CREATED_AT)
                .addMember(receiver, CREATED_AT),
        )
    }

    private fun clearReimbursementFixture(groupSeed: String) {
        dataSource.connection.use { connection ->
            listOf(
                "reimbursement_supporting_documents",
                "supporting_document_attachments",
                "document_upload_intents",
                "reimbursements",
            ).forEach { table ->
                connection.prepareStatement("DELETE FROM $table WHERE \"group\" = ?").use { statement ->
                    statement.setObject(1, groupUuid(groupSeed))
                    statement.executeUpdate()
                }
            }
        }
    }

    private fun insertReimbursement(
        groupSeed: String,
        reimbursementSeed: String,
        paidBy: String = payer(groupSeed),
        receivedBy: String = receiver(groupSeed),
        reimbursedAt: Instant = REIMBURSED_AT,
        declaredBy: String,
        status: String,
        acceptedAt: Instant? = null,
    ): Int =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO reimbursements (
                        id, "group", paid_by, received_by, amount, reimbursed_at,
                        declared_by, declared_at, status, accepted_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::reimbursement_status, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, reimbursementUuid(reimbursementSeed))
                    statement.setObject(2, groupUuid(groupSeed))
                    statement.setString(3, paidBy)
                    statement.setString(4, receivedBy)
                    statement.setLong(5, 100)
                    statement.setObject(6, reimbursedAt.atOffset(ZoneOffset.UTC))
                    statement.setString(7, declaredBy)
                    statement.setObject(8, DECLARED_AT.atOffset(ZoneOffset.UTC))
                    statement.setString(9, status)
                    statement.setObject(10, acceptedAt?.atOffset(ZoneOffset.UTC))
                    statement.executeUpdate()
                }
        }

    private fun insertConsumedIntent(
        groupSeed: String,
        intentSeed: String,
        uploader: String,
    ) = insertUploadIntent(groupSeed, intentSeed, uploader, "CONSUMED", CONSUMED_AT)

    private fun insertReadyIntent(
        groupSeed: String,
        intentSeed: String,
        uploader: String,
    ) = insertUploadIntent(groupSeed, intentSeed, uploader, "READY", null)

    private fun insertUploadIntent(
        groupSeed: String,
        intentSeed: String,
        uploader: String,
        status: String,
        consumedAt: Instant?,
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
                    statement.setObject(1, uploadIntentUuid(intentSeed))
                    statement.setObject(2, groupUuid(groupSeed))
                    statement.setString(3, uploader)
                    statement.setString(4, "groups/$groupSeed/documents/$intentSeed.pdf")
                    statement.setString(5, "Receipt $intentSeed.pdf")
                    statement.setString(6, "application/pdf")
                    statement.setLong(7, 512)
                    statement.setString(8, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                    statement.setObject(9, CREATED_AT.atOffset(ZoneOffset.UTC))
                    statement.setObject(10, EXPIRES_AT.atOffset(ZoneOffset.UTC))
                    statement.setString(11, status)
                    statement.setObject(12, READY_AT.atOffset(ZoneOffset.UTC))
                    statement.setObject(13, consumedAt?.atOffset(ZoneOffset.UTC))
                    statement.executeUpdate()
                }
        }
    }

    private fun insertRegistry(
        groupSeed: String,
        intentSeed: String,
        type: String,
    ) {
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO supporting_document_attachments (source_upload_intent, "group", source_status, type)
                    VALUES (?, ?, 'CONSUMED', ?::supporting_document_attachment_type)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, uploadIntentUuid(intentSeed))
                    statement.setObject(2, groupUuid(groupSeed))
                    statement.setString(3, type)
                    statement.executeUpdate()
                }
        }
    }

    private fun insertReimbursementDocument(
        groupSeed: String,
        reimbursementSeed: String,
        intentSeed: String,
        uploader: String,
    ) {
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO reimbursement_supporting_documents (
                        source_upload_intent, "group", source_status, type, reimbursement, uploader
                    ) VALUES (?, ?, 'CONSUMED', 'REIMBURSEMENT', ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, uploadIntentUuid(intentSeed))
                    statement.setObject(2, groupUuid(groupSeed))
                    statement.setObject(3, reimbursementUuid(reimbursementSeed))
                    statement.setString(4, uploader)
                    statement.executeUpdate()
                }
        }
    }

    private fun reimbursementDocumentSeeds(
        groupSeed: String,
        reimbursementSeed: String,
    ): List<String> =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT source_upload_intent
                    FROM reimbursement_supporting_documents
                    WHERE "group" = ? AND reimbursement = ?
                    ORDER BY source_upload_intent
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, groupUuid(groupSeed))
                    statement.setObject(2, reimbursementUuid(reimbursementSeed))
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(documentSeed(rows.getObject(1).toString()))
                            }
                        }
                    }
                }
        }

    private fun enumLabels(typeName: String): List<String> =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT enumlabel
                    FROM pg_enum
                    JOIN pg_type ON pg_type.oid = pg_enum.enumtypid
                    WHERE pg_type.typname = ?
                    ORDER BY enumsortorder
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, typeName)
                    statement.executeQuery().use { rows ->
                        buildList { while (rows.next()) add(rows.getString(1)) }
                    }
                }
        }

    private fun reimbursementSupportingDocumentForeignKeyNames(): List<String> =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT conname
                    FROM pg_constraint
                    WHERE conrelid = 'reimbursement_supporting_documents'::regclass
                        AND contype = 'f'
                    ORDER BY conname
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { rows ->
                        buildList { while (rows.next()) add(rows.getString(1)) }
                    }
                }
        }

    private fun payer(groupSeed: String): String = memberEmailString("$groupSeed-payer")

    private fun receiver(groupSeed: String): String = memberEmailString("$groupSeed-receiver")

    private fun outsider(groupSeed: String): String = memberEmailString("$groupSeed-outsider")

    private fun reimbursementUuid(seed: String) = testUuid("reimbursement:$seed")

    private fun uploadIntentUuid(seed: String) = testUuid("rd:$seed")

    private fun documentSeed(uploadIntent: String): String =
        mapOf(
            uploadIntentUuid("first").toString() to "first",
            uploadIntentUuid("second").toString() to "second",
        ).getValue(uploadIntent)

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-10-01T10:00:00Z")
        val REIMBURSED_AT: Instant = Instant.parse("2026-10-01T10:01:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-10-01T10:02:00Z")
        val ACCEPTED_AT: Instant = Instant.parse("2026-10-01T10:03:00Z")
        val READY_AT: Instant = Instant.parse("2026-10-01T10:04:00Z")
        val CONSUMED_AT: Instant = Instant.parse("2026-10-01T10:05:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-10-01T10:10:00Z")
    }
}
