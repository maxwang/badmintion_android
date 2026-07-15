# Milestone 4: Reports + Poster + Share Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Weekly/monthly report posters rendered from a pure-domain line model to a bitmap, previewed in a Report screen, and shared through the system share sheet into the WeChat group.

**Architecture:** Three pure-domain layers ported from the WeChat app, all TDD: poster line builders (`weeklyPosterLines`/`monthlyPosterLines`, port of `pages/report/report.js` with English copy), layout geometry (`layoutPoster`, port of `utils/poster.js` — positioned text/rect primitives, so the WeChat `poster.test.js` suite ports directly), and picker options (`reportOptions`, port of the page's `onShow`). The app layer only maps primitives to `android.graphics.Canvas` calls (`PosterRenderer`), writes a PNG to cache and fires `ACTION_SEND` via FileProvider (`PosterShare`), and hosts the Report screen (mode toggle, pickers, generate → preview → share) with a session-save handoff that auto-generates this week's poster.

**Tech Stack:** No new dependencies. `android.graphics.Bitmap/Canvas/Paint`, `androidx.core.content.FileProvider` (in androidx.core, already transitive — declare explicitly), Compose `Image(bitmap.asImageBitmap())`.

## Global Constraints

- `domain/` pure Kotlin: no `android.*` imports. Colors travel as hex strings (`"#2E7D32"`), converted only in `PosterRenderer`.
- All money integer cents in domain; poster text uses domain-formatted dollar strings; the `$` sign IS part of poster copy (it is user-facing content, matching WeChat's `'$' + …`).
- Poster copy is English (the group is in Australia). Exact strings are defined in Task 1 and are load-bearing — the WeChat original is Chinese; numbers/structure must stay identical, labels translate.
- Poster geometry parity with `utils/poster.js`: canvas width 750, PAD 40, base height 100, first baseline advance from y=50, default size 30, default gap 18, divider = 2px `#DDDDDD` rect at x=40 width 670, background `#ffffff`. Colors: GREEN `#2E7D32`, RED `#C62828`, GRAY `#999999`, INK `#333333`.
- WeChat parity ground truth: `E:\Code\ai\wechat\badminton\pages\report\report.js`, `utils\poster.js`, `tests\poster.test.js`.
- Standing constraints (from M3 review): any mutating screen ports WeChat's `this.saving` double-submit guard (the Report screen is read-only; the Share button tolerates double-taps — worst case two chooser opens, acceptable); edit forms always submit all fields and the VM validates all of them; **UI conversion convention**: UI may call `dollarsToCents` / domain view-builders for display previews, persistence conversions happen only in the ViewModel.
- The Report screen reads `vm.ledger` and calls domain builders directly — NO new LedgerViewModel functions (it stays at its current size).
- Week starts Monday; derived values always recomputed from the document.
- TDD for all domain code; app rendering/share verified by build + on-phone acceptance. ktlint/detekt green at every commit; smallest-scope `@Suppress` where detekt fights plan code; conventional commits.
- Branch: `feat/m4-reports-poster-share` off `main`. Never commit real backup JSON.

**Intentionally different from the design spec (documented deviation):** the spec sketched "poster: Compose layout → bitmap capture". This plan instead renders the poster from domain-computed primitives via `android.graphics.Canvas`. Rationale: the WeChat poster is itself canvas-drawn with exact pixel geometry (`utils/poster.js`); porting that geometry as a pure-domain function makes the entire WeChat `poster.test.js` suite portable as headless parity tests and guarantees the acceptance criterion ("visually complete and numerically identical") by construction, whereas a Compose-capture would re-derive layout in a way that can't be tested without a device. The `poster/` package boundary from the spec's module architecture is preserved.

**Intentionally different from WeChat (documented, not drift):**
- Poster labels English (copy table in Task 1).
- WeChat's "save to album" button is deferred to M5 polish — the Android share sheet already offers "save" targets.
- WeChat scales the canvas display height by /2 (`canvasH: Math.ceil(h / 2)`) — a mini-program display detail; Compose previews the bitmap with `fillMaxWidth()` and intrinsic aspect ratio instead.

## File Structure

```
domain/src/main/kotlin/com/badmintonledger/domain/report/Poster.kt        Task 1 — line model + weekly/monthly builders
domain/src/test/kotlin/com/badmintonledger/domain/report/PosterTest.kt    Task 1
domain/src/main/kotlin/com/badmintonledger/domain/report/PosterLayout.kt  Task 2 — geometry → primitives
domain/src/test/kotlin/com/badmintonledger/domain/report/PosterLayoutTest.kt Task 2 (port of poster.test.js)
domain/src/main/kotlin/com/badmintonledger/domain/report/ReportOptions.kt Task 3
domain/src/test/kotlin/com/badmintonledger/domain/report/ReportOptionsTest.kt Task 3
app/src/main/kotlin/com/badmintonledger/app/poster/PosterRenderer.kt      Task 4 — primitives → Bitmap
app/src/main/kotlin/com/badmintonledger/app/poster/PosterShare.kt         Task 4 — PNG to cache + ACTION_SEND
app/src/main/res/xml/file_paths.xml                                       Task 4
app/src/main/AndroidManifest.xml                                          Task 4 (FileProvider)
app/build.gradle.kts + gradle/libs.versions.toml                          Task 4 (androidx.core-ktx explicit)
app/src/main/kotlin/com/badmintonledger/app/ui/ReportScreen.kt            Task 5
app/src/main/kotlin/com/badmintonledger/app/ui/AppNav.kt                  Task 5 (route report?sessionId=)
app/src/main/kotlin/com/badmintonledger/app/ui/HomeScreen.kt              Task 5 (Report button)
app/src/main/kotlin/com/badmintonledger/app/ui/SessionScreen.kt           Task 5 (save → report handoff)
```

---

### Task 1: domain — poster line model + weekly/monthly builders (TDD)

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/report/Poster.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/report/PosterTest.kt`

**Interfaces:**
- Consumes: `WeeklyPayload`, `MonthlyPayload`, `PlayerRow`, `BalanceRow`, `MonthlyRow` (Report.kt, M1), `centsToDollars`.
- Produces (Tasks 2/5 rely on these exact shapes):
  - `object PosterColors { const val GREEN = "#2E7D32"; const val RED = "#C62828"; const val GRAY = "#999999"; const val INK = "#333333"; const val DIVIDER = "#DDDDDD"; const val BACKGROUND = "#ffffff" }`
  - `enum class PosterAlign { LEFT, CENTER, RIGHT }`
  - `data class PosterCell(val text: String, val x: Int, val align: PosterAlign, val color: String? = null, val bold: Boolean = false)`
  - `sealed interface PosterLine` with:
    - `data class TextLine(val text: String, val size: Int = 30, val bold: Boolean = false, val color: String = PosterColors.INK, val center: Boolean = false, val right: String? = null, val rightColor: String? = null, val gap: Int = 18) : PosterLine`
    - `data class CellsLine(val cells: List<PosterCell>, val size: Int = 30, val bold: Boolean = false, val color: String = PosterColors.INK, val gap: Int = 18) : PosterLine`
    - `data class DividerLine(val gap: Int = 18) : PosterLine`
  - `fun weeklyPosterLines(p: WeeklyPayload): List<PosterLine>`
  - `fun monthlyPosterLines(p: MonthlyPayload): List<PosterLine>`

**Exact English copy (port of report.js `weeklyLines`/`monthlyLines`):**

| WeChat | English |
|---|---|
| 🏸 羽毛球周结算 | `🏸 Badminton Weekly Settlement` |
| `4小时 × $24 = $96.00` | `4h × $24 = $96.00` |
| `× 折扣 0.8 = 实付 $76.80` | `× factor 0.8 = paid $76.80` |
| 本周上场（3人） | `Played this week (3)` |
| 欠 $X / $X / 剩 $X | `owes $X` / `$X` / `left $X` |
| 未上场成员余额 | `Balances (didn't play)` |
| 球馆额度剩余：$X | `Venue pool remaining: $X` |
| 🏸 羽毛球月度报告 | `🏸 Badminton Monthly Report` |
| `2026-07（2次活动，合计实付 $153.60）` | `2026-07 (2 sessions, total paid $153.60)` |
| 成员/出场/应摊/当前余额 | `Member` / `Played` / `Share` / `Balance` |

- [ ] **Step 1: Write the failing test**

`PosterTest.kt`:
```kotlin
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
private fun fixture() = LedgerData(
    members = listOf(
        Member("A", "阿安", false),
        Member("B", "小波", false),
        Member("G", "客串", true),
    ),
    refills = listOf(
        Refill("r1", "2026-07-01", Cents(200000), Cents(250000), listOf(
            Contribution("A", Cents(100000)),
            Contribution("B", Cents(100000)),
        )),
    ),
    sessions = listOf(
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
        data = data.copy(
            members = data.members + Member("D", "丁叔", false),
            refills = listOf(
                data.refills[0].copy(
                    contributions = data.refills[0].contributions + Contribution("D", Cents(50000)),
                ),
            ),
            sessions = data.sessions +
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
        assertEquals("2026-07 (1 sessions, total paid $76.80)", subtitle.text)

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.PosterTest"`
Expected: FAIL — compilation error (unresolved `weeklyPosterLines` etc.).

- [ ] **Step 3: Implement**

`Poster.kt`:
```kotlin
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
    val lines = mutableListOf<PosterLine>(
        PosterLine.TextLine("🏸 Badminton Weekly Settlement", size = 44, bold = true, center = true, gap = 30),
        PosterLine.TextLine(p.date, color = PosterColors.GRAY, center = true, gap = 30),
        PosterLine.TextLine("${plainNumber(p.hours)}h × $${plainDollars(p.rate)} = $${p.faceDollars}", size = 32),
        PosterLine.TextLine("× factor ${p.factorText} = paid $${p.realDollars}", size = 32, bold = true, gap = 30),
        PosterLine.TextLine("Played this week (${p.players.size})", color = PosterColors.GRAY),
    )
    p.players.forEach { pl ->
        lines +=
            PosterLine.TextLine(
                pl.name,
                size = 32,
                right = (if (pl.owesBefore) "owes $" else "$") + pl.beforeDollars +
                    " − $" + pl.shareDollars + " = " +
                    (if (pl.owesAfter) "owes $" else "left $") + pl.afterDollars,
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
                right = (if (b.owes) "owes $" else "left $") + b.absDollars,
                rightColor = if (b.owes) PosterColors.RED else PosterColors.GREEN,
            )
    }
    lines += PosterLine.TextLine("Venue pool remaining: $${p.poolDollars}", color = PosterColors.GRAY, gap = 0)
    return lines
}

// Monthly table column x coordinates (canvas width 750, PAD 40) — port of report.js.
private const val COL_NAME = 40
private const val COL_COUNT = 300
private const val COL_SHARE = 520
private const val COL_BALANCE = 710

// Port of pages/report/report.js monthlyLines, English copy.
fun monthlyPosterLines(p: MonthlyPayload): List<PosterLine> {
    val lines = mutableListOf<PosterLine>(
        PosterLine.TextLine("🏸 Badminton Monthly Report", size = 44, bold = true, center = true, gap = 30),
        PosterLine.TextLine(
            "${p.ym} (${p.weeks} sessions, total paid $${p.totalDollars})",
            color = PosterColors.GRAY,
            center = true,
            gap = 30,
        ),
        PosterLine.CellsLine(
            size = 28,
            color = PosterColors.GRAY,
            gap = 14,
            cells = listOf(
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
                cells = listOf(
                    PosterCell(r.name, COL_NAME, PosterAlign.LEFT),
                    PosterCell(r.count.toString(), COL_COUNT, PosterAlign.CENTER),
                    PosterCell("$" + r.shareDollars, COL_SHARE, PosterAlign.RIGHT),
                    PosterCell(
                        (if (r.owes) "owes $" else "$") + r.absDollars,
                        COL_BALANCE,
                        PosterAlign.RIGHT,
                        color = if (r.owes) PosterColors.RED else PosterColors.GREEN,
                    ),
                ),
            )
    }
    lines += PosterLine.TextLine("Venue pool remaining: $${p.poolDollars}", color = PosterColors.GRAY, gap = 0)
    return lines
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.PosterTest"` → PASS (3 tests). Then `gradlew.bat :domain:test ktlintCheck detekt` → green.

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): weekly and monthly poster line builders with English copy"
```

---

### Task 2: domain — poster layout geometry (TDD, port of poster.js + poster.test.js)

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/report/PosterLayout.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/report/PosterLayoutTest.kt`

**Interfaces:**
- Consumes: `PosterLine`, `PosterCell`, `PosterAlign`, `PosterColors` (Task 1).
- Produces (Task 4 renders these):
  - `const val POSTER_WIDTH = 750` (top-level in this file)
  - `data class PosterText(val text: String, val x: Int, val y: Int, val size: Int, val bold: Boolean, val color: String, val align: PosterAlign)`
  - `data class PosterRect(val x: Int, val y: Int, val w: Int, val h: Int, val color: String)`
  - `data class PosterLayout(val width: Int, val height: Int, val rects: List<PosterRect>, val texts: List<PosterText>)` — `rects[0]` is always the full white background.
  - `fun layoutPoster(lines: List<PosterLine>): PosterLayout`

**Geometry (verbatim from `utils/poster.js` drawLines):** `lineHeight = if divider 2+gap else size+gap`; total `height = 100 + Σ lineHeight`; cursor starts `y = 50`; divider emits `PosterRect(40, y, 670, 2, DIVIDER)` then `y += 2 + gap`; text/cells lines do `y += size`, emit texts at baseline `y`, then `y += gap`. Text placement: cells at each `cell.x` with its align (cell color ?: line color); centered text at `x = 375` CENTER; text-with-right at `x = 40` LEFT plus right text at `x = 710` RIGHT (color `rightColor ?: color`); plain text at `x = 40` LEFT.

- [ ] **Step 1: Write the failing test** (port of `tests/poster.test.js`, same numbers)

`PosterLayoutTest.kt`:
```kotlin
package com.badmintonledger.domain.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PosterLayoutTest {
    @Test
    fun `cells line - each cell drawn at its own x align color on one baseline`() {
        val layout = layoutPoster(
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
        val layout = layoutPoster(
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.PosterLayoutTest"`
Expected: FAIL — compilation error.

- [ ] **Step 3: Implement**

`PosterLayout.kt`:
```kotlin
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
                    texts += PosterText(
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
                        texts += PosterText(
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.PosterLayoutTest"` → PASS (3 tests). Then `gradlew.bat :domain:test ktlintCheck detekt` → green.

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): poster layout geometry ported from WeChat canvas drawing"
```

---

### Task 3: domain — report picker options (TDD)

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/report/ReportOptions.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/report/ReportOptionsTest.kt`

**Interfaces:**
- Consumes: `LedgerData`, `sessionRealCostCents`, `centsToDollars`.
- Produces (Task 5):
  - `data class WeekOption(val sessionId: String, val label: String)` — label `"2026-07-04 (paid $76.80)"`
  - `data class ReportOptions(val weeks: List<WeekOption>, val months: List<String>)`
  - `fun reportOptions(data: LedgerData): ReportOptions` — sessions sorted by date DESCENDING (newest first, port of report.js onShow); months are the distinct `date.take(7)` values in that same order.

- [ ] **Step 1: Write the failing test**

`ReportOptionsTest.kt`:
```kotlin
package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportOptionsTest {
    @Test
    fun `weeks newest first with paid labels, months distinct in same order`() {
        val data = LedgerData(
            sessions = listOf(
                Session("s1", "2026-06-27", 4.0, Cents(2400), 0.8, listOf("A")),
                Session("s2", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A")),
                Session("s3", "2026-07-11", 1.0, Cents(2400), 1.0, listOf("A")),
            ),
        )
        val o = reportOptions(data)
        assertEquals(listOf("s3", "s2", "s1"), o.weeks.map { it.sessionId })
        assertEquals("2026-07-11 (paid $24.00)", o.weeks[0].label)
        assertEquals("2026-07-04 (paid $76.80)", o.weeks[1].label)
        assertEquals(listOf("2026-07", "2026-06"), o.months)
    }

    @Test
    fun `empty ledger yields empty options`() {
        val o = reportOptions(LedgerData())
        assertEquals(emptyList(), o.weeks)
        assertEquals(emptyList(), o.months)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.ReportOptionsTest"`
Expected: FAIL — compilation error.

- [ ] **Step 3: Implement**

`ReportOptions.kt`:
```kotlin
package com.badmintonledger.domain.report

import com.badmintonledger.domain.calc.sessionRealCostCents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.centsToDollars

data class WeekOption(val sessionId: String, val label: String)

data class ReportOptions(val weeks: List<WeekOption>, val months: List<String>)

// Port of pages/report/report.js onShow: sessions newest first; months distinct in that order.
fun reportOptions(data: LedgerData): ReportOptions {
    val sorted = data.sessions.sortedByDescending { it.date }
    val weeks = sorted.map {
        WeekOption(it.id, "${it.date} (paid $${centsToDollars(sessionRealCostCents(it))})")
    }
    val months = sorted.map { it.date.take(7) }.distinct()
    return ReportOptions(weeks, months)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.ReportOptionsTest"` → PASS (2 tests). Then full `gradlew.bat :domain:test ktlintCheck detekt` → green.

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): report picker options - weeks newest first, distinct months"
```

---

### Task 4: app — poster renderer and share plumbing

**Files:**
- Create: `app/src/main/kotlin/com/badmintonledger/app/poster/PosterRenderer.kt`
- Create: `app/src/main/kotlin/com/badmintonledger/app/poster/PosterShare.kt`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml` (FileProvider inside `<application>`)
- Modify: `gradle/libs.versions.toml` + `app/build.gradle.kts` (explicit androidx-core-ktx)

