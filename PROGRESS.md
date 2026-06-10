# PROGRESS.md — Build ledger

> Updated every iteration. The session-recovery entry point alongside
> `CLAUDE.md`, `DECISION-LOG.md`, `FOUNDER-FEEDBACK.md`, and `SETUP.md`.

## Current state

- **Current phase:** Phase 4 — The food data layer (`docs/roadmap.md` §3)
- **Current task:** Phase 4 kickoff per protocol: (1) read `data-model.md` §5 (cache/bundle DDL detail — already extracted), ADR 0012 in full, and the Phase 4 checkpoints (USDA/OFF API state [research-informed] — research current endpoints/auth/rate limits/schemas BEFORE building clients; AI-fallback accuracy [data-driven] → alpha-deferred); (2) build order: CanonicalIngredientEntry payloads + bundle loader (assets/food_data_bundle/v1.json — author initial Sukhi-relevant entries) → FoodDataCache (Room ops + TTLs: USDA 6mo, OFF 3mo, bundle/user-verified never) → Retrofit USDA + OFF clients behind interfaces with recorded-fixture fakes (key absent → fake; note in SETUP.md: USDA needs key, OFF doesn't) → severity-scoped AI fallback wiring through ModelProvider port (FakeModelProvider until Phase 5) → replace `StaticFoodDataPort` binding with the real three-layer provider (validator unchanged) → allergen-exhaustive (45+: 9 allergens × 5) + religious-dietary (15+) suites + Sukhi-and-alliums canonical case against real categories; (3) Retrofit/OkHttp/serialization versions already researched (DECISION-LOG #1a: Retrofit 3.0.0 first-party kotlinx converter, OkHttp 5.4.0, mockwebserver3 coordinates for tests — new test dep, surface in log)
- **Last completed:** **Hard-cases suite COMPLETE — 224 tests green, Kover enforcing validator 95% line / 85% branch** (commits `eeec208` engine core, `c0b0a86` Room store + EngineProfileRepository bound [first mock→real swap, screens untouched], `5fb4a30` hard-cases + 2 spec-conformance fixes the suite caught: Preference-tier never rejects (§4), end-of-local-day contextual expiry (§3), Step-4 disclosure-only-on-pass (§7)). Allergen-exhaustive (45+) and religious-dietary (15+) cases arrive with Phase 4's real food categories per testing-strategy
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
| 3 — Constraint engine + validator | **✅ Complete (2026-06-10)** — `PHASE-REPORTS/phase-3.md`; 224 tests, validator coverage enforced; one founder-pending doc-wording note (#3b-4) |
| 4 — Food data layer | Not started |
| 5 — Agents + orchestrator | Not started |
| 6 — Sync + encryption + billing | Not started |
| 7 — Integration polish | Not started |
