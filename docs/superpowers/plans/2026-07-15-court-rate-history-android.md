# Court Rate History (Backup v2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port today's WeChat v1.1.0 feature to Android: dated append-only court-rate history, backup schema v2 with v1 migration on import/load, date-based rate prefill, the settings split, and the reset-all button — restoring the WeChat↔Android round trip.

**Architecture:** The model drops `Config.defaultRate` and gains top-level `rates: List<RateChange>` (schema v2). Migration lives in the JSON layer (`BackupCodec`) BEFORE typed decode — the typed model would otherwise silently default a v1 document's rate to $24. `calc.currentRate` ports the JS lookup; `buildHomeSummary` gains a `today` parameter; the app layer re-wires the session prefill/date-repick, splits Settings into 球馆单价 + 默认充值参数 cards, and adds 清空全部数据.

**Tech Stack:** No new dependencies.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-15-court-rate-history-android.md` (supersedes the v1 contract). Ground truth: today's WeChat commits — `utils/calc.js currentRate`, `utils/data.js` (DEFAULT_DATA v2, addRateChange, migrate, validateImport v1|v2), `utils/store.js` migrate-on-load, `pages/session/session.js` onDate repick, `pages/home/home.js` threshold, `pages/settings/settings.*` split + resetAllData, and the new `tests/calc.test.js` / `tests/data.test.js` blocks — port test cases case-for-case.
- Chinese copy verbatim from the WeChat source: 单价需为正数, 日期格式不正确, 单价历史数据不完整, 备份文件版本不兼容 (now for version ∉ {1,2}), 球馆单价, 生效日期, 单价（$/小时）, 记录价格变更, 已记录, `{date} 起` / `$X/小时`, 默认充值参数, 清空全部数据, 将清空全部成员、记录与设置，且无法恢复，建议先导出备份。确定清空？, 已清空.
- `rates` append-only: no edit/delete anywhere. Non-empty by construction (default seed / migration seed); `currentRate` may assume it.
- Money integer cents in domain; `rate` on the wire is dollars (existing `Cents` serializer). Domain purity; gates green at EVERY commit (each task leaves both `:domain` and `:app` compiling).
- Existing behavior guards: sessions keep snapshotting their own rate (no changes to Session/save paths beyond the prefill source); `currentFactor` unchanged.
- Branch `feat/court-rate-history` off `main`. Conventional commits.

## File Structure

```
domain/model/LedgerData.kt         T1 — RateChange, Config minus defaultRate, LedgerData v2 + rates default
domain/calc/Calc.kt                T1 — currentRate
domain/report/Home.kt              T1 — buildHomeSummary(data, today)
domain/backup/BackupCodec.kt       T1 — validate v1|v2, migrateToV2, decode migrates
domain tests: LedgerDataJsonTest, LedgerCalcTest (currentRate cases), HomeTest, BackupCodecTest, BackupRoundTripTest   T1
app: LedgerViewModel (saveConfig 2-param), SettingsScreen (defaults card minus rate), SessionScreen (prefill + onDate repick), HomeScreen (today param)   T1 (mechanical compile-fixes + behavior port)
domain/edit/RateChanges.kt + RateChangeTest   T2 — addRateChange
app: LedgerViewModel.addRateChange, SettingsScreen 球馆单价 card   T2
app: SettingsScreen 清空全部数据 + VM resetAllData; app/build.gradle.kts version bump   T3
```

---

### Task 1: schema v2 core — model, currentRate, migration, home threshold, mechanical app rewire

One atomic commit (the `Config` change breaks every consumer; everything below lands together so gates stay green).

**Interfaces produced:**
- `data class RateChange(val id: String, val date: String, val rate: Cents)` (@Serializable)
- `Config(defaultPaid: Cents, defaultCredit: Cents)`; `LedgerData(version: Int = 2, …, rates: List<RateChange> = listOf(RateChange("rate_seed", "2000-01-01", Cents(2400))), …)` — `rates` positioned after `config`
- `fun currentRate(data: LedgerData, dateStr: String): Cents`
- `fun buildHomeSummary(data: LedgerData, today: String): HomeSummary` (threshold `currentRate(data, today).value * 4`)
- `BackupCodec.validate` accepts version 1|2 (v1: `config.defaultRate` positive; v2: `rates` non-empty, entries `{id: non-empty string, date: YYYY-MM-DD, rate > 0}` else 单价历史数据不完整; other versions → 备份文件版本不兼容); `BackupCodec.decode` migrates v1 JsonElements before typed decode; `encode`/`encodePretty` emit v2 automatically.

- [ ] **Step 1: New/updated domain tests first** (all in one red batch)

`LedgerCalcTest.kt` — add (port of today's calc.test.js block):
```kotlin
    @Test
    fun `current rate by date - exact, between, before and after all entries`() {
        val data = LedgerData(
            rates = listOf(
                RateChange("rt1", "2026-01-01", Cents(2400)),
                RateChange("rt2", "2026-06-01", Cents(2600)),
            ),
        )
        assertEquals(Cents(2400), currentRate(data, "2026-01-01"))
        assertEquals(Cents(2400), currentRate(data, "2026-03-15"))
        assertEquals(Cents(2600), currentRate(data, "2026-06-01"))
        assertEquals(Cents(2600), currentRate(data, "2026-12-31"))
        assertEquals(Cents(2400), currentRate(data, "2025-01-01"))
    }
