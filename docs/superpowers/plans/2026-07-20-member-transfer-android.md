# Member Transfer (Backup v4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the admin record a member-to-member court-fee balance transfer (two members settled up offline; the app should move balance from the sender to the receiver so the numbers stay accurate) — backup schema v4, a new `Transfer` record type, its own screen, and a History section.

**Architecture:** Additive, same shape as the v3 (membership-fee) port — no field removed, no existing function signature broken. `LedgerData` gains `transfers: List<Transfer>`; `memberBalancesCents` gains one more summed term (`+ received − sent`), so every existing balance/pool/report consumer picks up transfers automatically with no other changes needed. One new domain file (`edit/Transfers.kt`) holds the mutator; `BackupCodec` gains a fourth migration link in its chain (`migrateToV4`) and a fourth validation branch.

**Tech Stack:** No new dependencies.

**Ground truth:** `docs/superpowers/specs/2026-07-20-member-transfer-design.md` (the approved design — read it first) and `docs/domain-reference.md` (current behavior spec, to be updated in the final task). Current code shapes verified fresh against `domain/src/main/kotlin/com/badmintonledger/domain/{model/LedgerData.kt, calc/Calc.kt, backup/BackupCodec.kt, edit/Settle.kt, report/History.kt}` and `app/src/main/kotlin/com/badmintonledger/app/{LedgerViewModel.kt, ui/HomeScreen.kt, ui/HistoryScreen.kt, ui/AppNav.kt}`.

## Global Constraints

- Chinese copy verbatim, exactly as specified: 请选择转出成员, 请选择转入成员, 转出转入不能是同一人, 金额需为正数, 日期格式不正确, 转出成员余额不足, 转账数据不完整, 转账, 转账记录（点击可删除）, 转出成员, 转入成员, 转账日期, 保存转账, 最多可转 $X.
- Transfer eligibility: both `fromMemberId` and `toMemberId` must reference an existing member with `isGuest == false`. `active` (disabled) does **not** exclude a member — matches the Payment screen's existing-balance-action convention.
- The sender's balance cap check uses `memberBalancesCents(data)[fromMemberId]` computed on the document **before** the transfer is applied — a zero-or-negative balance rejects any amount; the exact balance amount is a valid transfer (boundary case, not `<` — allowed at `==`).
- A transfer never touches `poolRemainingCents` and never touches the membership-fee ledger — it is purely a `memberBalancesCents` redistribution between two members, so the sum of all member balances is invariant across any transfer.
- Money integer cents in domain; `Transfer.amount` is dollars on the wire (existing `Cents` serializer, same pattern as every other money field). Domain purity — every mutator returns a new document, never mutates. Gates green at EVERY commit (`:domain` and `:app` both compile after every task).
- Branch `feat/member-transfer` off `main`. Conventional commits.

## File Structure

```
domain/model/LedgerData.kt          T1 — Transfer model, LedgerData v4 default
domain/backup/BackupCodec.kt        T1 — validate v1|v2|v3|v4, migrateToV4, decode chains migrations
domain tests: LedgerDataJsonTest, BackupCodecTest, BackupRoundTripTest             T1

domain/calc/Calc.kt                 T2 — memberBalancesCents gains the transfer term
domain/edit/Transfers.kt (new)      T2 — addTransfer, deleteTransfer
domain tests: LedgerCalcTest, TransferTest (new)                                   T2

domain/report/History.kt            T3 — TransferHistoryRow, buildHistoryRows gains transfers
domain tests: HistoryTest                                                          T3

app: LedgerViewModel (addTransfer, deleteTransfer)                                 T4
app: TransferScreen.kt (new), HomeScreen (转账 button), AppNav (transfer route),
     HistoryScreen (转账记录 section)                                              T4
app/build.gradle.kts version bump; docs/domain-reference.md updated                T5
```

---

### Task 1: schema v4 core — model, migration, validation

One atomic commit (additive; nothing downstream breaks, but migration/validation must land together with the model so `:domain` compiles and the contract stays internally consistent).

**Interfaces produced:**
- `data class Transfer(val id: String, val fromMemberId: String, val toMemberId: String, val amount: Cents, val date: String)`
- `LedgerData(version: Int = 4, …, transfers: List<Transfer> = emptyList())` — `transfers` positioned last, after `memberships`
- `BackupCodec.validate`/`decode` accept version 1|2|3|4; v4 additionally requires a `transfers` array where every entry has non-empty `id`, `fromMemberId`/`toMemberId` each referencing an existing member id, and a positive `amount` and valid `date` — else `转账数据不完整`

