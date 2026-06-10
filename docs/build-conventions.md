# Cuizine — Build Conventions

> The doc that bridges the architectural foundation and the actual code. Where the preceding foundation docs answer "what should Cuizine do," this doc answers "how should Cuizine be built." It specifies the conventions and disciplines that govern code structure, naming, organization, the coding agent's decision protocol, the bidirectional flow between foundation docs and code as both evolve, prompt versioning, ADR discipline, and the explicit list of build-time forbidden behaviors. This is the doc the agent reads when deciding *how* to do something the architectural docs told it *what* to do.

## 1. Purpose & how to read this doc

This document specifies the **build conventions for Cuizine across v1, v2, and v3**, with the discipline that conventions are stable across versions even as features grow. It assumes you have read all previous foundation docs and the ADRs.

This document defines: the working principles that govern how code is built against the foundation set; the repository folder structure and naming conventions; the Kotlin/Compose patterns including the Orbit MVI state management commitment; Kotlin code style; the agent decision protocol with calibrated guidance on local vs architectural choices; the bidirectional update discipline between foundation docs and code; the prompt versioning and iteration model; the ADR discipline including when and how new ADRs get written during build; the relationship between this doc and `testing-strategy.md`; the explicit forbidden behaviors during build; and the open questions this spec acknowledges.

