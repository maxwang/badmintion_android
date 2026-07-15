package com.badmintonledger.domain.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PosterLayoutTest {
    @Test
    fun `cells line - each cell drawn at its own x align color on one baseline`() {
        val layout =
            layoutPoster(
                listOf(
                PosterLine.CellsLine(
                    size = 32,
                    cells = listOf(
                        PosterCell("王哥", 40, PosterAlign.LEFT),
                        PosterCell("2", 300, PosterAlign.CENTER),
                        PosterCell("$34.56", 520, PosterAlign.RIGHT),
                        PosterCell("left $465.44", 710, PosterAlign.RIGHT, color = "#2E7D32"),
                    ),
                ),
            ),
        )
        val t = layout.texts
        assertEquals(4, t.size)
        assertEquals(
            listOf("王哥@40/LEFT", "2@300/CENTER", "$34.56@520/RIGHT", "left $465.44@710/RIGHT"),
            t.map { "${it.text}@${it.x}/${it.align}" },
        )
        assertEquals("#333333", t[0].color)
        assertEquals("#2E7D32", t[3].color)
        // all cells of one line share the baseline; y = 50 + size
        assertTrue(t.all { it.y == t[0].y })
        assertEquals(82, t[0].y)
        assertTrue(t.all { it.size == 32 && !it.bold })
    }

    @Test
    fun `divider line - 2px light gray rect counted into height`() {
        val layout = layoutPoster(listOf(PosterLine.DividerLine(gap = 10)))
        // rects[0] is the white background, rects[1] the divider
        assertEquals(2, layout.rects.size)
        val d = layout.rects[1]
        assertEquals(PosterRect(40, 50, 670, 2, "#DDDDDD"), d)
        assertEquals(100 + 2 + 10, layout.height)
        assertEquals(PosterRect(0, 0, 750, 112, "#ffffff"), layout.rects[0])
    }

    @Test
    fun `existing line types unaffected`() {
        val layout =
            layoutPoster(
                listOf(
                PosterLine.TextLine("标题", size = 44, bold = true, center = true, gap = 30),
                PosterLine.TextLine("左", size = 32, right = "右", rightColor = "#C62828"),
            ),
        )
        assertEquals(100 + (44 + 30) + (32 + 18), layout.height)
        assertEquals(
            listOf("标题/CENTER", "左/LEFT", "右/RIGHT"),
            layout.texts.map { "${it.text}/${it.align}" },
        )
        assertEquals("#C62828", layout.texts[2].color)
        assertTrue(layout.texts[0].bold)
        assertEquals(44, layout.texts[0].size)
        assertEquals(375, layout.texts[0].x)
        assertEquals(40, layout.texts[1].x)
        assertEquals(710, layout.texts[2].x)
        // baselines: title at 50+44=94; second line at 94+30+32=156
        assertEquals(94, layout.texts[0].y)
        assertEquals(156, layout.texts[1].y)
        assertEquals(156, layout.texts[2].y)
        assertEquals(POSTER_WIDTH, layout.width)
    }
}
