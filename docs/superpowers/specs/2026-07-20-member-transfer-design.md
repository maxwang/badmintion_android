# Design: 转账 (Member Transfer) — Backup Schema v4

**Date:** 2026-07-20
**Status:** Approved

## Problem

Two members sometimes settle a debt directly with each other outside the app (cash, bank transfer, whatever) instead of through the club's normal refill/payment flow — e.g. one member covers what another owes them personally. Today there's no way to record that: the only mutators that touch a member's court-fee balance are refill contributions, cash payments, and session shares, none of which model money moving between two members' own balances. The admin (Max) wants to record these peer-to-peer settlements himself, from the app, once the two members have agreed offline, so the balances he monitors on Home/History stay accurate.

This is a court-fee-balance concept only — membership fees are individual annual dues billed to one specific person, so there's nothing to hand off between members there.

## Decision

### Data model

A new top-level array, parallel to `refills`/`payments`/`memberships`:

```
transfers: [ { id, fromMemberId, toMemberId, amount, date } ]
```

- `fromMemberId` / `toMemberId` — must reference two distinct existing members, and both must be **non-guest** (`isGuest == false`). Guests (补位) can never send or receive a transfer. A disabled (`active == false`) formal member remains eligible in either direction — disabling only affects being added to *new* sessions/refills, never existing-balance actions (same rule Payment already follows).
- `amount` (yuan, positive) — same numeric convention as `refill.paid`/`payment.amount`/`membership.amount`.
- `date` (`YYYY-MM-DD`) — when the transfer was recorded.

### Balance calculation

`memberBalancesCents` gains one more term, alongside the existing refill/payment/session terms it already sums:

```
balance[m] = Σ refill contributions + Σ cash payments − Σ session shares
             + Σ transfers received by m − Σ transfers sent by m
```

This preserves ledger conservation automatically: every cent debited from a sender's balance is credited to a receiver's balance, so a transfer never changes the sum of all member balances, and never touches `poolRemainingCents` (no real money enters or leaves the club). This is the same independence principle the membership-fee ledger already follows, just within the court-fee ledger itself.

### New pure functions (`domain/`)

- `addTransfer(data, id, fromMemberId, toMemberId, amountCents, date): EditResult<Transfer>` — validates, in order:
  1. `请选择转出成员` — `fromMemberId` blank, doesn't reference an existing member, or references a guest
  2. `请选择转入成员` — same checks for `toMemberId`
  3. `转出转入不能是同一人` — `fromMemberId == toMemberId`
  4. `金额需为正数` — `amountCents` null or ≤ 0
  5. `日期格式不正确` — malformed date
  6. `转出成员余额不足` — `amountCents` exceeds the sender's **current** court-fee balance, computed via `memberBalancesCents(data)[fromMemberId]` (a zero or negative balance means any transfer amount is rejected)

  On success, appends `Transfer(id, fromMemberId, toMemberId, Cents(amountCents), date)`.
- `deleteTransfer(data, id)` — mirrors `deleteRefill`/`deletePayment`/`deleteMembershipFee`: filters the entry out. Balances recompute automatically; nothing is cached.

### Migration

- `version` bumps to 4.
- `validateImport`/`BackupCodec.validate`: accepts versions 1–4. v4 additionally requires `transfers` to be an array where every entry has a non-empty `id`, `fromMemberId`/`toMemberId` each referencing an existing member id, positive `amount`, and a valid `YYYY-MM-DD` `date` — else `转账数据不完整`. (Whether the referenced members are guests is a domain-level eligibility rule enforced by `addTransfer`, not re-validated on import — an imported document is trusted structurally, same as how existing membership/session records aren't re-checked against `active`/`isGuest` on import today.)
- Migrating a v1/v2/v3 document to v4 just adds `transfers: []` — nobody has been transferred under old data, which is correct (mirrors how v2→v3 added an empty `memberships: []`).
- Export always emits v4.

### App layer

New **转账** screen, following the same shape as Refill/Payment:

- 转出成员 picker — non-guest members only. Selecting one live-computes their current court-fee balance and **prefills the amount field with that balance** (still editable down), with helper text showing the cap (e.g. "最多可转 $X").
- 转入成员 picker — same member list, excluding whichever member is currently selected as 转出成员.
- Date picker, defaulting to today.
- 保存转账 button → `vm.addTransfer(...)`; a rejection reason surfaces as a snackbar, same pattern as every other recording screen.

**History** gains a 转账记录 section (unbounded history, like 充值/收款/会员年费): `{date} · {fromName} → {toName} ${amount}`, tap → confirm-delete → `deleteTransfer`.

**Home screen**: no changes — a transfer's effect is already visible through the existing balance numbers there.

**Reports/posters**: no changes — transfers are not called out as their own section (unlike membership debt); their effect is already reflected in the balance numbers the posters already show.

## Out of scope

- Transfers involving guests, in either direction — guests never carry a "real" balance in the sense this feature cares about (their balance today only ever comes from a refill contribution or a cash payment settling a session debt).
- Transfers against the membership-fee ledger — membership dues are individual, non-transferable.
- Editing a transfer after creation — delete and re-add, matching every other record type's pattern (no dedicated edit UI exists for refills/payments/memberships either).
- Partial/scheduled/recurring transfers — one manual entry per real-world settlement event.
- Any balance-check override (e.g. an admin "force" option to transfer beyond the sender's balance) — `转出成员余额不足` is a hard rejection, no bypass.

## Testing & Verification

- Domain unit tests (mirroring the style already used for `chargeAnnualMembershipFee`/`settleDebtors`):
  - `addTransfer` — rejects blank/missing/guest `fromMemberId`/`toMemberId`, same-member transfer, non-positive amount, bad date, and an amount exceeding the sender's current balance; succeeds otherwise and the sender's balance decreases by exactly the same amount the receiver's increases.
  - `memberBalancesCents` — a transfer changes exactly the two members involved and leaves every other member's balance and the sum of all balances unchanged; `poolRemainingCents` is unaffected by any transfer.
  - `deleteTransfer` — removes the entry; balances revert as if it never happened.
  - Migration — a v1/v2/v3 blob with no `transfers` loads into `transfers: []`; `validateImport` accepts v1–v3 (legacy, no `transfers`) and v4 (requires valid `transfers`, rejects a reference to a nonexistent member).
- `HistoryTest` — transfers listed newest-first, unfiltered by any cutoff, with the correct `{from} → {to}` description.
- Manual (on-device): 转账 screen prefills the amount from the selected 转出成员's balance; saving updates both members' balances correctly on Home; History lists and can delete a transfer with balances recomputing live; attempting to select the same member for both sides is prevented or rejected with the correct message; a guest never appears in either picker.
