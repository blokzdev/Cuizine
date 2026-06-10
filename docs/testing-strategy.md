# Cuizine — Testing Strategy

> The doc that specifies how Cuizine knows it works. Where the preceding foundation docs answer "what should Cuizine do" and "how should it be built," this document answers "how do we verify, before users see it, that Cuizine actually does what the docs say." Every safety commitment in `constraint-engine-spec.md`, every trust commitment in `local-first-sync.md`, every principle in `monetization-and-billing.md` becomes a real guarantee only when the test suite verifies it. Without rigorous tests, the foundation docs are just promises; with them, the promises are verifiable.

## 1. Purpose & how to read this doc

This document specifies the **testing strategy for Cuizine across v1, v2, and v3**, with the discipline that the test suite grows organically with the codebase but is grounded in canonical fixtures and structured categories from day one. It assumes you have read all previous foundation docs and the ADRs.

This document defines: the core testing principles; the test pyramid for Cuizine and what each layer covers; the canonical test fixtures (Sukhi, Aisha, Marcus profiles plus seeded constraint graphs and food data); the hard cases test suite for the constraint engine that exercises every safety-floor behavior; the per-agent eval harness with its hybrid automated-and-manual approach; the validator's exhaustive test suite; the sync layer tests including encryption round-trip and recovery flows; the schema migration tests including the empty-registry pattern; the integration tests for the Sukhi user journey from PRD § 4; the performance and resource tests for low-end Android devices; the explicit list of things deliberately not tested with rationale; and the open questions this spec acknowledges.

This document does **not** define: the actual test code (lives in `test/`); the CI/CD pipeline configuration (lives in deployment documentation); the manual QA process during alpha (lives in `alpha-feedback-and-iteration.md`); or a complete enumeration of every test case Cuizine will ever need (the test suite grows organically — this doc specifies the framework, the categories, and the canonical fixtures).

**When this document and a deeper-dive doc disagree,** this doc wins for testing approach, fixture definitions, and coverage discipline. The foundation docs win for the behaviors being tested. If a tension arises between what a behavior should be and what tests for it should look like, the behavior wins and the tests are revised to verify it.

## 2. Core testing principles

> The non-negotiable commitments the test suite is designed against. Every testing decision traces back to one of these. They are why the test suite exists and what it is for.

### Principle 1: Tests are how we know the safety floor holds

Per ADR 0010 and `constraint-engine-spec.md` Section 7, Cuizine commits to deterministic post-generation validation that catches medical and religious constraint violations before users see them. This commitment is real only if there is a test suite that exercises every reasonable failure mode and confirms the validator catches each one. The validator's test suite is the largest single body of tests in Cuizine and it is the test suite the founder reviews most carefully.

The same principle applies to `local-first-sync.md` (the encryption and recovery flow tests are how we know the trust posture holds) and to `monetization-and-billing.md` (the tier enforcement tests are how we know the billing principles hold). The pattern: every safety floor in a foundation doc deserves a test suite that demonstrates it actually holds.

### Principle 2: Fixtures are canonical, named, and reusable

Tests need realistic data to be meaningful. Cuizine's canonical fixtures (Sukhi, Aisha, Marcus, seeded constraint graphs, food data entries) are defined once in `test/fixtures/` and used everywhere a test needs that shape of data. The discipline: never invent new test profiles for individual tests when a canonical fixture would work. The canonical fixtures are part of the test suite's vocabulary, and every developer (human or AI) reads them as the starting point for understanding what realistic Cuizine data looks like.

### Principle 3: Tests document behavior

A well-written test is a specification of behavior in code form. When the foundation docs say "the validator must reject any suggestion that violates an active inviolable constraint," the corresponding test demonstrates exactly what that means with a concrete example. Future developers (human or AI) read tests to understand how the system is supposed to work, alongside reading the foundation docs. Tests that don't document anything — that just assert internal implementation details without explaining why — are a smell, not a feature.

### Principle 4: The test bar is non-negotiable

Per `build-conventions.md` Section 10, every commit passes all unit tests, the linter, and the format check. There are no exceptions for "WIP commits" or "fast iteration cycles." The discipline is what makes the test suite useful — a test suite that's allowed to fail occasionally is a test suite that no one trusts and that catches nothing. Every commit is a green commit.

### Principle 5: Coverage is risk-tiered, not uniform

Different parts of Cuizine have different consequences for being wrong. The validator that catches medical-tier constraint violations has near-zero tolerance for bugs because the failure mode is real harm to a user. A trivial data class accessor has very high tolerance because the failure mode is a compile error caught immediately. The test suite reflects this asymmetry: the validator gets exhaustive coverage with edge case enumeration, while data classes get incidental coverage from the integration tests that exercise them. The discipline: spend the testing investment where it matters most.

### Principle 6: Tests run fast or they don't run

A test suite that takes 30 minutes to run is a test suite that gets skipped, and a skipped test suite catches nothing. Cuizine's test suite targets sub-five-minute total runtime for the unit and integration test suites combined, with the most expensive tests (LLM-backed agent evals, sync round-trips against a real Firestore emulator) clearly separated and run on a slower cadence. Fast tests are run on every commit; slow tests are run on an automated schedule (configured in CI) and before every release.

## 3. The test pyramid for Cuizine

> The test pyramid is the standard model for how to allocate testing effort across abstraction layers. Cuizine adapts the standard pyramid to its specific architecture: heavy unit testing at the engine and validator layers, integration tests for cross-layer behavior, end-to-end tests for the canonical user journeys, and a small number of performance tests at the top.

### Layer 1: Unit tests

The largest layer of the pyramid. Unit tests cover individual classes, methods, and functions in isolation. They run in milliseconds, they have no external dependencies, and they exercise behavior in narrow, focused ways.

The vast majority of Cuizine's unit tests live in three places:

- **`test/engine/`** — tests for the constraint engine, the validator, the conflict resolver, the scope evaluator, the type payload validation. This is where the hard cases test suite from Section 5 lives. Heavy investment because Principle 1.
- **`test/data/encryption/`** — tests for the encryption layer, the key derivation, the recovery flow, the per-blob envelope. Heavy investment because the encryption layer is part of the trust posture.
- **`test/billing/`** — tests for the `TierPolicy` module, the subscription state transitions, the trial mechanics, the upgrade/downgrade flows in isolation. Heavy investment because billing is where most products' trust relationships break down.

Unit tests in other folders (`test/core/`, `test/data/database/`, `test/data/sync/`, `test/data/food_data/`, `test/agents/`, `test/ui/`) are written with behavior focus rather than coverage targets. Every documented behavior has at least one test; trivial code (data class accessors, simple wrappers) is exercised incidentally by integration tests rather than via dedicated unit tests.

### Layer 2: Integration tests

The middle layer. Integration tests cover behaviors that emerge from the interaction of multiple classes or layers — the constraint engine and the validator working together, the orchestrator routing through the agent layer with validation pass-through, the Curator parsing user input and writing to the constraint graph in a single round-trip.

Integration tests live in `test/integration/` and are organized by user journey rather than by code layer. Each test simulates a realistic user action and verifies the system's end-to-end behavior:

- "Sukhi adds a new constraint via free-text input"
- "The Chef generates a meal that the validator catches and regenerates"
- "The conflict resolver surfaces options when an inviolable constraint blocks all suggestions"
- "A trial expires and the user is downgraded gracefully"

