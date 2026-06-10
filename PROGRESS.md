# PROGRESS.md — Build ledger

> Updated every iteration. The session-recovery entry point alongside
> `CLAUDE.md`, `DECISION-LOG.md`, `FOUNDER-FEEDBACK.md`, and `SETUP.md`.

## Current state

- **Current phase:** Phase 3 — The constraint engine and validator (`docs/roadmap.md` §3)
- **Current task:** Phase 3 continuation — next units in order: (1) expand the hard-cases suite to the testing-strategy §5 minimums (7 categories; current engine-core slice = 17 tests; remaining bulk can fan out to parallel test-writer agents against the now-stable API, then verified); (2) property-based tests for active-set + per-constraint check (pick Kotlin lib — "glados" in testing-strategy is a Dart residue; candidates: jqwik or kotest-property; surface in DECISION-LOG before adding); (3) Room-backed `ConstraintStore` implementation + DI; (4) rebind `ProfileRepository` to the real engine (same State/Intents — replaces constraint paths of MockProfileRepository; onboarding writes via addConstraint with provenance); (5) Kover near-100% threshold on `ai.cuizine.engine.validator` + `engine.scope`; (6) phase report + push
- **Last completed:** Engine core committed (`eeec208`): 11-op ConstraintGraphApi, ConstraintGraphEngine (tier enforcement, two-pass active set, cache, session relaxations), ScopeEvaluator (6 temporal kinds incl. composite + NOAA solar port), 5-step SuggestionValidator (verbatim §7, safety floor verified), ConflictAnalyzer (§8 mechanics), Aisha fixture, 17 engine-core tests. qualityGate green. Spec gaps logged in DECISION-LOG #3a. Phase 2 closed earlier today (`PHASE-REPORTS/phase-2.md`)
- **Phase 3 plan notes (recovery):** engine/ stays pure Kotlin; the validator's deterministic logic and conflict policy (ADR 0009/0010) are PARK-ALWAYS territory — build exactly as specified, any deviation goes founder-pending; hard-cases suite minimums per testing-strategy §5 (7 categories: ≥16 severity, 25+ type, 15+ scope incl. all 6 temporal kinds + DST + solar, 10+ active-set, 30+ validator, 15+ conflict, 10+ provenance; allergens 45+, religious 15+, limits 12+ arrive with Phase 4 food data); Aisha fixture authored here (composite temporal scopes); property-based lib decision due (testing-strategy "glados or equivalent" is a Dart residue — pick Kotlin equivalent, surface in DECISION-LOG)

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
| Android SDK | `C:\Users\ganes\AppData\Local\Android\Sdk`; Platform 37 + Build-Tools 36.0.0 auto-installed during first build |
| Gradle | Wrapper 9.4.1 · AGP 9.2.1 · Kotlin 2.3.21 · KSP 2.3.9 (full matrix: DECISION-LOG #1a) |
| Emulator | Pixel 8 AVD verified booting + running the app (headless) |
| Git | Working; remote = GitHub (`main`) |
| Quality gate | `gradlew.bat qualityGate` — green as of Phase 1 close |

## Phase exit-criteria tracker

| Phase | Status |
|---|---|
| 1 — Scaffolding | **✅ Complete (2026-06-10)** — `PHASE-REPORTS/phase-1.md` |
| 2 — UI + mock data | **✅ Complete (2026-06-10)** — `PHASE-REPORTS/phase-2.md`; founder device walkthrough pending (non-blocking) |
| 3 — Constraint engine + validator | Not started |
| 4 — Food data layer | Not started |
| 5 — Agents + orchestrator | Not started |
| 6 — Sync + encryption + billing | Not started |
| 7 — Integration polish | Not started |
