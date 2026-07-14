package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Session
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/** Monday of the week containing dateStr, as YYYY-MM-DD. */
fun weekStart(dateStr: String): String {
    val d = LocalDate.parse(dateStr)
    return d.minus(d.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY).toString()
}

fun findSessionInWeek(
    data: LedgerData,
    dateStr: String,
    excludeId: String? = null,
): Session? {
    val wk = weekStart(dateStr)
    return data.sessions.firstOrNull { it.id != excludeId && weekStart(it.date) == wk }
}