**Interfaces:**
- Consumes: `PosterLayout`, `PosterText`, `PosterRect`, `PosterAlign` (Task 2).
- Produces (Task 5):
  - `fun renderPoster(layout: PosterLayout): Bitmap` — pure mapping, no scaling.
  - `suspend fun sharePoster(context: Context, bitmap: Bitmap)` — writes `poster.png` into `cacheDir/posters/` on Dispatchers.IO, then fires an `ACTION_SEND` `image/png` chooser with a read-granted FileProvider URI. Authority: `com.badmintonledger.fileprovider`.

- [ ] **Step 1: Add the core-ktx dependency**

`gradle/libs.versions.toml` — `[versions]`: `coreKtx = "1.16.0"`; `[libraries]`:
```toml
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
```
`app/build.gradle.kts` dependencies: `implementation(libs.androidx.core.ktx)`
(Bump to nearest stable if the pin fails to resolve; note it in the commit.)

- [ ] **Step 2: Implement renderer**

`PosterRenderer.kt`:
```kotlin
package com.badmintonledger.app.poster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.badmintonledger.domain.report.PosterAlign
import com.badmintonledger.domain.report.PosterLayout

/** Draws the domain-computed poster primitives 1:1 onto a bitmap (750px wide, like WeChat). */
fun renderPoster(layout: PosterLayout): Bitmap {
    val bitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    layout.rects.forEach { r ->
        paint.color = Color.parseColor(r.color)
        canvas.drawRect(
            r.x.toFloat(),
            r.y.toFloat(),
            (r.x + r.w).toFloat(),
            (r.y + r.h).toFloat(),
            paint,
        )
    }
    layout.texts.forEach { t ->
        paint.color = Color.parseColor(t.color)
        paint.textSize = t.size.toFloat()
        paint.typeface = if (t.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textAlign = when (t.align) {
            PosterAlign.LEFT -> Paint.Align.LEFT
            PosterAlign.CENTER -> Paint.Align.CENTER
            PosterAlign.RIGHT -> Paint.Align.RIGHT
        }
        canvas.drawText(t.text, t.x.toFloat(), t.y.toFloat(), paint)
    }
    return bitmap
}
```

