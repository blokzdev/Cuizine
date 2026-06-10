# Cuizine — Constraint Engine Specification

> The heart of pillar 1. This document is the logical specification of Cuizine's constraint engine — what a constraint is as a data structure, what operations the engine supports, how conflicts are resolved mechanically, and how the validator checks suggestions against the graph deterministically. Everything below is *logical*: it describes entities, relationships, operations, and algorithms. The *physical* translation to SQLite tables and Room entities/DAOs lives in `data-model.md`. Every agent that reads from or writes to the engine — the Curator, the Chef, the Pantry agent, the validator, and every future v2/v3 agent — reads from this document to understand its contract with the engine.

## 1. Purpose & how to read this doc

This document specifies the **logical design of the constraint engine** that powers pillar 1 (layered constraints respected automatically) for Cuizine v1 and remains the same architecture through v2 and v3. It assumes you have read `vision.md`, `PRD.md`, `technical-architecture.md`, and the ADRs — especially ADR 0004 (profile ownership), ADR 0009 (severity-tiered conflict resolution), ADR 0010 (post-generation deterministic validation), and ADR 0012 (food data sources and the three-layer architecture). Concepts established there are not re-derived here.

This document defines: the vocabulary of constraints; the type taxonomy of constraints; the severity tier model and its mechanical rules; the scope dimensions (temporal, contextual, household, profile) along which constraints can be active or inactive; the constraint graph API as a set of operations; the validator's deterministic contract; the conflict resolution algorithm; the provenance and audit metadata; and the schema versioning and migration strategy.

This document does **not** define how constraints are *physically* stored (Room entities, column types, indices — `data-model.md`), how the Curator agent *produces* constraints from natural language (`agent-architecture.md` and `prompts/curator.md`), how the Chef agent *consumes* the constraint graph to generate suggestions (`agent-architecture.md`), or how the cache, curated bundle, and external sources are physically integrated (`data-model.md` and ADR 0012). These are logical-to-physical mappings that live elsewhere.

**When this document and a deeper-dive doc disagree,** this doc wins for matters of logical structure (what a constraint is, what operations exist, how severity and scope compose), and the deeper-dive doc wins for matters of physical representation (field types, table layouts, index strategies). If a conflict arises between logical and physical that cannot be cleanly split, it's a design bug that needs a deliberate decision, not a silent resolution.

## 2. Foundational concepts and vocabulary

> This section defines the vocabulary the rest of the document relies on. Every term below is used with precision; every term that is *not* defined here is either external (e.g. "Sukhi," "Punjabi," "IBS") or belongs to another doc. When the Curator, Chef, validator, or any future agent needs to reason about constraints, these are the words they use.

### Profile

