# Cuizine — Technical Architecture

> The system map. This document is the bird's-eye view that lets a developer (human or AI) read it once and have a correct mental model of how Cuizine fits together. It names components, shows relationships, points at deeper-dive docs for specifications. It is not the implementation guide and not the line-by-line spec — those live in the deeper docs.

## 1. Purpose & how to read this doc

This document specifies the **system architecture for Cuizine v1** (the closed Canadian alpha) with explicit forward-references to v2 and v3 evolution. It assumes you have read `vision.md`, `PRD.md`, and the ADRs. Concepts established there are not re-derived here.

This document defines: the major components of the system and what each one does; how components communicate with each other; where data lives at every moment (the trust boundaries); what third-party services and libraries Cuizine depends on; how the app is built, distributed, and updated.

This document does **not** define the constraint engine schema (see `constraint-engine-spec.md`), specific agent prompts (see `agent-architecture.md` and `prompts/`), data model field definitions (see `data-model.md`), sync conflict resolution algorithms (see `local-first-sync.md`), or per-agent prompt templates and evaluation harnesses.

When this doc and a deeper-dive doc disagree, the deeper-dive doc wins for matters of specification, but this doc wins for matters of structure and component relationships.

## 2. System overview at three altitudes

### 30,000 feet — what Cuizine is, structurally

**Cuizine is a local-first native Android application (Kotlin + Jetpack Compose) with an optional Firebase backend that, when enabled, proxies multi-agent LLM calls to Anthropic, Google, and OpenAI.** Every user's data lives on their device as the source of truth. Users who sign in get cross-device encrypted sync (the cloud holds encrypted blobs it cannot read) and access to AI features through a backend proxy. Users who don't sign in get a fully functional local-first product with no Cuizine servers in the picture. The intelligence — the constraint engine and the multi-agent system — runs as a coordinated set of agents, each routed to the cheapest LLM provider that meets its capability requirements.

### 5,000 feet — the major subsystems

Six conceptually distinct subsystems share a single native Android codebase. (1) **Client UI** — Jetpack Compose screens, navigation, the conversation surfaces, with Orbit MVI ViewModels holding presentation state. (2) **Local data layer** — SQLite via Room, holding constraint graph, recipes, history, pantry, preferences. (3) **Constraint engine** — the structured representation of the user's full picture, a typed temporal conflict-aware graph that downstream agents read from and the Curator writes to. (4) **Agent orchestration** — routes user intents to agents (Curator, Chef, Pantry in v1), runs the deterministic post-generation validator. (5) **Provider abstraction** — `ModelProvider` interface and three adapters (Anthropic, Gemini, OpenAI). (6) **Optional Firebase backend** — Auth, Firestore for encrypted blob sync, Cloud Functions for the LLM proxy, all activating only when the user signs in.

### 500 feet — the components

The six subsystems decompose into roughly fifteen named components in Section 3 below. From this altitude the system looks like a layered architecture: UI on top, then orchestration, then engine and agents, then data and providers, with the backend as a sidecar that activates conditionally.

## 3. The component map

Components grouped by subsystem. Each has a name, a one-line responsibility, and the ADR or PRD section that justifies it.

### Subsystem 1: Client UI layer

- **Onboarding & Constraint Conversation** — Hosts the user-facing surface where the Curator agent runs the guided dialogue from PRD § 4. *ADR 0002; PRD § 4.*
- **Suggestion Surface** — Where Sukhi asks for a meal and sees the Chef's response. Includes the low-friction rejection feedback affordance. *PRD § 4 and § 5.*
- **Settings & Profile UI** — Minimal in v1. Export data, sign out, view profile. No model selection, no token counters. *PRD § 5; ADR 0006; ADR 0007.*
- **Sign-in / Sign-out Flow** — Optional path. v1 ships Sign in with Google. Activates the backend subsystem. *ADR 0011.*

### Subsystem 2: Local data layer

- **SQLite database via Room** — Single local source of truth. Schema versioned from day one. *ADR 0016.*
- **Constraint Graph storage** — Read by all agents; written only by the Curator. Detailed schema in `constraint-engine-spec.md`. *Pillar 1; ADR 0009; ADR 0010.*
- **Recipes & Pantry storage** — Imported and accepted recipes, manual pantry entries, inferred pantry context. *PRD § 5.*
- **History & event logging** — Local-only event log. Exportable by user; never sent to Cuizine without explicit user action. *PRD § 5; vision.md trust posture.*
- **Encryption layer** — Wraps sync-bound data with client-side AES before any cloud upload. Activates only when sync is enabled. *ADR 0011.*

### Subsystem 3: Constraint engine

