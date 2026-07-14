# Milestone 3: Recording Flows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app records a weekly session (with edit-in-place for the current week), a refill, and payments (full-debt settlement per member) with WeChat-parity validation, reachable from Home action buttons — plus the deferred M1/M2 debt items (import off the main thread, decode-once import, day/night theme, numeric keyboards, `launchSingleTop`, `compareAndSet` init, `.gitattributes`, Home component extraction).

**Architecture:** `domain/` gains three small pure additions (TDD): recording view builders (`buildSessionPreview`, `refillFactorText`, `buildPaymentSummary`), an all-or-nothing `settleDebtors` mutation, and `ImportResult.Ok` carrying the decoded `LedgerData`. `app/` gains four `LedgerViewModel` recording actions (dollars→cents at the VM edge, ids minted in app), a suspend `loadBackup` on `Dispatchers.IO`, three navigation routes, shared UI components (`DateField`, `PoolCard`, `MemberBalanceRow`), and the three screens. All domain mutations (`addSession`, `updateSession`, `addRefill`, `addPayment`) already exist and are tested — screens delegate through the VM.

**Tech Stack:** No new dependencies. Compose BOM 2025.05.00 already provides Material 3 `DatePicker`/`DatePickerDialog`, `FilterChip`, and `FlowRow` (foundation).

## Global Constraints

