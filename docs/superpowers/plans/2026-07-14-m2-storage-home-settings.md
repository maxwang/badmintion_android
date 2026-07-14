# Milestone 2: Storage + Home + Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app persists one `LedgerData` document in DataStore, shows the Home screen (balances, pool, warning) and Settings (members CRUD, defaults), and imports a WeChat backup via the Android file picker.

**Architecture:** `domain/` gains one pure function (`buildHomeSummary`, ported from the WeChat `home.js` display logic, TDD). `app/` gains: `LedgerApplication` (owns a `LedgerStore` — Preferences DataStore holding the JSON-serialized document under key `badminton_data_v1`), one shared `LedgerViewModel` (StateFlow of the document + thin actions delegating to `domain/edit`), a Navigation Compose host with `home` and `settings` routes, and the two screens. No DI, no Room, no network (YAGNI per spec).

**Tech Stack additions:** androidx.datastore:datastore-preferences 1.1.6, androidx.navigation:navigation-compose 2.8.9, androidx.lifecycle:lifecycle-viewmodel-compose 2.8.7 (bump to nearest stable if a pin fails to resolve; note it in the commit).

## Global Constraints

- `domain/` stays pure Kotlin: no `android.*` imports. All new Android code lives in `app/`.
- All money integer cents (`Long`) inside domain; dollars only in UI formatting (`$` + `centsToDollars`) and the JSON contract.
- One `LedgerData` document is the single source of truth; screens recompute derived values from it (`buildHomeSummary` runs on every emission); no incremental caches.
- Behavior parity with the WeChat pages (`E:\Code\ai\wechat\badminton\pages\home\home.js`, `pages\settings\settings.js`):
  - Home hides members with zero balance who never funded a refill (`hasContributed`); shows funded members even at zero balance; guest members marked; owes = negative balance shown as positive amount labelled owing.
  - Pool warning when pool < one typical session = 4h × default rate: `poolRemainingCents(data) < data.config.defaultRate.value * 4` (strict `<`).
  - Members with records cannot be deleted — surface the domain `EditResult.Err` reason.
  - Config defaults: all three values must be positive numbers, else reject with a message and save nothing.
  - Import: parse → `BackupCodec.validate` → confirmation dialog with summary (members / weekly records / refills counts, warns it replaces ALL data) → replace whole document. No partial writes; invalid file → error message only.
- Import uses SAF (`ActivityResultContracts.OpenDocument`); no extension filter reliance — content validation is the gate (WeChat comment: iOS extension filters can hide files).
- UI copy is English; money formatted `$X.XX`. Fixture member names in domain tests stay Chinese.
- Ids are generated in `app/` (`prefix_epochMillis_counter`), never in `domain/`.
- Persisted JSON uses `BackupCodec.encode`/`decode` (strict Json, `encodeDefaults = true`) — the store and the backup file share one format. `decode` of our own persisted text is trusted; a corrupt store falls back to `LedgerData()` rather than crashing.
- TDD for domain code. App UI code is verified by `assembleDebug` + on-phone acceptance (no Robolectric/instrumented tests in M2 — YAGNI).
- Quality gates: `gradlew test ktlintCheck detekt` green at every commit; smallest-scope `@Suppress` if detekt fights the plan's code; conventional commits.
- Branch: `feat/m2-storage-home-settings` off `main`.

## File Structure

```
domain/src/main/kotlin/com/badmintonledger/domain/report/Home.kt        Task 1
domain/src/test/kotlin/com/badmintonledger/domain/report/HomeTest.kt    Task 1
gradle/libs.versions.toml                                               Task 2 (add 3 libs)
app/build.gradle.kts                                                    Task 2 (add 3 deps)
app/src/main/AndroidManifest.xml                                        Task 2 (android:name)
app/src/main/kotlin/com/badmintonledger/app/LedgerApplication.kt        Task 2
app/src/main/kotlin/com/badmintonledger/app/storage/LedgerStore.kt      Task 2
app/src/main/kotlin/com/badmintonledger/app/LedgerViewModel.kt          Task 3
app/src/main/kotlin/com/badmintonledger/app/ui/Theme.kt                 Task 4
app/src/main/kotlin/com/badmintonledger/app/ui/AppNav.kt                Task 4
app/src/main/kotlin/com/badmintonledger/app/MainActivity.kt             Task 4 (rewrite)
app/src/main/kotlin/com/badmintonledger/app/ui/HomeScreen.kt            Task 5
app/src/main/kotlin/com/badmintonledger/app/ui/SettingsScreen.kt        Task 6 (CRUD+defaults), Task 7 (import)
```