Integration tests typically run in tens of milliseconds each and use the canonical fixtures from Section 4. They do not invoke real LLM providers or real Firestore — those are mocked at the orchestrator boundary so the integration tests stay fast and deterministic.

### Layer 3: End-to-end tests

The smallest layer of the pyramid. End-to-end tests cover full user journeys from PRD § 4 with as little mocking as possible. They run in seconds (not milliseconds) and they use the Jetpack Compose UI testing framework (`androidx.compose.ui.test`, run on Robolectric for JVM speed where possible and on-device/emulator where necessary) to drive the actual UI through user actions, with the agent layer mocked to return canned responses (so the tests are deterministic) and with a real local SQLite database via Room (not an in-memory mock).

End-to-end tests live in `test/integration/journeys/` and cover:

- Sukhi's day 0 onboarding journey
- Sukhi's day 4 first meal suggestion
- Sukhi's day 5 free-text update with contextual constraint
- Sukhi's day 8 rejection feedback loop
- Sukhi's day 14 trial expiration (when applicable)
- Sukhi's day 30 cumulative state check

These are the journeys from the PRD § 4 walkthroughs. Each journey is a single integration test that simulates the entire flow and verifies the cumulative state at the end.

### Layer 4: Performance, load, and resource tests

The top of the pyramid. These tests verify that Cuizine performs adequately under realistic conditions — that the active-set computation is fast enough, that the food data cache lookups are O(log n), that the sync round-trip completes within target latency, that the app starts up in under 2 seconds on a low-end Android device, that the encryption derivation completes in 500ms-1s on representative hardware.

Performance tests live in `test/performance/` and run on a slower cadence (scheduled runs and pre-release). They are written to fail loudly when a regression appears — a function that previously took 50ms now takes 500ms is a regression that demands investigation.

### Why the pyramid is shaped this way

The unit test layer is the largest because unit tests are the cheapest and most precise — they're easy to write, easy to maintain, and they pinpoint bugs to specific functions. The integration test layer is smaller because integration tests are more expensive per test but they catch the bugs unit tests miss (interaction failures). The end-to-end test layer is smallest because end-to-end tests are the most expensive per test and they catch the smallest set of bugs (UI integration, cross-cutting failures). The performance test layer is smallest because performance regressions are uncommon and don't need a high test count to catch — a few well-chosen tests are sufficient.

## 4. Test fixtures

> Canonical fixtures used across the test suite. Per Principle 2, these are defined once and reused everywhere. New tests do not invent new profiles when a canonical fixture would work. The fixtures live in `test/fixtures/` and are organized into a small number of well-known files.

### Profile fixtures

**`fixtures/profiles/sukhi.kt`** — The canonical Sukhi profile from PRD § 4. Sukhi is a 53-year-old Punjabi Canadian woman in Brampton, ON, with T2 diabetes and IBS-M, cooking for a household of five. Her constraint graph at the canonical fixture state contains:

- **Inviolable constraints:** none (Sukhi has chosen to flag her medical constraints as Medical rather than Inviolable)
- **Medical constraints:** avoid onions and garlic during IBS flares (contextual scope on the `ibs_flare` flag); limit sodium to 2000mg/day (soft); limit refined carbs per meal to 45g (soft); avoid mango and honey (high glycemic, conditional on blood sugar context)
- **Religious & Cultural constraints:** Tuesday vegetarian (temporal scope on weekday Tuesday); avoid beef (lifelong cultural)
- **Preference constraints:** prefer rajma (high strength); prefer aloo dishes; prefer her family's specific masala patterns
- **Cultural context:** Punjabi origin, North Indian cooking patterns, Brampton-area Canadian immigrant context, Tuesday vegetarian tradition

This fixture is the most-used in Cuizine's test suite. Every test that needs a "realistic complex constraint graph" starts here.

**`fixtures/profiles/aisha.kt`** — A canonical Aisha profile for temporal scope testing. Aisha is a Muslim Canadian observing Ramadan, with constraints that include:

- **Religious & Cultural:** Ramadan fasting from sunrise to sunset (composite temporal scope: date-bounded to Ramadan 2026 AND daily window sunrise-to-sunset); halal observance (lifelong); prefer iftar-appropriate foods (require contextual)
- **Cultural context:** South Asian Muslim, varied cuisines, observant of religious calendars

This fixture exercises the temporal scope dimensions in their hardest form (sunrise/sunset solar bases, composite scopes, date-bounded windows).

**`fixtures/profiles/marcus.kt`** — A canonical Marcus profile for v2 caregiver testing. Marcus is a 42-year-old single father in Toronto managing meals for himself and his daughter Emma (10, ADHD on stimulant medication that suppresses appetite). His v2 household includes both profiles:

- **Marcus's profile:** standard adult cooking, no medical constraints
- **Emma's profile (dependent, silent flow):** prefer high-calorie foods (medication side effect); contextual constraint on appetite (when meds are active)

This fixture exercises the v2 multi-profile household features and the dependent profile relationships from ADR 0004.

### Constraint graph fixtures

**`fixtures/graphs/empty.kt`** — An empty constraint graph for tests that need a baseline.

**`fixtures/graphs/sukhi_canonical.kt`** — The full Sukhi constraint graph as described above. Imported by every test that uses the Sukhi profile.

**`fixtures/graphs/sukhi_with_active_flare.kt`** — Sukhi's graph with the `ibs_flare` contextual constraint active, so that her onion/garlic avoid constraints are in the active set. Used for tests that verify the active-set computation correctly applies contextual scope.

