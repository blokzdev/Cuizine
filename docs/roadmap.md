# Cuizine — Roadmap

> The sequencing doc. Where the preceding foundation docs specify what Cuizine is and how it is built, this document specifies *when* things happen relative to each other, *what* depends on what, and *which* decision points shape the path from foundation-set-complete through v1 alpha through v2 public launch through v3 global expansion. It is a sequencing guide, not a schedule — dates are deliberately rough, and the roadmap refines based on real measurement from each phase. The discipline is to move with intent and not linger.

## 1. Purpose & how to read this doc

This document specifies the **version-by-version roadmap for Cuizine** from the completion of the foundation doc set through v3 global expansion. It assumes you have read all previous foundation docs and the ADRs. It is the single place where someone can understand "what happens first, what happens next, what the decision points are."

> **A note on the word "tier" in this doc.** This roadmap organizes Cuizine's lifecycle into numbered **Tiers** (Tier 1 = v1 alpha build, Tier 2 = v1 alpha period, Tier 2.5 = transition, Tier 3 = v2 launch, Tier 3.5 = v2 launch period, Tier 4 = v3 expansion). These lifecycle Tiers are entirely distinct from the **subscription tiers** in `monetization-and-billing.md` (Free, Cuizine, Cuizine Family). The numbered "Tier N" always means a lifecycle stage here; the named tiers (Free/Cuizine/Family) always mean pricing. The numbered v1 build steps *within* Tier 1 are called **Phases** (Phase 1–7, Section 3), not Tiers.

This document defines: the core planning principles that govern how the roadmap is used; the v1 alpha build plan covering the sequence from foundation-complete to alpha kickoff; the v1 alpha period covering the timeline from kickoff to v2 planning with lean exit criteria; the alpha-to-v2 transition covering the bridge work that specifically handles the transition; the v2 launch plan covering everything that ships in v2 as a full public North American launch; the v2 launch period covering the expected duration from launch to v3 planning; the v3 expansion plan covering global (geographic) expansion, new agents, and new features; the cross-tier themes covering maintenance and review cadences that span multiple versions; the dependencies and critical paths that make blocking relationships explicit; the open questions; and the cross-references.

This document does **not** define: specific calendar dates for milestones (real dates emerge from real build measurement); the actual feature content of each version (that's in the other foundation docs); the financial projections or business plan (that's a separate business document); or the marketing launch plan (that's a v2 launch preparation artifact).

**When this document and a deeper-dive doc disagree,** this doc wins for sequencing and timing, and the foundation docs win for feature content. If a sequencing decision surfaces a contradiction with a foundation doc, the contradiction is itself the signal — either the sequencing is wrong or the foundation needs updating.

## 2. Core planning principles

> The disciplines that govern how the roadmap is used. Every sequencing decision in this doc traces back to one of these principles. They are also the commitments about how Cuizine approaches version planning — not just which version ships when, but *how* the company moves through phases.

### Principle 1: Move with intent, don't linger

Cuizine's working pattern throughout the foundation docs has been "design with care, commit quickly, iterate in place." The roadmap extends this principle to version planning: each phase of the project exists to serve specific learning goals, and when those learning goals are met, the phase ends and the next one begins. Phases are not extended for comfort, for perfection, or for the aesthetic pleasure of "one more iteration before launch." The discipline is: when a phase has done its job, move on.

This principle matters most at the v1 alpha period because alphas feel safer than public launches. The alpha population is small, expectations are forgiving, the founder has direct relationships with every user. Extending the alpha "to be safe" is the most common failure mode of trust-conscious products. This roadmap actively resists that drift by specifying lean exit criteria and committing to moving to v2 when they're met.

### Principle 2: The architectural readiness pattern means versions grow features additively

Per the pattern established throughout the foundation docs (Canada-first / NA-ready, single-profile / multi-profile-ready, three-agents / six-agents-ready, local-first / sync-ready), each version of Cuizine is additive relative to the previous one. v2 does not rewrite v1's architecture; it turns on capabilities the architecture already supports. v3 does not rewrite v2's architecture; it expands the capabilities v2 already has. (Note: cross-platform readiness is no longer part of this pattern — per ADR 0015, Cuizine is Android-only across all versions and v3 expansion is geographic, not cross-platform.)

This means the roadmap is less about "when do we rebuild the system" and more about "when do we enable the next capability." The build work for v2 and v3 is meaningfully smaller than the build work for v1 because v1 lays the architectural foundation that v2 and v3 inherit.

### Principle 3: Completion gates, not calendar commitments

This roadmap specifies phases, exit criteria, and dependencies — not timelines. Phases end when their completion criteria are met, not when a calendar target says they should. The founder works at a variable pace and the architecture supports that: nothing in the phase structure assumes a particular velocity, and no sequencing decision changes based on whether a phase takes days or months.

The discipline: define what "done" means for each phase, work until done, move on. No calendar anchors, no time-box pressure, no artificial urgency. The exit criteria are the milestones, not dates.

### Principle 4: The foundation docs refine as phases progress

The foundation doc set is stable but not frozen. As v1 build surfaces gaps the foundation didn't anticipate, the foundation is updated with deliberate intent (per `build-conventions.md` Section 7's bidirectional update discipline). The roadmap names explicit review points where the foundation is expected to have grown, and names the specific kinds of updates we expect at each point. This is the mechanism that makes the foundation a living specification rather than a write-once-then-frozen document.

### Principle 5: Every phase has a defined exit

Phases end when their exit criteria are met, not when the calendar says so. The roadmap specifies exit criteria for each phase in plain language, sized appropriately (small for the alpha period, larger for the v2 launch period, forward-looking for v3 expansion). Exit criteria are the alternative to extending phases for comfort — they create an objective signal that "it is time to move on" that the founder can use to resist the lingering impulse.

### Principle 6: Premade decisions get resurfaced at the moment they're knowable

The foundation locks many decisions "now, in the abstract" that the ADRs themselves admit are judgment calls, guesses, or contingent on data we don't have yet — the validator's three-retry bound (ADR 0010, "a parameter, not a principle"), the inferred-pantry heuristic (PRD open question, "ship the simple version"), per-agent provider routing (ADR 0006, "the v1 default, revisable based on eval data"), last-write-wins conflict (`local-first-sync.md` Section 6, deliberate simplification), the Argon2id 500ms-1s calibration, the full-public-v2-launch sequencing call, and others. The risk is that a list of "revisit later" notes that lives only in someone's memory never actually gets revisited. The structural fix: each phase in this roadmap carries a **Decision checkpoints** subsection naming the specific decisions to re-confirm or challenge at *that* phase, with a tag for what input answers each one:

- **[data-driven]** — the deciding input is your own usage, telemetry, or build experience. Research is explicitly *not* a substitute; a generic recommendation must not crowd out the empirical signal you're collecting.
- **[research-informed]** — the deciding input is external (current best practice, library or API state, platform/standards changes since the foundation was written). The coding agent is encouraged to research before proposing; the foundation's stack snapshot will quietly age and the web is where currency lives.
- **[both]** — research informs the *range* (e.g., OWASP guidance on Argon2id parameters); your data picks the *point* within it (calibration on actual low-end devices).

Three rules govern what happens at a checkpoint, and they apply equally to data-driven and research-informed cases:

1. **Research informs the proposal; it does not authorize the change.** The agent surfaces the checkpoint, optionally researches, presents what it found with a recommendation, and the founder decides. Newly-found "best practices" are not a license to silently swap settled architecture.
2. **A changed decision goes through the ADR discipline.** If a checkpoint produces an actual decision change, a superseding ADR is written with the new context and rationale (per `build-conventions.md` Section 9). Settled decisions outside the flagged checkpoints stay settled — checkpoints are a targeted re-asking, not an invitation to re-litigate the whole foundation every phase.
3. **The phase-kickoff protocol activates the checkpoints.** Every phase begins with the coding agent reading the phase, surfacing its Decision checkpoints, and confirming or challenging each one before writing code. This is specified in `CLAUDE.md`'s phase-kickoff protocol and is what turns the roadmap's lists into an active conversation at the right moment.

The goal is not to re-litigate everything — that's just a slower way to never ship. It's to mark the specific decisions that were genuine coin-flips or data-contingent guesses, and let the settled ones stay settled. The checkpoints ensure the question gets *asked* at the moment the answer is finally knowable.

## 3. The v1 alpha build plan (Tier 1)

> The sequence of work from "foundation doc set complete" through "first alpha user installs the APK." This is the most substantive planning tier because it's where the coding agent will spend most of its attention, and where the sequencing decisions have the largest impact on how quickly the alpha can begin.

### The seven-phase build sequence

v1 build proceeds through seven roughly-sequential phases. The defining choice in this sequence is **UI-first**: after scaffolding, the entire user interface is built against mock data (Phase 2) so the app is complete and navigable before any real subsystem exists. Each subsequent subsystem phase then *wires its real implementation into the already-built UI*, replacing that subsystem's mock containers. This is possible because `ui-ux-spec.md` Section 10 specifies that mock and real containers expose the identical Orbit MVI `State` shapes and `Intents` — so the screens never change when the real subsystem arrives, they just get a real data source.