- `domain/` stays pure Kotlin: no `android.*` imports. All new Android code lives in `app/`.
- All money integer cents (`Long`/`Cents`) inside domain; dollars only in UI text fields (parsed with `toDoubleOrNull`, converted via `dollarsToCents` in the VM) and the JSON contract.
- One `LedgerData` document is the single source of truth; screens recompute derived values from it. Ids minted in `app/` only (`newId(prefix)`), never in `domain/`.
- Behavior parity with the WeChat pages (`E:\Code\ai\wechat\badminton\pages\session\session.js`, `pages\refill\refill.js`, `pages\payment\payment.js`, `utils\data.js`):
  - **Session:** opening the screen when this week (Monday-based, `findSessionInWeek`) already has a record silently enters edit mode with a notice; defaults for a new record: today's date, hours `4` (hardcoded, not config), rate = `config.defaultRate`, factor = `currentFactor(data)` to 4 decimals; live preview only when hours/rate/factor positive and ≥1 player; per-person preview = `realCents / players` (floor); guests selectable and addable inline (auto-selected after add).
  - **Refill:** defaults today / `defaultPaid` / `defaultCredit`; contribution rows for **non-guest members only**; blank or non-positive rows are dropped before save; contributions must sum cent-exactly to paid (domain enforces); live factor text = paid ÷ credit to 4 decimals or `—`.
  - **Payment:** checking a debtor settles their **full** debt (no amount input); debtor list and reference balances derived from `memberBalancesCents`; all-or-nothing (no partial writes on failure).
  - Validation failures surface the domain `EditResult.Err.reason` verbatim in a snackbar; on success navigate back (WeChat's toast + delayed navigation is replaced by immediate back — Home shows the result).
  - Deviation (documented): WeChat redirects to the report page after saving a session; the Report screen is Milestone 4, so M3 returns to Home. Wire the redirect in M4.
- UI copy is English; money formatted `$X.XX`. Fixture member names in domain tests stay Chinese.
- TDD for all domain code. App UI code is verified by `assembleDebug` + on-phone acceptance (no app-module test harness yet — unchanged M2 decision).
- Quality gates: `gradlew.bat test ktlintCheck detekt` green at every commit; smallest-scope `@Suppress` if detekt fights the plan's code (LongMethod=60 lines and LongParameterList=6 are at detekt defaults); conventional commits.
- Branch: `feat/m3-recording-flows` off `develop`.
- Debt items NOT in this plan: real-backup balance verification (blocked on `backups/real-backup.json` — not present; check again at acceptance).

## File Structure

```
.gitattributes                                                              Task 1
app/src/main/res/values/themes.xml                                          Task 1
app/src/main/res/values-night/themes.xml                                    Task 1
app/src/main/AndroidManifest.xml                                            Task 1 (theme ref)
domain/src/main/kotlin/com/badmintonledger/domain/report/Recording.kt       Task 2
domain/src/test/kotlin/com/badmintonledger/domain/report/RecordingTest.kt   Task 2
domain/src/main/kotlin/com/badmintonledger/domain/edit/Settle.kt            Task 3
domain/src/test/kotlin/com/badmintonledger/domain/edit/SettleTest.kt        Task 3
domain/src/main/kotlin/com/badmintonledger/domain/backup/BackupCodec.kt     Task 4 (Ok carries data)
domain/src/test/kotlin/com/badmintonledger/domain/backup/BackupCodecTest.kt Task 4
app/src/main/kotlin/com/badmintonledger/app/LedgerViewModel.kt              Task 5 (actions), Task 6 (import)
app/src/main/kotlin/com/badmintonledger/app/ui/SettingsScreen.kt            Task 6 (import + keyboards), Task 7 (shared dollarsText)
app/src/main/kotlin/com/badmintonledger/app/ui/Format.kt                    Task 7
app/src/main/kotlin/com/badmintonledger/app/ui/components/DateField.kt      Task 7
app/src/main/kotlin/com/badmintonledger/app/ui/components/LedgerParts.kt    Task 7
app/src/main/kotlin/com/badmintonledger/app/ui/HomeScreen.kt                Task 8 (rework)
app/src/main/kotlin/com/badmintonledger/app/ui/AppNav.kt                    Task 8 (3 routes + launchSingleTop)
app/src/main/kotlin/com/badmintonledger/app/ui/SessionScreen.kt             Task 8 (placeholder), Task 9
app/src/main/kotlin/com/badmintonledger/app/ui/RefillScreen.kt              Task 8 (placeholder), Task 10
app/src/main/kotlin/com/badmintonledger/app/ui/PaymentScreen.kt             Task 8 (placeholder), Task 11
```

---

### Task 1: chores — line endings + day/night theme (cold-start flash)

**Files:**
- Create: `.gitattributes`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values-night/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml` (theme attribute only)

**Interfaces:**
- Consumes: nothing.
- Produces: `@style/Theme.BadmintonLedger` referenced by the manifest; repo-level EOL normalization for `gradlew`.

- [ ] **Step 1: Create `.gitattributes`**

```
gradlew text eol=lf
*.bat text eol=crlf
```

- [ ] **Step 2: Day/night launch theme**

`app/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.BadmintonLedger" parent="@android:style/Theme.Material.Light.NoActionBar" />
</resources>
```

`app/src/main/res/values-night/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.BadmintonLedger" parent="@android:style/Theme.Material.NoActionBar" />
</resources>
```

`AndroidManifest.xml` — change the `<application>` theme attribute:
```xml
        android:theme="@style/Theme.BadmintonLedger">
```
(replaces `android:theme="@android:style/Theme.Material.Light.NoActionBar">`; everything else stays.)

- [ ] **Step 3: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 4: Commit**

```powershell
git add .gitattributes app/src/main
git commit -m "chore: normalize gradlew line endings and fix dark-mode cold-start flash"
```

---

### Task 2: domain — recording view builders (TDD)

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/report/Recording.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/report/RecordingTest.kt`

**Interfaces:**
- Consumes: `sessionFaceCostCents`, `sessionRealCostCents`, `memberBalancesCents` (calc), `centsToDollars`, `Cents`, `Session`, `LedgerData`.
- Produces (used by Tasks 9–11):
  - `data class SessionPreview(val faceDollars: String, val realDollars: String, val players: Int, val perPersonDollars: String)`
  - `fun buildSessionPreview(hours: Double?, rateCents: Long?, factor: Double?, playerCount: Int): SessionPreview?` — null while any input invalid
  - `fun refillFactorText(paidCents: Long?, creditCents: Long?): String` — 4-decimal ratio or `"—"`
  - `data class DebtorRow(val id: String, val name: String, val owedCents: Long, val owedDollars: String)`
  - `data class PaymentMemberRow(val id: String, val name: String, val isGuest: Boolean, val owes: Boolean, val absDollars: String)`
  - `data class PaymentSummary(val debtors: List<DebtorRow>, val rows: List<PaymentMemberRow>)`
  - `fun buildPaymentSummary(data: LedgerData): PaymentSummary`

- [ ] **Step 1: Write the failing test**

`RecordingTest.kt`:
```kotlin
package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Payment
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordingTest {
    // A funds 600 and plays (574.40 left); C funds 800, never plays; D played once and
    // paid the exact share in cash (balance 0); G is a guest who owes 25.60.
    private fun fixture() =
        LedgerData(
            members =
                listOf(
                    Member("A", "阿安", false),
                    Member("C", "陈叔", false),
                    Member("D", "大东", false),
                    Member("G", "客串", true),
                ),
            refills =
                listOf(
                    Refill(
                        "r1", "2026-07-01", Cents(140000), Cents(175000),
                        listOf(
                            Contribution("A", Cents(60000)),
                            Contribution("C", Cents(80000)),
                        ),
                    ),
                ),
            payments = listOf(Payment("p1", "D", Cents(2560), "2026-07-05")),
            sessions = listOf(Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "D", "G"))),
        )

    @Test
    fun `session preview - 4h at 24 dollars and factor 0_8 for 3 players`() {
        assertEquals(
            SessionPreview(faceDollars = "96.00", realDollars = "76.80", players = 3, perPersonDollars = "25.60"),
            buildSessionPreview(hours = 4.0, rateCents = 2400, factor = 0.8, playerCount = 3),
        )
    }

    @Test
    fun `session preview - per-person amount floors like the WeChat preview`() {
        // 1h x $1.25 x 0.8 = 100 cents; 100 / 3 floors to 33
        assertEquals(
            SessionPreview("1.25", "1.00", 3, "0.33"),
            buildSessionPreview(1.0, 125, 0.8, 3),
        )
    }

    @Test
    fun `session preview - null while any input is invalid`() {
        assertNull(buildSessionPreview(null, 2400, 0.8, 3))
        assertNull(buildSessionPreview(0.0, 2400, 0.8, 3))
        assertNull(buildSessionPreview(4.0, null, 0.8, 3))
        assertNull(buildSessionPreview(4.0, 2400, 0.0, 3))
        assertNull(buildSessionPreview(4.0, 2400, 0.8, 0))
    }

    @Test
    fun `refill factor text - 4 decimals or em dash`() {
        assertEquals("0.8000", refillFactorText(200000, 250000))
        assertEquals("0.7500", refillFactorText(180000, 240000))
        assertEquals("1.2500", refillFactorText(250000, 200000))
        assertEquals("—", refillFactorText(null, 250000))
        assertEquals("—", refillFactorText(200000, 0))
    }

    @Test
    fun `payment summary - debtors and all-member reference rows`() {
        val s = buildPaymentSummary(fixture())
        assertEquals(listOf(DebtorRow("G", "客串", 2560, "25.60")), s.debtors)
        assertEquals(
            listOf(
                PaymentMemberRow("A", "阿安", isGuest = false, owes = false, absDollars = "574.40"),
                PaymentMemberRow("C", "陈叔", isGuest = false, owes = false, absDollars = "800.00"),
                PaymentMemberRow("D", "大东", isGuest = false, owes = false, absDollars = "0.00"),
                PaymentMemberRow("G", "客串", isGuest = true, owes = true, absDollars = "25.60"),
            ),
            s.rows,
        )
    }

    @Test
    fun `payment summary - empty ledger`() {
        val s = buildPaymentSummary(LedgerData())
        assertEquals(emptyList(), s.debtors)
        assertEquals(emptyList(), s.rows)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.RecordingTest"`
Expected: FAIL — compilation error (`buildSessionPreview` unresolved).

- [ ] **Step 3: Implement**

`Recording.kt`:
```kotlin
package com.badmintonledger.domain.report

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.calc.sessionFaceCostCents
import com.badmintonledger.domain.calc.sessionRealCostCents
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Session
import com.badmintonledger.domain.model.centsToDollars
import kotlin.math.abs

data class SessionPreview(
    val faceDollars: String,
    val realDollars: String,
    val players: Int,
    val perPersonDollars: String,
)

/** Live cost preview for the session form (port of session.js recalc); null until every input is valid. */
@Suppress("ReturnCount")
fun buildSessionPreview(
    hours: Double?,
    rateCents: Long?,
    factor: Double?,
    playerCount: Int,
): SessionPreview? {
    if (hours == null || !hours.isFinite() || hours <= 0) return null
    if (rateCents == null || rateCents <= 0) return null
    if (factor == null || !factor.isFinite() || factor <= 0) return null
    if (playerCount < 1) return null
    val probe = Session("preview", "2026-01-05", hours, Cents(rateCents), factor, List(playerCount) { "p$it" })
    val realCents = sessionRealCostCents(probe)
    return SessionPreview(
        faceDollars = centsToDollars(sessionFaceCostCents(probe)),
        realDollars = centsToDollars(realCents),
        players = playerCount,
        perPersonDollars = centsToDollars(realCents / playerCount),
    )
}

private const val FACTOR_SCALE = 10_000L

/** "paid ÷ credit" to 4 decimals (port of refill.js factor line), or an em dash while inputs are invalid. */
fun refillFactorText(
    paidCents: Long?,
    creditCents: Long?,
): String {
    if (paidCents == null || paidCents <= 0 || creditCents == null || creditCents <= 0) return "—"
    val scaled = (paidCents * FACTOR_SCALE + creditCents / 2) / creditCents
    return "${scaled / FACTOR_SCALE}.${(scaled % FACTOR_SCALE).toString().padStart(4, '0')}"
}

data class DebtorRow(val id: String, val name: String, val owedCents: Long, val owedDollars: String)

data class PaymentMemberRow(
    val id: String,
    val name: String,
    val isGuest: Boolean,
    val owes: Boolean,
    val absDollars: String,
)

data class PaymentSummary(val debtors: List<DebtorRow>, val rows: List<PaymentMemberRow>)

/** Port of payment.js onShow: debtor chips plus the all-members reference balance list. */
fun buildPaymentSummary(data: LedgerData): PaymentSummary {
    val bal = memberBalancesCents(data)
    val rows =
        data.members.map { m ->
            val c = bal[m.id] ?: 0L
            PaymentMemberRow(m.id, m.name, m.isGuest, owes = c < 0, absDollars = centsToDollars(abs(c)))
        }
    val debtors =
        data.members.mapNotNull { m ->
            val c = bal[m.id] ?: 0L
            if (c < 0) DebtorRow(m.id, m.name, -c, centsToDollars(-c)) else null
        }
    return PaymentSummary(debtors, rows)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.report.RecordingTest"`
Expected: PASS (6 tests). Then `gradlew.bat :domain:test ktlintCheck detekt` — green.

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): recording view builders - session preview, refill factor, payment summary"
```

---

### Task 3: domain — settleDebtors (TDD)

**Files:**
- Create: `domain/src/main/kotlin/com/badmintonledger/domain/edit/Settle.kt`
- Test: `domain/src/test/kotlin/com/badmintonledger/domain/edit/SettleTest.kt`

**Interfaces:**
- Consumes: `memberBalancesCents` (calc), `addPayment`, `EditResult`.
- Produces (used by Task 5): `fun settleDebtors(data: LedgerData, memberIds: List<String>, paymentIds: List<String>, date: String): EditResult<List<Payment>>` — one full-debt payment per member, all-or-nothing.

- [ ] **Step 1: Write the failing test**

`SettleTest.kt`:
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Payment
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SettleTest {
    // A funds the pot and plays; guests G and H play without funding and owe a 25.60 share each.
    private fun fixture() =
        LedgerData(
            members =
                listOf(
                    Member("A", "阿安", false),
                    Member("G", "客串", true),
                    Member("H", "候补", true),
                ),
            refills =
                listOf(
                    Refill("r1", "2026-07-01", Cents(60000), Cents(75000), listOf(Contribution("A", Cents(60000)))),
                ),
            sessions = listOf(Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "G", "H"))),
        )

    @Test
    fun `settling one debtor records their full debt and zeroes the balance`() {
        val r = settleDebtors(fixture(), listOf("G"), listOf("p_1"), "2026-07-05")
        assertIs<EditResult.Ok<List<Payment>>>(r)
        assertEquals(listOf(Payment("p_1", "G", Cents(2560), "2026-07-05")), r.value)
        val bal = memberBalancesCents(r.data)
        assertEquals(0L, bal["G"])
        assertEquals(-2560L, bal["H"]) // untouched
    }

    @Test
    fun `settling several debtors records one payment each`() {
        val r = settleDebtors(fixture(), listOf("G", "H"), listOf("p_1", "p_2"), "2026-07-05")
        assertIs<EditResult.Ok<List<Payment>>>(r)
        assertEquals(
            listOf(
                Payment("p_1", "G", Cents(2560), "2026-07-05"),
                Payment("p_2", "H", Cents(2560), "2026-07-05"),
            ),
            r.value,
        )
        val bal = memberBalancesCents(r.data)
        assertEquals(0L, bal["G"])
        assertEquals(0L, bal["H"])
        assertEquals(2, r.data.payments.size)
    }

    @Test
    fun `empty selection is refused`() {
        assertEquals(
            EditResult.Err("Please select a member"),
            settleDebtors(fixture(), emptyList(), emptyList(), "2026-07-05"),
        )
    }

    @Test
    fun `member with nothing owing is refused`() {
        assertEquals(
            EditResult.Err("Nothing owing for the selected member"),
            settleDebtors(fixture(), listOf("A"), listOf("p_1"), "2026-07-05"),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.edit.SettleTest"`
