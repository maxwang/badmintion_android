# Membership Fee & Member Disable (Backup v3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the WeChat mini program's 会员年费 (annual membership fee) & member soft-disable feature to Android: backup schema v3 with v1/v2 migration, the even-split bulk-charge mutator, a fully separate membership-debt ledger, member 启用/停用, and every UI surface (Settings, Session, Refill, Payment, History, Report/poster) that the WeChat feature touches.

**Architecture:** Additive, unlike the v2 port — no field is removed. `Member` gains `active: Boolean = true`; `Config` gains `membershipFee: Cents = Cents(5000)`; a new `Membership` model plus `LedgerData.memberships: List<Membership>`; `LedgerData.version` default becomes `3`. Migration chains v1→v2→v3 in `BackupCodec` before typed decode (same reasoning as the v2 port: the typed model would otherwise silently default the new fields). One new domain file (`edit/MembershipFees.kt`) holds every new mutator; existing `calc`/`report` files gain new functions/fields rather than new files, mirroring how court-rate-history extended `Calc.kt`/`Home.kt` in place.

**Tech Stack:** No new dependencies.

**Ground truth:** WeChat repo `E:\Code\ai\wechat\badminton`, commits `30cf02c`..`39623a0` (`f959b8f` member disable, `c60163b`/`81e2b84`/`9e4ae22` membership fee UI, `9c53fd5`/`3daefbe` picker filters, `adb9b6c`/`305da52`/`911d923` balance-separation + even-split fixes, `509adc6` report integration). Spec: `docs/superpowers/specs/2026-07-19-membership-fee-and-member-disable-android.md` (supersedes the v2 contract). Port `tests/data.test.js`, `tests/calc.test.js`, `tests/report.test.js` membership/active cases **case-for-case** — exact fixtures and expected values below, drawn directly from those files.

## Global Constraints

- Chinese copy verbatim from the WeChat source: 请选择成员, 金额需为正数, 日期格式不正确, 年份不正确, 该成员该年度已收取会费, 记录不存在, 会员年费数据不完整, 备份数据引用了不存在的成员, 启用, 会员年费, 会费总额（$/年，按正式且启用成员人数均分）, 收取{year}年会费, 谁交年费了？（勾选即结清会员年费，与球馆余额无关）, 保存年费收款, 会员年费记录（点击可删除）, 会员年费未付, 欠年费 $, 已开单/已付清.
- `memberBalancesCents` and `membershipBalancesCents` stay fully independent — every new test that touches either must assert the *other* is untouched, matching the WeChat regression tests.
- `chargeAnnualMembershipFee`'s even split reuses the exact floor-and-remainder rule already in `sessionShares`/`addRateChange`'s spirit: `base = totalCents / n` (integer division = floor for non-negative operands), last candidate gets `total - base * (n - 1)`.
- Money integer cents in domain; `membershipFee`/`membership.amount` are dollars on the wire (existing `Cents` serializer, same pattern as `defaultPaid`/`rate`). Domain purity; gates green at EVERY commit (`:domain` and `:app` both compile after every task).
- Adding `active`/`membershipFee` is purely additive — no existing call site of `Member(...)` or `Config(...)` breaks (Kotlin default parameters).
- Branch `feat/membership-fee-and-member-disable` off `main` (or current branch tip). Conventional commits.

## File Structure

```
domain/model/LedgerData.kt          T1 — Member.active, Config.membershipFee, Membership, LedgerData v3
domain/calc/Calc.kt                 T1 — membershipBalancesCents, MembershipStatus, membershipStatus
domain/backup/BackupCodec.kt        T1 — validate v1|v2|v3, migrateToV3, decode chains migrations
domain/edit/MemberEdits.kt          T1 — memberReferenced += memberships check
domain tests: LedgerDataJsonTest, LedgerCalcTest, BackupCodecTest, BackupRoundTripTest, MemberEditTest   T1

domain/edit/MembershipFees.kt (new) T2 — addMembershipFee, deleteMembershipFee, setMembershipFeePaid,
                                          chargeAnnualMembershipFee, MembershipChargeResult
domain/edit/MemberEdits.kt           T2 — setActive
domain tests: MembershipFeeTest (new), MemberEditTest (setActive)                                       T2

domain/report/Report.kt              T3 — MembershipDebtRow, membershipDebtRows, payloads gain membershipDebts
domain/report/Poster.kt              T3 — weeklyPosterLines/monthlyPosterLines gain 会员年费未付 block
domain/report/Recording.kt           T3 — MembershipDebtorRow, buildPaymentSummary gains membershipDebtors
domain/report/History.kt             T3 — MembershipHistoryRow, buildHistoryRows gains memberships
domain tests: ReportTest, PosterTest, RecordingTest, HistoryTest                                        T3

app: LedgerViewModel (setActive, chargeAnnualMembershipFee, settleMembershipDebtors, deleteMembershipFee) T4
app: SettingsScreen (启用 switch + 会员年费 card), SessionScreen (picker filter), RefillScreen (picker filter),
     PaymentScreen (谁交年费了 section), HistoryScreen (会员年费记录 section)                              T4
app/build.gradle.kts version bump                                                                        T5
```

---

### Task 1: schema v3 core — model, calc, migration, memberReferenced

One atomic commit (additive; nothing downstream breaks, but migration/validation must land together with the model so `:domain` compiles and the contract stays internally consistent).

**Interfaces produced:**
- `Member(id, name, isGuest, active: Boolean = true)`
- `Config(defaultPaid, defaultCredit, membershipFee: Cents = Cents(5000))`
- `data class Membership(val id: String, val memberId: String, val year: Int, val date: String, val amount: Cents, val paidDate: String? = null)`
- `LedgerData(version: Int = 3, …, memberships: List<Membership> = emptyList())` — `memberships` positioned last, after `sessions`
- `data class MembershipStatus(val eligible: Int, val charged: Int, val paid: Int)`
- `fun membershipBalancesCents(data: LedgerData): Map<String, Long>`
- `fun membershipStatus(data: LedgerData, year: Int): MembershipStatus`
- `BackupCodec.validate`/`decode` accept version 1|2|3; v3 requires `config.membershipFee` positive and a valid `memberships` array; `member.active`, if present, must be boolean

- [ ] **Step 1: domain tests first (red batch)**

