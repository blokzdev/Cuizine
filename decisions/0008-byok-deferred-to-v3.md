# 0008 — BYOK (Bring Your Own Key) deferred to v3

**Status:** Accepted
**Date:** 2026-04-11

## Context

BYOK is a feature in some AI-powered apps where users provide their own API key for the LLM provider (Anthropic, OpenAI, Google, etc.), so that *they* pay for inference rather than the app paying. It's a popular pattern with technically-sophisticated users and is sometimes proposed as a way to reduce app-side costs and offer power users more control.

The question of whether Cuizine should support BYOK is related to but distinct from the multi-provider routing decision (ADR 0006). Multi-provider routing is an *architecture* decision about where the routing intelligence lives. BYOK is a *user-facing* decision about who supplies and pays for API access. They are often bundled in discussions but they're independent.

## Decision

**BYOK is not implemented in v1 or v2. It is deferred to v3** as a possible power-user option, alongside other v3 power-user features (configurable agent personas, possibly the deeper Observer-agent capabilities). This deferral is not a commitment to ship BYOK in v3 — it is an explicit decision *not* to ship it before then, with the question genuinely open for v3 reconsideration.

## Consequences

**Positive.** v1 and v2 ship a single, consistent product with a single, predictable model quality across all users. Evals are clean (no "which model is this user on?" confounders). Support burden stays bounded (no API-key-not-working tickets in alpha or v2 launch). The subscription-tier business model (ADR 0007) is not undermined by users routing around it. Cuizine optimizes for its actual target user — newly-diagnosed people who have never heard of an API key — rather than for technically-sophisticated power users who are not v1's audience.

**Negative.** Some technically-sophisticated users will want BYOK and won't get it in v1 or v2. This may cost a small number of potential users at the margin. We accept this cost because those users are not Cuizine's first audience.

**Neutral.** v3 reconsideration is genuine, not a soft commitment. By v3, the product will have proven itself, the user base will have stabilized, and the question of whether BYOK serves a real user need (rather than a vocal minority) can be answered with data instead of speculation.

## Alternatives considered

**Implement BYOK in v1.** Rejected. v1 is a closed alpha with hand-picked users and the goal is validating the AI agents under real constraints. Adding BYOK to v1 fragments the alpha (different users on different models), confounds eval signal, and adds engineering surface for a feature the alpha users will not need.

**Implement BYOK in v2 as an optional power-user toggle.** Rejected. v2's job is to validate the subscription-tier business model and onboard strangers to a calm, predictable product. Exposing BYOK at v2 launch would attract the wrong early adopters (technically-sophisticated rather than newly-diagnosed), fragment quality across users, complicate support, and undermine the tier model right when we're trying to prove it works.

**Commit to never implementing BYOK.** Rejected as too rigid. BYOK is the kind of feature that could become valuable later for specific use cases (e.g., enterprise/B2B customers, users in regions with regional API requirements, users who genuinely have sophisticated reasons to want their own key). Closing the door permanently is unnecessary; deferring with genuine reconsideration in v3 is the right amount of optionality.

**Implement BYOK only for Cuizine Family tier.** Considered and rejected. Tier-gated BYOK has the worst of both worlds: it doesn't reach the power users who want it (because they want it on the cheaper tier), and it complicates the Family tier with a feature that doesn't serve household-planning use cases.

## Related

- See `vision.md` § v3 — Global expansion + advanced features
- Distinct from but related to 0006 (multi-provider routing is architecture; BYOK is user-facing access)
- Distinct from but related to 0007 (BYOK would undermine the subscription tier model if shipped before the model is proven)