- [ ] **Step 1: domain tests first (red batch)**

`LedgerDataJsonTest.kt` — bump the top-of-file `backupJson` fixture to version 4 and add a transfer entry:
```kotlin
    private val backupJson =
        """
        {
          "version": 4,
          "members": [
            { "id": "A", "name": "阿安", "isGuest": false },
            { "id": "G", "name": "客串", "isGuest": true }
          ],
          "config": { "defaultPaid": 2000, "defaultCredit": 2500, "membershipFee": 50 },
          "rates": [{ "id": "rt1", "date": "2026-01-01", "rate": 24 }],
          "refills": [{
            "id": "r1", "date": "2026-07-01", "paid": 600, "credit": 750,
            "contributions": [{ "memberId": "A", "amount": 600 }]
          }],
          "payments": [{ "id": "p1", "memberId": "G", "amount": 25.6, "date": "2026-07-05" }],
          "sessions": [{ "id": "s1", "date": "2026-07-04", "hours": 4, "rate": 24,
                         "factor": 0.8, "playerIds": ["A", "G"] }],
          "memberships": [{ "id": "mf1", "memberId": "A", "year": 2026, "date": "2026-07-01", "amount": 25 }],
          "transfers": [{ "id": "tr1", "fromMemberId": "A", "toMemberId": "G", "amount": 5, "date": "2026-07-06" }]
        }
        """.trimIndent()

    @Test
    fun `backup JSON decodes with dollar amounts becoming cents`() {
        val data = BackupCodec.decode(backupJson)
        assertEquals(4, data.version)
        assertEquals(listOf(RateChange("rt1", "2026-01-01", Cents(2400))), data.rates)
        assertEquals(Cents(60000), data.refills[0].contributions[0].amount)
        assertEquals(Cents(75000), data.refills[0].credit)
        assertEquals(Cents(2560), data.payments[0].amount)
        assertEquals(Cents(2400), data.sessions[0].rate)
        assertEquals(4.0, data.sessions[0].hours)
        assertEquals(0.8, data.sessions[0].factor)
        assertEquals(listOf("A", "G"), data.sessions[0].playerIds)
        assertEquals(Cents(2500), data.memberships[0].amount)
        assertEquals(Transfer("tr1", "A", "G", Cents(500), "2026-07-06"), data.transfers[0])
    }
```
(add `import com.badmintonledger.domain.model.Transfer` if not already present via the same-package rule — this test file is in `com.badmintonledger.domain.model` so `Transfer` needs no import)

Add to the same file:
```kotlin
    @Test
    fun `default LedgerData matches WeChat DEFAULT_DATA v4`() {
        val d = LedgerData()
        assertEquals(4, d.version)
        assertEquals(Config(Cents(200000), Cents(250000), Cents(5000)), d.config)
        assertEquals(listOf(RateChange("rate_seed", "2000-01-01", Cents(2400))), d.rates)
        assertEquals(emptyList(), d.members)
        assertEquals(emptyList(), d.memberships)
        assertEquals(emptyList(), d.transfers)
    }

    @Test
    fun `v3 document decodes through migration gaining empty transfers`() {
        val v3 =
            """{"version":3,"members":[],"config":{"defaultPaid":2000,"defaultCredit":2500,"membershipFee":50},
            "rates":[{"id":"rate_seed","date":"2000-01-01","rate":24}],
            "refills":[],"payments":[],"sessions":[],"memberships":[]}"""
        val d = BackupCodec.decode(v3)
        assertEquals(4, d.version)
        assertEquals(emptyList(), d.transfers)
    }
```
(remove/replace the now-superseded `default LedgerData matches WeChat DEFAULT_DATA v3` test with the v4 version above — same test name pattern, just the next version number, following the exact precedent set when v2→v3 replaced the v2 default-structure test.)

