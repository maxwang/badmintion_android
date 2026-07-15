package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.centsToDollars

object PosterColors {
    const val GREEN = "#2E7D32"
    const val RED = "#C62828"
    const val GRAY = "#999999"
    const val INK = "#333333"
    const val DIVIDER = "#DDDDDD"
    const val BACKGROUND = "#ffffff"
}

enum class PosterAlign { LEFT, CENTER, RIGHT }

data class PosterCell(
    val text: String,
    val x: Int,
    val align: PosterAlign,
    val color: String? = null,
    val bold: Boolean = false,
)

sealed interface PosterLine {
    data class TextLine(
        val text: String,
        val size: Int = 30,
        val bold: Boolean = false,
        val color: String = PosterColors.INK,
        val center: Boolean = false,
        val right: String? = null,
        val rightColor: String? = null,
        val gap: Int = 18,
    ) : PosterLine

    data class CellsLine(
        val cells: List<PosterCell>,
        val size: Int = 30,
        val bold: Boolean = false,
        val color: String = PosterColors.INK,
        val gap: Int = 18,
    ) : PosterLine

    data class DividerLine(val gap: Int = 18) : PosterLine
}

private fun plainDollars(c: Cents): String = centsToDollars(c.value).removeSuffix(".00")

private fun plainNumber(v: Double): String = v.toString().removeSuffix(".0")

// Port of pages/report/report.js weeklyLines, English copy.
fun weeklyPosterLines(p: WeeklyPayload): List<PosterLine> {
    val lines =
        mutableListOf<PosterLine>(
            PosterLine.TextLine("🏸 Badminton Weekly Settlement", size = 44, bold = true, center = true, gap = 30),
            PosterLine.TextLine(p.date, color = PosterColors.GRAY, center = true, gap = 30),
            PosterLine.TextLine("${plainNumber(p.hours)}h × \$${plainDollars(p.rate)} = \$${p.faceDollars}", size = 32),
            PosterLine.TextLine("× factor ${p.factorText} = paid \$${p.realDollars}", size = 32, bold = true, gap = 30),
            PosterLine.TextLine("Played this week (${p.players.size})", color = PosterColors.GRAY),
        )
    p.players.forEach { pl ->
        lines +=
            PosterLine.TextLine(
                pl.name,
                size = 32,
                right =
                    (if (pl.owesBefore) "owes \$" else "\$") + pl.beforeDollars +
                        " − \$" + pl.shareDollars + " = " +
                        (if (pl.owesAfter) "owes \$" else "left \$") + pl.afterDollars,
                rightColor = if (pl.owesAfter) PosterColors.RED else PosterColors.GREEN,
            )
    }
    if (p.balances.isNotEmpty()) {
        lines += PosterLine.TextLine("Balances (didn't play)", color = PosterColors.GRAY, gap = 10)
    }
    p.balances.forEach { b ->
        lines +=
            PosterLine.TextLine(
                b.name,
                size = 32,
                right = (if (b.owes) "owes \$" else "left \$") + b.absDollars,
                rightColor = if (b.owes) PosterColors.RED else PosterColors.GREEN,
            )
    }
    lines += PosterLine.TextLine("Venue pool remaining: \$${p.poolDollars}", color = PosterColors.GRAY, gap = 0)
    return lines
}

// Monthly table column x coordinates (canvas width 750, PAD 40) — port of report.js.
private const val COL_NAME = 40
private const val COL_COUNT = 300
private const val COL_SHARE = 520
private const val COL_BALANCE = 710

// Port of pages/report/report.js monthlyLines, English copy.
fun monthlyPosterLines(p: MonthlyPayload): List<PosterLine> {
    val lines =
        mutableListOf<PosterLine>(
            PosterLine.TextLine("🏸 Badminton Monthly Report", size = 44, bold = true, center = true, gap = 30),
            PosterLine.TextLine(
                "${p.ym} (${p.weeks} sessions, total paid \$${p.totalDollars})",
                color = PosterColors.GRAY,
                center = true,
                gap = 30,
            ),
            PosterLine.CellsLine(
                size = 28,
                color = PosterColors.GRAY,
                gap = 14,
                cells =
                    listOf(
                        PosterCell("Member", COL_NAME, PosterAlign.LEFT),
                        PosterCell("Played", COL_COUNT, PosterAlign.CENTER),
                        PosterCell("Share", COL_SHARE, PosterAlign.RIGHT),
                        PosterCell("Balance", COL_BALANCE, PosterAlign.RIGHT),
                    ),
            ),
            PosterLine.DividerLine(gap = 14),
        )
    p.rows.forEach { r ->
        lines +=
            PosterLine.CellsLine(
                size = 32,
                gap = 24,
                cells =
                    listOf(
                        PosterCell(r.name, COL_NAME, PosterAlign.LEFT),
                        PosterCell(r.count.toString(), COL_COUNT, PosterAlign.CENTER),
                        PosterCell("\$" + r.shareDollars, COL_SHARE, PosterAlign.RIGHT),
                        PosterCell(
                            (if (r.owes) "owes \$" else "\$") + r.absDollars,
                            COL_BALANCE,
                            PosterAlign.RIGHT,
                            color = if (r.owes) PosterColors.RED else PosterColors.GREEN,
                        ),
                    ),
            )
    }
    lines += PosterLine.TextLine("Venue pool remaining: \$${p.poolDollars}", color = PosterColors.GRAY, gap = 0)
    return lines
}
