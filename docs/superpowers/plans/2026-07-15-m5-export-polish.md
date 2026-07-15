# Milestone 5: Export + History + Icon + Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backup export through the share sheet, the History screen (browse/edit/delete records), the report-polish debt, an app icon, and a release-signed APK — completing the WeChat-app replacement.

**Architecture:** Domain gains a pure history-rows builder (port of WeChat `pages/history/history.js`) and a pretty-printing export encoder (WeChat exports `JSON.stringify(d, null, 2)`). Shared raw-number formatting is promoted out of `Poster.kt` into `domain/report/Format.kt`. App gains delete actions on the ViewModel, a backup-export share helper (same FileProvider pattern as the poster), the History screen with WeChat's edit/delete semantics, an `editId` route into the Session screen, an adaptive launcher icon, and a `release` signing config fed by a gitignored `keystore.properties`.

**Tech Stack:** No new dependencies. `keytool` from the local JDK 17 for the keystore.

## Global Constraints

- `domain/` pure Kotlin, no `android.*`; money integer cents; derived values recomputed from the document.
- WeChat parity ground truth: `E:\Code\ai\wechat\badminton\pages\history\history.js` and the `exportData` block of `pages\settings\settings.js`. History sessions are cut off at 1 year (cutoff computed app-side, passed into the pure builder); all lists newest-first; deletes confirm with "balances recalculate" copy; session rows offer Edit + Delete, refill/payment rows offer Delete.
- Export JSON is pretty-printed (2-space indent) like WeChat's `JSON.stringify(d, null, 2)`; the frozen v1 contract is unchanged; file name from the existing `BackupCodec.exportFileName` (`badminton-backup-YYYY-MM-DD.json`).
- Standing constraints: `this.saving` guard on mutating screens (History deletes go through confirm dialogs; the dialogs close before persist — same benign single-frame window as M3, acceptable); UI may call domain view-builders/`dollarsToCents` for display only; persistence conversions only in the VM.
- UI copy English; money `$X.XX` (raw trimmed dollars where WeChat prints raw JS numbers).
- Release build: `isMinifyEnabled = false` (YAGNI — no ProGuard risk for a personal app); signing config reads `keystore.properties` at the repo root and MUST degrade gracefully (unsigned release) when the file is absent so clean checkouts still build. `*.jks` and `keystore.properties` are already gitignored — NEVER commit either.
- TDD for domain; gates green at every commit; conventional commits; branch `feat/m5-export-polish` off `main`.

## File Structure

```
domain/src/main/kotlin/com/badmintonledger/domain/report/Format.kt      Task 1 — rawDollars/rawNumber (promoted from Poster.kt privates)
domain/src/main/kotlin/com/badmintonledger/domain/report/History.kt     Task 1 — history rows builder
domain/src/test/kotlin/com/badmintonledger/domain/report/HistoryTest.kt Task 1
domain/src/main/kotlin/com/badmintonledger/domain/backup/BackupCodec.kt Task 2 — add encodePretty
domain/src/test/kotlin/com/badmintonledger/domain/backup/BackupCodecTest.kt Task 2
app/src/main/kotlin/com/badmintonledger/app/LedgerViewModel.kt          Task 3 — delete actions
app/src/main/kotlin/com/badmintonledger/app/backup/BackupExport.kt      Task 3 — share helper
app/src/main/res/xml/file_paths.xml                                     Task 3 — add exports/
app/src/main/kotlin/com/badmintonledger/app/ui/SettingsScreen.kt        Task 3 — Export button
app/src/main/kotlin/com/badmintonledger/app/ui/HistoryScreen.kt         Task 4
app/src/main/kotlin/com/badmintonledger/app/ui/AppNav.kt                Task 4 — history route + session?editId=
app/src/main/kotlin/com/badmintonledger/app/ui/HomeScreen.kt            Task 4 — History button
app/src/main/kotlin/com/badmintonledger/app/ui/SessionScreen.kt         Task 4 — editId init branch
app/src/main/kotlin/com/badmintonledger/app/ui/ReportScreen.kt          Task 5 — polish
app/src/main/kotlin/com/badmintonledger/app/poster/PosterShare.kt       Task 5 — authority via packageName
app/src/main/AndroidManifest.xml                                        Task 5 (authority placeholder) + Task 6 (icon)
app/src/main/kotlin/com/badmintonledger/domain/... (no other changes)
app/src/main/res/drawable/ic_launcher_foreground.xml                    Task 6
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml Task 6
app/src/main/res/values/colors.xml                                      Task 6
app/build.gradle.kts                                                    Task 7 — release signing
keystore.properties + release.jks (repo root, GITIGNORED, generated)    Task 7
```