---

### Task 1: domain — home summary builder (TDD)

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/report/Home.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/report/HomeTest.kt`

**Interfaces:**
- Consumes: `memberBalancesCents`, `poolRemainingCents`, `hasContributed` (calc), `centsToDollars`, `LedgerData`.
- Produces (used by Task 5):
  - `data class HomeRow(val id: String, val name: String, val isGuest: Boolean, val owes: Boolean, val absDollars: String)`
  - `data class HomeSummary(val rows: List<HomeRow>, val poolDollars: String, val poolWarn: Boolean, val empty: Boolean)`
  - `fun buildHomeSummary(data: LedgerData): HomeSummary`

- [ ] **Step 1: Write the failing test**

`HomeTest.kt`:
```kotlin
package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Config
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Payment
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeTest {
    // A funds 600 and plays (574.40 left); C funds 800, never plays (shown at 800);
    // D never funds, played once and paid the exact share in cash (balance 0 -> hidden);
    // G is a guest who played without funding (owes 25.60 -> shown).
    private fun fixture() = LedgerData(
        members = listOf(
            Member("A", "阿安", false),
            Member("C", "陈叔", false),
            Member("D", "大东", false),
            Member("G", "客串", true),
        ),
        refills = listOf(
            Refill("r1", "2026-07-01", Cents(140000), Cents(175000), listOf(
                Contribution("A", Cents(60000)),
                Contribution("C", Cents(80000)),
            )),
        ),
        payments = listOf(Payment("p1", "D", Cents(2560), "2026-07-05")),
        sessions = listOf(
            Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "D", "G")),
        ),
    )

    @Test
    fun `rows hide zero-balance members who never funded, keep funded and owing ones`() {
        val s = buildHomeSummary(fixture())
        assertEquals(listOf("A", "C", "G"), s.rows.map { it.id })
        assertEquals(HomeRow("A", "阿安", false, owes = false, absDollars = "574.40"), s.rows[0])
        assertEquals(HomeRow("C", "陈叔", false, owes = false, absDollars = "800.00"), s.rows[1])
        assertEquals(HomeRow("G", "客串", true, owes = true, absDollars = "25.60"), s.rows[2])
        assertEquals(false, s.empty)
    }

    @Test
    fun `pool remaining and warning threshold - strictly below 4h at default rate`() {
        val s = buildHomeSummary(fixture())
        // pool = 1750.00 - face 96.00 = 1654.00; threshold 4h x $24 = 96.00 -> no warning
        assertEquals("1654.00", s.poolDollars)
        assertEquals(false, s.poolWarn)

        // drain the pool to exactly the threshold: still no warning (strict <)
        val atThreshold = fixture().copy(
            refills = listOf(
                Refill("r1", "2026-07-01", Cents(140000), Cents(9600 + 9600), listOf(
                    Contribution("A", Cents(60000)),
                    Contribution("C", Cents(80000)),
                )),
            ),
        )
        assertEquals(false, buildHomeSummary(atThreshold).poolWarn)

        // one cent below the threshold: warn
        val belowThreshold = fixture().copy(
            refills = listOf(
                Refill("r1", "2026-07-01", Cents(140000), Cents(9600 + 9599), listOf(
                    Contribution("A", Cents(60000)),
                    Contribution("C", Cents(80000)),
                )),
            ),
        )
        assertEquals(true, buildHomeSummary(belowThreshold).poolWarn)
    }

    @Test
    fun `empty ledger - empty flag and default pool`() {
        val s = buildHomeSummary(LedgerData())
        assertEquals(true, s.empty)
        assertEquals(emptyList(), s.rows)
        assertEquals("0.00", s.poolDollars)
        assertEquals(true, s.poolWarn) // 0 < 4h x $24
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.HomeTest"`
Expected: FAIL — compilation error (`buildHomeSummary` unresolved).

- [ ] **Step 3: Implement**

`Home.kt`:
```kotlin
package com.badmintonledger.domain.report

import com.badmintonledger.domain.calc.hasContributed
import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.calc.poolRemainingCents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.centsToDollars
import kotlin.math.abs

data class HomeRow(
    val id: String,
    val name: String,
    val isGuest: Boolean,
    val owes: Boolean,
    val absDollars: String,
)

data class HomeSummary(
    val rows: List<HomeRow>,
    val poolDollars: String,
    val poolWarn: Boolean,
    val empty: Boolean,
)

// Port of pages/home/home.js onShow: zero-balance members who never funded a refill
// are hidden; the pool warns strictly below one typical session (4h x default rate).
fun buildHomeSummary(data: LedgerData): HomeSummary {
    val bal = memberBalancesCents(data)
    val rows = data.members
        .filter { (bal[it.id] ?: 0L) != 0L || hasContributed(data, it.id) }
        .map { m ->
            val c = bal[m.id] ?: 0L
            HomeRow(m.id, m.name, m.isGuest, owes = c < 0, absDollars = centsToDollars(abs(c)))
        }
    val pool = poolRemainingCents(data)
    return HomeSummary(
        rows = rows,
        poolDollars = centsToDollars(pool),
        poolWarn = pool < data.config.defaultRate.value * 4,
        empty = data.members.isEmpty(),
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.HomeTest"`
Expected: PASS (3 tests). Then `gradlew.bat :domain:test ktlintCheck detekt` — green.

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): home summary builder with visibility and pool-warning parity"
```

---

### Task 2: app — DataStore persistence (LedgerStore, LedgerApplication)

**Files:**
- Modify: `gradle/libs.versions.toml` (3 versions + 3 libraries)
- Modify: `app/build.gradle.kts` (3 deps)
- Modify: `app/src/main/AndroidManifest.xml` (`android:name`)
- Create: `app/src/main/kotlin/com/badmintonledger/app/LedgerApplication.kt`
- Create: `app/src/main/kotlin/com/badmintonledger/app/storage/LedgerStore.kt`

**Interfaces:**
- Produces (used by Task 3): `class LedgerStore(context: Context)` with `val data: Flow<LedgerData>` (corrupt/absent → `LedgerData()`) and `suspend fun save(data: LedgerData)`; `class LedgerApplication : Application` exposing `val store: LedgerStore`.

- [ ] **Step 1: Add versions and libraries**

`gradle/libs.versions.toml` — add to `[versions]`:
```toml
datastore = "1.1.6"
navigationCompose = "2.8.9"
lifecycleViewmodelCompose = "2.8.7"
```
Add to `[libraries]`:
```toml
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
```

`app/build.gradle.kts` — add to `dependencies`:
```kotlin
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
```

- [ ] **Step 2: Application + store**

`LedgerApplication.kt`:
```kotlin
package com.badmintonledger.app

