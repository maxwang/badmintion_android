package com.badmintonledger.domain.calc

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

// Balance per member (cents): contributions + cash payments - session shares.
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
