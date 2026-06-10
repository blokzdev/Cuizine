# Phase 1 report — Foundation and scaffolding

**Completed:** 2026-06-10 · **Exit criteria:** all met (`docs/roadmap.md` §3/§11)
**Commits:** `f46de11` (delegated-mode activation + doc fixes), `b067e7b` (scaffold)

## Built

- **Gradle build:** wrapper 9.4.1, AGP 9.2.1 (built-in Kotlin — new AGP 9
  pattern, no `kotlin-android` plugin), Kotlin 2.3.21, KSP 2.3.9, JDK 21,
  compileSdk 37 / targetSdk 36 / minSdk 26, version catalog
  (`gradle/libs.versions.toml`) as single version source of truth.
- **Room data layer:** all 9 tables from `data-model.md` §3–7 as entities with
  the spec's indices and FKs (FK enforcement explicitly enabled), schema v1
  exported to `app/schemas/` and committed; minimal DAOs for the Phase 1 hot
  paths; `schema_metadata` seeded `current_version=1` on first launch.
- **Migrator:** `ConstraintGraphMigrator` — forward-only, single-step chain,
  transactional, records `schema_metadata` + `schema_migration_completed`
  event rows; registry empty by design.
- **Engine types (pure Kotlin):** the 5 constraint payload schemas, 5-dimension
  scope, provenance record — exact snake_case wire shapes from `data-model.md`
  §4; `Severity`/`ConstraintType` enums as the CHECK-set enforcement locus.
- **UI shell:** Material 3 theme (`Color/Type/Shape/Theme.kt`) — warm-neutral
  light+dark, dynamic color with severity semantics pinned outside the dynamic
  palette; `CuizineScaffold` (4 tabs + centered conversation FAB); per-tab
  back-stack navigation; themed "Welcome to Cuizine" empty state. Verified
  launching on a Pixel 8 AVD (screenshot during build; emulator headless).
- **MVI/DI:** Hilt wired app-wide; first Orbit container (`TodayViewModel`)
  as the Orbit-11-on-Kotlin-2.3 smoke test — passed.
- **Quality gate (local-first CI):** `gradlew qualityGate` = ktlint 1.8.0 +
  Android Lint (warnings-as-errors) + unit tests (incl. Robolectric) +
  koverVerify. Green at every commit. Layering discipline enforced by a
  source-scan test (engine/shared pure-Kotlin, data no-UI, ui no-DAOs).
- **Trust posture hardening:** OS backup + device-transfer of the local store
  fully excluded (`data_extraction_rules.xml`, `full_backup_content.xml`) —
  they would bypass the encryption boundary.

## Tested

27 unit tests, all green: 7 migrator tests (the named set from
`testing-strategy.md` §9 incl. rollback-on-failure and idempotency),
6 schema-integrity tests (FK enforcement both ways, soft-delete readability,
active-constraint query semantics, food-cache uniqueness, metadata seed),
8 payload/scope/provenance serialization round-trips + enum rejection,
1 Orbit container test, 5 layering tests.

## Checkpoint decisions (full detail in DECISION-LOG.md #1a–#1d)

- **Dependency currency [research-informed]: KEEP all documented choices.**
  No disqualifiers found on the ADR 0016 stack. Kotlin pinned 2.3.21 (2.4.0
  blocked by KSP), Compose BOM fell back 2026.06.00→2026.05.01 (mirror lag,
  pre-planned fallback). Navigation 3 noted as Google's new-app
  recommendation but Navigation Compose 2.9.8 kept (no disqualifier; Nav3
  would be an un-mandated architecture change — flagged as v2 checkpoint).
- **Orbit ergonomics [data-driven]: deferred** to its named moment (after 2–3
  real containers, mid-Phase 2). Smoke test passed.

## Deviations & notable implementation mappings

- DDL CHECK constraints → code-level enforcement (Room limitation;
  consistent with `data-model.md` §4's own note). DECISION-LOG #1c.
- Partial indices (`WHERE removed_at IS NULL`) → plain indices (Room
  annotation limitation; widening only, perf-equivalent at v1 scale).
- `event_log.profile_id` nullable (doc self-contradiction resolved;
  doc fix queued below).
- Version-currency lint checks disabled in the per-commit gate (they'd make
  it fail on upstream releases); currency is owned by the roadmap checkpoints.

## Doc updates made

- `CLAUDE.md`: explicit conflict-resolution chain added (§8).
- Flutter-era residue corrected in `build-conventions.md` (4×),
  `glossary.md`, `security-and-privacy.md`, `technical-architecture.md`
  (DECISION-LOG #1).

## Doc updates queued (will land with a Phase 2 commit)

- `data-model.md` §7: make `event_log.profile_id` nullability explicit
  (currently NOT NULL DDL with a "nullable for pre-profile events" comment).

## Founder-pending

None for Phase 1. Nothing in SETUP.md is needed yet; the emulator on this
machine (Pixel 8 AVD) boots and runs the app.

## Next

Phase 2 — every v1 screen + the 12-component library against mock containers
returning canonical Sukhi fixtures, Flows A–F walkable with zero LLM calls,
visible mock-data indicator in debug builds. A walkthrough-ready install guide
for your device will be in the Phase 2 report.