import android.app.Application
import com.badmintonledger.app.storage.LedgerStore

class LedgerApplication : Application() {
    val store: LedgerStore by lazy { LedgerStore(this) }
}
```

`LedgerStore.kt`:
```kotlin
package com.badmintonledger.app.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.badmintonledger.domain.backup.BackupCodec
import com.badmintonledger.domain.model.LedgerData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ledgerDataStore by preferencesDataStore(name = "badminton_ledger")
private val DATA_KEY = stringPreferencesKey("badminton_data_v1")

/**
 * Single-document persistence: the whole LedgerData is stored as one JSON string in
 * the same format as backup files (BackupCodec). A missing or unreadable document
 * falls back to the default empty ledger instead of crashing.
 */
class LedgerStore(private val context: Context) {
    val data: Flow<LedgerData> = context.ledgerDataStore.data.map { prefs ->
        prefs[DATA_KEY]?.let { text ->
            runCatching { BackupCodec.decode(text) }.getOrNull()
        } ?: LedgerData()
    }

    suspend fun save(data: LedgerData) {
        context.ledgerDataStore.edit { prefs ->
            prefs[DATA_KEY] = BackupCodec.encode(data)
        }
    }
}
```

`AndroidManifest.xml` — on the `<application>` element add:
```xml
        android:name=".LedgerApplication"
