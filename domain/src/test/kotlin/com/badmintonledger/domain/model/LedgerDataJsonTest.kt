package com.badmintonledger.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerDataJsonTest {
    private val backupJson =
        """
        {
          "version": 1,
          "members": [
            { "id": "A", "name": "阿安", "isGuest": false },
            { "id": "G", "name": "客串", "isGuest": true }
          ],
          "config": { "defaultRate": 24, "defaultPaid": 2000, "defaultCredit": 2500 },
          "refills": [{
            "id": "r1", "date": "2026-07-01", "paid": 600, "credit": 750,
            "contributions": [{ "memberId": "A", "amount": 600 }]
          }],
          "payments": [{ "id": "p1", "memberId": "G", "amount": 25.6, "date": "2026-07-05" }],
          "sessions": [{ "id": "s1", "date": "2026-07-04", "hours": 4, "rate": 24,
                         "factor": 0.8, "playerIds": ["A", "G"] }]
        }
        """.trimIndent()

    @Test
    fun `backup JSON decodes with dollar amounts becoming cents`() {
        val data = Json.decodeFromString<LedgerData>(backupJson)
        assertEquals(1, data.version)
        assertEquals(Cents(2400), data.config.defaultRate)
        assertEquals(Cents(60000), data.refills[0].contributions[0].amount)
        assertEquals(Cents(75000), data.refills[0].credit)
        assertEquals(Cents(2560), data.payments[0].amount)
        assertEquals(Cents(2400), data.sessions[0].rate)
        assertEquals(4.0, data.sessions[0].hours)
        assertEquals(0.8, data.sessions[0].factor)
        assertEquals(listOf("A", "G"), data.sessions[0].playerIds)
    }

    @Test
    fun `round trip preserves the document exactly`() {
        val data = Json.decodeFromString<LedgerData>(backupJson)
        val reparsed = Json.decodeFromString<LedgerData>(Json.encodeToString(LedgerData.serializer(), data))
        assertEquals(data, reparsed)
    }

    @Test
    fun `default LedgerData matches WeChat DEFAULT_DATA`() {
        val d = LedgerData()
        assertEquals(1, d.version)
        assertEquals(Cents(2400), d.config.defaultRate)
        assertEquals(Cents(200000), d.config.defaultPaid)
        assertEquals(Cents(250000), d.config.defaultCredit)
        assertEquals(emptyList(), d.members)
    }
}