- [ ] **Step 3: Implement share plumbing**

`PosterShare.kt`:
```kotlin
package com.badmintonledger.app.poster

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val AUTHORITY = "com.badmintonledger.fileprovider"

/** Writes the poster PNG to cache and opens the system share sheet. */
suspend fun sharePoster(context: Context, bitmap: Bitmap) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "posters").apply { mkdirs() }
        val file = File(dir, "poster.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, AUTHORITY, file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share poster"))
}
```

`app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="posters" path="posters/" />
</paths>
```

`AndroidManifest.xml` — inside `<application>`:
```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="com.badmintonledger.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 4: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 5: Commit**

```powershell
git add -A -- gradle app
git commit -m "feat(app): poster bitmap renderer and share-sheet plumbing via FileProvider"
```

---

### Task 5: app — Report screen, route, Home button, session handoff

**Files:**
- Create: `app/src/main/kotlin/com/badmintonledger/app/ui/ReportScreen.kt`
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/AppNav.kt` (route `report?sessionId={sessionId}`; SessionScreen handoff)
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/HomeScreen.kt` (Report button)
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/SessionScreen.kt` (signature gains `onSaved: (String) -> Unit`)

**Interfaces:**
- Consumes: `vm.ledger`; domain `reportOptions`, `buildWeeklyPayload`, `buildMonthlyPayload`, `weeklyPosterLines`, `monthlyPosterLines`, `layoutPoster`; app `renderPoster`, `sharePoster`.
- Produces: route `"report?sessionId={sessionId}"`; `ReportScreen(vm: LedgerViewModel, initialSessionId: String?, onBack: () -> Unit)`; `SessionScreen(vm, onBack, onSaved: (sessionId: String) -> Unit)` — on successful save the screen calls `onSaved(savedSessionId)` instead of `onBack()` (WeChat redirects to the report page with the session preselected and auto-generates). `vm.saveSession` must therefore return the saved session id on success — change its contract from `String?` to `SaveSessionResult`:
  - In `LedgerViewModel.kt` add `sealed interface SaveSessionResult { data class Saved(val sessionId: String) : SaveSessionResult; data class Rejected(val reason: String) : SaveSessionResult }` and change `saveSession` to return it (`Saved(s.id)` from the domain `EditResult.Ok` value on both branches, `Rejected(...)` for every current `String` return). SessionScreen's save handler updates accordingly (`is Rejected -> saving = false; snackbar`, `is Saved -> onSaved(r.sessionId)`).

