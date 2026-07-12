package com.badmintonledger.domain.calc

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("LongParameterList")
fun testSession(
    id: String = "s",
    date: String = "2026-07-04",
    hours: Double,
    rateCents: Long,
    factor: Double,
    playerIds: List<String>,
) = Session(id, date, hours, Cents(rateCents), factor, playerIds)

class SessionCostTest {
    @Test
    fun `weekly cost - 4 hours at 24 dollars x 0_8 gives 76_80 real and 96 face`() {
        val s1 = testSession(hours = 4.0, rateCents = 2400, factor = 0.8, playerIds = listOf("A", "B", "D"))
        assertEquals(7680L, sessionRealCostCents(s1))
        assertEquals(9600L, sessionFaceCostCents(s1))

        // even split: 7680 / 3 = 2560 exactly
        val r1 = sessionShares(s1)
        assertEquals(7680L, r1.totalCents)
        assertEquals(mapOf("A" to 2560L, "B" to 2560L, "D" to 2560L), r1.shares)
    }

    @Test
    fun `rounding - last player absorbs the remainder so the sum is exact`() {
        // 100 cents / 3 players -> 33, 33, 34
        val s =
            testSession(
                date = "2026-07-11",
                hours = 1.0,
                rateCents = 125,
                factor = 0.8,
                playerIds = listOf("A", "B", "C"),
            )
        assertEquals(100L, sessionRealCostCents(s))
        assertEquals(
            mapOf("A" to 33L, "B" to 33L, "C" to 34L),
            sessionShares(s).shares,
        )
    }

    @Test
    fun `fractional hours - 1_5 hours at 23 dollars x 0_8 gives 27_60`() {
        val s = testSession(date = "2026-07-18", hours = 1.5, rateCents = 2300, factor = 0.8, playerIds = listOf("A"))
        assertEquals(2760L, sessionRealCostCents(s))
    }

    @Test
    fun `empty player list does not crash`() {
        val s = testSession(hours = 2.0, rateCents = 2400, factor = 0.8, playerIds = emptyList())
        assertEquals(emptyMap(), sessionShares(s).shares)
    }
}
