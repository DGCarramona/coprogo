package tech.justdev.interfaces.ledger

import io.micronaut.json.JsonMapper
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.testsupport.NoDbMicronautTest
import java.util.UUID

@NoDbMicronautTest
class LedgerResponseSerializationTest {
    @Inject
    lateinit var jsonMapper: JsonMapper

    private val group = UUID.fromString("156dd5e5-54c3-4abe-b19a-05fbe893ef28")

    @Nested
    inner class Serialization {
        @Test
        fun `includes empty member balances`() {
            assertEquals(
                jsonMapper.readValue("""{"group":"$group","balances":[]}""", Map::class.java),
                jsonMapper.readValue(jsonMapper.writeValueAsString(GroupBalancesResponse(group, emptyList())), Map::class.java),
            )
        }

        @Test
        fun `includes empty cash pool shares`() {
            assertEquals(
                jsonMapper.readValue("""{"group":"$group","shares":[]}""", Map::class.java),
                jsonMapper.readValue(jsonMapper.writeValueAsString(GroupMemberCashPoolSharesResponse(group, emptyList())), Map::class.java),
            )
        }
    }
}
