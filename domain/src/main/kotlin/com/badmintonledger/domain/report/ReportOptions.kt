package com.badmintonledger.domain.report

import com.badmintonledger.domain.calc.sessionRealCostCents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.centsToDollars

data class WeekOption(val sessionId: String, val label: String)

data class ReportOptions(val weeks: List<WeekOption>, val months: List<String>)

// Port of pages/report/report.js onShow: sessions newest first; months distinct in that order.
fun reportOptions(data: LedgerData): ReportOptions {
    val sorted = data.sessions.sortedByDescending { it.date }
    val weeks =
        sorted.map {
            WeekOption(it.id, "${it.date} (paid $${centsToDollars(sessionRealCostCents(it))})")
        }
    val months = sorted.map { it.date.take(7) }.distinct()
    return ReportOptions(weeks, months)
}
