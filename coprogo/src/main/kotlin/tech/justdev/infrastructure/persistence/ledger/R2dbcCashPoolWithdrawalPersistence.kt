package tech.justdev.infrastructure.persistence.ledger

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitFirstOrNull
import tech.justdev.application.ledger.CashPoolWithdrawalPersistence
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.GROUPS
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.transaction

@Singleton
class R2dbcCashPoolWithdrawalPersistence(
    @param:Named("default")
    private val connectionFactory: ConnectionFactory,
    private val ledgerEventRepository: LedgerEventRepository,
) : CashPoolWithdrawalPersistence {
    override suspend fun appendFromCurrentLedger(
        group: GroupId,
        build: suspend (List<LedgerEvent>) -> CashPoolWithdrawalLedgerEvent,
    ) {
        connectionFactory.transaction {
            val lockedGroup =
                connectionFactory
                    .dsl()
                    .select(GROUPS.ID)
                    .from(GROUPS)
                    .where(GROUPS.ID.eq(group.toPrimitive()))
                    .forUpdate()
                    .awaitFirstOrNull()
            checkNotNull(lockedGroup) { "group must exist before a cash pool withdrawal is persisted" }

            ledgerEventRepository.append(build(ledgerEventRepository.findByGroup(group)))
        }
    }
}
