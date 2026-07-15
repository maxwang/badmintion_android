package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.centsToDollars

/** Dollar amount printed the way JS prints a raw number: "24", "24.9", "25.61". */
fun rawDollars(c: Cents): String {
    val s = centsToDollars(c.value)
    return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}

/** Double printed the way JS prints a raw number: "4", "1.5". */
fun rawNumber(v: Double): String = v.toString().removeSuffix(".0")