```

`LedgerDataJsonTest.kt` — replace the defaults test and add the migration guard:
```kotlin
    @Test
    fun `default LedgerData matches WeChat DEFAULT_DATA v2`() {
        val d = LedgerData()
        assertEquals(2, d.version)
        assertEquals(Config(Cents(200000), Cents(250000)), d.config)
        assertEquals(listOf(RateChange("rate_seed", "2000-01-01", Cents(2400))), d.rates)
        assertEquals(emptyList(), d.members)
    }

    @Test
    fun `v1 document decodes through migration keeping ITS rate, not the default`() {
        val v1 = """{"version":1,"members":[],"config":{"defaultRate":30,"defaultPaid":2000,
            "defaultCredit":2500},"refills":[],"payments":[],"sessions":[]}"""
        val d = BackupCodec.decode(v1)
        assertEquals(2, d.version)
        assertEquals(Config(Cents(200000), Cents(250000)), d.config)
        assertEquals(listOf(RateChange("rate_seed", "2000-01-01", Cents(3000))), d.rates)
    }
```
(The existing `backup JSON decodes…` / round-trip tests: change their fixture to v2 — `"version": 2`, config without defaultRate, plus `"rates": [{"id":"rt1","date":"2026-01-01","rate":24}]` — and route decoding through `BackupCodec.decode`; keep all other assertions.)

`BackupCodecTest.kt` — update + add (port of today's data.test.js block):
- Existing `rejects non-objects and wrong versions`: the `fixture(version = "2")` expectation flips — version 2 alone no longer fails; change that case to `fixture(version = "3")` → `备份文件版本不兼容`. NOTE: `fixture()` is a v1 shape, so `fixture(version = "2")` without rates must now fail with `单价历史数据不完整` — assert exactly that as a new case.
- Existing v1 fixture test stays passing unchanged (v1 accepted).
- Add:
```kotlin
    private fun fixtureV2(
        ratesJson: String = """[{"id":"rt1","date":"2026-01-01","rate":24}]""",
    ): String =
        """{"version":2,"members":[
            {"id":"A","name":"阿安","isGuest":false},
            {"id":"G","name":"客串","isGuest":true}
        ],"config":{"defaultPaid":2000,"defaultCredit":2500},
        "rates":$ratesJson,
        "refills":[{"id":"r1","date":"2026-07-01","paid":600,"credit":750,
            "contributions":[{"memberId":"A","amount":600}]}],
        "payments":[{"id":"p1","memberId":"G","amount":25.6,"date":"2026-07-05"}],
        "sessions":[{"id":"s1","date":"2026-07-04","hours":4,"rate":24,
            "factor":0.8,"playerIds":["A","G"]}]}"""

    @Test
    fun `v2 backup passes, broken rate history rejected`() {
        val ok = BackupCodec.validate(fixtureV2())
        assertEquals(ImportResult.Ok::class, ok::class)
        assertEquals(
            ImportResult.Summary(members = 2, sessions = 1, refills = 1),
            (ok as ImportResult.Ok).summary,
        )
        assertEquals(
            ImportResult.Err("单价历史数据不完整"),
            BackupCodec.validate(fixtureV2(ratesJson = "[]")),
        )
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV2(ratesJson = """[{"id":"rt1","date":"2026-01-01","rate":-1}]""")))
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV2(ratesJson = """[{"id":"rt1","date":"07/01/2026","rate":24}]""")))
    }

    @Test
    fun `v1 import migrates and re-exports as v2`() {
        val r = BackupCodec.validate(fixture()) // v1 fixture
        assertIs<ImportResult.Ok>(r)
        assertEquals(2, r.data.version)
        assertEquals(listOf(RateChange("rate_seed", "2000-01-01", Cents(2400))), r.data.rates)
        val out = BackupCodec.encode(r.data)
        assertTrue(out.contains("\"version\":2") || out.contains("\"version\": 2"))
        assertIs<ImportResult.Ok>(BackupCodec.validate(out))
    }
