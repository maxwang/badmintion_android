# Design: 会员年费 (Annual Membership Fee) & Member Disable — Android Port + Backup Schema v3

**Date:** 2026-07-19
**Status:** Approved (mirrors the WeChat design `E:\Code\ai\wechat\badminton\docs\superpowers\specs\2026-07-18-membership-fee-and-member-disable-design.md`, revised 2026-07-19 after WeChat live testing settled the even-split behavior)

## Problem

The WeChat mini program added a club-wide annual membership fee ($50/year total, split evenly across current paying members) and a member soft-disable flag, moving its backup schema to **v3**. The Android app still implements v2 (court-rate history only); a fresh WeChat v3 export is rejected (备份文件版本不兼容), breaking the WeChat→Android round trip, and Android has no equivalent feature at all — no membership billing, no disable switch, no membership section in reports/History/Payment.

## Decision

Port the feature 1:1 and unfreeze the contract to v3. This document supersedes the v2 schema section of `2026-07-15-court-rate-history-android.md`.

### Data contract (Backup JSON v3)

```json
{
  "version": 3,
  "members":  [{ "id": "m_...", "name": "Max", "isGuest": false, "active": true }],
  "config":   { "defaultPaid": 2000, "defaultCredit": 2500, "membershipFee": 50 },
  "rates":    [...unchanged...],
  "refills":  [...unchanged...], "payments": [...unchanged...], "sessions": [...unchanged...],
  "memberships": [{ "id": "mf_...", "memberId": "m_...", "year": 2026, "date": "YYYY-MM-DD",
                     "amount": 25, "paidDate": "YYYY-MM-DD" }]
}
```

