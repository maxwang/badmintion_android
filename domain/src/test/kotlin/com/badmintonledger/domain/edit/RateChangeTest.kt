package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.RateChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RateChangeTest {
    @Test
    fun `validates then appends - never mutates history`() {
        val data = LedgerData()
        assertEquals(
            EditResult.Err("单价需为正数"),
            addRateChange(data, "rate_x", "2026-08-01", -1.0),
        )
        assertEquals(
            EditResult.Err("日期格式不正确"),
            addRateChange(data, "rate_x", "bad-date", 26.0),
        )
        assertEquals(1, data.rates.size)

        val added = addRateChange(data, "rate_x", "2026-08-01", 26.0)
        assertIs<EditResult.Ok<RateChange>>(added)
        assertEquals(2, added.data.rates.size)
        assertEquals(RateChange("rate_x", "2026-08-01", Cents(2600)), added.data.rates[1])
        assertEquals(1, data.rates.size) // original untouched
    }
}
