package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.RateChange
import com.badmintonledger.domain.model.dollarsToCents

private val DATE_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")

/** Appends a dated rate change. rates is append-only — no edit or delete, ever. */
@Suppress("ReturnCount")
fun addRateChange(
    data: LedgerData,
    id: String,
    date: String,
    rateDollars: Double?,
): EditResult<RateChange> {
    if (rateDollars == null || !rateDollars.isFinite() || rateDollars <= 0) return EditResult.Err("单价需为正数")
    if (!DATE_RE.matches(date)) return EditResult.Err("日期格式不正确")
    val rt = RateChange(id, date, Cents(dollarsToCents(rateDollars)))
    return EditResult.Ok(data.copy(rates = data.rates + rt), rt)
}