`BackupCodecTest.kt` — update the version-boundary test (v4 is now valid, so the "always invalid" probe moves to v5):
```kotlin
    @Test
    fun `rejects non-objects and wrong versions`() {
        assertIs<ImportResult.Err>(BackupCodec.validate("null"))
        assertIs<ImportResult.Err>(BackupCodec.validate("\"[]\""))
        assertIs<ImportResult.Err>(BackupCodec.validate("[]"))
        assertIs<ImportResult.Err>(BackupCodec.validate("not json at all"))
        assertEquals(
            ImportResult.Err("备份文件版本不兼容"),
            BackupCodec.validate(fixture(version = "5")),
        )
        // fixture() is a v1 shape (no "rates" key); version 2/3/4 alone doesn't fail, but the
        // missing rate history does.
        assertEquals(ImportResult.Err("单价历史数据不完整"), BackupCodec.validate(fixture(version = "2")))
        assertEquals(ImportResult.Err("单价历史数据不完整"), BackupCodec.validate(fixture(version = "3")))
        assertEquals(ImportResult.Err("单价历史数据不完整"), BackupCodec.validate(fixture(version = "4")))
    }
```
Add a `fixtureV4` helper and new tests, following the exact shape of the existing `fixtureV3` helper (built on top of it — a v4 fixture is a v3 fixture plus a `transfers` array):
```kotlin
@Suppress("LongParameterList")
private fun fixtureV4(
    transfersJson: String = """[{"id":"tr1","fromMemberId":"A","toMemberId":"G","amount":5,"date":"2026-07-06"}]""",
    membershipsJson: String = """[{"id":"mf1","memberId":"A","year":2026,"date":"2026-07-01","amount":50}]""",
    membersJson: String = """[
        {"id":"A","name":"阿安","isGuest":false},
        {"id":"G","name":"客串","isGuest":true}
    ]""",
    configJson: String = """{"defaultPaid":2000,"defaultCredit":2500,"membershipFee":50}""",
): String =
    """{"version":4,"members":$membersJson,"config":$configJson,
    "rates":[{"id":"rt1","date":"2026-01-01","rate":24}],
    "refills":[{"id":"r1","date":"2026-07-01","paid":600,"credit":750,
        "contributions":[{"memberId":"A","amount":600}]}],
    "payments":[{"id":"p1","memberId":"G","amount":25.6,"date":"2026-07-05"}],
    "sessions":[{"id":"s1","date":"2026-07-04","hours":4,"rate":24,
        "factor":0.8,"playerIds":["A","G"]}],
    "memberships":$membershipsJson,
    "transfers":$transfersJson}"""

private const val V4_NO_TRANSFERS =
    """{"version":4,"members":[{"id":"A","name":"阿安","isGuest":false}],
    "config":{"defaultPaid":2000,"defaultCredit":2500,"membershipFee":50},
    "rates":[{"id":"rt1","date":"2026-01-01","rate":24}],
    "refills":[],"payments":[],"sessions":[],"memberships":[]}"""
```
Add these tests to the `BackupCodecTest` class:
```kotlin
    @Test
    fun `v4 backup passes, missing or broken transfer data rejected`() {
        val ok = BackupCodec.validate(fixtureV4())
        assertIs<ImportResult.Ok>(ok)
        assertEquals(ImportResult.Summary(members = 2, sessions = 1, refills = 1), (ok as ImportResult.Ok).summary)

        assertEquals(ImportResult.Err("转账数据不完整"), BackupCodec.validate(V4_NO_TRANSFERS))
        val zeroAmount = """[{"id":"tr1","fromMemberId":"A","toMemberId":"G","amount":0,"date":"2026-07-06"}]"""
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV4(transfersJson = zeroAmount)))
        val badDate = """[{"id":"tr1","fromMemberId":"A","toMemberId":"G","amount":5,"date":"07/06/2026"}]"""
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV4(transfersJson = badDate)))
        val ghostFrom = """[{"id":"tr1","fromMemberId":"X","toMemberId":"G","amount":5,"date":"2026-07-06"}]"""
        assertEquals(
            ImportResult.Err("备份数据引用了不存在的成员"),
            BackupCodec.validate(fixtureV4(transfersJson = ghostFrom)),
        )
        val ghostTo = """[{"id":"tr1","fromMemberId":"A","toMemberId":"X","amount":5,"date":"2026-07-06"}]"""
        assertEquals(
            ImportResult.Err("备份数据引用了不存在的成员"),
            BackupCodec.validate(fixtureV4(transfersJson = ghostTo)),
        )
    }

    @Test
    fun `v1 through v3 import migrate to v4 in one decode`() {
        val r1 = BackupCodec.validate(fixture()) // v1 fixture
        assertIs<ImportResult.Ok>(r1)
        assertEquals(4, r1.data.version)
        assertEquals(emptyList(), r1.data.transfers)

        val r2 = BackupCodec.validate(fixtureV2()) // v2 fixture
        assertIs<ImportResult.Ok>(r2)
        assertEquals(4, r2.data.version)
        assertEquals(emptyList(), r2.data.transfers)

        val r3 = BackupCodec.validate(fixtureV3()) // v3 fixture
        assertIs<ImportResult.Ok>(r3)
        assertEquals(4, r3.data.version)
        assertEquals(emptyList(), r3.data.transfers)

        val out = BackupCodec.encode(r1.data)
        assertTrue(out.contains("\"version\":4") || out.contains("\"version\": 4"))
        assertIs<ImportResult.Ok>(BackupCodec.validate(out))
    }
```

