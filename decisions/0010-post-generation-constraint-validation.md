# 0010 — Post-generation constraint validation

**Status:** Accepted
**Date:** 2026-04-11

## Context

The Chef agent generates meal suggestions by calling an LLM with the user's constraint graph and cooking context as input. LLMs are not reliable rule-followers — even with careful prompting, even with a strong frontier model, even with the constraint graph spelled out clearly in the prompt, the model will occasionally generate a suggestion that violates a constraint. A diabetic user might be offered a dessert that's too high-sugar. A halal observant user might be offered a dish containing pork. An IBS sufferer might be offered something with onion and garlic when those are explicitly avoided. These failures are statistically rare but inevitable given how LLMs work.

The PRD's quality bar (Section 7) declares that **constraint violations on medical or religious constraints are severity-zero incidents** — equivalent to security incidents, requiring immediate response. ADR 0009 establishes the conflict resolution policy: the engine never silently violates a constraint to satisfy another. But neither of these prevents the simpler, dumber failure: the LLM just gets it wrong and generates a violating suggestion that nothing checks before it reaches the user.

The persona makes the stakes concrete. Sukhi has uninstalled two diabetes apps already. If Cuizine offers her a sugary lassi recipe in week one because the Chef agent had a hallucination, she will uninstall it before her daughter has a chance to ask how it's going. There is no second chance with this user.

The fix is conceptually simple and architecturally non-negotiable: **every Chef suggestion must be validated against the user's constraint graph after generation, before being shown to the user.** This is the safety net that turns "we hope the LLM gets it right" into "we know the LLM got it right because we checked." Without this layer, every other commitment in the project — pillar 1, the trust posture, ADR 0009's resolution policy, the alpha exit criteria — is undermined by the next hallucination.

## Decision

**Cuizine implements a post-generation validation layer that runs on every Chef agent output before the suggestion is shown to the user.** The validator is a deterministic, non-LLM check that compares the generated suggestion against the user's active constraint graph and rejects any suggestion that violates an inviolable, medical, or religious constraint. The validator runs in v1 and continues running in v2 and v3 — it is not optional, not configurable, not skippable.

### The validation flow