**`fixtures/graphs/conflict_scenarios.kt`** — A collection of constraint graphs designed to surface specific conflict cases:
- Two medical constraints conflicting (Sukhi's IBS avoid vs a low-sodium limit forcing onion-based flavor)
- A medical constraint conflicting with a religious constraint (rare but tested)
- An inviolable constraint blocking all reasonable meals (forcing the conflict resolution to return "no meal found")
- A user-friendly conflict (preferences only, silently relaxed)

### Food data fixtures

**`fixtures/food/canonical_entries.kt`** — A collection of canonical ingredient entries covering the high-confidence cases:
- Common produce (onion, garlic, leek, tomato, potato, spinach, etc.)
- Common dals and legumes (toor dal, masoor dal, chana dal, kala chana, rajma)
- Common spices (cumin, turmeric, garam masala, asafoetida, ajwain)
- Common dairy (milk, ghee, paneer, yogurt)
- Common allergens (peanut, tree nut, sesame, soy, wheat)

Each entry carries the categories, allergens, and nutritional composition that the tests need to verify validator behavior.

**`fixtures/food/edge_cases.kt`** — Specific entries designed to trigger edge cases:
- "ramps" — wild allium, used in the Section 5 Sukhi-and-ramps case for AI fallback testing
- "kala namak" — black salt, used to test cultural-specific ingredients
- A deliberately ambiguous entry like "beans" that requires disambiguation

**`fixtures/food/ai_fallback_scenarios.kt`** — Entries designed to exercise the AI-assisted categorization path from ADR 0012, with mock LLM responses for deterministic testing.

### Suggestion fixtures

**`fixtures/suggestions/passing.kt`** — A collection of Chef outputs that should pass validation against canonical constraint graphs. Used as positive test cases for the validator.

**`fixtures/suggestions/failing.kt`** — A collection of Chef outputs that should fail validation. Each one violates a specific constraint in a specific way, with the violation type documented in the fixture file. Used as negative test cases.

**`fixtures/suggestions/edge_cases.kt`** — Suggestions that exercise specific edge cases: unknown ingredients with various severity contexts, soft limits being approached, prefer constraints being violated without rejection, contextual constraints temporarily affecting active sets.

## 5. The hard cases test suite for the constraint engine

> The single most important section in this doc. The hard cases test suite is the load-bearing test suite that exercises every safety-floor behavior from `constraint-engine-spec.md`. If this test suite is comprehensive and passing, the constraint engine is trustworthy. If it has gaps, the safety commitments from ADR 0009 and ADR 0010 are at risk.

### What the hard cases suite covers

The suite is organized into seven categories, mapped to the `constraint-engine-spec.md` sections that specify the behaviors being tested. Each category has a target test count and a discipline for what must be exercised.

#### Category 1: Severity tier handling

For each of the four severity tiers from ADR 0009, the suite verifies:

- **Inviolable:** A suggestion violating an active inviolable constraint is rejected. The validator does not AI-fallback for unknown ingredients. The conflict resolver never offers an inviolable constraint for relaxation. The user-facing fallback message is shown when no meal can be found.
- **Medical:** A suggestion violating an active medical constraint is rejected and triggers regeneration. The AI fallback runs for unknown ingredients with the disclosure note. The conflict resolver surfaces options to the user, never silently relaxing.
- **Religious & Cultural:** Same behaviors as Medical, with the additional verification that Religious & Cultural constraints are co-equal with Medical in conflict resolution (neither takes precedence).
- **Preference:** A suggestion violating a preference produces a disclosure note but not a rejection. The conflict resolver silently relaxes preferences when needed. The AI fallback runs without disclosure.

Target: at least 4 tests per tier (one per behavior), 16 total minimum, growing as edge cases surface.

#### Category 2: Constraint type semantics

For each of the five constraint types from `constraint-engine-spec.md` Section 3, the suite verifies the type's semantic behavior:

- **`avoid`:** literal target match, categorical match (onion → allium → all alliums), exception list (Sukhi tolerates ghee), targets that resolve through the Food Data Provider
- **`prefer`:** strength affecting Chef ranking (low/medium/high), no rejection on violation, disclosure note structure
- **`require`:** single-meal vs daily vs weekly windows, threshold-based requirements (iron-rich foods), categorical requires
- **`limit`:** per-meal vs per-day vs per-week windows, hard vs soft enforcement, ceiling overflow detection, the boundary case where a meal exactly hits the ceiling
- **`contextual`:** state lifecycle (set, expire, clear), interaction with other constraints' scope (the IBS flare example), the `incompatible_with` field (Aisha's Ramadan fasting blocking food during the window)

Target: 5+ tests per type, 25+ total.

#### Category 3: Scope dimension evaluation

For each of the five scope dimensions from `constraint-engine-spec.md` Section 5, the suite verifies the dimension's evaluation logic:

- **Temporal:** all six scope kinds (always, weekly, daily_window, date_bounded, phase_bounded, composite), timezone handling, daylight saving transitions, sunrise/sunset solar bases for religious fasts
- **Contextual:** required-flag and excluded-flag forms, multi-flag composition, flag expiration mid-query
- **Location:** anywhere (NULL country) vs specific country, future v2/v3 location updates
- **Household:** self vs specific_profiles vs whole_household modes, the v1 single-profile case, forward compatibility for v2 multi-profile
- **Profile:** the v1 trivial case (always equals owning profile), forward compatibility for v2/v3

Target: at least 3 tests per dimension, 15+ total.

#### Category 4: Active-set computation

The hot-path query that runs on every Chef invocation. The suite verifies:

- The full graph filters correctly to the active subset given a moment and context
- AND composition across dimensions (a constraint with both temporal and contextual scope is only active when both match)
- The cache correctly returns the same result for repeated identical queries
- Cache invalidation on graph writes
- Cache invalidation on contextual state changes
- Lazy invalidation on temporal scope boundaries (no proactive recomputation, but next query reflects the new state)
- Correct attribution metadata (which scope dimensions activated/deactivated each constraint)

Target: 10+ tests covering the cache invalidation cases specifically, since they are the hottest of the hot paths.

#### Category 5: The validator's deterministic algorithm

The mechanical contract from `constraint-engine-spec.md` Section 7. This category is the largest in the suite and gets near-100% coverage per the risk-tiered strategy. The suite verifies the validator's five-step algorithm:

- **Step 1 (active set computation):** correct active set for varied profile states
- **Step 2 (Food Data Provider resolution):** every entry resolves to high-confidence categories from the canonical fixtures; unknown ingredients trigger Step 4
- **Step 3 (per-constraint check):** each of the five constraint types is checked correctly:
  - `avoid` matches catch literal and categorical violations, respect exceptions
  - `require` violations correctly identify missing requirements
  - `limit` violations correctly aggregate nutritional properties across ingredients
  - `prefer` misalignments produce disclosure notes
  - `contextual` violations from `incompatible_with` are detected
- **Step 4 (severity-scoped unknown ingredient handling):** Inviolable rejects, Medical/Religious AI-fallback with disclosure, Preference AI-fallback silent
- **Step 5 (aggregation):** correct verdict structure with violations and disclosure notes

Target: 30+ tests, with explicit coverage of every branch in the algorithm. This is the test suite the founder reviews most carefully because of Principle 1.

#### Category 6: The conflict resolution algorithm

The mechanical contract from `constraint-engine-spec.md` Section 8. The suite verifies the four-step sequence:

- **Step 1 (failure pattern analysis):** correct identification of single-constraint vs multi-constraint conflicts
- **Step 2 (silent preference relaxation):** preferences are tried first, never higher tiers
- **Step 3 (surfacing to user):** correct option construction, no inviolable in the options, calm message format
- **Step 4 (recording user choice):** session-scoped relaxation, no permanent graph modification

Plus the named edge cases from Section 8: contextual-constraint-blocking, no-active-preferences, household conflicts (v2), repeat-relaxation patterns (does not silently graduate to permanent removal).

Target: 15+ tests covering each step and each edge case.

#### Category 7: Provenance and audit

The integrity of the provenance metadata across all constraint operations. The suite verifies:

- Every `addConstraint` populates all required provenance fields
- The original phrasing is preserved verbatim when the user provides it
- The modification history is append-only and correctly captures every change
- Provenance survives sync round-trips
- The disclosure flow can read provenance and produce honest explanations
- Soft-deleted constraints retain their provenance for audit purposes

Target: 10+ tests covering each provenance field and the modification history specifically.

### How the hard cases suite is maintained

The hard cases suite is **append-only by convention**. Tests are added when new edge cases surface during development, alpha feedback, or constraint-engine refinement. Tests are not removed even when the underlying behavior changes — instead, the test is updated to verify the new behavior, and the old behavior's test is preserved as a regression guard with a comment explaining the change.

The suite is reviewed as a whole at least once per quarter during alpha to identify gaps. The review asks: are there scenarios from real alpha usage that we're not testing? Are there severity tier interactions we haven't exercised? Are there scope compositions we haven't tried? The review produces a list of new tests to write, which become part of the next sprint's work.

### What the hard cases suite does NOT cover

- **LLM agent quality.** The hard cases suite tests the constraint engine, the validator, and the conflict resolver — all deterministic code. Agent prompt quality is tested separately via the per-agent eval harness in Section 6.
- **UI integration.** The hard cases suite tests the engine in isolation. UI-engine integration is tested in the integration test layer.
- **Performance.** The hard cases suite tests correctness, not performance. Performance is tested separately in `test/performance/`.

## 6. Per-agent eval harness