`BackupRoundTripTest.kt` — the existing v1-fixture round-trip test asserts `data.version == 3`; bump to `4` and add a `transfers` assertion:
```kotlin
        assertEquals(4, data.version)
        assertEquals(Cents(2400), data.rates.single().rate)
        assertEquals(emptyList(), data.memberships)
        assertEquals(Cents(5000), data.config.membershipFee)
        assertEquals(emptyList(), data.transfers)
```

- [ ] **Step 2: red** — `gradlew.bat :domain:test` fails to compile (`Transfer` unresolved, `V4_NO_TRANSFERS`/`fixtureV4` unresolved, version assertions fail).

- [ ] **Step 3: Implement domain**

`LedgerData.kt` — add after `Membership`:
```kotlin
@Serializable
data class Transfer(
    val id: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amount: Cents,
    val date: String,
)
```
and in `LedgerData`: `version: Int = 4`; append after `memberships`:
```kotlin
    val transfers: List<Transfer> = emptyList(),
```

`BackupCodec.kt`:
- version check: `if (version != 1 && version != 2 && version != 3 && version != 4) { return ImportResult.Err("备份文件版本不兼容") }`
- inside the existing `else` branch (version >= 2), after the v3 membership block, add:
  ```kotlin
      if (version == 4) {
          val transfers = obj["transfers"] as? JsonArray ?: return ImportResult.Err("转账数据不完整")
          for (tr in transfers) {
              val tro = tr as? JsonObject ?: return ImportResult.Err("转账数据不完整")
              if (tro.stringOrNull("id").isNullOrEmpty() || !tro.dateOk("date") || !tro.positive("amount")) {
                  return ImportResult.Err("转账数据不完整")
              }
              if (tro.stringOrNull("fromMemberId") !in ids || tro.stringOrNull("toMemberId") !in ids) {
                  return ImportResult.Err("备份数据引用了不存在的成员")
              }
          }
      }
  ```
  (place this as a sibling to the existing `if (version == 3) { ... }` block, both nested inside the same `else`)
- migration chain: change `private fun migrate(obj: JsonObject): JsonObject = migrateToV3(migrateToV2(obj))` to:
  ```kotlin
      private fun migrate(obj: JsonObject): JsonObject = migrateToV4(migrateToV3(migrateToV2(obj)))
  ```
  and add, after `migrateToV3`:
  ```kotlin
      // v3 → v4 at the JSON layer: same reasoning as the earlier links in the chain.
      @Suppress("ReturnCount")
      private fun migrateToV4(obj: JsonObject): JsonObject {
          val version = (obj["version"] as? JsonPrimitive)?.intOrNull
          if (version == 4) return obj
          return buildJsonObject {
              obj.forEach { (k, v) -> put(k, if (k == "version") JsonPrimitive(4) else v) }
              put("transfers", buildJsonArray {})
          }
      }
  ```

- [ ] **Step 4: green + gates** — `gradlew.bat assembleDebug test ktlintCheck detekt` all green.
- [ ] **Step 5: Commit** — `feat: backup schema v4 with Transfer model and v1-v3 migration`

---

### Task 2: balance calculation + mutator (domain TDD)

**Interfaces produced:**
- `memberBalancesCents` (unchanged signature) — now also sums transfers
- `domain/edit/Transfers.kt` (new file):
  - `fun addTransfer(data: LedgerData, id: String, fromMemberId: String, toMemberId: String, amountCents: Long?, date: String): EditResult<Transfer>`
  - `fun deleteTransfer(data: LedgerData, id: String): LedgerData`

- [ ] **Step 1: domain tests first**