---

### Task 1: domain — shared raw formatting + history rows builder (TDD)

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/report/Format.kt`
- Modify: `domain/src/main/kotlin/com/badmintonledger/domain/report/Poster.kt` (delete private `plainDollars`/`plainNumber`, use the new public functions)
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/report/History.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/report/HistoryTest.kt`

**Interfaces:**
- Produces:
  - `fun rawDollars(c: Cents): String` — `"24"`, `"24.9"`, `"25.61"` (2-dp then trailing zeros trimmed; the JS raw-number print)
  - `fun rawNumber(v: Double): String` — `"4"`, `"1.5"`
  - `data class SessionHistoryRow(val id: String, val date: String, val desc: String, val names: String, val realDollars: String)`
  - `data class RefillHistoryRow(val id: String, val date: String, val desc: String)`
  - `data class PaymentHistoryRow(val id: String, val date: String, val desc: String)`
  - `data class HistoryRows(val sessions: List<SessionHistoryRow>, val refills: List<RefillHistoryRow>, val payments: List<PaymentHistoryRow>)`
  - `fun buildHistoryRows(data: LedgerData, cutoff: String): HistoryRows` — sessions with `date >= cutoff` only (refills/payments unfiltered, per history.js), all sorted newest-first.
- English copy (WeChat → English): session desc `4小时 × $24，3人` → `4h × $24, 3 players`; refill `实付 $2000 → 到账 $2500` → `Paid $2000 → credit $2500`; payment `阿安 交来 $25.6` → `阿安 paid $25.6`; unknown member `未知` → `Unknown`; names joined with `", "` (WeChat uses `、`).

- [ ] **Step 1: Write the failing test**

`HistoryTest.kt`:
```kotlin
package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Payment
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryTest {
    private fun fixture() = LedgerData(
        members = listOf(Member("A", "阿安", false), Member("G", "客串", true)),
        refills = listOf(
            Refill("r1", "2026-07-01", Cents(200000), Cents(250000), emptyList()),
            Refill("r0", "2024-01-01", Cents(100000), Cents(125000), emptyList()),
        ),
        payments = listOf(Payment("p1", "G", Cents(2560), "2026-07-05")),
        sessions = listOf(
            Session("sOld", "2024-06-01", 4.0, Cents(2400), 0.8, listOf("A")),
            Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "G")),
            Session("s2", "2026-07-11", 1.5, Cents(2561), 1.0, listOf("A")),
        ),
    )

    @Test
    fun `sessions cut off and sorted, descriptions and names match WeChat shapes`() {
        val h = buildHistoryRows(fixture(), cutoff = "2025-07-15")
        assertEquals(listOf("s2", "s1"), h.sessions.map { it.id })
        assertEquals("1.5h × $25.61, 1 players", h.sessions[0].desc)
        assertEquals("阿安", h.sessions[0].names)
        assertEquals("38.42", h.sessions[0].realDollars)
        assertEquals("4h × $24, 2 players", h.sessions[1].desc)
        assertEquals("阿安, 客串", h.sessions[1].names)
        assertEquals("76.80", h.sessions[1].realDollars)
    }

    @Test
    fun `refills and payments unfiltered, sorted, with raw-dollar descriptions`() {
        val h = buildHistoryRows(fixture(), cutoff = "2025-07-15")
        assertEquals(listOf("r1", "r0"), h.refills.map { it.id })
        assertEquals("Paid $2000 → credit $2500", h.refills[0].desc)
        assertEquals("Paid $1000 → credit $1250", h.refills[1].desc)
        assertEquals("客串 paid $25.6", h.payments[0].desc)
    }

    @Test
    fun `unknown member id renders Unknown`() {
        val data = fixture().copy(payments = listOf(Payment("p2", "GHOST", Cents(100), "2026-07-06")))
        assertEquals("Unknown paid $1", buildHistoryRows(data, "2020-01-01").payments[0].desc)
    }
}
```
- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.HistoryTest"`
Expected: FAIL — compilation error (`buildHistoryRows` unresolved).