```

- [ ] **Step 3: Build + gates**

Run: `gradlew.bat assembleDebug` → BUILD SUCCESSFUL; `gradlew.bat test ktlintCheck detekt` → green.

- [ ] **Step 4: Commit**

```powershell
git add -A -- gradle app
git commit -m "feat(app): DataStore-backed LedgerStore holding the single LedgerData document"
```

---

### Task 3: app — LedgerViewModel

**Files:**
- Create: `app/src/main/kotlin/com/badmintonledger/app/LedgerViewModel.kt`

**Interfaces:**
- Consumes: `LedgerStore` (Task 2), domain `edit` functions, `BackupCodec`, `ImportResult`.
- Produces (used by Tasks 5–7):
  - `val ledger: StateFlow<LedgerData?>` (null until first load)
  - `fun addMember(name: String)` — trims; ignores blank
  - `fun renameMember(id: String, name: String)` — trims; ignores blank
  - `fun setGuest(id: String, isGuest: Boolean)`
  - `fun removeMember(id: String): String?` — null on success, refusal reason otherwise
  - `fun saveConfig(rateDollars: Double?, paidDollars: Double?, creditDollars: Double?): String?` — null on success, error message otherwise
  - `fun validateBackup(text: String): ImportResult`
  - `fun applyImport(text: String)` — call only after validate returned Ok

- [ ] **Step 1: Implement**

`LedgerViewModel.kt`:
```kotlin
package com.badmintonledger.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.badmintonledger.domain.backup.BackupCodec
import com.badmintonledger.domain.backup.ImportResult
import com.badmintonledger.domain.edit.EditResult
import com.badmintonledger.domain.edit.addMember as domainAddMember
import com.badmintonledger.domain.edit.removeMember as domainRemoveMember
import com.badmintonledger.domain.edit.renameMember as domainRenameMember
import com.badmintonledger.domain.edit.setGuest as domainSetGuest
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Config
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.dollarsToCents
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as LedgerApplication).store

    val ledger: StateFlow<LedgerData?> =
        store.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var idCounter = 0

    private fun newId(prefix: String): String {
        idCounter += 1
        return "${prefix}_${System.currentTimeMillis()}_$idCounter"
    }

    private fun persist(data: LedgerData) {
        viewModelScope.launch { store.save(data) }
    }

    fun addMember(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = ledger.value ?: return
        persist(domainAddMember(current, newId("m"), trimmed, isGuest = false).data)
    }

    fun renameMember(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = ledger.value ?: return
        persist(domainRenameMember(current, id, trimmed))
    }

    fun setGuest(id: String, isGuest: Boolean) {
        val current = ledger.value ?: return
        persist(domainSetGuest(current, id, isGuest))
    }

    /** Returns null on success, or the refusal reason (member has records). */
    fun removeMember(id: String): String? {
        val current = ledger.value ?: return null
        return when (val r = domainRemoveMember(current, id)) {
            is EditResult.Ok -> {
                persist(r.data)
                null
            }
            is EditResult.Err -> r.reason
        }
    }

    /** Returns null on success, or an error message. All three must be positive. */
    fun saveConfig(rateDollars: Double?, paidDollars: Double?, creditDollars: Double?): String? {
        if (rateDollars == null || !rateDollars.isFinite() || rateDollars <= 0 ||
            paidDollars == null || !paidDollars.isFinite() || paidDollars <= 0 ||
            creditDollars == null || !creditDollars.isFinite() || creditDollars <= 0
        ) {
            return "Enter valid positive numbers"
        }
        val current = ledger.value ?: return null
        persist(
            current.copy(
                config = Config(
                    defaultRate = Cents(dollarsToCents(rateDollars)),
                    defaultPaid = Cents(dollarsToCents(paidDollars)),
                    defaultCredit = Cents(dollarsToCents(creditDollars)),
                ),
            ),
        )
        return null
    }

    fun validateBackup(text: String): ImportResult = BackupCodec.validate(text)

    /** Replaces the whole document. Call only after validateBackup returned Ok. */
    fun applyImport(text: String) {
        viewModelScope.launch { store.save(BackupCodec.decode(text)) }
    }
}
```

- [ ] **Step 2: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 3: Commit**

```powershell
git add app/src
git commit -m "feat(app): shared LedgerViewModel exposing the document and ledger actions"
```

---

### Task 4: app — theme, navigation, MainActivity rewrite

**Files:**
- Create: `app/src/main/kotlin/com/badmintonledger/app/ui/Theme.kt`
- Create: `app/src/main/kotlin/com/badmintonledger/app/ui/AppNav.kt`
- Rewrite: `app/src/main/kotlin/com/badmintonledger/app/MainActivity.kt`

**Interfaces:**
- Consumes: `LedgerViewModel`.
- Produces: routes `"home"` and `"settings"`; `LedgerTheme { }` wrapper; Tasks 5–6 fill in `HomeScreen`/`SettingsScreen` — this task ships with minimal placeholder composables that Tasks 5/6 replace.

- [ ] **Step 1: Implement**

`Theme.kt`:
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
```