> Cuizine's agents are LLM-backed, which means their behavior is partially non-deterministic and partially shaped by prompts that evolve faster than code. The eval harness is how Cuizine knows that prompt changes don't break previously-working behaviors and that agent quality is acceptable on representative scenarios. Per the answers to the upfront questions, the harness uses a hybrid approach: automated regression for safety-floor deterministic cases, manual review for subjective behaviors.

### The hybrid model

The hybrid model recognizes that agent behaviors fall into two categories:

**Deterministic safety-floor behaviors** are cases where the right answer is unambiguous and can be checked programmatically. Examples:
- Did the Curator correctly classify a religious observance as Religious & Cultural rather than Preference?
- Did the Chef avoid suggesting a recipe with peanuts to a peanut-allergic user?
- Did the Curator correctly extract an ingredient from "I can't have onions or garlic" rather than misinterpreting it?
- Did the orchestrator route a `free_text_update` intent to the Curator and not to the Chef?

These are tested via **automated regression evals** with structured scoring — the eval harness runs the agent against fixed inputs, checks the structured output against the expected value, and fails the test on mismatch.

**Subjective quality behaviors** are cases where the right answer is fuzzy and requires human judgment. Examples:
- Did the Chef suggest a culturally appropriate meal for Sukhi's Punjabi context?
- Was the conflict resolution message warm and calm, or did it sound clinical?
- Was the rejection feedback prompt non-judgmental?
- Did the Curator's plain-language explanation honor the user's original phrasing?

These are tested via **manual review** — the founder reads representative outputs after each prompt change and judges quality directly. The manual review is structured (a small checklist of dimensions to evaluate) but it is not automated.

### The automated eval set (v1 size: ~50-100 scenarios)

Each automated eval scenario has this structure:

```kotlin
data class AgentEvalScenario(
    val id: String,
    val description: String,
    val targetAgent: Agent,
    val input: Any,                    // typed input matching the agent's contract
    val expected: ExpectedBehavior,    // the structured assertion to check
    val tags: List<String>,            // 'severity_zero', 'severity_one', 'cultural_fluency', etc.
)
```

The `ExpectedBehavior` is the structured assertion. Examples:

- "The Curator output's `constraint_operations` contains exactly one operation, of type `addConstraint`, with severity Medical."
- "The Chef output's `ingredients` array does NOT contain any ingredient resolving to category `peanut`."
- "The orchestrator's intent routing returned `Curator` agent for the input."

The harness runs each scenario by invoking the agent with the input (using a real LLM call or a deterministic mock depending on the scenario), parses the structured output, and runs the assertion. Pass or fail.

The initial automated set covers (target counts; grow over time):

- **Safety floor cases (highest priority):** ~20 scenarios covering peanut/tree nut/sesame allergen detection, religious dietary violation detection, inviolable constraint enforcement, the validator-rejecting-unknown-in-inviolable case
- **Conflict resolution correctness:** ~10 scenarios covering the four-step algorithm with various conflict shapes
- **Curator classification correctness:** ~10 scenarios covering severity tier classification, constraint type classification, contextual vs permanent constraint distinction
- **Curator parsing correctness:** ~10 scenarios covering ingredient extraction, scope extraction, household scope assignment
- **Orchestrator routing correctness:** ~5 scenarios covering each intent type
- **Validator behavior:** integrated with the hard cases suite from Section 5

Target: ~50-100 scenarios at v1 alpha kickoff, growing to ~200-300 by v2 launch.

### The manual review process

Manual review happens after every meaningful prompt change (per `build-conventions.md` Section 8). The process:

1. The agent or founder identifies the prompt change being reviewed
2. The agent generates outputs for a representative set of scenarios — the canonical Sukhi day 0 conversation, a culturally-specific meal request, a free-text contextual update, a rejection feedback flow, a conflict resolution interaction
3. The founder reads the outputs alongside the previous version's outputs (when available)
4. The founder judges the change against a small structured checklist:
   - **Tone:** does it match the calm-precise-warm voice from `vision.md`?
   - **Cultural fluency:** is it respectful and accurate to the user's cultural context?
   - **Honesty:** does it acknowledge uncertainty when present?
   - **Clarity:** would Sukhi understand it without explanation?
   - **Brevity:** is it concise without being terse?
5. The founder decides: ship the change, refine the prompt, or revert
6. The decision is recorded in the prompt's change history file

The manual review is deliberately not automated because the dimensions being judged are inherently subjective. Trying to score "tone" or "cultural fluency" programmatically produces brittle metrics that don't reflect real quality.

### The eval harness infrastructure

The harness is implemented as Kotlin code in `test/agents/eval_harness/`. It supports:

- Running individual scenarios against any agent
- Running the full automated set as a regression check (returns pass/fail/diff against the expected behaviors)
- Running scenarios against multiple LLM providers in parallel for comparison (used during model selection per ADR 0006)
- Mocking LLM responses for deterministic testing (used in CI where real LLM calls are too expensive and slow)
- Outputting eval results in a structured format that can be reviewed in alpha event log exports

The harness uses real LLM calls during the scheduled slow-test runs (Section 3 Layer 4) and mock responses during the per-commit fast-test runs.

## 7. The validator's exhaustive test suite

> The validator from `constraint-engine-spec.md` Section 7 is the safety floor. It gets near-100% coverage per the risk-tiered strategy. This section specifies what "exhaustive" means in practice for the validator's test suite, going beyond the hard cases suite from Section 5 with edge case enumeration and property-based testing.

### Why the validator deserves its own section

The validator is small enough that exhaustive testing is achievable, and it is consequential enough that exhaustive testing is required. A bug in the validator can let a peanut through to a peanut-allergic user, which is real harm. The asymmetry — small surface, high consequence — means the validator should be tested more rigorously than any other piece of code in Cuizine.

### The exhaustive test plan

**Per-step coverage:** every branch of the validator's five-step algorithm has tests. For each branch, the test enumerates the inputs that trigger it, the expected outputs, and the expected side effects (event log entries, regeneration triggers, etc.). The branches are:

- Step 1: empty active set, single-constraint active set, multi-constraint active set, contextual-modified active set, multi-profile active set (v2)
- Step 2: all-cached resolution, all-bundle resolution, mixed cache+bundle+external, single unknown ingredient, multiple unknown ingredients
- Step 3 per type: every constraint type's check, every payload variation, every exception structure
- Step 4 per severity: Inviolable+unknown, Medical+unknown+safe, Medical+unknown+unsafe, Religious+unknown+safe, Religious+unknown+unsafe, Preference+unknown+safe, Preference+unknown+unsafe
- Step 5: all-pass aggregation, mixed pass-and-fail, all-fail aggregation, disclosure note propagation

**Property-based testing for the high-volume cases:** for the active-set computation and the per-constraint check, Cuizine uses property-based testing (via the `glados` package or equivalent) to generate random valid constraint graphs and random valid suggestions, and verify that the validator's output is internally consistent. Property-based testing catches edge cases that hand-written tests miss because it explores the input space systematically rather than relying on the test author's imagination.

**Allergen exhaustive testing:** for each of the top common allergens (peanut, tree nut, dairy, egg, wheat, soy, fish, shellfish, sesame), the suite includes at least 5 tests covering: literal ingredient match, categorical match, hidden-in-product match (via Open Food Facts ingredient parsing), the AI-fallback rejection case, and the user-confirmed safe exception. That's 45+ tests for allergens alone, which is appropriate given the consequence of missing one.

