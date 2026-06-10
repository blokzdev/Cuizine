# Cuizine — Agent Architecture

> How the agents use the engine. Where `constraint-engine-spec.md` defined *what the engine is*, this document defines *how the agents behave against it*. The two documents together specify Cuizine's reasoning layer: `constraint-engine-spec.md` is the contract, this document is the behavior. Every agent — Curator, Chef, Pantry in v1, plus Planner, Sourcing, Observer arriving in v2 and v3 — is specified here with its role, inputs, outputs, capability requirements, prompt structure, tool access, and interaction patterns with the engine.

## 1. Purpose & how to read this doc

This document specifies the **agent architecture for Cuizine v1** with forward-references to v2 and v3 evolution. It assumes you have read `vision.md`, `PRD.md`, `technical-architecture.md`, `constraint-engine-spec.md`, and the ADRs — especially ADR 0006 (multi-provider per-agent routing), ADR 0009 (conflict resolution policy), ADR 0010 (post-generation validation), and ADR 0012 (food data sources). Concepts established there are not re-derived here.

This document defines: the common agent archetype (the shape every agent shares); the orchestrator's routing, retry, and validation logic; the Curator agent's role, contract, prompt structure, and tool access; the Chef agent's role, contract, prompt structure, and tool access; the Pantry agent's lightweight v1 role and contract; the validator's relationship to the agent layer (it is not an agent but participates in the agent flow); the per-agent provider routing decisions for v1; the agent-to-engine interaction patterns as worked examples; and the forward specification for v2 and v3 agents.

This document does **not** define: the actual system prompt text for each agent (those live as versioned files in `prompts/curator.md`, `prompts/chef.md`, `prompts/pantry.md` — kept separate so they can be iterated without revising this doc); the evaluation harness or per-agent evals (those live in `testing-strategy.md`); the physical data flow at the network level (that lives in `technical-architecture.md`, already complete); or the line-by-line fields of the structured output schemas (those are referenced here by type name and specified in `data-model.md`).

**When this document and a deeper-dive doc disagree,** this doc wins for agent behavior and contracts, and the deeper-dive docs win for their respective domains (prompt text, eval results, physical data representation).

## 2. The agent archetype

> Every agent in Cuizine's multi-agent system shares a common shape. This section defines that shape once so the agent-specific sections (4, 5, 6) can reference it by shorthand rather than re-specifying common properties. The archetype is not a class hierarchy in the implementation sense — it is a set of conventions every agent follows, enforced by the orchestrator and verified by tests.

### Common properties

Every agent in Cuizine has these properties:

**Role.** A one-sentence description of what the agent *does*, phrased as an action ("the Curator parses natural-language user speech into structured constraint graph updates"). The role is how the orchestrator decides which agent to invoke for a given intent. No two agents share a role.

**Capability requirements.** A structured declaration of what the agent needs from its LLM provider: reasoning quality (reasoning-strong, reasoning-standard, reasoning-light), world knowledge (general, cultural-depth, nutritional-depth, tool-use-heavy), context window (standard, long), and latency tolerance (interactive, background). These feed into ADR 0006's `ModelProvider` routing, specified in Section 8.

**Input contract.** A typed specification of what the agent receives when invoked. All agent inputs are structured objects, not raw strings. The Curator receives a typed `ConversationTurn` containing the user's message text plus contextual metadata; the Chef receives a typed `MealRequest` containing the active constraint set plus session state; the Pantry receives a typed `PantryQuery`. Input types are shared across the codebase and validated at the orchestrator boundary.

**Output contract.** A typed specification of what the agent produces. **All agent outputs conform to a strict structured schema** — the agent is invoked with constrained generation enabled on whichever provider it's routed to, and outputs that fail schema validation are treated as agent errors. No post-hoc parsing heuristics. Structured output is non-negotiable for every agent in Cuizine because it makes the validator's job possible, makes testing tractable, and makes failure modes legible.

**State handling.** Every agent in Cuizine is **stateless between invocations**. An agent never holds state that persists beyond a single call. All state the agent needs — the active constraint set, session context, conversation history, previous rejection reasons — is passed in as input on each invocation. The orchestrator is responsible for accumulating and passing this state. Statelessness makes testing dramatically easier, makes replay possible, and matches the validator's stateless design from `constraint-engine-spec.md` Section 7.

**Tool access.** Every agent has a *declared* set of tools it can call during its invocation. Tools include: the constraint engine API (per `constraint-engine-spec.md` Section 6), the Food Data Provider (per ADR 0012), the `ModelProvider` interface for its own LLM calls, and in some cases read-only access to other agents' capabilities. **Tool access is enforced by the orchestrator, not by convention.** An agent cannot call a tool outside its declared set; attempts are rejected by the orchestrator before the call lands. This prevents the silent failure mode of an agent that "helpfully" starts writing to the constraint graph when it was only supposed to read.

**Error handling.** Every agent declares its expected error modes (malformed output, provider API failures, tool-call errors, unknown ingredients) and the orchestrator handles them per agent. For some errors, the orchestrator retries with the same agent; for some, it retries with a different provider; for some, it surfaces the error to the user via a warm, non-technical message; for some, it logs the failure as an alpha event for founder review. Error handling is specified per agent in Sections 4-6.

### Why the archetype matters

The archetype exists because without it, agents drift. Each agent would develop its own conventions, its own state quirks, its own output format, and the orchestrator would become a pile of agent-specific special cases. The archetype makes agents interchangeable in the ways that matter — the orchestrator treats all of them the same way, the validator receives all of their outputs the same way, and adding a new agent (v2's Planner and Sourcing, v3's Observer) is a new instance of the same pattern rather than a new architecture.

The archetype is the mechanical expression of the architectural readiness principle applied to the agent layer: three agents in v1, six in v3, same shape throughout.

## 3. The orchestrator

> The orchestrator is the non-agent component that routes user intents to agents, manages the validator loop from ADR 0010, handles retry and fallback paths, and triggers the conflict resolution algorithm from `constraint-engine-spec.md` Section 8. It has no LLM prompt and is not an agent itself — it is deterministic code. It is load-bearing because every user interaction flows through it.

### What the orchestrator does