`AppNav.kt`:
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.badmintonledger.app.LedgerViewModel

@Composable
fun AppNav(vm: LedgerViewModel = viewModel()) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(vm = vm, onOpenSettings = { nav.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
```

Placeholder screens so this task compiles standalone (Tasks 5/6 replace the bodies; keep the signatures EXACTLY as below):

`HomeScreen.kt` (placeholder):
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.badmintonledger.app.LedgerViewModel

@Composable
fun HomeScreen(vm: LedgerViewModel, onOpenSettings: () -> Unit) {
    Text("Home — coming in Task 5")
}
```

`SettingsScreen.kt` (placeholder):
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.badmintonledger.app.LedgerViewModel

@Composable
fun SettingsScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    Text("Settings — coming in Task 6")
}
```

`MainActivity.kt` (full rewrite):
```kotlin
package com.badmintonledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.badmintonledger.app.ui.AppNav
import com.badmintonledger.app.ui.LedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LedgerTheme {
                AppNav()
            }
        }
    }
}
```

- [ ] **Step 2: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 3: Commit**

```powershell
git add app/src
git commit -m "feat(app): Material3 theme and home/settings navigation scaffold"
```

---

### Task 5: app — Home screen

**Files:**
- Rewrite: `app/src/main/kotlin/com/badmintonledger/app/ui/HomeScreen.kt`

**Interfaces:**
- Consumes: `vm.ledger`, `buildHomeSummary` (Task 1). Signature stays `HomeScreen(vm: LedgerViewModel, onOpenSettings: () -> Unit)`.
- UI copy (exact): title "Badminton Ledger"; pool line "Venue pool  $X.XX"; warning "Low balance — consider a refill"; guest suffix " (guest)"; owing rows show "owes $X.XX", others "$X.XX"; empty state "No members yet — add members in Settings".

- [ ] **Step 1: Implement**

`HomeScreen.kt` (full content):
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.domain.report.buildHomeSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: LedgerViewModel, onOpenSettings: () -> Unit) {
    val data by vm.ledger.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Badminton Ledger") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        val current = data
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val summary = remember(current) { buildHomeSummary(current) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Venue pool", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "$${summary.poolDollars}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (summary.poolWarn) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        if (summary.poolWarn) {
                            Text(
                                "Low balance — consider a refill",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            if (summary.empty) {
                item {
                    Text(
                        "No members yet — add members in Settings",
                        modifier = Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(summary.rows, key = { it.id }) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(row.name + if (row.isGuest) " (guest)" else "")
                    Text(
                        if (row.owes) "owes $${row.absDollars}" else "$${row.absDollars}",
                        color = if (row.owes) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 3: Commit**

```powershell
git add app/src
git commit -m "feat(app): Home screen with balances, pool and low-balance warning"
```

---

### Task 6: app — Settings screen (members CRUD + defaults)

**Files:**
- Rewrite: `app/src/main/kotlin/com/badmintonledger/app/ui/SettingsScreen.kt`

**Interfaces:**
- Consumes: `vm.ledger`, `vm.addMember/renameMember/setGuest/removeMember/saveConfig`, `centsToDollars`. Signature stays `SettingsScreen(vm: LedgerViewModel, onBack: () -> Unit)`.
- Behavior parity with `settings.js`: add member (blank rejected silently in VM; button disabled when blank); rename via dialog (blank keeps old name); guest toggle immediate; delete asks for confirmation, then surfaces the domain refusal reason in a snackbar if the member has records; defaults save validates all three positive else snackbar "Enter valid positive numbers", success snackbar "Saved".
- Defaults are displayed in dollars without forced decimals (e.g. `24`, not `24.00`): use `centsToDollars(v).removeSuffix(".00")`.
- Import UI arrives in Task 7 — leave a clearly-marked `// Import (Task 7)` slot at the bottom of the column.