`LedgerCalcTest.kt` — add:
```kotlin
    @Test
    fun `memberBalancesCents includes transfers - sender down, receiver up, sum and pool unaffected`() {
        val data =
            LedgerData(
                members = listOf(Member("A", "阿安", false), Member("B", "小波", false)),
                refills =
                    listOf(
                        Refill(
                            "r1",
                            "2026-07-01",
                            Cents(10000),
                            Cents(12500),
                            listOf(Contribution("A", Cents(10000))),
                        ),
                    ),
                transfers = listOf(Transfer("t1", "A", "B", Cents(4000), "2026-07-05")),
            )
        val bal = memberBalancesCents(data)
        assertEquals(6000L, bal["A"]) // 10000 contributed - 4000 sent
        assertEquals(4000L, bal["B"]) // 4000 received
        assertEquals(12500L, poolRemainingCents(data)) // no sessions yet; pool = credit only, transfer-blind
    }
```
(imports: add `Transfer` to the existing `com.badmintonledger.domain.model.*` import block in this file.)

`domain/src/test/kotlin/com/badmintonledger/domain/edit/TransferTest.kt` (new):
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.calc.poolRemainingCents
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Transfer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TransferTest {
    // A has a 10000-cent balance (contributed 100, never played); B and the guest G have none.
    private fun fixture(): LedgerData {
        var data = addMember(LedgerData(), "A", "阿安", false).data
        data = addMember(data, "B", "小波", false).data
        data = addMember(data, "G", "客串", true).data
        return data.copy(
            refills =
                listOf(
                    Refill(
                        "r1",
                        "2026-07-01",
                        Cents(10000),
                        Cents(12500),
                        listOf(Contribution("A", Cents(10000))),
                    ),
                ),
        )
    }

    @Test
    fun `rejects missing or guest members, same-person, non-positive amount, bad date`() {
        val data = fixture()
        assertEquals(EditResult.Err("请选择转出成员"), addTransfer(data, "t1", "nope", "B", 1000, "2026-07-05"))
        assertEquals(EditResult.Err("请选择转出成员"), addTransfer(data, "t1", "G", "B", 1000, "2026-07-05"))
        assertEquals(EditResult.Err("请选择转入成员"), addTransfer(data, "t1", "A", "nope", 1000, "2026-07-05"))
        assertEquals(EditResult.Err("请选择转入成员"), addTransfer(data, "t1", "A", "G", 1000, "2026-07-05"))
        assertEquals(EditResult.Err("转出转入不能是同一人"), addTransfer(data, "t1", "A", "A", 1000, "2026-07-05"))
        assertEquals(EditResult.Err("金额需为正数"), addTransfer(data, "t1", "A", "B", -1, "2026-07-05"))
        assertEquals(EditResult.Err("金额需为正数"), addTransfer(data, "t1", "A", "B", null, "2026-07-05"))
        assertEquals(EditResult.Err("日期格式不正确"), addTransfer(data, "t1", "A", "B", 1000, "07/05/2026"))
        assertEquals(emptyList(), data.transfers)
    }

    @Test
    fun `rejects an amount exceeding the sender's current balance, allows the exact balance`() {
        val data = fixture() // A has 10000, B has 0
        assertEquals(EditResult.Err("转出成员余额不足"), addTransfer(data, "t1", "A", "B", 10001, "2026-07-05"))
        assertEquals(EditResult.Err("转出成员余额不足"), addTransfer(data, "t1", "B", "A", 1, "2026-07-05"))
        assertIs<EditResult.Ok<Transfer>>(addTransfer(data, "t1", "A", "B", 10000, "2026-07-05"))
    }

    @Test
    fun `succeeds - balance moves from sender to receiver, sum and pool unaffected`() {
        val data = fixture()
        val before = memberBalancesCents(data)
        val r = addTransfer(data, "t1", "A", "B", 4000, "2026-07-05")
        assertIs<EditResult.Ok<Transfer>>(r)
        assertEquals(Transfer("t1", "A", "B", Cents(4000), "2026-07-05"), r.value)
        assertEquals(1, r.data.transfers.size)

        val after = memberBalancesCents(r.data)
        assertEquals(before.getValue("A") - 4000, after["A"])
        assertEquals(before.getValue("B") + 4000, after["B"])
        assertEquals(before.values.sum(), after.values.sum())
        assertEquals(poolRemainingCents(data), poolRemainingCents(r.data))
    }

    @Test
    fun `delete removes the entry and balances revert as if it never happened`() {
        val data = fixture()
        val added = addTransfer(data, "t1", "A", "B", 4000, "2026-07-05")
        assertIs<EditResult.Ok<Transfer>>(added)
        val reverted = deleteTransfer(added.data, "t1")
        assertEquals(emptyList(), reverted.transfers)
        assertEquals(memberBalancesCents(data), memberBalancesCents(reverted))
    }
}
```

- [ ] **Step 2: red** — `gradlew.bat :domain:test` fails to compile.

- [ ] **Step 3: Implement**

`Calc.kt` — in `memberBalancesCents`, add the transfer term (between the `payments` and `sessions` loops):
```kotlin
    data.transfers.forEach { t ->
        bal[t.fromMemberId] = (bal[t.fromMemberId] ?: 0L) - t.amount.value
        bal[t.toMemberId] = (bal[t.toMemberId] ?: 0L) + t.amount.value
    }
