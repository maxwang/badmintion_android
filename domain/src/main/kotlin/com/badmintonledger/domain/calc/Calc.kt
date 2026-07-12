package com.badmintonledger.domain.calc

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