Phases are mostly sequential because later phases depend on earlier ones, but there is real opportunity for parallelism (the agent can work on multiple files at once, and the wiring of one subsystem can overlap with the building of the next).

#### Phase 1: Foundation and scaffolding

**What gets built:** The repository structure per `build-conventions.md` Section 3, the initial Gradle build with the version catalog and dependencies from `technical-architecture.md` Section 6, the Room database setup with empty tables matching the schemas from `data-model.md` Sections 3-7, the migrator module with the empty registry per `data-model.md` Section 8, the basic Compose app shell (single-activity host; the `CuizineScaffold` with the four-tab bottom bar and centered conversation FAB from `ui-ux-spec.md` Section 4), the Material 3 theme expressing the design tokens from `ui-ux-spec.md` Section 3 (`Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`), the Orbit MVI + Hilt infrastructure, the ktlint and Android Lint configuration, and the initial test scaffolding.

**What this phase is for:** Establishing the skeleton — including the app's navigational shape and visual language, so Phase 2 has a themed shell to build screens into. When Phase 1 is done, the app compiles, runs, shows the four-tab scaffold with the FAB and an empty themed "Welcome to Cuizine" state, and the tests (the infrastructure tests for the empty registry and the lint checks) pass. No features work yet, but the structure, theme, and navigation are in place.

**Dependencies:** None. Phase 1 can begin as soon as the foundation doc set is complete.

**Relative size:** The smallest phase. Most of the work is mechanical — setting up configuration files and directory structures from specifications.

**Decision checkpoints:**
- *[research-informed]* **Dependency and library currency.** ADR 0016's stack snapshot (Orbit MVI, Hilt, Tink, Compose Material 3, Room, Retrofit/OkHttp, Google Play Billing Library, Coroutines/Flow, Gradle Kotlin DSL) was written at a point in time. Research current versions, deprecation notices, and any meaningful ecosystem shifts before pinning the version catalog. The foundation's choices stand on their merits; the *versions* and *current best-practice initialization patterns* should be re-verified.
- *[data-driven]* **Orbit MVI ergonomics, after the first two or three real containers exist.** ADR 0016 chose Orbit over plain ViewModel+StateFlow as a "both are good" call — the cost of MVI ceremony is paid in real code, not specifications. Once two or three containers are written, surface honestly whether the discipline is earning its keep. Cheap to course-correct here; expensive after twenty containers.

#### Phase 2: The UI shell, component library, and mock data

**What gets built:** Every v1 screen from `ui-ux-spec.md` Section 5 (onboarding and first-run, Today and suggestion detail and conflict resolution, Pantry, Profile and constraint detail, all the Settings surfaces, and the conversation surface), the core component library from `ui-ux-spec.md` Section 6 (`SuggestionCard`, `ConstraintChip`, `SeverityIndicator`, `ConversationMessage`, `ThinkingIndicator`, `DisclosureNote`, `GracefulRefusal`, `EmptyState`, `PassphraseReveal`, etc.), and the **mock containers** from `ui-ux-spec.md` Section 10 — Orbit MVI containers backed by mock repositories and mock agents returning the canonical Sukhi/Aisha/Marcus fixtures from `testing-strategy.md` Section 5. Plus the Compose UI tests that verify each screen renders and each key flow is navigable against the mocks.

**What this phase is for:** Producing a complete, navigable Cuizine that *looks and feels* like the real product before any real logic exists. When Phase 2 is done, the founder (and alpha-adjacent testers, if desired) can walk every flow from `ui-ux-spec.md` Section 7 end-to-end — Sukhi's day-0 onboarding, asking for and refining a meal, telling Cuizine something via the FAB, the honest conflict, the passphrase reveal, export-and-walk-away — all driven by seeded fixtures. This is where UX problems surface while they are cheap to fix, and it gives the build tangible momentum: there is a real app to hold.

**Why UI-first:** Building the UI against mock containers that expose the exact `State` shapes and `Intents` the real containers will (per `ui-ux-spec.md` Section 10) means the later subsystem phases are genuinely just *wiring* — the screens already exist and already match the architecture. It also front-loads the design iteration (calm-precise-warm tone, the navigation feel, the conversation surface) to the moment it is cheapest, rather than discovering UX problems after the engine is built.

**Dependencies:** Phase 1 scaffolding (the theme, the shell, the Orbit MVI + Hilt infrastructure). No dependency on any real subsystem — that is the point.

**Relative size:** Medium-to-large. The UI has a longer tail than specified work because `ui-ux-spec.md` deliberately leaves final pixels to in-build iteration (Section 11). Budget for visual refinement on-device.

**Decision checkpoints:**
- *[data-driven]* **The navigation and conversation-surface bets from `ui-ux-spec.md` Section 12 (UI1-UI6).** The four-tabs-plus-FAB model, the FAB-vs-contextual-affordance redundancy (UI6), the long-press affordance question (UI1), the single-suggestion-vs-set question on Today, and how much the `ThinkingIndicator` reveals are all already tagged "tune in-build." Phase 2 is the moment to answer them on-device, not extrapolate from screenshots. Run each flow from Section 7 with the canonical fixtures and tune.
- *[research-informed]* **Material 3 / Material You evolution since `ui-ux-spec.md` Section 3 was written.** Confirm the design-token choices are still aligned with current Material guidance, that no significant new components (or deprecations) are worth folding in, and that the dynamic-color and Material-You expressive-mode story still maps to what was specified.

#### Phase 3: The constraint engine and validator

**What gets built:** The constraint engine API per `constraint-engine-spec.md` Section 6 (the 11 operations), the five constraint types with their payload schemas from Section 4, the five scope dimensions with their evaluation logic from Section 5, the active-set computation with caching from Section 5, the deterministic validator with its five-step algorithm from Section 7, the conflict resolution algorithm from Section 8, the provenance tracking from Section 9, and the full hard cases test suite from `testing-strategy.md` Section 5 covering all of the above. Then: **wire the real engine into the Profile and constraint-detail surfaces built in Phase 2**, replacing those screens' mock containers with real ones (same `State`, same `Intents`).

**What this phase is for:** Proving the engine works and connecting it to the UI that already displays constraints. When Phase 3 is done, the constraint engine passes every test in the hard cases suite against the canonical fixtures, *and* the Profile surface now shows real constraint data driven by the real engine instead of mocks. The conflict-resolution surface is backed by the real conflict algorithm. No agents yet, no sync yet, but the engine is provably correct and visible in the UI.

**Dependencies:** Phase 1 scaffolding (the data layer for persistence). Phase 2 UI (the surfaces to wire into).

**Relative size:** The single most substantive phase of the v1 build. The constraint engine is the heart of pillar 1 and the hard cases test suite is the single largest body of tests in the project. Getting this right is worth taking time for.

**Decision checkpoints:**
- *[data-driven]* **The validator's three-retry regeneration bound (ADR 0010).** ADR 0010 explicitly calls 3 "a parameter, not a principle... a guess that may need tuning in alpha." First contact with real Chef behavior on the hard cases suite is when 3 starts feeling either too few (frequent honest-fallback messages) or too many (latency on hard cases). Surface honestly; do not silently change. If the number changes, the ADR is amended with the new rationale.
- *[data-driven]* **The active-set cache invalidation strategy.** The cache design from `constraint-engine-spec.md` Section 5 looks clean on paper; real query patterns are where staleness bugs or unnecessary recomputation surface. After the hard cases suite is green, measure recomputation frequency against expectations and refine the invalidation predicate if needed.

#### Phase 4: The food data layer

**What gets built:** The Food Data Provider abstraction from `technical-architecture.md` Section 3 and ADR 0012, the local cache schema and operations from `data-model.md` Section 5, the curated bundle loader for the JSON asset from `data-model.md` Section 5, the USDA FoodData Central client, the Open Food Facts client (via its REST API using Retrofit), the cache-first query logic, the AI-assisted categorization fallback with severity scoping, and the tests for all of the above.

**What this phase is for:** Giving the validator real ingredient knowledge. When Phase 4 is done, the validator from Phase 3 can actually resolve ingredients through the Food Data Provider and the severity-scoped unknown ingredient handling works end-to-end. The Sukhi-and-alliums case (the canonical test of categorical reasoning) passes against real food data.

**Dependencies:** Phase 3 validator (because the food data layer exists to feed the validator). Phase 1 scaffolding.

**Relative size:** Medium. The external API clients are straightforward; the cache logic is nontrivial but well-specified; the curated bundle's initial content needs real authoring work but the bundle structure is simple.

