# PROGRESS.md — Build ledger

> Updated every iteration. The session-recovery entry point alongside
> `CLAUDE.md`, `DECISION-LOG.md`, `FOUNDER-FEEDBACK.md`, and `SETUP.md`.

## Current state

- **Current phase:** Phase 2 — UI shell, component library, and mock data (`docs/roadmap.md` §3)
- **Current task:** Phase 2 kickoff: surface Phase 2 decision checkpoints; build canonical fixtures (Sukhi/Aisha) + mock containers; then screens per `ui-ux-spec.md` §5
- **Last completed:** **Phase 1 COMPLETE** (report: `PHASE-REPORTS/phase-1.md`; commits `f46de11`, `b067e7b`). qualityGate green (27 tests, zero lint warnings); app verified launching on Pixel 8 AVD with themed 4-tab scaffold + FAB
- **Next up (Phase 2 outline):**
  1. Phase 2 checkpoints: UI1–UI6 nav/conversation bets [data-driven, tune on-device]; Material 3 evolution [research-informed — research done at Phase 1: M3 1.4.0 current, no disqualifier; re-verify only if BOM bumps]
  2. Canonical fixtures in `test/fixtures/` + mock repositories returning them (Sukhi constraint graph, seeded pantry, saved suggestions; Aisha for temporal scopes)
  3. Mock containers per `ui-ux-spec.md` §5 contracts (State/Intents identical to future real ones) + 12-component library (§6)
  4. Screens: onboarding/conversation flow A → Today/suggestion/conflict (B, D) → FAB conversation (C) → Profile/constraint detail → Pantry → Settings surfaces (E, F)
  5. Compose UI tests per screen + flow navigability (Robolectric where possible)
  6. Mock-data indicator in debug builds; doc-fix for `event_log.profile_id` nullability
  7. Phase 2 exit: Flows A–F walkable on fixtures, zero LLM calls → report with device install steps → push

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
| 2 — UI + mock data | **In progress** |
| 3 — Constraint engine + validator | Not started |
| 4 — Food data layer | Not started |
| 5 — Agents + orchestrator | Not started |
| 6 — Sync + encryption + billing | Not started |
| 7 — Integration polish | Not started |