Expected: FAIL — compilation error (`settleDebtors` unresolved).

- [ ] **Step 3: Implement**

`Settle.kt`:
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Payment

/**
 * Records one full-settlement payment per selected debtor (WeChat payment page:
 * checking a member pays their entire debt). All-or-nothing: the returned document
 * only exists when every payment was valid.
 */
@Suppress("ReturnCount")
fun settleDebtors(
    data: LedgerData,
    memberIds: List<String>,
    paymentIds: List<String>,
    date: String,
): EditResult<List<Payment>> {
    require(paymentIds.size == memberIds.size) { "one payment id per member" }
    if (memberIds.isEmpty()) return EditResult.Err("Please select a member")
    var doc = data
    val created = mutableListOf<Payment>()
    for ((i, memberId) in memberIds.withIndex()) {
        val owedCents = -(memberBalancesCents(doc)[memberId] ?: 0L)
        if (owedCents <= 0) return EditResult.Err("Nothing owing for the selected member")
        when (val r = addPayment(doc, paymentIds[i], memberId, owedCents, date)) {
            is EditResult.Ok -> {
                doc = r.data
                created += r.value
            }
            is EditResult.Err -> return r
        }
    }
    return EditResult.Ok(doc, created)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.edit.SettleTest"`
Expected: PASS (4 tests). Then `gradlew.bat :domain:test ktlintCheck detekt` — green.

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): settleDebtors records full-debt payments all-or-nothing"
```

---

### Task 4: domain — decode-once import (`ImportResult.Ok` carries the document)

**Files:**
- Modify: `domain/src/main/kotlin/com/badmintonledger/domain/backup/BackupCodec.kt`
- Modify: `domain/src/test/kotlin/com/badmintonledger/domain/backup/BackupCodecTest.kt`

**Interfaces:**
- Produces (used by Task 6): `ImportResult.Ok(val data: LedgerData, val summary: Summary)` — validate and decode can no longer diverge.
- Note: the app still compiles after this task — `SettingsScreen` and `LedgerViewModel` only pattern-match `ImportResult.Ok` and read `.summary`.

- [ ] **Step 1: Update the failing tests first**

In `BackupCodecTest.kt`, replace the two `Ok` tests (keep every other test unchanged):

```kotlin
    @Test
    fun `complete backup passes with a summary and the decoded document`() {
        val text = fixture()
        val r = BackupCodec.validate(text)
        assertIs<ImportResult.Ok>(r)
        assertEquals(ImportResult.Summary(members = 2, sessions = 1, refills = 1), r.summary)
        assertEquals(BackupCodec.decode(text), r.data)
        assertEquals("阿安", r.data.members[0].name)
        assertEquals(60000L, r.data.refills[0].contributions[0].amount.value)
    }

    @Test
    fun `default empty data passes too`() {
        val r = BackupCodec.validate(BackupCodec.encode(LedgerData()))
        assertIs<ImportResult.Ok>(r)
        assertEquals(ImportResult.Summary(0, 0, 0), r.summary)
        assertEquals(LedgerData(), r.data)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.backup.BackupCodecTest"`
Expected: FAIL — compilation error (`Ok` has no `data` property).

- [ ] **Step 3: Implement**

In `BackupCodec.kt`, change the `Ok` declaration:
```kotlin
    data class Ok(val data: LedgerData, val summary: Summary) : ImportResult
```

and the success return at the end of `validate(root: JsonElement)`:
```kotlin
        return ImportResult.Ok(
            json.decodeFromJsonElement(LedgerData.serializer(), root),
            ImportResult.Summary(members.size, sessions.size, refills.size),
        )
```
(`decodeFromJsonElement` is a member of `Json` — no new imports.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :domain:test --tests "com.badmintonledger.domain.backup.BackupCodecTest"`
Expected: PASS. Then `gradlew.bat test ktlintCheck detekt assembleDebug` — green (app must still compile).

- [ ] **Step 5: Commit**

```powershell
git add domain/src
git commit -m "feat(domain): ImportResult.Ok carries the decoded document so validate and decode cannot diverge"
```

---

### Task 5: app — LedgerViewModel recording actions + init hardening

**Files:**
- Modify: `app/src/main/kotlin/com/badmintonledger/app/LedgerViewModel.kt`

**Interfaces:**
- Consumes: `addSession`/`updateSession`/`SessionUpdate`/`addRefill`/`settleDebtors`/`addMember` (domain edit), `Contribution`, `dollarsToCents`.
- Produces (used by Tasks 9–11):
  - `fun saveSession(editId: String?, date: String, hours: Double?, rateDollars: Double?, factor: Double?, playerIds: List<String>): String?` — null on success
  - `fun addRefill(date: String, paidDollars: Double?, creditDollars: Double?, contributionsDollars: List<Pair<String, Double>>): String?`
  - `fun settleDebtors(memberIds: List<String>, date: String): String?`
  - `fun addGuest(name: String): Member?` — created guest (for auto-select), null when blank

- [ ] **Step 1: Implement**

In `LedgerViewModel.kt` — change the `init` block to:
```kotlin
    init {
        // compareAndSet: a mutation that lands before the first DataStore emission must not
        // be clobbered by the (stale) loaded document.
        viewModelScope.launch { _ledger.compareAndSet(null, store.data.first()) }
    }
```

Add these import aliases next to the existing `domainXxx` ones, plus the new model imports:
```kotlin
import com.badmintonledger.domain.edit.SessionUpdate
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.edit.addRefill as domainAddRefill
import com.badmintonledger.domain.edit.addSession as domainAddSession
import com.badmintonledger.domain.edit.settleDebtors as domainSettleDebtors
import com.badmintonledger.domain.edit.updateSession as domainUpdateSession
```

Add these actions after `saveConfig`:
```kotlin
    /** Creates this week's record or edits [editId]. Returns null on success, or the refusal reason. */
    @Suppress("LongParameterList")
    fun saveSession(
        editId: String?,
        date: String,
        hours: Double?,
        rateDollars: Double?,
        factor: Double?,
        playerIds: List<String>,
    ): String? {
        val current = ledger.value ?: return "Data is still loading"
        val rateCents = rateDollars?.let(::dollarsToCents)
        val result =
            if (editId == null) {
                domainAddSession(current, newId("s"), date, hours, rateCents, factor, playerIds)
            } else {
                domainUpdateSession(current, editId, SessionUpdate(date, hours, rateCents, factor, playerIds))
            }
        return when (result) {
            is EditResult.Ok -> {
                persist(result.data)
                null
            }
            is EditResult.Err -> result.reason
        }
    }

    /** Returns null on success, or the refusal reason. Amounts arrive in dollars from the form. */
    fun addRefill(
        date: String,
        paidDollars: Double?,
        creditDollars: Double?,
        contributionsDollars: List<Pair<String, Double>>,
    ): String? {
        val current = ledger.value ?: return "Data is still loading"
        val contributions =
            contributionsDollars.map { (memberId, dollars) -> Contribution(memberId, Cents(dollarsToCents(dollars))) }
        val result =
            domainAddRefill(
                current,
                newId("r"),
                date,
                paidDollars?.let(::dollarsToCents),
                creditDollars?.let(::dollarsToCents),
                contributions,
            )
        return when (result) {
            is EditResult.Ok -> {
                persist(result.data)
                null
            }
            is EditResult.Err -> result.reason
        }
    }

    /** Records one full-debt payment per selected member. Returns null on success. */
    fun settleDebtors(
        memberIds: List<String>,
        date: String,
    ): String? {
        val current = ledger.value ?: return "Data is still loading"
        return when (val r = domainSettleDebtors(current, memberIds, memberIds.map { newId("p") }, date)) {
            is EditResult.Ok -> {
                persist(r.data)
                null
            }
            is EditResult.Err -> r.reason
        }
    }

    /** Adds a guest member and returns it (so the caller can auto-select), or null when the name is blank. */
    fun addGuest(name: String): Member? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val current = ledger.value ?: return null
        val r = domainAddMember(current, newId("m"), trimmed, isGuest = true)
        persist(r.data)
        return r.value
    }
```

- [ ] **Step 2: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 3: Commit**

```powershell
git add app/src
git commit -m "feat(app): ViewModel recording actions and load-race hardening"
```

---

### Task 6: app — import off the main thread, decode-once wiring, numeric keyboards

**Files:**
- Modify: `app/src/main/kotlin/com/badmintonledger/app/LedgerViewModel.kt` (replace `validateBackup`/`applyImport`)
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/SettingsScreen.kt` (import block + keyboards)

**Interfaces:**
- Consumes: `ImportResult.Ok(data, summary)` (Task 4).
- Produces:
  - `sealed interface BackupLoad` with `CouldNotRead`, `Invalid(reason)`, `Ready(data, summary)` (top level in `LedgerViewModel.kt`)
  - `suspend fun LedgerViewModel.loadBackup(uri: Uri): BackupLoad` — reads + validates on `Dispatchers.IO`, decodes exactly once
  - `fun applyImport(data: LedgerData)` — replaces `applyImport(text)`; `validateBackup` is deleted

- [ ] **Step 1: Rework the ViewModel import API**

In `LedgerViewModel.kt`, delete `validateBackup` and the old `applyImport(text: String)`, and add:

```kotlin
sealed interface BackupLoad {
    data object CouldNotRead : BackupLoad

    data class Invalid(val reason: String) : BackupLoad

    data class Ready(val data: LedgerData, val summary: ImportResult.Summary) : BackupLoad
}
```
(top level, after the class) and inside the class:
```kotlin
    /** Reads and validates a backup off the main thread; the document is decoded exactly once. */
    suspend fun loadBackup(uri: Uri): BackupLoad =
        withContext(Dispatchers.IO) {
            val text =
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                }.getOrNull() ?: return@withContext BackupLoad.CouldNotRead
            when (val r = BackupCodec.validate(text)) {
                is ImportResult.Ok -> BackupLoad.Ready(r.data, r.summary)
                is ImportResult.Err -> BackupLoad.Invalid(r.reason)
            }
        }

    /** Replaces the whole document with an already-validated backup. */
    fun applyImport(data: LedgerData) {
        persist(data)
    }
```
New imports: `android.net.Uri`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`.

- [ ] **Step 2: Rewire SettingsScreen**

Replace the `pendingImport` state + `importPicker` launcher block with:
```kotlin
    var pendingImport by remember { mutableStateOf<BackupLoad.Ready?>(null) }
    val importPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                when (val load = vm.loadBackup(uri)) {
                    BackupLoad.CouldNotRead -> snackbar.showSnackbar("Could not read the file")
                    is BackupLoad.Invalid -> snackbar.showSnackbar(load.reason)
                    is BackupLoad.Ready -> pendingImport = load
                }
            }
        }