- [ ] **Step 3: Implement**

`Format.kt`:
```kotlin
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
```

`Poster.kt`: delete the private `plainDollars`/`plainNumber` functions and replace their two call sites with `rawDollars(p.rate)` / `rawNumber(p.hours)` (same file's package — no import needed). The existing PosterTest suite is the regression guard.

`History.kt`:
```kotlin
package com.badmintonledger.domain.report

import com.badmintonledger.domain.calc.sessionRealCostCents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.centsToDollars

data class SessionHistoryRow(
    val id: String,
    val date: String,
    val desc: String,
    val names: String,
    val realDollars: String,
)

data class RefillHistoryRow(val id: String, val date: String, val desc: String)

data class PaymentHistoryRow(val id: String, val date: String, val desc: String)

data class HistoryRows(
    val sessions: List<SessionHistoryRow>,
    val refills: List<RefillHistoryRow>,
    val payments: List<PaymentHistoryRow>,
)

// Port of pages/history/history.js refresh: sessions cut off at [cutoff], everything newest-first.
fun buildHistoryRows(data: LedgerData, cutoff: String): HistoryRows {
    fun nameOf(id: String) = data.members.firstOrNull { it.id == id }?.name ?: "Unknown"
    val sessions = data.sessions
        .filter { it.date >= cutoff }
        .sortedByDescending { it.date }
        .map { s ->
            SessionHistoryRow(
                id = s.id,
                date = s.date,
                desc = "${rawNumber(s.hours)}h × $${rawDollars(s.rate)}, ${s.playerIds.size} players",
                names = s.playerIds.joinToString(", ") { nameOf(it) },
                realDollars = centsToDollars(sessionRealCostCents(s)),
            )
        }
    val refills = data.refills.sortedByDescending { it.date }.map { r ->
        RefillHistoryRow(r.id, r.date, "Paid $${rawDollars(r.paid)} → credit $${rawDollars(r.credit)}")
    }
    val payments = data.payments.sortedByDescending { it.date }.map { p ->
        PaymentHistoryRow(p.id, p.date, "${nameOf(p.memberId)} paid $${rawDollars(p.amount)}")
    }
    return HistoryRows(sessions, refills, payments)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test` (HistoryTest 3 new PLUS the whole existing suite — the Poster.kt refactor must not break PosterTest). Then `ktlintCheck detekt` → green.

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): history rows builder and shared raw-dollar formatting"
```

---

### Task 2: domain — pretty-printed export encoder (TDD)

**Files:**
- Modify: `domain/src/main/kotlin/com/badmintonledger/domain/backup/BackupCodec.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/backup/BackupCodecTest.kt` (add one test)

**Interfaces:**
- Produces: `fun BackupCodec.encodePretty(data: LedgerData): String` (member of the object) — 2-space-indented JSON, `encodeDefaults = true`, same contract as `encode`. Task 3 uses it for export files.

- [ ] **Step 1: Write the failing test** — add to `BackupCodecTest.kt`:
```kotlin
    @Test
    fun `pretty export is indented, valid and round-trips`() {
        val data = BackupCodec.decode(fixture())
        val pretty = BackupCodec.encodePretty(data)
        assertTrue(pretty.contains("\n  \"version\""))
        assertIs<ImportResult.Ok>(BackupCodec.validate(pretty))
        assertEquals(data, BackupCodec.decode(pretty))
    }
