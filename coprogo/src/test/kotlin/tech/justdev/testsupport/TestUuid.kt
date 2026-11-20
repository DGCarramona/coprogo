package tech.justdev.testsupport

import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.valueobject.LedgerEventId
import tech.justdev.domain.revenue.entity.OwnershipShareChangeId
import tech.justdev.domain.shared.valueobject.GroupId
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

fun testUuid(seed: String): UUID {
    val byteBuffer = ByteBuffer.wrap(seed.toByteArray(UTF_8).copyOf(16))
    return UUID(byteBuffer.long, byteBuffer.long)
}

fun groupUuid(seed: String): UUID = testUuid("g:$seed")

fun expenseUuid(seed: String): UUID = testUuid("e:$seed")

fun ownershipShareChangeUuid(seed: String): UUID = testUuid("osc:$seed")

fun groupId(seed: String): GroupId = GroupId(groupUuid(seed))

fun memberEmail(seed: String): MemberEmail = MemberEmail.of("$seed@example.com")

fun memberEmailString(seed: String): String = memberEmail(seed).toPrimitive()

fun expenseId(seed: String): ExpenseId = ExpenseId(expenseUuid(seed))

fun ownershipShareChangeId(seed: String): OwnershipShareChangeId = OwnershipShareChangeId(ownershipShareChangeUuid(seed))

fun ledgerEventId(seed: String): LedgerEventId = LedgerEventId.fromName("ledger-event:$seed")

fun acceptedExpenseLedgerEventId(expenseSeed: String): LedgerEventId =
    LedgerEventId.fromName("accepted-expense:${expenseUuid(expenseSeed)}")
