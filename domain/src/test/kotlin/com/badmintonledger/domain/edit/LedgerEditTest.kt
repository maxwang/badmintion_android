package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerEditTest {
    private fun withMembers(): LedgerData {
        var data = LedgerData()
        data = addMember(data, "mA", "阿安", false).data
        data = addMember(data, "mG", "客串", true).data
        return data
    }

    @Test
    fun `record edit delete - members with records cannot be deleted`() {
        var data = withMembers()

        val r =
            addRefill(
                data,
                "r1",
                "2026-07-01",
                200000,
                250000,
                listOf(Contribution("mA", Cents(200000))),
            )
        assertIs<EditResult.Ok<*>>(r)
        data = r.data
        val p = addPayment(data, "p1", "mG", 2560, "2026-07-05")
        assertIs<EditResult.Ok<*>>(p)
        data = p.data
        val s = addSession(data, "s1", "2026-07-04", 4.0, 2400, 0.8, listOf("mA", "mG"))
        assertIs<EditResult.Ok<*>>(s)
        data = s.data

        // members with records cannot be deleted
        assertIs<EditResult.Err>(removeMember(data, "mA"))

        val u = updateSession(data, "s1", SessionUpdate(hours = 2.0, playerIds = listOf("mA")))
        assertIs<EditResult.Ok<*>>(u)
        data = u.data
        assertEquals(2.0, data.sessions[0].hours)
        assertEquals(listOf("mA"), data.sessions[0].playerIds)

        data = deleteSession(data, "s1")
        data = deletePayment(data, "p1")
        data = deleteRefill(data, "r1")
        assertEquals(0, data.sessions.size + data.payments.size + data.refills.size)

        // with records gone the member can be deleted
        assertIs<EditResult.Ok<*>>(removeMember(data, "mA"))
    }

    @Test
    fun `refill validation - contributions must sum to paid, amounts must be positive`() {
        val data = withMembers()

        // sum != paid -> rejected
        val bad1 =
            addRefill(
                data,
                "r1",
                "2026-07-01",
                200000,
                250000,
                listOf(Contribution("mA", Cents(190000))),
            )
        assertIs<EditResult.Err>(bad1)
        assertTrue(bad1.reason.isNotEmpty())

        // null / non-positive -> rejected
        assertIs<EditResult.Err>(addRefill(data, "r1", "2026-07-01", null, 250000, emptyList()))
        assertIs<EditResult.Err>(addRefill(data, "r1", "2026-07-01", 200000, 0, emptyList()))
        assertIs<EditResult.Err>(
            addRefill(
                data,
                "r1",
                "2026-07-01",
                200000,
                250000,
                listOf(Contribution("mA", Cents(0))),
            ),
        )

        // cent-exact comparison: 600.50 + 600.50 + 799.00 = 2000.00
        val ok =
            addRefill(
                data,
                "r1",
                "2026-07-01",
                200000,
                250000,
                listOf(
                    Contribution("mA", Cents(60050)),
                    Contribution("mA", Cents(60050)),
                    Contribution("mA", Cents(79900)),
                ),
            )
        assertIs<EditResult.Ok<*>>(ok)
    }

    @Test
    fun `payment validation - positive amount and member required`() {
        val data = withMembers()
        assertIs<EditResult.Err>(addPayment(data, "p1", "mA", -500, "2026-07-05"))
        assertIs<EditResult.Err>(addPayment(data, "p1", "mA", 0, "2026-07-05"))
        assertIs<EditResult.Err>(addPayment(data, "p1", "", 1000, "2026-07-05"))
        assertIs<EditResult.Ok<*>>(addPayment(data, "p1", "mA", 1000, "2026-07-05"))
    }

    @Test
    fun `session validation - positive hours rate factor, at least one player`() {
        val data = withMembers()
        assertIs<EditResult.Err>(addSession(data, "s1", "2026-07-04", 0.0, 2400, 0.8, listOf("mA")))
        assertIs<EditResult.Err>(addSession(data, "s1", "2026-07-04", 4.0, null, 0.8, listOf("mA")))
        assertIs<EditResult.Err>(addSession(data, "s1", "2026-07-04", 4.0, 2400, 0.8, emptyList()))
    }

    @Test
    fun `updateSession - invalid values rejected leaving original intact, unknown id rejected`() {
        var data = withMembers()
        val added = addSession(data, "s1", "2026-07-04", 4.0, 2400, 0.8, listOf("mA"))
        assertIs<EditResult.Ok<*>>(added)
        data = added.data

        assertIs<EditResult.Err>(updateSession(data, "s1", SessionUpdate(hours = -1.0)))
        assertIs<EditResult.Err>(updateSession(data, "s1", SessionUpdate(playerIds = emptyList())))
        assertEquals(4.0, data.sessions[0].hours)

        assertIs<EditResult.Err>(updateSession(data, "nope", SessionUpdate(hours = 2.0)))
    }

    @Test
    fun `one session per week - duplicates rejected, next week fine, week moves checked`() {
        var data = withMembers()
        val s1 = addSession(data, "s1", "2026-07-04", 4.0, 2400, 0.8, listOf("mA"))
        assertIs<EditResult.Ok<*>>(s1)
        data = s1.data
        // same week (Sunday) -> rejected
        val dup = addSession(data, "s2", "2026-07-05", 2.0, 2400, 0.8, listOf("mA"))
        assertIs<EditResult.Err>(dup)
        assertTrue(dup.reason.isNotEmpty())
        assertEquals(1, data.sessions.size)
        // next week (Monday) -> fine
        val s2 = addSession(data, "s2", "2026-07-06", 2.0, 2400, 0.8, listOf("mA"))
        assertIs<EditResult.Ok<*>>(s2)
        data = s2.data
        // findSessionInWeek: hit, and exclude-self
        assertEquals("s1", findSessionInWeek(data, "2026-07-05")?.id)
        assertNull(findSessionInWeek(data, "2026-07-05", "s1"))
        // moving into an occupied week -> rejected; new date within own week -> fine
        assertIs<EditResult.Err>(updateSession(data, "s2", SessionUpdate(date = "2026-07-03")))
        assertIs<EditResult.Ok<*>>(updateSession(data, "s2", SessionUpdate(date = "2026-07-07")))
    }
}