- [ ] **Step 1: ViewModel + SessionScreen contract change**

In `LedgerViewModel.kt`: add the sealed interface (file-level, above the class); change `saveSession`'s return type and returns:
```kotlin
sealed interface SaveSessionResult {
    data class Saved(val sessionId: String) : SaveSessionResult
    data class Rejected(val reason: String) : SaveSessionResult
}
```
Every `return "..."` in `saveSession` becomes `return SaveSessionResult.Rejected("...")`; the success path becomes:
```kotlin
return when (result) {
    is EditResult.Ok -> {
        persist(result.data)
        SaveSessionResult.Saved(result.value.id)
    }
    is EditResult.Err -> SaveSessionResult.Rejected(result.reason)
}
```
(Domain `addSession`/`updateSession` both return `EditResult<Session>`, so `result.value.id` is available on both branches.)

In `SessionScreen.kt`: signature becomes
```kotlin
fun SessionScreen(vm: LedgerViewModel, onBack: () -> Unit, onSaved: (String) -> Unit)
```
and the save `onClick` becomes:
```kotlin
                    saving = true
                    when (val r = vm.saveSession(editId, date, hours.toDoubleOrNull(), rate.toDoubleOrNull(), factor.toDoubleOrNull(), selectedIds)) {
                        is SaveSessionResult.Saved -> onSaved(r.sessionId)
                        is SaveSessionResult.Rejected -> {
                            saving = false
                            scope.launch { snackbar.showSnackbar(r.reason) }
                        }
                    }
```
(Adapt local variable names to the file's existing ones — read the file first; the structure with the `saving` guard from M3 must be preserved.)

- [ ] **Step 2: Report screen**

`ReportScreen.kt`:
```kotlin
package com.badmintonledger.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.app.poster.renderPoster
import com.badmintonledger.app.poster.sharePoster
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.report.buildMonthlyPayload
import com.badmintonledger.domain.report.buildWeeklyPayload
import com.badmintonledger.domain.report.layoutPoster
import com.badmintonledger.domain.report.monthlyPosterLines
import com.badmintonledger.domain.report.reportOptions
import com.badmintonledger.domain.report.weeklyPosterLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun buildPoster(data: LedgerData, week: Boolean, sessionId: String?, month: String?): Bitmap? {
    val lines = if (week) {
        val id = sessionId ?: return null
        weeklyPosterLines(buildWeeklyPayload(data, id))
    } else {
        val ym = month ?: return null
        monthlyPosterLines(buildMonthlyPayload(data, ym))
    }
    return renderPoster(layoutPoster(lines))
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ReportScreen(vm: LedgerViewModel, initialSessionId: String?, onBack: () -> Unit) {
    val data by vm.ledger.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var weekMode by remember { mutableStateOf(true) }
    var sessionId by remember { mutableStateOf(initialSessionId) }
    var month by remember { mutableStateOf<String?>(null) }
    var poster by remember { mutableStateOf<Bitmap?>(null) }
    var weekMenu by remember { mutableStateOf(false) }
    var monthMenu by remember { mutableStateOf(false) }

    val current = data
    val options = remember(current) { current?.let { reportOptions(it) } }
    LaunchedEffect(options) {
        val o = options ?: return@LaunchedEffect
        if (sessionId == null || o.weeks.none { it.sessionId == sessionId }) {
            sessionId = o.weeks.firstOrNull()?.sessionId
        }
        if (month == null || month !in o.months) month = o.months.firstOrNull()
        // arriving from a session save: auto-generate this week's poster
        if (initialSessionId != null && poster == null && current != null) {
            poster = withContext(Dispatchers.Default) {
                buildPoster(current, week = true, sessionId = sessionId, month = month)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (current == null || options == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = weekMode,
                        onClick = { weekMode = true; poster = null },
                        label = { Text("Weekly") },
                    )
                    FilterChip(
                        selected = !weekMode,
                        onClick = { weekMode = false; poster = null },
                        label = { Text("Monthly") },
                    )
                }
            }
            item {
                if (weekMode) {
                    ExposedDropdownMenuBox(expanded = weekMenu, onExpandedChange = { weekMenu = it }) {
                        OutlinedTextField(
                            value = options.weeks.firstOrNull { it.sessionId == sessionId }?.label ?: "No weeks recorded",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Week") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weekMenu) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = weekMenu, onDismissRequest = { weekMenu = false }) {
                            options.weeks.forEach { w ->
                                DropdownMenuItem(
                                    text = { Text(w.label) },
                                    onClick = { sessionId = w.sessionId; poster = null; weekMenu = false },
                                )
                            }
                        }
                    }
                } else {
                    ExposedDropdownMenuBox(expanded = monthMenu, onExpandedChange = { monthMenu = it }) {
                        OutlinedTextField(
                            value = month ?: "No months recorded",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Month") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthMenu) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                            options.months.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = { month = m; poster = null; monthMenu = false },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = if (weekMode) sessionId != null else month != null,
                        onClick = {
                            scope.launch {
                                val bmp = withContext(Dispatchers.Default) {
                                    buildPoster(current, weekMode, sessionId, month)
                                }
                                if (bmp == null) {
                                    snackbar.showSnackbar("Nothing to generate yet")
                                } else {
                                    poster = bmp
                                }
                            }
                        },
                    ) { Text("Generate poster") }
                    OutlinedButton(
                        enabled = poster != null,
                        onClick = {
                            val bmp = poster ?: return@OutlinedButton
                            scope.launch { sharePoster(context, bmp) }
                        },
                    ) { Text("Share") }
                }
            }
            item {
                poster?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Poster preview",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Navigation + Home button**

`AppNav.kt` — add the import for `ReportScreen` consumers as needed and:
- Session route wiring becomes:
```kotlin
        composable("session") {
            SessionScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onSaved = { sessionId ->
                    nav.navigate("report?sessionId=$sessionId") {
                        launchSingleTop = true
                        popUpTo("home")
                    }
                },
            )
        }
