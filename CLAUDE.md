# Badminton Ledger — Android (Kotlin + Compose, KMP-ready)

Native Android rebuild of the badminton cost-sharing WeChat mini program. Owner: Max. Daily-use app + Kotlin/Compose learning project. iOS may come later via KMP.

## Source of truth

- **Product behavior (current state): `docs/domain-reference.md`.** This is the self-contained, up-to-date description of every domain rule, mutator, calculation, backup schema detail, and app-layer behavior. It is maintained in lockstep with the code — when behavior changes, update the relevant section of that doc in the same change. Do not go back to the WeChat mini program (`E:\Code\ai\wechat\badminton`) to understand behavior; it was this app's origin but is no longer authoritative and may have diverged. If `docs/domain-reference.md` and the code ever disagree, the code (and its tests) win, and the doc is stale and needs fixing — not the other way around.
- Architecture and milestones (historical): `docs/superpowers/specs/2026-07-11-android-app-design.md` and the other dated specs in `docs/superpowers/specs/`. These record the *why* behind each milestone's design decisions at the time; `docs/domain-reference.md` records the current *what*. Follow the milestone order for new work; each milestone gets its own plan in `docs/superpowers/plans/`.

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
- UI copy is Chinese, taken verbatim from the WeChat mini program wherever a counterpart string exists (error reasons, poster lines, labels); money formatted as `$X.XX`.