1. The Chef agent generates a suggestion (a meal name plus ingredient list plus brief preparation summary).
2. The validator parses the ingredient list and the dish description into structured form. The validator may use a small, cheap LLM call for structured extraction from natural language (this is a *parsing* task, not a *reasoning* task — different and much more reliable).
3. The validator checks each parsed ingredient and characteristic against the user's active constraints, walking down by severity tier:
   - **Inviolable constraints** — any violation here causes immediate rejection.
   - **Medical constraints** — any violation here causes immediate rejection.
   - **Religious & cultural constraints** — any violation here causes immediate rejection.
   - **Preference constraints** — preference violations are noted but do not cause rejection (per ADR 0009's silent-relaxation rule).
4. **If the suggestion passes all higher-tier checks, it is shown to the user.** The validator's metadata is stored alongside the suggestion for provenance and explanation purposes (per ADR 0009).
5. **If the suggestion fails any higher-tier check, the suggestion is rejected and the Chef agent is asked to regenerate**, with the rejection reason added to the prompt context ("the previous suggestion contained X which violates the user's Y constraint — try again, avoiding X"). The user never sees the rejected suggestion.
6. **Regeneration is bounded.** After three failed regeneration attempts on the same request, the system surfaces an honest message to the user ("I couldn't find a meal that works tonight — could you tell me more about what you're in the mood for, or relax one of your constraints for tonight?") rather than continuing to retry indefinitely. This is the fallback path ADR 0009 references for the case where conflict resolution itself fails.
7. **Severity-zero logging.** Every rejected suggestion is logged locally with full metadata — the prompt, the rejected output, the violated constraint, the regeneration outcome. During alpha, the founder reviews these logs (when alpha users export them) to identify systematic Chef failures and improve the prompts. In v2, the same logging informs ongoing prompt and routing tuning.

### What the validator is and is not

The validator **is** a deterministic rule-checker that implements ADR 0009's policy at the output layer. It is the safety net that makes the conflict resolution policy real rather than aspirational.

The validator **is not** another layer of LLM reasoning. It does not "think about" whether a suggestion is appropriate. It does not weigh tradeoffs. It does not exercise judgment. It compares ingredients and characteristics against rules and produces a yes/no answer per constraint. The reason this matters: an LLM-based validator could itself hallucinate a pass when the suggestion actually violates a constraint, which would defeat the whole point.

The structured extraction step (parsing the suggestion into ingredients and characteristics) *can* use an LLM, because parsing natural language to structured data is a task LLMs are reliably good at. The actual validation is then deterministic.

### Constraint engine implications

The constraint graph schema must support deterministic validation. This means constraints are stored not just as natural-language descriptions but as structured rules the validator can mechanically check. The constraint conversation (per the PRD) parses Sukhi's natural-language input into structured constraints; the validator then operates on those structured constraints, not on the original phrasing. This is one of the load-bearing reasons the constraint engine is structured rather than vector-stored.

Some constraints will be hard to express deterministically — "avoid ingredients that have given me trouble in the past" is a soft pattern, not a rule. These are handled at the medical/religious tier with explicit ingredient lists rather than abstract rules; the soft-pattern version is handled by the Chef agent's prompt context, not by the validator. The constraint engine spec will need to be explicit about which constraint types are validator-checkable and which are prompt-only.

### Performance implications

The validator runs on every Chef call, which adds latency. The expected cost is small (a fast structured-extraction LLM call plus deterministic rule checks, on the order of 200-500ms) but it must be measured. The PRD's response-time bar (sub-5-second for v1) accounts for this. If validation latency is a problem in alpha, the optimization is to make the structured extraction call cheaper or to cache parsed ingredients across regenerations within the same request — not to skip validation.

## Consequences

**Positive.** Constraint violations on medical and religious constraints become preventable rather than aspirational. The severity-zero quality bar from the PRD becomes operationally enforceable. ADR 0009's conflict resolution policy gets a real teeth-having implementation. Alpha users who trust Cuizine with their diabetes or their halal observance are not betrayed by an LLM hallucination. Sukhi does not get offered a sugary lassi in week one. The system is honest about its limits — when the Chef can't produce a valid suggestion after retries, it says so rather than degrading silently.

**Negative.** Every Chef call now has additional latency (validation step) and additional inference cost (the structured extraction sub-call). This is a real cost that adds to per-meal-suggestion compute and slightly slows the user experience. We accept this cost as the price of trust. Regeneration loops can stretch the response time when the Chef gets it wrong on the first try. The bounded retry limit (three attempts) is a guess that may need tuning in alpha — too few and users get "no suggestion found" too often, too many and the latency on hard cases becomes painful.

The validator's deterministic nature requires the constraint engine schema to support deterministic checking, which constrains what kinds of constraints can be expressed. Soft patterns and fuzzy preferences must be handled at the prompt layer rather than the validation layer. This is a real design constraint and `constraint-engine-spec.md` will need to address it explicitly.

The structured extraction sub-call is a new failure mode. If the parser misses an ingredient, the validator passes a violating suggestion through. Mitigation: the parser is conservative (when in doubt, surface for validation), and the alpha logs are reviewed for parser misses as severity-one bugs.

**Neutral.** The validator becomes a load-bearing piece of infrastructure that needs its own tests, its own evals, and its own monitoring. The testing strategy doc must give it first-class treatment.

## Alternatives considered

**Trust the Chef agent's prompting and skip validation.** Rejected. LLMs are not reliable rule-followers no matter how carefully prompted. The frequency of failures is low but non-zero, and at the scale of "every meal Sukhi cooks for the next year" the absolute count of violations becomes meaningful. More importantly, even one violation in the wrong moment is enough to lose the user permanently. The math does not justify the risk.

**LLM-based validation (a second LLM call that judges whether the first suggestion is valid).** Rejected. An LLM validator can hallucinate just as easily as the LLM generator. Two unreliable systems checking each other is not more reliable than one unreliable system; it is approximately as reliable, with double the cost. Deterministic rule checking is the only path to a guarantee.

**Validation only for severity-flagged constraints, not for all constraints.** Considered. The benefit would be lower latency on the common case. The cost is that the validator's logic becomes conditional and harder to reason about, and edge cases (a user who hasn't formally tagged a religious constraint as "religious" tier) get handled inconsistently. Rejected in favor of always-validate-against-the-full-graph for simplicity and safety.

**Validation as an opt-in user setting.** Rejected. Validation is a safety-critical layer; users should not have to know it exists, let alone configure it. It is on for everyone, always.

**Skip regeneration on failure and just show the user the error.** Rejected as user-hostile. Regeneration is the right default; only after multiple failures does the user see a fallback message, and even that message is framed warmly and helpfully rather than as an error.

**Cap regenerations at one or two attempts instead of three.** Considered. Three is the current best-guess balance between latency on hard cases and quality on tractable ones. Will be tuned during alpha based on real data — this is a parameter, not a principle.

## Related

- See `vision.md` § Trust posture
- See `PRD.md` § 7 (Acceptance Criteria) — the severity-zero quality bar this enforces
- Pairs with 0009 (constraint conflict resolution policy — this ADR is the output-layer enforcement of that policy)
- Pairs with 0006 (multi-provider routing — the structured extraction call may be a separate cheap-model call routed independently from the main Chef call)
- The forthcoming `constraint-engine-spec.md` must define which constraint types are validator-checkable (deterministic) and which are prompt-only (soft patterns)
- The forthcoming `agent-architecture.md` must specify the Chef-validator-regenerate loop, the bounded retry limit, the fallback message UX, and the local logging format
- The forthcoming `testing-strategy.md` must give the validator first-class treatment with its own test suite separate from the Chef agent's evals
