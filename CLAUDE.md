# Badminton Ledger — Android (Kotlin + Compose, KMP-ready)

Native Android rebuild of the badminton cost-sharing WeChat mini program. Owner: Max. Daily-use app + Kotlin/Compose learning project. iOS may come later via KMP.

## Source of truth

- Product behavior: the WeChat mini program at `E:\Code\ai\wechat\badminton` (repo `badmintion_wechat`, branch `develop`). Its Node test suites (`tests/calc.test.js`, `tests/data.test.js`, `tests/report.test.js`) are the authoritative domain spec — when porting or in doubt, read them; do not rely on memory.
- Architecture and milestones: `docs/superpowers/specs/2026-07-11-android-app-design.md`. Follow the milestone order; each milestone gets its own plan in `docs/superpowers/plans/`.

## Hard rules

- `domain/` is pure Kotlin: no `android.*` imports, ever. All Android code lives in `app/`.
- All money is integer cents inside `domain/`; dollars only at the edges (JSON contract, UI formatting).
- The backup JSON schema (`version: 1`) is a frozen contract shared with the WeChat app — changes require a spec update first.
- TDD: port/write the failing test first. Parity tests must keep the same fixtures and expected values as the WeChat repo's tests.
- One `LedgerData` document is the single source of truth; derived values (balances, pool) are always recomputed from it, never cached incrementally.
- Never commit real group backup JSON files (gitignored under `backups/`).

## Commands

- Build: `gradlew.bat assembleDebug`
- All tests: `gradlew.bat test`
- Domain tests only: `gradlew.bat :domain:test`
- Lint: `gradlew.bat ktlintCheck detekt`

## Conventions

- Branch: `main` + short-lived feature branches. Conventional commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`).
- Specs in `docs/superpowers/specs/`, plans in `docs/superpowers/plans/`, named `YYYY-MM-DD-<topic>.md`.
- UI copy is English (the group is in Australia); money formatted as `$X.XX`.
