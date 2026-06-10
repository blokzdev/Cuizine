# 0006 — Multi-provider per-agent routing with automatic model escalation

**Status:** Accepted
**Date:** 2026-04-11

## Context

Cuizine is built around a multi-agent architecture (Curator, Chef, Pantry, Planner, Sourcing, Observer — full topology in the forthcoming `agent-architecture.md`). Each agent has genuinely different capability requirements: the Curator and Planner need strong instruction-following and careful multi-constraint reasoning; the Chef needs broad world knowledge plus cultural fluency for grounded creative generation; the Pantry agent does mostly fuzzy matching and disambiguation that doesn't need a frontier model at all; the Sourcing agent is mostly tool-calling against Instacart with structured output requirements; the Observer (in v3) will need large context windows for long-term trend analysis.

Picking a single LLM provider for all agents would either over-pay for the lighter agents (using a frontier model where a cheap one would do) or under-deliver on the heavy agents (using a cheap model where reasoning quality matters). It would also create vendor concentration risk: an outage, deprecation, or pricing change at the chosen provider would degrade or break the entire product.

Separately, there's a user-facing question: should users be exposed to model selection or credit-based per-action upgrades? This was considered alongside the routing question because both decisions concern *where the routing intelligence lives* — server-side (Cuizine decides) or user-side (the human decides).

## Decision

**Cuizine implements per-agent provider routing through a `ModelProvider` interface.** Each agent declares its capability requirements (instruction-following strength, world knowledge depth, context window size, tool-calling reliability, cost tier) in its config. The orchestrator routes each agent's calls to the provider-and-model that satisfies those requirements at the lowest viable cost.

**Three providers are supported from v1:** Anthropic (Claude family), Google (Gemini family), and OpenAI (GPT family). Each provider is implemented as a thin adapter behind the `ModelProvider` interface. Default routing in v1:

- **Curator** → Claude (strong instruction-following, careful reasoning, conflict resolution)
- **Chef** → Claude or Gemini (creative generation grounded in cultural knowledge; either works, exact split refined by eval)
- **Planner** → Claude (multi-constraint reasoning across a household and a week)
- **Pantry** → Gemini Flash or Claude Haiku (cheap fuzzy matching; cost dominates over quality)
- **Sourcing** → whichever provider gives the most reliable structured tool-calling against Instacart's API shape (likely OpenAI or Claude)
- **Observer** (v3) → Gemini (largest context windows for long history analysis)

These defaults are starting points, not commitments — they will be refined based on eval results in v1 alpha and may shift between providers as the providers themselves evolve.

**Automatic model escalation based on task stakes.** Within an agent, the orchestrator can escalate to a stronger model when the task warrants it. A "what's for dinner tonight" Chef request might run on a fast/cheap model; a "plan my entire week around my new diabetes diagnosis" Chef request escalates to a stronger model because the stakes are higher. The user never sees this — escalation is silent, automatic, and based on task signals (stakes, complexity, novelty).

**No user-facing model selector. No credit economy. No per-action upgrade prompts. No model names exposed in the UI.** This is a *forever* commitment, not a v1 deferral. The user pays a tier subscription (see ADR 0007) and Cuizine takes care of the routing, optimization, and quality. Cost optimization is Cuizine's problem, not the user's. Revisiting this commitment requires a deliberate ADR superseding this one.

**Provider redundancy as resilience.** Because three providers are supported through one interface, an outage or pricing change at any one provider degrades Cuizine to using the others rather than breaking it. The orchestrator can fall back across providers per-agent without code changes.

## Consequences

**Positive.** Cost-quality optimization happens per-agent, which is meaningfully cheaper at scale than single-provider routing. Provider redundancy protects Cuizine from any single vendor's outages, pricing changes, or terms changes. The user is shielded from all of this complexity, which preserves the calm-precise-warm tone (no token counters, no model anxiety, no per-action decisions). Tier differentiation (ADR 0007) becomes implicit in routing behavior — paid tiers can run heavier models more often without exposing that to users.

**Negative.** Three provider adapters must be built and maintained from v1 instead of one. Eval discipline is harder because we have to evaluate each agent against multiple providers, not just one. The agent topology and prompts must be designed to work across providers (avoiding provider-specific quirks where possible). Slightly more orchestration complexity than a single-provider design.

**Neutral.** Anthropic Claude is the default for the heavy reasoning agents (Curator, Chef, Planner) at v1 launch. This may shift as eval data accumulates. The decision to ship with Claude as the heavy-reasoning default is empirical and revisable, not architectural and permanent.

## Alternatives considered

**Single provider (e.g., all-Claude or all-Gemini).** Rejected because no single provider is best across all agent types, vendor concentration risk is real for a product whose users depend on it daily, and the cost difference between using a frontier model for fuzzy matching vs using a cheap model is enormous at scale.

**User-facing model selector with credit economy.** Considered seriously and rejected on multiple grounds. Cognitive tax on a user whose entire reason for using Cuizine is to escape complexity. Fragmented evals (we can't tell whether a bad output is a bug or a model choice). Gambling-shaped interactions in a medical-adjacent context. Misaligned with the calm-precise-warm tone. Misaligned with the trust posture (users shouldn't have to think about infrastructure). The credit-economy framing also makes Cuizine feel like infrastructure rather than a service, which undermines the chef metaphor we've committed to.

**BYOK (Bring Your Own Key) from v1.** Rejected for v1 and v2; deferred to v3 (see ADR 0008) as a power-user option once the default-experience product is proven. BYOK fragments the user base, complicates support, undermines the subscription model, and attracts the wrong audience for Cuizine's first users.

**Manual per-agent provider configuration as a power-user setting.** Rejected for the same reasons as the user-facing model selector. Power users are not Cuizine's target audience and the cost of supporting their preferences exceeds the benefit.

## Related

- See `vision.md` § Tone & personality, § Trust posture
- Pairs with 0007 (tier-based monetization absorbs the cost-quality tradeoff that user-facing selectors would expose)
- Pairs with 0008 (BYOK explicitly deferred as a separate decision)
- Future `agent-architecture.md` must define the `ModelProvider` interface and per-agent capability requirements explicitly