- [ ] **Step 1: Implement**

`SettingsScreen.kt` (full content):
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.centsToDollars
import kotlinx.coroutines.launch

private fun dollarsText(cents: Long): String = centsToDollars(cents).removeSuffix(".00")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val data by vm.ledger.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Member?>(null) }
    var deleteTarget by remember { mutableStateOf<Member?>(null) }

    var rate by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }
    var credit by remember { mutableStateOf("") }
    LaunchedEffect(data?.config) {
        data?.config?.let {
            rate = dollarsText(it.defaultRate.value)
            paid = dollarsText(it.defaultPaid.value)
            credit = dollarsText(it.defaultCredit.value)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = data ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Members",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(current.members, key = { it.id }) { member ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        member.name,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { renameTarget = member }) { Text("Rename") }
                    Text("Guest", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = member.isGuest,
                        onCheckedChange = { vm.setGuest(member.id, it) },
                    )
                    IconButton(onClick = { deleteTarget = member }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${member.name}")
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New member name") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            vm.addMember(newName)
                            newName = ""
                        },
                    ) { Text("Add") }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Text("Defaults", style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Hourly rate ($)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = paid,
                    onValueChange = { paid = it },
                    label = { Text("Typical refill paid ($)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = credit,
                    onValueChange = { credit = it },
                    label = { Text("Typical refill credit ($)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = {
                        val err = vm.saveConfig(
                            rate.toDoubleOrNull(),
                            paid.toDoubleOrNull(),
                            credit.toDoubleOrNull(),
                        )
                        scope.launch { snackbar.showSnackbar(err ?: "Saved") }
                    },
                ) { Text("Save defaults") }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            // Import (Task 7)
        }
    }

    renameTarget?.let { member ->
        var name by remember(member.id) { mutableStateOf(member.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename member") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it })
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameMember(member.id, name)
                        renameTarget = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete member") },
            text = { Text("Delete ${member.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val reason = vm.removeMember(member.id)
                        deleteTarget = null
                        if (reason != null) {
                            scope.launch { snackbar.showSnackbar(reason) }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}
```

- [ ] **Step 2: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green. (If detekt flags `LongMethod`/`CyclomaticComplexMethod` on `SettingsScreen`, add smallest-scope `@Suppress` — Compose screens legitimately run long.)

- [ ] **Step 3: Commit**

```powershell
git add app/src
git commit -m "feat(app): Settings screen with member CRUD and ledger defaults"
```

---

### Task 7: app — backup import via file picker

**Files:**
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/SettingsScreen.kt` (replace the `// Import (Task 7)` slot; add imports)

**Interfaces:**
- Consumes: `vm.validateBackup(text): ImportResult`, `vm.applyImport(text)`, `ImportResult.Ok/Err`.
- Behavior parity with `settings.js` importData: SAF picker with no meaningful type restriction (`*/*`); unreadable file → snackbar "Could not read the file"; invalid backup → snackbar with the exact `ImportResult.Err.reason`; valid backup → confirmation dialog "This backup contains N members, M weekly records and K refills. Importing will replace ALL current data. Continue?" with Import/Cancel; on confirm `applyImport` + snackbar "Import successful".

- [ ] **Step 1: Implement**

Add these imports to `SettingsScreen.kt`:
```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.badmintonledger.domain.backup.ImportResult
```

Add this state and launcher inside `SettingsScreen` (next to the other `remember` blocks):
```kotlin
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<Pair<String, ImportResult.Summary>?>(null) }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            scope.launch { snackbar.showSnackbar("Could not read the file") }
            return@rememberLauncherForActivityResult
        }
        when (val result = vm.validateBackup(text)) {
            is ImportResult.Ok -> pendingImport = text to result.summary
            is ImportResult.Err -> scope.launch { snackbar.showSnackbar(result.reason) }
        }
    }
```

Replace the `// Import (Task 7)` comment with:
```kotlin
            item { Text("Data", style = MaterialTheme.typography.titleMedium) }
            item {
                Button(onClick = { importPicker.launch(arrayOf("*/*")) }) {
                    Text("Import backup")
                }
            }
```

Add the confirmation dialog at the bottom of `SettingsScreen` (next to the other dialogs):
```kotlin
    pendingImport?.let { (text, summary) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import backup") },
            text = {
                Text(
                    "This backup contains ${summary.members} members, " +
                        "${summary.sessions} weekly records and ${summary.refills} refills. " +
                        "Importing will replace ALL current data. Continue?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.applyImport(text)
                        pendingImport = null
                        scope.launch { snackbar.showSnackbar("Import successful") }
                    },
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            },
        )
    }
```

- [ ] **Step 2: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 3: Commit**

```powershell
git add app/src
git commit -m "feat(app): backup import via SAF picker with validation and confirm dialog"
```

---

### Task 8: acceptance — install on phone and verify

**Files:** none (verification task; README milestone note optional)

- [ ] **Step 1: Full gates**

Run: `gradlew.bat test ktlintCheck detekt assembleDebug`
Expected: BUILD SUCCESSFUL; domain suite 43 tests (42 passing + 1 skipped RealBackupTest).

- [ ] **Step 2: Install on the connected phone**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: `Success`. Launch: `adb shell am start -n com.badmintonledger/com.badmintonledger.app.MainActivity` — app opens on Home.

- [ ] **Step 3: On-phone manual checklist (report what was verified; the human confirms the rest)**

1. Home shows empty state + venue pool $0.00 with low-balance warning.
2. Settings: add two members; rename one; toggle guest; both appear.
3. Home: both members hidden (zero balance, never funded) — expected per parity rule.
4. Settings: change defaults to 24/2000/2500, Save → "Saved"; invalid input (e.g. "abc") → "Enter valid positive numbers".
5. Deleting a member with no records works; after (in a later milestone) records exist, deletion is refused.
6. Import: pick a backup JSON (any WeChat export or a file made from the test fixture) → summary dialog → Import → Home shows the imported balances and pool.
7. Kill and relaunch the app → data persists (DataStore).

- [ ] **Step 4: Commit ledger/README touch-ups if any, then done**

## Milestone 2 Acceptance Checklist

- [ ] `gradlew test ktlintCheck detekt` green (new HomeTest included)
- [ ] `assembleDebug` builds; APK installs and launches on the phone
- [ ] Members/defaults edited on the phone persist across app restarts
- [ ] A WeChat backup imports through the file picker and Home shows the real balances
- [ ] No `android.*` import under `domain/`