```

`edit/Transfers.kt` (new):
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Transfer

private val DATE_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")

// 转账：将球馆余额从一名正式成员直接转移给另一名正式成员（线下已结清，仅由管理员在此登记）；
// 与会员年费账本无关，也不影响球馆额度（poolRemainingCents），纯粹是两人余额之间的再分配。
@Suppress("LongParameterList", "ReturnCount")
fun addTransfer(
    data: LedgerData,
    id: String,
    fromMemberId: String,
    toMemberId: String,
    amountCents: Long?,
    date: String,
): EditResult<Transfer> {
    val fromMember = data.members.firstOrNull { it.id == fromMemberId }
    if (fromMember == null || fromMember.isGuest) return EditResult.Err("请选择转出成员")
    val toMember = data.members.firstOrNull { it.id == toMemberId }
    if (toMember == null || toMember.isGuest) return EditResult.Err("请选择转入成员")
    if (fromMemberId == toMemberId) return EditResult.Err("转出转入不能是同一人")
    if (amountCents == null || amountCents <= 0) return EditResult.Err("金额需为正数")
    if (!DATE_RE.matches(date)) return EditResult.Err("日期格式不正确")
    val fromBalance = memberBalancesCents(data)[fromMemberId] ?: 0L
    if (amountCents > fromBalance) return EditResult.Err("转出成员余额不足")
    val t = Transfer(id, fromMemberId, toMemberId, Cents(amountCents), date)
    return EditResult.Ok(data.copy(transfers = data.transfers + t), t)
}

fun deleteTransfer(
    data: LedgerData,
    id: String,
): LedgerData = data.copy(transfers = data.transfers.filter { it.id != id })
```

- [ ] **Step 4: gates green.** Commit — `feat: member transfer mutator and balance calculation (domain)`

---

### Task 3: History integration (domain TDD)

**Interfaces produced:**
- `data class TransferHistoryRow(val id: String, val date: String, val desc: String)`
- `HistoryRows` gains `val transfers: List<TransferHistoryRow>`

- [ ] **Step 1: domain test first**