A **profile** is the first-class entity that owns a constraint graph. A profile represents one human (or one human's situation at a point in time, for cases like pregnancy where the constraint reality is bounded). Every profile has exactly one constraint graph. In v1, each user account holds exactly one profile (their own). In v2, an account can own multiple profiles (self plus dependents — per ADR 0004). In v3, a profile can be owned by one account and *linked* to another with peer-appropriate visibility.

A profile carries metadata distinct from the constraint graph itself: its `owner` (the account ID that controls it), its `display_name` (how the user names it — "Myself," "Beeji," "Simran"), its `relationship_type` (self, silent-dependent, consent-aware-dependent, adult-dependent, linked-partner), and its `created_at` / `modified_at` timestamps. These fields live in the data model, not inside the constraint graph, but they are referenced constantly by the engine and agents and need to be present in the vocabulary.

### Constraint graph

A **constraint graph** is the complete set of constraints for one profile at a given point in time. The word "graph" is meaningful: constraints can *reference other constraints* (one constraint's scope can be conditional on another's state, one can supersede another when both are active), and the relationships between them matter. In most cases the graph is shallow and looks like a flat list, but the engine represents it as a graph because the shallow cases are a special case of the full structure.

A constraint graph is read by the Chef, Pantry, Planner, and validator, and it is written *only* by the Curator (and, rarely, by the user through a manual Settings edit — a v2 capability). The read/write asymmetry is important and enforced architecturally: no other component has write access to the graph.

### Constraint

A **constraint** is a typed, structured, first-class entity representing one rule that applies to the profile's eating life. Per the schema philosophy locked in by user answer to our schema-richness question, every constraint is a *rich object* with a full set of fields regardless of how simple the underlying rule is. The fields are specified in Section 3 (The constraint type taxonomy). The schema is uniform; simple constraints just leave optional fields empty or default.

Constraints are the central data type of the engine. Every other concept in this document is either a property of a constraint, an operation on constraints, or a relationship between constraints.

### Severity

The **severity** of a constraint is a tag drawn from the four-tier model established in ADR 0009: **Inviolable**, **Medical**, **Religious & Cultural**, or **Preference**. Severity determines how the engine handles conflicts, whether the validator can AI-fallback on unknown ingredients, whether silent relaxation is permitted, and whether the user is interrupted when a conflict cannot be resolved automatically. Severity is not a number or a priority ordering — it is a *qualitative* tag that the conflict resolution algorithm reads to determine which of several formally-distinct paths to take.

The phrase "high severity" and "low severity" should be avoided in this document (and in code) because they imply a linear ordering that the four-tier model does *not* have: Medical and Religious & Cultural are co-equal at the second/third tier (per ADR 0009), not ordered. Use the explicit tier names.

### Scope

The **scope** of a constraint is the set of conditions under which it is *active*. A constraint whose scope does not currently match the user's context is *inactive* and does not influence the engine's reasoning. The scope dimensions are specified in Section 5 and include temporal (days of the week, date ranges, recurring patterns), contextual (user-stated situational states like "I'm traveling this week" or "my gut has been off"), household (which profiles the constraint applies to — v2), and profile (whose constraint is this — always equal to the containing profile in v1, but structurally present because future v2/v3 cases need it).

Scope is a structured object, not a boolean, because a single constraint can be active on some days and inactive on others (Aisha's Ramadan constraint is active for 30 days a year), can be conditional on another constraint (Sukhi's "no onions during IBS flares" is active only when her IBS-flare contextual flag is set), or can be bounded by a date range (a pregnancy-specific constraint that auto-deactivates after the due date).

### Provenance

The **provenance** of a constraint is the metadata about *how it came to exist*. Provenance is load-bearing for the conflict resolution UX (per ADR 0009), the alpha feedback review (per PRD § 5), and the validator's confidence decisions (per ADR 0010). Every constraint carries provenance fields: *who added it* (user directly, Curator agent inferred from user speech, AI-derived from an ingredient categorization, imported from a profile template), *when it was added*, *in what context* (during initial constraint conversation, during a free-text update, during a rejection feedback event), *the original phrasing the user used* (if the constraint came from user speech — stored verbatim for disclosure purposes), and *the confidence tag* (`user-verified`, `curator-inferred`, `ai-derived-low-confidence`, `bundle-default`, etc.) paralleling the confidence tagging from ADR 0012.

Provenance is never shown to the user unprompted — that would violate the calm-precise-warm tone. It is available to the user on demand ("why did you suggest this?" / "why is this off-limits for me?") and is always available to the orchestrator, the validator, and the alpha instrumentation.

### Active constraint set

The **active constraint set** for a profile at a given moment is the subset of the profile's constraint graph whose scopes currently match the user's context. The Chef agent, the Planner agent (v2), and the validator operate on the active constraint set, not on the full graph. The engine is responsible for computing this set efficiently and correctly on every query — this is the central operation of the engine and is specified in Section 6 (The constraint graph API).

### Conflict

A **conflict** is a situation in which two or more active constraints for a profile (or across profiles in a v2 household planning context) produce contradictory requirements for a meal. Conflicts are resolved by the algorithm in Section 8, following the policy established in ADR 0009. A conflict is not an *error* — it is the central case the engine exists to handle — and the engine is designed to handle conflicts gracefully rather than throw exceptions.

### Suggestion

A **suggestion** is a structured output from the Chef agent (or in v2, from the Planner agent) representing a proposed meal or plan. The engine does not generate suggestions — the agents do — but the engine receives suggestions for validation, and the validator's mechanical contract (Section 7) specifies what fields a suggestion must contain for the validator to do its job.

### Ingredient entry

An **ingredient entry** is a canonical, structured representation of one ingredient, as returned by the Food Data Provider (per ADR 0012). The engine's validator reads ingredient entries from the Food Data Provider to check whether a suggestion violates the user's active constraints. An ingredient entry includes categories, allergens, nutritional properties, and a confidence tag. The engine treats ingredient entries as opaque inputs to its reasoning — the internals of how the Food Data Provider produces them (cache, bundle, external sources, AI fallback) are invisible to the engine.

## 3. The constraint type taxonomy

> This section enumerates the *kinds* of constraints the engine supports. Every constraint in a profile's graph is an instance of exactly one of these types. The type determines the semantics of the constraint's fields and the validator's rule check against it. Types are finite and closed — new types require a schema version bump and a migration (Section 10). The type taxonomy is deliberately small: five types cover every constraint pattern we've encountered in the persona work, the secondary personas, and the hard-cases test suite design.

Every constraint, regardless of type, carries the same structural envelope: an `id`, a `type` (one of the five below), a `severity` (one of the four tiers), a `scope` (Section 5), `provenance` (Section 9), a `created_at` / `modified_at` pair, and type-specific `payload` fields defined below.

### Type 1: `avoid`

An **avoid** constraint says: *this ingredient or category should not appear in suggestions for this profile when the scope is active.* The simplest and most common type. Sukhi's "I can't have onions" is an avoid constraint with severity Medical, targeting the onion ingredient entry, with scope conditional on her IBS-flare flag (or unconditionally active if she avoids onions always). Aisha's Ramadan fast is a family of avoid constraints (no food or drink during fasting hours), scoped to the fasting window.

Payload fields:
- `target`: a reference to an ingredient entry OR an ingredient category (e.g. "onion" or "all alliums"). The `target` is resolved through the Food Data Provider; categorical targets (from ADR 0012's categorical reasoning) expand to cover all member ingredients at validation time.
- `exceptions`: optional list of ingredient entries the user has explicitly marked as safe despite being in the category. Useful for edge cases like "no dairy, but ghee is fine for me" — the user's own documented tolerance.

### Type 2: `prefer`

A **prefer** constraint says: *this ingredient, cuisine, or dish should be favored in suggestions when reasonable.* Prefer constraints do not *require* inclusion; they bias the Chef agent's reasoning. Sukhi's "I love rajma" is a prefer constraint with severity Preference. Prefer constraints are never inviolable and never medical — they live in the Preference tier by definition.

Payload fields:
- `target`: the ingredient, cuisine, dish name, or cooking technique being preferred.
- `strength`: a small ordinal (low, medium, high) indicating how strongly the preference should bias suggestions. Default is medium.

### Type 3: `require`

A **require** constraint says: *this ingredient or nutritional property must appear in suggestions when the scope is active.* Rarer than `avoid` but real. A user on a low-iron diet might have a Medical-tier `require` for iron-rich foods across the day. Aisha breaking her Ramadan fast has a contextual `require` for hydrating foods at iftar. Require constraints are conceptually symmetric with avoid constraints but operationally more complex because the Chef can satisfy an avoid trivially (just don't include the thing) whereas satisfying a require means actively finding something to include.

Payload fields:
- `target`: the ingredient, category, or nutritional property being required.
- `window`: whether the requirement applies to a single meal, a day, a week, or the active scope (for v3 Observer-tracked requirements). v1 supports single-meal and daily windows; weekly windows are v2.

### Type 4: `limit`

A **limit** constraint says: *this nutritional property, ingredient, or category must not exceed a specified quantity within a window.* The classic example is a low-sodium diet (limit sodium to 1500mg/day) or diabetic carb management (limit carbs per meal). Limit constraints require the validator to do *quantitative* reasoning, not just categorical, which means they depend on the Food Data Provider returning nutritional composition data — a direct dependency on ADR 0012's USDA integration.

Payload fields:
- `target`: the nutritional property (sodium, carbs, sugar, potassium, phosphorus, etc.) or ingredient being limited.
- `ceiling`: the numeric limit with units (e.g. `{value: 1500, unit: "mg"}`).
- `window`: per-meal, per-day, or per-week.
- `hard_or_soft`: whether exceeding the limit is a hard rejection (hard) or triggers a disclosure note (soft). Hard limits behave like avoids above the ceiling; soft limits behave like strong preferences.

### Type 5: `contextual`

A **contextual** constraint is a special type that represents *a situational flag the user has reported* rather than a permanent rule. "My gut has been off all day," "my blood sugar ran high this morning," "I'm in Toronto for a few days" — these are contextual constraints. They always have a short expiration (hours, days, or the length of the trip), they always come from user free-text input via the Curator agent, and they always *interact* with other constraints rather than standing alone. A contextual constraint typically acts as a scope modifier: Sukhi's "my gut is off today" contextual flag activates a more restrictive set of her standing IBS-related avoid constraints.

Contextual constraints are the mechanism by which pillar 4 (instant context adaptation) is implemented. The engine treats them as first-class constraints in the graph, with scope limited by their expiration and with payload fields describing what contextual state they represent.

Payload fields:
- `state`: a structured representation of the contextual state (e.g. `{flag: "ibs_flare", severity: "moderate"}` or `{flag: "traveling", location: "Toronto"}`).
- `expires_at`: when the contextual state ends. For "today only" flags, this is end-of-day in the user's timezone. For trip flags, it's the end of the trip if the user provided one, otherwise a default of seven days (renewable by the user).
- `triggers`: optional list of other constraint IDs that this contextual state activates or intensifies. The Curator populates this when it recognizes that a contextual flag should modify existing constraints' scope.

### What the taxonomy explicitly does not include

A few types we considered and deliberately left out, so the agent doesn't accidentally invent them:

- **`warn`**: A constraint that neither avoids nor prefers but flags an ingredient for disclosure. Rejected because the warn semantic is subsumed by the validator's existing disclosure mechanism for low-confidence AI-derived entries (per ADR 0012) and by the inline notes on soft-limit constraints. Adding a separate type would duplicate functionality.
- **`goal`**: A constraint expressing a long-term goal like "lose weight" or "improve cardiovascular health." Rejected for v1 because goals require the v3 Observer agent to track progress meaningfully, and shipping the type without the tracking infrastructure would produce hollow behavior. Revisit in v3.
- **`substitute`**: A constraint saying "when a recipe calls for X, substitute Y." Rejected as a constraint type because substitution is a *Chef behavior*, not a rule on the profile. The Chef can learn substitutions from user feedback history and bundle corrections, but the engine doesn't need a formal constraint type for them.
- **`allergy`** as a distinct type from `avoid`. Rejected because allergy is a *severity* (Inviolable) applied to an avoid constraint, not a separate type. Keeping allergy as a severity tag on avoid preserves the uniform envelope.

The five types above cover every constraint pattern we've identified in the persona work and the hard-cases design. If a new pattern surfaces during alpha that none of these types can express, that's a signal to write a new ADR before extending the taxonomy — not a signal to silently add a sixth type.

## 4. The severity tier model (mechanical rules)

> ADR 0009 establishes the four severity tiers as a policy. This section translates them into mechanical rules the validator and conflict resolution algorithm implement. Every rule below is a direct consequence of ADR 0009; this section makes them explicit so the agent implementing the engine has concrete behavior to build against rather than having to re-derive rules from policy prose.

### Tier 1: Inviolable

**What it means:** the constraint cannot be violated under any circumstances. Silent relaxation is forbidden. User-initiated relaxation is forbidden (or requires a separate explicit confirmation flow that does not exist in v1 and will not exist without a deliberate future ADR). AI fallback on unknown ingredients is forbidden (per ADR 0012) — unknown ingredients cause the suggestion to be rejected, not AI-categorized.

**What can be tagged Inviolable:** allergies the user has explicitly marked as Inviolable during onboarding or Settings, hard religious prohibitions the user has explicitly marked as Inviolable (a peanut allergy that triggers anaphylaxis, a strict halal observance the user has declared non-negotiable), and medication-food interactions the user has explicitly flagged as dangerous.

**What cannot be tagged Inviolable:** preferences, soft religious observances, contextual flags, or constraints the Curator has inferred from user speech without explicit user confirmation of the Inviolable status. The Inviolable tier is *only* applied by explicit user action, never by agent inference.

**Validator behavior:** any suggestion that violates an active Inviolable constraint is rejected outright. Regeneration is invoked up to three times (per ADR 0010); if all three regenerations fail, the user is shown the honest fallback message ("I couldn't find a meal that works tonight") rather than any violating suggestion.

**Conflict resolution behavior:** Inviolable constraints cannot be relaxed by the conflict resolution algorithm. If no meal can be found that satisfies an Inviolable constraint along with the other active constraints, the engine does *not* surface a conflict UX asking the user to pick which constraint to relax — Inviolable constraints are never part of the pickable set. Instead, the engine tells the user no meal could be found, honestly.

### Tier 2: Medical

**What it means:** the constraint represents a health-related rule the user has marked as medical. Taken seriously, never silently overridden, but can be temporarily relaxed by explicit user choice in a specific moment ("I know this isn't ideal but I want it anyway tonight"). Temporary relaxation is recorded so the engine doesn't repeat the violation by inference on subsequent suggestions.

**What can be tagged Medical:** constraints the user has identified as relating to a health condition — diabetes management, IBS triggers, kidney-disease limits, cholesterol management, etc. The Curator agent applies this severity when the user explicitly mentions a health condition in the constraint conversation.

**Validator behavior:** any suggestion that violates an active Medical constraint is rejected and regenerated (same loop as Inviolable). The difference between Medical and Inviolable appears only in the conflict resolution algorithm and the AI fallback rules, not in the validator's core rejection behavior.

**AI fallback behavior:** unknown ingredients encountered while checking a Medical constraint are AI-categorized using the cheap-tier LLM (per ADR 0012), the result is tagged `low-confidence-ai`, and if the AI-derived entry indicates the ingredient is *safe*, the validator passes the suggestion with an inline disclosure note to the user. This is the "Sukhi and ramps" case worked through in ADR 0012.

**Conflict resolution behavior:** Medical constraints are *co-equal with Religious & Cultural constraints* in the conflict resolution algorithm. Neither takes precedence over the other. When a conflict between a Medical and a Religious & Cultural constraint cannot be resolved by silent preference relaxation, the engine surfaces the conflict to the user for explicit choice (Section 8). This co-equality is deliberate and load-bearing for pillar 5 (cultural fluency) — see ADR 0002.

### Tier 3: Religious & Cultural

**What it means:** the constraint represents a rule rooted in religious observance, cultural tradition, or strongly-held identity. Taken seriously, never silently overridden, can be temporarily relaxed by explicit user choice. Structurally identical to Medical except for the provenance of the rule.

**What can be tagged Religious & Cultural:** halal, kosher, Lenten observance, Hindu vegetarian practices, Jain dietary restrictions, fasts tied to religious calendars, cultural traditions around specific foods, and identity-rooted preferences the user has explicitly characterized as more than mere taste. The Curator applies this severity when the user explicitly mentions religion, tradition, or cultural practice.

**Validator, AI fallback, and conflict resolution behaviors:** identical to Medical, with co-equal standing in conflict resolution.

### Tier 4: Preference

**What it means:** personal preferences, dislikes, dietary leanings rooted in taste or choice rather than necessity, aesthetic considerations. Can be overridden by the engine's reasoning when a higher-tier constraint would otherwise be violated, but only after the engine has tried to find a satisfying option that respects the preference too. Preference overrides are silent — the user is not interrupted to ask permission to ignore a mild dislike.

**What can be tagged Preference:** vegetarian-by-choice (not religious or medical), dislikes (no cilantro, don't love eggplant), dietary leanings (mostly-pescatarian), aesthetic considerations (don't serve mashed foods to kids). The Curator applies this severity when the user explicitly mentions preference, choice, or taste — or by default when the user mentions something that isn't clearly medical or religious.

**Validator behavior:** the validator checks preference constraints but treats violations as *soft* — a preference violation does not cause suggestion rejection on its own. Instead, the validator logs the preference violation in the suggestion's provenance metadata, and the orchestrator's suggestion-ranking logic (Chef agent responsibility, specified in `agent-architecture.md`) may use the preference violation to prefer regeneration over acceptance when alternatives exist.

**AI fallback behavior:** AI categorization runs for unknown ingredients at the Preference tier without user-facing disclosure. Cache entries are still tagged `low-confidence-ai` for audit purposes.

**Conflict resolution behavior:** preferences are the *only* tier that can be silently relaxed by the conflict resolution algorithm. When two higher-tier constraints conflict, the algorithm first tries to resolve by relaxing any preference-tier constraints that are also blocking satisfaction. Only after all preferences have been silently relaxed does the algorithm surface a conflict to the user.

### The explicit disallowed combinations

Some combinations of type and severity are not valid and the engine rejects them at constraint-creation time:

- A **`prefer`** constraint cannot have severity Inviolable, Medical, or Religious & Cultural. Prefers live in the Preference tier by definition. If a user tries to express something stronger through the Curator conversation (e.g. "I *really* love rajma, I need it every week"), the Curator should represent this as a `require` constraint, not a strong `prefer`.
- A **`contextual`** constraint cannot have severity Inviolable. Contextual flags are inherently transient and user-reported; treating them as Inviolable would be inconsistent with their nature. Contextual constraints can be Medical, Religious & Cultural, or Preference depending on what they modify.
- A **`limit`** constraint with a **soft** hard_or_soft setting cannot have severity Inviolable. Inviolable implies hard enforcement; soft limits are by definition relaxable. If a user has a strict limit that must never be exceeded, it should be marked hard.

These combinations are rejected by the constraint graph API (Section 6) at write time, not silently coerced. A Curator agent that tries to add a disallowed combination receives an error and must resolve it (usually by asking the user a follow-up question to disambiguate).

## 5. Scope dimensions

> The scope of a constraint determines when it is *active*. The engine's central query — "what constraints are active right now for this profile?" — walks the full constraint graph, evaluates each constraint's scope against the current context, and returns the active subset. Scope is the mechanism by which a single constraint graph serves the user differently across time, location, health state, and household composition without duplicating constraints. Five dimensions compose to form a constraint's full scope; a constraint is active when *all* dimensions evaluate to true.

### Dimension 1: Temporal

**Temporal scope** answers: *is this constraint active on the current date, day, time, or phase?* A temporal scope is a structured expression — not a free-form rule — so the engine can evaluate it deterministically without an LLM call. The engine supports the following temporal scope shapes:

- **Always** — the default. The constraint has no temporal restriction.
- **Recurring weekly** — a set of weekdays (e.g. `[TUE]` for Sukhi's Tuesday fast).
- **Recurring daily window** — a time range within each day (e.g. "sunrise to sunset" for Aisha's Ramadan fasting hours). Sunrise/sunset is computed from the user's location on each day, not hardcoded.
- **Date-bounded** — a `[start, end]` range with optional open ends (e.g. Ramadan 2026 starts on a specific date and ends on a specific date; a pregnancy-specific constraint starts at diagnosis and ends at the due date).
- **Phase-bounded** — bound to a named phase tracked elsewhere in the profile (e.g. "first trimester," "post-surgery recovery window"). Phases are profile-level state, not constraint-level state, so multiple constraints can reference the same phase.
- **Composite** — an AND/OR combination of the above (e.g. Aisha's fasting-hours constraint is "recurring daily window sunrise-to-sunset" AND "date-bounded Ramadan 2026").

Temporal scope is evaluated against the **user's timezone**, which is a profile field. A user traveling across timezones can update their effective timezone through a contextual constraint; this is one of the cases where contextual constraints (type 5 from Section 3) modify temporal scope.

### Dimension 2: Contextual

**Contextual scope** answers: *is this constraint active given the user's current reported situational state?* Contextual scope references one or more contextual constraints (Type 5 from Section 3) that must be active for this constraint to be active. Sukhi's "avoid onion-garlic during IBS flares" has a contextual scope conditional on the presence of an active `ibs_flare` contextual constraint. If Sukhi has not reported an IBS flare recently, the avoid-onion-garlic constraint is inactive and the Chef can suggest meals with onion and garlic normally.

Contextual scope has two forms:

- **Requires-flag** — the referenced contextual constraint must be active for the constraint to be active. "This constraint kicks in when I'm traveling."
- **Excludes-flag** — the referenced contextual constraint must be *inactive* for the constraint to be active. Rare, but real: "this preference for rich foods doesn't apply when my gut is off."

### Dimension 3: Location

**Location scope** answers: *is this constraint active given the user's current physical location?* A constraint can be scoped to apply only when the user is in a particular city, country, region, or travel state. Examples: a dairy-avoid constraint that applies only while traveling in a country where dairy quality is uncertain; a preference for a specific cuisine that's only meaningful while at home; a religious observance that changes rules when on pilgrimage.

Location scope is evaluated against the user's current location, which is updated through contextual constraints (e.g. the user telling Cuizine "I'm in Toronto for a few days" creates a location-flagged contextual constraint). In v1, location is coarse (country and region level); finer-grained location awareness is deferred to v2 if alpha data shows it's needed.

Most v1 constraints will have **location = anywhere** as the default. Location scope exists for secondary persona Aisha and travelers like her, and for v3 cases where users cross markets (Canadian expat in the UK during v3 global expansion).

### Dimension 4: Household

**Household scope** answers: *which profiles in a household does this constraint apply to?* In v1, this dimension is trivially "the single profile that owns this constraint graph" because v1 is single-profile per account. In v2, when Sukhi has dependent profiles for Beeji, Simran, and Harpreet, household scope becomes meaningful: a constraint added to Sukhi's profile might apply only to her (her diabetes), only to Simran (her vegetarianism), or to the whole household (the family avoids pork).

Household scope has three forms in v2:

- **Self** — the constraint applies only to the profile that owns it.
- **Specific profiles** — the constraint applies to a named set of profiles in the household.
- **Whole household** — the constraint applies to every profile in the household, including any profiles added in the future.

In v1, household scope defaults to "self" for every constraint, and the dimension exists in the schema solely for v2 readiness (per the architectural discipline from ADR 0004). The engine's active-set computation checks household scope and it always passes in v1 because there is only one profile, but the code path exists.

### Dimension 5: Profile

**Profile scope** is the innermost dimension: *which profile does this constraint belong to?* This is trivially the profile whose graph contains the constraint, but it's named as a dimension because the v2 Planner agent composes active sets from multiple profiles (the whole household) and must be able to attribute each constraint back to its source profile for conflict resolution and disclosure purposes.

In v1, profile scope is always equal to the containing profile (there is no cross-profile reasoning). In v2, the Planner queries the engine for "the union of active constraint sets across these profiles," and every constraint in the result carries its profile attribution.

### How scope composes: the active-set computation

For a given query moment, the engine computes the active constraint set as follows:

1. Start with the full constraint graph for the profile (or set of profiles, for v2 household queries).
2. For each constraint in the graph, evaluate its scope dimensions in order: temporal first (cheapest to evaluate, most selective), then contextual, then location, then household, then profile.
3. If *every* dimension evaluates to true, the constraint is in the active set. If any dimension evaluates to false, the constraint is inactive for this query.
4. The active set is returned as a structured object carrying the full constraint records plus computed attribution (which scope dimensions were decisive in activating or deactivating each constraint, for audit purposes).

The active-set computation is the engine's hottest path — it runs on every Chef query, every validator check, and every Planner reasoning step. The engine caches active sets at the query level with invalidation on any constraint graph write or contextual state change. Invalidation is fine-grained: adding a new avoid constraint invalidates the cache; adding a new prefer constraint does not if the Chef query doesn't depend on preferences.

## 6. The constraint graph API

> This section specifies the operations the engine supports as a set of named methods with inputs, outputs, and semantics. Every agent that talks to the engine talks through this API — there is no other read or write path. The API is deliberately small (eleven operations) because a small surface is easier to test, easier to reason about, and harder for the agent to misuse.

### Read operations

- **`queryActive(profile_id, at_time=now, context=current)`** — Returns the active constraint set for a profile at a given moment, computed per Section 5. Most common operation. Called by the Chef on every suggestion request, by the validator on every check, by the Planner on every reasoning step, and by the orchestrator whenever it needs to know what rules apply. Returns: the set of active constraint records with attribution metadata.
- **`queryAll(profile_id)`** — Returns the full constraint graph for a profile, active and inactive alike. Called by the Settings UI (user wants to see everything) and by the export function (data portability). Rarely called in the hot path. Returns: the complete constraint graph with full metadata.
- **`queryByType(profile_id, type, at_time=now)`** — Returns active constraints of a specific type (avoid, prefer, require, limit, contextual). Optimization for the Chef agent when it only cares about avoids, or the Observer agent (v3) when it only cares about limits. Returns: a filtered active set.
- **`queryProvenance(constraint_id)`** — Returns the provenance metadata for a specific constraint (Section 9). Called when the user asks "why is this on my list?" or when the validator needs to explain a rejection. Returns: the provenance record.
- **`queryConflicts(profile_id, at_time=now)`** — Returns any conflicts currently present in the active set without checking a suggestion. Called by the Settings UI and by alpha diagnostics. Returns: a list of conflict records describing which constraints conflict and at what tier.

### Write operations

- **`addConstraint(profile_id, constraint)`** — Adds a new constraint to the profile's graph. Validates the constraint envelope against the rules in Sections 3 and 4 (type-severity compatibility, disallowed combinations, scope well-formedness) and rejects invalid constraints with a structured error. Called almost exclusively by the Curator agent during the constraint conversation and during free-text updates. Returns: the canonical constraint record with assigned ID and provenance fields populated.
- **`updateConstraint(profile_id, constraint_id, changes)`** — Modifies an existing constraint (severity change, scope change, payload change). Revalidates the modified constraint against the rules. Rarely used — most "updates" are handled by adding or removing constraints rather than editing. Available for the Settings UI's manual-edit case (v2) and for the Curator when it realizes a previous inference was wrong. Returns: the updated constraint record with an incremented version number.
- **`removeConstraint(profile_id, constraint_id)`** — Removes a constraint from the graph. Does not hard-delete — the constraint is marked as `removed_at` with a timestamp and preserved for audit purposes (Section 9). Called by the Settings UI and by the Curator when the user explicitly retracts a constraint. Returns: the removal confirmation record.
- **`setContextualState(profile_id, state, expires_at=auto)`** — A specialized write operation for contextual constraints. Takes the contextual state (e.g. `{flag: "ibs_flare"}`), an optional expiration (defaults based on state type), and creates the appropriate contextual constraint entry. Called by the Curator during free-text updates. Returns: the new contextual constraint record.

### Validation operations

- **`validateSuggestion(profile_id, suggestion, at_time=now)`** — The core validator entrypoint from ADR 0010. Takes a Chef agent suggestion (structured: meal name, ingredient list, rough nutritional properties if available), computes the active constraint set, parses the suggestion's ingredients through the Food Data Provider, and runs the deterministic rule check from Section 7. Returns: a validation result carrying `passed: bool`, `violations: [...]`, and `disclosure_notes: [...]` for the user-facing inline notes.
- **`resolveConflict(profile_id, conflict_id, user_choice)`** — Called when the conflict resolution algorithm (Section 8) has surfaced a conflict to the user and the user has picked an option. Records the user's choice as a temporary relaxation (scoped to this session), triggers Chef regeneration with the updated constraint state, and logs the choice to provenance. Returns: the regenerated suggestion or a "no meal found" error.

### Operations explicitly not in this API

A few operations we considered and deliberately left out:

- **`deleteProfile`**: profile lifecycle management belongs to the data model and account layers, not the constraint engine API.
- **`exportGraph`**: data portability is handled by the app-wide export function, which calls `queryAll` internally. Not an engine API.
- **`importGraph`**: no v1 use case. Profile templates are a deferred v3 consideration (ADR 0004 alternatives).
- **`mergeGraphs`**: v2 household planning uses `queryActive` with multiple profile IDs, not a merge operation. No merge semantics needed.
- **`bulkWrite`**: every constraint addition during the constraint conversation is a separate `addConstraint` call. Batching is a performance optimization and not a semantic need.

The eleven operations above (five reads, four writes, two validation) cover every interaction the v1 agents and UI need. New operations require a deliberate decision — ideally an ADR if the addition is architectural or a PRD revision if it's feature-driven.

## 7. The validator's mechanical contract

> ADR 0010 establishes the post-generation validator as non-optional, non-configurable, non-skippable, and deterministic. This section translates that policy into a concrete algorithm the validator implements step by step. The goal is a specification precise enough that two engineers implementing it independently would produce functionally equivalent validators.

### Input contract

The validator takes three inputs:

1. **`profile_id`** — whose constraint graph to check against.
2. **`suggestion`** — a structured object from the Chef agent with the following fields: `meal_name` (the dish title), `ingredients` (a list of ingredient entries with quantities and units), `preparation_summary` (brief natural-language description), and optional `nutritional_rough_estimate` (per-serving macros if the Chef can estimate them).
3. **`at_time`** — the moment for which the active constraint set should be computed. Defaults to now but is parameterized for testing and replay.

### The algorithm

The validator executes the following steps in order. Each step can only produce three possible outcomes: pass (continue to the next step), reject (return a failed result immediately), or disclosure (note the disclosure and continue).

**Step 1: Compute the active constraint set.** Call `queryActive(profile_id, at_time)` on the constraint graph API. Receive the structured active set.

**Step 2: Resolve the suggestion's ingredients through the Food Data Provider.** For each ingredient in the suggestion, call the Food Data Provider's lookup function. The provider returns a canonical ingredient entry with categories, allergens, nutritional properties, and a confidence tag (per ADR 0012). If the lookup triggers the AI-fallback path (unknown ingredient), the validator's behavior depends on the severity of the constraints being checked (Step 4 handles this).

**Step 3: For each constraint in the active set, check the suggestion against the constraint's type.** The check logic is type-specific:

- **`avoid` constraints**: iterate through the suggestion's resolved ingredients, check whether any match the constraint's target (literal match or categorical match through the Food Data Provider's category graph), and also check the constraint's `exceptions` list to see if any matching ingredient has been marked as a user-approved exception. If a match exists and is not excepted, this is a violation.
- **`require` constraints**: check whether the suggestion's ingredients satisfy the requirement (contains the required ingredient, or the required category, or meets the nutritional property threshold). If not, this is a violation. Require constraints are the hardest for the Chef to satisfy and produce the most regeneration loops in practice.
- **`limit` constraints**: sum the relevant nutritional property across the suggestion's ingredients (using the Food Data Provider's composition data), compare against the ceiling, and check the `window` setting. If the sum exceeds the ceiling for a hard limit, this is a violation. For a soft limit, this is a disclosure note.
- **`prefer` constraints**: check whether the suggestion aligns with the preference (includes the preferred target, uses the preferred technique, matches the preferred cuisine). Misalignment is a disclosure note, never a rejection. The soft-limit and prefer cases are the only constraint types that can produce disclosure notes without rejection.
- **`contextual` constraints**: contextual constraints rarely produce direct violations on their own — they mostly act as scope modifiers for other constraints, already handled in Step 1's active-set computation. A contextual constraint does produce a direct violation if it has a `state` with an `incompatible_with` field that the suggestion triggers (e.g. a "fasting" contextual state is incompatible with any non-empty meal during the fasting window — a rare but real case for Aisha's Ramadan fast).

**Step 4: Handle unknown-ingredient cases based on constraint severity.** When the Food Data Provider could not identify an ingredient and returned an AI-derived or unknown entry, the validator applies the severity-scoped logic from ADR 0012:

- **Inviolable constraints with unknown ingredients**: reject the suggestion immediately. Unknown ingredients in suggestions being checked against Inviolable constraints are never passed through the AI-fallback. This is the safety floor.
- **Medical or Religious & Cultural constraints with unknown ingredients**: trigger the AI-assisted categorization sub-call (per ADR 0012), receive a structured categorization tagged `low-confidence-ai`, and run the rule check against that categorization. If the check passes, add a disclosure note for the user ("I'm not 100% sure about ramps — if you know they bother you, swap them out") and continue. If the check fails, this is a violation.
- **Preference constraints with unknown ingredients**: trigger the AI-assisted categorization sub-call as above but *without* the disclosure note. Preferences are lower-stakes and don't justify interrupting the user.

**Step 5: Aggregate the results and return.** If any Step 3 or Step 4 check produced a violation, the validator returns `{passed: false, violations: [...], disclosure_notes: [...]}`. If no violations were produced, the validator returns `{passed: true, violations: [], disclosure_notes: [...]}` — including any disclosure notes that accumulated from soft limits, prefer misalignments, and AI-fallback medical cases.

### What the validator does not do

The validator is a deterministic rule checker, not a reasoning engine. It does *not*:

- Reason about whether a violation might be "acceptable in this case" — that decision belongs to the conflict resolution algorithm (Section 8), not the validator.
- Attempt to modify the suggestion to fix violations — modifications are the Chef's job during regeneration.
- Call the Chef, the Curator, the Planner, or any other agent. The validator is a pure function from (profile graph, suggestion, time) → (passed, violations, disclosures).
- Make network calls *other than* the Food Data Provider lookups and the severity-scoped AI-fallback sub-call. In particular, the validator never calls the main LLM providers for reasoning purposes. If the validator starts needing a "smart" decision, that's a signal we're conflating two concerns and the architecture has a bug.

### The regeneration loop

The orchestrator (not the validator) runs the regeneration loop specified in ADR 0010: on a failed validation, the orchestrator re-invokes the Chef with the violation reasons added to its prompt context, gets a new suggestion, and re-runs the validator. The loop is bounded at three attempts. After three failed attempts, the orchestrator surfaces the honest fallback message to the user ("I couldn't find a meal that works tonight — could you tell me more about what you're in the mood for, or relax one of your constraints for tonight?").

The validator itself is stateless between invocations — it has no memory of previous attempts. The orchestrator maintains the retry count and the accumulated rejection reasons.

## 8. Conflict resolution algorithm

> ADR 0009 establishes the four-step sequence for resolving conflicts: try-to-satisfy-all → silently-relax-preferences → surface-conflict-to-user → never-relax-inviolables. This section translates that policy into the runnable algorithm the orchestrator implements. Conflict resolution is *not* the validator's job — the validator just detects violations. Conflict resolution is the orchestrator's job when the validator has repeatedly failed and the engine needs to decide whether the failure represents a "no possible meal" case, a "user must choose" case, or a "give up" case.

### When the conflict resolution algorithm runs

The algorithm runs when the orchestrator has exhausted its regeneration attempts (three failed validations in a row on the same user request) and the Chef cannot produce a valid suggestion. At this point, the orchestrator does not immediately surface failure to the user — it first runs the conflict resolution algorithm to check whether relaxing constraints might unstick the situation.

### The four-step sequence

**Step 1: Analyze the failure pattern.** Examine the three rejected suggestions from the regeneration loop and determine which constraints caused the rejections. If all three rejections were caused by the same constraint, the conflict is clear and narrow. If the rejections were caused by different constraints conflicting with each other, the conflict is structural — the constraints cannot be simultaneously satisfied in any meal the Chef has found.

**Step 2: Attempt silent preference relaxation.** Identify any active preference-tier constraints in the relevant active set. Try re-invoking the Chef one more time with those preferences marked as "relaxable" in its prompt context. If the Chef can produce a suggestion that satisfies all higher-tier constraints by relaxing one or more preferences, run the validator on it. If it passes, return the suggestion to the user *without* a disclosure note (preferences silently relax). If it fails too, continue to Step 3.

**Step 3: Surface the conflict to the user.** Identify the higher-tier constraints (Medical, Religious & Cultural) that are jointly blocking satisfaction. Construct a user-facing conflict message using the calm-precise-warm voice. The message names the conflict honestly without using words like "violation" or "override" — e.g. "I couldn't find a meal tonight that works for both your IBS and your low-sodium needs. Here are a few options, each of which works for one but relaxes the other. You decide." Present three options to the user, each satisfying a different subset of the conflicting constraints. The user picks one (or picks "none of these — I'll figure it out myself," which is always a valid option).

**Step 4: Record the user's choice and continue.** The user's selection is recorded as a temporary relaxation via `resolveConflict` on the graph API. The relaxation is scoped to *this session only* — it does not permanently modify the constraint graph. The Chef is re-invoked with the relaxation in context, produces a suggestion, which is validated (and which now passes because the previously-conflicting constraint has been relaxed for this session), and returns to the user. The provenance of the suggestion carries metadata noting which constraint was relaxed at user choice, so the engine can explain itself if asked.

### Inviolable constraints are never offered for relaxation

The conflict resolution algorithm never includes Inviolable constraints in the "pick one to relax" options surfaced in Step 3. If an Inviolable constraint is part of the conflict and cannot be satisfied simultaneously with another active constraint, the algorithm skips Step 3 entirely and proceeds to the "no meal found" fallback. The user is told honestly: "I couldn't find a meal tonight that works with [the non-Inviolable constraint] without violating [the Inviolable constraint]. Could you tell me more about what you're in the mood for?"

This is the mechanical expression of the principle that Inviolable constraints are the safety floor. The algorithm physically cannot produce a suggestion that violates them, and it physically cannot ask the user to accept one.

### Edge cases the algorithm handles explicitly

- **A contextual constraint blocking all options**: if the conflict turns out to be driven by a user-reported contextual state (e.g. "my gut is off today" combined with dietary restrictions too narrow to satisfy), the algorithm may suggest *relaxing or clarifying the contextual constraint* as one of the Step 3 options. "Is your gut really still off, or would you be okay with something mildly spiced tonight?" This is a specialized option that only surfaces for contextual-driven conflicts.

- **No active preferences to relax**: if Step 2 finds no preference-tier constraints to relax (a sparse profile that only has Medical and Religious & Cultural constraints), Step 2 returns immediately and the algorithm proceeds to Step 3.

- **Household (v2) conflicts across profiles**: when the Planner is composing a household meal and two profiles' constraints conflict (Sukhi's IBS triggers vs Beeji's low-fat needs), the algorithm runs the same four steps, but Step 3's conflict message attributes each conflicting constraint to its source profile ("Sukhi's gut needs and Beeji's heart needs pull in different directions tonight — here are three options, each focusing on one"). Profile attribution in the conflict UX is a v2 extension to the v1 single-profile case.

- **The user repeatedly picks the same relaxation**: if a user picks "relax my low-sodium tonight" three nights in a row, the engine does *not* silently remove the low-sodium constraint. It continues to apply the constraint by default and continues to surface the conflict when it arises. The user can permanently change or remove the constraint through the Settings UI or by telling the Curator explicitly — the session-scoped relaxation never graduates to permanent change silently.

### What the algorithm does not try to do

- It does not attempt to "optimize" the user's diet or suggest healthier alternatives. Conflict resolution is about making the engine *responsive* to contradictions, not about making it *pushy*.
- It does not modify the constraint graph permanently. Every relaxation is session-scoped. Permanent changes only happen through explicit Curator or Settings UI writes.
- It does not ask the user multiple rounds of clarifying questions. The algorithm surfaces a conflict once, gets a decision, and moves on. If the user's choice leads to another failure, the algorithm reruns from Step 1 rather than continuing to pester.

## 9. Provenance and audit

> Every constraint in the graph carries a provenance record — metadata about how it came to exist. Provenance is load-bearing for the conflict resolution UX (per ADR 0009), the alpha feedback review (per PRD § 5), the validator's confidence decisions (per ADR 0010), and the user's ability to ask "why is this on my list?" without being lied to. Provenance is never shown unprompted, but it is always available. A constraint without provenance is a constraint the engine cannot honestly explain, and the engine is not allowed to produce those.

### The provenance record

Every constraint has a `provenance` field containing these sub-fields:

- **`source`** — one of a fixed set of values: `user-direct` (user said this explicitly), `curator-inferred` (the Curator agent deduced this from surrounding context in user speech), `ai-derived-low-confidence` (the constraint was created as a consequence of ADR 0012's AI-fallback on an unknown ingredient), `bundle-default` (the constraint was seeded from a curated profile template — a v3 feature), or `user-edited` (a previously-created constraint that the user has since explicitly modified).
- **`added_at`** — timestamp when the constraint entered the graph, in the user's timezone.
- **`added_context`** — structured metadata about the moment of creation: `{flow: "constraint_conversation", question_index: 3}` for constraints created during onboarding, `{flow: "free_text_update", triggering_message_excerpt: "..."}` for constraints created from later free-text input, `{flow: "rejection_feedback", rejected_suggestion_id: "..."}` for constraints implied by a user's rejection of a prior suggestion.
- **`original_phrasing`** — the verbatim text the user used when this constraint was created (if applicable). Stored for disclosure purposes: when the user asks "why is this on my list?" the engine can say "you told me on day 1 that you can't have onions or garlic" with the exact phrase they used.
- **`confidence`** — one of the five tags from ADR 0012's confidence model (`high-confidence-bundle`, `high-confidence-usda`, `high-confidence-off`, `low-confidence-ai`, `high-confidence-user-verified`). For constraints whose validity depends on Food Data Provider lookups (categorical avoids, limit constraints referencing nutritional properties), the confidence tag of the underlying ingredient data propagates to the constraint.
- **`modification_history`** — an append-only log of every change to the constraint since creation, with timestamp, source, and reason. If Sukhi adds "no onions" on day 1 and later updates it to "no alliums except leeks when I'm feeling good," the history contains both versions.

### Audit trail for conflict resolution

Every conflict resolution decision (Section 8's Step 4 user choices) is logged to the local event store with its own audit record, separate from constraint provenance. Audit records contain: the conflict that was surfaced, the options the user saw, the option they picked, and the resulting regenerated suggestion. During alpha, these audit records are reviewable by the founder on user request (with local-only event logs per PRD § 5) and are the primary signal for whether the conflict resolution algorithm is producing good outcomes.

### Disclosure semantics

When the user asks the engine to explain itself — "why did you suggest this meal?" or "why is this off-limits for me?" — the engine reads the provenance records of the relevant constraints and composes a plain-language explanation. The Curator agent owns this composition (it's a natural-language generation task), but the data it uses is structured and auditable. The explanation cites the user's original phrasing where available, the source of the constraint, and the date it was added. The explanation never invents or smooths over provenance data; if a constraint's provenance is missing fields or has low confidence, the explanation says so honestly.

### What provenance does not track

Provenance tracks how a constraint came to exist, not how it has been *used*. The engine does not record "this constraint caused 14 meal rejections last week" as part of the constraint record — that kind of usage telemetry is a separate concern (alpha event logging from PRD § 5), lives in a separate store, and is reviewable only by the user exporting their own logs. The separation is deliberate: provenance is about truth-telling, usage is about debugging, and conflating them would make both harder.

## 10. Schema versioning and migration

> v1 ships with schema version 1. The engine is designed from day one to support versioned schemas and forward migrations, even though v1 has nothing to migrate yet. This is the architectural readiness discipline (Canada-first / NA-ready, single-profile / multi-profile-ready, three-agents / six-agents-ready) applied to the constraint engine's schema. Without versioning and migration tooling in place from day one, the first schema change becomes a rebuild rather than an upgrade.

### Schema versioning

The constraint engine's schema has a single integer `schema_version` field stored at the graph level (not per-constraint). v1 ships with `schema_version = 1`. Every write operation checks the current schema version before proceeding; if the stored version is older than the running code's expected version, a migration runs before the write. If the stored version is *newer* than the running code's expected version (user downgraded the app), the engine refuses to read and asks the user to update — corrupting user data is worse than blocking it temporarily.

### Migration tooling

The migration tooling is a small module (`constraint_graph_migrator`) with a single entry point: `migrate(graph, from_version, to_version)`. The module contains a registry of migration functions keyed by `(from, to)` pairs; to migrate from an older version to the current version, the module composes a chain of single-step migrations. At v1 ship, the registry is empty — there are no migrations yet because there is only one schema version. The empty registry exists and is exercised by tests so that the *first* real migration (v1 → v2) is a new entry in an existing system rather than a new system.

Migrations are *additive only* by convention: a migration can add new fields, new types, new severity tiers, new scope dimensions, or new operations, but it cannot rename or remove existing fields without a deprecation period. Deprecation periods span at least two major versions. A field deprecated in v2 stays readable through v3 and is removed only in v4.

### What cannot change without a deliberate ADR

Some properties of the schema are *not* legitimately migrable without rewriting everything that depends on them, and the engine treats changes to these as triggering a new ADR before any implementation work:

- The four severity tiers (Inviolable, Medical, Religious & Cultural, Preference) are fixed. Adding a fifth tier changes the conflict resolution algorithm and requires a deliberate decision.
- The five constraint types (avoid, prefer, require, limit, contextual) are fixed for the same reason. A new type is a new ADR.
- The profile-as-first-class-entity commitment from ADR 0004 is fixed. Changing ownership semantics requires a new ADR.
- The stateless validator commitment from ADR 0010 is fixed. Adding state to the validator is architecturally consequential and requires reconsideration.

Schema migrations that *would* be legitimate without a new ADR include: adding a new field to an existing constraint type's payload (e.g. adding a `notes` field for user annotations), adding a new sub-field to provenance records, adding a new temporal scope shape, adding a new contextual state vocabulary entry. These are additive and bounded and should be expected to happen regularly.

## 11. Open questions

> The genuine unknowns this spec acknowledges rather than papering over. Every entry below is a real question that will need an answer during build or alpha. Not pretending to have an answer is more useful than pretending to have one. When an answer is reached, the resolution moves to the appropriate doc and the entry is removed.

### Active-set computation

- **What is the exact query-cache invalidation strategy?** Section 5 says the cache is invalidated on constraint graph writes and contextual state changes, but the granularity is unspecified. Does a new `prefer` constraint invalidate caches for Chef queries that only care about `avoid`s? Probably not, but the invalidation predicate needs to be specified precisely in the implementation. *To be resolved in `data-model.md` or the initial implementation.*
- **How often should the active-set computation run when temporal scopes have time-of-day boundaries?** Aisha's Ramadan fasting window changes state at sunrise and sunset — does the engine proactively recompute at those boundaries, or does it compute lazily on the next query? Lazy is simpler but means the engine's "what's active right now" is briefly stale around boundaries. *To be measured in alpha.*

### Validator and Chef interaction

- **What exactly goes into the Chef's prompt context after a validation failure?** Section 7 says the orchestrator re-invokes the Chef with "the violation reasons added to its prompt context" but the exact format of those reasons is deferred to `agent-architecture.md`. Too much detail and the prompt becomes unwieldy; too little and the Chef makes the same mistake twice.
- **How should the Chef's rough nutritional estimates be reconciled with the Food Data Provider's canonical composition data?** When the Chef suggests a dish and estimates its sodium content in its output, and the Food Data Provider's sum-of-ingredients gives a different number, which one does the validator's `limit` check use? Probably the Food Data Provider (more authoritative) but the Chef's estimate should be logged for calibration. *To be resolved in `agent-architecture.md`.*

### Conflict resolution UX

- **What are the exact words the engine uses when surfacing a conflict to the user?** Section 8 says the message uses the calm-precise-warm voice and avoids words like "violation" or "override," but the specific copy is not locked. This needs to be hand-written, reviewed, and alpha-tested. *To be resolved during prompt and copy work in `agent-architecture.md` and iterated in alpha.*
- **How does the engine handle a user who keeps picking "none of these — I'll figure it out myself"?** The algorithm accepts this as a valid option, but repeated selection of it is a signal something is wrong. Does the engine surface a meta-question ("it seems like none of my suggestions have been working for you lately — can you tell me more about what you need?") after N consecutive abandonments? Probably yes, but the N and the wording need design. *To be resolved in alpha.*

### Provenance and confidence

- **When a constraint's confidence tag is `low-confidence-ai` and the user accepts a suggestion containing it without explicit confirmation, does the tag graduate to `high-confidence-user-verified`?** Implicit acceptance might be evidence the categorization was correct, or it might just be that the user didn't notice. Alpha will tell us. *To be measured in alpha.*
- **Can a constraint's provenance record ever be retroactively edited?** Example: a user says "actually, I added that onion constraint because my gut was bothering me that day, not because it's a permanent thing." Is this a new `modification_history` entry or a correction to the original record? Probably the former, but the UX matters. *To be resolved in v2 Settings UI design.*

### v2 readiness gaps

- **How do household queries compose profiles' active constraint sets?** Section 5 says the Planner calls `queryActive` with multiple profile IDs, but the exact composition semantics (union, weighted union, attribution-aware union) are deferred to v2. *To be resolved when the Planner agent is designed.*
- **What happens when a dependent profile is added to a household mid-planning?** If Sukhi is in the middle of planning a week's meals and adds Arjun as a dependent (he came home from university unexpectedly), do the in-flight meal plans get re-checked against his constraints? *To be resolved in v2 Planner design.*

### Deferred but parked

- **The possibility of a `warn` constraint type** was rejected in Section 3 with rationale, but if alpha data shows the validator's disclosure-note mechanism is insufficient for some user case, revisiting is allowed via a new ADR.
- **User-defined severity tiers** beyond the four from ADR 0009 — e.g., a personal "really strict preference" tier that sits between Preference and Religious & Cultural — was not considered and is not planned. If alpha data shows it's needed, revisiting is allowed via a new ADR.
- **Constraint templates** (curated starter profiles for common conditions like T2 diabetes, kidney disease, pregnancy — professionally authored, user-adoptable) are mentioned in ADR 0004's alternatives and parked for v3 consideration. The provenance `source` value `bundle-default` exists in v1 to make the future addition architecturally clean.

## 12. Cross-references

### What this document references

- `vision.md` — for the trust posture, the five pillars, the tone
- `PRD.md` — for Sukhi, the secondary personas, the v1 user journey, the feature scope
- `technical-architecture.md` — for the system structure and the Food Data Provider's position
- ADR 0002 — cultural fluency as the fifth pillar, which gives Religious & Cultural its co-equal standing with Medical
- ADR 0004 — multi-profile households, profile-as-first-class, the v2/v3 extension path
- ADR 0006 — multi-provider routing, which the AI-fallback sub-calls use
- ADR 0009 — the four-tier severity model and the conflict resolution policy, translated mechanically in Sections 4 and 8
- ADR 0010 — the post-generation validator, translated mechanically in Section 7
- ADR 0011 — the optional backend model that affects how the validator's calls are routed (direct-from-device in signed-out mode, via Cloud Function proxy in signed-in mode)
- ADR 0012 — the food data sources and the three-layer architecture, which the validator depends on for deterministic ground truth

### What this document defers to deeper-dive docs

- **`data-model.md`** — the physical translation: Room entities, column types, indices, the SQLite storage layer for constraints, profiles, provenance records, and the active-set cache
- **`agent-architecture.md`** — how the Curator parses natural language into constraint types, how the Chef reads the active set and generates suggestions, the exact prompt structures for each agent, the rejection-reason format in regeneration loops, the composition-rules for v2 household queries
- **`local-first-sync.md`** — how the constraint graph is encrypted and synced across devices, including the provenance records and the modification history
- **`testing-strategy.md`** — the "hard cases" test suite that exercises this spec end-to-end, including the worked examples from Sukhi's life and the secondary personas
- **`prompts/curator.md`** and **`prompts/chef.md`** — the system prompts that translate this spec's logical contracts into agent behavior

### What this document does *not* defer (decisions made here)

- The five constraint types and their payload fields
- The mechanical rules for each severity tier (Section 4)
- The five scope dimensions and their composition (Section 5)
- The eleven-operation graph API surface (Section 6)
- The validator's step-by-step algorithm (Section 7)
- The four-step conflict resolution algorithm (Section 8)
- The provenance record structure (Section 9)
- The schema versioning and migration strategy (Section 10)

### How the agent should use this doc

When the coding agent builds the constraint engine, it should treat Sections 3-8 as the authoritative specification for the engine's behavior. Section 3 (type taxonomy) tells the agent what data shapes exist. Section 4 (severity tiers) tells it how to handle each tier's behavior. Sections 5-6 tell it what operations the engine supports and how scope is evaluated. Section 7 is the validator's algorithm, which should be implemented as a pure function with full test coverage — this is the single most important component of the engine to get right, and the tests should exercise every branch of the algorithm against the hard-cases suite. Section 8 is the conflict resolution algorithm, which should be implemented inside the orchestrator (not the validator) and should be extensively tested with multi-constraint scenarios from Sukhi's life and the secondary personas.

When the agent encounters a question this document does not answer, the discipline from PRD § 9 applies: check the vision, check the relevant ADR, check the architecture doc, ask the founder before guessing. The constraint engine is the heart of pillar 1 — silent guessing here compounds into every downstream agent, and the cost of asking is always smaller than the cost of inheriting wrong assumptions.

---

*End of `constraint-engine-spec.md` v1 (initial draft). Next revision will incorporate any semantics that surface during the writing of `agent-architecture.md` and `data-model.md`, and any learnings from the v1 alpha build. The vision and the ADRs are more stable than this document; the spec is expected to learn from contact with implementation and real users.*