```
(imports as needed: `RateChange`, `Cents`, `assertIs`, `assertTrue`.)

`HomeTest.kt` — mechanical: every `buildHomeSummary(x)` becomes `buildHomeSummary(x, "2026-07-15")`; fixture `Refill(..)`/`Config(..)` constructions drop the rate argument (`Config(Cents(140000)...)` fixtures don't construct Config — only the threshold cases matter: the default `rates` seed keeps the 9600-cent threshold, so all expected values stand).

`BackupRoundTripTest.kt` — fixture JSON stays v1 (good: exercises migration end-to-end); assert after decode: `data.version == 2` and `data.rates.single().rate == Cents(2400)`; the re-encode round-trip equality assertion stays.

- [ ] **Step 2: red** — `gradlew.bat :domain:test` fails to compile (RateChange etc. unresolved).

- [ ] **Step 3: Implement domain**

`LedgerData.kt`:
```kotlin
@Serializable
data class RateChange(val id: String, val date: String, val rate: Cents)

@Serializable
data class Config(val defaultPaid: Cents, val defaultCredit: Cents)
```
and in `LedgerData`: `version: Int = 2`; `config: Config = Config(Cents(200000), Cents(250000))`; insert after config:
```kotlin
    val rates: List<RateChange> = listOf(RateChange("rate_seed", "2000-01-01", Cents(2400))),
