# Domain & App Behavior Reference

**This document is the authoritative, self-contained description of what the app currently does.** It is maintained alongside the code — whenever a change lands, update the relevant section here in the same commit/PR. Do not rely on the WeChat mini program (`E:\Code\ai\wechat\badminton`) to understand behavior; that project was the origin of this app's design but is no longer the source of truth and may have since diverged. The `docs/superpowers/specs/*.md` files record the historical *why* behind each milestone; this file records the current *what*.

Current version: **1.1.0** (`versionCode 4`), backup schema **v4**.

## Money conventions

- All money is stored internally as **integer cents** (`Cents` value class wrapping a `Long`).
- On the JSON wire (backup files, the on-device persisted document, which use the same format) money is a **dollar number** (e.g. `24.5`, not `2450`). `Cents` has a custom `kotlinx.serialization` serializer (`CentsAsDollarsSerializer`) that converts both ways: `dollarsToCents(dollars) = round(dollars * 100)`, `centsToDollars(cents)` formats with exactly two decimals and a sign, e.g. `-12.80`.
- `domain/` has zero `android.*` imports — it is pure Kotlin, usable as a future KMP shared module.
- Every mutator function is pure: it takes a `LedgerData` and returns either a new `LedgerData` (via `.copy`, never mutating the input) or a rejection reason. This is enforced by convention, not the type system, but every mutator test explicitly checks the original document is untouched where that matters.

## Data model (`domain/model/LedgerData.kt`)

```kotlin
LedgerData(
    version: Int = 4,
    members: List<Member> = emptyList(),
    config: Config = Config(defaultPaid=2000.00, defaultCredit=2500.00, membershipFee=50.00),
    rates: List<RateChange> = [RateChange("rate_seed", "2000-01-01", 24.00)],
    refills: List<Refill> = emptyList(),
    payments: List<Payment> = emptyList(),
    sessions: List<Session> = emptyList(),
    memberships: List<Membership> = emptyList(),
    transfers: List<Transfer> = emptyList(),
)
```

| Type | Fields | Notes |
|---|---|---|
| `Member` | `id, name, isGuest, active=true` | `isGuest` = "补位" (substitute), never a paying/eligible member for membership fees. `active` = whether this member should appear in *new-record* pickers; `false` = "停用" (disabled). Absent/true in imported JSON both mean active. |
| `Config` | `defaultPaid, defaultCredit, membershipFee` | Prefill defaults, not historized. `defaultPaid`/`defaultCredit` seed the Refill screen (their ratio also backs `currentFactor` when there are no refills yet). `membershipFee` is the club's **total** yearly due (not per-member), prefilled into the Settings 会员年费 card and updated to whatever was last actually charged. |
| `RateChange` | `id, date, rate` | Append-only, dated court hourly rate history. Never edited or deleted — a session snapshots its own rate at creation time, so history only serves as a *prefill* source for new sessions. Guaranteed non-empty (seeded by default/migration). |
| `Contribution` | `memberId, amount` | One member's cash contribution within a `Refill`. |
| `Refill` | `id, date, paid, credit, contributions` | A top-up event: the group actually paid `paid` dollars to the venue and received `credit` dollars of usable court credit (the discount factor is `paid / credit`, always ≤ 1 in practice). `contributions` is how members split funding that refill — need not be even, and need not sum to anything except `paid` (enforced, see below). |
| `Payment` | `id, memberId, amount, date` | A member paying off court-fee debt in cash. Independent of refills — recorded whenever someone settles up. |
| `Session` | `id, date, hours, rate, factor, playerIds` | One recorded playing week. `rate`/`factor` are **snapshotted** at save time — later court-rate or refill-factor changes never retroactively change a past session's cost. |
| `Membership` | `id, memberId, year, date, amount, paidDate=null` | One member's annual membership fee for one `year`. `amount` is *that member's own share* (not the club total — see `chargeAnnualMembershipFee` below). `paidDate` absent = billed-but-unpaid. |
| `Transfer` | `id, fromMemberId, toMemberId, amount, date` | An admin-recorded, already-settled-offline reassignment of court balance from one non-guest member to another. Only ever moves a number between the two members' `memberBalancesCents` entries — never touches `poolRemainingCents` or the membership-fee ledger. |

