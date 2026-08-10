@file:Suppress("TooManyFunctions")

package com.badmintonledger.domain.calc

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Session
import kotlin.math.round

// JS parity: Math.round(hours * rateDollars * factor * 100) == round(hours * rateCents * factor)
fun sessionRealCostCents(s: Session): Long = round(s.hours * s.rate.value * s.factor).toLong()

fun sessionFaceCostCents(s: Session): Long = round(s.hours * s.rate.value).toLong()

data class SessionShares(val totalCents: Long, val shares: Map<String, Long>)

// Even split; the last player absorbs the rounding remainder so the sum is exact.
fun sessionShares(s: Session): SessionShares {
    val total = sessionRealCostCents(s)
    val n = s.playerIds.size
    if (n == 0) return SessionShares(total, emptyMap())
    val base = total / n
    val shares =
        s.playerIds.mapIndexed { i, id ->
            id to if (i == n - 1) total - base * (n - 1) else base
        }.toMap()
    return SessionShares(total, shares)
}

// Balance per member (cents): contributions + cash payments - session shares - transfers out + transfers in.
// Positive = money left; negative = owes. excludeSessionId gives the balance "before" that week.
fun memberBalancesCents(
    data: LedgerData,
    excludeSessionId: String? = null,
): Map<String, Long> {
    val bal = mutableMapOf<String, Long>()
    data.members.forEach { bal[it.id] = 0L }
    data.refills.forEach { r ->
        r.contributions.forEach { c -> bal[c.memberId] = (bal[c.memberId] ?: 0L) + c.amount.value }
    }
    data.payments.forEach { p -> bal[p.memberId] = (bal[p.memberId] ?: 0L) + p.amount.value }
    data.transfers.forEach { t ->
        bal[t.fromMemberId] = (bal[t.fromMemberId] ?: 0L) - t.amount.value
        bal[t.toMemberId] = (bal[t.toMemberId] ?: 0L) + t.amount.value
    }
    data.sessions.forEach { s ->
        if (s.id == excludeSessionId) return@forEach
        sessionShares(s).shares.forEach { (id, share) -> bal[id] = (bal[id] ?: 0L) - share }
    }
    return bal
}

// Venue pool remaining (cents) = total refill credits - total session face costs.
fun poolRemainingCents(data: LedgerData): Long =
    data.refills.sumOf { it.credit.value } - data.sessions.sumOf { sessionFaceCostCents(it) }

// Latest refill's paid/credit ratio; default config ratio when there are no refills.
fun currentFactor(data: LedgerData): Double {
    val latest =
        data.refills.maxByOrNull { it.date }
            ?: return data.config.defaultPaid.value.toDouble() / data.config.defaultCredit.value
    return latest.paid.value.toDouble() / latest.credit.value
}

data class MemberMonth(val count: Int, val shareCents: Long)

data class MonthSummary(val weeks: Int, val totalCents: Long, val perMember: Map<String, MemberMonth>)

fun monthSummary(
    data: LedgerData,
    ym: String,
): MonthSummary {
    val sessions = data.sessions.filter { it.date.startsWith(ym) }
    val per = mutableMapOf<String, MemberMonth>()
    data.members.forEach { per[it.id] = MemberMonth(0, 0L) }
    var total = 0L
    sessions.forEach { s ->
        val r = sessionShares(s)
        total += r.totalCents
        r.shares.forEach { (id, share) ->
            val prev = per[id] ?: MemberMonth(0, 0L)
            per[id] = MemberMonth(prev.count + 1, prev.shareCents + share)
        }
    }
    return MonthSummary(sessions.size, total, per)
}

// True only if the member funded any refill; paying off debt in cash does not count.
fun hasContributed(
    data: LedgerData,
    memberId: String,
): Boolean = data.refills.any { r -> r.contributions.any { it.memberId == memberId && it.amount.value > 0 } }

// 按日期取历史单价：找 date <= dateStr 中日期最晚的一条；早于最早记录时取最早一条
fun currentRate(
    data: LedgerData,
    dateStr: String,
): Cents {
    val eligible = data.rates.filter { it.date <= dateStr }
    val hit = eligible.maxByOrNull { it.date } ?: data.rates.minByOrNull { it.date }
    return checkNotNull(hit) { "rates is never empty" }.rate
}

// 会员年费欠费（分）：仅统计未标记已付的记录；与球馆余额（memberBalancesCents）完全独立
fun membershipBalancesCents(data: LedgerData): Map<String, Long> {
    val bal = mutableMapOf<String, Long>()
    data.members.forEach { bal[it.id] = 0L }
    data.memberships.forEach { mf ->
        if (mf.paidDate != null) return@forEach
        bal[mf.memberId] = (bal[mf.memberId] ?: 0L) - mf.amount.value
    }
    return bal
}

data class MembershipStatus(val eligible: Int, val charged: Int, val paid: Int)

// 会员年费收取情况：eligible=正式且启用成员数，charged=其中已开单该年度会费的人数，paid=其中已标记付清的人数
fun membershipStatus(
    data: LedgerData,
    year: Int,
): MembershipStatus {
    val eligible = data.members.filter { !it.isGuest && it.active }
    val yearEntries = eligible.map { m -> data.memberships.firstOrNull { it.memberId == m.id && it.year == year } }
    return MembershipStatus(
        eligible = eligible.size,
        charged = yearEntries.count { it != null },
        paid = yearEntries.count { it?.paidDate != null },
    )
}
