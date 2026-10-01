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
import tech.justdev.testsupport.expenseUuid
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
class SupportingDocumentAttachmentSchemaIntegrationTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Nested
    inner class Insert {
        @Test
        fun `should attach several upload intents to the same expense`() =
            runTest {
                seedGroup("attachment-several")
                insertExpense("attachment-several", "shared")
                listOf("first", "second").forEach { intentSeed ->
                    insertConsumedIntent("attachment-several", intentSeed)
                    insertRegistry("attachment-several", intentSeed)
                    insertExpenseAttachment("attachment-several", "shared", intentSeed)
                }

                dataSource.connection.use { connection ->
                    connection
                        .prepareStatement(
                            "SELECT count(*) FROM expense_supporting_documents WHERE expense = ? AND \"group\" = ?",
                        ).use { statement ->
                            statement.setObject(1, expenseUuid("shared"))
                            statement.setObject(2, groupUuid("attachment-several"))
                            statement.executeQuery().use { rows ->
                                rows.next()
                                assertEquals(2, rows.getInt(1))
                            }
                        }
                }
            }

        @Test
        fun `should allow an upload intent to be attached only once across event types`() =
            runTest {
                seedGroup("attachment-unique")
                insertConsumedIntent("attachment-unique", "single")
                insertRegistry("attachment-unique", "single")

                val error = assertThrows<SQLException> { insertRegistry("attachment-unique", "single") }

                assertEquals("23505", error.sqlState)
            }

        @Test
        fun `should reject an upload intent that is not consumed`() =
            runTest {
                seedGroup("attachment-ready")
                insertReadyIntent("attachment-ready", "not-consumed")

                val error = assertThrows<SQLException> { insertRegistry("attachment-ready", "not-consumed") }

                assertEquals("23503", error.sqlState)
            }

        @Test
        fun `should enforce upload intent and expense group scopes through foreign keys`() =
            runTest {
                seedGroup("source-attachment-scope")
                seedGroup("other-attachment-scope")
                insertExpense("source-attachment-scope", "source-expense")
                insertExpense("other-attachment-scope", "other-expense")
                insertConsumedIntent("source-attachment-scope", "wrong-intent-group")
                insertConsumedIntent("source-attachment-scope", "wrong-expense-group")
                insertConsumedIntent("source-attachment-scope", "missing-registry")

                val wrongIntentGroup =
                    assertThrows<SQLException> {
                        insertRegistry("other-attachment-scope", "wrong-intent-group")
                    }
                insertRegistry("source-attachment-scope", "wrong-expense-group")
                val wrongExpenseGroup =
                    assertThrows<SQLException> {
                        insertExpenseAttachment("source-attachment-scope", "other-expense", "wrong-expense-group")
                    }
                val missingRegistry =
                    assertThrows<SQLException> {
                        insertExpenseAttachment("source-attachment-scope", "source-expense", "missing-registry")
                    }

                assertEquals("23503", wrongIntentGroup.sqlState)
                assertEquals("23503", wrongExpenseGroup.sqlState)
                assertEquals("23503", missingRegistry.sqlState)
            }
    }

    @Nested
    inner class AttachmentType {
        @Test
        fun `should be a PostgreSQL enum with the expense type`() {
            dataSource.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
                        SELECT enumlabel
                        FROM pg_enum
                        JOIN pg_type ON pg_type.oid = pg_enum.enumtypid
                        WHERE pg_type.typname = 'supporting_document_attachment_type'
                        ORDER BY enumsortorder
                        """.trimIndent(),
                    ).use { statement ->
                        statement.executeQuery().use { rows ->
                            val labels = buildList { while (rows.next()) add(rows.getString(1)) }
                            assertEquals(listOf("EXPENSE", "REIMBURSEMENT"), labels)
                        }
                    }
            }
        }
    }

    @Nested
    inner class Replacement {
        @Test
        fun `should retain the replaced document and link its successor in the same expense`() =
            runTest {
                seedGroup("replacement-history")
                insertExpense("replacement-history", "history-expense")
                listOf("history-original", "history-successor").forEach { intentSeed ->
                    insertConsumedIntent("replacement-history", intentSeed)
                    insertRegistry("replacement-history", intentSeed)
                }
                insertExpenseAttachment("replacement-history", "history-expense", "history-original")
                insertExpenseAttachment(
                    groupSeed = "replacement-history",
                    expenseSeed = "history-expense",
                    intentSeed = "history-successor",
                    replacesIntentSeed = "history-original",
                )

                assertEquals(
                    listOf(
                        AttachmentReference("history-original", null),
                        AttachmentReference("history-successor", "history-original"),
                    ),
                    expenseAttachmentReferences("replacement-history", "history-expense"),
                )
            }

        @Test
        fun `should reject a second direct successor for the same document`() =
            runTest {
                seedGroup("replacement-successor")
                insertExpense("replacement-successor", "branch-expense")
                listOf("branch-original", "branch-first", "branch-second").forEach { intentSeed ->
                    insertConsumedIntent("replacement-successor", intentSeed)
                    insertRegistry("replacement-successor", intentSeed)
                }
                insertExpenseAttachment("replacement-successor", "branch-expense", "branch-original")
                insertExpenseAttachment(
                    groupSeed = "replacement-successor",
                    expenseSeed = "branch-expense",
                    intentSeed = "branch-first",
                    replacesIntentSeed = "branch-original",
                )

                val error =
                    assertThrows<SQLException> {
                        insertExpenseAttachment(
                            groupSeed = "replacement-successor",
                            expenseSeed = "branch-expense",
                            intentSeed = "branch-second",
                            replacesIntentSeed = "branch-original",
                        )
                    }

                assertEquals("23505", error.sqlState)
            }

        @Test
        fun `should reject replacing a document associated with another expense`() =
            runTest {
                seedGroup("replacement-expense-scope")
                insertExpense("replacement-expense-scope", "scope-replaced")
                insertExpense("replacement-expense-scope", "scope-other")
                listOf("scope-original", "scope-successor").forEach { intentSeed ->
                    insertConsumedIntent("replacement-expense-scope", intentSeed)
                    insertRegistry("replacement-expense-scope", intentSeed)
                }
                insertExpenseAttachment("replacement-expense-scope", "scope-replaced", "scope-original")

                val error =
                    assertThrows<SQLException> {
                        insertExpenseAttachment(
                            groupSeed = "replacement-expense-scope",
                            expenseSeed = "scope-other",
                            intentSeed = "scope-successor",
                            replacesIntentSeed = "scope-original",
                        )
                    }

                assertEquals("23503", error.sqlState)
            }
    }

    private suspend fun seedGroup(seed: String) {
        val creator = memberEmail("$seed-uploader")
        memberRepository.persist(Member(creator, CREATED_AT))
        groupRepository.persist(Group.create(groupId(seed), creator, CREATED_AT))
    }

    private fun insertExpense(
        groupSeed: String,
        expenseSeed: String,
    ) {
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO expenses (id, "group", title, created_by, total_amount, status, created_at)
                    VALUES (?, ?, ?, ?, ?, 'PROPOSED', ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, expenseUuid(expenseSeed))
                    statement.setObject(2, groupUuid(groupSeed))
                    statement.setString(3, "Expense $expenseSeed")
                    statement.setString(4, memberEmailString("$groupSeed-uploader"))
                    statement.setLong(5, 100)
                    statement.setObject(6, CREATED_AT.atOffset(ZoneOffset.UTC))
                    statement.executeUpdate()
                }
        }
    }

    @Nested
    inner class DeletionAudit {
        @Test
        fun `should allow an attachment with no deletion audit`() =
            runTest {
                seedGroup("deletion-not-deleted")
                insertConsumedIntent("deletion-not-deleted", "document-no-audit")

                assertEquals(1, insertRegistry("deletion-not-deleted", "document-no-audit"))
            }

        @Test
        fun `should allow a complete deletion audit from a group member`() =
            runTest {
                seedGroup("deletion-audited")
                insertConsumedIntent("deletion-audited", "document-audited")

                assertEquals(
                    1,
                    insertRegistry(
                        groupSeed = "deletion-audited",
                        intentSeed = "document-audited",
                        deletedBy = memberEmailString("deletion-audited-uploader"),
                        deletedAt = DELETED_AT,
                    ),
                )
            }

        @Test
        fun `should reject a partial deletion audit`() =
            runTest {
                seedGroup("deletion-partial")
                insertConsumedIntent("deletion-partial", "document-partial")

                val deletedByOnly =
                    assertThrows<SQLException> {
                        insertRegistry(
                            groupSeed = "deletion-partial",
                            intentSeed = "document-partial",
                            deletedBy = memberEmailString("deletion-partial-uploader"),
                        )
                    }
                val deletedAtOnly =
                    assertThrows<SQLException> {
                        insertRegistry(
                            groupSeed = "deletion-partial",
                            intentSeed = "document-partial",
                            deletedAt = DELETED_AT,
                        )
                    }

                assertEquals(listOf("23514", "23514"), listOf(deletedByOnly.sqlState, deletedAtOnly.sqlState))
            }

        @Test
        fun `should reject a deletion audit from outside the group`() =
            runTest {
                seedGroup("deletion-outsider")
                val outsider = memberEmail("deletion-outsider-member")
                memberRepository.persist(Member(outsider, CREATED_AT))
                insertConsumedIntent("deletion-outsider", "document-outsider")

                val error =
                    assertThrows<SQLException> {
                        insertRegistry(
                            groupSeed = "deletion-outsider",
                            intentSeed = "document-outsider",
                            deletedBy = outsider.toPrimitive(),
                            deletedAt = DELETED_AT,
                        )
                    }

                assertEquals("23503", error.sqlState)
            }
    }

    private fun insertConsumedIntent(
        groupSeed: String,
        intentSeed: String,
    ) {
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO document_upload_intents (
                        id, "group", uploader, storage_key, file_name, media_type,
                        expected_size, expected_sha256, created_at, expires_at,
                        status, ready_at, consumed_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONSUMED', ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, uploadIntentUuid(intentSeed))
                    statement.setObject(2, groupUuid(groupSeed))
                    statement.setString(3, memberEmailString("$groupSeed-uploader"))
                    statement.setString(4, "groups/$groupSeed/documents/$intentSeed.pdf")
                    statement.setString(5, "Facture $intentSeed.pdf")
                    statement.setString(6, "application/pdf")
                    statement.setLong(7, 512)
                    statement.setString(8, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                    statement.setObject(9, CREATED_AT.atOffset(ZoneOffset.UTC))
                    statement.setObject(10, EXPIRES_AT.atOffset(ZoneOffset.UTC))
                    statement.setObject(11, READY_AT.atOffset(ZoneOffset.UTC))
                    statement.setObject(12, CONSUMED_AT.atOffset(ZoneOffset.UTC))
                    statement.executeUpdate()
                }
        }
    }

    private fun insertReadyIntent(
        groupSeed: String,
        intentSeed: String,
    ) {
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO document_upload_intents (
                        id, "group", uploader, storage_key, file_name, media_type,
                        expected_size, expected_sha256, created_at, expires_at,
                        status, ready_at, consumed_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'READY', ?, NULL)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, uploadIntentUuid(intentSeed))
                    statement.setObject(2, groupUuid(groupSeed))
                    statement.setString(3, memberEmailString("$groupSeed-uploader"))
                    statement.setString(4, "groups/$groupSeed/documents/$intentSeed.pdf")
                    statement.setString(5, "Facture $intentSeed.pdf")
                    statement.setString(6, "application/pdf")
                    statement.setLong(7, 512)
                    statement.setString(8, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                    statement.setObject(9, CREATED_AT.atOffset(ZoneOffset.UTC))
                    statement.setObject(10, EXPIRES_AT.atOffset(ZoneOffset.UTC))
                    statement.setObject(11, READY_AT.atOffset(ZoneOffset.UTC))
                    statement.executeUpdate()
                }
        }
    }

    private fun insertRegistry(
        groupSeed: String,
        intentSeed: String,
        deletedBy: String? = null,
        deletedAt: Instant? = null,
    ): Int =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO supporting_document_attachments (
                        source_upload_intent, "group", source_status, type, deleted_by, deleted_at
                    ) VALUES (?, ?, 'CONSUMED', 'EXPENSE', ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, uploadIntentUuid(intentSeed))
                    statement.setObject(2, groupUuid(groupSeed))
                    statement.setString(3, deletedBy)
                    statement.setObject(4, deletedAt?.atOffset(ZoneOffset.UTC))
                    statement.executeUpdate()
                }
        }

    private fun insertExpenseAttachment(
        groupSeed: String,
        expenseSeed: String,
        intentSeed: String,
        replacesIntentSeed: String? = null,
    ) {
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO expense_supporting_documents (
                        source_upload_intent, "group", type, expense, replaces_source_upload_intent
                    ) VALUES (?, ?, 'EXPENSE', ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, uploadIntentUuid(intentSeed))
                    statement.setObject(2, groupUuid(groupSeed))
                    statement.setObject(3, expenseUuid(expenseSeed))
                    statement.setObject(4, replacesIntentSeed?.let(::uploadIntentUuid))
                    statement.executeUpdate()
                }
        }
    }

    private fun expenseAttachmentReferences(
        groupSeed: String,
        expenseSeed: String,
    ): List<AttachmentReference> =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT source_upload_intent, replaces_source_upload_intent
                    FROM expense_supporting_documents
                    WHERE "group" = ? AND expense = ?
                    ORDER BY source_upload_intent
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, groupUuid(groupSeed))
                    statement.setObject(2, expenseUuid(expenseSeed))
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(
                                    AttachmentReference(
                                        sourceIntentSeed = attachmentSeed(rows.getObject(1).toString()),
                                        replacesIntentSeed = rows.getObject(2)?.toString()?.let(::attachmentSeed),
                                    ),
                                )
                            }
                        }
                    }
                }
        }

    private fun attachmentSeed(uploadIntent: String): String =
        mapOf(
            uploadIntentUuid("history-original").toString() to "history-original",
            uploadIntentUuid("history-successor").toString() to "history-successor",
        ).getValue(uploadIntent)

    private fun uploadIntentUuid(seed: String) = testUuid("di:$seed")

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-16T10:00:00Z")
        val READY_AT: Instant = Instant.parse("2026-08-16T10:01:00Z")
        val CONSUMED_AT: Instant = Instant.parse("2026-08-16T10:02:00Z")
        val DELETED_AT: Instant = Instant.parse("2026-08-16T10:03:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-08-16T10:05:00Z")
    }

    private data class AttachmentReference(
        val sourceIntentSeed: String,
        val replacesIntentSeed: String?,
    )
}