`LedgerDataJsonTest.kt` — add:
```kotlin
    @Test
    fun `default LedgerData matches WeChat DEFAULT_DATA v3`() {
        val d = LedgerData()
        assertEquals(3, d.version)
        assertEquals(Config(Cents(200000), Cents(250000), Cents(5000)), d.config)
        assertEquals(emptyList(), d.memberships)
    }

    @Test
    fun `member active defaults true and is omitted-safe on decode`() {
        assertEquals(true, Member("A", "阿安", false).active)
    }

    @Test
    fun `v2 document decodes through migration gaining empty memberships and default fee`() {
        val v2 = """{"version":2,"members":[],"config":{"defaultPaid":2000,"defaultCredit":2500},
            "rates":[{"id":"rate_seed","date":"2000-01-01","rate":24}],
            "refills":[],"payments":[],"sessions":[]}"""
        val d = BackupCodec.decode(v2)
        assertEquals(3, d.version)
        assertEquals(Cents(5000), d.config.membershipFee)
        assertEquals(emptyList(), d.memberships)
    }
```
(The existing v1/v2 fixture-based decode/round-trip tests: bump their fixture to `"version": 3` with `"memberships": []` and `config` including `"membershipFee": 50`; keep all other assertions — same pattern as the v2 port's fixture bump.)

`LedgerCalcTest.kt` — add (port of today's calc.test.js membership block):
```kotlin
    @Test
    fun `membership balance is fully independent of member balance`() {
        val data =
            LedgerData(
                members = listOf(Member("A", "阿安", false)),
                refills = listOf(Refill("r1", "2026-07-01", Cents(10000), Cents(12500), listOf(Contribution("A", Cents(10000))))),
                memberships = listOf(Membership("mf1", "A", 2026, "2026-07-01", Cents(5000))),
            )
        assertEquals(10000L, memberBalancesCents(data)["A"]) // contributed 100, membership debt does not touch this
    }

    @Test
    fun `membershipBalancesCents - only unpaid entries count, independent of court balance`() {
        val data =
            LedgerData(
                members = listOf(Member("A", "阿安", false), Member("B", "小波", false)),
                memberships =
                    listOf(
                        Membership("mf1", "A", 2026, "2026-07-01", Cents(5000)),
                        Membership("mf2", "B", 2026, "2026-07-01", Cents(5000), paidDate = "2026-07-10"),
                    ),
            )
        val bal = membershipBalancesCents(data)
        assertEquals(-5000L, bal["A"]) // unpaid, owes $50
        assertEquals(0L, bal["B"]) // paid, no longer counted
    }

    @Test
    fun `membershipStatus - eligible skips guests and disabled, charged and paid count that year`() {
        val data =
            LedgerData(
                members =
                    listOf(
                        Member("A", "阿安", false),
                        Member("B", "小波", false),
                        Member("C", "陈叔", false, active = false),
                        Member("G", "客串", true),
                    ),
                memberships =
                    listOf(
                        Membership("mf1", "A", 2026, "2026-07-01", Cents(5000), paidDate = "2026-07-10"),
                        Membership("mf2", "B", 2026, "2026-07-01", Cents(5000)),
                    ),
            )
        assertEquals(MembershipStatus(eligible = 2, charged = 2, paid = 1), membershipStatus(data, 2026))
        assertEquals(MembershipStatus(eligible = 2, charged = 0, paid = 0), membershipStatus(data, 2025))
    }
```

`BackupCodecTest.kt` — add (port of today's data.test.js v3 validateImport block):
```kotlin
    private fun fixtureV3(
        membershipsJson: String = """[{"id":"mf1","memberId":"A","year":2026,"date":"2026-07-01","amount":50}]""",
        membersJson: String = """[
            {"id":"A","name":"阿安","isGuest":false},
            {"id":"G","name":"客串","isGuest":true}
        ]""",
        configJson: String = """{"defaultPaid":2000,"defaultCredit":2500,"membershipFee":50}""",
    ): String =
        """{"version":3,"members":$membersJson,"config":$configJson,
        "rates":[{"id":"rt1","date":"2026-01-01","rate":24}],
        "refills":[{"id":"r1","date":"2026-07-01","paid":600,"credit":750,
            "contributions":[{"memberId":"A","amount":600}]}],
        "payments":[{"id":"p1","memberId":"G","amount":25.6,"date":"2026-07-05"}],
        "sessions":[{"id":"s1","date":"2026-07-04","hours":4,"rate":24,
            "factor":0.8,"playerIds":["A","G"]}],
        "memberships":$membershipsJson}"""

Build the "missing memberships" / "missing membershipFee" cases as raw JSON strings directly, same style as `BackupCodecTest`'s existing `noMembers` case:
```kotlin
    private val v3NoMemberships =
        """{"version":3,"members":[{"id":"A","name":"阿安","isGuest":false}],
        "config":{"defaultPaid":2000,"defaultCredit":2500,"membershipFee":50},
        "rates":[{"id":"rt1","date":"2026-01-01","rate":24}],
        "refills":[],"payments":[],"sessions":[]}"""

    private val v3NoFee =
        """{"version":3,"members":[{"id":"A","name":"阿安","isGuest":false}],
        "config":{"defaultPaid":2000,"defaultCredit":2500},
        "rates":[{"id":"rt1","date":"2026-01-01","rate":24}],
        "refills":[],"payments":[],"sessions":[],"memberships":[]}"""

    @Test
    fun `v3 backup passes; missing or broken membership data rejected`() {
        val ok = BackupCodec.validate(fixtureV3())
        assertIs<ImportResult.Ok>(ok)
        assertEquals(ImportResult.Summary(members = 2, sessions = 1, refills = 1), (ok as ImportResult.Ok).summary)

        assertEquals(ImportResult.Err("会员年费数据不完整"), BackupCodec.validate(v3NoMemberships))
        assertEquals(ImportResult.Err("配置数据不完整"), BackupCodec.validate(v3NoFee))
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV3(membershipsJson = """[{"id":"mf1","memberId":"A","year":2026,"date":"2026-07-01","amount":0}]""")))
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV3(membershipsJson = """[{"id":"mf1","memberId":"A","year":2026.5,"date":"2026-07-01","amount":50}]""")))
        assertIs<ImportResult.Err>(BackupCodec.validate(fixtureV3(membershipsJson = """[{"id":"mf1","memberId":"A","year":2026,"date":"07/01/2026","amount":50}]""")))
        assertEquals(
            ImportResult.Err("备份数据引用了不存在的成员"),
            BackupCodec.validate(fixtureV3(membershipsJson = """[{"id":"mf1","memberId":"X","year":2026,"date":"2026-07-01","amount":50}]""")),
        )
    }

    @Test
    fun `membership paidDate optional but must be a valid date if present`() {
        assertIs<ImportResult.Ok>(
            BackupCodec.validate(fixtureV3(membershipsJson = """[{"id":"mf1","memberId":"A","year":2026,"date":"2026-07-01","amount":50,"paidDate":"2026-07-15"}]""")),
        )
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixtureV3(membershipsJson = """[{"id":"mf1","memberId":"A","year":2026,"date":"2026-07-01","amount":50,"paidDate":"07/15/2026"}]""")),
        )
    }

    @Test
    fun `member active optional but must be boolean if present`() {
        assertIs<ImportResult.Ok>(
            BackupCodec.validate(fixtureV3(membersJson = """[{"id":"A","name":"阿安","isGuest":false,"active":false},{"id":"G","name":"客串","isGuest":true}]""")),
        )
        assertIs<ImportResult.Err>(
            BackupCodec.validate(fixtureV3(membersJson = """[{"id":"A","name":"阿安","isGuest":false,"active":"no"},{"id":"G","name":"客串","isGuest":true}]""")),
        )
    }

    @Test
    fun `rejects version 4`() {
        // fixtureV3 with version forced to 4 via string replace on the leading literal
        assertEquals(ImportResult.Err("备份文件版本不兼容"), BackupCodec.validate(fixtureV3().replaceFirst("\"version\":3", "\"version\":4")))
    }

    @Test
    fun `v1 and v2 import migrate through to v3 in one decode`() {
        val r1 = BackupCodec.validate(fixture()) // existing v1 fixture
        assertIs<ImportResult.Ok>(r1)
        assertEquals(3, r1.data.version)
        assertEquals(emptyList(), r1.data.memberships)
        assertEquals(Cents(5000), r1.data.config.membershipFee)

        val r2 = BackupCodec.validate(fixtureV2()) // existing v2 fixture
        assertIs<ImportResult.Ok>(r2)
        assertEquals(3, r2.data.version)
        assertEquals(emptyList(), r2.data.memberships)
        assertEquals(Cents(5000), r2.data.config.membershipFee)
    }