**Decision checkpoints:**
- *[research-informed]* **USDA FoodData Central and Open Food Facts API state.** Both APIs evolve. Confirm endpoint stability, rate limits, response schemas, and authentication requirements before building the clients per ADR 0012. If either has shifted meaningfully, surface it — the cache-first architecture protects against frequency, not against schema drift.
- *[data-driven]* **AI-fallback accuracy on real ingredient queries.** ADR 0012's severity-scoped AI categorization is the riskiest call in the food data layer ("mitigations are genuine but not absolute"). First contact with messy real-world ingredient names (regional spellings, romanizations, brand-name foods) is where the accuracy floor is felt. If the fallback is misclassifying at a meaningful rate, the curated bundle's initial scope needs to grow — and that scope-up is itself a decision worth surfacing.

#### Phase 5: The agent layer and orchestrator

**What gets built:** The agent archetype from `agent-architecture.md` Section 2, the orchestrator with rule-based intent routing from Section 3, the Curator agent with its input/output contracts and prompt structure from Section 4, the Chef agent from Section 5, the lightweight v1 Pantry agent from Section 6, the validator integration with the regeneration loop from Section 3, the `ModelProvider` interface with the three provider adapters per ADR 0006, the per-agent provider routing from `agent-architecture.md` Section 8, the initial system prompts in `prompts/curator.md`, `prompts/chef.md`, and `prompts/pantry.md`, and the agent eval harness from `testing-strategy.md` Section 6. Then: **wire the real agents into the Today, suggestion, and conversation surfaces built in Phase 2**, replacing those screens' mock agents with the real orchestrator (same `State`, same `Intents`). The `ThinkingIndicator`'s revealed-specialization line (`ui-ux-spec.md` Section 6) now reflects real routing.