`HistoryTest.kt` — add (extend the existing `fixture()` with a transfer, or build inline):
```kotlin
    @Test
    fun `transfers listed newest-first, unfiltered by cutoff, with from-to description`() {
        val data =
            fixture().copy(
                transfers =
                    listOf(
                        Transfer("t1", "A", "G", Cents(500), "2020-01-01"),
                        Transfer("t2", "G", "A", Cents(2560), "2026-07-06"),
                    ),
            )
        val h = buildHistoryRows(data, cutoff = "2025-07-15") // even before the cutoff, t1 still shows
        assertEquals(listOf("t2", "t1"), h.transfers.map { it.id })
        assertEquals("客串 → 阿安 $25.6", h.transfers[0].desc) // rawDollars trims trailing zeros, like payments' "$25.6"
        assertEquals("阿安 → 客串 $5", h.transfers[1].desc)
    }
```
(add `import com.badmintonledger.domain.model.Transfer` to this file's imports.)

- [ ] **Step 2: red.**

- [ ] **Step 3: implement**

`History.kt`:
```kotlin
data class TransferHistoryRow(val id: String, val date: String, val desc: String)
```
`HistoryRows` gains `val transfers: List<TransferHistoryRow>`; in `buildHistoryRows`, add:
```kotlin
    val transfers =
        data.transfers.sortedByDescending { it.date }.map { t ->
            TransferHistoryRow(t.id, t.date, "${nameOf(t.fromMemberId)} → ${nameOf(t.toMemberId)} $${rawDollars(t.amount)}")
        }
    return HistoryRows(sessions, refills, payments, memberships, transfers)
```
(note: uses `rawDollars` — already imported in this file via the `Format.kt` same-package helper used for refills/payments — matching that trimmed-trailing-zero style, e.g. `$5` not `$5.00`, `$25.60` stays as-is since it's not a whole number.)

- [ ] **Step 4: gates green.** Commit — `feat: transfer records surface in History`

---

### Task 4: app layer wiring (ViewModel + TransferScreen + navigation)

**Interfaces produced:**
- `LedgerViewModel.addTransfer(fromMemberId: String, toMemberId: String, amountDollars: Double?, date: String): String?` (null = success)
- `LedgerViewModel.deleteTransfer(id: String)`
- `@Composable fun TransferScreen(vm: LedgerViewModel, onBack: () -> Unit)`

- [ ] **Step 1: ViewModel** (`LedgerViewModel.kt`) — add imports:
```kotlin
import com.badmintonledger.domain.edit.addTransfer as domainAddTransfer
import com.badmintonledger.domain.edit.deleteTransfer as domainDeleteTransfer
```
(insert alphabetically among the existing `import ... as domain...` block) and add, near `deleteMembershipFee`:
```kotlin
    /** Returns null on success, or the refusal reason. Amount arrives in dollars from the form. */
    fun addTransfer(
        fromMemberId: String,
        toMemberId: String,
        amountDollars: Double?,
        date: String,
    ): String? {
        val current = ledger.value ?: return "数据加载中，请稍后再试"
        val amountCents = amountDollars?.let(::dollarsToCents)
        return when (val r = domainAddTransfer(current, newId("t"), fromMemberId, toMemberId, amountCents, date)) {
            is EditResult.Ok -> {
                persist(r.data)
                null
            }
            is EditResult.Err -> r.reason
        }
    }

    fun deleteTransfer(id: String) {
        val current = ledger.value ?: return
        persist(domainDeleteTransfer(current, id))
    }
```

- [ ] **Step 2: new `app/src/main/kotlin/com/badmintonledger/app/ui/TransferScreen.kt`:**
```kotlin
package com.badmintonledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.badmintonledger.app.LedgerViewModel
import com.badmintonledger.app.ui.components.DateField
import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.model.Member
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun TransferScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit,
) {
    val data by vm.ledger.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var fromMember by remember { mutableStateOf<Member?>(null) }
    var toMember by remember { mutableStateOf<Member?>(null) }
    var amount by remember { mutableStateOf("") }
    var fromMenu by remember { mutableStateOf(false) }
    var toMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("转账") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = data ?: return@Scaffold
        val candidates = current.members.filter { !it.isGuest }
        val fromBalanceCents = fromMember?.let { memberBalancesCents(current)[it.id] ?: 0L } ?: 0L

        LaunchedEffect(fromMember) {
            fromMember?.let { amount = dollarsText(maxOf(fromBalanceCents, 0L)) }
        }

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
                label = "转账日期",
                value = date,
                onChange = { date = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ExposedDropdownMenuBox(expanded = fromMenu, onExpandedChange = { fromMenu = it }) {
                OutlinedTextField(
                    value = fromMember?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("转出成员") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = fromMenu, onDismissRequest = { fromMenu = false }) {
                    candidates.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.name) },
                            onClick = {
                                fromMember = m
                                if (toMember?.id == m.id) toMember = null
                                fromMenu = false
                            },
                        )
                    }
                }
            }
            if (fromMember != null) {
                Text("最多可转 \$${dollarsText(maxOf(fromBalanceCents, 0L))}", style = MaterialTheme.typography.bodySmall)
            }
            ExposedDropdownMenuBox(expanded = toMenu, onExpandedChange = { toMenu = it }) {
                OutlinedTextField(
                    value = toMember?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("转入成员") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = toMenu, onDismissRequest = { toMenu = false }) {
                    candidates.filter { it.id != fromMember?.id }.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.name) },
                            onClick = {
                                toMember = m
                                toMenu = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("金额（$）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = fromMember != null && toMember != null && !saving,
                onClick = {
                    val from = fromMember ?: return@Button
                    val to = toMember ?: return@Button
                    saving = true
                    val err = vm.addTransfer(from.id, to.id, amount.toDoubleOrNull(), date)
                    if (err == null) {
                        onBack()
                    } else {
                        saving = false
                        scope.launch { snackbar.showSnackbar(err) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) { Text("保存转账") }
        }
    }
}
```
(`dollarsText` is the existing `app/ui/Format.kt` helper, same package, no import needed.)

- [ ] **Step 3: `HomeScreen.kt`** — add a parameter and a button:
```kotlin
fun HomeScreen(
    vm: LedgerViewModel,
    onOpenSettings: () -> Unit,
    onRecordSession: () -> Unit,
    onOpenRefill: () -> Unit,
    onOpenPayment: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenHistory: () -> Unit,
) {
```
and in the `FlowRow` of buttons, after `Button(onClick = onOpenPayment) { Text("收款") }`:
```kotlin
                    OutlinedButton(onClick = onOpenTransfer) { Text("转账") }
```

- [ ] **Step 4: `AppNav.kt`** — wire the new param on the `HomeScreen` call:
```kotlin
                onOpenTransfer = { nav.navigate("transfer") { launchSingleTop = true } },
```
(insert after `onOpenPayment`) and add a new route, after the `"payment"` composable:
```kotlin
        composable("transfer") {
            TransferScreen(vm = vm, onBack = { nav.popBackStack() })
        }
```

- [ ] **Step 5: `HistoryScreen.kt`** — add a section after 会员年费记录:
```kotlin
            item { Text("转账记录（点击可删除）", style = MaterialTheme.typography.titleMedium) }
            items(rows.transfers, key = { "t" + it.id }) { t ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            action = HistoryAction.ConfirmDelete("转账") { vm.deleteTransfer(t.id) }
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Text(t.date, style = MaterialTheme.typography.titleSmall)
                    Text(t.desc, style = MaterialTheme.typography.bodySmall)
                }
            }
```

- [ ] **Step 6: gates green** — `gradlew.bat test ktlintCheck detekt assembleDebug` all green.
- [ ] **Step 7: Commit** — `feat: member transfer UI (new screen, Home entry point, History section)`

---

### Task 5: version bump, docs, and acceptance

- [ ] **Step 1:** `app/build.gradle.kts`: `versionCode = 4`, `versionName = "1.1.0"`.
- [ ] **Step 2:** Update `docs/domain-reference.md` in the same commit (this doc is the project's source of truth and must stay current, per its own header and `CLAUDE.md`):
  - Bump "Current version" line to `1.1.0` / schema v4.
  - Add `Transfer` to the data model table.
  - Add the transfer term to the `memberBalancesCents` formula under "Domain invariants" and to its row in the calc table.
  - Add `addTransfer`/`deleteTransfer` to the mutators table (same row style as the others), including the exact validation order and Chinese strings.
  - Add a bullet to the Backup schema section describing the v4 requirement and migration link.
  - Add `TransferHistoryRow` to the `History.kt` bullet in the report-builders section.
  - Add a short "转账 screen" paragraph to the App layer section (mirrors the "Settings screen card order" style), and mention the new `home` FlowRow button + `transfer` route in the Navigation section.
- [ ] **Step 3: gates** — `gradlew.bat test ktlintCheck detekt assembleDebug assembleRelease` all green.
- [ ] **Step 4: acceptance** — import a fresh v1/v2/v3 backup and confirm it migrates silently to v4 with `transfers: []`; on-phone manual pass: 转账 screen prefills the amount to the selected 转出成员's current balance and updates the cap text when a different sender is chosen; saving a transfer updates both members' Home balances correctly; attempting to pick the same member for both sides is prevented (the 转入成员 list excludes whoever is picked as 转出成员, and the domain check is a backstop); a guest never appears in either picker; History lists and can delete a transfer, with balances reverting live after delete.
- [ ] **Step 5:** Commit — `docs: bump version 1.1.0, update domain reference for member transfer`.

## Acceptance Checklist
- [ ] Gates green (`:domain:test`, `:app` build, ktlint, detekt)
- [ ] v1, v2, v3, and v4 backups all import; v1/v2/v3 documents migrate through the full chain to v4; export always emits v4
- [ ] A transfer moves balance from sender to receiver with the sum of all balances and `poolRemainingCents` both provably unaffected (dedicated regression tests)
- [ ] A transfer amount exceeding the sender's current balance is rejected; the exact balance amount is allowed (boundary case covered)
- [ ] Guests never appear as either side of a transfer, in the domain layer or the UI pickers
- [ ] `docs/domain-reference.md` updated in the same change — no behavior exists that isn't described there
- [ ] No `android.*` import under `domain/`; every new mutator returns a new document, never mutates
