package com.badmintonledger.domain.report

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.calc.monthSummary
import com.badmintonledger.domain.calc.poolRemainingCents
import com.badmintonledger.domain.calc.sessionFaceCostCents
import com.badmintonledger.domain.calc.sessionShares
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Session
import com.badmintonledger.domain.model.centsToDollars
import kotlin.math.abs

data class BalanceRow(val name: String, val owes: Boolean, val absDollars: String)

data class PlayerRow(
    val name: String,
    val beforeDollars: String,
    val owesBefore: Boolean,
    val shareDollars: String,
    val afterDollars: String,
    val owesAfter: Boolean,
)

data class WeeklyPayload(
    val date: String,
    val hours: Double,
    val rate: Cents,
    val factorText: String,
    val faceDollars: String,
    val realDollars: String,
    val players: List<PlayerRow>,
    val balances: List<BalanceRow>,
    val poolDollars: String,
)

data class MonthlyRow(
    val name: String,
    val count: Int,
    val shareDollars: String,
    val owes: Boolean,
    val absDollars: String,
)

data class MonthlyPayload(
    val ym: String,
    val weeks: Int,
    val totalDollars: String,
    val rows: List<MonthlyRow>,
    val poolDollars: String,
)

fun memberName(
    data: LedgerData,
    id: String,
): String = data.members.firstOrNull { it.id == id }?.name ?: "未知"

// Balance section: non-zero balances only; excludeIds (this week's players, whose
// after-balance is already in the breakdown) are not repeated.
fun balanceRows(
    data: LedgerData,
    excludeIds: List<String> = emptyList(),
): List<BalanceRow> {
    val bal = memberBalancesCents(data)
    return data.members
        .filter { it.id !in excludeIds && (bal[it.id] ?: 0L) != 0L }
        .map {
            val c = bal[it.id] ?: 0L
            BalanceRow(it.name, owes = c < 0, absDollars = centsToDollars(abs(c)))
        }
}

// Per-player week detail: balance before - this week's share = balance after.
// The session must already be saved; the before-balance excludes the session itself.
fun sessionBreakdownRows(
    data: LedgerData,
    session: Session,
): List<PlayerRow> {
    val before = memberBalancesCents(data, session.id)
    val shares = sessionShares(session).shares
    return session.playerIds.map { id ->
        val b = before[id] ?: 0L
        val a = b - (shares[id] ?: 0L)
        PlayerRow(
            name = memberName(data, id),
            beforeDollars = centsToDollars(abs(b)),
            owesBefore = b < 0,
            shareDollars = centsToDollars(shares[id] ?: 0L),
            afterDollars = centsToDollars(abs(a)),
            owesAfter = a < 0,
        )
    }
}

fun buildWeeklyPayload(
    data: LedgerData,
    sessionId: String,
): WeeklyPayload {
    val s = data.sessions.first { it.id == sessionId }
    val r = sessionShares(s)
    return WeeklyPayload(
        date = s.date,
        hours = s.hours,
        rate = s.rate,
        factorText = s.factor.toString().removeSuffix(".0"),
        faceDollars = centsToDollars(sessionFaceCostCents(s)),
        realDollars = centsToDollars(r.totalCents),
        players = sessionBreakdownRows(data, s),
        balances = balanceRows(data, s.playerIds),
        poolDollars = centsToDollars(poolRemainingCents(data)),
    )
}

fun buildMonthlyPayload(
    data: LedgerData,
    ym: String,
): MonthlyPayload {
    val m = monthSummary(data, ym)
    val bal = memberBalancesCents(data)
    val rows =
        data.members
            .filter { mem ->
                val count = m.perMember[mem.id]?.count ?: 0
                count > 0 || (bal[mem.id] ?: 0L) < 0
            }
            .map { mem ->
                val pm = m.perMember[mem.id]
                val c = bal[mem.id] ?: 0L
                MonthlyRow(
                    name = mem.name,
                    count = pm?.count ?: 0,
                    shareDollars = centsToDollars(pm?.shareCents ?: 0L),
                    owes = c < 0,
                    absDollars = centsToDollars(abs(c)),
                )
            }
    return MonthlyPayload(
        ym = ym,
        weeks = m.weeks,
        totalDollars = centsToDollars(m.totalCents),
        rows = rows,
        poolDollars = centsToDollars(poolRemainingCents(data)),
    )
}
