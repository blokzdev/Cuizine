# 0009 — Constraint conflict resolution policy

**Status:** Accepted
**Date:** 2026-04-11

## Context

The constraint engine holds layered constraints from multiple sources — medical conditions, religious observance, cultural traditions, personal preferences, temporary contextual states, and household/cooking-for context. ADR 0002 established cultural fluency as the fifth pillar; ADR 0004 established multi-profile household reasoning across overlapping constraint sets. The PRD's primary persona Sukhi has T2 diabetes, IBS-M, a Tuesday fast, traditional Punjabi food preferences, and four household members with their own dietary realities — all of which the engine must navigate every time the Chef agent generates a meal suggestion.

The unavoidable consequence of layered constraints is that they will sometimes contradict each other. A diabetic constraint says "more whole grains and legumes." An IBS constraint says "many of those are high-FODMAP triggers." A religious fast says "nothing today between sunrise and sunset." A medical condition says "small frequent meals are better than infrequent large ones." A cultural preference says "this holiday is incomplete without a sweet dish." These conflicts are not edge cases — they are *the central case* the engine exists to handle, and the user lives in them every day.

The PRD raised the conflict resolution question explicitly in Section 8 as needing a policy. Leaving it undefined would force the agent to invent one inconsistently, with serious downstream consequences: silent overrides of medical constraints, baffling user-visible behavior, and erosion of trust.

## Decision

Cuizine resolves constraint conflicts through a **severity-tiered model with explicit user-visible escalation**, never silent overruling. The principle is: **Cuizine never silently violates a constraint to satisfy another. When constraints genuinely conflict, the user is shown the conflict and given control over how to resolve it.**

### The four severity tiers

Constraints in the engine are tagged with one of four severity levels:

1. **Inviolable.** Constraints whose violation would cause immediate harm or be ethically intolerable. Examples: a peanut allergy that triggers anaphylaxis, a religious prohibition the user has marked as non-negotiable, a medication-food interaction the user has explicitly flagged as dangerous. These constraints are *never* overridden, by anything, ever. A meal suggestion that would violate an inviolable constraint is regenerated; if regeneration fails repeatedly, the user is told no suggestion could be found rather than being shown a violating one. This is the safety floor — see ADR 0010 for the post-generation validation mechanism that enforces it.

2. **Medical.** Health-related constraints the user has marked as medical (diabetes management, IBS triggers, kidney-disease limits, etc.). These are taken seriously and never silently overridden, but they can be temporarily relaxed by *explicit user choice* in a specific moment ("I know this isn't ideal but I want it anyway tonight") with the engine recording that the override happened so future suggestions don't repeat the violation by inference.

3. **Religious & cultural.** Constraints rooted in religious observance, cultural tradition, or strongly-held identity. Treated as co-equal with medical constraints in severity — never silently overridden, can be explicitly relaxed by the user. This co-equality is important and deliberate: the engine does not treat medical needs as inherently more important than religious needs. They are different *kinds* of important, and the user is the one who decides which to relax in a given moment.

4. **Preference.** Personal preferences, dislikes, dietary leanings (vegetarian-by-choice rather than by allergy), aesthetic considerations. These can be overridden by the engine's reasoning when a higher-tier constraint would otherwise be violated, but only after the engine has tried to find a satisfying option that respects the preference too. Preference overrides are silent — the user is not interrupted to ask permission to ignore a mild dislike.

### How conflicts are resolved

When the engine encounters a conflict during meal suggestion, it follows this sequence:

1. **Try to find a meal that satisfies all constraints across all tiers.** Most of the time, this works. The Chef agent's job is largely to find creative solutions that thread the needle.

2. **If no such meal exists, attempt to satisfy all medical, religious, and inviolable constraints by relaxing preference-tier constraints silently.** This is the only silent override the engine performs. The user sees a meal that doesn't perfectly match their preferences but respects their health and beliefs.

3. **If even relaxing preferences cannot produce a meal, the engine surfaces the conflict to the user explicitly.** This is the "no meal satisfies both — here are your options" UX. The conflict is named honestly: "I couldn't find a meal that works for both your IBS and your low-sodium diet tonight. Here are three options, each of which respects one constraint but relaxes another. You decide." The user picks one, and the engine records *which* constraint they chose to relax in *this specific moment* — not as a permanent change to the constraint graph, but as a contextual signal that informs future suggestions.

4. **Inviolable constraints are never part of step 3.** They cannot be relaxed via the conflict UX. If the inviolable constraint is the one blocking all options, the engine tells the user no meal could be found rather than offering them a violation.

