package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Same fixture as ReportTest: A/B fund 1000 each, s1 = 4h x $24 x 0.8 with A, B, G playing.
private fun fixture() =
    LedgerData(
        members =
            listOf(
                Member("A", "阿安", false),
                Member("B", "小波", false),
                Member("G", "客串", true),
            ),
        refills =
            listOf(
                Refill(
                    "r1",
                    "2026-07-01",
                    Cents(200000),
                    Cents(250000),
                    listOf(
                        Contribution("A", Cents(100000)),
                        Contribution("B", Cents(100000)),
                    ),
                ),
            ),
        sessions =
            listOf(
                Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "B", "G")),
            ),
    )

class PosterTest {
    @Test
    fun `weekly lines - header, cost lines, player rows, pool`() {
        val lines = weeklyPosterLines(buildWeeklyPayload(fixture(), "s1"))

        val title = assertIs<PosterLine.TextLine>(lines[0])
        assertEquals("🏸 Badminton Weekly Settlement", title.text)
        assertEquals(44, title.size)
        assertEquals(true, title.bold)
        assertEquals(true, title.center)
        assertEquals(30, title.gap)

        val date = assertIs<PosterLine.TextLine>(lines[1])
        assertEquals("2026-07-04", date.text)
        assertEquals(PosterColors.GRAY, date.color)

        val face = assertIs<PosterLine.TextLine>(lines[2])
        assertEquals("4h × $24 = $96.00", face.text)
        assertEquals(32, face.size)

        val real = assertIs<PosterLine.TextLine>(lines[3])
        assertEquals("× factor 0.8 = paid $76.80", real.text)
        assertEquals(true, real.bold)
        assertEquals(30, real.gap)

        val playersHeader = assertIs<PosterLine.TextLine>(lines[4])
        assertEquals("Played this week (3)", playersHeader.text)

        // A: 1000.00 - 25.60 = left 974.40, green
        val rowA = assertIs<PosterLine.TextLine>(lines[5])
        assertEquals("阿安", rowA.text)
        assertEquals("$1000.00 − $25.60 = left $974.40", rowA.right)
        assertEquals(PosterColors.GREEN, rowA.rightColor)

        // G: 0.00 - 25.60 = owes 25.60, red
        val rowG = assertIs<PosterLine.TextLine>(lines[7])
        assertEquals("客串", rowG.text)
        assertEquals("$0.00 − $25.60 = owes $25.60", rowG.right)
        assertEquals(PosterColors.RED, rowG.rightColor)

        // no non-player balances in this fixture -> straight to the pool line
        val pool = assertIs<PosterLine.TextLine>(lines[8])
        assertEquals("Venue pool remaining: $2404.00", pool.text)
        assertEquals(0, pool.gap)
        assertEquals(9, lines.size)
    }

    @Test
    fun `weekly lines - owing before shows owes prefix and balances section appears`() {
        var data = fixture()
        data =
            data.copy(
                members = data.members + Member("D", "丁叔", false),
                refills =
                    listOf(
                        data.refills[0].copy(
                            contributions =
                                data.refills[0].contributions +
                                    Contribution("D", Cents(50000)),
                        ),
                    ),
                sessions =
                    data.sessions +
                        Session("s2", "2026-07-11", 1.0, Cents(2400), 1.0, listOf("A", "G")),
            )
        val lines = weeklyPosterLines(buildWeeklyPayload(data, "s2"))
        // G owed 25.60 before s2: before shows "owes $25.60", after "owes $37.60" (share 12.00)
        val rowG = lines.filterIsInstance<PosterLine.TextLine>().first { it.text == "客串" }
        assertEquals("owes $25.60 − $12.00 = owes $37.60", rowG.right)

        // D funded but never played -> balances section header + row
        val header = lines.filterIsInstance<PosterLine.TextLine>().first { it.text == "Balances (didn't play)" }
        assertEquals(PosterColors.GRAY, header.color)
        assertEquals(10, header.gap)
        val rowD = lines.filterIsInstance<PosterLine.TextLine>().first { it.text == "丁叔" }
        assertEquals("left $500.00", rowD.right)
        assertEquals(PosterColors.GREEN, rowD.rightColor)
    }

    @Test
    fun `monthly lines - header cells, divider, member rows`() {
        val lines = monthlyPosterLines(buildMonthlyPayload(fixture(), "2026-07"))

        val title = assertIs<PosterLine.TextLine>(lines[0])
        assertEquals("🏸 Badminton Monthly Report", title.text)

        val subtitle = assertIs<PosterLine.TextLine>(lines[1])
        assertEquals("2026-07 (1 session, total paid $76.80)", subtitle.text)

        val header = assertIs<PosterLine.CellsLine>(lines[2])
        assertEquals(28, header.size)
        assertEquals(PosterColors.GRAY, header.color)
        assertEquals(14, header.gap)
        assertEquals(
            listOf(
                PosterCell("Member", 40, PosterAlign.LEFT),
                PosterCell("Played", 300, PosterAlign.CENTER),
                PosterCell("Share", 520, PosterAlign.RIGHT),
                PosterCell("Balance", 710, PosterAlign.RIGHT),
            ),
            header.cells,
        )

        assertIs<PosterLine.DividerLine>(lines[3])
        assertEquals(14, (lines[3] as PosterLine.DividerLine).gap)

        val rowA = assertIs<PosterLine.CellsLine>(lines[4])
        assertEquals(32, rowA.size)
        assertEquals(24, rowA.gap)
        assertEquals(
            listOf(
                PosterCell("阿安", 40, PosterAlign.LEFT),
                PosterCell("1", 300, PosterAlign.CENTER),
                PosterCell("$25.60", 520, PosterAlign.RIGHT),
                PosterCell("$974.40", 710, PosterAlign.RIGHT, color = PosterColors.GREEN),
            ),
            rowA.cells,
        )

        // G owes -> "owes $25.60" red
        val rowG = lines.filterIsInstance<PosterLine.CellsLine>().first { it.cells[0].text == "客串" }
        assertEquals("owes $25.60", rowG.cells[3].text)
        assertEquals(PosterColors.RED, rowG.cells[3].color)

        val pool = assertIs<PosterLine.TextLine>(lines.last())
        assertEquals("Venue pool remaining: $2404.00", pool.text)
        assertTrue(lines.size >= 6)
    }

    @Test
    fun `weekly face line trims trailing zeros from fractional rates like WeChat`() {
        val data =
            fixture().copy(
                sessions =
                    listOf(
                        Session("s1", "2026-07-04", 4.0, Cents(2490), 0.8, listOf("A", "B", "G")),
                    ),
            )
        val lines = weeklyPosterLines(buildWeeklyPayload(data, "s1"))
        val face = lines[2] as PosterLine.TextLine
        assertEquals("4h × \$24.9 = \$99.60", face.text)
    }
}