**Religious dietary exhaustive testing:** for each of the major religious dietary categories (halal, kosher, Hindu vegetarian, Jain, Buddhist), the suite includes tests covering: forbidden ingredient detection, contextual modification (e.g., Ramadan fasting hours), the conflict resolution behavior when multiple religious constraints interact. That's 15+ tests for religious dietary cases.

**Quantitative limit boundary testing:** for `limit` constraints, the suite tests the boundary cases — exactly at the ceiling, one unit below the ceiling, one unit above the ceiling, soft vs hard enforcement at each boundary. That's 12+ tests just for the boundary behavior.

### Total target test count for the validator

Combining the categories above with the hard cases suite from Section 5, the validator's total test count at v1 alpha kickoff targets ~150-200 tests. This is a significant investment but it is the right investment given the validator's role as the safety floor.

Coverage target: **near-100% line coverage on the validator's code**, with explicit justification required for any uncovered line. Uncovered lines must be either dead code (delete it), unreachable error handling (document why it's unreachable), or genuinely impossible to test (extremely rare; document the case).

### What the validator's test suite does NOT include

- **Performance tests for the validator.** Validation latency is exercised in the performance test layer (Section 11), not the validator's unit test suite.
- **End-to-end UI tests that involve validation.** Those are integration tests, separately maintained.
- **Tests that verify validator behavior on malformed inputs.** Malformed inputs are caught at the orchestrator boundary before reaching the validator; the validator's contract assumes valid inputs and tests verify that contract.

## 8. Sync layer tests

> The sync layer is part of the trust posture from `local-first-sync.md`, and per the risk-tiered coverage strategy from Section 2 Principle 5, it gets near-100% coverage on the encryption code. This section specifies what that means concretely: the encryption round-trip tests, the recovery flow tests, the transition flow tests with simulated failures, the conflict resolution tests, and the failure mode tests that exercise the rollback semantics from `local-first-sync.md` Section 7.

### Encryption round-trip tests

The most important sync layer tests because they verify that data encrypted on one device can be decrypted on another. Test cases:

- **Basic round-trip:** generate a passphrase, derive a key, encrypt a structured `DecryptedSyncContainer`, decrypt it, verify the result is byte-identical to the input. Run this against representative payloads (empty container, small container with one profile and three constraints, large container with hundreds of suggestions).
- **Wrong passphrase fails closed:** generate a passphrase, encrypt a payload, attempt to decrypt with a different passphrase. Verify the decryption fails with a clear error rather than silently returning corrupted data. The GCM authentication tag is what makes this safe; the test verifies the tag is honored.
- **Tampered ciphertext fails closed:** encrypt a payload, modify a single byte in the ciphertext, attempt to decrypt. Verify the GCM authentication catches the tampering and the decryption fails.
- **Wrong salt fails closed:** encrypt a payload, attempt to decrypt with a different salt (which produces a different derived key). Verify failure.
- **Version byte handling:** encrypt with version 1, attempt to decrypt with code expecting version 1 (passes) and version 2 (fails with clear "unknown version" error).
- **IV uniqueness:** encrypt the same payload twice with the same key and verify the resulting ciphertexts differ (because IVs are random per encryption). This catches the catastrophic bug of accidentally reusing IVs.
- **Argon2id parameter consistency:** derive a key with the v1 parameters, derive again with the same inputs, verify the keys match. Then derive with slightly different parameters and verify the keys differ.

Target: 15+ encryption round-trip tests. Coverage target: 100% of the encryption module.

### Recovery passphrase tests

Tests for the passphrase generation, presentation, and recovery flow from `local-first-sync.md` Section 4.

- **Generation produces 6 words from the wordlist:** every passphrase generation produces exactly 6 space-separated words, each from the canonical Diceware-style wordlist. Run this 1000 times in a loop and verify uniqueness across runs (catches the bug of accidentally producing identical passphrases).
- **Generation uses the platform CSPRNG:** verify the generation calls `Random.secure()` and not the predictable `Random()`. Verifiable through code inspection but also through statistical tests on a large sample.
- **Confirmation step accepts correct re-entry:** the confirmation flow accepts a re-entered passphrase that matches the generated one.
- **Confirmation step rejects incorrect re-entry:** verify that mistyped passphrases are rejected with a clear error.
- **Pick-three-of-six confirmation works:** when the confirmation uses the random-three-of-six variant, picking the right three words succeeds and picking wrong ones fails.
- **Recovery on a new device with correct passphrase:** simulate a fresh device, use the recovery flow with the right passphrase against a previously-encrypted blob, verify the data is restored intact.
- **Recovery with wrong passphrase fails gracefully:** the user gets the calm error message, not a crash. After 3 failed attempts in a session, the 30-second pause kicks in.
- **The "start fresh" option works:** after passphrase failure, choosing "start fresh" creates a new local profile without touching the existing Firestore blob.

Target: 12+ recovery flow tests.

### Transition flow tests

The signed-out to signed-in transition from `local-first-sync.md` Section 7 is the most consequential single flow in the sync layer. Its rollback semantics need exhaustive testing.

The discipline: every step of the ten-step transition gets a test that simulates failure at that step and verifies the rollback restores the previous state cleanly.

- **Step 1 failure (Firebase auth fails):** the user remains signed-out, no Firestore document exists, no local data is touched.
- **Step 2 failure (passphrase confirmation dismissed):** Firebase is signed out, no Firestore document, no data touched.
- **Step 3 failure (Firestore document creation fails):** Firebase is signed out, passphrase dropped, local state unchanged.
- **Step 4 failure (key derivation fails):** Firestore document is deleted, Firebase signed out, local state unchanged.
- **Step 5 failure (database snapshot fails):** Firestore document deleted, Firebase signed out.
- **Step 6 failure (encryption fails):** same rollback.
- **Step 7 failure (Firestore upload fails):** Firestore document deleted, Firebase signed out, local data unchanged. **This is the most important rollback case** because it's the boundary between "transition is in progress" and "transition is complete."
- **Step 8 failure (local accounts table update fails):** the local data is unchanged but the Firestore upload succeeded. The test verifies the next launch detects this half-state and recovers correctly.
- **Successful transition end-to-end:** all 10 steps complete, the user sees the success confirmation, the data is correctly synced.

Plus the **mid-transition app crash** scenario:

- **Crash between Steps 1 and 7:** simulated by killing the test process mid-transition. Verify the next launch's recovery logic correctly identifies the half-state, signs out of Firebase, deletes the partial Firestore document, returns to signed-out state with all local data intact.

The same exhaustive treatment applies to:

- **The signed-in to signed-out transition** from `local-first-sync.md` Section 8 (simpler but still tested per step)
- **The fresh-start vs restore branch** when signing in on a device with existing local data and an existing Firestore blob
- **The passphrase change flow** from `local-first-sync.md` Section 4

Target: 30+ transition flow tests covering all the failure modes.

### Multi-device conflict resolution tests

Per `local-first-sync.md` Section 6, the conflict resolution policy is last-write-wins per row with append-only modification history merging on the constraints table. The tests verify each case from Section 6.

