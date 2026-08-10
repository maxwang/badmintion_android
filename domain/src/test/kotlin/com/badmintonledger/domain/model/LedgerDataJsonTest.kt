package com.badmintonledger.domain.model

import com.badmintonledger.domain.backup.BackupCodec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerDataJsonTest {
    private val backupJson =
        """
        {
          "version": 4,
          "members": [
            { "id": "A", "name": "阿安", "isGuest": false },
            { "id": "G", "name": "客串", "isGuest": true }
          ],
          "config": { "defaultPaid": 2000, "defaultCredit": 2500, "membershipFee": 50 },
          "rates": [{ "id": "rt1", "date": "2026-01-01", "rate": 24 }],
          "refills": [{
            "id": "r1", "date": "2026-07-01", "paid": 600, "credit": 750,
            "contributions": [{ "memberId": "A", "amount": 600 }]
          }],
          "payments": [{ "id": "p1", "memberId": "G", "amount": 25.6, "date": "2026-07-05" }],
          "sessions": [{ "id": "s1", "date": "2026-07-04", "hours": 4, "rate": 24,
                         "factor": 0.8, "playerIds": ["A", "G"] }],
          "memberships": [{ "id": "mf1", "memberId": "A", "year": 2026, "date": "2026-07-01", "amount": 25 }],
          "transfers": [{ "id": "tr1", "fromMemberId": "A", "toMemberId": "G", "amount": 5, "date": "2026-07-06" }]
        }
        """.trimIndent()

    @Test
    fun `backup JSON decodes with dollar amounts becoming cents`() {
        val data = BackupCodec.decode(backupJson)
        assertEquals(4, data.version)
        assertEquals(listOf(RateChange("rt1", "2026-01-01", Cents(2400))), data.rates)
        assertEquals(Cents(60000), data.refills[0].contributions[0].amount)
        assertEquals(Cents(75000), data.refills[0].credit)
        assertEquals(Cents(2560), data.payments[0].amount)
        assertEquals(Cents(2400), data.sessions[0].rate)
        assertEquals(4.0, data.sessions[0].hours)
        assertEquals(0.8, data.sessions[0].factor)
        assertEquals(listOf("A", "G"), data.sessions[0].playerIds)
        assertEquals(Cents(2500), data.memberships[0].amount)
        assertEquals(Transfer("tr1", "A", "G", Cents(500), "2026-07-06"), data.transfers[0])
    }

    @Test
    fun `round trip preserves the document exactly`() {
        val data = BackupCodec.decode(backupJson)
        val reparsed = Json.decodeFromString<LedgerData>(Json.encodeToString(LedgerData.serializer(), data))
        assertEquals(data, reparsed)
    }

    @Test
    fun `default LedgerData matches WeChat DEFAULT_DATA v4`() {
        val d = LedgerData()
        assertEquals(4, d.version)
        assertEquals(Config(Cents(200000), Cents(250000), Cents(5000)), d.config)
        assertEquals(listOf(RateChange("rate_seed", "2000-01-01", Cents(2400))), d.rates)
        assertEquals(emptyList(), d.members)
        assertEquals(emptyList(), d.memberships)
        assertEquals(emptyList(), d.transfers)
    }

    @Test
    fun `v3 document decodes through migration gaining empty transfers`() {
        val v3 =
            """{"version":3,"members":[],"config":{"defaultPaid":2000,"defaultCredit":2500,"membershipFee":50},
            "rates":[{"id":"rate_seed","date":"2000-01-01","rate":24}],
            "refills":[],"payments":[],"sessions":[],"memberships":[]}"""
        val d = BackupCodec.decode(v3)
        assertEquals(4, d.version)
        assertEquals(emptyList(), d.transfers)
    }

    @Test
    fun `member active defaults true`() {
        assertEquals(true, Member("A", "阿安", false).active)
    }

    @Test
    fun `v1 document decodes through migration keeping ITS rate, not the default`() {
        val v1 =
            """{"version":1,"members":[],"config":{"defaultRate":30,"defaultPaid":2000,
            "defaultCredit":2500},"refills":[],"payments":[],"sessions":[]}"""
        val d = BackupCodec.decode(v1)
        assertEquals(4, d.version)
        assertEquals(Config(Cents(200000), Cents(250000), Cents(5000)), d.config)
        assertEquals(listOf(RateChange("rate_seed", "2000-01-01", Cents(3000))), d.rates)
        assertEquals(emptyList(), d.memberships)
        assertEquals(emptyList(), d.transfers)
    }

    @Test
    fun `v2 document decodes through migration gaining empty memberships and default fee`() {
        val v2 =
            """{"version":2,"members":[],"config":{"defaultPaid":2000,"defaultCredit":2500},
            "rates":[{"id":"rate_seed","date":"2000-01-01","rate":24}],
            "refills":[],"payments":[],"sessions":[]}"""
        val d = BackupCodec.decode(v2)
        assertEquals(4, d.version)
        assertEquals(Cents(5000), d.config.membershipFee)
        assertEquals(emptyList(), d.memberships)
        assertEquals(emptyList(), d.transfers)
    }
}