```
(`assertTrue` import may need adding: `kotlin.test.assertTrue`.)

- [ ] **Step 2: Run to verify it fails** — `gradlew.bat :domain:test --tests "com.badmintonledger.domain.backup.BackupCodecTest"` → FAIL (unresolved `encodePretty`).

- [ ] **Step 3: Implement** — in `BackupCodec`, alongside the existing `json`:
```kotlin
    @OptIn(ExperimentalSerializationApi::class)
    private val prettyJson = Json {
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  " // WeChat exports JSON.stringify(d, null, 2)
    }

    /** Export encoding: pretty-printed like WeChat's JSON.stringify(d, null, 2). */
    fun encodePretty(data: LedgerData): String = prettyJson.encodeToString(LedgerData.serializer(), data)
```

- [ ] **Step 4: Run to green** — `gradlew.bat :domain:test ktlintCheck detekt` → green (BackupCodecTest 8 tests).

- [ ] **Step 5: Commit**
```powershell
git add domain/src
git commit -m "feat(domain): pretty-printed backup export encoding"
```

---

### Task 3: app — delete actions, backup export share, Settings button

**Files:**
- Modify: `app/src/main/kotlin/com/badmintonledger/app/LedgerViewModel.kt` (3 delete actions)
- Create: `app/src/main/kotlin/com/badmintonledger/app/backup/BackupExport.kt`
- Modify: `app/src/main/res/xml/file_paths.xml` (add exports path)
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/SettingsScreen.kt` (Export button in the Data section)

**Interfaces:**
- Produces:
  - `fun LedgerViewModel.deleteSession(id: String)` / `deleteRefill(id)` / `deletePayment(id)` — read `ledger.value`, apply the domain delete (returns `LedgerData`), `persist`. No-op before first load.
  - `suspend fun shareBackup(context: Context, data: LedgerData, dateStr: String)` in `com.badmintonledger.app.backup` — writes `BackupCodec.exportFileName(dateStr)` under `cacheDir/exports/` with `BackupCodec.encodePretty(data)` on Dispatchers.IO, then ACTION_SEND `application/json` chooser "Share backup" with the FileProvider URI (same authority as the poster).

