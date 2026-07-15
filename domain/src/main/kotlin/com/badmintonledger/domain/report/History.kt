package com.badmintonledger.domain.report

import com.badmintonledger.domain.calc.sessionRealCostCents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.centsToDollars

data class SessionHistoryRow(
    val id: String,
    val date: String,
    val desc: String,
    val names: String,
    val realDollars: String,
)

data class RefillHistoryRow(val id: String, val date: String, val desc: String)

data class PaymentHistoryRow(val id: String, val date: String, val desc: String)

data class HistoryRows(
    val sessions: List<SessionHistoryRow>,
    val refills: List<RefillHistoryRow>,
    val payments: List<PaymentHistoryRow>,
)

// Port of pages/history/history.js refresh: sessions cut off at [cutoff], everything newest-first.
fun buildHistoryRows(
    data: LedgerData,
    cutoff: String,
): HistoryRows {
    fun nameOf(id: String) = data.members.firstOrNull { it.id == id }?.name ?: "未知"
    val sessions =
        data.sessions
            .filter { it.date >= cutoff }
            .sortedByDescending { it.date }
            .map { s ->
                SessionHistoryRow(
                    id = s.id,
                    date = s.date,
                    desc = "${rawNumber(s.hours)}小时 × $${rawDollars(s.rate)}，${s.playerIds.size}人",
                    names = s.playerIds.joinToString("、") { nameOf(it) },
                    realDollars = centsToDollars(sessionRealCostCents(s)),
                )
            }
    val refills =
        data.refills.sortedByDescending { it.date }.map { r ->
            RefillHistoryRow(r.id, r.date, "实付 $${rawDollars(r.paid)} → 到账 $${rawDollars(r.credit)}")
        }
    val payments =
        data.payments.sortedByDescending { it.date }.map { p ->
            PaymentHistoryRow(p.id, p.date, "${nameOf(p.memberId)} 交来 $${rawDollars(p.amount)}")
        }
    return HistoryRows(sessions, refills, payments)
}