```

Replace the import confirmation dialog with:
```kotlin
    pendingImport?.let { load ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import backup") },
            text = {
                Text(
                    "This backup contains ${load.summary.members} members, " +
                        "${load.summary.sessions} weekly records and ${load.summary.refills} refills. " +
                        "Importing will replace ALL current data. Continue?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.applyImport(load.data)
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

Import changes in `SettingsScreen.kt`: add `com.badmintonledger.app.BackupLoad`, `androidx.compose.foundation.text.KeyboardOptions`, `androidx.compose.ui.text.input.KeyboardType`; remove `androidx.compose.ui.platform.LocalContext`, `com.badmintonledger.domain.backup.ImportResult`, and the now-unused `val context = LocalContext.current`.

Add to each of the three defaults `OutlinedTextField`s (rate, paid, credit):
```kotlin
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
```

- [ ] **Step 3: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 4: Commit**

```powershell
git add app/src
git commit -m "fix(app): backup import reads and validates on IO with a single decode; numeric keyboards"
```

---

### Task 7: app — shared format helpers, DateField, PoolCard/MemberBalanceRow

**Files:**
- Create: `app/src/main/kotlin/com/badmintonledger/app/ui/Format.kt`
- Create: `app/src/main/kotlin/com/badmintonledger/app/ui/components/DateField.kt`
- Create: `app/src/main/kotlin/com/badmintonledger/app/ui/components/LedgerParts.kt`
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/SettingsScreen.kt` (drop the private `dollarsText`)

**Interfaces:**
- Produces (used by Tasks 8–11):
  - `fun dollarsText(cents: Long): String` — `"24"` not `"24.00"` (package `com.badmintonledger.app.ui`)
  - `fun numberText(v: Double): String` — `"4"` not `"4.0"`, `"1.5"` stays
  - `fun factorText(v: Double): String` — 4 decimals, `"0.8000"`
  - `@Composable fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier)` — read-only field + M3 DatePickerDialog, value is `YYYY-MM-DD`
  - `@Composable fun PoolCard(poolDollars: String, warn: Boolean, modifier: Modifier = Modifier)`
  - `@Composable fun MemberBalanceRow(name: String, isGuest: Boolean, owes: Boolean, absDollars: String, modifier: Modifier = Modifier)`

- [ ] **Step 1: Implement**

`Format.kt`:
```kotlin
package com.badmintonledger.app.ui

import com.badmintonledger.domain.model.centsToDollars
import java.util.Locale

/** Dollars without forced decimals: 2400 cents -> "24", 2450 -> "24.50". */
fun dollarsText(cents: Long): String = centsToDollars(cents).removeSuffix(".00")

/** Plain number text without a trailing ".0": 4.0 -> "4", 1.5 -> "1.5". */
fun numberText(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/** Factor with 4 decimals, matching the WeChat form prefill: 0.8 -> "0.8000". */
fun factorText(v: Double): String = String.format(Locale.ROOT, "%.4f", v)
```

`components/DateField.kt`:
```kotlin
package com.badmintonledger.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Read-only date field with a Material3 date picker; [value] and [onChange] use YYYY-MM-DD. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.DateRange, contentDescription = "Pick $label")
            }
        },
        modifier = modifier,
    )
    if (showPicker) {
        val initialMillis =
            runCatching {
                LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                        }
                        showPicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state) }
    }
}
```

`components/LedgerParts.kt`:
```kotlin
package com.badmintonledger.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PoolCard(
    poolDollars: String,
    warn: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Venue pool", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$$poolDollars",
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (warn) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }
            if (warn) {
                Text(
                    "Low balance — consider a refill",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun MemberBalanceRow(
    name: String,
    isGuest: Boolean,
    owes: Boolean,
    absDollars: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name + if (isGuest) " (guest)" else "")
        Text(
            if (owes) "owes $$absDollars" else "$$absDollars",
            color =
                if (owes) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}
```

In `SettingsScreen.kt`, delete the file-private helper (the shared one in `Format.kt` is in the same package, so call sites are unchanged):
```kotlin
private fun dollarsText(cents: Long): String = centsToDollars(cents).removeSuffix(".00")
```
and remove the now-unused `import com.badmintonledger.domain.model.centsToDollars`.

- [ ] **Step 2: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 3: Commit**

```powershell
git add app/src
git commit -m "feat(app): shared DateField, PoolCard, MemberBalanceRow and format helpers"
```

---

### Task 8: app — navigation routes + Home action buttons

**Files:**
- Modify: `app/src/main/kotlin/com/badmintonledger/app/ui/AppNav.kt`
- Rewrite: `app/src/main/kotlin/com/badmintonledger/app/ui/HomeScreen.kt`
- Create: `app/src/main/kotlin/com/badmintonledger/app/ui/SessionScreen.kt` (placeholder)
- Create: `app/src/main/kotlin/com/badmintonledger/app/ui/RefillScreen.kt` (placeholder)
- Create: `app/src/main/kotlin/com/badmintonledger/app/ui/PaymentScreen.kt` (placeholder)

**Interfaces:**
- Consumes: `PoolCard`, `MemberBalanceRow` (Task 7), `buildHomeSummary`.
- Produces: routes `"session"`, `"refill"`, `"payment"`; every `navigate` uses `launchSingleTop = true`. Placeholder screen signatures (Tasks 9–11 replace bodies, signatures stay EXACTLY): `SessionScreen(vm: LedgerViewModel, onBack: () -> Unit)`, `RefillScreen(vm: LedgerViewModel, onBack: () -> Unit)`, `PaymentScreen(vm: LedgerViewModel, onBack: () -> Unit)`.
- Home UI copy (exact): buttons "Record week", "Refill", "Payment" in a row under the pool card; everything else unchanged from M2.

- [ ] **Step 1: Implement**

`AppNav.kt` (full content):
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
            HomeScreen(
                vm = vm,
                onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
                onRecordSession = { nav.navigate("session") { launchSingleTop = true } },
                onOpenRefill = { nav.navigate("refill") { launchSingleTop = true } },
                onOpenPayment = { nav.navigate("payment") { launchSingleTop = true } },
            )
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("session") {
            SessionScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("refill") {
            RefillScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("payment") {
            PaymentScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
```

`HomeScreen.kt` (full rewrite):
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import com.badmintonledger.app.ui.components.MemberBalanceRow
import com.badmintonledger.app.ui.components.PoolCard
import com.badmintonledger.domain.report.buildHomeSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: LedgerViewModel,
    onOpenSettings: () -> Unit,
    onRecordSession: () -> Unit,
    onOpenRefill: () -> Unit,
    onOpenPayment: () -> Unit,
) {
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
                PoolCard(
                    poolDollars = summary.poolDollars,
                    warn = summary.poolWarn,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onRecordSession, modifier = Modifier.weight(1f)) { Text("Record week") }
                    Button(onClick = onOpenRefill, modifier = Modifier.weight(1f)) { Text("Refill") }
                    Button(onClick = onOpenPayment, modifier = Modifier.weight(1f)) { Text("Payment") }
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
                MemberBalanceRow(
                    name = row.name,
                    isGuest = row.isGuest,
                    owes = row.owes,
                    absDollars = row.absDollars,
                )
            }
        }
    }
}
```
(The `@Suppress("LongMethod")` from M2 is intentionally dropped — the extraction should bring it under detekt's 60-line default. If detekt still flags it, re-add the smallest-scope suppress.)

Placeholder screens so this task compiles standalone (Tasks 9–11 replace the bodies; keep the signatures EXACTLY as below):

`SessionScreen.kt` (placeholder):
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.badmintonledger.app.LedgerViewModel

@Composable
fun SessionScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
) {
    Text("Session — coming in Task 9")
}
```

