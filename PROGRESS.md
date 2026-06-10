# PROGRESS.md — Build ledger

> Updated every iteration. The session-recovery entry point alongside
> `CLAUDE.md`, `DECISION-LOG.md`, `FOUNDER-FEEDBACK.md`, and `SETUP.md`.

## Current state

- **Current phase:** Phase 4 — The food data layer (`docs/roadmap.md` §3)
- **Current task:** Phase 4 continuation. DONE: three-layer FoodDataProvider + curated bundle v1.0.0 (48 entries + ramps correction) + cache TTLs + canonical Sukhi-and-alliums case green (commit `8b5f8b2`); checkpoint resolved KEEP (DECISION-LOG #4a — USDA/OFF build facts pinned there); SETUP.md USDA-key section written. REMAINING: (1) Retrofit USDA client (X-Api-Key header interceptor; POST /foods/search dataType Foundation+SR Legacy; GET /food/{fdcId}?format=full; TWO nutrient DTO shapes — flattened search vs nested detail; nutrient numbers 203/204/205/291/307/306/305 per-100g) + OFF client (User-Agent required; OFF detail in phase4-food-api-currency workflow output file) behind `ExternalFoodSources`, absence-driven on `cuizine.usda.api.key` in local.properties (BuildConfig wiring through core/config); add retrofit/okhttp catalog entries (#1a versions); (2) allergen-exhaustive (45+: 9 allergens × {literal, categorical, hidden-in-product, AI-fallback-reject, user-confirmed-safe}) + religious-dietary (15+: halal, kosher, Hindu veg, Jain, Buddhist) suites — can fan out to test authors against the now-real provider+bundle; (3) `verified-against-fake / pending live verification` row in the honesty ledger for live USDA/OFF; (4) phase report + push
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