### Two completely independent ledgers

This is the single most important invariant in the whole app: **court-fee balance and membership-fee balance never touch each other.**

- `memberBalancesCents` (court fees) reads only `refills`, `payments`, `sessions`, `transfers` — it has never read `memberships` and must never be changed to.
- `membershipBalancesCents` (membership fees) reads only `memberships` — it has never read `refills`/`payments`/`sessions`.
- A member's prepaid court credit is never silently drawn down to cover a membership bill, and vice versa. Settling one never moves a number in the other, anywhere in the UI (Home, Payment, History, reports/posters all keep the two visually and numerically separate).

## Domain invariants (headline rules)

- **Session cost:** face cost (cents) = `round(hours × rate × 100)`; real cost = `round(hours × rate × factor × 100)`. Split evenly across `playerIds`; the base share is `floor(total / n)` and **the last player in the list absorbs the rounding remainder**, so the sum of shares is always exactly the total. This exact rule (floor + last-absorbs-remainder) is reused everywhere an amount needs splitting: session shares, and the annual membership fee split.
- **Member balance** = Σ refill contributions + Σ cash payments − Σ session shares − Σ transfers sent + Σ transfers received. Positive = credit remaining, negative = owes. A transfer only ever redistributes balance between the two members involved — it changes neither the sum of all balances nor the venue pool.
- **Venue pool** = Σ refill `credit` − Σ session **face** costs (not real/discounted cost — the pool tracks raw court-time consumed against the credit bought).
- **Current factor** (`currentFactor`) = the most recent refill's `paid / credit` ratio by date; if there are no refills yet, falls back to `config.defaultPaid / config.defaultCredit`.
- **Current rate** (`currentRate(data, date)`) = the `rates` entry with the latest `date ≤ date`; if `date` is before every entry, falls back to the chronologically earliest entry. Only used as a *prefill* for new sessions — never changes a session already saved.
- **Week = Monday-start.** `weekStart(dateStr)` returns the Monday of that date's week. `findSessionInWeek` looks for an existing session in the same week (excluding the record's own id when editing) — **at most one session per week** is enforced by `addSession`/`updateSession` (`该周已有记录，请编辑原记录` / `目标周已有另一条记录`).
- **Hard-delete protection:** a member cannot be hard-deleted (`removeMember`) if referenced anywhere — any session's `playerIds`, any `payment.memberId`, any refill `contribution.memberId`, or any `membership.memberId` (`该成员已有记录，不能删除`). Disabling (`setActive(..., false)`) is the path forward for anyone with real history; hard-delete only works for members added by mistake with zero records.
- **Member disable is a picker filter, not a data filter.** A disabled member still shows everywhere existing state is displayed (Home balance list, Payment screen including as a debtor, reports/posters, History) and remains fully payable. Disabling only removes them from pickers used to add someone to a *new, forward-looking* record: the Session screen's player-chip list (unless they're already selected — e.g. editing a session that already includes a since-disabled player keeps that player visible) and the Refill screen's contribution list (which already excluded guests).
- **Money amounts must be positive**; dates must match `YYYY-MM-DD` exactly; every rejection returns a Chinese-language reason string (see the mutator table below) rather than throwing.

## `domain/calc/Calc.kt` — pure calculations

