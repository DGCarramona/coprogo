package tech.justdev.interfaces.ledger

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.ledger.InsufficientCashPoolBalanceException
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.interfaces.ApiErrorResponse

class LedgerExceptionHandlersTest {
    @Test
    fun `should map insufficient cash-pool balance to a 409 API response`() {
        val path = "/api/groups/group/cash-pool-withdrawals"
        val exception =
            InsufficientCashPoolBalanceException(
                requestedAmount = MoneyAmount.ofCents(101),
                availableAmount = MoneyAmount.ofCents(100),
            )

        val response = InsufficientCashPoolBalanceExceptionHandler().handle(HttpRequest.POST(path, Unit), exception)

        assertEquals(HttpStatus.CONFLICT, response.status)
        assertEquals(ApiErrorResponse(exception.message!!, path), response.body())
    }
}
