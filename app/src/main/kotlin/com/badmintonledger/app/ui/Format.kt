package com.badmintonledger.app.ui

import com.badmintonledger.domain.model.centsToDollars
import java.util.Locale

/** Dollars without forced decimals: 2400 cents -> "24", 2450 -> "24.50". */
fun dollarsText(cents: Long): String = centsToDollars(cents).removeSuffix(".00")

/** Plain number text without a trailing ".0": 4.0 -> "4", 1.5 -> "1.5". */
fun numberText(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/** Factor with 4 decimals, matching the WeChat form prefill: 0.8 -> "0.8000". */
fun factorText(v: Double): String = String.format(Locale.ROOT, "%.4f", v)