This document does **not** define: the actual code (that's the codebase); the testing strategy in detail (that's `testing-strategy.md`); deployment configuration (that's operational documentation); the Android/Kotlin/Compose framework documentation (the agent reads the official Android docs directly); or the CI/CD pipeline (deferred to the deployment guide).

**When this document and a deeper-dive doc disagree,** this doc wins for build conventions, code style, and the agent decision protocol. The architectural docs win for what the system does. If a build convention surfaces a contradiction with an architectural doc, that contradiction is itself a signal — see Section 7 on the bidirectional update discipline.

## 2. Working principles

> The disciplines that govern how Cuizine is built. Every build decision in the codebase traces back to one of these principles. They are the answer to "why did we build it this way" when someone asks six months from now.

### Principle 1: The foundation docs are the source of truth

When the code and a foundation doc disagree, the foundation doc wins until and unless a deliberate decision changes it. This means: the agent does not silently invent architecture that contradicts the docs. The agent does not "improve" on the constraint engine spec mid-build because something seemed clearer to implement differently. The agent does not skip writing tests for a behavior the docs specify as required. When the agent encounters a tension between what the docs say and what feels right to implement, the agent surfaces the tension to the founder rather than choosing.

The corollary: when the docs and the code drift, the *docs* are updated to reflect the new reality (with deliberate intent) or the *code* is changed to match the docs. Both directions are valid; silent drift is not.

### Principle 2: Conventions exist to make the codebase coherent across files

A codebase where every file looks slightly different is a codebase that's hard to maintain, hard to onboard into, and hard to refactor confidently. Conventions exist not because any individual convention is uniquely right, but because *consistency* is uniquely valuable. The naming conventions in Section 5 are not optimized for elegance — they're optimized for predictability. Anyone (human or AI) reading any file should be able to predict what other files in the codebase look like.

### Principle 3: The agent is a collaborator, not an autopilot

The coding agent is doing meaningful technical work, but it is not making decisions in isolation. The founder is in the loop on architectural decisions, principle conflicts, and any moment where the right answer isn't obvious from the foundation docs. The agent's job is to do the things that have clear answers and surface the things that don't. The agent is not graded on speed; it is graded on whether the resulting code reflects the foundation faithfully.

This principle is what makes the calibrated decision protocol from Section 6 work. The agent isn't trying to minimize founder interruption — it's trying to maximize the chance that the build comes out right.

### Principle 4: Tests are not optional, they are how we know it works

Cuizine's correctness depends on a constraint engine that handles severity tiers, a validator that catches violations deterministically, a sync layer that doesn't lose data, a billing layer that respects the trust posture, and a recovery flow that works under stress. None of these can be verified by reading the code; they have to be verified by tests. Tests are not "nice to have when there's time" — they are part of the definition of "done." A feature without tests is a feature that doesn't work yet.

### Principle 5: Small, frequent commits over large, infrequent ones

The agent commits work frequently — once per feature increment, once per bugfix, once per refactor. Each commit message is descriptive ("Add LimitConstraint payload schema with hard/soft enforcement" rather than "WIP"). Each commit passes all tests. Frequent commits create a clean history that supports debugging, review, and rollback. Large infrequent commits hide intermediate states and make it impossible to bisect when something breaks.

## 3. The repository structure

> The folder layout for the Cuizine codebase. The structure is organized by *layer* (architectural responsibility) rather than by *feature*, because Cuizine's features cross multiple layers and a feature-organized layout would scatter related code across the codebase.

```
cuizine/
├── app/                                   # the Android application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/ai/cuizine/
│   │   │   │   ├── CuizineApplication.kt   # app entry point (Hilt @HiltAndroidApp)
│   │   │   │   ├── MainActivity.kt         # single-activity host for Compose
│   │   │   │   │
│   │   │   │   ├── core/                   # cross-cutting infrastructure
│   │   │   │   │   ├── config/             # environment, build flags, feature toggles
│   │   │   │   │   ├── errors/             # error types and handling utilities
│   │   │   │   │   ├── logging/            # event log and operational telemetry
│   │   │   │   │   └── ext/                # Kotlin extension functions used across the app
│   │   │   │   │
│   │   │   │   ├── data/                   # persistence layer (data-model.md implementation)
│   │   │   │   │   ├── database/           # Room database, entities, DAOs, migrations
│   │   │   │   │   │   ├── entities/       # Room @Entity definitions
│   │   │   │   │   │   ├── daos/           # Room @Dao interfaces
│   │   │   │   │   │   └── migrations/     # Room migration registry
│   │   │   │   │   ├── encryption/         # the cryptography layer (local-first-sync.md implementation)
│   │   │   │   │   ├── sync/               # the Firestore sync protocol (local-first-sync.md implementation)
│   │   │   │   │   └── food/               # the Food Data Provider (ADR 0012 implementation)
│   │   │   │   │       ├── FoodDataCache.kt        # cache layer
│   │   │   │   │       ├── bundle/                 # bundle loader and structure
│   │   │   │   │       ├── UsdaClient.kt           # USDA FoodData Central client (Retrofit)
│   │   │   │   │       └── OffClient.kt            # Open Food Facts client (Retrofit)
│   │   │   │   │
│   │   │   │   ├── engine/                 # the constraint engine (constraint-engine-spec.md implementation)
│   │   │   │   │   ├── ConstraintGraph.kt  # the constraint graph API surface
│   │   │   │   │   ├── types/              # the five constraint types and their payloads
│   │   │   │   │   ├── scope/              # the five scope dimensions and their evaluation
│   │   │   │   │   ├── validator/          # the deterministic validator
│   │   │   │   │   └── conflict/           # the conflict resolution algorithm
│   │   │   │   │
│   │   │   │   ├── agents/                 # the agent layer (agent-architecture.md implementation)
│   │   │   │   │   ├── Orchestrator.kt     # the deterministic orchestrator
│   │   │   │   │   ├── Archetype.kt        # the agent archetype base interface
│   │   │   │   │   ├── curator/            # the Curator agent
│   │   │   │   │   ├── chef/               # the Chef agent
│   │   │   │   │   ├── pantry/             # the Pantry agent (lightweight in v1)
│   │   │   │   │   └── providers/          # the ModelProvider interface and adapters
│   │   │   │   │
│   │   │   │   ├── billing/                # the billing layer (monetization-and-billing.md implementation)
│   │   │   │   │   ├── TierPolicy.kt       # the TierPolicy module
│   │   │   │   │   ├── BillingService.kt   # the Google Play Billing wrapper
│   │   │   │   │   └── SubscriptionState.kt # the subscription state types
│   │   │   │   │
│   │   │   │   ├── ui/                     # the user-facing Compose presentation layer
│   │   │   │   │   ├── screens/            # full-screen composables
│   │   │   │   │   ├── components/         # reusable composables
│   │   │   │   │   ├── theme/              # Material 3 theme: color, typography, shape
│   │   │   │   │   └── state/              # Orbit MVI ViewModels (containers, states, side effects)
│   │   │   │   │
│   │   │   │   └── shared/                 # types and utilities shared across layers
│   │   │   │       ├── types/             # the typed data classes from agent-architecture.md contracts
│   │   │   │       └── util/              # generic utilities
│   │   │   │
│   │   │   ├── res/                        # Android resources (strings, drawables, etc.)
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   ├── test/                           # JVM unit tests (mirror main/kotlin structure)
│   │   │   └── kotlin/ai/cuizine/
│   │   │       ├── core/
│   │   │       ├── data/
│   │   │       ├── engine/
│   │   │       ├── agents/
│   │   │       ├── billing/
│   │   │       └── fixtures/               # test data, seeded constraint graphs, etc.
│   │   │
│   │   └── androidTest/                    # instrumented tests (Compose UI, Room, integration)
│   │       └── kotlin/ai/cuizine/
│   │           ├── ui/
│   │           └── integration/            # cross-layer integration tests
│   │
│   └── build.gradle.kts                    # app module build script (Kotlin DSL)
│
├── prompts/                               # versioned agent prompts (separate from code)
│   ├── curator.md
│   ├── chef.md
│   ├── pantry.md
│   ├── copy/                              # user-facing copy strings
│   └── README.md                          # how prompts are versioned and iterated
│
├── decisions/                             # ADRs (the decisions folder we've been writing)
│   ├── 0001-canadian-first-alpha-...md
│   ├── 0002-...
│   └── README.md
│
├── docs/                                  # the foundation docs (this folder)
│   ├── vision.md
│   ├── PRD.md
│   ├── technical-architecture.md
│   ├── ...                                # constraint-engine-spec, agent-architecture, data-model, etc.
│   ├── ui-ux-spec.md
│   └── README.md                          # the authoritative index / reading order
│
├── assets/                                # bundled assets (loaded from res/raw or assets/)
│   ├── food_data_bundle/
│   │   └── v1.json
│   └── recovery_wordlist/
│       └── v1.json
│
├── scripts/                               # one-off scripts (not shipped)
│   ├── alpha/
│   │   └── grant_bypass.kts                # the alpha bypass script from monetization-and-billing.md § 9
│   └── README.md
│
├── gradle/                                # Gradle wrapper and version catalog
│   └── libs.versions.toml                 # centralized dependency versions
├── build.gradle.kts                       # root build script
├── settings.gradle.kts
└── README.md                              # project README
```

### Folder discipline

- **Code only goes in one folder.** A file that needs to be referenced by multiple layers lives in `shared/` or `core/`, not duplicated.
- **The `engine/` package has no Android or Compose dependencies.** Cuizine's constraint engine is pure Kotlin and could in principle be reused outside of Android. The `engine/` package must not import anything from `androidx.compose` or the Android framework.
- **The `data/` package has no UI dependencies.** The persistence layer is independent of how the data is presented.
- **The `ui/` package has no direct database access.** UI code interacts with the engine and agents through the typed contracts in `shared/types/` and through Orbit MVI ViewModels, never by importing Room DAOs directly.
- **The `agents/` package has no UI dependencies.** Agent code is invoked by the orchestrator, which is invoked by the UI layer's ViewModels, but the agent code itself does not know about Compose or the Android framework.

These layering rules are enforced by lints in `analysis_options.yaml` where possible and by code review where lints can't catch them.

## 4. Kotlin/Compose patterns and the state management choice

### Orbit MVI is Cuizine's state management approach

Cuizine uses **Orbit MVI** (the `org.orbit-mvi:orbit-core` and `orbit-compose` libraries) layered on top of Android's standard **ViewModel + StateFlow**, with **Jetpack Compose** for UI. This decision (ADR 0016) deserves explicit explanation because it shapes how every UI-connected file is written.

**Why Orbit MVI.** Within the structured-framework category, Orbit was chosen because it builds *on top of* ViewModel + StateFlow rather than replacing them — it composes cleanly with the rest of the standard Android ecosystem (Compose, Hilt, navigation, lifecycle) and keeps the agent maximally effective because the underlying primitives are conventional. The MVI pattern (Model-View-Intent) maps naturally onto Cuizine's domain, which is fundamentally state-machine-shaped: the constraint conversation moves through defined states, the meal-suggestion flow transitions through requesting → generating → validating → presenting → accepted/rejected → regenerating, the trial lifecycle is a state machine, and the sync transitions are explicitly state-machine-modeled in local-first-sync.md. MVI's discipline — state is a single immutable object, changes flow through explicit intents handled by reducers, side effects are modeled separately — fits these flows directly, makes complex state debuggable through unidirectional data flow, and is deterministically testable in exactly the way testing-strategy.md wants.

**What this means in practice.** Each feature area has an Orbit `ContainerHost` (typically a `ViewModel`) that owns:
- A **State** — a single immutable Kotlin `data class` representing everything the UI needs to render.
- **Intents** — functions on the container that express what the user (or system) wants to do. Intents run inside Orbit's `intent { }` block and emit state changes via `reduce { }` and one-off events via `postSideEffect()`.
- **Side effects** — a sealed class hierarchy of one-time events (navigation, transient messages, etc.) that the UI collects but that are not part of the persistent state.

Cuizine uses this pattern consistently. A container's job is to expose the engine/agent/data layers' state to the UI in a Compose-friendly way and to translate user intents into calls on those layers. Containers do not contain business logic — the constraint engine, the validator, the agents, and the repositories hold the logic; the container orchestrates and exposes it.

**Container locations.** Orbit containers live in `app/src/main/kotlin/ai/cuizine/ui/state/`, organized by feature area: `ConstraintConversationViewModel.kt`, `SuggestionViewModel.kt`, `SubscriptionViewModel.kt`, etc. Each has its companion `State` and `SideEffect` definitions co-located.

**No global mutable state outside the MVI containers and the repository layer.** Cuizine has no top-level mutable singletons, no mutable global state, no ad-hoc event buses. Every stateful thing is either owned by an Orbit container (UI-facing state) or by a repository/layer below the UI (domain state exposed as `Flow`). This makes testing dramatically easier (containers are tested in isolation with Orbit's test harness, dependencies are injected via Hilt and overridden in tests) and prevents the failure mode of "I changed this value and three unrelated things broke."

### The Compose UI pattern

Cuizine uses Jetpack Compose with these specifics:

- **Composables are stateless where possible.** A composable that doesn't need to own state is a pure function of its inputs. State is hoisted to the Orbit container and collected via `collectAsStateWithLifecycle()`. State read from the container is not the composable's own state.
- **State collection at the screen level.** Screen-level composables collect the container's `StateFlow` and pass plain data down to child composables. Side effects are collected via Orbit's `collectSideEffect { }` at the screen level and translated into navigation calls or transient UI (snackbars, etc.).
- **Screen composables compose feature composables compose primitive composables.** A screen is a top-level composable representing a full surface (e.g., the suggestion screen). A feature composable is a self-contained piece of UI handling one concern (e.g., the rejection feedback affordance). A primitive composable is a low-level reusable component (e.g., a calm button styled per the Material 3 theme).
- **Material 3 / Material You.** Cuizine uses Material 3 theming with dynamic color support, defined in `ui/theme/`. The calm-precise-warm voice from `vision.md` is expressed through the color, typography, shape, and motion choices in the theme.

### Dependency usage discipline

The Gradle version catalog (`gradle/libs.versions.toml`) is the source of truth for dependencies, with the app module's `build.gradle.kts` declaring which it uses. Adding a new dependency requires:

1. The dependency is named in one of the foundation docs (most likely `technical-architecture.md` Section 6).
2. The dependency is justified — what does it do that we can't reasonably do ourselves.
3. The dependency is checked against the "things explicitly not depended on" list in `technical-architecture.md` Section 6.
4. The agent surfaces the addition to the founder before adding it to the version catalog.

Removing or upgrading a dependency requires:

1. The change is described in a commit message that explains why.
2. Tests still pass after the change.
3. Any breaking changes in upgraded dependencies are addressed in the same commit.

## 5. Code style and naming conventions

### Kotlin style

Cuizine follows the official Kotlin coding conventions and the Android Kotlin style guide, enforced via ktlint, with these specific commitments:

- **Prefer `val` over `var`.** Immutability is the default; mutability is opt-in and deliberate.
- **Prefer immutable data classes for state.** Orbit MVI states, agent input/output contracts, and domain types are immutable `data class`es. Mutation produces a new instance via `copy()`.
- **Prefer named arguments for any function with more than two parameters**, and always for boolean parameters. Positional arguments are fine for one or two parameters; beyond that, names improve readability.
- **Prefer expression bodies for short functions.** `fun square(x: Int) = x * x` is preferred over the block-bodied form when the body is a single expression.
- **Use trailing commas** in multi-line argument lists and collection literals. This makes diffs cleaner.
- **Prefer sealed classes / sealed interfaces for closed type hierarchies** — the five constraint types, the four severity tiers, the validator verdicts, the Orbit side effects. This gives exhaustive `when` checks the compiler enforces.
- **Limit lines to 120 characters** (the ktlint default for Kotlin), which comfortably accommodates Kotlin's longer type annotations.

### File naming

- **Files use UpperCamelCase matching their primary type.** `ConstraintGraph.kt`, not `constraint_graph.kt`. A file containing a single class is named for that class.
- **Test files mirror their source files with a `Test` suffix.** `ConstraintGraph.kt` → `ConstraintGraphTest.kt`.
- **Files containing only extension functions or top-level utilities are named for their purpose**, e.g. `ScopeExtensions.kt`, `DateUtils.kt`.

### Class and type naming

- **Classes, interfaces, and objects use UpperCamelCase.** `ConstraintGraph`, `ChefAgent`, `SubscriptionState`.
- **Type parameters use single uppercase letters or short PascalCase.** `T`, `K`, `V` for generics; `TPayload`, `TResult` when the parameter has semantic meaning.
- **Enum entries and sealed subtypes use UpperCamelCase.** `Severity.Inviolable`, not `Severity.INVIOLABLE`. (Kotlin convention favors UpperCamelCase for enum entries; this differs from Java's SCREAMING_CASE.)
- **Boolean properties and functions use `is`, `has`, `can`, or `should` prefixes.** `isLoading`, `hasPermission`, `canUpgrade`, `shouldRetry`. This makes call sites self-documenting.
- **Compile-time constants use SCREAMING_SNAKE_CASE.** `const val MAX_TRIAL_DAYS = 14`.

### Function naming

- **Verb phrases for functions that do something.** `addConstraint()`, `validateSuggestion()`, `derivePartnerKey()`.
- **Noun phrases (often properties) for things that return a value without side effects.** `activeConstraintSet`, `currentTier`, `deviceTimezone`. These usually become `val` properties or getters rather than functions.
- **Suspend functions carry no special suffix.** Kotlin's `suspend` modifier indicates asynchrony; an `Async` suffix is redundant. Functions returning `Flow` are named as nouns describing the stream (e.g., `observeActiveConstraints()`).

### Documentation comments

- **Public API surfaces are documented with KDoc (`/** ... */`).** Every public class, every public function, every public property on a data class.
- **KDoc explains *what* and *why*, not *how*.** The how is the code itself.
- **Cross-references in KDoc use the standard `[ClassName]` syntax** so IDE tooling can navigate between related types.

## 6. The agent decision protocol

> The single most important section in this doc operationally. The discipline for when the coding agent decides things on its own versus when it must surface the question to the founder. The wrong answer here either slows the build to a crawl or lets the agent silently drift away from the foundation. The right answer is calibrated: decide locally, surface architecturally, with explicit examples on both sides so the agent knows the line.

### The general principle

The agent decides things that are *obviously local* — choices that affect a single file, a single function, a single test, with no implications for other files or for the foundation docs. The agent surfaces things that are *obviously architectural* — choices that affect multiple files, multiple foundation docs, the agent's understanding of any principle, or anything that looks like a design decision.

The line between local and architectural is not always obvious. When in doubt, surface. The cost of surfacing too often is founder time; the cost of surfacing too rarely is silent drift, and silent drift is harder to recover from than wasted founder time.

### Examples of local decisions (decide without asking)

The agent decides these without surfacing:

- **Variable names within a function.** `final activeSet = ...` vs `final activeConstraints = ...` is a local choice; either is fine as long as it's clear in context.
- **The exact code structure within a method.** Whether to use a for loop or a `.map().toList()` call is local; the conventions in Section 5 give general guidance and the rest is judgment.
- **Micro-optimizations that don't change behavior.** Caching a value in a local variable, hoisting a constant out of a loop, using `const` constructors where applicable.
- **Test fixture details.** What names to give test profiles, what specific constraints to use in test cases (as long as they exercise the right behavior), how to organize test helpers.
- **File organization within an existing package.** Whether `ChefInput.kt` and `ChefOutput.kt` should be in `agents/chef/` or split into `agents/chef/types/` is local — pick the cleaner option.
- **Lint warnings and small refactors.** Fixing a lint warning, renaming a variable for clarity, extracting a helper function — all local.
- **Documentation comments.** Writing the doc comment for a method the agent has just implemented is local.
- **Test cases for a behavior the foundation docs specify.** If `constraint-engine-spec.md` says "the validator must reject suggestions that violate inviolable constraints," the agent writes the test cases for that behavior without surfacing — the behavior is specified, the tests are obviously needed.

### Examples of architectural decisions (always surface)

The agent surfaces these and waits for a decision:

- **New files in unexpected folders.** If the agent finds itself wanting to create a new top-level folder under `lib/` that isn't in Section 3's structure, that's a signal to stop and surface. Maybe it's the right thing to do — but the founder needs to confirm.
- **New dependencies in the Gradle version catalog.** Per Section 4's dependency usage discipline, every new dependency is surfaced before being added.
- **Decisions that affect multiple foundation docs.** If implementing a feature in the engine layer requires changing the data model, that's a signal to stop and surface. The foundation docs need to update first.
- **Anything that looks like a design choice.** If the agent is implementing a feature and finds itself thinking "there are several reasonable ways to do this," that's the signal — the agent surfaces the alternatives and lets the founder pick.
- **Anything that touches the encryption boundary.** Per `local-first-sync.md` Section 10's forbidden behaviors list, the encryption layer is fragile in specific ways. Any change here is architectural.
- **Anything that touches the validator's deterministic logic.** Per ADR 0010 and `constraint-engine-spec.md` Section 7, the validator must remain deterministic. Any change to its core logic is architectural.
- **Anything that touches the `TierPolicy` module's gate decisions.** Per `monetization-and-billing.md` Section 11's forbidden behaviors list, billing logic is fragile. Any change is architectural.
- **Conflicts between foundation docs.** If the agent reads two foundation docs and finds them disagreeing about something, that's the most important kind of thing to surface. The founder needs to resolve the contradiction before the build proceeds.
- **Behaviors not specified by the foundation docs.** If the agent encounters a question whose answer isn't in any foundation doc, the agent surfaces it. Maybe it's a deliberate omission (and the agent is expected to make a reasonable choice and document it). Maybe it's an oversight (and the foundation needs to be updated). The founder decides which.

### The "I'm not sure if this is local or architectural" case

When the agent isn't sure which side a decision falls on, the rule is **surface it**. The cost of asking is small; the cost of guessing wrong is larger. The agent does not get penalized for asking too many questions; the agent is praised for catching things that would have been silent drift.

### How surfacing works in practice

When the agent surfaces a decision, the format is:

1. **Brief description of the situation.** What is the agent trying to do.
2. **The specific question or alternatives.** What are the possible answers.
3. **The agent's recommendation and rationale.** Which option does the agent prefer and why.
4. **Any cross-references to foundation docs that are relevant.** Where in the docs does the question live.

The founder responds with a decision (or with a counter-question if the agent's framing missed something). The decision is recorded — either inline in the conversation, in a commit message, or in a new ADR if the decision is architecturally consequential enough.

### Surfacing under a delegated build loop

Everything above describes interactive operation: surface, wait, the founder responds. When the founder has granted a **standing delegation** for an autonomous build loop (`roadmap.md` Section 2, Principle 6, "Operating modes" — recorded as entry #0 in `DECISION-LOG.md`), the meaning of "surface" changes from *surface and wait* to **surface in writing and proceed**: the agent records the same four-part format as a `DECISION-LOG.md` entry (situation, alternatives, recommendation, doc cross-references — plus the course taken and a rollback note) and continues under the delegation's decision rules: keep documented choices unless research disqualifies them, take the minimal-deviation alternative otherwise, and write a superseding ADR for any changed product decision. This redefinition applies wherever this document says "surface," including the dependency rule in Section 11 — in delegated mode, a new dependency is surfaced via the `DECISION-LOG.md` entry accompanying the commit that introduces it.

Three categories are **never resolved autonomously, in any mode**:

1. **Deviations from spec at the trust-critical core** — the encryption boundary, the validator's deterministic logic, and `TierPolicy` enforcement. Implementing these *as specified* requires no permission; *deviating* from their specification parks as a founder-pending blocker in `PROGRESS.md` with an options analysis, and work continues elsewhere.
2. **Cross-doc contradictions the conflict-resolution chain cannot resolve.** Same parking behavior.
3. **Anything on the Section 11 forbidden list.** Forbidden means forbidden; no delegation unlocks it.

## 7. How the foundation docs are kept current

> The bidirectional update discipline. The foundation docs and the code evolve together; neither is the master and neither is the slave. When they drift, they get reconciled.

### When the code teaches us something the docs didn't anticipate

Sometimes during build, the agent or the founder discovers that a foundation doc specified something that turns out to be wrong, incomplete, or surprising in implementation. When this happens:

1. **The agent surfaces the discovery.** "While implementing the active-set computation, I found that the temporal scope evaluation has a subtle issue with daylight saving transitions that the doc doesn't address."
2. **The founder and agent decide together** whether the doc should be updated, the code should be implemented differently, or both.
3. **The doc is updated** with a clear note in its history about what changed and why. The update is committed with a descriptive message.
4. **The code is implemented to match the updated doc.** The doc is the source of truth, even when the doc is itself being updated.

### When the docs need to update for a v2 or v3 readiness gap

The architectural readiness pattern means the foundation docs already specify v2 and v3 behavior in many places. But sometimes implementing v1 surfaces a gap — a v2 feature that the docs assumed would be straightforward but turns out to require v1 changes. When this happens:

1. **The agent surfaces the gap.** "The data model spec assumes household profiles can be added without touching the constraint engine, but I'm finding that the active-set composition logic actually needs household awareness from day one."
2. **The founder decides** whether to implement the v2 readiness in v1 (because it's easier now than later) or to defer it (because the cost is too high right now).
3. **The decision is recorded** either as an update to the foundation doc or as a new open question in the doc's open questions section.

### When the UI evolves after Phase 2

The UI is built first, on mock data, in Phase 2 (per `ui-ux-spec.md` and `roadmap.md` Section 3) — but it is not frozen there. It keeps evolving as real subsystems land: each later phase wires a real subsystem into the existing screens, and real data, real agent latency, and real edge cases routinely surface UI work the mocks didn't (an empty-provenance constraint, a conflict the mock never produced, a four-second think time the `ThinkingIndicator` must hold gracefully). New v2/v3 features also attach *inside* the existing surfaces rather than adding navigation. So continued UI development is expected, not exceptional. The discipline that keeps it coherent rather than drifting into one-off screens:

1. **Cosmetic changes flow freely.** Spacing, color tuning, copy, motion timing, and per-screen layout are deferred to in-build by `ui-ux-spec.md` Section 11; tuning them on-device needs no doc update.
2. **Structural changes flow back into `ui-ux-spec.md`.** A new screen, a new navigation destination, a new reusable component, a changed key flow, or a change to a screen's Orbit MVI `State`/`Intents` contract is a structural change. When the build introduces one, `ui-ux-spec.md` is updated to match — the same bidirectional discipline as every other foundation doc. `ui-ux-spec.md` is a living doc, not a one-time spec.
3. **The State/Intents contract is the seam.** Because mock and real containers expose identical `State` and `Intents` (`ui-ux-spec.md` Section 10), a feature's UI change is expressed as a change to that contract first, then implemented in both the screen and its backing container. This keeps the UI and the subsystems aligned as both evolve.

### The role of ADRs in this flow

When a discovery during build rises to the level of a real architectural decision — not just "this implementation detail is different" but "we need to make a choice that affects how the system works" — that's an ADR. Section 9 specifies the ADR discipline.

### What never happens

- The code does not silently diverge from the docs. If the code and the docs disagree, one of them is wrong, and the disagreement is resolved deliberately.
- The docs are not updated retroactively to match code that was wrong. If the agent built something wrong, the code is fixed; the docs are not edited to pretend the wrong thing was always intended.
- Foundation docs are not deleted. Even when a section becomes obsolete (e.g., a deferred feature actually ships), the section is updated rather than removed, with a note about the change. The history of the foundation is preserved.

## 8. Prompt versioning and iteration

> The `prompts/` directory holds the versioned system prompts for each agent (Curator, Chef, Pantry in v1; Planner, Sourcing, Observer added in v2/v3) plus the user-facing copy in `prompts/copy/`. Prompts evolve faster than the foundation docs and are managed with their own discipline.

### Why prompts are separate from the codebase

The prompts could in principle be string constants in Kotlin files. They are intentionally separate because:

- **Prompts iterate at a different cadence.** A prompt change to fix a Chef agent failure mode is a small, contained, fast change. Treating it as a code change adds ceremony that slows down iteration.
- **Prompts are content, not code.** They're written in natural language for an LLM to read, not in Kotlin for the compiler to parse. Mixing them with code muddles the editing experience.
- **Prompt changes have different review needs.** A prompt change deserves a different kind of review — does this rephrasing actually improve the agent's behavior on the test cases — than a code change.

### How prompts are versioned

Each prompt file in `prompts/` is a Markdown file with this structure:

```markdown
# {Agent name} prompt

**Version:** v1.3
**Last updated:** 2026-04-12
**Maintained by:** the founder

## Change history

- v1.3 (2026-04-12): refined cultural fluency guidance for Punjabi context
- v1.2 (2026-04-08): added explicit handling for IBS-flare contextual constraints
- v1.1 (2026-04-05): clarified the "do not lecture" tone instruction
- v1.0 (2026-04-01): initial version

## System prompt

[the actual prompt text]
```

The version number is informal (just "v1.X") and bumps on every meaningful change. The change history is the most important part — it tells the future-us why each version exists.

### How prompts are tested

When a prompt changes, the test for that change is the **hard cases test suite** from `testing-strategy.md` plus any new test cases that exercise the specific behavior the change was meant to fix. The prompt change is committed only after the tests pass.

For meaningful behavioral changes (not just typo fixes), the agent and founder also run the change against representative test scenarios — Sukhi's day 0 conversation, the rejection feedback flow, a culturally-specific suggestion request — and review the outputs together. This is more like product review than code review.

### How A/B testing of prompts works during alpha

A/B testing of prompts is **deferred** in v1 alpha. The alpha population is small enough (15-25 users per ADR 0001) that statistical comparison between prompt versions isn't meaningful. v1 alpha runs a single prompt version and iterates based on direct founder review of event log exports.

A/B testing becomes feasible in v2 with a larger population, and the infrastructure for it is part of v2 planning. The prompt versioning structure already supports it — multiple versions can coexist and the orchestrator can route between them based on configuration.

## 9. Decision records (ADRs) — when and how to write them

> ADRs (Architectural Decision Records) document the *why* behind significant architectural choices. They are the doc form of the answer to "why did we do it this way?" six months from now. The Cuizine `decisions/` folder already contains 12 ADRs from the foundation work; new ADRs are written during build whenever a decision rises to the level of "this affects how the system works."

### When to write a new ADR

Write a new ADR when any of these conditions are met:

- **A choice between two or more architectural options has been made.** Not "should we use a Map or a List for this internal data structure" (that's local). But "should we add server-side receipt validation for v3 BYOK or stay client-side" (that's architectural).
- **A foundation doc needs to change in a way that affects multiple downstream docs.** The ADR documents the change and references the doc updates.
- **A principle is being clarified or extended.** Not changed (changing a principle requires explicit founder decision and may require more than an ADR), but clarified — "we said 'no dark patterns' and we're now committing that this specifically means 'no in-app countdown timers'."
- **A v2 or v3 readiness gap is being resolved.** When alpha data shows that a v2 feature needs to be specified in more detail than the foundation docs currently do, the ADR documents the new specification.
- **A previously deferred decision is being made.** The deferred decisions in the open questions sections of various docs become ADRs when they're resolved.

### When NOT to write a new ADR

Don't write an ADR for:

- **Local implementation decisions.** Variable names, function structure, file organization within a folder.
- **Bug fixes.** Unless the bug fix surfaces a real architectural change, just commit the fix with a clear message.
- **Refactoring without behavioral change.** Cleanup work doesn't need an ADR.
- **Prompt iterations.** The change history in the prompt file is the record.
- **Anything that's already specified in a foundation doc.** Implementing what the docs already say doesn't need a new ADR — it's just build work.

### How to write an ADR

ADRs follow the format used in the existing 12 ADRs in `decisions/`:

```markdown
# {NNNN} — {Short title}

**Status:** Accepted
**Date:** YYYY-MM-DD

## Context
[Why is this decision needed? What are the constraints?]

## Decision
[What did we decide?]

## Consequences
[Positive, negative, neutral consequences]

## Alternatives considered
[What other options were on the table and why we rejected them]

## Related
[Cross-references to other ADRs and foundation docs]
```

The ADR number is monotonic — the next ADR after 0012 is 0013. Numbers are not reused; if an ADR is later superseded, the new ADR gets a fresh number and references the superseded one in its Related section.

## 10. Testing during build

> The full testing strategy lives in `testing-strategy.md`. This section specifies only the tests-during-build subset: what gets tested as part of the normal build flow, when tests are expected to be written, and the relationship between code and tests during construction.

### Tests are written alongside code, not after

Per Principle 4, a feature without tests is a feature that doesn't work yet. The agent's workflow when implementing a new feature is:

1. **Read the foundation doc** that specifies the feature.
2. **Sketch the test cases** from the foundation doc's specification — what behaviors must this feature exhibit? Each behavior is a test case.
3. **Implement the code** to satisfy those test cases.
4. **Run the tests.** If they pass, commit. If they fail, fix the code (not the tests, unless the tests were wrong).

The agent does not commit code without tests for that code's specified behavior. The agent does not implement multiple features in a single commit without tests for each one.

### The minimum test bar for any commit

Every commit passes:

- All unit tests in the `test/` directory
- The Kotlin linter and static analysis (`ktlint` and Android Lint)
- The format check (`ktlint --format` produces no changes, i.e. code is already formatted)

Failing any of these is a failed commit. The agent does not commit failing builds.

### Integration tests for cross-layer behavior

Some behaviors only emerge from the interaction of multiple layers — the constraint engine and the validator and the orchestrator working together to produce a meal suggestion that respects Sukhi's constraints. These are integration tests, and they live in `test/integration/`. They run as part of the normal test suite.

The hard-cases test suite from `constraint-engine-spec.md` Section 11 lives here, exercising specific scenarios like:

- Sukhi adding a new constraint that conflicts with an existing one
- The conflict resolution algorithm running through all four steps
- The validator catching a violation and triggering regeneration
- A trial expiration causing graceful tier downgrade

### Test data and fixtures

Test fixtures live in `test/fixtures/` and include:

- A canonical Sukhi profile with her full constraint graph from PRD § 4
- A canonical Aisha profile for temporal scope testing
- A canonical Marcus profile for v2 caregiver testing
- A set of food data cache entries covering common ingredients
- A set of suggestion outputs covering passing, failing, and edge cases

These fixtures are versioned with the codebase and are the shared starting point for every test that needs realistic data.

## 11. What is forbidden during build

> The discipline list parallel to Sections 10/11 of `local-first-sync.md` and `monetization-and-billing.md`. Each item is a behavior that would erode the build's quality or violate one of the working principles. The list exists to prevent silent drift when the agent or founder is tempted by a "small reasonable change" that would actually break a discipline.

- **Committing code that fails tests, lints, or format checks.** Every commit passes. Failing builds do not get committed and "fixed in the next commit" — they get fixed in the same commit before it lands.
- **Implementing a feature without tests for its specified behavior.** Per Principle 4. A feature without tests is incomplete, not "to be tested later."
- **Silently diverging from a foundation doc.** If the implementation needs to differ from what the doc says, the divergence is surfaced and the doc is updated or the implementation is changed. Both directions are valid; silent divergence is not.
- **Adding a dependency without surfacing it first.** Per Section 4. Every new dependency requires founder approval, even small ones, even ones that look harmless.
- **Bypassing the layering discipline from Section 3.** UI code does not import Room DAOs directly. Engine code does not depend on Compose or the Android framework. These boundaries exist for good reasons and are not negotiable.
- **Making local decisions that should have been architectural.** When in doubt, surface. The cost of asking is smaller than the cost of silent drift.
- **Editing the foundation docs to match wrong code rather than fixing the code.** If the implementation is wrong, the implementation gets fixed. The docs are not retroactively edited to pretend the wrong thing was intended.
- **Hard-coding values that should be configuration.** Pricing strings, API endpoints, model names, feature flags — none of these are hardcoded inline. They live in `lib/core/config/` and are loaded at startup.
- **Adding logging that captures user content.** Per ADR 0011 and the forbidden behaviors lists in `local-first-sync.md` and `monetization-and-billing.md`, content telemetry is forbidden. Operational telemetry is fine; content telemetry is not.
- **Implementing TODO comments without filing them as open questions.** A TODO in the code is a deferred decision. If the deferral is meaningful, it goes in the open questions section of the relevant foundation doc. If it's trivial, it's resolved before the commit. TODO comments that linger forever are signs of accumulating debt.
- **Skipping the ADR discipline for architectural decisions.** Per Section 9. Architectural decisions get ADRs, period. The discipline of writing them down is what makes the foundation legible six months from now.
- **Implementing v2 or v3 features ahead of their planned ship.** The architectural readiness pattern means we *design* for v2/v3 from day one, but we *implement* the v1 scope in v1. Implementing v2 features in v1 builds technical debt and risks shipping incomplete things.
- **Modifying the alpha bypass mechanism after v2 launch.** Per `monetization-and-billing.md` Section 9. The bypass is a v1-only mechanism that gets removed cleanly at v2 launch, and the removal logic is the only acceptable change to it after that point.

## 12. Open questions

### Repository structure

- **Should we adopt a multi-module Gradle structure with separate modules for the engine, the agents, and the UI?** This would reinforce the layering discipline from Section 3 by making cross-layer imports literally impossible (a module can only see its declared dependencies). The cost is more Gradle setup and build configuration. Tentatively no for v1 (single app module), revisit for v2 if the codebase grows — multi-module is a natural fit for Kotlin/Gradle and worth considering earlier than it would be in a single-package ecosystem.
- **Should we have a separate `samples/` module for code samples that exercise the engine in isolation?** Useful for documentation and onboarding but adds maintenance. Tentatively no for v1, possibly yes if alpha generates community interest.

### Orbit MVI usage

- **How granular should Orbit containers be?** One container per screen? One per feature area that may span multiple screens? A single container exposing everything is too coarse; one per tiny UI value is too fine. The right granularity is per-feature-area but the line is fuzzy. Iterate during alpha based on what feels maintainable.
- **How should shared state across containers be handled?** When two screens need the same domain state (e.g., the active constraint set), the state should live in a repository exposed as a `Flow` and each container collects it, rather than containers sharing state directly. Confirm this pattern holds up in practice during build.
- **Should side effects ever carry domain data, or only navigation/transient-UI signals?** Tentatively side effects are for one-time events (navigate, show snackbar) and all rendered data lives in the state. Confirm during build.

### Agent decision protocol

- **What's the right rhythm for the founder to review the agent's surfaced decisions during alpha?** Per review session? After every meaningful commit? Probably depends on alpha activity. Establish a rhythm that matches the actual pace of surfaced questions.
- **Should there be a "deferred decisions" log that the agent maintains for things it decided locally but flagged as worth revisiting?** Useful for catching the case where a series of local decisions accumulate into something architectural. Tentatively yes, in `decisions/local-deferred.md` or similar.

### Foundation doc maintenance

- **Who reviews foundation doc updates during the build phase?** During the foundation writing, the founder reviewed every change. During build, the volume of doc updates may be too high for that — but the alternative is unreviewed doc changes, which is worse. Probably a lighter-touch review for build-phase doc updates with a periodic full review as the rhythm emerges.
- **Should foundation doc changes be tracked in their own changelog?** The git history captures changes but a human-readable changelog might be more useful for someone joining the project later. Tentatively yes, in `docs/CHANGELOG.md`.

### Prompt iteration

- **What's the right test bar for a prompt change?** Section 8 says "the hard cases test suite plus any new test cases for the specific behavior the change was meant to fix." But the hard cases suite is non-trivial to run and prompt changes happen frequently. Maybe a smaller "fast feedback" subset of the hard cases plus the targeted new tests. Iterate during alpha.

## 13. Cross-references

### What this document references

- `vision.md` — for the trust posture and the working tone
- `PRD.md` — for the user journey that drives the test cases
- `technical-architecture.md` — for the dependency list and the package usage discipline
- `constraint-engine-spec.md` — for the engine layer specifications
- `agent-architecture.md` — for the agent layer contracts and the orchestrator structure
- `data-model.md` — for the persistence layer schema
- `local-first-sync.md` — for the encryption and sync layer behaviors
- `monetization-and-billing.md` — for the billing layer specifications
- `ui-ux-spec.md` — for the UI layer that the Compose/Orbit MVI conventions in Section 4 are built against (the screen-to-state mapping, the component library, the navigation shell)
- ADR 0005 — superseded by ADR 0016 (native Kotlin + Jetpack Compose)
- ADR 0015 — Android-only scope
- ADR 0016 — native Kotlin + Jetpack Compose + Orbit MVI + Room as the framework choice
- ADR 0006 — multi-provider per-agent routing, which the providers folder implements
- ADR 0010 — post-generation validation, which constrains the validator's implementation
- ADR 0011 — optional backend with capability tiers
- ADR 0012 — food data sources, which the food_data folder implements

### What this document defers to deeper-dive docs

- **`testing-strategy.md`** — the full test suite specification, including the hard cases suite, the per-agent eval harness, integration test scenarios, and CI test orchestration
- **`security-and-privacy.md`** — the threat model and the security review process for code changes that touch sensitive layers
- **`roadmap.md`** — the milestone-level plan for which features ship in which version
- **`alpha-feedback-and-iteration.md`** — the alpha process workflow, including how feedback flows into prompt changes and code changes
- **`launch-readiness-checklist.md`** — the go/no-go gates for each launch milestone
- The deployment guide — Firebase project setup, Google Play Console configuration, code signing, release process

### What this document does *not* defer (decisions made here)

- The five working principles (Section 2)
- The repository folder structure with layering discipline (Section 3)
- The Orbit MVI state management commitment (Section 4)
- The Kotlin code style and naming conventions (Section 5)
- The calibrated agent decision protocol with explicit local vs architectural examples (Section 6)
- The bidirectional update discipline between foundation docs and code (Section 7)
- The prompt versioning and iteration model (Section 8)
- The ADR discipline for build-time decisions (Section 9)
- The minimum test bar for any commit (Section 10)
- The 13-item forbidden behaviors list (Section 11)

### How the agent should use this doc

This doc is the agent's operational manual. Sections 2-5 specify how to write code; Section 6 specifies when to ask versus when to decide; Section 7 specifies how to handle drift between code and docs; Sections 8-9 specify how to maintain prompts and ADRs; Section 10 specifies the test bar; Section 11 is the discipline list.

The agent should re-read Section 6 and Section 11 frequently. They are the parts of the doc most likely to be ignored under the pressure of "let me just get this feature done." The discipline of asking when in doubt and never violating the forbidden list is what makes the difference between a build that reflects the foundation faithfully and a build that drifts away from it silently.

When the agent encounters a question this doc does not answer, the discipline from PRD § 9 applies — but with an important addition for build conventions: the question itself is often a signal that this doc needs to be updated. If the agent finds itself wishing for guidance that isn't in this doc, the agent surfaces the gap and the doc gets updated. Build conventions are meant to be a living document that grows with the codebase.

---

*End of `build-conventions.md` v1 (initial draft). Next revision will incorporate any conventions that surface during the v1 alpha build, any patterns that emerge from real implementation against this foundation, and any decisions made during the first weeks of code generation. The principles in Section 2 are stable; the conventions in Sections 3-5 are expected to refine as the codebase grows; the agent decision protocol in Section 6 is expected to be the most-iterated section based on real founder-agent interaction patterns.*