- **Both devices online, no conflict:** the more recent write wins by `modified_at`.
- **Both devices offline, then online with non-conflicting changes:** both changes are merged.
- **Both devices offline, then online with conflicting structural changes on the same constraint:** the more recent `modified_at` wins for the structural fields, and both modification history entries are preserved in the merged provenance.
- **Both devices offline, then online with the same constraint deleted on one and modified on the other:** the deletion wins if its `modified_at` is more recent, the modification wins if its `modified_at` is more recent.
- **Concurrent constraint creation with the same primary key (defensive case):** the merge surfaces the conflict to the user and pauses sync per Section 6's "deeper exception."
- **Tombstone propagation:** a soft-deleted constraint on one device propagates as deleted to other devices.
- **The CRDT revisit criteria check:** the test suite logs every conflict event so the alpha review can measure whether the revisit criteria from Section 6 are being triggered.

Target: 15+ conflict resolution tests.

### Sync protocol tests

Tests for the three-phase sync lifecycle from `local-first-sync.md` Section 5.

- **Pull phase with no remote changes:** local data is unchanged.
- **Pull phase with remote changes:** changes are downloaded, decrypted, parsed, and merged correctly.
- **Pull phase with decryption failure:** the pull aborts and surfaces an alert.
- **Merge phase with various conflict shapes:** covered by the conflict resolution tests above.
- **Push phase with no local changes:** no upload happens.
- **Push phase with local changes:** changes are serialized, encrypted, uploaded.
- **Push phase with optimistic concurrency conflict:** another device wrote to Firestore between the pull and the push, the cycle restarts from pull.
- **Sync triggered by various conditions:** app foreground after 5+ minutes, user-initiated write, periodic background, manual pull-to-refresh.
- **Sync paused conditions:** device offline, low-power mode, in the middle of constraint conversation.
- **Retry policy:** exponential backoff with bounded retries, retry budget reset on user action.

Target: 20+ sync protocol tests.

### Chunking tests

Per `local-first-sync.md` Section 9, the encrypted blob is chunked across multiple Firestore documents when it exceeds ~750 KiB. The chunking logic ships in v1 even though most alpha users won't trigger it.

- **Single-document blob (under threshold):** the blob is uploaded as a single document, no chunking.
- **Multi-document blob (over threshold):** the blob is split into ~750 KiB chunks, each uploaded to a `blob_chunks` subcollection, the parent document holds metadata.
- **Chunk integrity:** the parent document's content hash matches the assembled chunks.
- **Chunk pull and reassembly:** chunks are downloaded in order, reassembled, and decrypted as a single blob.
- **Partial chunk upload failure:** if upload of one chunk fails partway through, the next sync cycle retries from the failed chunk rather than starting over.
- **Boundary case:** a blob exactly at the chunking threshold doesn't oscillate between single-document and chunked formats across sync cycles.

Target: 8+ chunking tests.

## 9. Schema migration tests

> Per `data-model.md` Section 8, the migrator module exists in v1 with an empty registry that's exercised by tests. This section specifies the tests that exercise the empty-registry pattern and the migration discipline.

### The empty-registry tests (v1 ship)

These tests must exist at v1 ship even though there are no real migrations yet. They exercise the migrator's code path so that the *first* real migration (v1 → v2) is a new entry in an existing system rather than a new system.

- **`test_migrator_no_op_when_versions_match`:** invoking `migrate(db, 1, 1)` is a no-op and does not modify the database.
- **`test_migrator_throws_on_backward_migration`:** invoking `migrate(db, 2, 1)` throws an exception with a clear "user must update app" error message. This protects against the case where a user downgrades the app version unexpectedly.
- **`test_migrator_throws_on_missing_migration_step`:** invoking `migrate(db, 1, 3)` when no v1→v2 migration exists throws with a clear "no migration path" error.
- **`test_migrator_runs_single_step_migration`:** uses a synthetic test-only migration from v1 to v2 that is registered only in the test scope, runs `migrate(db, 1, 2)`, verifies the migration ran, the database is updated, and the global `schema_metadata.current_version` is now 2.
- **`test_migrator_chains_multi_step_migrations`:** uses synthetic test-only migrations for v1→v2 and v2→v3, runs `migrate(db, 1, 3)`, verifies both migrations ran in order and the final version is 3.
- **`test_migrator_rolls_back_on_step_failure`:** uses a synthetic migration that throws partway through, verifies the database state is restored to the pre-migration state and the version is unchanged.
- **`test_migrator_idempotency`:** running the same migration twice is a no-op the second time, not an error.

Target: 7+ migrator tests, all of which must pass at v1 ship.

### Real migration tests (added as migrations are written)

When the first real v1→v2 migration is written, it gets its own test that:

- Verifies the migration runs correctly on a representative v1 database
- Verifies the migration is idempotent (running it twice produces the same result as running it once)
- Verifies the migration handles edge cases (empty database, database with only profiles and no constraints, etc.)
- Verifies the migration logs to the event log per `data-model.md` Section 8

This pattern repeats for every subsequent migration. The test count grows monotonically as migrations are added.

### Schema integrity tests

Tests that verify the data model's invariants are preserved across migrations.

- **CHECK constraint enforcement:** verify that constraint type values, severity values, etc. are restricted to the enumerated sets and invalid values are rejected at the database level.
- **Foreign key enforcement:** verify that constraints can't reference non-existent profiles, that suggestions can't reference non-existent profiles, etc.
- **Soft-delete preservation:** verify that rows with `removed_at` set are still readable and that their data is preserved across migrations.
- **JSON column shape validation:** verify that JSON payloads conform to their type-specific schemas (the discriminated unions for the five constraint types, etc.).

Target: 10+ schema integrity tests.

## 10. Integration tests for the Sukhi user journey

> The end-to-end tests that cover the user journeys from PRD § 4. These run as Jetpack Compose UI tests (on Robolectric where possible) with the agent layer mocked (so the tests are deterministic) and a real local SQLite database via Room. They are the most expensive tests in the suite per test, and there are deliberately few of them.

### The canonical journeys

Each journey is a single integration test that simulates the entire flow and verifies the cumulative state at the end. The journeys mirror PRD § 4's walkthroughs.

**`test/integration/journeys/sukhi_day_0_onboarding.kt`** — Sukhi opens Cuizine for the first time. The test:

1. Launches the app in the signed-out state
2. Walks through the constraint conversation by simulating user input and verifying the Curator's responses
3. Verifies the final constraint graph contains the expected constraints with the expected severity tiers and provenance
4. Verifies the cultural context and cooking-for fields are populated
5. Verifies the user is asked to confirm before the conversation ends

**`test/integration/journeys/sukhi_day_4_first_meal_suggestion.kt`** — Sukhi asks for her first meal suggestion four days after onboarding. The test:

1. Loads the canonical Sukhi profile from fixtures
2. Triggers a meal suggestion request
3. Verifies the Chef is invoked with the correct input
4. Verifies the validator runs on the Chef's output
5. Verifies the suggestion is returned to the user without violations
6. Verifies the suggestion is logged to the event log with full metadata

**`test/integration/journeys/sukhi_day_5_free_text_update.kt`** — Sukhi reports that her sugar was 9.2 that morning. The test:

1. Starts from Sukhi's day 4 state
2. Submits the free-text update via the suggestion surface
3. Verifies the Curator is invoked, recognizes the contextual signal, and creates a `setContextualState` operation
4. Verifies the contextual constraint enters the graph with the correct expiration
5. Verifies the next meal suggestion's active set includes the contextual constraint
6. Verifies the Chef's subsequent suggestion respects the elevated blood sugar context

**`test/integration/journeys/sukhi_day_8_rejection_feedback.kt`** — Sukhi rejects a suggestion because she doesn't have the ingredients. The test:

1. Starts with a generated suggestion
2. Submits the rejection with the "don't have the ingredients" reason
3. Verifies the Curator is invoked to record the contextual state
4. Verifies the Chef is re-invoked with the updated state
5. Verifies the new suggestion does not contain the unavailable ingredients
6. Verifies both the rejected and the accepted suggestions are logged

**`test/integration/journeys/sukhi_day_30_cumulative_state.kt`** — A check on Sukhi's accumulated state after 30 days of usage. The test:

1. Loads a fixture representing 30 days of accumulated activity (constraints, contextual states, suggestions, cooked meals)
2. Verifies the active-set computation is fast (sub-100ms)
3. Verifies the food data cache has accumulated the user's vocabulary
4. Verifies the event log contains the expected event types in the expected proportions
5. Verifies a fresh meal suggestion completes quickly and respects the full constraint graph

### What the integration tests verify together

Beyond the per-journey assertions, the integration tests collectively verify:

- The orchestrator correctly routes intents through the agent layer
- The validator runs on every Chef output (no path bypasses it)
- The constraint engine API operations return the expected results
- The data model's tables are correctly populated
- The event log captures every relevant event
- The UI layer correctly displays the agent outputs (verified through Compose UI tests)

### What integration tests deliberately mock

- **The actual LLM provider calls.** Mocked at the `ModelProvider` boundary to return canned responses. This is what makes integration tests deterministic and fast.
- **Firestore interactions.** Mocked at the sync layer boundary. The actual Firestore interaction is tested separately in the sync layer tests (Section 8) using the Firebase emulator.
- **Time.** Tests use a controllable clock so that scenarios involving "yesterday," "tomorrow," "30 days from now" are deterministic.

Target: ~15-20 integration tests at v1 alpha kickoff, growing to ~40-50 by v2 launch.

## 11. Performance, load, and resource tests

> The tests that verify Cuizine performs adequately on real devices. These run on a scheduled cadence (not every commit) and pre-release, and they are the layer that would catch a regression in the form of "this used to be fast and now it's slow."

### Performance budgets

Cuizine has explicit performance budgets that the test suite enforces:

- **App cold start:** under 2 seconds on a typical mid-range Android phone (e.g., a 2024 Samsung A-series device)
- **Constraint conversation turn latency:** under 1.5 seconds from user input to next agent response (most of this is LLM latency, which is mocked in tests; the test verifies the framework overhead is under 200ms)
- **Active-set computation:** under 50ms for a profile with 100 constraints
- **Food data cache lookup:** under 10ms for cache hits, under 100ms including USDA round-trip on cache miss (the USDA call is mocked with realistic latency)
- **Encryption derivation (Argon2id):** between 500ms and 1 second on representative hardware (this is the deliberate slowness from `local-first-sync.md` Section 3)
- **Sync round-trip:** under 3 seconds for a typical user's encrypted blob (under 100 KiB) on a fast network
- **Validator runtime:** under 50ms per suggestion check, including Food Data Provider lookups
- **Database query (single profile load):** under 20ms

Each budget is a test that fails if exceeded. Budgets are revisited as device capabilities improve.

### Load tests

- **Constraint graph at scale:** verify the engine handles a profile with 1000 constraints without significant performance degradation. Real users will not have 1000 constraints, but verifying the engine doesn't degrade dramatically catches the failure mode of unintentional O(n²) algorithms.
- **Suggestion history at scale:** verify the database handles 10,000 suggestions per profile (covering several years of heavy use) with the indices from `data-model.md` Section 10.
- **Food data cache at scale:** verify the cache handles 5,000 entries (a heavy long-term user) with consistent lookup performance.
- **Event log at scale:** verify the event log handles 100,000 entries (about a year of heavy use) without query degradation.

### Resource tests

- **Memory usage at idle:** under 150 MB with the app foregrounded but inactive
- **Memory usage during active use:** under 250 MB during meal suggestion generation
- **Battery usage during periodic sync:** measured against a baseline; sync overhead should be under 2% of device daily battery
- **Disk usage:** under 100 MB for a typical year of accumulated data (food data cache, suggestions, history)
- **Network usage during typical day:** under 1 MB of data transferred for a user with 5-10 sync events

### Low-end device verification

Specific tests run on low-end Android device profiles (representing the lower bound of devices Cuizine alpha users might have):

- A 2022 budget Android phone simulator with 3 GB RAM and a slow CPU
- Verification that all performance budgets are still met (with slightly relaxed thresholds — cold start under 4 seconds rather than 2)
- Verification that no out-of-memory errors occur during normal use

## 12. What is not tested

> Per the discipline pattern from other docs, an explicit list of things deliberately not tested with rationale. This list exists to prevent the failure mode of "we should test everything, even when testing it adds no value." Each item is named here because it is a thing that might be tempting to test, with the explicit reason why we don't.

- **Trivial data class accessors.** A class with three fields and three getters does not need three tests. The accessors are exercised incidentally by every test that uses the class.
- **The Android framework and Jetpack libraries themselves.** Cuizine does not test that Compose recomposition works, that `StateFlow` emits correctly, or that ViewModel lifecycle behaves. These are tested by Google.
- **The Room query layer.** Room's generated DAO query code is not Cuizine's responsibility to test; we trust Room's own test suite. (Cuizine does test its own queries' *correctness of intent* through the data-layer tests, but not Room's code generation.)
- **Third-party API correctness.** Cuizine does not verify that USDA returns correct nutritional data or that Open Food Facts returns correct ingredient lists. We trust those services to be correct and we test our handling of their responses (including edge cases like malformed responses, but not the responses themselves).
- **LLM provider model quality.** Cuizine does not score or evaluate the underlying quality of Claude Sonnet vs Gemini Pro vs GPT-4. We test that our routing and prompt structures produce acceptable outputs given a model, not that the models themselves are good.
- **Network reliability.** Cuizine assumes the network is unreliable and tests its handling of failures (per Section 8). We do not test that the network "works" in any positive sense.
- **Google Play in-app purchase backend.** We test our `BillingService` wrapper's handling of events from the Google Play Billing Library, but we do not test Google Play's own subscription logic, receipt format, or refund processing.
- **Firebase Authentication's backend.** We test our handling of auth events but not Firebase's own auth flow.
- **The OS-level encryption (Android Keystore).** We rely on the platform; we do not test that AES-256 is implemented correctly by the OS.
- **The recovery wordlist's randomness.** We use the platform CSPRNG; we do not run statistical tests on the wordlist generation beyond verifying it produces 6 distinct words from the list.
- **CI environment correctness.** We do not test that GitHub Actions or whatever CI we use runs the tests correctly. CI failures are debugged manually.
- **Performance on platforms we don't support.** Cuizine is Android-only (ADR 0015). We do not test on iOS, Linux, Windows, or other platforms — they are out of scope across all versions. The supported target is Android phones, including the low-end device floor specified in the performance budgets.
- **Behaviors not specified by foundation docs.** If a behavior isn't in the foundation, we don't write speculative tests for "what the user might want." We test what the docs say.
- **Stress tests for impossible scenarios.** We do not test "what if the user has 1 million constraints" because that's not a realistic scenario and testing it would distort the engine to handle a case we'll never see.
- **Negative tests for forbidden behaviors.** The forbidden behaviors lists from `local-first-sync.md`, `monetization-and-billing.md`, and `build-conventions.md` are enforced by code review and by the agent decision protocol, not by tests that verify "we did not implement this forbidden thing." The absence of forbidden code is the test.

## 13. Open questions and cross-references

### Open questions

#### Eval harness