- `member.active` — optional boolean; absent or `true` means active, `false` means disabled. Kotlin model defaults `active = true` so absence and presence-as-true are indistinguishable (matches WeChat's `active !== false` check everywhere).
- `config.membershipFee` (default `50`) — the prefilled **total** club due for the bulk-charge action, not a per-member amount.
- `memberships` is top-level, one entry per member per year billed. `amount` is that member's own computed share (not the club total). `paidDate` absent = billed-but-unpaid.
- Import accepts **v1, v2, or v3**. v1→v2 migration is unchanged (seed `rates`, drop `config.defaultRate`). v2→v3 migration: `memberships = []`, `config.membershipFee = 50`. `version ∉ {1,2,3}` → 备份文件版本不兼容. v3 requires `config.membershipFee` positive and a valid `memberships` array (each entry: non-empty `id`, `memberId` referencing an existing member, positive integer `year`, valid `date`, positive `amount`, and if present a valid `paidDate`); `member.active`, if present, must be boolean.
- Export always writes v3. WeChat's current build accepts v1/v2/v3 both directions → round trip restored.
- **Migration is chained at the JSON layer** (`BackupCodec`), same reasoning as the v2 port: the typed Kotlin model defaults `memberships`/`membershipFee` silently, so a v1 or v2 document must be migrated to v3 *before* typed decode, or an old document's actual (nonexistent) membership history would be indistinguishable from "feature didn't exist yet" — which happens to be the correct outcome here, but the migration must still be explicit and tested, matching the v1→v2 precedent.

### Domain

- Model: `Membership(id, memberId, year: Int, date, amount: Cents, paidDate: String? = null)`; `Member` gains `active: Boolean = true`; `Config` gains `membershipFee: Cents = Cents(5000)`; `LedgerData.version` default `3`; `LedgerData.memberships` default `emptyList()`.
- `calc.membershipBalancesCents(data): Map<String, Long>` — completely separate from `memberBalancesCents`: for every `memberships` entry with `paidDate == null`, subtracts `amount`; paid entries contribute nothing. `memberBalancesCents` is untouched and never reads `memberships`.
- `calc.membershipStatus(data, year): MembershipStatus(eligible, charged, paid)` — `eligible` = `!isGuest && active` members; `charged` = how many have a `memberships` entry for `year`; `paid` = how many of those are also paid.
- `edit.setActive(data, id, active): LedgerData` — mirrors `setGuest`.
- `edit.memberReferenced` gains a `memberships.any { it.memberId == id }` check (blocks hard-delete).
- `edit.addMembershipFee(data, id, memberId, year, date, amountCents): EditResult<Membership>` — rejects blank member, non-positive amount, bad date, non-positive year, and a duplicate `(memberId, year)` pair (该成员该年度已收取会费).
- `edit.chargeAnnualMembershipFee(data, ids, year, totalAmountDollars, date): EditResult<MembershipChargeResult>` — `ids.size` must equal the eligible candidate pool size (`!isGuest && active`, computed the same way as `membershipStatus`'s `eligible`); splits the total evenly with the last candidate absorbing the rounding remainder (identical rule to `calc.sessionShares`); already-billed-for-`year` candidates are skipped (their existing `amount` is untouched); an empty pool returns empty results without dividing by zero.
- `edit.setMembershipFeePaid(data, id, paid, date): EditResult<Unit>` — `paid = true` requires a valid `date`; `paid = false` clears `paidDate`; unknown `id` → 记录不存在.
- `edit.deleteMembershipFee(data, id): LedgerData` — mirrors `deleteRefill`/`deletePayment`.
- `report.membershipDebtRows(data): List<MembershipDebtRow>` — mirrors `balanceRows` but sourced from `membershipBalancesCents`; only nonzero (unpaid) entries.
- `WeeklyPayload`/`MonthlyPayload` each gain `membershipDebts: List<MembershipDebtRow>`.
- `report.buildPaymentSummary` gains a `membershipDebtors: List<MembershipDebtorRow>` field (id, name, owedDollars), independent of the existing court-fee `debtors`.
- `report.buildHistoryRows` gains a `memberships: List<MembershipHistoryRow>` field (id, date, desc), sorted newest-first like refills/payments (no 12-month cutoff).

### App

- Settings: member rows gain a second switch "启用" (default checked) next to "补位"; a new **会员年费** card sits directly after 成员管理 (before 球馆单价) with a status line ("{year}年度：共 N 名正式成员，已开单 X，已付清 Y"), a total-amount input (prefilled from `config.membershipFee`, labeled as a total to split, not per-person), and a "收取{year}年会费" button that charges, persists the entered total back into `config.membershipFee`, and toasts a summary including the computed per-person share.
- Session screen: the player chip list excludes disabled members *unless* they're already selected (editing an existing session keeps a since-disabled player visible) — new sessions simply can't add a disabled member.
- Refill screen: the funder list additionally excludes disabled members (already excludes guests).
- Payment screen: gains a second, independent "谁交年费了？" chip section (from `membershipDebtors`) with its own selection state and its own "保存年费收款" button; settling here only calls `setMembershipFeePaid`, never touches `payments[]` or the court-fee balance. The existing "当前余额（参考）" list is unaffected and unfiltered by `active` (a disabled member with a balance still shows and remains payable there, and on Home).
- History screen: gains a "会员年费记录" section (date · name · year · amount · 已付/未付), tap → confirm-delete → `deleteMembershipFee`.
- Report/poster: weekly and monthly posters gain a gray "会员年费未付" header + one red debtor line per unpaid member, placed after the existing balance rows and before "球馆额度剩余" — visually identical styling to an owed court-fee row but a clearly separate section.
- `versionName`/`versionCode` bump (schema v3 release).

## Out of scope (as in the WeChat spec)

Prorating/auto-billing mid-year joiners; retroactively rebalancing already-billed members if the pool size changes between runs within a year; custom per-member override of the even split; date-historized membership fee; per-member paid/unpaid indicator inline in the member list; partial membership payments; archiving/hiding disabled members from Settings.

## Testing

Port today's WeChat test additions case-for-case:
- `tests/data.test.js` — default structure (`version: 3`, `config.membershipFee: 50`, `memberships: []`); `addMembershipFee` validation + duplicate-year rejection; `deleteMembershipFee`; hard-delete now blocked by a `memberships` reference; `chargeAnnualMembershipFee` even-split (2-member $50→$25 each), remainder-absorption (3-member $50→$16.66/$16.66/$16.68), skip-guest/skip-disabled/skip-already-billed, validation, empty-pool no-op; `setMembershipFeePaid` toggle + bad-date + unknown-id; v1→v3 and v2→v3 migration; v3 `validateImport` (missing/broken `memberships`, missing `config.membershipFee`, bad `paidDate`, bad `member.active`); `setActive` toggle.
- `tests/calc.test.js` — `memberBalancesCents` unaffected by `memberships` (regression); `membershipBalancesCents` unpaid-vs-paid; `membershipStatus` eligible/charged/paid counts with a guest+disabled+partially-paid mix.
- `tests/report.test.js` — membership debt absent from `balances`/`rows`, present in `membershipDebts`; `membershipDebtRows` excludes paid entries.
- Android-specific guards (beyond the JS parity set): decoding a v1 or v2 document with real history must migrate through the full v1→v2→v3 chain in one `decode()` call; `RealBackupTest` keeps working against whichever version the real export currently is; encode always emits v3 and is accepted by WeChat's current `validateImport`.