`RefillScreen.kt` (placeholder):
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.badmintonledger.app.LedgerViewModel

@Composable
fun RefillScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
) {
    Text("Refill — coming in Task 10")
}
```

`PaymentScreen.kt` (placeholder):
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.badmintonledger.app.LedgerViewModel

@Composable
fun PaymentScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
) {
    Text("Payment — coming in Task 11")
}
```

- [ ] **Step 2: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 3: Commit**

```powershell
git add app/src
git commit -m "feat(app): recording routes with singleTop navigation and Home action buttons"
```

---

### Task 9: app — Session screen (record/edit this week)

**Files:**
- Rewrite: `app/src/main/kotlin/com/badmintonledger/app/ui/SessionScreen.kt`

**Interfaces:**
- Consumes: `vm.ledger`, `vm.saveSession`, `vm.addGuest`, `findSessionInWeek`, `currentFactor`, `buildSessionPreview`, `dollarsToCents`, `DateField`, `dollarsText`/`numberText`/`factorText`. Signature stays `SessionScreen(vm: LedgerViewModel, onBack: () -> Unit)`.
- Behavior parity with `session.js`: auto-edit this week's record with notice "This week already has a record — editing it"; create defaults today / hours "4" / rate from config / factor `currentFactor` to 4 dp; edit prefill from the stored session (no forced 4-dp on factor); player chips over ALL members with " (guest)" suffix; inline add-guest auto-selects; preview only when all inputs valid; on success navigate back, on failure snackbar with the domain reason.
- UI copy (exact): title "Record This Week"; fields "Date", "Hours", "Rate ($/hour)", "Factor (paid ÷ credit)"; section "Players"; "Guest name" + "Add guest"; preview lines "Court fee $X → actual $Y", "N players · about $Z each", "The last player absorbs the rounding remainder, so the total is exact."; button "Save this week" / "Save changes".

