# PROGRESS.md — Build ledger

> Updated every iteration. The session-recovery entry point alongside
> `CLAUDE.md`, `DECISION-LOG.md`, `FOUNDER-FEEDBACK.md`, and `SETUP.md`.

## Current state

- **Current phase:** Phase 5 — The agent layer and orchestrator (`docs/roadmap.md` §3)
- **Current task:** Phase 5 kickoff per protocol: (1) read `agent-architecture.md` IN FULL (§2 archetype, §3 orchestrator + regeneration loop, §4 Curator, §5 Chef, §6 Pantry, §8 provider routing, §9 canonical patterns A/B/C) + ADR 0006 + `testing-strategy.md` §6 (eval harness: AgentEvalScenario/ExpectedBehavior, ~50–100 automated scenarios) + extracts in `C:\Users\ganes\AppData\Local\Temp\claude\cuizine-extracts\` (agent-architecture extract = file `rchitecture_md...json`); (2) checkpoints: LLM provider landscape since ADR 0006 [research-informed — web research model lineups/pricing/structured-output before locking adapters]; per-agent routing benefit [data-driven → post-eval-harness]; inferred-pantry heuristic [data-driven → alpha]; (3) build order: ModelProvider interface + FakeModelProvider (deterministic, fixture-based, covers eval scenarios — CLAUDE.md known fake) → agent archetype + typed contracts (CuratorInput/Output etc. in shared/types per build-conventions §3) → orchestrator (rule-based intent routing; owns 3-attempt regeneration + conflict resolution loop from Phase 3's ConflictAnalyzer) → Curator/Chef/Pantry agents + prompts/ dir (curator.md, chef.md, pantry.md with version headers per build-conventions §8; halal risk-tag layering guidance from #4b goes in curator.md) → eval harness in test/agents/eval_harness/ → wire into Today/conversation surfaces replacing MockConversationService + ValidatedMockSuggestionRepository bindings (same State/Intents); (4) real LLM keys → SETUP.md Phase 5 section (Anthropic/Google/OpenAI signup + local.properties names); honesty-ledger rows for live-provider behavior
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
| USDA FoodData Central live lookups (client built; mapping tested against recorded shapes; key absent) | 4 | verified-against-fake / pending live verification |
| Open Food Facts live search (Search-a-licious client built; mapping tested against recorded shapes) | 4 | verified-against-fake / pending live verification |

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
| 4 — Food data layer | **✅ Complete (2026-06-10)** — `PHASE-REPORTS/phase-4.md`; 298 tests; live USDA/OFF pending key (honesty ledger); kosher combination-rule limitation founder-noted (#4b) |
| 5 — Agents + orchestrator | Not started |
| 6 — Sync + encryption + billing | Not started |
| 7 — Integration polish | Not started |
