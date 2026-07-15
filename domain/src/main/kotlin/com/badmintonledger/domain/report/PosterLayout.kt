package com.badmintonledger.domain.report

const val POSTER_WIDTH = 750
private const val PAD = 40
private const val BASE_HEIGHT = 100
private const val START_Y = 50
private const val DIVIDER_HEIGHT = 2

data class PosterText(
    val text: String,
    val x: Int,
    val y: Int,
    val size: Int,
    val bold: Boolean,
    val color: String,
    val align: PosterAlign,
)

data class PosterRect(val x: Int, val y: Int, val w: Int, val h: Int, val color: String)

data class PosterLayout(
    val width: Int,
    val height: Int,
    val rects: List<PosterRect>,
    val texts: List<PosterText>,
)

private fun lineHeight(l: PosterLine): Int =
    when (l) {
        is PosterLine.DividerLine -> DIVIDER_HEIGHT + l.gap
        is PosterLine.TextLine -> l.size + l.gap
        is PosterLine.CellsLine -> l.size + l.gap
    }

// Port of utils/poster.js drawLines: same heights, baselines and x placement.
@Suppress("NestedBlockDepth")
fun layoutPoster(lines: List<PosterLine>): PosterLayout {
    val height = BASE_HEIGHT + lines.sumOf { lineHeight(it) }
    val rects = mutableListOf(PosterRect(0, 0, POSTER_WIDTH, height, PosterColors.BACKGROUND))
    val texts = mutableListOf<PosterText>()
    var y = START_Y
    lines.forEach { l ->
        when (l) {
            is PosterLine.DividerLine -> {
                rects += PosterRect(PAD, y, POSTER_WIDTH - PAD * 2, DIVIDER_HEIGHT, PosterColors.DIVIDER)
                y += DIVIDER_HEIGHT + l.gap
            }
            is PosterLine.CellsLine -> {
                y += l.size
                l.cells.forEach { c ->
                    texts +=
                        PosterText(
                            c.text, c.x, y, l.size,
                            bold = c.bold || l.bold,
                            color = c.color ?: l.color,
                            align = c.align,
                        )
                }
                y += l.gap
            }
            is PosterLine.TextLine -> {
                y += l.size
                if (l.center) {
                    texts += PosterText(l.text, POSTER_WIDTH / 2, y, l.size, l.bold, l.color, PosterAlign.CENTER)
                } else {
                    texts += PosterText(l.text, PAD, y, l.size, l.bold, l.color, PosterAlign.LEFT)
                    if (l.right != null) {
                        texts +=
                            PosterText(
                                l.right, POSTER_WIDTH - PAD, y, l.size, l.bold,
                                color = l.rightColor ?: l.color,
                                align = PosterAlign.RIGHT,
                            )
                    }
                }
                y += l.gap
            }
        }
    }
    return PosterLayout(POSTER_WIDTH, height, rects, texts)
}