- **Constraint engine API** — The interface the Curator uses to write constraints, the Chef and Pantry use to read them, and the validator uses to check suggestions. Detailed in `constraint-engine-spec.md`. *Pillar 1; ADRs 0004, 0009, 0010.*
- **Food Data Provider** — The abstraction layer over the three-layer food data architecture (local cache → curated bundle → external sources USDA and Open Food Facts), with severity-scoped AI fallback for unknown ingredients. Presents a unified API to the rest of the engine: "give me the canonical entry for this ingredient name or barcode." Internal multi-layer query orchestration is invisible to callers, same pattern as `ModelProvider`. The validator reads from this layer to check suggestions deterministically against the constraint graph. *Justified by: ADR 0012; pillar 1; ADR 0010 (validator's deterministic ground truth).*

### Subsystem 4: Agent orchestration layer

- **Orchestrator** — Routes user intents to agents, manages the validation loop, handles fallback paths. Stateless beyond the current request. *PRD § 5; ADRs 0006, 0010.*
- **Curator agent** — Hosts the constraint conversation, parses natural language into structured constraints, manages constraint graph updates from free-text input. Read/write authority over the constraint graph. *Pillar 1; PRD § 4 and § 5; ADRs 0002, 0009.*
- **Chef agent** — Generates meal suggestions in the user's cultural idiom, respecting the constraint graph. Read-only access to constraint graph. *Pillar 5; PRD § 4 and § 5; ADR 0002.*
- **Pantry agent (lightweight in v1)** — Fuzzy ingredient matching and disambiguation. v1 mostly uses inferred-pantry-context heuristic. *PRD § 5; pillar 2 in degraded form.*
- **Post-Generation Validator** — Deterministic rule checker that validates every Chef output against the constraint graph before the user sees it. **Non-optional, non-configurable, non-skippable.** Triggers regeneration on failure with bounded retries; surfaces honest fallback message when retries exhaust. *ADR 0010.*

#### Future agents (v2 and v3)

The v1 agent set is deliberately small (Curator, Chef, Pantry) to keep the alpha focused on proving the constraint engine. Three additional agents are planned for v2 and v3, each slotting into the same orchestration architecture as a new pluggable component — they conform to the same agent interface, take a profile (or set of profiles) as input, return structured output, and are routed by the orchestrator through the same `ModelProvider` interface. **The orchestrator, the constraint engine API, the validator, and the data layer do not change to accommodate them.** Adding a new agent is writing a new agent class, registering it with the orchestrator, and authoring its prompts — not refactoring anything below the agent layer. This is the architectural readiness discipline applied to the agent set.

- **Planner agent** *(v2)* — Reasons across days (and across multiple profiles in the Family tier) to produce week-ahead meal plans that minimize constraint conflicts, respect cultural patterns, and balance the household's dietary needs over time rather than per-meal. The Planner calls the Chef as a sub-routine to generate the individual meals in the plan. **Validator interaction:** the Post-Generation Validator runs on each meal in the plan, not on the plan as a whole — every meal must pass validation before the plan is shown to the user. *Justified by: pillar 1, pillar 2, pillar 4; PRD § 6 (deferred from v1).*
- **Sourcing agent** *(v2)* — Translates Chef and Planner outputs into shopping lists and Instacart orders. Mostly tool-calling against the Instacart API; minimal reasoning beyond ingredient deduplication and quantity aggregation. **Validator interaction:** the Sourcing agent does *not* require separate validation, because the underlying meals (from Chef or Planner) have already been validated. The Sourcing agent operates on already-trusted output. *Justified by: pillar 2; ADR 0003; PRD § 6 (deferred from v1).*
- **Observer agent** *(v3)* — Runs asynchronously over the user's accumulated eating history and surfaces long-term trends, predictions, and gentle warnings (rising sodium intake, drift from target macros, patterns the user might not notice themselves). **Validator interaction:** the Observer lives outside the validator path entirely because it produces *insights and warnings about long-term trends*, not meal suggestions. Insights are reviewed for tone and accuracy through a different mechanism (still TBD, deferred to v3 planning). *Justified by: pillar 3; ADR 0007 (deferred to v3 because it requires accumulated user data to be meaningful); PRD § 6 (explicitly out of v1).*

The v2 and v3 versions of Subsystem 4 contain five and six agents respectively — but the *shape* of the subsystem stays identical. The orchestrator at the top, the validator below the agents that produce meal suggestions, the agents themselves as pluggable components in between.

### Subsystem 5: Provider abstraction layer

- **`ModelProvider` interface** — The contract every adapter implements. Each agent declares capability requirements; orchestrator routes through the interface. *ADR 0006.*
- **Anthropic adapter** — Calls Claude (Opus, Sonnet, Haiku). Default for Curator, Chef, and validator's structured-extraction sub-call in v1. Zero-data-retention enabled. *ADR 0006; ADR 0010.*
- **Gemini adapter** — Calls Gemini (Pro, Flash). Default for Pantry agent's cheap fuzzy matching; candidate for Chef. *ADR 0006.*
- **OpenAI adapter** — Calls GPT models. Built in v1 even though minimally used, so v2 has it ready. *ADR 0006.*

### Subsystem 6: Optional backend (Firebase, signed-in users only)

- **Firebase Authentication** — Identity layer. v1 ships Sign in with Google. *ADR 0011.*
- **Cloud Firestore (as encrypted blob store)** — Holds the user's encrypted container for cross-device sync. Cuizine cannot read the contents. *ADR 0011.*
- **Cloud Functions (LLM proxy)** — When a signed-in user makes an LLM call, the Cloud Function holds provider API keys and forwards the request upstream. Does not log inference content. *ADRs 0011, 0006.*

## 4. Data flow walkthroughs

Three concrete walkthroughs of real operations end-to-end. Each is narrated as Sukhi specifically because concrete grounding produces better thinking.

### Walkthrough A: Sukhi completes the constraint conversation (signed-out path)

Sukhi opens Cuizine for the first time. She has not signed in. The app is in **signed-out mode** — Firebase is uninitialized, no backend services are reachable.

1. The **Onboarding component** renders the first screen. One sentence, one tap. No backend interaction.
2. Sukhi taps "Let's start with what you cook." The UI navigates to the Constraint Conversation screen.
3. The **Orchestrator** routes the conversation request to the **Curator agent**.
4. The Curator needs an LLM call. Because Sukhi is signed out, the Anthropic adapter calls the Anthropic API **directly from the device**, using a per-device API key embedded in the v1 alpha APK. *(This direct-from-device key embedding is acceptable in v1 alpha because the alpha population is small and trusted; v2 hardens this by requiring sign-in for AI features per ADR 0011's matrix.)*
5. Sukhi answers the first question in free text. The Curator makes another Anthropic call to parse her answer into structured cultural-context fields.
6. The structured fields are written to the **Constraint Graph storage** in local SQLite via the **Constraint Engine API**.
7. Steps 5-6 repeat for each question.
8. At the end, Sukhi has a complete structured constraint graph. **At no point did data leave Sukhi's device, except as ephemeral inference inputs to Anthropic's API for the brief moment of each call** — and per zero-data-retention, those inputs are not retained anywhere.

**Trust posture:** The strongest possible expression of the local-first commitment. Inference traffic to Anthropic, no persistent data anywhere except Sukhi's device. Cuizine's backend uninvolved.

### Walkthrough B: Sukhi requests a meal suggestion and receives one (signed-in path)

Sukhi opens Cuizine on day 4. She is now signed in (alpha is full-feature paid trial per ADR 0007, so signing in is required for AI features). She taps the suggestion surface.

1. The **Suggestion Surface** captures her request.
2. The **Orchestrator** routes the request to the **Chef agent**.
3. The Chef reads the active constraint graph from the **Constraint Engine API** — diabetes and IBS-M constraints, cultural context, cooking-for context (family of four), recent history, current contextual state.
4. The Chef constructs a prompt and calls the **`ModelProvider` interface**, which routes to the **Anthropic adapter**.
5. Because Sukhi is signed in, the adapter does **not** call Anthropic directly. It calls the **Cloud Function LLM proxy**, which holds the Anthropic API key, forwards upstream with zero-data-retention enabled, and returns the response.
6. The Chef receives the suggestion in natural language.
7. **The orchestrator now invokes the Post-Generation Validator** (per ADR 0010). The validator parses the ingredient list using a small structured-extraction LLM call (also routed through the Cloud Function proxy, but to a cheaper model — Claude Haiku or Gemini Flash). The validator deterministically checks each ingredient against the constraint graph by severity tier.
8. The suggestion passes (no diabetes-violating sugars, no IBS-triggering onion or garlic). The orchestrator returns it to the UI.
9. The Suggestion Surface displays the meal in Sukhi's cultural idiom, with the rejection-feedback affordance unobtrusive but present.
10. The suggestion is logged to the local **History & event logging** component. *Not* uploaded to Cuizine. If Sukhi later wants to share usage logs with the founder, she exports them explicitly per PRD § 5.

**Trust posture:** Inference content flowed device → Cloud Function proxy → Anthropic API → back. The Cloud Function is operated by Cuizine and is technically capable of seeing the request in flight, though it does not log content. This is slightly weaker than Walkthrough A, which is the price of the convenience signing in provides. The user has chosen this trade. **User data (constraint graph, history, recipes) never left Sukhi's device — only the ephemeral inference request did.**

### Walkthrough C: Sukhi rejects a suggestion and gets a better one

Day 5. Sukhi asks for a meal. The Chef suggests chana masala. Sukhi taps "this isn't quite right" → "don't have the ingredients."

1. The Suggestion Surface captures the rejection with reason.
2. The orchestrator passes the rejection signal to the **Curator agent**, which updates the constraint graph with a temporary contextual signal: "exclude chickpeas this session." Short expiration (session-scoped, not a permanent constraint).
3. The orchestrator re-invokes the **Chef agent** with updated constraint state. The Chef now knows to avoid chickpeas in this regeneration.
4. The Chef generates a new suggestion: aloo methi with phulka roti.
5. Steps 7-9 from Walkthrough B repeat. Validator passes the new suggestion. UI displays it.
6. Sukhi accepts. The History layer logs both the rejected first suggestion (with reason) and the accepted second suggestion. **This data is the second-most-important feature in v1 after the constraint conversation itself** (per PRD § 4) because it is how Cuizine learns to be Sukhi's chef rather than a generic chef who knows her rules.

**Trust posture:** Identical to Walkthrough B with one additional Cloud Function proxy call. No additional data leaves the device.

## 5. Trust boundaries map

> This section is the architectural expression of vision.md's trust posture. For every kind of data Cuizine handles, it answers: where does this data live, who can read it, when does it move, and what is the absolute worst-case exposure? The discipline of this section is *no hand-waving*. If the answer is "it's encrypted," that's only a useful answer when paired with "encrypted by whom, with what key, derived from what, recoverable how." Vague trust claims age badly; specific ones earn trust.

### What kinds of data Cuizine handles

Cuizine touches several categorically distinct kinds of data, each with its own trust profile:

- **User content** — the constraint graph, recipes, history, pantry, household structure. The most sensitive data Cuizine handles. Includes medical conditions, religious practices, household composition, and cultural identity. Never leaves the user's device in readable form. Ever.
- **Inference requests** — the structured prompts sent to LLM providers when a signed-in user invokes an agent. Contains user content as input (the constraint graph slice relevant to this request). Ephemeral by design — exists for the duration of the request, retained nowhere afterward.
- **Account metadata** — email address, Google account ID, account creation timestamp, last sign-in timestamp. Held by Firebase Authentication. Visible to Cuizine operators in aggregate (Firebase console).
- **Operational telemetry** — Cloud Function invocation counts, error rates, latency percentiles. Aggregate, anonymous, no user content. Held by Firebase observability.
- **Encrypted backup blobs** — the user's full container, encrypted client-side, stored in Firestore for cross-device sync. Visible to Cuizine as opaque bytes; never decryptable by Cuizine.
- **Local event logs** — meal suggestions, acceptances, rejections, constraint updates. Lives only on the user's device. Exportable by the user when they want to share with the founder during alpha. Never auto-uploaded.

### The boundaries diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     SUKHI'S DEVICE                           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  USER CONTENT (plaintext, the source of truth)        │  │
│  │  • Constraint graph                                   │  │
│  │  • Recipes, history, pantry                           │  │
│  │  • Household structure (v2)                           │  │
│  │  • Local event logs                                   │  │
│  │  Encryption: at rest (Android keystore)               │  │
│  │  Visibility: Sukhi only                               │  │
│  └───────────────────────────────────────────────────────┘  │
│                            │                                 │
│                  ┌─────────┴─────────┐                       │
│                  │  Encryption layer  │ ← signed-in only     │
│                  │  (client-side AES) │                      │
│                  └─────────┬─────────┘                       │
└────────────────────────────┼─────────────────────────────────┘
                             │
                             ▼ (encrypted blob, opaque)
┌─────────────────────────────────────────────────────────────┐
│                  CUIZINE BACKEND (Firebase)                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Firestore  │  │     Auth     │  │   Cloud      │      │
│  │  ENCRYPTED   │  │  (metadata)  │  │  Functions   │      │
│  │   BLOBS      │  │              │  │ (LLM proxy)  │      │
│  │              │  │  Email,      │  │              │      │
│  │  Cuizine     │  │  Google ID   │  │  Inference   │      │
│  │  CANNOT      │  │  only        │  │  in flight,  │      │
│  │  READ        │  │              │  │  not logged  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└────────────────────────────┼─────────────────────────────────┘
                             │
                             ▼ (inference request, ephemeral)
┌─────────────────────────────────────────────────────────────┐
│              LLM PROVIDER (Anthropic / Gemini / OpenAI)      │
│  Receives inference content during the call.                │
│  Anthropic API: zero-data-retention enabled.                │
│  Other providers: zero-retention or equivalent enabled       │
│    where supported; flagged in security-and-privacy.md.     │
└─────────────────────────────────────────────────────────────┘
```

### What Cuizine can and cannot see, in plain language

**What Cuizine the company can see, even with full access to its own servers:**

- The fact that an account exists, when it was created, when it last signed in
- The size and update timestamps of a user's encrypted backup blob
- Aggregate operational metrics (request counts, error rates, latency)
- Cloud Function invocation logs (timestamps, response codes, no inference content)

**What Cuizine the company cannot see, ever, even if compelled:**

- The contents of any user's constraint graph (encrypted at rest on device, encrypted client-side before sync)
- The medical conditions, religious practices, household members, or cultural identity of any user
- The recipes any user has saved, generated, or rejected
- The pantry contents of any user
- The history of meals any user has cooked or planned
- The contents of any inference request after it has been forwarded upstream (the Cloud Function proxy holds the request only in memory during the forwarding moment, never logs it, never persists it)

**The two places where the trust posture is weakest, named honestly:**

1. **The Cloud Function LLM proxy holds inference content in memory during the forwarding moment.** A determined attacker who compromised the Cloud Function infrastructure during a request could see that request. We mitigate this by not logging, not persisting, and using Firebase's standard security infrastructure — but we do not pretend the moment of forwarding is invisible. Signed-out users avoid this entirely (Walkthrough A).

2. **Anthropic, Google, and OpenAI receive inference content during the call itself.** Zero-data-retention is enabled where supported. We are trusting these providers' commitments. Users who want zero trust in upstream providers must use signed-out mode in v1 (and BYOK in v3 once it ships per ADR 0008).

### The trust posture by quadrant (from ADR 0011)

| | Signed out | Signed in |
|---|---|---|
| **Free tier** | Strongest. No Cuizine backend in picture, no inference traffic at all (free tier excludes AI per ADR 0007). Cuizine sees nothing. | Strong. Encrypted blob sync only. Cuizine sees account metadata and blob sizes; cannot read content. No inference traffic (still free tier). |
| **Paid tier** | v1/v2: not possible. v3: BYOK enables this — pure local-first AI with user-supplied keys, no Cuizine backend. | Strong but with the two named weaknesses above. The convenience trade-off the user has chosen by signing in. |

The free-tier-signed-out cell is the strongest possible expression of the trust posture and is genuinely available to any user who chooses it.

## 6. Third-party dependencies

> This section is the deliberate, named list of every external service or library Cuizine depends on. The discipline is: nothing on this list happens by accident. The agent is forbidden from adding dependencies not on this list (or its successor revisions) without surfacing the addition for explicit approval. The cost of this discipline is small; the cost of the failure mode it prevents (Cuizine somehow ending up depending on 47 random packages) is large.

### Cloud services

- **Anthropic API** — Primary LLM provider for Curator, Chef, and the validator's structured-extraction sub-call. Zero-data-retention enabled. *Rationale: ADR 0006; strongest reasoning quality for the heavy agents.*
- **Google AI API (Gemini)** — Default for Pantry agent fuzzy matching (Gemini Flash, low cost). Candidate for Chef cultural-fluency tasks. *Rationale: ADR 0006.*
- **OpenAI API** — Adapter built but minimally used in v1. Standby for tool-calling-heavy tasks in v2 (Sourcing agent against Instacart). *Rationale: ADR 0006; v2 readiness.*
- **USDA FoodData Central API** — Primary external source for raw and minimally-processed ingredients with high-quality nutritional composition data. Free with API key registration. *Rationale: ADR 0012.*
- **Open Food Facts API** — Primary external source for packaged products and global ingredient coverage. Open-data licensed (commercial use allowed). *Rationale: ADR 0012.*
- **Firebase Authentication** — Sign in with Google in v1. *Rationale: ADR 0011.*
- **Cloud Firestore** — Encrypted blob storage for sync (signed-in users). Used as a blob store, not a queryable database. *Rationale: ADR 0011.*
- **Firebase Cloud Functions** — LLM proxy layer for signed-in users; holds upstream provider API keys. *Rationale: ADR 0011.*
- **Instacart API** — *v2 only.* Sourcing agent's grocery integration. Not used in v1. *Rationale: ADR 0003.*

### Android dependencies (v1)

Each dependency below is named, justified, and tagged with its role. The agent is responsible for keeping this list current as dependencies are added or removed; new additions require an `open-questions.md` entry and ideally a brief discussion before adoption. Versions are centralized in the Gradle version catalog (`gradle/libs.versions.toml`).

- **Kotlin + Jetpack Compose** (Compose UI, Material 3, Navigation Compose) — The language and UI toolkit. *Rationale: ADR 0016.*
- **Room** (`androidx.room`: runtime, ktx, compiler via KSP) — Type-safe SQLite layer for the local data layer. *Rationale: ADR 0016.*
- **Orbit MVI** (`org.orbit-mvi`: orbit-core, orbit-viewmodel, orbit-compose) — State management layered on ViewModel + StateFlow. *Rationale: ADR 0016; details in `build-conventions.md` Section 4.*
- **Hilt** (`com.google.dagger:hilt-android`) — Dependency injection. *Rationale: ADR 0016.*
- **Firebase Android SDK** (`firebase-auth-ktx`, `firebase-firestore-ktx`, `firebase-functions-ktx` via the Firebase BoM) — Official Firebase libraries for Auth, Firestore blob sync, and the Cloud Functions proxy. *Rationale: ADR 0011.*
- **Credential Manager / Sign in with Google** (`androidx.credentials`, `googleid`) — The Sign in with Google flow on modern Android. *Rationale: ADR 0011.*
- **Tink** (`com.google.crypto.tink:tink-android`) — Client-side AES-256-GCM encryption for sync blobs and Argon2id-based key derivation. Library choice confirmable in `local-first-sync.md` if a stronger option emerges. *Rationale: ADR 0011.*
- **Retrofit + OkHttp** (with the `kotlinx-serialization` converter) — HTTP clients for the Food Data Provider's USDA and Open Food Facts lookups, for direct LLM API calls in signed-out mode, and as the transport for Cloud Functions calls in signed-in mode. The Open Food Facts and USDA APIs are accessed via their REST endpoints directly. *Rationale: ADR 0012, ADR 0011.*
- **Kotlin Coroutines + Flow** (`kotlinx-coroutines-core`, `kotlinx-coroutines-android`) — Asynchrony and reactive streams across all layers. *Rationale: ADR 0016.*
- **kotlinx.serialization** — JSON serialization for the discriminated-union constraint payloads, the food data bundle, agent contracts, and the export format. *Rationale: data-model.md, ADR 0012.*
- **Google Play Billing Library** (`com.android.billingclient:billing-ktx`) — The paid-tier billing integration (introduced in v2). *Rationale: ADR 0007; details in `monetization-and-billing.md` Section 6.*
- **JSON serialization** — `json_serializable` and `freezed` (or equivalent) for type-safe model classes. *Rationale: this doc.*

### Things explicitly *not* depended on

- **No analytics package.** Not Firebase Analytics, not Mixpanel, not Amplitude, not anything. *Rationale: vision.md trust posture; ADR 0011.*
- **No ad SDKs of any kind.** Not now, not ever. *Rationale: vision.md and the company's stance on ads in Anthropic products is the same stance we take for ours.*
- **No A/B testing framework.** Not Firebase Remote Config, not Optimizely, not anything that would silently change the product behind users' backs. *Rationale: trust posture; calm-precise-warm tone is a single voice, not an experimentally-optimized one.*
- **No social SDKs.** Not Facebook, not Twitter/X, not anything. *Rationale: vision.md "Not a social network."*
- **No crash reporting in v1.** Crashlytics may return in v2 with careful redaction. *Rationale: ADR 0011; v1 alpha is small enough to debug from voluntary user-exported logs.*
- **No on-device ML / TensorFlow Lite / ONNX Runtime in v1.** All AI inference is via cloud APIs in v1 and v2. On-device inference is a v3 consideration per the deferred discussion in ADR 0006. *Rationale: ADR 0006.*

## 7. Deployment topology

### v1 — APK distribution to closed alpha

The v1 alpha is distributed as **a sideloaded APK file**, shared directly with each alpha user via a link the founder sends. There is **no Play Store presence in v1.** This matches ADR 0001's closed-alpha posture and avoids the operational overhead of Play Store review for a build that will never face strangers.

The build pipeline for v1 is intentionally simple: the founder builds the APK locally (or via a CI job, depending on `build-conventions.md`), uploads it to a private location, and sends links to alpha users when a new version ships. Update notifications happen out of band (a message to the alpha group) — there is no in-app update mechanism in v1. Users update by installing a newer APK on top of the older one; the local SQLite database persists across updates because the schema is versioned and migrate-able from day one.

Firebase services (Auth, Firestore, Cloud Functions) are deployed to a single Firebase project for v1. The project is in the **Spark plan (free tier)** for the alpha duration; the free quotas are well above what 15-25 alpha users will consume.

### v2 — Play Store launch (North America)

v2 ships through the **Google Play Store**. This requires a normal app submission process (privacy policy, store listing, age rating, content rating) which is itself meaningful work — the privacy policy in particular needs to accurately reflect the trust posture from ADR 0011, and the store listing copy needs to match the calm-precise-warm voice from vision.md.

The Firebase project may need to upgrade from Spark to **Blaze (pay-as-you-go)** during v2 if usage exceeds free tier limits. Cost projections are a v2-planning concern, not a v1 concern, but the architectural discipline is: nothing changes between v1 and v2 except for which Firebase plan we're on and which distribution channel we use. The codebase is the same; the deployment is the same.

v2 introduces the **paid tier billing** through Google Play's standard billing infrastructure (the Google Play Billing Library). No third-party billing service. This is the only place Cuizine handles money, and we let Google Play handle it.

### v3 — Global expansion (Android, new geographies)

v3 expands Cuizine to new English-speaking geographies (UK, EU, and other markets) — **Android-only, in new regions** (per ADR 0015). There is no iOS, web, or other-platform target. Global expansion is geographic, not cross-platform: the same native Android app, distributed via Google Play in new countries, with the jurisdictional compliance work (GDPR for UK/EU per `security-and-privacy.md` Section 7), localization, and any region-specific Firestore configuration that entails.

v3 also introduces the **non-Instacart sourcing strategy** for markets Instacart doesn't serve (UK, Australia, etc.). This is a separate `SourcingProvider` implementation per ADR 0003's clean-interface discipline.

### What does *not* change across v1 → v2 → v3

The core architecture — six subsystems, the constraint engine, the **agent orchestration architecture** (orchestrator pattern, `ModelProvider` routing, post-generation validator, constraint engine API, the boundaries between subsystems), the trust boundaries, the deployment discipline — is identical across all three versions. v1 to v2 to v3 is a sequence of *enabling more features, more agents, and more distribution channels within the same architecture*, not *rebuilding the system*.

Specifically: the orchestration architecture is invariant, but the **set of agents inside that architecture grows** — three in v1 (Curator, Chef, Pantry), five in v2 (adding Planner and Sourcing), six in v3 (adding Observer). Each new agent is a pluggable component that conforms to the existing agent interface, registers with the existing orchestrator, and is routed through the existing `ModelProvider` interface. Adding an agent does not require changes to the constraint engine, the validator, the data layer, or the trust boundaries.

This is the architectural readiness discipline from every other ADR (Canada-first / NA-ready, single-profile / multi-profile-ready, Anthropic-first / multi-provider-ready, signed-out / signed-in, three-agents / six-agents-ready) applied at the deployment layer. (Note: cross-platform readiness is no longer part of this list — per ADR 0015, Cuizine is Android-only across all versions, and v3 expansion is geographic rather than cross-platform.)

## 7.5 v2 and v3 architectural deltas

> Section 7's "What does *not* change" subsection names the architectural invariants — orchestrator pattern, validator, constraint engine API, trust boundaries, deployment discipline. This section names what *does* change, version by version, so the agent has a clear picture of how the system grows. Each delta is small enough to be added without rebuilding anything, which is the discipline that makes the v1 → v2 → v3 evolution affordable. If a future delta starts requiring rewrites of the existing components, that's a signal that the architectural readiness has eroded and we should pause and revisit before continuing.

### v2 deltas (relative to v1)

**New agents added to Subsystem 4 (orchestration layer):**

- **Planner agent** registered with the orchestrator. Calls the Chef as a sub-routine. Validator runs per-meal on Planner output (per Section 3's Future Agents subsection).
- **Sourcing agent** registered with the orchestrator. Mostly tool-calling against the Instacart API. Does not require validation pass-through.

**Pantry agent grows up:**

The v1 Pantry agent is intentionally lightweight (manual entry only, inferred-context heuristic for the Chef). v2 is when the Pantry agent becomes a real first-class capability with three new ingestion mechanisms bundled together:

- **Barcode scanning** — Camera-based barcode capture for packaged goods, looking up products via the Food Data Provider's Open Food Facts integration (per ADR 0012). Adds packaged products to the user's pantry with full ingredient and allergen detail without manual typing.
- **Receipt scanning** — Camera capture of grocery receipts, OCR + AI parsing to extract purchased items, adds them to the pantry with quantities and dates.
- **Fridge photo ingestion** — Multimodal AI capture of the contents of the user's fridge or pantry shelves, identifying ingredients and quantities visually.

All three capture mechanisms write to the same Pantry storage and feed the same Pantry agent's reasoning. They ship together in v2 as part of the "lightweight Pantry → real Pantry" transition rather than being added one at a time, because the underlying camera permissions, vision pipelines, and ingestion UX are shared infrastructure. The food data layer from ADR 0012 is what makes all three work — barcode scans, OCR-extracted items, and vision-identified ingredients all flow through the Food Data Provider for canonical resolution.

**New external dependency:**

- **Instacart API** moves from "listed but unused" (v1) to "actively integrated" (v2). The Sourcing agent is the only component that talks to it. The Sourcing API client lives behind a `SourcingProvider` interface (per ADR 0003) so the v3 non-Instacart strategy can slot in without refactoring the agent.

**Backend evolution:**

- **Subscription billing** added. Cuizine Free / Cuizine / Cuizine Family tiers go live (per ADR 0007). Billing handled through the Google Play Billing Library — Cuizine itself never touches money. The backend gains a thin tier-state field on the user account that the client checks to decide which features are available.
- **Multi-profile support activates** in the data layer. Per ADR 0004, the schema was profile-aware from v1 (every profile had an explicit `owner` field, every agent took a profile-or-set as input). v2 simply turns on the UI and orchestration paths that let a primary user create dependent profiles, compose households, and have the Planner reason across them. **No data layer rewrites required** — this is the architectural readiness from ADR 0004 paying off.
- **Cloud Functions LLM proxy expands** to handle the new agent types' calls. Same architectural shape, new routing rules.

**New UI surfaces:**

- **Family tier onboarding** — adding dependent profiles with the three flow variants from ADR 0004 (silent, consent-aware, recommended-disclosure).
- **Household planning surface** — a new view where the user picks which profiles are at the table tonight and the Planner generates a meal that works for everyone.
- **Week-ahead planning surface** — the Planner agent's main UI, showing seven days of meals with the ability to swap individual meals or regenerate the whole week.
- **Shopping list / Instacart handoff surface** — the Sourcing agent's UI, where the user reviews the auto-generated shopping list before sending it to Instacart.
- **Tier and billing UI** — sign-up flow for paid tiers, account management, the 14-day-trial mechanics from ADR 0007.

**Trust boundary changes:**

- **No changes to the trust boundaries diagram from Section 5.** Every v2 addition lives within the existing trust model. Multi-profile data still lives in the primary user's encrypted container (per ADR 0004 — no cross-account data sharing in v2). Instacart is a third-party API the Sourcing agent calls; the user's full constraint graph is *not* sent to Instacart (only ingredient names and quantities), and this is enforced at the Sourcing agent's boundary.
- **One thing to flag honestly:** the Sourcing agent's Instacart calls go through the Cloud Function proxy (so the upstream Instacart API key is not exposed on the device), which means the proxy now also handles Instacart traffic in addition to LLM traffic. The proxy still does not log content, but its surface area has grown. Worth a security review when v2 ships.

**New deployment requirements:**

- **Play Store submission** with full privacy policy, store listing, content rating, age rating.
- **Firebase project may upgrade from Spark to Blaze** if usage exceeds free tier limits.
- **Privacy policy must be revised** to accurately describe the multi-profile data model, the Instacart integration, and the billing relationship.

**Possibly added in v2 (still TBD):**

- **Crashlytics** (per ADR 0011's note that Crashlytics may return in v2 with careful redaction). If added, it requires user-content field redaction logic that the v1 alpha did not need.

### v3 deltas (relative to v2)

**New agents added to Subsystem 4:**

- **Observer agent** registered with the orchestrator. Runs asynchronously over the user's accumulated history. Lives outside the validator path (per Section 3's Future Agents subsection). Requires meaningful accumulated user data to be useful — which is why it cannot honestly ship before v3.

**New backend capabilities:**

- **Cross-account peer coordination** for linked partner profiles (per ADR 0004). This is the **first real cross-account data sharing in Cuizine's history** and it requires backend infrastructure that v2 does not have. The shape of this is: when Sukhi adds Harpreet as a linked partner (his own account, his own profile), there must be a coordination layer that lets Sukhi's planning operations include a peer-appropriate view of Harpreet's constraints without giving Sukhi raw access to his full profile. This likely requires a new permissions model in Firestore and a peer-encrypted sharing protocol on top of the existing client-side encryption. **It is the largest single architectural change of v3** and probably deserves its own ADR when v3 planning happens.
- **BYOK support** (per ADR 0008). A new path in the `ModelProvider` interface for "user-supplied API key" that bypasses the Cloud Function proxy and calls the upstream provider directly from the device using the user's key. This enables the previously-impossible signed-out-paid quadrant from ADR 0011's matrix.

**New external dependencies:**

- **Non-Instacart sourcing strategy** for markets Instacart doesn't serve (UK, Australia). A second `SourcingProvider` implementation per ADR 0003. The exact strategy is deferred to v3 planning — could be a different aggregator, direct retailer integrations, or a manual-list-first fallback.

**New UI surfaces:**

- **Linked partner flows** — invite, accept, manage permissions, revoke access. Significantly more complex than v2's dependent flows because both parties have agency.
- **Configurable agent personas** (per ADR 0006). A settings surface where the user can pick a default tone for the Chef, the Curator, or the app overall. v3 only.
- **Observer insights surface** — where the user reads the long-term trend reports and warnings the Observer produces. Tone and frequency need careful design (this is the surface most likely to violate the calm-precise-warm voice if built carelessly).
- **BYOK setup flow** — for users who want to supply their own API keys. Quietly available, not pushed.

**Trust boundary changes:**

- **The trust boundary diagram changes meaningfully for the first time.** Linked partner sharing is the first time *user content* (in the form of peer-appropriate constraint views) crosses an account boundary at all. This is an additive change, not a replacement of the existing model — the existing model still applies for self-data — but it introduces a new "peer-shared" trust zone that needs to be added to the diagram and analyzed in `security-and-privacy.md`.
- **The BYOK path bypasses the Cloud Function proxy entirely**, which means BYOK users get the v1-alpha-style direct-from-device trust posture (the strongest possible) at the cost of managing their own API keys. The diagram should show this as an alternate path for signed-out and BYOK users.

**New deployment requirements:**

- **Google Play distribution in new countries** — enabling the listing in UK/EU/other target markets, with the per-region store presence (localized listing, content rating per region, regional pricing) that entails. No new app store; the same Play Store, more countries.
- **Multi-region considerations** — v3 expands beyond North America, which means the privacy policy must address GDPR (UK and EU) and any other applicable regimes. This is non-trivial legal work that v3 planning needs to budget for. May also require region-specific Firestore location configuration for data residency.

### What does *not* change in v2 or v3 (restated for emphasis)

The orchestrator pattern, the constraint engine API, the post-generation validator, the `ModelProvider` interface, the data layer's profile-as-first-class structure, the local-first source-of-truth principle, the "no analytics on personal data, ever" commitment, the calm-precise-warm tone, and the dependency on Firebase as the backend platform — none of these change in v2 or v3. Every delta listed above is *additive*, slotting into the existing architecture as new components or new paths through existing components.

If a v2 or v3 addition starts to require *rewriting* one of the invariants above, that's a signal that the architectural readiness from v1 has eroded, and the right response is to pause, understand why, and probably write a superseding ADR rather than push through with the rewrite. The point of this section is to make that signal *visible*: any drift from the additive-only pattern is the agent's cue to ask the founder before proceeding.



### What this document references

- `vision.md` — for tone, trust posture, the five pillars, the launch sequencing
- `PRD.md` — for the persona, the user journey, the v1 feature scope, the acceptance criteria
- All ADRs in `decisions/` as decision context

### What this document defers to deeper-dive docs

- **`constraint-engine-spec.md`** — the schema of the constraint graph, the severity tier model from ADR 0009 in mechanical detail, the deterministic validation rules from ADR 0010, the temporal scope structure
- **`agent-architecture.md`** — per-agent capability requirements, prompt structure conventions, the orchestrator's routing logic, the validator's parsing approach, the bounded retry loop, the fallback message UX
- **`data-model.md`** — every persistent data structure field-by-field, the profile ownership semantics from ADR 0004 in detail, the schema versioning and migration tooling
- **`local-first-sync.md`** — the encryption library and approach, the key derivation from ADR 0011 in detail, the recovery passphrase UX, the Firestore blob schema, the conflict resolution policy for multi-device edits
- **`build-conventions.md`** — the Orbit MVI state management choice, folder structure, naming conventions, when the agent asks vs decides, Kotlin/Compose patterns
- **`testing-strategy.md`** — the hard-cases test suite for the constraint engine, the validator's own test suite, the agent eval harness
- **`security-and-privacy.md`** — the formal threat model, the upstream provider data-retention audit, the recovery passphrase loss policy

### What this document does *not* defer (decisions made here)

- The six-subsystem decomposition and fifteen-component map
- The signed-out vs signed-in trust boundary diagram
- The named third-party dependencies list (and its forbidden-additions list)
- The deployment topology (v1 APK / v2 Play Store / v3 Play Store in new geographies)

### How the agent should use this doc

When the coding agent encounters a question about *system structure* — what component does X live in, how does Y talk to Z, where does data W flow — the answer should be findable in this doc. When the agent encounters a question about *implementation specifics* — what fields does the constraint graph have, what does the Chef agent's prompt look like, how exactly does sync resolve conflicts — the answer is in a deeper-dive doc, not here. If the agent cannot find the answer in either place, the discipline from PRD § 9 applies: check the vision, check the relevant ADR, check the architecture docs, ask the founder before guessing.

---

*End of `technical-architecture.md` v1 (initial draft). Next revision will incorporate any structural changes that surface during the writing of Docs #4-#7 (the deeper-dive docs that this architecture references), and any architectural learnings from the v1 alpha build.*
