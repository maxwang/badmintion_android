package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportOptionsTest {
    @Test
    fun `weeks newest first with paid labels, months distinct in same order`() {
        val data =
            LedgerData(
                sessions =
                    listOf(
                        Session("s1", "2026-06-27", 4.0, Cents(2400), 0.8, listOf("A")),
                        Session("s2", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A")),
                        Session("s3", "2026-07-11", 1.0, Cents(2400), 1.0, listOf("A")),
                    ),
            )
        val o = reportOptions(data)
        assertEquals(listOf("s3", "s2", "s1"), o.weeks.map { it.sessionId })
        assertEquals("2026-07-11（实付 $24.00）", o.weeks[0].label)
        assertEquals("2026-07-04（实付 $76.80）", o.weeks[1].label)
        assertEquals(listOf("2026-07", "2026-06"), o.months)
    }

    @Test
    fun `empty ledger yields empty options`() {
        val o = reportOptions(LedgerData())
        assertEquals(emptyList(), o.weeks)
        assertEquals(emptyList(), o.months)
    }
}
