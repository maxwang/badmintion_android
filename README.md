# Badminton Ledger

Native Android app (Kotlin + Jetpack Compose) for tracking a badminton group's shared costs: venue credit pool, weekly session splits, member balances, cash settlements, and shareable settlement posters.

A ground-up rebuild of a WeChat mini program, structured KMP-ready (pure-Kotlin `domain/` module) so an iOS target can be added later. Data migrates between the two apps via a shared backup JSON contract.

- Design: [docs/superpowers/specs/2026-07-11-android-app-design.md](docs/superpowers/specs/2026-07-11-android-app-design.md)
- Status: pre-code — Milestone 1 (project skeleton + domain port) is next.

## Modules

- `domain/` — pure Kotlin: money math, report builders, mutations/validation, backup schema. No Android dependencies.
- `app/` — Android: Compose UI, DataStore persistence, poster rendering, share/import.
