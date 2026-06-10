# PROGRESS.md — Build ledger

> Updated every iteration. The session-recovery entry point alongside
> `CLAUDE.md`, `DECISION-LOG.md`, `FOUNDER-FEEDBACK.md`, and `SETUP.md`.

## Current state

- **Current phase:** Phase 3 — The constraint engine and validator (`docs/roadmap.md` §3)
- **Current task:** Phase 3 kickoff: re-read `constraint-engine-spec.md` in full (§4–§9 + §11) + ADR 0009/0010; surface Phase 3 checkpoints (validator 3-retry bound, active-set cache invalidation — both [data-driven] at named moments); then engine types → scope evaluation → active set → validator → conflict resolution → hard-cases suite → wire Profile surface to real engine
- **Last completed:** **Phase 2 COMPLETE** (report: `PHASE-REPORTS/phase-2.md`). All Flows A–F walkable on fixtures with zero LLM calls; on-emulator verification done (screenshots); 50 tests green; UI1–UI6 resolved (DECISION-LOG #2c); ui-ux-spec §5.5 living-doc note added; Orbit ergonomics verdict: keeping it
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
