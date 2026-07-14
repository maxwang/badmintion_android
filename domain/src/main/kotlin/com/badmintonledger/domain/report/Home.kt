package com.badmintonledger.domain.report

import com.badmintonledger.domain.calc.hasContributed
import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.calc.poolRemainingCents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.centsToDollars
import kotlin.math.abs

data class HomeRow(
    val id: String,
    val name: String,
    val isGuest: Boolean,
    val owes: Boolean,
    val absDollars: String,
)

data class HomeSummary(
    val rows: List<HomeRow>,
    val poolDollars: String,
    val poolWarn: Boolean,
    val empty: Boolean,
)

// Port of pages/home/home.js onShow: zero-balance members who never funded a refill
// are hidden; the pool warns strictly below one typical session (4h x default rate).
fun buildHomeSummary(data: LedgerData): HomeSummary {
    val bal = memberBalancesCents(data)
    val rows =
        data.members
            .filter { (bal[it.id] ?: 0L) != 0L || hasContributed(data, it.id) }
            .map { m ->
                val c = bal[m.id] ?: 0L
                HomeRow(m.id, m.name, m.isGuest, owes = c < 0, absDollars = centsToDollars(abs(c)))
            }
    val pool = poolRemainingCents(data)
    return HomeSummary(
        rows = rows,
        poolDollars = centsToDollars(pool),
        poolWarn = pool < data.config.defaultRate.value * 4,
        empty = data.members.isEmpty(),
    )
}