| Function | Signature | Behavior |
|---|---|---|
| `sessionRealCostCents` | `(Session) -> Long` | `round(hours × rate.value × factor)` |
| `sessionFaceCostCents` | `(Session) -> Long` | `round(hours × rate.value)` |
| `sessionShares` | `(Session) -> SessionShares(totalCents, shares: Map<memberId, Long>)` | Even split, last player absorbs remainder; empty map if no players. |
| `memberBalancesCents` | `(data, excludeSessionId=null) -> Map<memberId, Long>` | Contributions + payments − session shares − transfers out + transfers in, per the invariant above. `excludeSessionId` computes the balance *as if* that one session never happened (used for weekly "before" balances). |
| `poolRemainingCents` | `(data) -> Long` | Σ refill credit − Σ session face cost. |
| `currentFactor` | `(data) -> Double` | Latest refill's `paid/credit`, else `config.defaultPaid/config.defaultCredit`. |
| `currentRate` | `(data, dateStr) -> Cents` | Latest `rates` entry with `date ≤ dateStr`, else the earliest entry. Assumes `rates` is non-empty (guaranteed by construction). |
| `monthSummary` | `(data, ym: "YYYY-MM") -> MonthSummary(weeks, totalCents, perMember: Map<memberId, MemberMonth(count, shareCents)>)` | Sessions whose `date` starts with `ym`. |
| `hasContributed` | `(data, memberId) -> Boolean` | True only if the member funded a refill with a positive amount; cash `payments` do **not** count (this is what makes them show up on Home even at a zero balance if they've ever funded a refill). |
| `membershipBalancesCents` | `(data) -> Map<memberId, Long>` | For every unpaid (`paidDate == null`) `memberships` entry, subtract `amount`; paid entries contribute nothing. Fully independent of `memberBalancesCents`. |
| `membershipStatus` | `(data, year: Int) -> MembershipStatus(eligible, charged, paid)` | `eligible` = members with `!isGuest && active`; `charged` = how many of those have a `memberships` entry for `year` (billed, any paid status); `paid` = how many of the charged ones are also paid. |

## `domain/edit/*.kt` — mutators

Every mutator returns `EditResult<T>` — `EditResult.Ok(newData, value)` or `EditResult.Err(reason)` — except pure appends/removals that can't fail (`renameMember`, `setGuest`, `setActive`, `deleteSession`, `deleteRefill`, `deletePayment`, `deleteMembershipFee`, `deleteTransfer`), which return the new `LedgerData` directly. Rejection reasons are the exact Chinese strings shown in the UI as toasts/snackbars.

| Function | File | Validates / rejects with | On success |
|---|---|---|---|
| `addMember(data, id, name, isGuest)` | MemberEdits.kt | — (always succeeds) | Appends `Member(id, name, isGuest, active=true)`. |
| `renameMember(data, id, name)` | MemberEdits.kt | — | Renames; no-op if `id` doesn't match. |
| `setGuest(data, id, isGuest)` | MemberEdits.kt | — | Toggles `isGuest`; no-op if unmatched. |
| `setActive(data, id, active)` | MemberEdits.kt | — | Toggles `active`; no-op if unmatched. |
| `memberReferenced(data, id)` | MemberEdits.kt | n/a (query, not mutator) | `true` if the member appears in any session/payment/refill-contribution/membership. |
| `removeMember(data, id)` | MemberEdits.kt | `该成员已有记录，不能删除` if `memberReferenced` | Removes the member entirely. |
| `addRefill(data, id, date, paidCents, creditCents, contributions)` | LedgerEdits.kt | `实付与到账额度需为正数`; `出资金额需为正数` (any contribution ≤ 0 or blank memberId); `出资合计需等于实付金额` (contributions must sum to exactly `paidCents`) | Appends the `Refill`. |
| `addPayment(data, id, memberId, amountCents, date)` | LedgerEdits.kt | `请选择成员`; `金额需为正数` | Appends the `Payment`. |
| `addSession(data, id, date, hours, rateCents, factor, playerIds)` | LedgerEdits.kt | `小时数需为正数`; `单价需为正数`; `折扣系数需为正数`; `至少选择一名上场成员`; `该周已有记录，请编辑原记录` | Appends the `Session`. |
| `updateSession(data, id, SessionUpdate(date?, hours?, rateCents?, factor?, playerIds?))` | LedgerEdits.kt | `记录不存在`; same field validations as `addSession` but only for fields actually being changed; `目标周已有另一条记录` if `date` moves into an occupied week | Patches only the supplied fields. |
| `deleteSession` / `deleteRefill` / `deletePayment` | LedgerEdits.kt | — | Filters the record out. |
| `addRateChange(data, id, date, rateDollars)` | RateChanges.kt | `单价需为正数`; `日期格式不正确` | Appends to `rates` (append-only — never edited/deleted). |
| `addMembershipFee(data, id, memberId, year, date, amountCents)` | MembershipFees.kt | `请选择成员`; `金额需为正数`; `日期格式不正确`; `年份不正确` (year ≤ 0); `该成员该年度已收取会费` (duplicate `memberId`+`year`) | Appends the `Membership` entry. |
| `deleteMembershipFee(data, id)` | MembershipFees.kt | — | Filters the entry out. |
| `setMembershipFeePaid(data, id, paid, date)` | MembershipFees.kt | `记录不存在`; `日期格式不正确` (only when `paid=true`) | `paid=true` sets `paidDate=date`; `paid=false` clears it. |
| `chargeAnnualMembershipFee(data, ids: List<String>, year, totalAmountDollars, date)` | MembershipFees.kt | `金额需为正数`; `日期格式不正确`; `年份不正确`; `require(ids.size == candidates.size)` (programmer error, not a user-facing rejection) | Bulk-bills. See below. |
| `addTransfer(data, id, fromMemberId, toMemberId, amountCents, date)` | Transfers.kt | `请选择转出成员` (unknown id or guest); `请选择转入成员` (unknown id or guest); `转出转入不能是同一人`; `金额需为正数`; `日期格式不正确`; `转出成员余额不足` (`amountCents` > sender's current `memberBalancesCents`, checked last — the exact balance amount is allowed) | Appends the `Transfer`; moves balance from `fromMemberId` to `toMemberId` with no effect on the sum of all balances or `poolRemainingCents`. |
| `deleteTransfer(data, id)` | Transfers.kt | — | Filters the transfer out. |
| `settleDebtors(data, memberIds, paymentIds, date)` | Settle.kt | `请选择成员` (empty list); `该成员当前无欠款` (a selected member's court balance isn't negative) | Records one full-payoff `Payment` per member, for their exact current owed amount. All-or-nothing — if any member fails validation, none of the payments are applied. |
| `findSessionInWeek(data, dateStr, excludeId=null)` / `weekStart(dateStr)` | Weeks.kt | n/a (queries) | Used by `addSession`/`updateSession` and by the Session screen's "already recorded this week → edit in place" flow. |

### `chargeAnnualMembershipFee` in detail

This is the bulk "收取{year}年会费" action from Settings. `totalAmountDollars` is the **club's total** due for the year (e.g. `$50` split among everyone, not $50 each).

1. Candidate pool = `data.members.filter { !it.isGuest && it.active }` — guests and disabled members are never billed and never appear in `chargedNames`/`skippedNames`.
2. If the pool is empty, returns `MembershipChargeResult(emptyList(), emptyList())` immediately (no divide-by-zero).
3. `totalCents = dollarsToCents(totalAmountDollars)`; `n = pool.size`; `baseCents = totalCents / n` (integer division ≡ floor for non-negative values).
4. Walk the pool in order: a candidate already billed for `year` is added to `skippedNames` and left untouched (their existing `amount` is **not** retroactively rebalanced even if the pool size changes between separate billing runs within the same year — each `Membership` is a snapshot, same philosophy as a `Session`'s snapshotted rate). Otherwise they're billed `baseCents`, except the **last** candidate in the pool, who gets `totalCents − baseCents × (n−1)` (absorbs the rounding remainder, so recorded shares always sum back to the entered total) — added to `chargedNames`.
5. The caller (ViewModel) is responsible for supplying one id per pool member — sized via `membershipStatus(data, year).eligible`, which uses the identical `!isGuest && active` predicate.

Worked example: $50 split 3 ways → `5000/3 = 1666` (floor) for the first two, and the third gets `5000 − 1666×2 = 1668`; i.e. $16.66 / $16.66 / $16.68, summing to exactly $50.00.

## Backup schema & migration (`domain/backup/BackupCodec.kt`)

- `encode`/`encodePretty` always emit the **current** schema (v4); `encodePretty` matches WeChat's `JSON.stringify(d, null, 2)` two-space indent (this is also what the persisted on-device document and the share-sheet export use).
- `validate(text)` fully structurally validates before any write — this is the only path that ever touches an untrusted document (import, and decoding the persisted store on load). It never partially applies a bad document.
- Accepted versions: **1, 2, 3, or 4**; anything else → `备份文件版本不兼容`.
  - v1: `config.defaultRate` required positive (the original flat-rate-only shape, no `rates`/`memberships`/`transfers`).
  - v2: `rates` required non-empty with valid `{id, date, rate}` entries → else `单价历史数据不完整`.
  - v3 (adds, on top of v2's rates requirement): `config.membershipFee` positive; `memberships` array required, each entry `{id, memberId (must reference an existing member id), year (positive integer), date, amount (positive), paidDate? (valid date if present)}` → else `会员年费数据不完整`. `member.active`, if present on any member, must be a boolean.
  - v4 (adds, on top of v3's requirements): `transfers` array required, each entry `{id, fromMemberId (must reference an existing member id), toMemberId (must reference an existing member id), amount (positive), date}` → else `转账数据不完整` (or `备份数据引用了不存在的成员` if either id doesn't match a member).
- **Migration happens at the raw JSON layer, before typed decode** — `migrate = migrateToV4 ∘ migrateToV3 ∘ migrateToV2`. This matters because the typed `LedgerData` model has default values for every v2/v3/v4-only field; decoding a v1 document directly (without migrating first) would silently seed today's defaults instead of preserving what the document actually said (e.g. a v1 document's real `defaultRate: 30` must become `rates: [{date: "2000-01-01", rate: 30}]`, not silently `24`).
  - `migrateToV2`: seeds `rates = [{id: "rate_seed", date: "2000-01-01", rate: <old config.defaultRate>}]`, removes `config.defaultRate`, bumps `version` to 2. No-op if already v2+.
  - `migrateToV3`: adds `memberships: []` and `config.membershipFee: 50`, bumps `version` to 3. No-op if already v3+.
  - `migrateToV4`: adds `transfers: []`, bumps `version` to 4. No-op if already v4.
- Both `decode()` (used after validation, and by the DataStore load path) and the happy-path branch of `validate()` apply this same migration before typed-decoding, so a stored v1/v2/v3 document on disk migrates transparently — through the full chain, straight to v4 — the next time it's loaded — no separate one-time migration step exists or is needed.

## `domain/report/*.kt` — report/poster/history/payment view builders

All of these are pure functions from `LedgerData` (plus a session/date/year parameter) to a plain data class the UI renders — no business logic lives in the Compose screens beyond picker filtering and form state.

- **`Home.kt` → `buildHomeSummary(data, today)`**: rows = members with a nonzero court balance *or* who have ever contributed to a refill (`hasContributed`) — a zero-balance member who never funded anything is hidden. `poolWarn` = pool < `currentRate(data, today) × 4` (i.e. less than one typical 4-hour session's worth of credit left).
- **`Report.kt`**:
  - `balanceRows(data, excludeIds)`: non-zero-balance members, excluding a given id set (used to exclude this week's own players, whose after-balance is already shown in the per-player breakdown).
  - `membershipDebtRows(data)`: mirrors `balanceRows` but sourced from `membershipBalancesCents` — only unpaid, nonzero entries. Never mixed into `balanceRows`.
  - `sessionBreakdownRows(data, session)`: per-player before/share/after for one session (before-balance excludes that session itself).
  - `buildWeeklyPayload(data, sessionId)` / `buildMonthlyPayload(data, ym)`: assemble the full poster payload, each including its own `membershipDebts` field (independent of `balances`/`rows`).
  - Monthly `rows` filter: a member appears only if they played that month **or** currently owe on the court balance — a member with money left but who didn't play, and a zero-balance non-player, are both hidden.
- **`Poster.kt`**: converts a payload into an ordered list of draw-able `PosterLine`s (text/cells/divider, with size/color/gap) — a pure port of the WeChat canvas line layout. Both weekly and monthly posters append a gray "会员年费未付" header + one red "欠 $X" line per debtor (only if `membershipDebts` is non-empty), placed after the balance section and right before the final "球馆额度剩余" line.
- **`PosterLayout.kt`**: turns the line list into absolute pixel positions (`PosterText`/`PosterRect` at a fixed 750px canvas width) — this is what `app/poster/PosterRenderer.kt` actually draws to a `Bitmap` via Compose, then `PosterShare.kt` writes to cache and opens the system share sheet as a PNG.
- **`Recording.kt`**: `buildSessionPreview` (live cost preview while filling in the Session form — null until every input is valid); `refillFactorText` (4-decimal `paid/credit`, or `—` while invalid); `buildPaymentSummary(data)` → `debtors` (court-fee, chip-selectable), `rows` (every member, reference-only balance list, unfiltered by `active`), and `membershipDebtors` (independent chip-selectable list from `membershipBalancesCents`).
- **`History.kt`**: `buildHistoryRows(data, cutoff)` — `sessions` are cut off at 12 months back and sorted newest-first; `refills`/`payments`/`memberships`/`transfers` are **not** cutoff-filtered (unbounded history), all sorted newest-first. Each `memberships` row's description includes a `（已付）`/`（未付）` tag from `paidDate`. Each `TransferHistoryRow`'s `desc` reads `<from name> → <to name> $<amount>`.
- **`ReportOptions.kt`**: `reportOptions(data)` — the Report screen's week/month picker lists, newest-first.
- **`Format.kt`**: `rawDollars`/`rawNumber` print numbers the way the WeChat JS did (trim trailing zeros: `24`, `24.9`, not `24.00`/`24.90`) — used only in poster/history descriptive text, not in balance amounts (which always show 2 decimals via `centsToDollars`).

## App layer (`app/`)

### Navigation (`ui/AppNav.kt`)

Single-activity, Compose Navigation, routes: `home` (start) → `settings`, `session?editId={editId}` (editId null for a new/current-week record), `refill`, `payment`, `transfer`, `report?sessionId={sessionId}`, `history`. Saving a session navigates to `report?sessionId=<id>` with `popUpTo("home")` so the back stack returns straight to Home. History's session rows navigate to `session?editId=<id>` for editing. Home's action-button `FlowRow` includes a 转账 button (`onOpenTransfer`) alongside 记录本周/充值/收款/报告/历史, navigating to `transfer`.

### Persistence (`storage/LedgerStore.kt`)

Single-document Preferences DataStore (`badminton_ledger`, key `badminton_data_v1`) storing the whole `LedgerData` as one JSON string in the exact same format as backup export/import (`BackupCodec.encode`/`decode`). A missing key, unreadable file, or decode failure all fall back to a fresh default `LedgerData()` rather than crashing — there is currently no user-visible warning when this happens (a decode failure silently resets to empty; this is a known gap, not a designed recovery path). `LedgerViewModel` treats the in-memory `StateFlow<LedgerData?>` as authoritative and writes to the store write-behind (fire-and-forget `viewModelScope.launch`), so back-to-back mutations always see the latest state even if the previous disk write hasn't finished.

### Backup import/export (`ui/SettingsScreen.kt`, `LedgerViewModel.loadBackup`/`applyImport`, `backup/BackupExport.kt`)

- **Export**: `shareBackup` writes `BackupCodec.encodePretty(data)` to a cache file named via `BackupCodec.exportFileName(dateStr)` and opens the system share sheet as `application/json`.
- **Import**: Settings' `OpenDocument` picker → `vm.loadBackup(uri)` reads the file off the main thread, runs `BackupCodec.validate` (never `decode` directly — validation is what performs the migration and referential checks), and returns `BackupLoad.Ready(data, summary)` / `.Invalid(reason)` / `.CouldNotRead`. On `Ready`, Settings shows a confirm dialog ("备份包含 N 位成员...导入将覆盖全部现有数据") before calling `vm.applyImport(data)`, which fully replaces the current document — no merge, no partial import.

### Picker filtering (lives in the Compose screens, not domain — mirrors WeChat's page-level filters)

- **Session screen** player chips: `current.members.filter { it.active || selected[it.id] == true }` — active members, plus anyone already selected (so editing an existing session never silently drops a since-disabled player).
- **Refill screen** funder rows: `current.members.filter { !it.isGuest && it.active }`.
- **Payment screen**: not filtered by `active` at all — it's the mechanism for settling existing debt (court-fee or membership), and a disabled member who still owes money must stay fully payable there and on Home.

### Settings screen card order

成员管理 (member list, rename/补位/启用 switches/delete) → **会员年费** (status line + total-amount input + 收取{year}年会费 button + explanatory note) → 球馆单价 (rate history + add-new-rate form) → 默认充值参数 (defaultPaid/defaultCredit) → 数据备份 (import/export/清空全部数据).

- The 会员年费 card's status line reads `calc.membershipStatus(current, currentYear)`. The charge button calls `vm.chargeAnnualMembershipFee(year, amountEntered, today)`, which persists `config.membershipFee = amountEntered` on success (so next time defaults to what was actually last charged) and shows a toast built from the returned `chargedCount`/`skippedCount`/`perPersonDollars` — `本年度会费已全部开单` if nothing new was billed, else `已开单 N 人，每人约 $X`（`，跳过 M 人（已开单）` appended if applicable).
- 清空全部数据 resets to `LedgerData()` after a confirm dialog — irreversible, no undo (the dialog text says so and recommends exporting first).

### Payment screen sections (independent of each other)

1. Date picker (shared by both sections below).
2. 谁交钱了？ — court-fee debtor chips → `vm.settleDebtors` (each selected member's *entire* current court debt is paid off in one `Payment`).
3. 谁交年费了？（only shown if there's at least one membership debtor) — membership debtor chips → `vm.settleMembershipDebtors`, which marks **every** unpaid `Membership` entry belonging to each selected member as paid on the chosen date (in practice a member usually has at most one unpaid entry, but this handles multiple outstanding years correctly).
4. 当前余额（参考） — every member's court balance, reference-only, unaffected by either save action above.

### 转账 screen

`TransferScreen` records an already-settled-offline balance transfer between two non-guest members: both the 转出成员 and 转入成员 dropdowns list `current.members.filter { !it.isGuest }` — unlike the Session/Refill pickers this is **not** filtered by `active`, since a disabled member can still hold a balance worth transferring. Picking a 转出成员 prefills 金额 to that member's current `memberBalancesCents` value (floored at 0, via a `LaunchedEffect(fromMember)`) and shows a "最多可转 $X" cap hint beneath the field; changing the sender re-prefills the amount and re-computes the cap, but the field itself stays freely editable afterward. The 转入成员 dropdown excludes whichever member is currently selected as 转出成员 (and clears an already-picked receiver if it now matches the newly chosen sender) — the domain's own `转出转入不能是同一人` check is a backstop, not the primary defense. Save is enabled once both members are picked; `vm.addTransfer` runs the domain validation and either shows the returned rejection as a snackbar or pops back to Home on success.

### History screen sections

周记录 (last 12 months, tap → edit/delete menu) → 充值记录 (unbounded, tap → confirm-delete) → 收款记录 (unbounded, tap → confirm-delete) → 会员年费记录 (unbounded, tap → confirm-delete, shows 已付/未付 tag) → 转账记录 (unbounded, tap → confirm-delete; deleting one live-recomputes both members' balances, since `memberBalancesCents` is always derived fresh from the whole document).

## Testing

`domain/` has 104 JVM unit tests (`gradlew.bat :domain:test`), covering every function/rule above case-by-case — that test suite is the executable spec; when this document and the tests ever disagree, the tests (and the code they exercise) win and this document is wrong and needs fixing. `RealBackupTest` additionally round-trips a real exported backup file when one is present at `backups/real-backup.json` (gitignored, not committed) — skipped otherwise.