```
Also update the existing `rejects non-objects and wrong versions` test: the `fixture(version = "3")` case (currently expecting 备份文件版本不兼容) now must expect `单价历史数据不完整` instead (v1 shape has no `rates`), and add a new `fixture(version = "4")` case for the actual version-rejection.

`BackupRoundTripTest.kt` — assert `data.version == 3`, `data.memberships == emptyList()`, `data.config.membershipFee == Cents(5000)` after decoding the v1 fixture; round-trip equality assertion stays.

`MemberEditTest.kt` — add:
```kotlin
    @Test
    fun `memberReferenced and hard-delete are blocked by a memberships entry alone`() {
        val data =
            LedgerData(members = listOf(Member("A", "阿安", false)))
                .copy(memberships = listOf(Membership("mf1", "A", 2026, "2026-07-01", Cents(5000))))
        assertEquals(true, memberReferenced(data, "A"))
        assertIs<EditResult.Err>(removeMember(data, "A"))
    }
```

- [ ] **Step 2: red** — `gradlew.bat :domain:test` fails to compile (`Membership`, `membershipBalancesCents`, etc. unresolved).

- [ ] **Step 3: Implement domain**

`LedgerData.kt`:
```kotlin
@Serializable
data class Member(val id: String, val name: String, val isGuest: Boolean, val active: Boolean = true)

@Serializable
data class Config(val defaultPaid: Cents, val defaultCredit: Cents, val membershipFee: Cents = Cents(5000))

@Serializable
data class Membership(
    val id: String,
    val memberId: String,
    val year: Int,
    val date: String,
    val amount: Cents,
    val paidDate: String? = null,
)
```
and in `LedgerData`: `version: Int = 3`; `config` default gains the third arg `Cents(5000)`; append after `sessions`:
```kotlin
    val memberships: List<Membership> = emptyList(),
```

`Calc.kt` — append (port of calc.js membershipBalancesCents/membershipStatus):
```kotlin
// 会员年费欠费（分）：仅统计未标记已付的记录；与球馆余额（memberBalancesCents）完全独立
fun membershipBalancesCents(data: LedgerData): Map<String, Long> {
    val bal = mutableMapOf<String, Long>()
    data.members.forEach { bal[it.id] = 0L }
    data.memberships.forEach { mf ->
        if (mf.paidDate != null) return@forEach
        bal[mf.memberId] = (bal[mf.memberId] ?: 0L) - mf.amount.value
    }
    return bal
}

data class MembershipStatus(val eligible: Int, val charged: Int, val paid: Int)

// 会员年费收取情况：eligible=正式且启用成员数，charged=其中已开单该年度会费的人数，paid=其中已标记付清的人数
fun membershipStatus(data: LedgerData, year: Int): MembershipStatus {
    val eligible = data.members.filter { !it.isGuest && it.active }
    val yearEntries = eligible.map { m -> data.memberships.firstOrNull { it.memberId == m.id && it.year == year } }
    return MembershipStatus(
        eligible = eligible.size,
        charged = yearEntries.count { it != null },
        paid = yearEntries.count { it?.paidDate != null },
    )
}
```

`MemberEdits.kt` — extend `memberReferenced`:
```kotlin
fun memberReferenced(data: LedgerData, id: String): Boolean =
    data.sessions.any { id in it.playerIds } ||
        data.payments.any { it.memberId == id } ||
        data.refills.any { r -> r.contributions.any { it.memberId == id } } ||
        data.memberships.any { it.memberId == id }
