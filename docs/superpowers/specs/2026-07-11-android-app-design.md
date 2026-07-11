# Design: Badminton Ledger — Android App (WeChat Mini Program Conversion)

**Date:** 2026-07-11
**Status:** Approved (brainstormed in the WeChat repo session; this repo is the implementation home)

## Goal

Rebuild the badminton cost-sharing WeChat mini program (`badmintion_wechat`, `E:\Code\ai\wechat\badminton`) as a native Android app, as (1) a daily-driver app on the owner's Android phone and (2) a Kotlin/Compose learning + portfolio project. iOS may follow later; the architecture must keep that path cheap.

## Decision

**Kotlin + Jetpack Compose, structured KMP-ready.** Native Android app; all money/domain logic lives in a pure-Kotlin module with zero Android dependencies so it can become a Kotlin Multiplatform shared module when iOS arrives. Rejected alternatives: React Native (reuses existing JS utils but doesn't teach native Android), Flutter (teaches Dart, reuses nothing, weak iOS-later story vs KMP).

The WeChat mini program stays alive and untouched during the conversion. Data moves between the two apps via the existing backup JSON contract.

## AI-First Engineering Rules (how this repo is built)

This project is built primarily by AI agents under human direction. Consequences:

- **Explicit boundaries:** `domain/` is pure Kotlin (no `android.*` imports, enforced by module dependencies). `app/` contains all Android specifics. An agent can hold either module fully in context.
- **Stable contracts:** the backup JSON schema (below) and the domain API are the contracts. UI and storage may change freely; contracts change only via a spec update.
- **Deterministic tests as acceptance criteria:** every milestone lands with tests that pass headlessly (`gradlew test`). The WeChat repo's Node test suites (`tests/calc.test.js`, `data.test.js`, `report.test.js`) are the authoritative behavior spec — port them case-for-case with identical fixtures and expected values (parity tests). Behavior questions are settled by reading the WeChat repo, not by memory.
- **Review focus:** behavior regressions, data integrity (money!), failure handling. Style is delegated to ktlint/detekt automation.

## Module Architecture

```
badminton-ledger/
  settings.gradle.kts
  gradle/libs.versions.toml     # version catalog
  domain/                       # pure Kotlin (kotlin-jvm now, KMP-ready)
    model/       Member, Session, Refill, Payment, Config, LedgerData (kotlinx.serialization)
    calc/        money math: cents, shares, balances, pool     (port of utils/calc.js)
    report/      weekly/monthly poster payload builders        (port of utils/report.js)
    edit/        mutations + invariant validation              (port of utils/data.js)
    backup/      JSON export/import + validateImport           (port of backup schema v1)
  app/                          # Android
    ui/          Compose screens: Home, Session, Refill, Payment, History, Report, Settings
    storage/     DataStore: single JSON document via kotlinx.serialization
    poster/      Compose layout → bitmap capture
    share/       Android share sheet (image + backup file), SAF import
```

- `app` depends on `domain`; never the reverse.
- State model mirrors the mini program: one `LedgerData` document is the single source of truth; every screen recomputes derived values (balances, pool) from the full document. No incremental caches.

## Data Contract: Backup JSON v1

Identical to the WeChat app's export (`utils/data.js` DEFAULT_DATA shape + `validateImport` rules). This is the migration bridge: export from WeChat → import into Android on day one, and back.

```json
{
  "version": 1,
  "members":  [{ "id": "m_...", "name": "Max", "isGuest": false }],
  "config":   { "defaultRate": 24, "defaultPaid": 2000, "defaultCredit": 2500 },
  "refills":  [{ "id": "r_...", "date": "YYYY-MM-DD", "paid": 2000, "credit": 2500,
                 "contributions": [{ "memberId": "m_...", "amount": 600 }] }],
  "payments": [{ "id": "p_...", "memberId": "m_...", "amount": 25.6, "date": "YYYY-MM-DD" }],
  "sessions": [{ "id": "s_...", "date": "YYYY-MM-DD", "hours": 4, "rate": 24,
                 "factor": 0.8, "playerIds": ["m_..."] }]
}
```

Validation on import (port of `validateImport`): structural checks, positive amounts, `YYYY-MM-DD` dates, unique member ids, referential integrity of every `memberId`/`playerIds`; reject `version != 1`; no partial writes — validate fully, confirm with the user, then replace.

## Domain Invariants (headline rules; the ported tests are the full spec)

- All money is integer **cents** internally; dollars only at the edges (amounts in the JSON contract are dollars, matching the WeChat app).
- Session face cost = hours × rate; real cost = face × factor. Even split among players; **rounding remainder is absorbed by the last player** so the sum is exact.
- Member balance = refill contributions + cash payments − session shares. Venue pool = refill credits − session **face** costs.
- Week starts **Monday**; at most one session per week.
- Members with any records cannot be deleted.
- Display filters (match current WeChat behavior): home hides zero-balance members who never contributed to a refill; weekly report balance section lists only non-players with non-zero balance; monthly report lists only that month's players and current debtors.

## Milestones (each independently shippable, each its own plan)

1. **Skeleton + domain port.** Gradle multi-module project builds; `domain/` fully ported with parity tests green; CLI-less proof: import the real WeChat backup JSON in a test and assert known balances. *Acceptance: `gradlew test` green, includes real-backup fixture test.*
2. **Storage + Home + Settings.** DataStore persistence, Home screen (balances, pool, warnings), Settings (members CRUD, defaults), backup **import** via Android file picker. *Acceptance: app installs on phone; real data imported and visible.*
3. **Recording flows.** Session (record/edit week), Refill, Payment screens with the same validation and edit-in-place-for-current-week behavior. *Acceptance: a full week's cycle done on the phone matches WeChat app numbers.*
4. **Reports + poster + share.** Weekly/monthly report screens; poster rendered as a Compose layout captured to bitmap; share via system share sheet. *Acceptance: poster shared into WeChat group chat is visually complete and numerically identical to the WeChat app's.*
5. **Export + polish.** Backup export (share sheet / save file), history screen, app icon, release-signed APK on the phone. *Acceptance: round-trip export→import between Android and WeChat apps preserves data exactly.*

## Tech Stack (pins at project creation)

- Kotlin 2.x, JDK 17, Gradle with version catalog; Android Studio current stable.
- Jetpack Compose (BOM-managed), Material 3, single-activity, Navigation Compose.
- `kotlinx.serialization` for the JSON contract; DataStore (Preferences or file-based) storing the serialized `LedgerData` document.
- Min SDK 26, target latest stable.
- Tests: JUnit/kotlin-test in `domain/` (pure JVM, fast); ktlint + detekt for automated style.
- No DI framework, no Room, no network — YAGNI until a milestone needs it.

## Out of Scope (for now)

- iOS target (architecture-ready, not built), cloud sync/backend, multi-user editing, Google Play publishing, migrating git history from the WeChat repo.