```
- New report route (imports: `androidx.navigation.NavType`, `androidx.navigation.navArgument`):
```kotlin
        composable(
            route = "report?sessionId={sessionId}",
            arguments = listOf(
                navArgument("sessionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            ReportScreen(
                vm = vm,
                initialSessionId = entry.arguments?.getString("sessionId"),
                onBack = { nav.popBackStack() },
            )
        }
```
`HomeScreen.kt` — the action-button row gains a fourth button after "Payment":
```kotlin
                OutlinedButton(onClick = onOpenReport) { Text("Report") }
```
with `HomeScreen` signature gaining `onOpenReport: () -> Unit` and AppNav passing `onOpenReport = { nav.navigate("report") { launchSingleTop = true } }`. (Read HomeScreen first; keep existing button style — if the existing three use `Button`/`OutlinedButton` variants, match the row's smallest variant so four fit; wrap the row in `horizontalScroll(rememberScrollState())` if they overflow.)

- [ ] **Step 4: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 5: Commit**

```powershell
git add app/src
git commit -m "feat(app): Report screen with poster preview, share, and session-save handoff"
```

---

### Task 6: acceptance — full gates, install, poster verification

**Files:** none (verification task)

- [ ] **Step 1: Full gates**

Run: `gradlew.bat test ktlintCheck detekt assembleDebug`
Expected: BUILD SUCCESSFUL; domain suite = previous 54 + 8 new (PosterTest 3, PosterLayoutTest 3, ReportOptionsTest 2) = 62 total (1 skipped RealBackupTest).

- [ ] **Step 2: Install on the phone (requires device attached)**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk` → `Success`; launch the app.

- [ ] **Step 3: On-phone manual checklist (controller verifies what it can; the human confirms the rest)**

1. Home shows the Report button; opens the Report screen.
2. With recorded weeks: weekly poster generates, preview shows title/rows/pool matching the WeChat app's numbers for the same data.
3. Monthly poster generates with the table layout.
4. Share opens the system share sheet; sharing into WeChat delivers a legible 750px-wide PNG.
5. Saving a session navigates to the Report screen with that week preselected and the poster auto-generated (WeChat parity).
6. `backups/real-backup.json` (when Max provides it): RealBackupTest runs and passes; spot-check 2-3 poster numbers against the WeChat app's poster for the same week.

## Milestone 4 Acceptance Checklist

- [ ] `gradlew test ktlintCheck detekt` green (62 domain tests, 1 skipped)
- [ ] `assembleDebug` builds; APK installs and launches
- [ ] Weekly and monthly posters render and share into WeChat, numerically identical to the WeChat app
- [ ] Session save lands on the Report screen with the poster auto-generated
- [ ] No `android.*` import under `domain/`
