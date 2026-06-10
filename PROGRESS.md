# PROGRESS.md — Build ledger

> Updated every iteration. The session-recovery entry point alongside
> `CLAUDE.md`, `DECISION-LOG.md`, `FOUNDER-FEEDBACK.md`, and `SETUP.md`.

## Current state

- **Current phase:** Phase 1 — Foundation and scaffolding (`docs/roadmap.md` §3)
- **Current task:** Dependency-currency research (web, in progress) → pin version catalog
- **Last completed:** Foundation read (all 18 docs + 15 ADRs); ledgers; CLAUDE.md conflict-chain fix; 6 Flutter-residue doc corrections (committed `f46de11`)
- **Next up (Phase 1 work breakdown, in order):**
  1. Gradle skeleton: wrapper, `settings.gradle.kts`, root + `app/build.gradle.kts`, `gradle/libs.versions.toml` (versions from checkpoint research), `local.properties` (sdk.dir, local only)
  2. App shell: `CuizineApplication` (Hilt), `MainActivity` (single-activity, edge-to-edge), manifest
  3. `core/config` absence-driven config reader (fake-first DI selection lives here)
  4. Room: 9 entities per `data-model.md` §3–7 (accounts, profiles, constraints, food_data_cache, suggestions, cooked_meals, pantry_items, event_log, schema_metadata) + TypeConverters + DAOs (minimal) + payload `@Serializable` classes in `engine/types`
  5. `ConstraintGraphMigrator` (empty registry) + the 7 named migrator tests + schema-integrity tests (`testing-strategy.md` §9)
  6. Theme: `ui/theme/{Color,Type,Shape,Theme}.kt` (M3, dynamic color with protected severity semantics, light+dark)
  7. `CuizineScaffold`: 4 tabs (Today/Pantry/Profile/Settings) + centered conversation FAB, per-tab back stacks, themed "Welcome to Cuizine" empty state
  8. Orbit MVI + Hilt wiring with one proof container
  9. Quality gate: ktlint, Android Lint, Kover thresholds, `qualityGate` aggregate task, layering-discipline test (source-scan, no new dep)
  10. Phase 1 exit check (`roadmap.md` §11: infra tests pass, app compiles + launches showing themed 4-tab scaffold + FAB, Room schema in place) → phase report → push

## Blockers

| # | Item | Status | Notes |
|---|---|---|---|
| — | *(none yet)* | | |

Founder-pending items are tagged **founder-pending** and mirrored in `SETUP.md`
where they involve credentials/services only the founder can supply.

## Verification honesty ledger

Tasks provable only against live services are listed here as
**verified-against-fake / pending live verification** — distinct from done.

| Item | Phase | Status |
|---|---|---|
| — | | |

## Toolchain (verified 2026-06-10)

| Tool | Version / state |
|---|---|
| JDK (JAVA_HOME) | JDK 21 at `C:\Program Files\Java\jdk-21` (PATH `java` is 22.0.2; Gradle pinned to JAVA_HOME) |
| Android SDK | `C:\Users\ganes\AppData\Local\Android\Sdk` (platform-tools, build-tools, emulator, system-images present; `ANDROID_HOME` unset — handled via `local.properties` `sdk.dir`) |
| Gradle | Via wrapper (created in Phase 1 scaffolding) |
| Git | Working; remote = GitHub (`main`) |

## Phase exit-criteria tracker

| Phase | Status |
|---|---|
| 1 — Scaffolding | **In progress** |
| 2 — UI + mock data | Not started |
| 3 — Constraint engine + validator | Not started |
| 4 — Food data layer | Not started |
| 5 — Agents + orchestrator | Not started |
| 6 — Sync + encryption + billing | Not started |
| 7 — Integration polish | Not started |