- [ ] **Step 1: ViewModel deletes** — add (with aliased imports `deleteSession as domainDeleteSession` etc., matching the file's alias convention):
```kotlin
    fun deleteSession(id: String) {
        val current = ledger.value ?: return
        persist(domainDeleteSession(current, id))
    }

    fun deleteRefill(id: String) {
        val current = ledger.value ?: return
        persist(domainDeleteRefill(current, id))
    }

    fun deletePayment(id: String) {
        val current = ledger.value ?: return
        persist(domainDeletePayment(current, id))
    }
```

- [ ] **Step 2: Export helper**

`BackupExport.kt`:
```kotlin
package com.badmintonledger.app.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.badmintonledger.domain.backup.BackupCodec
import com.badmintonledger.domain.model.LedgerData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes a pretty-printed backup JSON to cache and opens the system share sheet. */
suspend fun shareBackup(context: Context, data: LedgerData, dateStr: String) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, BackupCodec.exportFileName(dateStr))
        file.writeText(BackupCodec.encodePretty(data))
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share backup"))
}
```
NOTE: this uses `${context.packageName}.fileprovider`. Task 5 aligns the manifest + PosterShare to the same derivation; until Task 5 lands the manifest literal `com.badmintonledger.fileprovider` already equals `packageName + ".fileprovider"` (applicationId `com.badmintonledger`), so this works immediately.

`file_paths.xml` — add alongside the existing entry:
```xml
    <cache-path name="exports" path="exports/" />
```

- [ ] **Step 3: Settings button** — in `SettingsScreen.kt`'s Data section (next to "Import backup"), add:
```kotlin
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importPicker.launch(arrayOf("*/*")) }) {
                        Text("Import backup")
                    }
                    OutlinedButton(
                        onClick = {
                            val current = data ?: return@OutlinedButton
                            scope.launch {
                                shareBackup(context, current, LocalDate.now().toString())
                            }
                        },
                    ) { Text("Export backup") }
                }
            }
```
(replacing the existing single import-button item; imports to add: `androidx.compose.material3.OutlinedButton`, `com.badmintonledger.app.backup.shareBackup`, `java.time.LocalDate`. Read the file first and adapt to its existing structure — `context` and `scope` already exist there.)

- [ ] **Step 4: Build + gates** — `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 5: Commit**
```powershell
git add app/src
git commit -m "feat(app): backup export via share sheet and record delete actions"
```

---

### Task 4: app — History screen, route, Home button, session editId

**Files:**
- Create: `app/src/main/kotlin/com/badmintonledger/app/ui/HistoryScreen.kt`
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/AppNav.kt` (history route; session route gains `?editId={editId}`)
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/HomeScreen.kt` (History button)
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/SessionScreen.kt` (init from `editSessionId` when provided)

**Interfaces:**
- `HistoryScreen(vm: LedgerViewModel, onBack: () -> Unit, onEditSession: (String) -> Unit)`
- `SessionScreen(vm: LedgerViewModel, onBack: () -> Unit, onSaved: (String) -> Unit, editSessionId: String? = null)` — when `editSessionId != null`, init prefils from `data.sessions.firstOrNull { it.id == editSessionId }` (edit mode regardless of week) instead of `findSessionInWeek(today)`; everything else unchanged.
- Routes: `"history"`; session route becomes `"session?editId={editId}"` with a nullable string arg (Home's Record-week button navigates to plain `"session"`).
- History behavior (port of history.js): cutoff = today minus 365 days (`LocalDate.now().minusDays(365).toString()` computed in the screen, passed to `buildHistoryRows`); three sections: "Weekly records (last 12 months)", "Refills", "Payments"; session row tap → dialog with Edit / Delete / Cancel; refill/payment row tap → delete confirm; delete confirm copy: title "Delete record", text "Deleting recalculates all balances automatically. Continue?", buttons Delete / Cancel.

- [ ] **Step 1: History screen**

`HistoryScreen.kt`:
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.domain.report.buildHistoryRows
import java.time.LocalDate

private sealed interface HistoryAction {
    data class SessionMenu(val id: String, val label: String) : HistoryAction

    data class ConfirmDelete(val label: String, val delete: () -> Unit) : HistoryAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun HistoryScreen(vm: LedgerViewModel, onBack: () -> Unit, onEditSession: (String) -> Unit) {
    val data by vm.ledger.collectAsState()
    var action by remember { mutableStateOf<HistoryAction?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = data ?: return@Scaffold
        val cutoff = remember { LocalDate.now().minusDays(365).toString() }
        val rows = remember(current) { buildHistoryRows(current, cutoff) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Weekly records (last 12 months)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(rows.sessions, key = { "s" + it.id }) { s ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { action = HistoryAction.SessionMenu(s.id, s.date) }
                        .padding(vertical = 4.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.date, style = MaterialTheme.typography.titleSmall)
                        Text("$${s.realDollars}", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(s.desc, style = MaterialTheme.typography.bodySmall)
                    Text(
                        s.names,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Text("Refills", style = MaterialTheme.typography.titleMedium) }
            items(rows.refills, key = { "r" + it.id }) { r ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            action = HistoryAction.ConfirmDelete(r.date) { vm.deleteRefill(r.id) }
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Text(r.date, style = MaterialTheme.typography.titleSmall)
                    Text(r.desc, style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Text("Payments", style = MaterialTheme.typography.titleMedium) }
            items(rows.payments, key = { "p" + it.id }) { p ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            action = HistoryAction.ConfirmDelete(p.date) { vm.deletePayment(p.id) }
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Text(p.date, style = MaterialTheme.typography.titleSmall)
                    Text(p.desc, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    when (val a = action) {
        is HistoryAction.SessionMenu ->
            AlertDialog(
                onDismissRequest = { action = null },
                title = { Text("Weekly record ${a.label}") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            action = null
                            onEditSession(a.id)
                        },
                    ) { Text("Edit") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            action = HistoryAction.ConfirmDelete(a.label) { vm.deleteSession(a.id) }
                        },
                    ) { Text("Delete") }
                },
            )
        is HistoryAction.ConfirmDelete ->
            AlertDialog(
                onDismissRequest = { action = null },
                title = { Text("Delete record") },
                text = { Text("Deleting recalculates all balances automatically. Continue?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            a.delete()
                            action = null
                        },
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { action = null }) { Text("Cancel") }
                },
            )
        null -> Unit
    }
}
```

- [ ] **Step 2: Session editId + nav + Home button**

`SessionScreen.kt`: add `editSessionId: String? = null` as the last parameter. In the one-time init block (the `LaunchedEffect(data)` + `initialized` guard), branch:
```kotlin
                val existing = if (editSessionId != null) {
                    current.sessions.firstOrNull { it.id == editSessionId }
                } else {
                    findSessionInWeek(current, LocalDate.now().toString())
                }
```
(the rest of the init — prefill vs defaults — is unchanged; read the file and keep its variable names.)

`AppNav.kt`:
- Session route becomes:
```kotlin
        composable(
            route = "session?editId={editId}",
            arguments = listOf(
                navArgument("editId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            SessionScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onSaved = { sessionId ->
                    nav.navigate("report?sessionId=$sessionId") {
                        launchSingleTop = true
                        popUpTo("home")
                    }
                },
                editSessionId = entry.arguments?.getString("editId"),
            )
        }
```
(Existing `nav.navigate("session")` call sites keep working — the arg is optional.)
- New history route:
```kotlin
        composable("history") {
            HistoryScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onEditSession = { id ->
                    nav.navigate("session?editId=$id") { launchSingleTop = true }
                },
            )
        }
```
`HomeScreen.kt`: add `onOpenHistory: () -> Unit` parameter and a fifth button `OutlinedButton(onClick = onOpenHistory) { Text("History") }` after Report; AppNav passes `onOpenHistory = { nav.navigate("history") { launchSingleTop = true } }`.

- [ ] **Step 3: Build + gates** — `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 4: Commit**
```powershell
git add app/src
git commit -m "feat(app): History screen with edit and delete, session deep-edit route"
```

---

### Task 5: app — report polish (M4 review debt)

**Files:**
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/ReportScreen.kt`
- Modify: `app/src/main/kotlin/com/badmintonledger/app/poster/PosterShare.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/HomeScreen.kt`
- Modify: `domain/src/main/kotlin/com/badmintonledger/domain/report/Poster.kt` + `PosterTest.kt` (plural fix)

Five precise changes (the M4 final review's triaged Minors):
1. `ReportScreen` auto-generate effect: add `weekMode` to the guard — only generate when `initialSessionId != null && poster == null && weekMode`.
2. `ReportScreen` `buildPoster`: `buildWeeklyPayload` call sites guard against a dangling id — in `buildPoster`, before building weekly lines: `if (data.sessions.none { it.id == id }) return null` (the null branch already shows "Nothing to generate yet").
3. `PosterShare.kt`: replace `private const val AUTHORITY = "com.badmintonledger.fileprovider"` with `FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)`; manifest `android:authorities` becomes `${applicationId}.fileprovider`.
4. Monthly subtitle plural: in `Poster.kt`, `"${p.weeks} sessions"` becomes `"${p.weeks} " + if (p.weeks == 1) "session" else "sessions"`; update the PosterTest assertion from `"2026-07 (1 sessions, total paid $76.80)"` to `"2026-07 (1 session, total paid $76.80)"` FIRST (red), then fix (green).
5. `HomeScreen` action row: replace `Row + horizontalScroll` with `FlowRow` (`androidx.compose.foundation.layout.FlowRow`, `@OptIn(ExperimentalLayoutApi::class)`) so all five buttons wrap instead of hiding off-screen.

- [ ] **Step 1: plural test first** (red) → fix Poster.kt (green)
- [ ] **Step 2: apply changes 1, 2, 3, 5** (read each file first; keep structures)
- [ ] **Step 3: gates** — `gradlew.bat assembleDebug test ktlintCheck detekt` → green
- [ ] **Step 4: Commit**
```powershell
git add app/src domain/src
git commit -m "fix(app): report polish - mode-gated autogenerate, id guard, authority placeholder, plural, FlowRow"
```

---

### Task 6: app — adaptive launcher icon

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/AndroidManifest.xml` (`android:icon`, `android:roundIcon`)

A stylized white shuttlecock on the ledger-green background (minSdk 26 ⇒ the anydpi-v26 adaptive icon is always used; no legacy PNGs needed).

- [ ] **Step 1: Resources**

`colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#2E7D32</color>
</resources>
```

`ic_launcher_foreground.xml` (feathers fanning from a cork, drawn inside the 66dp safe zone):
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- cork -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M54,70 m-9,0 a9,9 0 1,0 18,0 a9,9 0 1,0 -18,0" />
    <!-- cork band -->
    <path
        android:fillColor="#DDE8DC"
        android:pathData="M45.5,66 h17 v3 h-17 z" />
    <!-- left feather -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M50,62 L34,34 L41,31 L53,60 Z" />
    <!-- middle feather -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M52,59 L52,28 L58,28 L58,59 Z" />
    <!-- right feather -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M57,60 L69,31 L76,34 L60,62 Z" />
</vector>
```

`ic_launcher.xml` and `ic_launcher_round.xml` (identical content):
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

Manifest `<application>` gains:
```xml
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
```

- [ ] **Step 2: gates** — `gradlew.bat assembleDebug test ktlintCheck detekt` → green.
- [ ] **Step 3: Commit**
```powershell
git add app/src
git commit -m "feat(app): adaptive launcher icon - shuttlecock on ledger green"
```

---

### Task 7: build — release signing

**Files:**
- Modify: `app/build.gradle.kts`
- Generate (NEVER COMMIT — verify gitignored): `release.jks` + `keystore.properties` at the repo root

- [ ] **Step 1: Generate the keystore** (Bash; keytool from JDK 17; generate a random password and write both files):
```bash
export JAVA_HOME='/c/Users/MWang/AppData/Local/Java/jdk-17.0.19+10'
PASS=$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
"$JAVA_HOME/bin/keytool" -genkeypair -v -keystore release.jks -alias badminton \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=Badminton Ledger, O=Max"
printf 'storeFile=../release.jks\nstorePassword=%s\nkeyAlias=badminton\nkeyPassword=%s\n' "$PASS" "$PASS" > keystore.properties
git check-ignore release.jks keystore.properties   # MUST print both names
```

- [ ] **Step 2: Signing config** — in `app/build.gradle.kts` (top: `import java.util.Properties`):
```kotlin
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
```
inside `android {}`:
```kotlin
    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile").removePrefix("../"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
```

- [ ] **Step 3: Build both variants** — `gradlew.bat assembleDebug assembleRelease` → BUILD SUCCESSFUL; `app/build/outputs/apk/release/app-release.apk` exists (signed). Then full `test ktlintCheck detekt` green.

- [ ] **Step 4: Commit** (verify `git status` shows NO jks/properties):
```powershell
git status --short
git add app/build.gradle.kts
git commit -m "build(app): release signing from gitignored keystore.properties"
```

---

### Task 8: acceptance — full gates, release install, round-trip

- [ ] **Step 1:** `gradlew.bat test ktlintCheck detekt assembleDebug assembleRelease` → green; domain suite = 63 + 3 (HistoryTest) + 1 (encodePretty) + plural-adjusted = 67 total (1 skipped).
- [ ] **Step 2:** If a device is attached: `adb install -r app/build/outputs/apk/release/app-release.apk` (the RELEASE build — this is the daily-driver artifact) and launch.
- [ ] **Step 3: On-phone manual checklist** — new icon on the launcher; History browse/edit/delete; Settings → Export backup → share sheet → send the JSON to WeChat/yourself; import that same file back (round trip preserves everything); poster share (M4 acceptance); week-cycle numbers vs WeChat (M3 acceptance); drop the WeChat export at `backups/real-backup.json` for the parity test.
- [ ] **Step 4:** Remind Max to BACK UP `release.jks` + `keystore.properties` somewhere safe (losing them means losing the app identity for updates).

## Milestone 5 Acceptance Checklist

- [ ] `gradlew test ktlintCheck detekt` green (~67 domain tests, 1 skipped)
- [ ] `assembleRelease` produces a signed APK; installs and launches
- [ ] Export → share → re-import round trip preserves data exactly (device + WeChat cross-check)
- [ ] History shows/edits/deletes records with recalculated balances
- [ ] Launcher shows the new icon
- [ ] No `android.*` import under `domain/`; no keystore files in git