- [ ] **Step 1: Implement**

`SessionScreen.kt` (full content):
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.app.ui.components.DateField
import com.badmintonledger.domain.calc.currentFactor
import com.badmintonledger.domain.edit.findSessionInWeek
import com.badmintonledger.domain.model.dollarsToCents
import com.badmintonledger.domain.report.buildSessionPreview
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
fun SessionScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
) {
    val data by vm.ledger.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var initialized by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var hours by remember { mutableStateOf("4") }
    var rate by remember { mutableStateOf("") }
    var factor by remember { mutableStateOf("") }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var guestName by remember { mutableStateOf("") }

    // Port of session.js onLoad: an existing record for the current week is edited in place.
    LaunchedEffect(data) {
        val current = data ?: return@LaunchedEffect
        if (initialized) return@LaunchedEffect
        initialized = true
        val existing = findSessionInWeek(current, LocalDate.now().toString())
        if (existing == null) {
            rate = dollarsText(current.config.defaultRate.value)
            factor = factorText(currentFactor(current))
        } else {
            editId = existing.id
            date = existing.date
            hours = numberText(existing.hours)
            rate = dollarsText(existing.rate.value)
            factor = numberText(existing.factor)
            existing.playerIds.forEach { selected[it] = true }
            snackbar.showSnackbar("This week already has a record — editing it")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record This Week") },
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DateField(
                label = "Date",
                value = date,
                onChange = { date = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = hours,
                onValueChange = { hours = it },
                label = { Text("Hours") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rate,
                onValueChange = { rate = it },
                label = { Text("Rate ($/hour)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = factor,
                onValueChange = { factor = it },
                label = { Text("Factor (paid ÷ credit)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Players", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                current.members.forEach { m ->
                    FilterChip(
                        selected = selected[m.id] == true,
                        onClick = { selected[m.id] = selected[m.id] != true },
                        label = { Text(m.name + if (m.isGuest) " (guest)" else "") },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = guestName,
                    onValueChange = { guestName = it },
                    label = { Text("Guest name") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = guestName.isNotBlank(),
                    onClick = {
                        vm.addGuest(guestName)?.let { selected[it.id] = true }
                        guestName = ""
                    },
                ) { Text("Add guest") }
            }
            val preview =
                buildSessionPreview(
                    hours.toDoubleOrNull(),
                    rate.toDoubleOrNull()?.let(::dollarsToCents),
                    factor.toDoubleOrNull(),
                    current.members.count { selected[it.id] == true },
                )
            if (preview != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Court fee $${preview.faceDollars} → actual $${preview.realDollars}")
                        Text("${preview.players} players · about $${preview.perPersonDollars} each")
                        Text(
                            "The last player absorbs the rounding remainder, so the total is exact.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Button(
                onClick = {
                    val playerIds = current.members.filter { selected[it.id] == true }.map { it.id }
                    val err =
                        vm.saveSession(
                            editId,
                            date,
                            hours.toDoubleOrNull(),
                            rate.toDoubleOrNull(),
                            factor.toDoubleOrNull(),
                            playerIds,
                        )
                    if (err == null) onBack() else scope.launch { snackbar.showSnackbar(err) }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) { Text(if (editId == null) "Save this week" else "Save changes") }
        }
    }
}
```

- [ ] **Step 2: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 3: Commit**

```powershell
git add app/src
git commit -m "feat(app): Session screen with week edit-in-place, guest add and live cost preview"
```

---

### Task 10: app — Refill screen

**Files:**
- Rewrite: `app/src/main/kotlin/com/badmintonledger/app/ui/RefillScreen.kt`

**Interfaces:**
- Consumes: `vm.ledger`, `vm.addRefill`, `refillFactorText`, `dollarsToCents`, `centsToDollars`, `DateField`, `dollarsText`. Signature stays `RefillScreen(vm: LedgerViewModel, onBack: () -> Unit)`.
- Behavior parity with `refill.js`: defaults today / `defaultPaid` / `defaultCredit`; contribution rows for non-guest members only; blank or non-positive amounts dropped before save; live factor line and running contributions total; on success navigate back, on failure snackbar with the domain reason (including "Contributions must add up to the paid amount").
- UI copy (exact): title "Refill"; fields "Date", "Paid ($)", "Credit ($)"; factor line "Factor (paid ÷ credit): X"; section "Contributions (must total the paid amount)"; per-row field label "Amount ($)"; total line "Total  $X.XX"; button "Save refill".

- [ ] **Step 1: Implement**

`RefillScreen.kt` (full content):
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.app.ui.components.DateField
import com.badmintonledger.domain.model.centsToDollars
import com.badmintonledger.domain.model.dollarsToCents
import com.badmintonledger.domain.report.refillFactorText
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun RefillScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
) {
    val data by vm.ledger.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var initialized by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var paid by remember { mutableStateOf("") }
    var credit by remember { mutableStateOf("") }
    val amounts = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(data) {
        val current = data ?: return@LaunchedEffect
        if (initialized) return@LaunchedEffect
        initialized = true
        paid = dollarsText(current.config.defaultPaid.value)
        credit = dollarsText(current.config.defaultCredit.value)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Refill") },
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
        val funders = current.members.filter { !it.isGuest }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                DateField(
                    label = "Date",
                    value = date,
                    onChange = { date = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = paid,
                    onValueChange = { paid = it },
                    label = { Text("Paid ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = credit,
                    onValueChange = { credit = it },
                    label = { Text("Credit ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "Factor (paid ÷ credit): " +
                        refillFactorText(
                            paid.toDoubleOrNull()?.let(::dollarsToCents),
                            credit.toDoubleOrNull()?.let(::dollarsToCents),
                        ),
                )
            }
            item {
                Text(
                    "Contributions (must total the paid amount)",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(funders, key = { it.id }) { member ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(member.name, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = amounts[member.id] ?: "",
                        onValueChange = { amounts[member.id] = it },
                        label = { Text("Amount ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                val totalCents =
                    funders.sumOf { m ->
                        amounts[m.id]?.toDoubleOrNull()?.takeIf { it > 0 }?.let(::dollarsToCents) ?: 0L
                    }
                Text("Total  $${centsToDollars(totalCents)}", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Button(
                    onClick = {
                        val contributions =
                            funders.mapNotNull { m ->
                                amounts[m.id]?.toDoubleOrNull()?.takeIf { it > 0 }?.let { m.id to it }
                            }
                        val err = vm.addRefill(date, paid.toDoubleOrNull(), credit.toDoubleOrNull(), contributions)
                        if (err == null) onBack() else scope.launch { snackbar.showSnackbar(err) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) { Text("Save refill") }
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
git commit -m "feat(app): Refill screen with per-member contributions and live factor"
```

---

### Task 11: app — Payment screen

**Files:**
- Rewrite: `app/src/main/kotlin/com/badmintonledger/app/ui/PaymentScreen.kt`

**Interfaces:**
- Consumes: `vm.ledger`, `vm.settleDebtors`, `buildPaymentSummary`, `DateField`, `MemberBalanceRow`. Signature stays `PaymentScreen(vm: LedgerViewModel, onBack: () -> Unit)`.
- Behavior parity with `payment.js`: no amount input — checking a debtor settles their full debt; debtor chips + all-member reference balances; save disabled until at least one debtor is checked; on success navigate back.
- UI copy (exact): title "Receive Payment"; "Date"; prompt "Who paid? Checking a name settles their full debt."; chip "Name · owes $X"; empty state "No one owes right now 🎉"; card "Current balances (reference)"; button "Record payments".

- [ ] **Step 1: Implement**

`PaymentScreen.kt` (full content):
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.app.ui.components.DateField
import com.badmintonledger.app.ui.components.MemberBalanceRow
import com.badmintonledger.domain.report.buildPaymentSummary
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
fun PaymentScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
) {
    val data by vm.ledger.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive Payment") },
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
        val summary = remember(current) { buildPaymentSummary(current) }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DateField(
                label = "Date",
                value = date,
                onChange = { date = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                "Who paid? Checking a name settles their full debt.",
                style = MaterialTheme.typography.titleMedium,
            )
            if (summary.debtors.isEmpty()) {
                Text("No one owes right now 🎉")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.debtors.forEach { d ->
                        FilterChip(
                            selected = selected[d.id] == true,
                            onClick = { selected[d.id] = selected[d.id] != true },
                            label = { Text("${d.name} · owes $${d.owedDollars}") },
                        )
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Current balances (reference)", style = MaterialTheme.typography.titleMedium)
                    summary.rows.forEach { row ->
                        MemberBalanceRow(
                            name = row.name,
                            isGuest = row.isGuest,
                            owes = row.owes,
                            absDollars = row.absDollars,
                        )
                    }
                }
            }
            Button(
                enabled = summary.debtors.any { selected[it.id] == true },
                onClick = {
                    val picked = summary.debtors.filter { selected[it.id] == true }.map { it.id }
                    val err = vm.settleDebtors(picked, date)
                    if (err == null) onBack() else scope.launch { snackbar.showSnackbar(err) }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) { Text("Record payments") }
        }
    }
}
```

- [ ] **Step 2: Build + gates**

Run: `gradlew.bat assembleDebug test ktlintCheck detekt` → green.

- [ ] **Step 3: Commit**

```powershell
git add app/src
git commit -m "feat(app): Payment screen settling full debts per checked member"
```

---

### Task 12: acceptance — full gates, install, week-cycle verification

**Files:** none (verification task)

- [ ] **Step 1: Full gates**

Run: `gradlew.bat test ktlintCheck detekt assembleDebug`
Expected: BUILD SUCCESSFUL; domain suite grows by ~12 tests (RecordingTest 6, SettleTest 4, BackupCodecTest reworked), still 1 skipped RealBackupTest.

- [ ] **Step 2: Install on the connected phone (if available)**

Run: `adb devices` — if a device is listed:
`adb install -r app/build/outputs/apk/debug/app-debug.apk` → `Success`, then
`adb shell am start -n com.badmintonledger/com.badmintonledger.app.MainActivity`.
If no device is connected, report that install/manual checks are pending and continue.

- [ ] **Step 3: On-phone manual checklist (report what was verified; the human confirms the rest)**

Full week cycle, numbers must match the WeChat app for the same inputs:
1. Settings: members A/B/C exist (add if needed).
2. Refill: paid 2000, credit 2500, contributions A 600 / B 600 / C 800 → saves; Home pool $2500.00, balances 600/600/800. A mismatched total (e.g. A 600 / B 600 / C 700) → snackbar "Contributions must add up to the paid amount".
3. Session: defaults show hours 4, rate 24, factor 0.8000; select A, B + add guest "G" (auto-selected); preview "Court fee $96.00 → actual $76.80", "3 players · about $25.60 each"; save → Home: A 574.40, B 574.40, C 800.00, G owes 25.60.
4. Re-open "Record week" → notice "This week already has a record — editing it", form prefilled; change hours to 2, save → balances recompute (share $12.80).
5. Payment: G listed as debtor with the owed amount; check G, save → G disappears from Home (balance 0) or shows $0.00 per visibility rules.
6. Second session in the same week is impossible by construction (edit-in-place); changing the date to an occupied other week → snackbar "Another record already exists in the target week".
7. Dark mode: cold start shows no white flash (values-night theme).
8. Double-tap Settings icon fast → only one Settings entry on the back stack (back returns straight to Home).
9. Kill and relaunch → all records persist.

- [ ] **Step 4: Real-backup parity check (only if the file exists)**

If `backups/real-backup.json` exists, import it on the phone and compare Home balances against the WeChat app; otherwise note it remains deferred.

## Milestone 3 Acceptance Checklist

- [ ] `gradlew test ktlintCheck detekt` green (RecordingTest, SettleTest, reworked BackupCodecTest included)
- [ ] `assembleDebug` builds; APK installs and launches
- [ ] A full week cycle (refill → session → edit session → payment) on the phone matches WeChat numbers
- [ ] This week's session edits in place; refills/payments are create-only (delete arrives with History in M5)
- [ ] Import still works end-to-end (now off the main thread, single decode)
- [ ] No `android.*` import under `domain/`