- **What is the right scoring rubric for the automated agent eval scenarios?** Section 6 specifies structured assertions for deterministic behaviors, but the exact assertion format (exact match, fuzzy match with similarity threshold, semantic equivalence) is unspecified for the cases where the agent might produce equivalent-but-different outputs. Probably exact-match for the safety-floor cases and fuzzy-match for the borderline cases, but the line is fuzzy. Refine during alpha based on real eval results.
- **How often should the full eval suite run?** The automated eval suite uses real LLM calls, which costs money and takes minutes. The right cadence depends on build activity — more frequent during active prompt iteration, less frequent during stable periods. Rhythm set by the founder in CI configuration.
- **Should the eval harness output be reviewed by the founder on a fixed cadence or only when a regression is detected?** Probably regular review (rhythm driven by eval run frequency) so the founder builds intuition for the eval results, plus immediately on regression.

#### Test fixtures

- **Should the canonical Sukhi profile evolve over time as we learn more about realistic constraint graphs from alpha users?** Yes, but with discipline — fixture changes need their own commit and rationale, and the prior version of the fixture stays available in case any tests depend on the specific shape of the old version.
- **Should we have fixtures for adversarial inputs?** A user who deliberately enters confusing or contradictory information to stress-test the Curator's handling. Probably yes, in `fixtures/adversarial/`, used by a small set of tests. Deferred to alpha if real adversarial cases surface.

#### Performance

- **What's the right response when a performance budget is exceeded?** Currently the test fails. But some failures are environmental (slow CI runner) and should not block commits. Probably: fail loudly but require a manual override to land (with a comment explaining why the budget was exceeded acceptably).
- **Should we measure tail latency (p95, p99) in addition to median?** For interactive operations like the constraint conversation, p99 matters as much as median. Tentatively yes; deferred to implementation.

#### Coverage

- **What's the right way to enforce the near-100% coverage target on the validator and encryption modules?** Code coverage tools like JaCoCo (or Kover for Kotlin) can report line and branch coverage, but enforcing it in CI requires deciding what to do with covered-but-trivial lines. Probably: report coverage in CI, fail the build if it drops below 95% on the high-risk modules, allow human override with rationale for the rare uncovered lines.
- **Should branch coverage be measured separately from line coverage?** Branch coverage is more rigorous but more expensive to compute. Probably yes for the validator specifically, no for the rest of the codebase.

#### Integration tests

- **Should the integration tests exercise the actual UI layer or just the data and engine layers?** Section 10 specifies Compose UI tests with mocked agents. The alternative is integration tests that bypass the UI and just test the data flow. Both have value; tentatively the Compose UI test approach because it catches UI-layer bugs that data-only tests would miss.
- **What's the right cadence for adding new integration tests?** Probably one new integration test per significant feature, plus regression tests when bugs surface. Not "one per commit."

#### v2 and v3

- **How does the test suite evolve when v2 introduces new agents (Planner, Sourcing) and new features (multi-profile households)?** The hard cases suite grows additively. The integration tests grow with new journeys (Marcus's caregiver flow, Aisha's Ramadan flow). The performance tests gain new budgets for the Planner's longer reasoning. Specified during v2 build.
- **Will v3's BYOK case need its own test suite?** Yes, but the tests are mostly variants of the existing sync layer tests with different routing assumptions. Specified during v3 build.

### Cross-references

#### What this document references

- `vision.md` — for the trust posture, the safety commitments, the calm-precise-warm voice
- `PRD.md` — for Sukhi's user journey, the secondary personas, the v1 feature scope, the alpha process
- `technical-architecture.md` — for the subsystem decomposition that the test suite mirrors
- `constraint-engine-spec.md` — for the foundation specs that the hard cases test suite verifies
- `agent-architecture.md` — for the agent contracts that the eval harness exercises
- `data-model.md` — for the schema, the migrator pattern, the indices
- `local-first-sync.md` — for the encryption layer, the sync protocol, the recovery flow, the conflict resolution policy
- `monetization-and-billing.md` — for the billing layer that gets near-100% coverage
- `build-conventions.md` — for the test bar discipline (Section 10) and the working principles
- ADR 0009 — severity tier model, the basis for the severity tier handling tests
- ADR 0010 — post-generation validation, the basis for the validator's exhaustive test suite
- ADR 0011 — optional backend with capability tiers, the basis for the sync layer tests
- ADR 0012 — food data sources, the basis for the food data fixture and the AI fallback tests

#### What this document defers to deeper-dive docs

- **The actual test code** lives in `test/` in the codebase, not in this doc
- **CI/CD configuration** lives in deployment documentation, not here
- **`alpha-feedback-and-iteration.md`** — the alpha process that uses event log exports and manual review of agent outputs
- **`security-and-privacy.md`** — the threat model that informs the security-focused tests in the encryption layer
- **`roadmap.md`** — the milestone-level plan that determines when new test categories are added
- **`launch-readiness-checklist.md`** — the go/no-go gates that include test suite completeness criteria

#### What this document does *not* defer (decisions made here)

- The six core testing principles (Section 2)
- The four-layer test pyramid for Cuizine (Section 3)
- The canonical fixtures (Sukhi, Aisha, Marcus, plus constraint graph and food data fixtures) (Section 4)
- The hard cases test suite organized into seven categories with target test counts (Section 5)
- The hybrid agent eval harness (automated regression for safety-floor cases, manual review for subjective behaviors) (Section 6)
- The validator's exhaustive test suite with property-based testing and near-100% line coverage (Section 7)
- The sync layer's exhaustive testing of encryption, recovery, transitions, conflicts, and chunking (Section 8)
- The schema migration tests including the empty-registry pattern (Section 9)
- The integration test journeys for Sukhi's day 0, day 4, day 5, day 8, and day 30 (Section 10)
- The performance budgets and load tests (Section 11)
- The 15-item "what is not tested" list (Section 12)

#### How the agent should use this doc

When the coding agent builds Cuizine, the test suite is built alongside the code per `build-conventions.md` Section 10. This doc specifies *what* to test; the agent decides *how* to write each individual test. Sections 5 and 7 (the constraint engine hard cases and the validator's exhaustive suite) are the agent's primary reference for the most consequential tests. Sections 6 and 8 (the agent eval harness and the sync layer tests) are the second priority. The integration tests in Section 10 are written when the relevant journeys are implemented.

The agent should not skip writing tests for behaviors specified in foundation docs, even when the test feels obvious. The discipline from `build-conventions.md` Principle 4 — "a feature without tests is a feature that doesn't work yet" — is enforced by this doc's test catalog.

When the agent encounters a test that's hard to write (because the behavior is hard to verify, the inputs are hard to construct, the outputs are hard to assert), the agent surfaces the difficulty rather than skipping the test. Hard-to-test behaviors are often hard to test because they're either underspecified in the foundation (in which case the foundation needs to be updated) or genuinely complex (in which case the test deserves more design effort).

When the agent encounters a behavior that isn't covered by any test in this doc but seems like it should be, the agent surfaces the gap. Either the doc needs to be updated to cover the case, or the case is one of the things deliberately not tested per Section 12.

---

*End of `testing-strategy.md` v1 (initial draft). Next revision will incorporate any test categories that surface during the v1 alpha build, any patterns that emerge from real test maintenance, and any decisions made during the first weeks of test writing. The principles in Section 2 are stable; the test counts and target percentages in Sections 5-11 are expected to refine as the test suite grows. The eval harness in Section 6 is the section most likely to evolve during alpha based on real prompt iteration patterns.*