The orchestrator is invoked with a user intent (a structured object representing what the user wants to do — continue the constraint conversation, ask for a meal suggestion, reject a suggestion with feedback, update a contextual state, etc.) and is responsible for producing a user-facing response. Between those two endpoints, the orchestrator:

1. **Routes the intent to the appropriate agent(s).** Intent routing is rule-based, not LLM-based. Each intent type has a fixed mapping to one or more agents. Routing is not a place where the orchestrator reasons; it is a place where the orchestrator looks up which agent handles which intent. This determinism is important — if routing were an LLM decision, a routing hallucination could send the user's "I'm in Toronto for a few days" to the Chef instead of the Curator, with silent consequences.

2. **Constructs the agent's input object.** The orchestrator gathers the active constraint set (from `queryActive`), the session state (conversation history, recent suggestions, recent rejections), and the user's current message, and composes them into the typed input object the target agent expects.

3. **Invokes the agent through the ModelProvider interface.** The provider-and-model selection comes from the per-agent routing table (Section 8). The invocation uses constrained generation to enforce the agent's output schema.

4. **Validates the agent's output.** Schema validation first — does the output conform to the declared schema? Then, for agents that produce meal suggestions (Chef, v2 Planner), post-generation constraint validation via the validator from `constraint-engine-spec.md` Section 7.

5. **Handles failure modes with retry and fallback.** Per-agent error handling from the agent-specific sections below. The regeneration loop for Chef validation failures is bounded at three attempts per ADR 0010. After exhaustion, the conflict resolution algorithm from `constraint-engine-spec.md` Section 8 runs.

6. **Routes the validated output to the user-facing surface.** The orchestrator is the only component that talks directly to the UI layer from the agent side. Agents never touch the UI; the UI never touches agents directly.

### The intent-to-agent routing table (v1)

The following intents exist in v1, with their fixed agent routing:

- **`begin_constraint_conversation`** → Curator. Triggered when the user first opens Cuizine.
- **`continue_constraint_conversation`** → Curator. Triggered for each subsequent turn of the constraint conversation on day 0.
- **`free_text_update`** → Curator. Triggered when the user types something into the free-text input outside the constraint conversation (e.g., "my sugar was high this morning," "I'm in Toronto for a few days").
- **`request_meal_suggestion`** → Chef (with validator pass-through). Triggered when the user asks "what should I make tonight" or equivalent.
- **`reject_suggestion`** → Curator (to record the rejection as contextual state) → Chef (to regenerate). Composite intent that chains two agents.
- **`request_suggestion_explanation`** → Curator (reads provenance and composes a plain-language explanation). Triggered when the user asks "why did you suggest this?"
- **`pantry_ingestion`** → Pantry agent (lightweight in v1). Triggered when the user manually adds an item to their pantry.

New intents in v2 and v3 add new rows to this table without changing existing ones. Intent types are a closed set — the orchestrator rejects unknown intent types at the boundary rather than silently routing them to a default agent.

### The regeneration loop, specified precisely

Per ADR 0010, the orchestrator runs a bounded regeneration loop when a Chef suggestion fails validation. The exact algorithm:

1. Invoke Chef with the current input. Receive a suggestion.
2. Run the validator (`validateSuggestion` from `constraint-engine-spec.md` Section 6). Receive a validation result.
3. If `passed: true`, return the suggestion (plus any disclosure notes) to the user.
4. If `passed: false`, append the failure reasons to the Chef's input context and re-invoke Chef with `attempt_number = 2`. Go to Step 2.
5. After three failed attempts, do *not* return failure to the user yet. Run the conflict resolution algorithm from `constraint-engine-spec.md` Section 8 instead.
6. If conflict resolution produces a valid suggestion (via silent preference relaxation or user-chosen relaxation), return it.
7. If conflict resolution also fails, return the honest fallback message to the user ("I couldn't find a meal that works tonight — could you tell me more about what you're in the mood for, or relax one of your constraints for tonight?").

The orchestrator maintains the retry count and the accumulated failure reasons across regenerations. Every regeneration attempt is logged to the local event store per PRD § 5 for alpha review, with `severity-zero` tagging for any suggestion that violated a medical or religious constraint (per ADR 0010).

### What the orchestrator is not allowed to do

- The orchestrator never modifies the constraint graph directly. Writes to the graph happen through the Curator agent, never through orchestrator shortcuts.
- The orchestrator never calls an LLM for its own reasoning. Every LLM call in Cuizine happens inside an agent. The orchestrator is deterministic code.
- The orchestrator never exposes model names, provider names, token counts, or confidence tags to the user (per ADR 0006's forever commitments and ADR 0007's tier framing).
- The orchestrator never surfaces agent errors as raw technical messages. Every error becomes a warm, non-technical user-facing message, with the technical detail logged separately for alpha review.

## 4. The Curator agent

**Role.** The Curator parses natural-language user speech into structured constraint graph updates, runs the constraint conversation on day 0, handles free-text updates during ongoing use, and composes plain-language explanations when the user asks "why?". The Curator is the only agent with *write* access to the constraint graph.

### Capability requirements

- **Reasoning quality:** reasoning-strong. The Curator does the hardest natural-language interpretation in v1 — turning messy free-text about medical conditions, religious practices, cultural food traditions, and contextual states into typed constraint objects with appropriate severity and scope. Misinterpretation here propagates to every downstream agent.
- **World knowledge:** cultural-depth. The Curator must recognize when a user's mention of "fasting on Tuesdays for Hanuman" is a Hindu religious observance versus when a user's mention of "fasting before a blood test" is a temporary medical preparation — both are fasts, both are temporal, but they land in different severity tiers and different scope shapes.
- **Context window:** standard. The Curator receives the active constraint set plus the current message plus recent conversation history. This fits comfortably in a standard context window.
- **Latency tolerance:** interactive. The user is waiting during the constraint conversation and during free-text updates, so Curator calls are on the hot path.

### Input contract

The Curator receives a typed `CuratorInput` object containing:

