# Design: 球馆单价历史 (Court Rate History) — Android Port + Backup Schema v2

**Date:** 2026-07-15
**Status:** Approved (mirrors the WeChat design `E:\Code\ai\wechat\badminton\docs\superpowers\specs\2026-07-15-court-rate-history-design.md`, adopted by WeChat v1.1.0 the same day)

## Problem

The WeChat mini program moved to backup schema **v2** today: the court hourly rate became a dated, append-only history (`rates[]`), `config.defaultRate` was removed, and `validateImport` accepts v1 or v2 with in-place migration. The Android app still implements the (previously frozen) v1 contract, so a fresh WeChat export is rejected (备份文件版本不兼容) — the cross-app round trip is broken in the WeChat→Android direction, and the two apps have diverged on the settings UI and rate-prefill behavior.

## Decision

Port the feature 1:1 and **unfreeze the contract to v2**. This document supersedes the v1 schema section of `2026-07-11-android-app-design.md`.

### Data contract (Backup JSON v2)

```json
{
  "version": 2,
  "members":  [...unchanged...],
  "config":   { "defaultPaid": 2000, "defaultCredit": 2500 },
  "rates":    [{ "id": "rate_...", "date": "YYYY-MM-DD", "rate": 24 }],
  "refills":  [...], "payments": [...], "sessions": [...]
}
```

- `rates` is top-level, append-only (no edit/delete — sessions snapshot their own rate, so history is only a prefill source), guaranteed non-empty (seeded on creation/migration).
- Import accepts **v1 or v2**. v1 migrates: seed `rates = [{id: "rate_seed", date: "2000-01-01", rate: config.defaultRate}]`, drop `config.defaultRate`, version → 2. `version != 1 && != 2` → 备份文件版本不兼容. v2 requires non-empty `rates` with valid entries, else 单价历史数据不完整.
- Export always writes v2. WeChat 1.1.0 accepts both directions → round trip restored.
- **Migration point is the JSON layer** (`BackupCodec`), BEFORE typed decode: the Kotlin model has no `defaultRate` field and defaults `rates` when the key is absent — decoding a v1 document directly would silently seed the default $24 instead of the document's actual rate. `BackupCodec.decode` therefore migrates the parsed `JsonElement` first; the persisted DataStore document (which flows through `decode`) migrates on first load for free.

### Domain

- Model: `RateChange(id: String, date: String, rate: Cents)`; `LedgerData.version` default 2; `LedgerData.rates` default `[RateChange("rate_seed", "2000-01-01", Cents(2400))]`; `Config(defaultPaid, defaultCredit)`.
- `calc.currentRate(data, dateStr): Cents` — latest `rates` entry with `date <= dateStr`; if none, the chronologically earliest entry (ties keep the first, like the JS reduce).
- `edit.addRateChange(data, id, date, rateDollars): EditResult<RateChange>` — rejects non-positive rate (单价需为正数) and malformed date (日期格式不正确).
- `report.buildHomeSummary(data, today)` — pool-warning threshold becomes `currentRate(data, today).value * 4`.

### App

- Session screen: new-record rate prefills from `currentRate(data, today)`; changing the date on a NEW record re-derives the rate for that date; editing an existing record never touches its stored rate.
- Settings split: 成员管理 / **球馆单价** card (生效日期 picker defaulting to today, 单价（$/小时） input, 记录价格变更 button → toast 已记录; read-only history newest-first as `{date} 起  $X/小时`) / **默认充值参数** card (充值实付/充值到账额度 only) / 数据备份 card gains **清空全部数据** (confirm: 将清空全部成员、记录与设置，且无法恢复，建议先导出备份。确定清空？ → resets to the default document, toast 已清空).
- `versionName` bumps to `0.2`.

## Out of scope (as in the WeChat spec)

Retroactive rate correction / adjustment ledger; history UI for refill defaults; changing `currentFactor`'s latest-refill behavior.

## Testing

Port today's WeChat test additions case-for-case (`tests/calc.test.js` currentRate block; `tests/data.test.js` v2 default structure, addRateChange, migrate, v2 validateImport block, version-3 rejection) plus Android-specific guards: decoding a v1 document with a NON-default rate must migrate that rate (the serialization-default pitfall), and encode must emit v2 that WeChat's shape rules accept. `RealBackupTest` keeps working for either version of the real export.