```

`Calc.kt` — append (port of calc.js currentRate; rates non-empty by construction):
```kotlin
// 按日期取历史单价：找 date <= dateStr 中日期最晚的一条；早于最早记录时取最早一条
fun currentRate(data: LedgerData, dateStr: String): Cents {
    val eligible = data.rates.filter { it.date <= dateStr }
    val hit = eligible.maxByOrNull { it.date } ?: data.rates.minByOrNull { it.date }
    return checkNotNull(hit) { "rates is never empty" }.rate
}
```

`Home.kt` — signature `fun buildHomeSummary(data: LedgerData, today: String): HomeSummary`; threshold line becomes `poolWarn = pool < currentRate(data, today).value * 4` (import `currentRate`).

`BackupCodec.kt`:
- version check: `val version = (obj["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull; if (version != 1 && version != 2) return ImportResult.Err("备份文件版本不兼容")`
- config block: keep defaultPaid/defaultCredit checks; then:
```kotlin
        if (version == 1) {
            if (!config.positive("defaultRate")) return ImportResult.Err("配置数据不完整")
        } else {
            val rates = obj["rates"] as? JsonArray
            if (rates == null || rates.isEmpty()) return ImportResult.Err("单价历史数据不完整")
            for (rt in rates) {
                val ro = rt as? JsonObject ?: return ImportResult.Err("单价历史数据不完整")
                if (ro.stringOrNull("id").isNullOrEmpty() || !ro.dateOk("date") || !ro.positive("rate")) {
                    return ImportResult.Err("单价历史数据不完整")
                }
            }
        }
```
- migration + decode:
```kotlin
    // v1 → v2 at the JSON layer: the typed model has no defaultRate and would silently
    // default a missing rates key, so migration must happen BEFORE decode.
    private fun migrateToV2(obj: JsonObject): JsonObject {
        val version = (obj["version"] as? JsonPrimitive)?.intOrNull
        if (version == 2) return obj
        val config = obj["config"] as? JsonObject ?: return obj
        val defaultRate = config["defaultRate"] ?: return obj
        return buildJsonObject {
            obj.forEach { (k, v) ->
                when (k) {
                    "version" -> put(k, 2)
                    "config" -> put(k, buildJsonObject { config.forEach { (ck, cv) -> if (ck != "defaultRate") put(ck, cv) } })
                    else -> put(k, v)
                }
            }
            put(
                "rates",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", "rate_seed")
                            put("date", "2000-01-01")
                            put("rate", defaultRate)
                        },
                    )
                },
            )
        }
    }
```
- `decode(text)` becomes: parse → `(root as? JsonObject)?.let(::migrateToV2) ?: root` → `json.decodeFromJsonElement(...)`; the happy-path decode inside `validate(root)` uses the same migrated object.
(imports: `buildJsonObject`, `buildJsonArray`, `put`, `add`, `decodeFromJsonElement` — some already present.)

- [ ] **Step 4: Mechanical app rewire** (same commit; READ each file first)
- `LedgerViewModel.saveConfig(paidDollars: Double?, creditDollars: Double?)` — drop the rate parameter and its validation clause; persist `Config(Cents(dollarsToCents(paid)), Cents(dollarsToCents(credit)))`.
- `SettingsScreen`: defaults card title becomes 默认充值参数; delete the rate `OutlinedTextField` + its state + its slot in `LaunchedEffect(data?.config)`; `vm.saveConfig(paid.toDoubleOrNull(), credit.toDoubleOrNull())`.
- `SessionScreen` (`applyExistingSessionToForm`): the no-existing branch's rate prefill becomes `fields.rate.value = dollarsText(currentRate(current, LocalDate.now().toString()).value)`. The date `DateField`'s `onChange` becomes: set the date, and when `editId == null` (creating) also `rate = dollarsText(currentRate(current, newDate).value)` — guard on the non-null `data` value in scope.
- `HomeScreen`: `buildHomeSummary(current, remember { LocalDate.now().toString() })` (import `java.time.LocalDate`).

- [ ] **Step 5: green + gates** — `gradlew.bat assembleDebug test ktlintCheck detekt` all green.
- [ ] **Step 6: Commit** — `feat: backup schema v2 with court-rate history and v1 migration`

---

### Task 2: addRateChange (domain TDD) + 球馆单价 settings card

**Interfaces:** `fun addRateChange(data: LedgerData, id: String, date: String, rateDollars: Double?): EditResult<RateChange>` in `domain/edit/RateChanges.kt`; `LedgerViewModel.addRateChange(date: String, rateDollars: Double?): String?` (null = success; generates `newId("rate")`); Settings card between 成员管理 and 默认充值参数.

- [ ] **Step 1: domain test first** — `domain/src/test/kotlin/com/badmintonledger/domain/edit/RateChangeTest.kt` (port of today's data.test.js case):
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.RateChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RateChangeTest {
    @Test
    fun `validates then appends - never mutates history`() {
        val data = LedgerData()
        assertEquals(
            EditResult.Err("单价需为正数"),
            addRateChange(data, "rate_x", "2026-08-01", -1.0),
        )
        assertEquals(
            EditResult.Err("日期格式不正确"),
            addRateChange(data, "rate_x", "bad-date", 26.0),
        )
        assertEquals(1, data.rates.size)

        val added = addRateChange(data, "rate_x", "2026-08-01", 26.0)
        assertIs<EditResult.Ok<RateChange>>(added)
        assertEquals(2, added.data.rates.size)
        assertEquals(RateChange("rate_x", "2026-08-01", Cents(2600)), added.data.rates[1])
        assertEquals(1, data.rates.size) // original untouched
    }
}
```
- [ ] **Step 2: red**, then implement `RateChanges.kt`:
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.RateChange
import com.badmintonledger.domain.model.dollarsToCents

private val DATE_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")

/** Appends a dated rate change. rates is append-only — no edit or delete, ever. */
fun addRateChange(data: LedgerData, id: String, date: String, rateDollars: Double?): EditResult<RateChange> {
    if (rateDollars == null || !rateDollars.isFinite() || rateDollars <= 0) return EditResult.Err("单价需为正数")
    if (!DATE_RE.matches(date)) return EditResult.Err("日期格式不正确")
    val rt = RateChange(id, date, Cents(dollarsToCents(rateDollars)))
    return EditResult.Ok(data.copy(rates = data.rates + rt), rt)
}
```
- [ ] **Step 3: VM + card.** VM (aliased import `addRateChange as domainAddRateChange`):
```kotlin
    fun addRateChange(date: String, rateDollars: Double?): String? {
        val current = ledger.value ?: return "数据加载中，请稍后再试"
        return when (val r = domainAddRateChange(current, newId("rate"), date, rateDollars)) {
            is EditResult.Ok -> {
                persist(r.data)
                null
            }
            is EditResult.Err -> r.reason
        }
    }
```
SettingsScreen — new card between 成员管理 and 默认充值参数 (follow the file's existing item/section idioms; state `rateDate` default `LocalDate.now().toString()`, `rateValue` text):
- header item 球馆单价
- history rows: `current.rates.sortedByDescending { it.date }` → Row(SpaceBetween) { Text("${r.date} 起"); Text("$${dollarsText(r.rate.value)}/小时") } (no edit/delete controls)
- `DateField(label = "生效日期", value = rateDate, onChange = { rateDate = it })`
- `OutlinedTextField`(label 单价（$/小时）, decimal keyboard)
- `Button("记录价格变更")` → `vm.addRateChange(rateDate, rateValue.toDoubleOrNull())` → snackbar reason ?: 已记录; on success clear `rateValue`.
- [ ] **Step 4: gates green.** Commit — `feat: court rate history entry and settings card`

---

### Task 3: 清空全部数据 + version bump + acceptance

- [ ] **Step 1:** VM: `fun resetAllData() { persist(LedgerData()) }`. SettingsScreen 数据备份 card: below import/export add `OutlinedButton("清空全部数据")` → confirm `AlertDialog` (title 清空全部数据; text 将清空全部成员、记录与设置，且无法恢复，建议先导出备份。确定清空？; confirm 清空 → `vm.resetAllData()` + snackbar 已清空; dismiss 取消).
- [ ] **Step 2:** `app/build.gradle.kts`: `versionCode = 2`, `versionName = "0.2"`.
- [ ] **Step 3: gates + acceptance** — `gradlew.bat test ktlintCheck detekt assembleDebug assembleRelease` green (expect 67 + ~4 new = ~71 domain tests, 1 skipped); `adb install -r app/build/outputs/apk/release/app-release.apk` if a device is attached (release-over-release updates in place now).
- [ ] **Step 4:** Commit — `feat: reset-all-data and version 0.2` — then on-phone: import a fresh WeChat v2 export (should now succeed) and export Android→WeChat (both directions green = the spec's round-trip acceptance).

## Acceptance Checklist
- [ ] Gates green (~71 domain tests, 1 skipped)
- [ ] v1 AND v2 backups import; v1 documents (store + files) migrate keeping their actual rate; export emits v2 accepted by WeChat 1.1.0
- [ ] New week prefills the date-appropriate rate; backdating re-picks; editing never touches a stored rate
- [ ] Settings shows 球馆单价 history card + 默认充值参数 + 清空全部数据
- [ ] No `android.*` under domain/; rates append-only everywhere