```

`BackupCodec.kt`:
- version check: `if (version != 1 && version != 2 && version != 3) return ImportResult.Err("备份文件版本不兼容")`
- member loop: after the existing checks, add
  ```kotlin
      if ("active" in mo && (mo["active"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == null) {
          return ImportResult.Err("成员数据不完整")
      }
  ```
  (only enforced when the key is present at all — check via `mo.containsKey("active")`, not `mo["active"] != null`, since JsonObject `containsKey` is the right presence test)
- after the existing v1/v2 rates branch, add the v3 branch:
  ```kotlin
      if (version == 3) {
          if (!config.positive("membershipFee")) return ImportResult.Err("配置数据不完整")
          val memberships = obj["memberships"] as? JsonArray ?: return ImportResult.Err("会员年费数据不完整")
          for (mf in memberships) {
              val mfo = mf as? JsonObject ?: return ImportResult.Err("会员年费数据不完整")
              val year = (mfo["year"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
              if (mfo.stringOrNull("id").isNullOrEmpty() || !mfo.dateOk("date") || !mfo.positive("amount") ||
                  year == null || year <= 0
              ) {
                  return ImportResult.Err("会员年费数据不完整")
              }
              if ("paidDate" in mfo && !mfo.dateOk("paidDate")) return ImportResult.Err("会员年费数据不完整")
              if (mfo.stringOrNull("memberId") !in ids) return ImportResult.Err("备份数据引用了不存在的成员")
          }
      }
  ```
  (place this branch inside the existing `else` for version >= 2, after the rates-history checks, so it only runs for v3 — mirror the JS `if (obj.version === 3) { ... }` nested inside the `else` block)
- migration: rename `migrateToV2` usage sites to a chain; add:
  ```kotlin
      // v2 → v3 at the JSON layer: same reasoning as v1 → v2 — the typed model would
      // otherwise silently default memberships/membershipFee before validation runs.
      private fun migrateToV3(obj: JsonObject): JsonObject {
          val version = (obj["version"] as? JsonPrimitive)?.intOrNull
          if (version == 3) return obj
          val config = obj["config"] as? JsonObject ?: return obj
          return buildJsonObject {
              obj.forEach { (k, v) ->
                  when (k) {
                      "version" -> put(k, 3)
                      "config" -> put(k, buildJsonObject { config.forEach { (ck, cv) -> put(ck, cv) }; put("membershipFee", 50) })
                      else -> put(k, v)
                  }
              }
              put("memberships", buildJsonArray {})
          }
      }

      private fun migrate(obj: JsonObject): JsonObject = migrateToV3(migrateToV2(obj))
  ```
  and change every call site that currently does `(root as? JsonObject)?.let(::migrateToV2)` / `migrateToV2(obj)` to `::migrate` / `migrate(obj)` (both in `decode` and in the happy-path `validate` return).

- [ ] **Step 4: green + gates** — `gradlew.bat assembleDebug test ktlintCheck detekt` all green.
- [ ] **Step 5: Commit** — `feat: backup schema v3 with membership fee model, calc, and v1/v2 migration`

---

### Task 2: membership fee mutators (domain TDD)

**Interfaces produced:** `domain/edit/MembershipFees.kt` (new file):
- `fun addMembershipFee(data: LedgerData, id: String, memberId: String, year: Int, date: String, amountCents: Long?): EditResult<Membership>`
- `fun deleteMembershipFee(data: LedgerData, id: String): LedgerData`
- `fun setMembershipFeePaid(data: LedgerData, id: String, paid: Boolean, date: String?): EditResult<Unit>`
- `data class MembershipChargeResult(val chargedNames: List<String>, val skippedNames: List<String>)`
- `fun chargeAnnualMembershipFee(data: LedgerData, ids: List<String>, year: Int, totalAmountDollars: Double?, date: String): EditResult<MembershipChargeResult>` — `ids.size` must equal the eligible pool size (`!isGuest && active`); caller sizes `ids` via `membershipStatus(data, year).eligible` (same predicate, matching the WeChat pattern of `chargeAnnualMembershipFee`'s candidate filter and `membershipStatus`'s eligible filter being independently identical one-liners in `data.js`/`calc.js`).
- `MemberEdits.kt` gains `fun setActive(data: LedgerData, id: String, active: Boolean): LedgerData`

- [ ] **Step 1: domain tests first**

`domain/src/test/kotlin/com/badmintonledger/domain/edit/MembershipFeeTest.kt` (new, port of today's data.test.js membership block):
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Membership
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MembershipFeeTest {
    @Test
    fun `add validates then rejects a duplicate member-year pair`() {
        val data = addMember(LedgerData(), "A", "阿安", false).data
        assertIs<EditResult.Err>(addMembershipFee(data, "mf1", "A", 2026, "2026-07-01", amountCents = -100))
        assertIs<EditResult.Err>(addMembershipFee(data, "mf1", "A", 2026, "bad-date", amountCents = 5000))
        assertIs<EditResult.Err>(addMembershipFee(data, "mf1", "A", 0, "2026-07-01", amountCents = 5000))
        assertIs<EditResult.Err>(addMembershipFee(data, "mf1", "", 2026, "2026-07-01", amountCents = 5000))

        val added = addMembershipFee(data, "mf1", "A", 2026, "2026-07-01", amountCents = 5000)
        assertIs<EditResult.Ok<Membership>>(added)
        assertEquals(Membership("mf1", "A", 2026, "2026-07-01", com.badmintonledger.domain.model.Cents(5000)), added.value)

        val dup = addMembershipFee(added.data, "mf2", "A", 2026, "2026-07-15", amountCents = 5000)
        assertEquals(EditResult.Err("该成员该年度已收取会费"), dup)

        val nextYear = addMembershipFee(added.data, "mf3", "A", 2027, "2027-07-01", amountCents = 5000)
        assertIs<EditResult.Ok<Membership>>(nextYear)
        assertEquals(2, nextYear.data.memberships.size)
    }

    @Test
    fun `delete removes only the matching entry`() {
        val data = LedgerData(memberships = listOf(Membership("mf1", "A", 2026, "2026-07-01", com.badmintonledger.domain.model.Cents(5000))))
        assertEquals(emptyList(), deleteMembershipFee(data, "mf1").memberships)
    }

    @Test
    fun `even split across two eligible members, skipping guest and disabled`() {
        var data = addMember(LedgerData(), "A", "阿安", false).data
        data = addMember(data, "B", "小波", false).data
        data = addMember(data, "G", "客串", true).data
        data = addMember(data, "C", "陈叔", false).data
        data = setActive(data, "C", false)
        data = addMembershipFee(data, "mf0", "B", 2026, "2026-06-01", amountCents = 5000).data

        val ids = List(membershipStatusEligible(data, 2026)) { "mf$it" }
        val r = chargeAnnualMembershipFee(data, ids, 2026, totalAmountDollars = 50.0, date = "2026-07-01")
        assertIs<EditResult.Ok<MembershipChargeResult>>(r)
        assertEquals(listOf("阿安"), r.value.chargedNames)
        assertEquals(listOf("小波"), r.value.skippedNames)
        assertEquals(2, r.data.memberships.size)
        assertEquals(2500L, r.data.memberships.first { it.memberId == "A" }.amount.value) // $50 / 2 = $25
        assertEquals(5000L, r.data.memberships.first { it.memberId == "B" }.amount.value) // untouched by this run

        // repeat run: everyone already billed -> all skipped, no new entries
        val ids2 = List(membershipStatusEligible(r.data, 2026)) { "mfx$it" }
        val r2 = chargeAnnualMembershipFee(r.data, ids2, 2026, 50.0, "2026-07-02")
        assertIs<EditResult.Ok<MembershipChargeResult>>(r2)
        assertEquals(emptyList(), r2.value.chargedNames)
        assertEquals(2, r2.data.memberships.size)
    }

    @Test
    fun `remainder absorbed by the last candidate, sum exact`() {
        var data = addMember(LedgerData(), "A", "阿安", false).data
        data = addMember(data, "B", "小波", false).data
        data = addMember(data, "C", "陈叔", false).data
        val ids = List(3) { "mf$it" }
        // $50 / 3: base = 5000 / 3 = 1666 cents; last gets 5000 - 1666*2 = 1668
        val r = chargeAnnualMembershipFee(data, ids, 2026, 50.0, "2026-07-01")
        assertIs<EditResult.Ok<MembershipChargeResult>>(r)
        assertEquals(listOf("阿安", "小波", "陈叔"), r.value.chargedNames)
        val amounts = listOf("A", "B", "C").map { id -> r.data.memberships.first { it.memberId == id }.amount.value }
        assertEquals(listOf(1666L, 1666L, 1668L), amounts)
        assertEquals(5000L, amounts.sum())
    }

    @Test
    fun `validates amount and date before charging anyone`() {
        val data = addMember(LedgerData(), "A", "阿安", false).data
        assertIs<EditResult.Err>(chargeAnnualMembershipFee(data, listOf("mf1"), 2026, -1.0, "2026-07-01"))
        assertIs<EditResult.Err>(chargeAnnualMembershipFee(data, listOf("mf1"), 2026, 50.0, "bad-date"))
        assertEquals(emptyList(), data.memberships)
    }

    @Test
    fun `empty candidate pool returns empty results without dividing by zero`() {
        val data = addMember(LedgerData(), "G", "客串", true).data // guest only, no eligible members
        val r = chargeAnnualMembershipFee(data, emptyList(), 2026, 50.0, "2026-07-01")
        assertEquals(EditResult.Ok(data, MembershipChargeResult(emptyList(), emptyList())), r)
    }

    @Test
    fun `setMembershipFeePaid toggles paidDate, rejects bad date and unknown id`() {
        val data = addMembershipFee(LedgerData(), "mf1", "A", 2026, "2026-07-01", 5000).data
        assertEquals(null, data.memberships[0].paidDate)

        assertIs<EditResult.Err>(setMembershipFeePaid(data, "mf1", true, "bad-date"))

        val paid = setMembershipFeePaid(data, "mf1", true, "2026-07-15")
        assertIs<EditResult.Ok<Unit>>(paid)
        assertEquals("2026-07-15", paid.data.memberships[0].paidDate)

        val unpaid = setMembershipFeePaid(paid.data, "mf1", false, null)
        assertIs<EditResult.Ok<Unit>>(unpaid)
        assertEquals(null, unpaid.data.memberships[0].paidDate)

        assertEquals(EditResult.Err("记录不存在"), setMembershipFeePaid(data, "nope", true, "2026-07-15"))
    }
}

private fun membershipStatusEligible(data: LedgerData, year: Int) =
    com.badmintonledger.domain.calc.membershipStatus(data, year).eligible
```
(Clean up imports at the top instead of fully-qualifying `Cents`/`membershipStatus` inline — shown qualified here only so every symbol used is unambiguous; the implementer writes normal `import` lines.)

`MemberEditTest.kt` — add:
```kotlin
    @Test
    fun `setActive toggles the flag, defaults true, unknown id is a no-op`() {
        val data = addMember(LedgerData(), "A", "阿安", false).data
        assertEquals(true, data.members[0].active)
        val disabled = setActive(data, "A", false)
        assertEquals(false, disabled.members[0].active)
        assertEquals(true, setActive(disabled, "A", true).members[0].active)
        assertEquals(disabled, setActive(disabled, "nope", true))
    }
```

- [ ] **Step 2: red** — `gradlew.bat :domain:test` fails to compile.

- [ ] **Step 3: implement**

`MemberEdits.kt` — append:
```kotlin
fun setActive(data: LedgerData, id: String, active: Boolean): LedgerData =
    data.copy(members = data.members.map { if (it.id == id) it.copy(active = active) else it })
```

`edit/MembershipFees.kt` (new):
```kotlin
package com.badmintonledger.domain.edit

import com.badmintonledger.domain.calc.membershipStatus
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Membership

private val DATE_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")

@Suppress("LongParameterList", "ReturnCount")
fun addMembershipFee(
    data: LedgerData,
    id: String,
    memberId: String,
    year: Int,
    date: String,
    amountCents: Long?,
): EditResult<Membership> {
    if (memberId.isEmpty()) return EditResult.Err("请选择成员")
    if (amountCents == null || amountCents <= 0) return EditResult.Err("金额需为正数")
    if (!DATE_RE.matches(date)) return EditResult.Err("日期格式不正确")
    if (year <= 0) return EditResult.Err("年份不正确")
    if (data.memberships.any { it.memberId == memberId && it.year == year }) {
        return EditResult.Err("该成员该年度已收取会费")
    }
    val mf = Membership(id, memberId, year, date, Cents(amountCents))
    return EditResult.Ok(data.copy(memberships = data.memberships + mf), mf)
}

fun deleteMembershipFee(data: LedgerData, id: String): LedgerData =
    data.copy(memberships = data.memberships.filter { it.id != id })

@Suppress("ReturnCount")
fun setMembershipFeePaid(
    data: LedgerData,
    id: String,
    paid: Boolean,
    date: String?,
): EditResult<Unit> {
    val mf = data.memberships.firstOrNull { it.id == id } ?: return EditResult.Err("记录不存在")
    if (paid && (date == null || !DATE_RE.matches(date))) return EditResult.Err("日期格式不正确")
    val updated = mf.copy(paidDate = if (paid) date else null)
    return EditResult.Ok(data.copy(memberships = data.memberships.map { if (it.id == id) updated else it }), Unit)
}

data class MembershipChargeResult(val chargedNames: List<String>, val skippedNames: List<String>)

// fields.totalAmountDollars 为俱乐部本年度会费总额，按当前符合条件的正式且启用成员人数均分
// （末位吸收取整余数，与 calc.sessionShares 的分摊规则一致）；已开单过的成员跳过，不重新计入其分摊
@Suppress("LongParameterList", "ReturnCount")
fun chargeAnnualMembershipFee(
    data: LedgerData,
    ids: List<String>,
    year: Int,
    totalAmountDollars: Double?,
    date: String,
): EditResult<MembershipChargeResult> {
    if (totalAmountDollars == null || !totalAmountDollars.isFinite() || totalAmountDollars <= 0) {
        return EditResult.Err("金额需为正数")
    }
    if (!DATE_RE.matches(date)) return EditResult.Err("日期格式不正确")
    if (year <= 0) return EditResult.Err("年份不正确")
    val candidates = data.members.filter { !it.isGuest && it.active }
    require(ids.size == candidates.size) { "one id per eligible member (see calc.membershipStatus.eligible)" }
    if (candidates.isEmpty()) return EditResult.Ok(data, MembershipChargeResult(emptyList(), emptyList()))
    val totalCents = com.badmintonledger.domain.model.dollarsToCents(totalAmountDollars)
    val n = candidates.size
    val baseCents = totalCents / n
    var doc = data
    val charged = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    candidates.forEachIndexed { i, m ->
        if (doc.memberships.any { it.memberId == m.id && it.year == year }) {
            skipped += m.name
            return@forEachIndexed
        }
        val shareCents = if (i == n - 1) totalCents - baseCents * (n - 1) else baseCents
        when (val r = addMembershipFee(doc, ids[i], m.id, year, date, shareCents)) {
            is EditResult.Ok -> {
                doc = r.data
                charged += m.name
            }
            is EditResult.Err -> return r
        }
    }
    return EditResult.Ok(doc, MembershipChargeResult(charged, skipped))
}
```
(implementer: move the `membershipStatus` import to the top of the file if referenced there — it currently is not, since `chargeAnnualMembershipFee` recomputes `candidates` itself, matching `data.js` doing the same filter independently of `calc.js`'s `membershipStatus`; the `ids.size == candidates.size` contract is what the caller satisfies by calling `membershipStatus(data, year).eligible` first, per the interface note above.)

- [ ] **Step 4: gates green.** Commit — `feat: membership fee mutators and member setActive (domain)`

---

### Task 3: report/poster/history/payment domain layer

**Interfaces produced:**
- `data class MembershipDebtRow(val name: String, val owedDollars: String)`; `fun membershipDebtRows(data: LedgerData): List<MembershipDebtRow>`
- `WeeklyPayload`/`MonthlyPayload` gain `val membershipDebts: List<MembershipDebtRow>`
- `weeklyPosterLines`/`monthlyPosterLines` gain the 会员年费未付 block
- `data class MembershipDebtorRow(val id: String, val name: String, val owedDollars: String)`; `PaymentSummary` gains `val membershipDebtors: List<MembershipDebtorRow>`
- `data class MembershipHistoryRow(val id: String, val date: String, val desc: String)`; `HistoryRows` gains `val memberships: List<MembershipHistoryRow>`

- [ ] **Step 1: domain tests first**

`ReportTest.kt` — add (port of today's report.test.js block; extend `fixture()` with a `memberships` param or build inline):
```kotlin
    @Test
    fun `unpaid membership fee stays out of court balances, appears only in membershipDebts`() {
        var data = fixture()
        data = data.copy(
            members = data.members + Member("M", "欠年费者", false),
            memberships = listOf(Membership("mf1", "M", 2026, "2026-07-01", Cents(5000))),
        )
        val w = buildWeeklyPayload(data, "s1")
        assertTrue(w.balances.none { it.name == "欠年费者" })
        assertEquals(listOf(MembershipDebtRow("欠年费者", "50.00")), w.membershipDebts)

        val mo = buildMonthlyPayload(data, "2026-07")
        assertTrue(mo.rows.none { it.name == "欠年费者" })
        assertEquals(listOf(MembershipDebtRow("欠年费者", "50.00")), mo.membershipDebts)
    }

    @Test
    fun `membershipDebtRows excludes paid entries`() {
        val data = fixture().copy(
            members = fixture().members + Member("M", "已付年费者", false),
            memberships = listOf(Membership("mf1", "M", 2026, "2026-07-01", Cents(5000), paidDate = "2026-07-10")),
        )
        assertEquals(emptyList(), membershipDebtRows(data))
    }
```

`PosterTest.kt` — add (check the existing test's fixture shape first; follow its established pattern for asserting `weeklyPosterLines`/`monthlyPosterLines` output — the new case asserts a payload with non-empty `membershipDebts` produces a gray "会员年费未付" `TextLine` immediately followed by one red-right `TextLine` per debtor, positioned before the final "球馆额度剩余" line, and that an empty `membershipDebts` list produces no such lines at all — mirroring the existing balance-rows assertions in that file).

`RecordingTest.kt` — add:
```kotlin
    @Test
    fun `payment summary includes independent membership debtors`() {
        val data = fixture().copy(memberships = listOf(Membership("mf1", "G", 2026, "2026-07-01", Cents(5000))))
        val s = buildPaymentSummary(data)
        assertEquals(listOf(MembershipDebtorRow("G", "客串", "50.00")), s.membershipDebtors)
        // court-fee debtors/rows unaffected by the membership entry
        assertEquals(listOf(DebtorRow("G", "客串", 2560, "25.60")), s.debtors)
    }
```

`HistoryTest.kt` — add:
```kotlin
    @Test
    fun `memberships listed newest-first, unfiltered by cutoff, paid tag reflects paidDate`() {
        val data = fixture().copy(
            memberships =
                listOf(
                    Membership("mf1", "A", 2025, "2025-07-01", Cents(5000)),
                    Membership("mf2", "A", 2026, "2026-07-01", Cents(2500), paidDate = "2026-07-10"),
                ),
        )
        val h = buildHistoryRows(data, cutoff = "2025-07-15") // even before the cutoff, mf1 still shows
        assertEquals(listOf("mf2", "mf1"), h.memberships.map { it.id })
        assertEquals("阿安 2026年度 $25.00（已付）", h.memberships[0].desc)
        assertEquals("阿安 2025年度 $50.00（未付）", h.memberships[1].desc)
    }
```

- [ ] **Step 2: red.**

- [ ] **Step 3: implement**

`Report.kt`:
```kotlin
data class MembershipDebtRow(val name: String, val owedDollars: String)

// 会员年费未付：与球馆余额（balanceRows）完全独立的单独列表，只列未标记已付的成员
fun membershipDebtRows(data: LedgerData): List<MembershipDebtRow> {
    val bal = membershipBalancesCents(data)
    return data.members
        .filter { (bal[it.id] ?: 0L) < 0L }
        .map { MembershipDebtRow(it.name, centsToDollars(-(bal[it.id] ?: 0L))) }
}
```
add `import com.badmintonledger.domain.calc.membershipBalancesCents`; add `membershipDebts: List<MembershipDebtRow>` to both payload data classes; in `buildWeeklyPayload`/`buildMonthlyPayload` add `membershipDebts = membershipDebtRows(data)`.

`Poster.kt` — in both `weeklyPosterLines` and `monthlyPosterLines`, right before the final `球馆额度剩余` line:
```kotlin
    if (p.membershipDebts.isNotEmpty()) {
        lines += PosterLine.TextLine("会员年费未付", color = PosterColors.GRAY, gap = 10)
    }
    p.membershipDebts.forEach { b ->
        lines += PosterLine.TextLine(b.name, size = 32, right = "欠 \$" + b.owedDollars, rightColor = PosterColors.RED)
    }
```

`Recording.kt`:
```kotlin
data class MembershipDebtorRow(val id: String, val name: String, val owedDollars: String)
```
`PaymentSummary` gains `val membershipDebtors: List<MembershipDebtorRow>`; in `buildPaymentSummary`:
```kotlin
    val membershipBal = membershipBalancesCents(data)
    val membershipDebtors =
        data.members.mapNotNull { m ->
            val c = membershipBal[m.id] ?: 0L
            if (c < 0L) MembershipDebtorRow(m.id, m.name, centsToDollars(-c)) else null
        }
    return PaymentSummary(debtors, rows, membershipDebtors)
```
(add `import com.badmintonledger.domain.calc.membershipBalancesCents`).

`History.kt`:
```kotlin
data class MembershipHistoryRow(val id: String, val date: String, val desc: String)
```
`HistoryRows` gains `val memberships: List<MembershipHistoryRow>`; in `buildHistoryRows`:
```kotlin
    val memberships =
        data.memberships.sortedByDescending { it.date }.map { mf ->
            val paidTag = if (mf.paidDate != null) "（已付）" else "（未付）"
            MembershipHistoryRow(mf.id, mf.date, "${nameOf(mf.memberId)} ${mf.year}年度 $${centsToDollars(mf.amount.value)}$paidTag")
        }
    return HistoryRows(sessions, refills, payments, memberships)
```

- [ ] **Step 4: gates green.** Commit — `feat: membership debt surfaces in reports, poster, payment, and history`

---

### Task 4: app layer wiring (ViewModel + screens)

**Interfaces produced:**
- `LedgerViewModel.setActive(id: String, active: Boolean)`
- `sealed interface ChargeMembershipFeeResult { data class Charged(val chargedCount: Int, val skippedCount: Int, val perPersonDollars: String); data class Rejected(val reason: String) }`
- `LedgerViewModel.chargeAnnualMembershipFee(year: Int, totalDollars: Double?, date: String): ChargeMembershipFeeResult`
- `LedgerViewModel.settleMembershipDebtors(memberIds: List<String>, date: String): String?`
- `LedgerViewModel.deleteMembershipFee(id: String)`

- [ ] **Step 1: ViewModel** (`LedgerViewModel.kt`):
```kotlin
import com.badmintonledger.domain.calc.membershipStatus
import com.badmintonledger.domain.edit.setActive as domainSetActive
import com.badmintonledger.domain.edit.setMembershipFeePaid as domainSetMembershipFeePaid
import com.badmintonledger.domain.edit.deleteMembershipFee as domainDeleteMembershipFee
import com.badmintonledger.domain.edit.chargeAnnualMembershipFee as domainChargeAnnualMembershipFee
import com.badmintonledger.domain.model.centsToDollars

sealed interface ChargeMembershipFeeResult {
    data class Charged(val chargedCount: Int, val skippedCount: Int, val perPersonDollars: String) : ChargeMembershipFeeResult
    data class Rejected(val reason: String) : ChargeMembershipFeeResult
}

    fun setActive(id: String, active: Boolean) {
        val current = ledger.value ?: return
        persist(domainSetActive(current, id, active))
    }

    /** Bulk-bills [year]'s membership fee, splitting [totalDollars] evenly; persists it as the new default prefill. */
    fun chargeAnnualMembershipFee(year: Int, totalDollars: Double?, date: String): ChargeMembershipFeeResult {
        val current = ledger.value ?: return ChargeMembershipFeeResult.Rejected("数据加载中，请稍后再试")
        val eligible = membershipStatus(current, year).eligible
        val ids = List(eligible) { newId("mf") }
        return when (val r = domainChargeAnnualMembershipFee(current, ids, year, totalDollars, date)) {
            is EditResult.Ok -> {
                val totalCents = dollarsToCents(totalDollars!!)
                persist(r.data.copy(config = r.data.config.copy(membershipFee = Cents(totalCents))))
                val perPersonCents = if (eligible > 0) totalCents / eligible else 0L
                ChargeMembershipFeeResult.Charged(
                    chargedCount = r.value.chargedNames.size,
                    skippedCount = r.value.skippedNames.size,
                    perPersonDollars = centsToDollars(perPersonCents),
                )
            }
            is EditResult.Err -> ChargeMembershipFeeResult.Rejected(r.reason)
        }
    }

    /** Marks every unpaid membership entry for each selected member paid (port of payment.js saveMembership). */
    fun settleMembershipDebtors(memberIds: List<String>, date: String): String? {
        var doc = ledger.value ?: return "数据加载中，请稍后再试"
        for (memberId in memberIds) {
            val unpaidIds = doc.memberships.filter { it.memberId == memberId && it.paidDate == null }.map { it.id }
            for (mfId in unpaidIds) {
                when (val r = domainSetMembershipFeePaid(doc, mfId, true, date)) {
                    is EditResult.Ok -> doc = r.data
                    is EditResult.Err -> return r.reason
                }
            }
        }
        persist(doc)
        return null
    }

    fun deleteMembershipFee(id: String) {
        val current = ledger.value ?: return
        persist(domainDeleteMembershipFee(current, id))
    }
```

- [ ] **Step 2: SettingsScreen** — member row gains a second switch:
```kotlin
                    Text("启用", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = member.active,
                        onCheckedChange = { vm.setActive(member.id, it) },
                    )
```
placed after the existing 补位 switch, before the delete `IconButton`. New card inserted between 成员管理 and 球馆单价 (state hoisted like the rate card):
```kotlin
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Text("会员年费", style = MaterialTheme.typography.titleMedium) }
            item {
                val year = remember { LocalDate.now().year }
                val status = remember(current) { membershipStatus(current, year) }
                Text("${year}年度：共 ${status.eligible} 名正式成员，已开单 ${status.charged}，已付清 ${status.paid}")
            }
            item {
                Text("会费总额（$/年，按正式且启用成员人数均分）", style = MaterialTheme.typography.bodySmall)
            }
            item {
                OutlinedTextField(
                    value = membershipFee,
                    onValueChange = { membershipFee = it },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            item {
                Button(onClick = {
                    val year = LocalDate.now().year
                    when (val r = vm.chargeAnnualMembershipFee(year, membershipFee.toDoubleOrNull(), LocalDate.now().toString())) {
                        is ChargeMembershipFeeResult.Charged ->
                            scope.launch {
                                snackbar.showSnackbar(
                                    if (r.chargedCount == 0) {
                                        "本年度会费已全部开单"
                                    } else {
                                        "已开单 ${r.chargedCount} 人，每人约 \$${r.perPersonDollars}" +
                                            if (r.skippedCount > 0) "，跳过 ${r.skippedCount} 人（已开单）" else ""
                                    },
                                )
                            }
                        is ChargeMembershipFeeResult.Rejected -> scope.launch { snackbar.showSnackbar(r.reason) }
                    }
                }) { Text("收取${LocalDate.now().year}年会费") }
            }
            item {
                Text(
                    "这里只登记应缴金额（按人数均分后开单）；实际收到付款后，请到「收款」页标记付清。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
```
add `var membershipFee by remember { mutableStateOf("") }` alongside the other form state, populated in the existing `LaunchedEffect(data?.config)` block: `membershipFee = dollarsText(it.membershipFee.value)`.

- [ ] **Step 3: SessionScreen picker filter** — the chip `FlowRow` iterates `current.members` directly today; change to:
```kotlin
                current.members.filter { it.active || selected[it.id] == true }.forEach { m ->
```
(mirrors `pickableMembers`: active, or already selected — so editing an existing session with a since-disabled player still shows them).

- [ ] **Step 4: RefillScreen picker filter** — `val funders = current.members.filter { !it.isGuest }` becomes `val funders = current.members.filter { !it.isGuest && it.active }`.

- [ ] **Step 5: PaymentScreen** — add the second section between the existing debtor `FlowRow`/empty-state and the `当前余额` `Card`:
```kotlin
            var membershipDate by remember { mutableStateOf(LocalDate.now().toString()) }
            val membershipSelected = remember { mutableStateMapOf<String, Boolean>() }
            var savingMembership by remember { mutableStateOf(false) }
            if (summary.membershipDebtors.isNotEmpty()) {
                Text(
                    "谁交年费了？（勾选即结清会员年费，与球馆余额无关）",
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.membershipDebtors.forEach { d ->
                        FilterChip(
                            selected = membershipSelected[d.id] == true,
                            onClick = { membershipSelected[d.id] = membershipSelected[d.id] != true },
                            label = { Text("${d.name} 欠年费 \$${d.owedDollars}") },
                        )
                    }
                }
                Button(
                    enabled = summary.membershipDebtors.any { membershipSelected[it.id] == true } && !savingMembership,
                    onClick = {
                        savingMembership = true
                        val picked = summary.membershipDebtors.filter { membershipSelected[it.id] == true }.map { it.id }
                        val err = vm.settleMembershipDebtors(picked, membershipDate)
                        if (err == null) {
                            onBack()
                        } else {
                            savingMembership = false
                            scope.launch { snackbar.showSnackbar(err) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存年费收款") }
            }
```
(uses the same `date` field as the court-fee section — WeChat's `payment.js` shares one `date` field across both `save`/`saveMembership`, so reuse the existing `date` state instead of a second one; drop the unused `membershipDate` above and use `date`).

- [ ] **Step 6: HistoryScreen** — add a fourth section mirroring 充值/收款:
```kotlin
            item { Text("会员年费记录（点击可删除）", style = MaterialTheme.typography.titleMedium) }
            items(rows.memberships, key = { "mf" + it.id }) { mf ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            action = HistoryAction.ConfirmDelete("会员年费") { vm.deleteMembershipFee(mf.id) }
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Text(mf.date, style = MaterialTheme.typography.titleSmall)
                    Text(mf.desc, style = MaterialTheme.typography.bodySmall)
                }
            }
```

- [ ] **Step 7: gates green.** Commit — `feat: membership fee and member-disable UI (Settings, Session, Refill, Payment, History)`

---

### Task 5: version bump + full gates + acceptance

- [ ] **Step 1:** `app/build.gradle.kts`: `versionCode = 3`, `versionName = "0.3"`.
- [ ] **Step 2: gates** — `gradlew.bat test ktlintCheck detekt assembleDebug assembleRelease` all green.
- [ ] **Step 3: acceptance** — import a fresh WeChat v3 export (should succeed); export Android→WeChat and re-import there (round trip both directions green); on-phone manual pass mirroring the WeChat spec's manual checklist: Settings shows 会员年费 card + 启用 switch; billing updates the status line and toasts the per-person share; disabling a member removes them from Session/Refill new-add pickers but an already-selected disabled player stays visible when editing; Payment page shows an independent 谁交年费了 section; weekly/monthly posters show a red 会员年费未付 section that disappears once settled; History lists and can delete a membership entry with the correct 已付/未付 tag.
- [ ] **Step 4:** Commit — `feat: reset-all-data-compatible version 0.3` (or fold the version bump into Task 4's commit if no separate change is needed beyond the gradle file).

## Acceptance Checklist
- [ ] Gates green (`:domain:test`, `:app` build, ktlint, detekt)
- [ ] v1, v2, and v3 backups all import; v1/v2 documents migrate through the full chain to v3; export always emits v3, accepted by the current WeChat build
- [ ] `memberBalancesCents` and `membershipBalancesCents` remain provably independent (dedicated regression tests both ways)
- [ ] Even-split billing matches WeChat exactly, including the last-candidate-absorbs-remainder case
- [ ] No `android.*` under `domain/`; every new mutator returns a new document, never mutates