### Provenance and explanation

Every suggestion the engine generates carries internal metadata about *which constraints were considered*, *which were satisfied*, *which were relaxed* (if any), and *at what tier*. This metadata is not shown to the user in normal use (which would violate the calm-precise-warm tone and feel like infrastructure exposure), but it is available on demand if the user asks "why this dish?" or "why not the dish I expected?" The Chef agent can explain itself in plain language by reading this metadata when asked.

This is also the metadata that makes ADR 0010's post-generation validation possible — the validator checks the suggestion against the constraint graph and uses the same metadata to decide whether the suggestion should be shown, regenerated, or surfaced as a conflict.

### User-visible language

The engine never uses words like "violation" or "override" with the user. The internal model is severity-tiered conflict resolution; the user-visible language is the calm-precise-warm voice of vision.md. "I couldn't find a meal that works for both X and Y tonight. Here's what I can offer." Not: "This suggestion overrides your low-sodium constraint." The internal model is precise; the user experience is human.

## Consequences

**Positive.** The engine has a deterministic, principled answer to the central question of the product — what to do when the user's life makes consistent eating impossible. The user is never silently betrayed; the user is never blocked unnecessarily. Medical and religious constraints are treated with co-equal seriousness, which is the right ethical posture for a product serving newly-diagnosed users in culturally specific households. The four-tier model is simple enough to communicate to the agent in the constraint engine spec and complex enough to handle real layered cases like Sukhi's. Inviolable constraints provide a hard floor that no reasoning chain can erode.

**Negative.** The four-tier model requires careful UX design for the conflict surfacing case (step 3 above) — getting this wrong would either frighten users with too-frequent conflict alerts or train them to ignore the alerts. The "user picks which constraint to relax in this moment" UX is genuinely hard to make feel calm rather than burdensome. Alpha will need to test it carefully and the wording will need iteration.

The provenance metadata adds non-trivial schema complexity to the constraint engine, but it's load-bearing for both the conflict resolution UX and ADR 0010's post-generation validation, so the cost is justified.

The distinction between "medical" and "inviolable" requires the user to mark some constraints as inviolable explicitly, which is an onboarding question the constraint conversation must handle gracefully. The default for medical constraints is medical-tier, not inviolable; users can escalate to inviolable for specific items (e.g., an allergy) when they want to.

**Neutral.** Religious-and-medical co-equality may surprise some users who expect medical advice to "win." The PRD's calm-precise-warm tone supports framing this as honoring the user's whole life, not relativizing their health.

## Alternatives considered

**Strict severity hierarchy (medical > religious > preference).** Rejected. Treats medical needs as inherently more important than religious or cultural needs, which is the wrong ethical posture for a product serving observant users. Also produces silent religious overrides, which would catastrophically erode trust with the very users pillar 5 was built to honor.

**User-configurable severity hierarchy.** Considered and rejected for v1 as too much cognitive load for the persona. May return as a v3 power-user feature if alpha data shows users actively want it. For v1, the four-tier model with co-equal medical/religious is the default and is not configurable.

**Always surface every conflict to the user.** Rejected. Would interrupt the user constantly, defeat the calm-precise-warm tone, and turn Cuizine into a tool that asks more than it answers. Silent preference relaxation is the right default; only higher-tier conflicts deserve the user's attention.

**Silent override of medical constraints in favor of religious ones (or vice versa).** Rejected on ethical grounds and on trust grounds. Either direction would be a betrayal of users in the other camp. Co-equal severity with explicit user-visible conflict resolution is the only honest answer.

**Engine generates and shows the violating suggestion with a warning label.** Rejected. Showing a user a suggestion that violates their diabetes constraint, even with a warning, normalizes the violation and trains the user to mistrust suggestions. Better to regenerate or to honestly say no suggestion was found.

## Related

- See `vision.md` § Trust posture, § Tone & personality
- Pairs with 0010 (post-generation constraint validation enforces the policy at the suggestion-output layer)
- Pairs with 0002 (cultural fluency requires religious-and-cultural constraints to be co-equal with medical, not subordinate)
- Pairs with 0004 (household planning runs the same conflict resolution across multiple profiles' constraints simultaneously)
- The forthcoming `constraint-engine-spec.md` must define the four severity tiers, the inviolable-vs-medical distinction, the conflict resolution sequence, and the provenance metadata schema explicitly
- The forthcoming `agent-architecture.md` must define the Chef agent's behavior when it cannot satisfy all constraints, including the regenerate-then-surface fallback path