- `profile_id` — which profile to update
- `current_message` — the user's current turn (free-text string)
- `conversation_history` — recent prior turns if this is part of an ongoing conversation (empty on day 0 first turn)
- `active_constraint_set` — the current active set for the profile, so the Curator can recognize when the user is referring to an existing constraint versus adding a new one
- `intent_type` — one of the Curator's intents from Section 3's routing table (so the Curator knows whether it's beginning a conversation, continuing one, handling a free-text update, or composing an explanation)

### Output contract

The Curator returns a typed `CuratorOutput` object with structured fields for every case:

- `constraint_operations` — a list of `addConstraint`, `updateConstraint`, `removeConstraint`, or `setContextualState` operations the orchestrator should apply to the graph, or empty if no graph changes are needed this turn
- `user_message` — the warm, conversational message to show the user this turn (the next question in the constraint conversation, the acknowledgment after a free-text update, the plain-language explanation the user asked for)
- `next_intent` — what the orchestrator should expect from the user next (`continue_constraint_conversation`, `ready_for_suggestions`, etc.). This is the Curator's way of telling the orchestrator whether the conversation is still in progress or complete.
- `disclosure_notes` — any uncertainty the Curator wants to surface to the user (e.g., "I interpreted 'no dairy' as a medical constraint based on the IBS you mentioned — let me know if that's wrong")
- `provenance_metadata` — the per-constraint provenance fields (`source`, `original_phrasing`, `added_context`) that the orchestrator attaches when it applies the constraint operations

Every field is required; unused fields return empty or null. Constrained generation against this schema is enabled on every Curator invocation.

### Prompt structure

The Curator's system prompt (stored in `prompts/curator.md`) has four required sections, in order:

1. **Role and tone** — who the Curator is, how it speaks (calm, precise, warm; per vision.md). This section is stable across versions.
2. **The task specification** — what the Curator does with the input, structured by `intent_type`. Different intent types produce different guidance (begin a conversation vs. continue one vs. handle a free-text update).
3. **The constraint engine contract** — the allowed constraint types, severity tiers, scope shapes, and the disallowed combinations from `constraint-engine-spec.md` Sections 3-4. This section is the Curator's reference for what it's allowed to emit. It's reproduced in the prompt (not just referenced) because the prompt has to be self-contained — the agent doesn't have a separate "read the spec" path.
4. **The output schema** — the CuratorOutput fields and their types. The agent produces output conforming to this schema via constrained generation; the schema is also spelled out in the prompt so the agent understands what it's producing semantically, not just structurally.

Prompt sections are versioned independently and changes are logged in `prompts/curator.md`'s history. Per-section change tracking means we can A/B test tone changes without touching task specification changes.

### Tool access

The Curator has access to:

- `addConstraint`, `updateConstraint`, `removeConstraint`, `setContextualState` on the constraint engine API — the Curator is the only agent with write access.
- `queryActive`, `queryAll`, `queryProvenance` on the constraint engine API for read operations (especially for composing explanations).
- The Food Data Provider, for disambiguation when the user mentions an ingredient the Curator is uncertain about (e.g., "I can't have 'beans'" — the Curator queries the Food Data Provider to understand what "beans" might mean and may ask the user to clarify).
- Its own `ModelProvider` calls for LLM inference, routed per Section 8.

The Curator does *not* have access to: the Chef agent, the Pantry agent, any other agent. Agent-to-agent calls in v1 are forbidden; composition happens through the orchestrator.

### Error handling

The Curator's expected error modes and orchestrator responses:

- **Malformed output (schema validation fails):** retry once with the same provider. If it fails again, retry with a different provider per the fallback chain in Section 8. If all providers fail, log a severity-one alpha event and surface a warm error message to the user ("Something went sideways on my end — could you say that again?").
- **Ambiguous user input the Curator cannot resolve:** the Curator is permitted to return a `CuratorOutput` with `user_message` containing a clarifying question and no constraint operations. This is not an error — it is a normal branch of the conversation.
- **Constraint write rejected by the engine** (disallowed combination per `constraint-engine-spec.md` Section 4): the Curator's output contained an invalid operation. This is a severity-one alpha event because the Curator's prompt is supposed to prevent this. The orchestrator retries Curator once; if the retry also fails, the orchestrator falls back to asking the user a clarifying question ("I'm not quite sure how to record that — could you tell me more about whether it's a health thing or a preference?").
- **Food Data Provider lookup fails** during disambiguation: the Curator proceeds without the disambiguation and surfaces a gentle note in its `user_message` if the ambiguity was material.

## 5. The Chef agent

**Role.** The Chef generates meal suggestions in the user's cultural idiom, respecting the active constraint set, using culturally-appropriate ingredients and techniques, and producing output structured enough for the validator to check deterministically. The Chef is the user's primary experience of Cuizine's intelligence.

### Capability requirements

- **Reasoning quality:** reasoning-strong. The Chef composes meals across multiple overlapping constraints while remaining culturally authentic, which is the hardest generation task in the product.
- **World knowledge:** cultural-depth + nutritional-depth. Cultural depth because pillar 5 requires meals to feel like the user's own food. Nutritional depth because the validator's `limit` checks depend on the Chef being able to produce suggestions whose estimated nutritional properties are usable as a starting point (the Food Data Provider supplies the canonical data, but the Chef's own knowledge informs meal composition).
- **Context window:** standard. The Chef receives the active constraint set, recent meal history, and the request. Standard context is sufficient.
- **Latency tolerance:** interactive. The user is waiting for a meal suggestion.

### Input contract

The Chef receives a typed `ChefInput` object containing:

- `profile_id` — whose meal is being planned
- `active_constraint_set` — the output of `queryActive` at request time, with full constraint records including severity and scope
- `cooking_context` — the profile's captured cultural context, cooking-for context (family of four, dietary mix), and kitchen inventory hints
- `recent_history` — meals cooked or rejected in recent days, so the Chef can avoid immediate repetition and learn from rejection feedback
- `request_details` — the specifics of this request (is the user asking for "dinner tonight," "something quick," "a meal that works for the whole household"?)
- `attempt_number` — 1 on first attempt, 2-3 on regeneration attempts (per the orchestrator's regeneration loop from Section 3)
- `previous_failure_reasons` — on attempts 2-3, the reasons previous attempts failed validation, so the Chef can learn within the loop

### Output contract

The Chef returns a typed `ChefOutput` object with structured fields:

- `meal_name` — the dish title in the user's cultural idiom (e.g., "aloo methi with phulka roti," not "potato and fenugreek stir-fry with flatbread")
- `cultural_context` — a brief note on the regional or cultural lineage of the dish (fed to the validator as metadata, used in disclosure notes when relevant)
- `ingredients` — a structured list of ingredient entries with quantities, units, and preparation notes. Each ingredient is a canonical reference the Food Data Provider can resolve for validation.
- `preparation_summary` — a brief natural-language description of the cooking process (1-3 sentences). Not the full recipe — just enough to convey the shape of the dish.
- `cooking_instructions` — the full step-by-step recipe, written in the user's cultural idiom, using the terms and techniques the user would recognize. This is the user-facing natural language that lives inside the structured output as a string field.
- `nutritional_rough_estimate` — per-serving estimates of the macros the validator's `limit` checks might need (carbs, sodium, etc.). Optional; the Food Data Provider's summed values are authoritative for validation, but the Chef's estimate is used as a sanity check.
- `confidence_notes` — any uncertainty the Chef wants to flag (e.g., "I'm not 100% sure asafoetida is in your pantry, but you could substitute black pepper")

Constrained generation against this schema is enabled on every Chef invocation.

### Prompt structure

The Chef's system prompt (stored in `prompts/chef.md`) has five required sections:

1. **Role and tone** — who the Chef is, how it speaks. Critical that it does not lecture, does not validate medical decisions, does not insert clinical framing.
2. **The task specification** — how to compose a meal from the active constraint set and cooking context. Includes explicit guidance on cultural idiom, ingredient naming, and avoiding "diabetic-friendly recipes" failure modes (per PRD § 4's Walkthrough B).
3. **The constraint engine contract** — a read-only summary of what the active constraint set means and how severity tiers affect what the Chef can suggest. The Chef does not modify constraints but must reason about them.
4. **The regeneration context** — on attempts 2-3, guidance on how to incorporate `previous_failure_reasons` without overfitting to them (e.g., "you rejected the previous suggestion because it contained chickpeas — don't just remove chickpeas, consider whether the constraint means you need a different dish entirely").
5. **The output schema** — the ChefOutput fields and the structured format, reproduced in the prompt for semantic understanding.

### Tool access

The Chef has access to:

- `queryActive`, `queryByType` on the constraint engine API — read-only.
- The Food Data Provider — for looking up ingredients the Chef is considering, especially to verify they match the user's cultural context or check their nutritional composition for `limit` constraints.
- Its own `ModelProvider` calls.

The Chef does *not* have access to: constraint write operations, the Curator agent, the Pantry agent, the Planner agent (when it arrives in v2 — the Planner calls the Chef, not the other way around).

### Error handling

- **Malformed output:** same retry-then-fallback-provider pattern as the Curator.
- **Validator rejection on first attempt:** the orchestrator's regeneration loop takes over (Section 3 Step 4). Not a Chef error per se.
- **Validator rejection on three consecutive attempts:** escalate to conflict resolution (`constraint-engine-spec.md` Section 8). Not a Chef error per se.
- **Chef produces a suggestion with an unknown ingredient the Food Data Provider cannot resolve:** the orchestrator invokes the AI-fallback path from ADR 0012 with severity-scoped behavior (`constraint-engine-spec.md` Section 7 Step 4). Not a Chef error.
- **Chef produces a culturally nonsensical suggestion** (e.g., suggesting chicken to a user whose profile is clearly vegetarian by preference): this is a prompt quality issue, logged as a severity-one alpha event for review, and the regeneration loop takes over. Systemic patterns here drive prompt iteration in `prompts/chef.md`.

## 6. The Pantry agent (lightweight in v1)

**Role.** The Pantry agent handles fuzzy matching and disambiguation of ingredient names, resolves user-entered pantry items to canonical ingredient entries from the Food Data Provider, and answers simple queries from other agents about what ingredients the user has on hand. The v1 Pantry agent is deliberately lightweight — it is not yet the full pantry-aware planning capability from pillar 2. That arrives in v2 per ADR 0012 and the technical-architecture.md v2 deltas.

### Capability requirements

- **Reasoning quality:** reasoning-light. Fuzzy matching and disambiguation are mostly parsing tasks, not reasoning tasks. A cheap model performs adequately and the cost savings are meaningful at scale.
- **World knowledge:** general. The Pantry agent does not need cultural depth (the Food Data Provider supplies cultural ingredient knowledge) or nutritional depth (the Provider supplies composition).
- **Context window:** standard (actually small — the Pantry agent typically works on single ingredient-name queries).
- **Latency tolerance:** interactive for user-facing pantry queries; background for Chef sub-queries.

### Input contract

The Pantry agent receives a typed `PantryInput` object:

- `profile_id`
- `query_type` — one of `resolve_ingredient` (user typed a name, resolve to canonical), `disambiguate` (multiple candidates, pick the most likely given user's cultural context), `check_availability` (does the user have this in their pantry)
- `query_string` — the ingredient name or query in free text
- `cooking_context` — the profile's cultural context, used for disambiguation
- `current_pantry` — the user's currently-tracked pantry items, for availability queries

### Output contract

The Pantry agent returns a typed `PantryOutput` object:

- `resolved_entry` — the canonical Food Data Provider entry the query resolved to, or null if unresolvable
- `candidates` — if disambiguation was needed, the list of candidates considered (ranked by likelihood given cultural context)
- `confidence` — high / medium / low
- `disclosure_notes` — any uncertainty worth surfacing (e.g., "I interpreted 'dal' as toor dal since that's the most common in North Indian cooking — tell me if you meant a different one")

### Prompt structure

The Pantry agent's system prompt (stored in `prompts/pantry.md`) is the shortest of the three v1 agents:

1. **Role and task** — fuzzy match ingredient names to canonical entries, with cultural context as a tiebreaker.
2. **The Food Data Provider contract** — how to interpret the entries returned by the provider and which fields matter for disambiguation.
3. **The output schema** — the PantryOutput fields.

### Tool access

- `queryActive` (read-only) for cultural context
- The Food Data Provider (heavy use — this is the Pantry agent's primary tool)
- Its own `ModelProvider` calls

### Error handling

- **Malformed output:** same retry-then-fallback pattern.
- **Completely unresolvable ingredient (not in cache, bundle, or external sources, and AI-fallback is not appropriate because the query isn't in a constraint-checking context):** return `resolved_entry: null` with a disclosure note. The caller (usually the orchestrator or the Curator during disambiguation) decides what to do next.
- **Culturally implausible disambiguation** (e.g., "dal" resolving to "lentil soup" when the user is Punjabi and clearly means a traditional Punjabi dal): logged as a severity-two alpha event, prompt iteration follows.

## 7. The validator as non-agent orchestration component

> The post-generation validator from ADR 0010 participates in the agent flow — it runs on every Chef output before the user sees it — but it is **not** an agent. This section clarifies its status because conflating the validator with an agent would reintroduce the exact failure mode ADR 0010 was written to prevent. The validator's full mechanical contract lives in `constraint-engine-spec.md` Section 7; this section addresses only how it slots into the agent layer.

### What the validator is

The validator is a **deterministic, LLM-free function** that takes `(profile_id, suggestion, at_time)` and returns `(passed, violations, disclosure_notes)`. It has no prompt, no role description, no capability requirements, no output schema in the agent-archetype sense. It is code, not a reasoning component. Per ADR 0010, it **cannot** be implemented as an LLM call — an LLM validator can hallucinate just as easily as an LLM generator, and two unreliable systems checking each other is not more reliable than one.

The validator's one concession to LLM involvement is the structured-extraction sub-call for parsing unknown-ingredient names into structured form, per ADR 0012 and `constraint-engine-spec.md` Section 7 Step 4. That sub-call is specifically a *parsing* task, not a *reasoning* task, and it is bounded by severity (Inviolable constraints never AI-fallback). This is the only LLM involvement in the validator's path, and it is carefully bounded.

### How the validator slots into the agent flow

The orchestrator invokes the validator after every Chef output. The invocation looks (conceptually) like this:

1. Chef produces a `ChefOutput` with its structured ingredient list.
2. Orchestrator calls `validateSuggestion(profile_id, suggestion, at_time=now)` — which is `constraint-engine-spec.md` Section 7's entrypoint.
3. Validator computes the active constraint set, resolves each ingredient through the Food Data Provider, runs deterministic rule checks per severity tier, and returns its verdict.
4. Orchestrator reads the verdict and decides: return to user (passed), trigger regeneration (failed, attempts < 3), or trigger conflict resolution (failed, attempts = 3).

The validator never calls the Chef, never calls the Curator, never calls any other agent. It is a pure function in the sense that matters most: given the same inputs (constraint graph state, suggestion, time), it always returns the same verdict.

### Why the archetype from Section 2 doesn't apply

The archetype's seven common properties assume an LLM-backed component. The validator has:
- No role in the "what action does it take" sense — it *checks*, it does not act.
- No capability requirements — it needs no LLM provider at all (except the bounded sub-call).
- An input contract but not an output contract in the agent sense — its output is a verdict, not a generation.
- No state handling concerns — statelessness is trivial because it has no state.
- No tool access in the agent sense — it calls the constraint engine API and the Food Data Provider as ordinary function calls, not as LLM-tool invocations.
- No error handling beyond "input was malformed" — the validator's deterministic logic doesn't have the failure modes LLM agents do.

Treating the validator as an agent would force it into a mold that hides its most important property: deterministic correctness. The archetype exists to unify LLM-backed components. The validator exists to be a hard floor *below* LLM-backed components. They serve different purposes and the architecture should reflect that.

## 8. Per-agent provider routing (v1)

> ADR 0006 establishes the `ModelProvider` interface and commits to per-agent routing with automatic model escalation. This section specifies the **v1 default routing table** for the Curator, Chef, and Pantry agents, with the capability requirements from Sections 4-6 mapped to concrete provider-and-model selections. The routing is revisable based on alpha eval data — the table below is the starting point, not a commitment that can only change with an ADR.

### The v1 routing table

| Agent | Default provider | Default model | Fallback provider | Rationale |
|---|---|---|---|---|
| **Curator** | Anthropic | Claude Sonnet | Google (Gemini Pro) | Strongest instruction-following and careful reasoning for the hardest natural-language interpretation task in v1. Cultural depth is real but not the highest-stakes factor here; reasoning quality is. |
| **Chef** | Anthropic | Claude Sonnet | Google (Gemini Pro) | Strong reasoning plus cultural depth for grounded creative generation. Claude's handling of nuanced multi-constraint prompts is strong; Gemini is the fallback because its cultural coverage is comparable and its fallback availability protects against Anthropic outages. |
| **Pantry (v1 lightweight)** | Google | Gemini Flash | Anthropic (Claude Haiku) | Cheap fast fuzzy matching. Parsing task, not reasoning task, so the cheap-tier model is correct. Cost savings matter here because Pantry is invoked frequently. |
| **Validator structured-extraction sub-call** | Google | Gemini Flash | Anthropic (Claude Haiku) | Parsing-shaped task at high volume. Matches Pantry's tier. |

### Automatic model escalation

Per ADR 0006, the orchestrator can escalate individual requests to stronger models when the task warrants it. The escalation criteria are **task-signal-based**, not user-facing:

- **Curator escalation** to Claude Opus: triggered when the user's free-text input contains indicators of a high-stakes medical disclosure (specific medications mentioned, explicit references to ICU or hospitalization, phrasing that suggests a new serious diagnosis). The Curator's interpretation of these cases deserves the strongest model available, and the cost is justified because these moments are rare and consequential.
- **Chef escalation** to Claude Opus: triggered when `attempt_number >= 2` in the regeneration loop. If the Chef's first attempt failed validation, the second attempt gets a stronger model — because the easy cases passed on attempt 1, and what's left is genuinely harder. Third attempts stay on Opus.
- **Pantry escalation**: Pantry does not escalate in v1. Its tasks are all lightweight enough that the cheap tier suffices.

Escalation is silent to the user. No UI indication, no notification, no "thinking harder" animation. Per ADR 0006, the user never sees model names or tiers.

### Fallback chain on provider failures

When the default provider fails (network error, rate limit, 5xx response), the orchestrator retries once on the same provider, then falls over to the fallback provider in the table above. Every fallback is logged as an operational telemetry event (per ADR 0011 — operational telemetry is allowed; content telemetry is not). The user-facing experience during a fallback is a slightly longer response time, nothing else.

If both providers fail, the orchestrator surfaces a warm error message to the user ("Something is slow on my end — could you try again in a moment?") and logs a severity-one alpha event for founder review.

### What the routing table is not

- **It is not a permanent commitment.** Early alpha data will tell us whether Claude Sonnet actually beats Gemini Pro for the Chef agent's cultural-depth cases, or vice versa. The table will move based on evals, and the movement does not require a new ADR unless the routing *architecture* changes (not the assignments).
- **It is not user-visible.** Nothing in this table surfaces to the user. The whole table could change overnight and the only user-visible effect would be subtle quality differences.
- **It is not a ceiling.** New models from any provider can be slotted in as they become available, replacing the current defaults, without architectural change.

## 9. Agent-to-engine interaction patterns

> The abstract contracts from Sections 4-6 come alive in concrete interactions. This section walks through three real interaction patterns from Sukhi's life, showing exactly how each agent reads from and writes to the constraint engine API during a real user action. These patterns are the test cases the agent architecture must support correctly — if an interaction described here doesn't work end-to-end, the architecture has a bug.

### Pattern A: Curator handles a free-text update ("my sugar was 9.2 this morning")

Day 5. Sukhi types "my sugar was 9.2 this morning" into the free-text input on the suggestion surface.

1. **The UI sends a `free_text_update` intent to the orchestrator** with the message text and the current profile ID.
2. **The orchestrator constructs a `CuratorInput`**: profile_id = Sukhi's profile, current_message = "my sugar was 9.2 this morning", conversation_history = last few turns of recent interaction, active_constraint_set = result of `queryActive(sukhi_profile_id)`, intent_type = `free_text_update`.
3. **The orchestrator invokes the Curator** through `ModelProvider` routing to Claude Sonnet per Section 8. The invocation uses constrained generation against the `CuratorOutput` schema.
4. **The Curator reads the input**, recognizes this as a contextual signal about blood sugar (not a new permanent constraint), and decides to emit a `setContextualState` operation: `{profile_id: sukhi, state: {flag: "blood_sugar_elevated", value: 9.2, unit: "mmol/L"}, expires_at: end_of_day}`. It also emits a `user_message` like: "Got it — I'll keep that in mind for tonight." No other operations, no clarifying questions needed.
5. **The Curator returns a `CuratorOutput`**: `constraint_operations: [setContextualState(...)], user_message: "Got it — I'll keep that in mind for tonight.", next_intent: "ready_for_suggestions", disclosure_notes: [], provenance_metadata: {source: "user-direct", original_phrasing: "my sugar was 9.2 this morning", added_context: {flow: "free_text_update"}}`.
6. **The orchestrator validates the output** against the schema (passes), then applies the `setContextualState` operation through the constraint engine API. The contextual constraint enters the graph with a TTL of end-of-day.
7. **The orchestrator returns the `user_message` to the UI**, which displays it with the suggestion surface still focused (the free-text input was inline, not a dedicated screen).
8. **The next time Sukhi asks for a meal**, the Chef's input will include this contextual constraint in its active set, and the Chef will quietly lean toward lower-glycemic options because the `blood_sugar_elevated` flag scopes certain avoid constraints into active status.

### Pattern B: Chef generates a meal suggestion (the validated happy path)

Later on day 5. Sukhi taps "suggest a meal" on the suggestion surface.

1. **The UI sends a `request_meal_suggestion` intent to the orchestrator** with the current profile ID.
2. **The orchestrator constructs a `ChefInput`**: profile_id, active_constraint_set from `queryActive` (which now includes the `blood_sugar_elevated` contextual constraint from Pattern A), cooking_context from the profile, recent_history from the last seven days of suggestions, request_details = "dinner tonight", attempt_number = 1, previous_failure_reasons = empty.
3. **The orchestrator invokes the Chef** through `ModelProvider` routing to Claude Sonnet. Constrained generation against the `ChefOutput` schema.
4. **The Chef reads the input**, including the elevated blood sugar flag, and composes a meal: masoor dal with spinach and cucumber raita — lower glycemic load than chana masala would have been, no onion or garlic (Sukhi's IBS avoid is active), culturally grounded, family-compatible.
5. **The Chef returns a `ChefOutput`**: structured meal_name, ingredient list, cooking_instructions in her cultural idiom, rough nutritional estimate.
6. **The orchestrator validates the output**: schema validation passes, then invokes `validateSuggestion` (`constraint-engine-spec.md` Section 7). The validator computes the active constraint set, resolves each ingredient through the Food Data Provider (all high-confidence hits from cache or bundle), runs deterministic checks, and returns `{passed: true, violations: [], disclosure_notes: []}`.
7. **The orchestrator returns the suggestion to the UI**, which renders it on the suggestion surface with the rejection-feedback affordance present but unobtrusive.
8. **The suggestion is logged** to the local event store with full provenance: which constraints were active, which were decisive, which ingredients were checked, how long the validation took.

### Pattern C: Chef regenerates after a rejection (Chef → Curator → Chef chain)

Day 5 still. Sukhi taps "this isn't quite right" on the masoor dal suggestion and selects "don't have the ingredients" (her masoor dal stock is empty).

1. **The UI sends a `reject_suggestion` intent to the orchestrator** with the rejection reason and the original suggestion ID.
2. **The orchestrator first invokes the Curator** with a `CuratorInput` where `intent_type = reject_suggestion`. The Curator's job is to record the rejection as contextual state: `{state: {flag: "ingredient_unavailable", ingredients: ["masoor_dal"]}, expires_at: end_of_session}`. It also emits a brief `user_message` like "Noted — let me find something else." This contextual constraint temporarily masks masoor dal from the Chef's next attempt.
3. **The Curator returns its output**, the orchestrator applies the state change, and then **the orchestrator invokes the Chef again** with a new `ChefInput`. The active_constraint_set now includes the session-scoped ingredient_unavailable flag. The `attempt_number` is still 1 because this is a fresh user-initiated request, not a regeneration loop (the previous suggestion passed validation; the user chose to reject it for a personal reason).
4. **The Chef reads the updated active set**, recognizes that masoor dal is temporarily unavailable, and composes a new suggestion: aloo methi with phulka roti.
5. **The orchestrator validates** the new suggestion. Passes.
6. **The new suggestion is returned to the UI.** Sukhi accepts it. The history layer logs both the rejected first suggestion (with reason) and the accepted second suggestion. Both events are part of the feedback-loop mechanism the PRD § 4 calls "the second-most-important feature in v1 after the constraint conversation itself."

These three patterns cover the dominant interaction shapes in v1. Other intents (begin_constraint_conversation, continue_constraint_conversation, request_suggestion_explanation, pantry_ingestion) follow the same shape: orchestrator → agent(s) → engine → validator (if applicable) → orchestrator → UI.

## 10. Future agents (v2 and v3)

> This section specifies the agents that arrive after v1 — Planner and Sourcing in v2, Observer in v3 — with enough detail that v2 and v3 planning can pick up where v1 leaves off without re-deriving them. Each follows the Section 2 archetype. None requires changes to the orchestrator, the constraint engine, the validator, or the Food Data Provider. They are additions to the agent roster, not architectural changes.

### Planner agent (v2)

**Role.** The Planner reasons across days (and, in the Family tier, across multiple profiles' constraints simultaneously) to produce week-ahead meal plans that respect every active constraint, minimize conflict, balance nutritional and cultural variety, and minimize waste by composing meals that share ingredients.

**Capability requirements.** Reasoning-strong (hardest multi-constraint reasoning in the product). Cultural-depth and nutritional-depth. **Long context window** — the Planner holds multiple profiles' constraint graphs, a week of meal history, and the household's recent rejection feedback in a single prompt. Interactive latency (the user is waiting for the plan).

**Input contract.** Typed `PlannerInput` with profile_ids (potentially multiple, for Family tier), active constraint sets for each, cooking_context, recent_history, planning_window (default: 7 days), and any household-specific preferences.

**Output contract.** Typed `PlannerOutput` with a list of `PlannedMeal` objects (each having the same shape as a ChefOutput), plus plan-level metadata (estimated total grocery cost, ingredient reuse statistics, conflicts that arose during planning and how they were resolved).

**Validator interaction.** The validator runs on *each individual meal* in the plan output, not on the plan as a whole. Every meal must pass validation before the plan is shown. Meals that fail are regenerated individually via Chef sub-calls (see below); plans with too many regeneration failures trigger conflict resolution at the plan level.

**Sub-agent calls.** The Planner calls the Chef as a sub-routine for individual meal generation. This is the first (and in v1/v2, only) exception to the "agents don't call other agents" rule — the Planner is explicitly allowed to invoke the Chef through the orchestrator, with the Planner's constraint context flowing into each Chef call. The v3 Observer does not make sub-agent calls; it is an analyzer, not a composer.

### Sourcing agent (v2)

**Role.** The Sourcing agent translates Chef and Planner outputs into shopping lists and (when the user chooses) Instacart orders per ADR 0003. Mostly tool-calling against the Instacart API; minimal reasoning beyond ingredient deduplication, quantity aggregation, and substitution handling when Instacart doesn't carry an exact item.

**Capability requirements.** Reasoning-light (mostly structured output and tool use). Tool-use-heavy world knowledge (understanding Instacart's product catalog conventions). Standard context window. Background latency — shopping list generation can happen slightly slower than meal suggestion.

**Input contract.** Typed `SourcingInput` with a set of meals (from Chef or Planner), the profile's pantry state, and the user's sourcing preferences (preferred store, delivery vs pickup, substitution tolerance).

**Output contract.** Typed `SourcingOutput` with a structured shopping list (ingredients, quantities, substitution candidates) and, when Instacart is invoked, an Instacart order confirmation.

**Validator interaction.** The Sourcing agent does *not* trigger separate post-generation validation because the underlying meals have already been validated upstream (by the Chef's own validation pass or the Planner's per-meal validation). The Sourcing agent operates on already-trusted output. Validation here would be duplicated work that doesn't add safety.

### Observer agent (v3)

**Role.** The Observer runs asynchronously over the user's accumulated eating history and surfaces long-term trends, predictions, and gentle warnings — rising sodium intake, drift from target macros, patterns the user might not notice themselves. Produces insights and disclosures, not meal suggestions.

**Capability requirements.** Reasoning-strong (pattern recognition over long histories). Nutritional-depth. **Long context window** — the Observer reads weeks or months of history in a single invocation. Background latency — Observer runs on a schedule (weekly? daily? TBD in v3 planning), not in response to immediate user requests.

**Input contract.** Typed `ObserverInput` with profile_id, historical meal data window, the constraint graph, and any profile-level health targets (e.g., "trying to keep sodium under 1500mg/day average").

**Output contract.** Typed `ObserverOutput` with a list of `Insight` objects, each containing a trend statement, a severity classification, optional gentle warnings, and optional recommended Curator actions (e.g., "it looks like your sodium average has been climbing — should I tag sodium as a medical limit for you?").

**Validator interaction.** The Observer lives **outside the validator path entirely** because it produces insights and warnings, not meal suggestions. There is nothing for a meal-validator to check. The Observer's outputs need a different review mechanism — likely a per-insight quality review by a human reviewer during v3 alpha — and that mechanism is deferred to v3 planning as an open question.

**Tone constraints.** The Observer is the agent most likely to violate the calm-precise-warm voice if built carelessly. Long-term health warnings can slide into anxiety-inducing or clinical territory. The Observer's prompts will need unusually careful authoring, and the v3 alpha needs to test the tone before the insights surface is publicly available.

## 11. Open questions

### Orchestration and routing

- **What is the exact escalation signal for Curator → Claude Opus on medical disclosures?** Section 8 says "indicators of a high-stakes medical disclosure" but the specific signal detection is not defined. Probably a combination of keyword matching and the Curator's own self-reported uncertainty; the implementation needs to be spelled out during build.
- **Should the orchestrator's intent classification itself ever be LLM-based?** Section 3 says intent routing is rule-based. But the *source* of the intent — how the UI decides which intent to send — is currently tied to specific UI surfaces (tapping "suggest a meal" sends `request_meal_suggestion`). If we ever want a unified free-text input where the user can say arbitrary things and the orchestrator figures out what they mean, that classification step might need to be LLM-based. Deferred as a v2 UI question.

### Chef behavior

- **How does the Chef handle "I want to make X tonight" requests** where the user names a specific dish? Section 5's input contract supports this through `request_details`, but the Chef's behavior when the requested dish conflicts with active constraints is not specified. Probably: suggest a modified version of the requested dish that respects the constraints, with a disclosure note explaining what changed. Worth testing in alpha.
- **Should the Chef's nutritional rough estimates be calibrated against the Food Data Provider's canonical data?** Section 5 says the FDP's summed values are authoritative for validation, and the Chef's estimate is a "sanity check." But calibration over time would be a genuinely useful signal for prompt iteration. Deferred to `testing-strategy.md`.

### Curator behavior

- **When the Curator is uncertain whether a new user statement is a new constraint or a modification of an existing one**, what's the right default? Currently it's probably "ask the user a clarifying question," but alpha data may show users prefer silent best-guess interpretation with an undo affordance.
- **How does the Curator handle revocation language?** Example: Sukhi says "actually, I can have onions now, my gut is better." This is clearly a `removeConstraint` (or more accurately, a scope adjustment on an existing constraint), but the Curator's prompt needs to recognize revocations as first-class operations, not just additions. Specified in the prompt, tested in alpha.

### Prompt evolution

- **What is the versioning discipline for `prompts/*.md` files?** Current plan is "every change is a git commit with a message explaining the rationale," but more formal version numbers and per-prompt changelog entries may be needed once alpha has multiple users and we're running A/B experiments. Revisit when alpha hits ~10 users.
- **How do we evaluate prompt changes?** The hard-cases test suite from `testing-strategy.md` is the regression floor, but we also need a way to measure whether a prompt change made the agents *better* on the non-regression cases. Deferred to `testing-strategy.md`.

### Agent-to-agent in v2

- **The Planner-calls-Chef exception from Section 10 is the first breach of "agents don't call agents."** The discipline was good for v1 (three agents, linear flow). Does it stay sustainable when v2 has five agents and one of them can call another? Probably yes, because the Planner → Chef call is structured and bounded (the Planner can call Chef for meal generation, nothing else). But worth watching carefully during v2 planning — the failure mode of "agents form a web of mutual calls" is a real risk that we must actively resist.

## 12. Cross-references

### What this document references

- `vision.md` — for tone, the calm-precise-warm voice, the five pillars
- `PRD.md` — for Sukhi, the v1 user journey, the feature scope
- `technical-architecture.md` — for the orchestration layer's position in the component map
- `constraint-engine-spec.md` — for the constraint graph API, the severity tier semantics, the validator's mechanical contract, and the conflict resolution algorithm
- `ui-ux-spec.md` — for how the unified-entity model (the orchestrator routing, agents never exposed as a user-facing roster) is presented in the UI: the conversation FAB, the "talk to Cuizine" front door, and specialization revealed in-conversation rather than operated through a menu
- ADR 0002 — cultural fluency as the fifth pillar (shapes Chef capability requirements)
- ADR 0004 — multi-profile households (shapes v2 Planner input contract)
- ADR 0006 — multi-provider per-agent routing (Section 8's routing table is the v1 concretization)
- ADR 0009 — conflict resolution policy (Section 3's regeneration loop + `constraint-engine-spec.md` Section 8)
- ADR 0010 — post-generation validation (Section 7's validator clarification)
- ADR 0011 — optional backend with capability tiers (shapes how agent calls are routed through the Cloud Function proxy for signed-in users)
- ADR 0012 — food data sources (shapes Pantry agent, Chef agent, and validator ingredient resolution)

### What this document defers to deeper-dive docs

- **`prompts/curator.md`, `prompts/chef.md`, `prompts/pantry.md`** — the actual system prompt text for each agent, iterated separately from this architectural doc
- **`data-model.md`** — the typed input and output schemas (CuratorInput, ChefOutput, etc.) specified field-by-field
- **`testing-strategy.md`** — the evaluation harness for each agent, the hard-cases test suite, the calibration methodology for prompt iterations
- **`local-first-sync.md`** — how agent calls are routed through the Cloud Function proxy versus direct-from-device in the signed-out path
- **`security-and-privacy.md`** — threat model for the agent layer, including the Curator's write access and the provenance disclosure surface

### What this document does *not* defer (decisions made here)

- The agent archetype and its seven common properties (Section 2)
- The orchestrator's structure, responsibilities, and intent routing table (Section 3)
- The three v1 agents' roles, contracts, prompt structures, tool access, and error handling (Sections 4-6)
- The validator's non-agent status and its slotting into the agent flow (Section 7)
- The v1 per-agent provider routing table and escalation criteria (Section 8)
- The worked interaction patterns from Sukhi's life (Section 9)
- The Planner, Sourcing, and Observer agents' architectural specifications for v2 and v3 (Section 10)

### How the agent should use this doc

When the coding agent builds the agent layer, Sections 2-6 are the authoritative specification for what each v1 agent is and does. Section 2's archetype is the base pattern every agent must conform to; Sections 4-6 are the specific instantiations. Section 3's orchestrator specification should be implemented as deterministic code with no LLM involvement in the routing path. Section 7's validator clarification is the firewall against treating deterministic checking as agent reasoning. Section 8's routing table is the v1 default and is revisable based on eval data without architectural change. Section 9's patterns are the end-to-end test cases the implementation must support correctly. Section 10's future agents are for v2/v3 planning, not v1 build.

When the agent encounters a question this doc does not answer, the discipline from PRD § 9 applies: check the vision, check the relevant ADR, check the constraint engine spec, check the architecture doc, ask the founder before guessing. The agent layer is the part of the system most directly exposed to users, and silent drift here compounds into the user experience more rapidly than anywhere else. The cost of asking is always smaller than the cost of inheriting wrong assumptions.

---

*End of `agent-architecture.md` v1 (initial draft). Next revision will incorporate any contracts that surface during the writing of `data-model.md` and `testing-strategy.md`, and any learnings from the v1 alpha build. Prompt files under `prompts/` are versioned independently and evolve faster than this doc; when a prompt change surfaces a structural question, update this doc alongside the prompt change.*