**What this phase is for:** Making Cuizine actually respond to users. When Phase 5 is done, the orchestrator routes a user intent to the appropriate agent, the agent produces structured output, the validator checks it, the regeneration loop handles rejections, and the whole flow works end-to-end for the canonical interaction patterns from `agent-architecture.md` Section 9 (Sukhi's Pattern A, Pattern B, Pattern C) — *driving the real Today and conversation surfaces* instead of mocks.

**Dependencies:** Phase 3 constraint engine. Phase 4 food data layer. Phase 2 UI (the surfaces to wire into). The LLM provider API keys need to be configured (the Firebase Functions proxy path and direct API access for local testing).

**Relative size:** Substantial. The agent layer is where a lot of product judgment lives — the prompt writing, the eval harness setup, the manual review of initial agent outputs — and this phase will involve meaningful iteration even before alpha kickoff.

**Decision checkpoints:**
- *[research-informed]* **LLM provider landscape since ADR 0006.** The per-agent provider routing rationale (Anthropic for nuanced reasoning, Google for cultural breadth, OpenAI for speed and structure) was a snapshot. Model lineups, pricing, structured-output support, and capability profiles shift quickly. Re-survey the three providers before locking the `ModelProvider` adapters, and surface any new entrant worth folding into the v3 BYOK plan.
- *[data-driven]* **Per-agent provider routing benefit (ADR 0006).** ADR 0006 explicitly marks this as "the v1 default, revisable based on eval data." After the agent eval harness produces real numbers, surface whether per-agent routing is meaningfully better than a single-provider baseline. If the per-agent advantage is marginal, the single-provider simplification is on the table.
- *[data-driven]* **The inferred-pantry heuristic (PRD open question, `agent-architecture.md`).** The PRD names "ship the simple version" — Punjabi household → assume atta, common dals, common spices. First contact with real Chef suggestions is when "Cuizine assumed I had X and I don't" becomes either a non-issue or a recurring friction. Tune in alpha; the upgrade path to a learned model is named but not yet justified.

#### Phase 6: The sync, encryption, and billing layers

**What gets built:** The encryption layer from `local-first-sync.md` Section 3 (AES-256-GCM with Argon2id key derivation), the recovery passphrase generation and presentation from Section 4, the Firestore sync protocol from Section 5, the conflict resolution from Section 6, the signed-out to signed-in transition from Section 7 with its full rollback semantics, the signed-in to signed-out transition from Section 8, the `TierPolicy` module from `monetization-and-billing.md` Section 5, the `BillingService` wrapper from Section 6 with the v1 alpha bypass from Section 9 active by default, and the sync and billing test suites from `testing-strategy.md` Sections 8 and part of 5. Then: **wire the real sync/encryption/billing into the Settings surfaces built in Phase 2** — the account & sync surface, the recovery passphrase reveal, export, delete-account — replacing those screens' mock containers.

**What this phase is for:** Enabling multi-device use (for users who sign in) and establishing the billing architecture that ships in v2 but exists in v1 in bypassed form. When Phase 6 is done, Sukhi can install Cuizine, optionally sign in (seeing the real passphrase reveal surface from Phase 2 now backed by real key derivation), optionally sync to another device, and the billing architecture is in place even though the alpha bypass means no user is charged.

**Dependencies:** Phase 1 data layer (the encryption layer operates on the data layer). Phase 2 UI (the Settings surfaces to wire into). Phase 5 is not strictly required but in practice, sync layer work benefits from having real data flowing through the system, so Phase 5 should be at least substantially underway before Phase 6 begins in earnest.

**Relative size:** Medium. The encryption and sync work is detail-heavy but well-specified; the billing layer's alpha-bypass mode is simpler than the v2 full billing flow.

**Decision checkpoints:**
- *[both]* **Argon2id parameter calibration (`local-first-sync.md` Section 3).** Research the current OWASP guidance on Argon2id parameters (memory, iterations, parallelism) since the foundation was written — the recommended floor moves as hardware moves. Then calibrate against actual low-end Android devices (the support floor from `testing-strategy.md`) to land within the 500ms-1s target. Research informs the range; the device measurement picks the point.
- *[research-informed]* **Tink and Google Play Billing Library version currency.** Both libraries are actively maintained and have meaningful version-to-version API changes. Re-confirm current major versions, deprecation notices, and recommended initialization patterns before pinning.
- *[data-driven]* **Last-write-wins conflict policy (`local-first-sync.md` Section 6).** LWW is a deliberate simplification. Multi-device testing — even informal, on the founder's own devices — is when "did I just lose a change?" surfaces. If the conflict rate or perceived loss is non-trivial, the policy is on the table for refinement.

#### Phase 7: Integration polish and journey verification

**What gets built:** The final integration pass now that every subsystem is wired into the UI built in Phase 2. This includes: confirming every screen's mock container has been replaced by its real counterpart (no mocks remain in a shippable build), the end-to-end integration tests from `testing-strategy.md` Section 10 covering Sukhi's day 0, day 4, day 5, day 8, and day 30 journeys against the *real* engine/agents/sync, and the on-device tone-and-texture refinement that `ui-ux-spec.md` Section 11 deliberately deferred to in-build (final spacing, color tuning in both themes, motion timing, copy in `prompts/copy/`). Plus resolving any UX issues surfaced when the real subsystems replaced the mocks (real agent latency, real data shapes, real edge cases).

**What this phase is for:** Making Cuizine feel finished. The UI existed and was navigable from Phase 2, but on mock data; this phase ensures the real product matches the vision once real intelligence is flowing through it. When Phase 7 is done, the journeys from PRD § 4 work end-to-end on real subsystems, the calm-precise-warm voice is fully realized on-device, and there are no mock containers left in the alpha build.

**Dependencies:** All previous phases. This phase is the convergence point where the UI-first work (Phase 2) and the subsystem work (Phases 3-6) fully meet. In practice it overlaps the tail of Phase 6, since each subsystem's wiring already happened in its own phase — what remains here is the cross-cutting polish and the full-journey verification.

**Relative size:** Medium. Because the UI was built in Phase 2 and each subsystem wired itself in as it was built, this phase is lighter than a from-scratch UI phase would be — it is polish and verification, not construction. The longer tail is the on-device visual refinement deferred from `ui-ux-spec.md`.

**Decision checkpoints:**
- *[data-driven]* **Alpha-readiness gate: the integrated Sukhi journey on real subsystems.** Phase 7's exit criterion is the end-to-end Sukhi journeys from PRD § 4 running against real engine, real agents, real food data, real sync — not unit tests, the actual experience. Run them. Surface anything that feels off in tone, latency, or the calm-precise-warm voice. The honest signal isn't "tests pass"; it's "would I let this in front of an alpha user?"
- *[data-driven]* **`ui-ux-spec.md` Section 12 unresolved questions, final pass.** Any UI question still open after Phase 2's on-device tuning gets a final pass here against real data and real agent latency. The answers folded into `ui-ux-spec.md` per the bidirectional discipline.

### Parallelism and sequencing flexibility

The seven phases are not strictly linear. Real parallelism is possible:

- **Phase 2 (UI + mock data)** can begin as soon as Phase 1's scaffolding, theme, and shell exist. It has no dependency on any real subsystem, so it can run in parallel with the entire subsystem track (Phases 3-6) — the UI is built against mocks while the engine, agents, and sync are built independently, and they converge as each subsystem is wired in.
- **Phase 4 (food data)** can begin before Phase 3 is complete because the Food Data Provider abstraction can be built against tests before the validator is ready to consume it.
- **Phase 5 (agents)** can begin in parallel with Phase 4 — the agent prompts and contracts can be drafted against the specifications before the food data layer is wired up.
- **Phase 6 (sync and billing)** can begin in parallel with Phase 5 because the sync layer doesn't depend on the agent layer at all, and the billing layer's alpha-bypass mode is independent of most of the rest of the system.
- **Phase 7 (integration polish)** is the convergence phase; it naturally overlaps the tails of the subsystem phases as each one finishes wiring into the Phase 2 UI.

The phases are sized relative to each other: Phase 3 (constraint engine) is the largest single subsystem, Phase 2 (UI + mock data) is medium-to-large, Phases 4-6 are each substantial but smaller, Phase 7 (integration polish) is medium, and Phase 1 (scaffolding) is the smallest. The total v1 build completes when all seven phases are done, not when a calendar target is hit.

### What "alpha kickoff ready" means concretely

Alpha kickoff happens when all seven phases are substantially complete and the alpha kickoff readiness criteria from `launch-readiness-checklist.md` are met (the checklist specifies these in detail; this roadmap names the high-level shape). At minimum:

- The full Sukhi journey from day 0 through day 30 works end-to-end in the app
- The hard cases test suite from `testing-strategy.md` Section 5 is passing
- The validator's exhaustive tests from Section 7 are passing with near-100% coverage
- The encryption round-trip tests from Section 8 are passing
- The v1 alpha bypass from `monetization-and-billing.md` Section 9 is working correctly
- The recovery passphrase flow works end-to-end against a real Firebase project
- The founder has personally walked through the Sukhi journey multiple times and the tone feels right
- The initial set of alpha users is identified and the onboarding materials are ready

## 4. The v1 alpha period (Tier 2)

> The timeline from alpha kickoff to v2 planning. Per the discipline from Principle 1, this tier is deliberately lean. The alpha is a learning mechanism, not a destination. Every piece of infrastructure we add to the alpha process is friction we have to push through before v2 can start.

### What the alpha is for

The alpha has three explicit learning goals:

1. **Validating the constraint engine's correctness in real use.** The hard cases test suite from `testing-strategy.md` Section 5 is exhaustive by design, but alpha users will exercise the engine in ways the test suite did not anticipate. The goal is to surface gaps — constraint combinations we didn't imagine, scope interactions we didn't test, conflict resolution paths we didn't trace — and update the foundation to cover them.

2. **Validating the calm-precise-warm tone against real users.** The tone from `vision.md` is a design target, but it is only real when users encounter it in practice. Alpha users report how the agents feel to interact with, and the founder's manual review of their event log exports identifies cases where the tone slipped — too clinical, too perky, too pushy, too bland. The goal is to refine the prompts (in the `prompts/` directory per `build-conventions.md` Section 8) until the tone feels right across cultural and linguistic variations.

3. **Surfacing gaps in the foundation we didn't anticipate.** Any structured plan this large has blind spots. Alpha users discover them. The goal is not just to patch the immediate issues but to update the foundation docs so the next phase of work benefits from the learning. Per `build-conventions.md` Section 7's bidirectional update discipline, foundation doc updates are a normal output of the alpha period, not an emergency fix-up.

### What the alpha is NOT for

The alpha is not for:

- **Gathering statistically significant usage data.** The alpha population (15-25 users per ADR 0001) is too small for statistical conclusions. Reading one alpha user's event log carefully is more valuable than applying analytics to all of them.
- **Testing monetization.** The alpha bypass from `monetization-and-billing.md` Section 9 is active; no one pays. Monetization is tested at v2 launch with real users facing real prices.
- **Testing scale.** The alpha is too small to surface scale issues. Scale testing happens at v2 launch.
- **A/B testing prompts or features.** Per `build-conventions.md` Section 8, A/B testing is deferred to v2+ because the alpha population is too small for it to produce meaningful signal. Single-version iteration is the v1 discipline.
- **Building a community.** The alpha population is small and relational (the founder has direct contact with each user). Community-building is a v2+ concern if it happens at all.

### The feedback mechanism

Alpha feedback flows through two channels:

1. **Event log exports** from PRD § 5, initiated by alpha users through Settings → Share feedback with Cuizine founder. These are manually reviewed by the founder, with the severity-zero and severity-one events prioritized. The founder reads the exports and identifies the patterns that matter.

2. **Direct conversation** with alpha users. The founder has ongoing contact with each alpha user and asks them informally about their experience. This is low-ceremony and unstructured — it is literally "how has Cuizine been working for you?"

There is no dedicated feedback portal, no structured survey tool, no user research platform. The alpha is small enough that personal contact and event log review are sufficient, and the discipline is to resist adding infrastructure that would have to be dismantled or expanded for v2.

### The iteration cycle

Learnings from the alpha feedback mechanism flow into three kinds of updates:

1. **Prompt iterations.** Changes to `prompts/curator.md`, `prompts/chef.md`, and `prompts/pantry.md` per `build-conventions.md` Section 8. Iterated frequently.
2. **Foundation doc updates.** Per `build-conventions.md` Section 7's bidirectional update discipline. Iterated less frequently but deliberately.
3. **Code fixes and small improvements.** Normal build work that continues during the alpha period.

The iteration cycle is driven by feedback volume — the founder reviews alpha feedback as it accumulates, identifies the most important patterns, and makes the corresponding updates in batches. There is no fixed cadence; the rhythm should feel natural rather than frantic, driven by learning rate rather than calendar obligation.

### Exit criteria

The alpha period ends when these criteria are met:

1. **The constraint engine has been exercised against the full primary and secondary persona set in real use.** Sukhi is the primary; Aisha and Marcus are the secondaries. Each persona's realistic constraint graph has been exercised by at least one alpha user in actual daily use, and the validator has handled it correctly.

2. **Severity-zero events have reached zero for a sustained period.** Severity-zero events (medical or religious constraint violations, safety-floor bypasses) are the worst failure mode Cuizine can produce. When there have been zero severity-zero events across a meaningful stretch of active use by multiple users, the validator and constraint engine have earned the trust required to serve a larger population.

3. **The calm-precise-warm tone has been validated by at least several alpha users with diverse cultural contexts.** Not every alpha user, just several — enough to know that the tone generalizes beyond the founder's own aesthetic. Diversity matters because cultural fluency is pillar 5, and tone failures often manifest differently across cultural contexts.

4. **The foundation doc updates from alpha learnings have stabilized.** If the foundation is still receiving frequent substantive updates, the alpha has more to teach us. When updates have slowed to minor refinements only for a sustained stretch, the alpha has done its job.

5. **The founder has the conviction to move to v2.** This is the subjective criterion, named honestly. Moving to v2 is a commitment that changes Cuizine's posture from "we are learning" to "we are serving." That commitment should feel earned, not forced. If it doesn't feel right, the other criteria are probably not actually met even if they look met on paper.

The alpha does not end because of a calendar date. It ends when these criteria are met. Shorter is possible if the alpha reveals that the foundation is substantially stronger than we hoped; longer is possible if it reveals gaps that require significant foundation work. The pace is set by the learning, not by the clock.

### The discipline against lingering

This section exists because lingering in the alpha is the single most common failure mode of trust-conscious products. The founder is specifically committed to:

- **Not extending the alpha "to be safe" once criteria are met.** If the criteria are genuinely met, moving to v2 is the right call even if alpha feels comfortable.
- **Not adding new alpha users after the initial cohort unless there's a specific learning goal.** Alpha population is small on purpose; expanding it mid-phase dilutes the personal-contact model.
- **Not accepting "one more iteration before launch" as a reason to delay.** There will always be one more iteration possible; the question is whether the one we just made is enough.
- **Periodically assessing whether the alpha is still producing new learning.** If it's not, that's the signal to move, not a signal to wait for more data.

### Decision checkpoints during alpha

- *[data-driven]* **The 2-channel feedback cadence (`alpha-feedback-and-iteration.md`).** The feedback model is sized for a small population. If alpha grows faster than expected, or if a single channel is producing the majority of signal, the cadence is on the table.
- *[data-driven]* **Anything from a Phase-3 through Phase-7 checkpoint that was "ship the simple version and revisit."** The inferred-pantry heuristic, the validator three-retry bound, the per-agent routing benefit, last-write-wins conflict friction — alpha is the data-gathering phase for each of these. Surface them periodically (not constantly) as alpha telemetry accumulates.
- *[data-driven]* **Severity-zero event rate.** If severity-zero events (validator misses, safety-floor bypasses) occur at all, that's the highest-priority signal regardless of phase — it forces re-examination of the constraint engine or validator design before transition can be considered.

## 5. The alpha-to-v2 transition (Tier 2.5)

> The bridge work that handles the transition from v1 alpha to v2 public launch. This is a focused phase — shorter than the alpha period, longer than a single sprint — but it's worth calling out as its own tier because the transition work is distinct from both alpha iteration and v2 launch planning.

### What happens in the transition

Five distinct streams of work happen during the transition phase:

**Stream 1: The alpha bypass removal.** Per `monetization-and-billing.md` Section 9, the v1 alpha bypass is removed via a single migration that sets `v1_alpha_bypass = 0` for every user, sets `current_tier = 'free'`, unlocks trial eligibility, logs the transition event, and presents a warm message to alpha users on their next app open. The migration is written, tested, and staged for deployment.

**Stream 2: The foundation doc retrospective.** Every foundation doc is reviewed in light of alpha learnings. Doc updates that have been made throughout the alpha period are consolidated, and any remaining alpha-surfaced issues are addressed. The foundation set is stabilized so that v2 build can proceed against a stable reference.

**Stream 3: ADR writing for deferred decisions.** Several ADRs that were deferred during v1 build (because the decisions could wait until we had alpha data) are written during this transition. The most likely candidates:

- **ADR 0013 (v3 cross-account peer coordination security model)** — promoted from Section 8 of `security-and-privacy.md` into its own formal ADR for v3 planning. Not written during v1 because v3 was too far out; written during the transition because v2 planning depends on knowing how v3 will work.
- **ADR 0014 (v2 pricing finalization)** — the specific numbers within the lower band from `monetization-and-billing.md` Section 3, decided based on v1 alpha cost data and market research.
- **ADRs for any v1 decisions that were deferred to "revisit during alpha"** — typically things in the Open Questions sections of various docs that alpha data has now resolved.

**Stream 4: v2 scope lock-in.** The exact feature set for v2 is locked in during the transition. The scope is shaped by the architectural readiness pattern (what v2 is supposed to add per the existing foundation docs) plus any alpha learnings that require scope adjustment. The v2 scope lock is a deliberate gate — after this point, scope changes during v2 build require explicit founder decision.

**Stream 5: Launch preparation groundwork.** The operational readiness work for v2 launch begins: Play Store developer account setup, Firebase project upgrade from Spark to Blaze, marketing asset preparation, privacy policy drafting with legal counsel, the formal security disclosure program infrastructure. Most of this work continues into the v2 launch plan, but it starts during the transition.

### Duration

The transition phase completes when all five streams are done. It is sized to be focused and efficient — extending it significantly beyond what the work requires is a sign that either the alpha did not stabilize fully, or the v2 scope is not yet clear, or the launch preparation has hit a real blocker that needs to be addressed.

### Decision checkpoints at transition

This stage is the highest concentration of revisitable decisions in the entire roadmap — it is explicitly the moment when alpha data converts into v2 commitments. Every checkpoint here is in some way contingent on alpha learnings.

- *[data-driven]* **The full-public-v2-launch sequencing call.** This is the single highest-risk sequencing decision in the roadmap, made deliberately for momentum (see §6 below). Re-confirm explicitly at transition: given what alpha actually taught us, is full public launch (vs. a staged or geo-limited rollout) still the right call? The default is yes; the question deserves an honest answer with alpha data in hand rather than automatic inheritance.
- *[data-driven]* **ADR 0014 — v2 pricing finalization.** Specific numbers within the lower band ($5-7 / $10-14), informed by alpha cost telemetry and market research. This ADR is *written for the first time* at this transition.
- *[data-driven]* **ADR 0013 — v3 cross-account peer coordination security model formalization.** Promoted from `security-and-privacy.md` Section 8's working specification into its own ADR. Real v2 Family-tier household dynamics (when they accumulate) will refine this further during the v2 launch period.
- *[data-driven]* **Promotion of any "ship the simple version" v1 decisions whose alpha data is now in.** Inferred-pantry heuristic upgrade, validator retry bound, per-agent routing — surface each one and decide whether the v1 default holds for v2 or needs an ADR-recorded change.
- *[research-informed]* **v2 dependency/library refresh.** Same currency check as Phase 1, repeated at v2 build start. The foundation's stack snapshot will have aged through the entire v1 build and alpha period; major versions, deprecations, and security advisories should be re-surveyed before v2 development begins.

## 6. The v2 launch plan (Tier 3)

> Everything that ships in v2, organized by area. v2 is a full public launch per the answer to the upfront question: Play Store availability across North America, the full Cuizine and Cuizine Family tier infrastructure active, billing enabled, no waitlist or geographic staging. This is the most substantive tier of the roadmap because v2 is where Cuizine becomes real for users who aren't hand-picked by the founder.

### v2 scope overview

v2 ships the following areas of work, each specified in more detail below:

- **The full billing layer** (from bypassed alpha state to real production billing)
- **The full Pantry agent** (from v1 lightweight to v2 real — barcode, receipt, fridge photos)
- **The Planner agent** (new in v2 — week-ahead meal planning, household reasoning)
- **The Sourcing agent** (new in v2 — Instacart integration, shopping list handoff)
- **Multi-profile household features** (activated from the profile-aware v1 architecture)
- **Play Store launch preparation and execution**
- **Operational readiness** (support, security disclosure, incident response)
- **Marketing and positioning**
- **Backend evolution** (Firebase plan upgrade, Cloud Function proxy scaling)

### Area 1: The full billing layer

Per `monetization-and-billing.md` Sections 5-7, the billing layer shifts from the v1 alpha bypass state to full production billing:

**What needs to happen:**
- Google Play product IDs (`cuizine_monthly`, `cuizine_yearly`, `cuizine_family_monthly`, `cuizine_family_yearly`) are created in the Google Play Console
- The `BillingService` wrapper is fully tested against real Google Play responses (v1 tested against mocks)
- The 14-day trial flow from Section 4 is activated, including the day 7 mid-trial offer
- The tier enforcement from Section 5 is activated on every relevant operation
- The upgrade and downgrade transition flows from Section 7 are tested with real Google Play subscription events
- The content portability flow from Section 8 is verified on real user data
- The subscription_state table is populated for every user per Section 5's specification
- The forbidden behaviors list from Section 11 is enforced by code review and by the forbidden-behaviors-in-build list from `build-conventions.md` Section 11

**Dependencies:** The alpha bypass removal from Stream 1 of the transition must be complete first. Google Play developer account and Play Store listing must be ready.

### Area 2: The full Pantry agent (v2 evolution)

Per `technical-architecture.md` Section 7.5's v2 deltas and ADR 0012, the Pantry agent evolves from its v1 lightweight form to a v2 first-class capability:

**What gets built:**
- **Barcode scanning** using the Open Food Facts REST API's barcode lookup, with camera integration through CameraX and ML Kit barcode scanning
- **Receipt scanning** using OCR and AI parsing to extract purchased items from a photographed grocery receipt (likely via one of the ModelProvider backends)
- **Fridge photo ingestion** using multimodal AI to identify ingredients visually
- The expanded Pantry agent's full reasoning about pantry contents, ingredient availability, and cross-meal ingredient reuse

**What unifies all three:** Per the v2 deltas section, these are bundled as a single capability rather than added piecemeal. The camera permissions, vision pipelines, and ingestion UX are shared infrastructure. Shipping them together is cleaner than adding them one at a time across point releases.

**Dependencies:** The food data layer from v1 Phase 4. The agent architecture from v1 Phase 5. The multimodal AI capability requires adding it to the `ModelProvider` interface (v1 only exercised text-based inference).

### Area 3: The Planner agent (new in v2)

Per `agent-architecture.md` Section 10 and `technical-architecture.md` Section 7.5, the Planner agent ships in v2:

**What gets built:**
- The Planner agent class conforming to the archetype from `agent-architecture.md` Section 2
- The `PlannerInput` and `PlannerOutput` typed contracts
- The long-context capability routing for the Planner (long context window capability is a new requirement per Section 10)
- The Planner's prompt in `prompts/planner.md`
- The Planner's integration with the Chef as a sub-routine per Section 10's "Sub-agent calls" discipline
- The per-meal validator invocation for each meal in a plan output
- The Planner's eval harness scenarios added to `testing-strategy.md` Section 6's automated set
- The UI surface for week-ahead planning where the user reviews and adjusts the generated plan

**Dependencies:** The agent architecture from v1 Phase 5 (the Planner composes with the existing Chef and Curator). The multi-profile household data from Area 5 for Family tier planning across profiles.

### Area 4: The Sourcing agent (new in v2)

Per `agent-architecture.md` Section 10 and ADR 0003:

**What gets built:**
- The Sourcing agent class with its typed contracts
- The Instacart API integration (the credentials, the API client, the product search and ordering flow)
- The shopping list generation from Chef or Planner outputs
- The ingredient deduplication and quantity aggregation logic
- The substitution handling for items Instacart doesn't carry exactly
- The user review step before submitting an Instacart order
- The Sourcing agent's prompt in `prompts/sourcing.md`
- The UI surface for shopping list review and Instacart handoff

**Dependencies:** The Chef and Planner agents. An Instacart API developer relationship established (which is an operational task that may need to begin during the alpha-to-v2 transition to allow time for approval).

### Area 5: Multi-profile household features

Per ADR 0004 and `technical-architecture.md` Section 7.5:

**What gets built:**
- The UI flows for adding dependent profiles with the three variants from ADR 0004 (silent, consent-aware, recommended-disclosure)
- The household composition management in Settings
- The active-set composition logic for "meals that work for everyone at the table" (the v2 extension to the v1 single-profile active set)
- The household planning surface where the user picks which profiles are at the table
- The cross-profile conflict resolution when multiple profiles' constraints interact

**Dependencies:** The data model from v1 already supports multi-profile architecture (per the profile-aware schema from `data-model.md` Section 3). What v2 ships is the UI flows and the active-set composition logic, not a data model change.

### Area 6: Play Store launch preparation and execution

Per `technical-architecture.md` Section 7:

**What needs to happen:**
- Google Play Developer account verified and in good standing
- App signing configured and the signing key secured
- The app's store listing written (title, description, screenshots, promotional graphics, content rating, target audience)
- The privacy policy drafted with legal counsel and published at `cuizine.ai/privacy`
- The terms of service drafted with legal counsel and published
- The app submitted for Google Play review
- Google Play review passed (typically a few days, sometimes more for apps that touch camera/location/health-related permissions)
- The app published to the Play Store
- The v2 launch announcement prepared

**Dependencies:** Essentially everything else. Play Store launch is gated by the operational readiness items and the scope items above.

### Area 7: Operational readiness

Per `security-and-privacy.md` Section 6 and Section 10, and per the new public-launch context:

**What needs to happen:**
- The formal security disclosure program activates: `security@cuizine.ai` email, the disclosure policy page on `cuizine.ai`, the response timelines
- The incident response process is operational with the stages from Section 10
- The support channel is established (probably email-based for v2, more elaborate channels deferred to v3 if demand warrants)
- The tabletop rehearsal of incident response from `security-and-privacy.md` Section 12's open questions is conducted
- The regular security review cadence begins

### Area 8: Marketing and positioning

Separate from the foundation docs but named here for completeness:

**What needs to happen:**
- The `cuizine.ai` website is built (landing page, pricing, privacy posture, FAQ)
- The positioning copy is written in the calm-precise-warm voice (per `vision.md`)
- The initial announcement channels are decided (probably a combination of a blog post on `cuizine.ai`, direct outreach to communities where the primary persona lives, and modest organic social media presence)
- The approach is honest and non-manipulative per Principle 3 from `monetization-and-billing.md` — no fake scarcity, no manufactured urgency, no dark patterns

### Area 9: Backend evolution

Per ADR 0011 and `local-first-sync.md`:

**What needs to happen:**
- Firebase project upgrades from Spark (free) to Blaze (pay-as-you-go) to support larger usage
- Firestore security rules are reviewed for v2 scale (still restrictive, but tested against the anticipated load)
- Cloud Function proxy scaling is verified (Firebase scales automatically but cost projections need to be understood)
- Backend monitoring is set up (operational telemetry only, no content telemetry)

### The full public launch commitment

Per the answer to the upfront question, v2 is a full public North American launch. This means:

- **No waitlist.** Users can download Cuizine from the Play Store immediately upon v2 availability.
- **No geographic staging.** v2 is available across Canada and the US simultaneously.
- **No soft rollout.** The Play Store listing goes live when everything is ready, not in a gradual percentage rollout.
- **The architecture stands on its own merits.** The foundation doc set has prepared Cuizine for this moment. The v1 alpha has validated the correctness. The v2 launch trusts the preparation.

The honest acknowledgment: full public launch is the highest-risk path because gaps in the foundation that alpha didn't catch will surface at scale immediately. The mitigation is the rigorous alpha exit criteria from Tier 2 and the comprehensive test suite from `testing-strategy.md`. The reason for taking the higher-risk path is that it maintains momentum — staged rollouts can dilute focus, create artificial gatekeeping, and delay the real learning that comes from scale.

### Build completion for v2

v2 launch happens when all nine areas of work are complete and the operational readiness gates are cleared. Some of this work is substantive (the Planner and Sourcing agents especially), some is operational (Play Store submission, legal counsel work), and some is preparatory (marketing). The discipline from Principle 1 applies: move with intent, don't linger.

### Decision checkpoints during v2 build

- *[research-informed]* **Instacart developer API state.** ADR 0003 made Instacart the sole grocery integration. Confirm the developer API is still available on terms compatible with the v2 plan, that no breaking changes have shipped, and that the integration approach in `agent-architecture.md` Section 7 still maps to what Instacart currently exposes.
- *[data-driven]* **Planner and Sourcing agent safety-valve assessment.** The roadmap (Section 10) names both as non-blocking — if either hits unexpected complexity, v2 can launch with the relevant Family-tier feature deferred. Re-evaluate honestly at the v2 mid-build: is the original v2 scope holding, or is a safety-valve activation the right call?
- *[data-driven]* **Multi-profile households (Area 5) scope.** Same safety-valve framing applies. If real household-tier reasoning surfaces unexpected complexity, holding Cuizine Family to a v2.1 point release is on the table.
- *[research-informed]* **Stack and library refresh, second pass.** Substantial calendar time has elapsed since v1; re-verify stack currency, especially for security-sensitive libraries (Tink) and platform libraries (Play Billing).

## 7. The v2 launch period (Tier 3.5)

> The timeline from v2 public launch to v3 planning start. This is a period of active operation and learning at scale, structurally parallel to the alpha period but at much larger scale.

### What the v2 launch period is for

Three explicit goals parallel to the alpha's:

1. **Validating the foundation at scale.** The alpha validated correctness in small-scale use; v2 validates it in real-world use across the variety of cultural contexts, devices, network conditions, and edge cases that a North American user base presents.

2. **Building real revenue from real users.** The billing layer that was bypassed in v1 is now active. Revenue is signal — it tells us whether the value proposition is working, whether the pricing is right, and whether the trust posture is credible enough to convert users to paid tiers.

3. **Accumulating the data that v3 depends on.** The v3 Observer agent requires accumulated user history to be meaningful. The v3 BYOK path requires enough non-BYOK usage data to calibrate the reduced pricing. The v3 linked partner feature needs enough household usage from v2 Family tier users to inform the security model.

### Exit criteria for v3 planning

v2 → v3 transition begins when:

1. The foundation has stabilized against real-world use, with periodic foundation reviews producing only minor updates consistently.
2. The Planner and Sourcing agents have been used enough to validate their designs or identify specific gaps that v3 planning should address.
3. The Family tier has enough real users to inform v3's linked partner feature design (the current Section 8 of `security-and-privacy.md` is based on reasoning; v3 needs real data).
4. The founder has capacity (or has hired the capacity) to take on v3 global expansion work, which is the most significant growth Cuizine will have done to that point.
5. v2 is producing sustainable revenue at a rate that supports the investment in v3.

### Duration

The v2 launch period ends when the exit criteria above are met. The pace depends on market reception, operational maturity, revenue growth, and foundation stability — variables that are observable in real time rather than predictable in advance. The discipline from Principle 3 applies: completion gates, not calendar commitments.

### Decision checkpoints during v2 launch period

- *[data-driven]* **ADR 0013 reality check against v2 Family-tier data.** The linked-partner security model in `security-and-privacy.md` Section 8 (formalized into ADR 0013 at the alpha-to-v2 transition) is based on reasoning. Real v2 Family-tier household dynamics are the input that confirms or revises that reasoning before v3 builds on it.
- *[data-driven]* **v3 readiness signal.** Are the §7 exit criteria genuinely met, or only-on-paper met? The honest assessment is the input to whether v3 planning starts now or waits.
- *[data-driven]* **The "configurable persona" v3 feature, decided from v2 tone-variation feedback.** Whether to offer tone variations as a setting depends on whether v2 users actually request them — and whether they request meaningfully different variations from the calm-precise-warm default.

## 8. The v3 expansion plan (Tier 4)

> Global (geographic) expansion, new agents, new features. The most speculative tier because v3 planning will be meaningfully informed by v2 learnings. This section names the shape of v3 based on the architectural readiness pattern but does not commit to details that will refine during v3 planning. Note: v3 expansion is geographic (Android in new markets), not cross-platform — Cuizine is Android-only across all versions per ADR 0015.

### v3 scope overview

- **Global expansion** to UK, EU, and other English-speaking markets (Android, via Google Play in new geographies — not a new platform)
- **The Observer agent** for long-term health intelligence
- **The linked partner feature** with the security model from `security-and-privacy.md` Section 8 (formalized as ADR 0013 during v2-to-v3 transition)
- **BYOK** for users who want to supply their own LLM provider keys
- **Configurable agent personas** with user-selectable tones
- **GDPR compliance operationalized** including DPIA completion and privacy policy updates
- **Multi-region backend infrastructure** as needed for the UK/EU user base

### Why v3 is more speculative

Every v3 scope item depends on decisions that will be informed by v2 reality. The Observer agent's design depends on what v2 usage patterns reveal about what kinds of long-term trends are actually useful. The linked partner feature's design depends on what v2 Family tier usage reveals about household dynamics. The BYOK pricing depends on v2 cost data. The configurable persona feature depends on v2 feedback about tone variations. The global expansion depends on whether v2 is generating enough revenue to support the operational overhead.

This is the architectural readiness pattern at its purest: the foundation has been designed for v3, the shape of v3 is known, but the details refine based on real data before they lock in.

### v3 build scope

v3 is substantially larger than the v1 → v2 transition in scope. Multiple new agents, global (geographic) expansion, new regulatory framework (GDPR), new architectural model (BYOK changes the four-quadrant matrix in ADR 0011). v3 may benefit from dedicated team members beyond the founder — but that is a v3 planning decision, not a v1 or v2 commitment.

### Decision checkpoints during v3 planning and build

- *[both]* **ADR 0013 final design from real v2 Family-tier data.** Per-link X25519 keys, shared-view schema, revocation flow, consent UX. Real household dynamics inform the design; current research on peer-to-peer encrypted sharing (Signal's protocols, MLS, Keybase's history) informs the cryptographic choices.
- *[research-informed]* **v3 BYOK provider landscape and user-key UX.** The provider list (Anthropic, Google, OpenAI) was a v1 snapshot. By v3, the landscape will have shifted substantially. Re-survey current providers, key-handling best practices, and BYOK UX patterns from comparable products before locking the v3 `ModelProvider` reconfiguration.
- *[data-driven]* **Observer agent design from v2 usage patterns.** What kinds of long-term trends are actually useful is the v2 signal. Without it, the Observer is reasoning about reasoning.
- *[research-informed]* **GDPR compliance posture, current state.** Regulatory guidance evolves. The v3 global-expansion compliance work should research current guidance at the time, not work from a snapshot in this doc.
- *[data-driven]* **The geographic-expansion sequencing.** Which markets, in which order — informed by v2 demand signals, regulatory complexity, and operational capacity rather than predicted now.

## 9. Cross-tier themes

> Maintenance and review cadences that span multiple versions. These are the disciplines that keep Cuizine healthy over time rather than the milestone-specific work of each version.

### Foundation doc maintenance

Per `build-conventions.md` Section 7's bidirectional update discipline, foundation docs are updated as the build teaches us things. The rhythm is driven by learning, not by calendar:

- **During v1 build (Phases 1-6):** Foundation docs are updated as real gaps surface. Frequent during active build, less often during polish.
- **During v1 alpha period:** Foundation docs are updated in batches as alpha learnings accumulate.
- **During alpha-to-v2 transition:** The foundation doc retrospective consolidates all alpha updates into a stable reference for v2 build.
- **During v2 build:** Similar to v1 build — updates as real gaps surface, usually in the context of an ADR or a formal scope change.
- **During v2 launch period:** Periodic foundation review. Most updates are minor; major updates trigger an ADR.
- **During v2-to-v3 transition:** A second foundation doc retrospective parallel to the first.
- **During v3 build:** Similar to v2.
- **Ongoing:** Regular review with ad-hoc updates when needed. The founder sets the specific rhythm based on learning rate.

### ADR writing cadence

ADRs are written when decisions rise to the level of "this affects how the system works" per `build-conventions.md` Section 9. Expected cadence:

- **v1 build:** Occasional ADRs as deferred decisions resolve (probably 2-5 new ADRs beyond the current 12)
- **Alpha-to-v2 transition:** A batch of ADRs (ADR 0013 for v3 linked partners, ADR 0014 for pricing, others as needed)
- **v2 build:** Occasional ADRs as v2 scope items surface new decisions
- **v2-to-v3 transition:** Another batch (v3 feature-specific ADRs)
- **v3 build:** Occasional ADRs similar to v2

By the end of v3 build, Cuizine probably has 20-30 ADRs total, each documenting a specific architectural decision that shaped the project.

### Security review cadence

Per `security-and-privacy.md` Section 10:

- Per-commit surfacing through the agent decision protocol
- Regular review during build, rhythm set by the founder
- Pre-release for every major release
- Periodic full review after v2 launch
- Regular threat model review starting at v2 launch

### Prompt iteration cadence

Per `build-conventions.md` Section 8 and `testing-strategy.md` Section 6:

- **During v1 build:** Prompts are written during Phase 5 and iterated as agent behavior is validated
- **During v1 alpha:** Prompts iterate based on alpha feedback and manual review, rhythm driven by feedback volume
- **During alpha-to-v2 transition:** Prompts stabilize as the foundation does
- **During v2 build:** New agents get their prompts during build; existing agents continue iterating
- **During v2 launch period:** Prompt iteration continues based on real user feedback; A/B testing becomes feasible at v2 scale
- **Ongoing:** Regular prompt review, rhythm set by the founder based on observed need

### Dependency and library updates

Kotlin, the Android Gradle Plugin, Jetpack libraries (Compose, Room, Hilt), Orbit MVI, the Firebase SDKs, and the other dependencies in the Gradle version catalog — all of these evolve independently of Cuizine. The discipline:

- Security-critical updates are applied promptly (within days of release)
- Minor version updates are applied during regular maintenance windows
- Major version updates are treated as architectural decisions and may require an ADR

### Testing discipline

Per `testing-strategy.md`:

- The test suite grows with the codebase, not after it
- The hard cases suite grows through the alpha and v2 periods as new cases surface
- Coverage on high-risk modules (validator, encryption, billing) never drops
- Performance budgets are tracked and regressions are investigated

## 10. Dependencies and critical paths

> The blocking relationships that determine what can happen when. This section is where the discipline of "you cannot ship X without completing Y" lives.

### Critical path for v1 alpha kickoff

The critical path from foundation-complete to alpha kickoff runs through:

**Phase 1 (scaffolding) → Phase 3 (constraint engine) → Phase 4 (food data) → Phase 5 (agents) → Phase 7 (integration) → Alpha kickoff**

with **Phase 2 (UI + mock data)** running in parallel from immediately after Phase 1, and **Phase 6 (sync and billing)** running in parallel with the agent track. The intelligence chain (engine → food data → agents) is the critical path because each link depends on the previous; the UI is built alongside it on mocks and the two converge at Phase 7. Sync/billing is not on the critical path to a usable alpha (a signed-out, single-device alpha works without it) but is wanted for the full alpha experience.

This is the minimum path. Phases 3 (food data) and 5 (sync and billing) can run in parallel and do not block alpha kickoff specifically — alpha kickoff only requires that Cuizine work for the canonical Sukhi journey from PRD § 4. The food data layer improves the Sukhi journey but doesn't strictly block it; the sync layer is an optional capability (alpha users can use Cuizine signed-out).

However, **alpha kickoff readiness criteria from `launch-readiness-checklist.md`** will specify that Phases 3 and 5 (the constraint engine and the agents) are in fact required for kickoff. The reason: alpha is the chance to validate these layers, and shipping alpha without them wastes the alpha's learning value. So in practice, all seven phases must be substantially complete before alpha kickoff.

### Critical path for v2 public launch

The critical path from alpha end to v2 public launch runs through:

**Alpha exit criteria met → Alpha-to-v2 transition → v2 scope lock-in → v2 build (Areas 1-9 in parallel) → Operational readiness → Play Store submission → Play Store approval → v2 public launch**

The Play Store approval is outside Cuizine's control but is typically quick. The other phases are within Cuizine's control.

Specific blocking relationships:

- **Area 1 (billing) blocks v2 launch.** Without working production billing, v2 cannot launch as a paid product.
- **Area 6 (Play Store launch preparation) blocks everything.** Without the store listing, privacy policy, and app submission, nothing ships.
- **Area 7 (operational readiness) blocks launch.** The formal security disclosure program must be active at launch per `security-and-privacy.md` Section 6.
- **Area 3 (Planner) and Area 4 (Sourcing) do NOT strictly block launch.** If one of them runs into unexpected complexity, v2 can launch without that specific agent (the relevant tier features become unavailable, but the rest of the product works). This is a safety valve.
- **Area 5 (multi-profile households) does NOT strictly block launch.** Similar safety valve — if household features hit a blocker, Cuizine Family tier can be held back to a point release (v2.1) while the Cuizine tier ships on time.

### Critical path for v3 global expansion

The critical path for v3 is more speculative but includes:

- **ADR 0013 (v3 linked partner security model) must be written before v3 build begins**
- **The GDPR DPIA must be completed before v3 launches in EU markets**
- **Google Play availability in target markets must be confirmed before global expansion begins** (some countries have specific Play Console requirements)
- **Legal counsel work for UK/EU jurisdictional compliance must be complete before launch in those markets**

v3 build probably runs Observer, linked partners, BYOK, and geographic expansion as mostly-parallel work streams.

## 11. Open questions

### v1 build

- **What's the right Phase 1 → Phase 2 handoff moment?** Phase 1 is short but the moment when "scaffolding is done" vs "scaffolding is still being refined" is fuzzy. Probably "all infrastructure tests pass, the empty app compiles and launches showing the themed four-tab scaffold with the FAB, and the Room schema is in place." Once that holds, Phase 2 (building screens against mocks) can begin.
- **Can Phase 3 and Phase 5 meaningfully overlap?** The agent layer technically depends on the constraint engine being in place, but agent prompt writing and input/output contract design can happen in parallel with engine implementation. Worth experimenting with.
- **How much of Phase 2's UI should be built before starting the subsystem track?** The whole UI on mocks, or just enough to validate the navigation and a few key flows before parallelizing? Tentatively build the full navigable shell and the key flows (onboarding, Today, conversation) first, then let the rest of Phase 2 overlap the subsystem phases. Originated here with the UI-first decision.
- **Who authors the curated food data bundle from ADR 0012?** The bundle needs real authoring work — culturally-specific ingredient entries, corrections for USDA/OFF errors, medically-critical category definitions. This is a task that might benefit from outside help (a nutritionist, a cultural consultant) and is worth budgeting for.

### v1 alpha period

- **How are the initial 15-25 alpha users identified?** Some combination of personal outreach, community channels (probably focused on the Brampton-area South Asian community where Sukhi's persona is strongest), and interested connections from the founder's network. No formal application process.
- **What happens if alpha surfaces a foundation gap that requires a new ADR mid-alpha?** Probably: the ADR is written during alpha, the foundation is updated, and the agent implements the change. The alpha continues with the updated foundation. This is the bidirectional update discipline in action.
- **What's the right communication channel with alpha users during the alpha?** Probably direct email or messaging rather than in-app — the founder has one-on-one relationships with each alpha user and the in-app channels are for product interaction, not feedback collection.

### Alpha-to-v2 transition

- **Is the transition phase compressed if alpha exits quickly?** Probably not — the transition work (foundation retrospective, ADR writing, v2 scope lock-in) has its own minimum duration regardless of how alpha concluded.
- **What happens to the alpha users during the transition?** They keep using the alpha version (with the bypass still active) until v2 is ready to ship, at which point they migrate through the v2 launch path.

### v2 launch

- **How do we know we're ready for the full public launch without a soft rollout safety net?** The rigor of the alpha exit criteria plus the comprehensive test suite is the answer, but it is a real risk and the founder should feel the weight of it when making the launch decision.
- **What's the right initial marketing investment?** Probably modest — a launch blog post, honest outreach, organic social. Not paid marketing in v2 unless there's a specific reason.
- **When does hiring start?** Solo founder is the expected mode through v1 and probably through early v2. Hiring is probably triggered by either operational overload (too many users for one person to support well) or by specific capability needs (legal, design, marketing). Deferred to real circumstances.

### v2 launch period and v3

- **What's the right duration to wait before starting v3 planning?** The exit criteria in Section 7 are soft — the founder's judgment is the ultimate gate. The actual moment is context-dependent, driven by stability signals rather than calendar time.
- **Should Cuizine pursue partnerships (healthcare providers, nutritionists, dietitians) in v3?** This came up during `security-and-privacy.md` as a possible HIPAA question. The current posture is explicitly not-HIPAA, but v3 might be the right moment to revisit if partnership opportunities emerge. Deferred to v3 planning.
- **Should v3 introduce dedicated team members beyond the founder?** Probably yes, given v3's scope. But hiring timing is a separate decision from technical roadmap.

### Cross-tier

- **Is the foundation doc maintenance rhythm sustainable over time?** Probably yes, but will need to be calibrated as Cuizine matures. The rhythm may shift toward less frequent for stable parts and more frequent for newly-added parts.
- **How does Cuizine decide when to deprecate a feature?** Every foundation doc's "open questions" mentions things that might be deprecated. The formal process for deprecation is not currently specified. Probably should be an addition to `build-conventions.md` or its own minor doc when the first real deprecation surfaces.

## 12. Cross-references

### What this document references

- `vision.md` — for the launch sequencing commitment from its constitution
- `PRD.md` — for the v1 feature scope, the Sukhi user journey, the alpha context
- `technical-architecture.md` — for the subsystem decomposition and the v2/v3 deltas in Section 7.5
- `constraint-engine-spec.md` — for what Phase 3 implements
- `agent-architecture.md` — for what Phase 5 implements and the future agents in Section 10
- `data-model.md` — for what Phase 1 scaffolds and what Phase 4 extends
- `local-first-sync.md` — for what Phase 6 implements
- `ui-ux-spec.md` — for what Phase 2 implements (the UI shell, components, and mock data) and the design language Phase 1's theme sets up
- `monetization-and-billing.md` — for the billing layer, the alpha bypass, and the tier evolution across versions
- `build-conventions.md` — for the agent decision protocol and the bidirectional update discipline
- `testing-strategy.md` — for what the test suites verify and the test layer responsibilities
- `security-and-privacy.md` — for the security review cadences and the v3 linked partner model
- ADR 0001 — Canadian-first alpha, North American v2, the launch sequence this roadmap operationalizes
- ADR 0004 — multi-profile households, the structure v2 activates
- ADR 0007 — three-tier subscription, the billing structure v2 activates
- ADR 0008 — BYOK deferred to v3, the commitment this roadmap's Tier 4 honors
- ADR 0011 — optional backend with capability tiers, the foundation for v1 and v2 architecture
- ADR 0012 — food data sources, the basis for Phase 4 and the v2 Pantry evolution
- ADR 0015 — Android-only scope; ADR 0016 — native Kotlin/Compose/Orbit/Room, the stack the build phases assume

### What this document defers to deeper-dive docs

- **`alpha-feedback-and-iteration.md`** — the detailed operational playbook for the alpha period, including feedback review process details
- **`launch-readiness-checklist.md`** — the specific go/no-go gates for alpha kickoff, v2 launch, and v3 expansion
- **`glossary.md`** — the canonical term definitions referenced across the roadmap
- **`open-questions.md`** — the consolidated open questions list across all foundation docs
- Future ADRs (0013 for v3 linked partners, 0014 for v2 pricing, others) — the formal decisions that emerge from the alpha-to-v2 transition
- Marketing documents (not in the foundation set) — the v2 launch plan's Area 8 marketing work
- Legal documents (ToS, privacy policy) — drafted by counsel using `security-and-privacy.md` as input

### What this document does *not* defer (decisions made here)

- The five core planning principles (Section 2)
- The seven-phase v1 alpha build plan with relative sizing and dependencies (Section 3)
- The lean alpha period disciplines and exit criteria (Section 4)
- The alpha-to-v2 transition streams (Section 5)
- The v2 launch plan covering nine areas of work with the full public launch commitment (Section 6)
- The v2 launch period exit criteria for v3 planning (Section 7)
- The v3 expansion scope with acknowledged speculation (Section 8)
- The cross-tier maintenance and review cadences (Section 9)
- The critical paths and blocking relationships (Section 10)

### How the agent should use this doc

When the coding agent begins the v1 build, this roadmap is the sequencing guide. Section 3 specifies which phase the work currently belongs to; Section 10 specifies what is blocking what. When the agent is unsure whether to work on feature A or feature B next, it consults the phase structure and picks the work that respects the dependencies.

During the v1 alpha period, this roadmap specifies what the alpha is for and what it is not for. The agent should not build feedback infrastructure that exceeds what Section 4 specifies. The agent should not extend alpha features during the alpha period unless the extensions directly serve the three learning goals.

During the alpha-to-v2 transition, this roadmap specifies the five streams of work. The agent should drive each stream to completion, with the founder's review at key gates.

During the v2 launch period and beyond, this roadmap's cross-tier themes (Section 9) specify the ongoing disciplines. The agent should maintain them even as specific version work happens.

When the roadmap and reality diverge — the build takes longer than expected, a phase reveals more complexity, a scope item surfaces unforeseen blockers — the agent surfaces the divergence per `build-conventions.md` Section 6. The roadmap is a sequencing guide, not a contract, and it updates as the reality teaches us things. Silent drift between the roadmap and actual progress is the failure mode this doc is designed to prevent.

---

*End of `roadmap.md` v1 (initial draft). Next revision will incorporate real learnings from v1 build, real alpha feedback patterns, and any foundation updates that refine the sequencing. The principles in Section 2 are stable; the relative sizing of phases in Section 3 may refine as actual build progresses. The critical paths in Section 10 may shift as work surfaces dependencies we did not anticipate. The doc is completion-driven throughout: phases end when their criteria are met, not when a calendar target says they should.*
