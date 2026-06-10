# 0007 — Three-tier subscription with truly free local-first base tier

**Status:** Accepted
**Date:** 2026-04-11

## Context

Cuizine has two architecturally separable halves: the constraint engine and local-first data layer (which costs Cuizine essentially nothing to run because it lives entirely on the user's device), and the multi-agent inference layer (which costs real money per request because it calls cloud LLM providers). Most SaaS apps don't have this property — their cost structure is uniformly cloud-bound — so most monetization models bundle everything behind a single paywall or trial countdown.

The initial proposed monetization model was a 14-day full-feature trial followed by a paid subscription. On reflection, this was the wrong shape for Cuizine specifically: it gated *both* the cheap local-first capabilities and the expensive AI capabilities behind the same paywall, which both undercharges for the AI value (a trial then nothing) and overcharges for the local-first value (the user can't keep using their own data tools without paying). It also created tension with the local-first trust posture, because the user's own data and the services that operate on it were treated as a single bundled product.

The reframed model separates the two halves and prices each one honestly: the free tier is the parts that cost Cuizine nothing, and the paid tiers are the parts that cost Cuizine real money.

## Decision

**Cuizine ships three subscription tiers at v2 launch:**

### Cuizine Free (local-first, truly free, no time limit)

- Local-first encrypted profile and constraint engine
- Manual recipe import (paste a URL parsed via schema.org Recipe markup, type one in, photograph one and manually transcribe)
- Manual recipe management and search/filter over the user's own collection
- Manual meal logging (what did you actually eat today)
- Household structure setup (without AI-powered planning across the household)
- Pantry tracking via manual entry (no AI ingestion from receipts or photos)
- Full local-first encrypted container, with cross-device E2E-encrypted sync

The free tier explicitly **does not** include: any AI generation, any AI parsing of imports, any AI suggestions or planning, Instacart integration (see ADR 0003), AI-powered pantry ingestion from receipts or photos, or any other capability that requires a cloud LLM call. The line is precise: **anything that requires Cuizine to call an LLM is paid; anything that's pure structured-data manipulation on the user's device is free.**

The free tier is *not* a trial, *not* time-limited, *not* feature-gimped relative to its own purpose. It is a real, complete, useful product on its own terms — a structured local-first food companion — with the AI agents being a paid expansion on top.

### Cuizine (standard paid tier)

Everything in Cuizine Free, plus the full multi-agent system: Curator, Chef, Planner, Pantry (with AI ingestion from receipts/photos), Sourcing (with full Instacart integration). Single profile (the user's own). Cloud inference active. Automatic model escalation based on task stakes (per ADR 0006). Full week-ahead planning.

### Cuizine Family (household tier)

Everything in Cuizine, plus dependent profiles, household-level planning across multiple constraint sets simultaneously, household-level cultural fluency reconciliation (see ADR 0002), family-shared pantry, and the deeper Planner reasoning that running across multiple constraint sets requires.

The Family tier's higher price reflects genuinely more substantial work: reasoning across three constraint sets simultaneously to find a single meal that satisfies all of them is meaningfully harder than reasoning across one. This is not a price-discrimination lever — it is an honest reflection of the work being done.

## Trial mechanics for paid tiers

New users who sign up for **Cuizine** or **Cuizine Family** receive **14 days of full-feature access at no charge**, with **no payment method required at signup**. Cuizine asks for the payment method on day 12, giving the user two days to decide before the trial ends. If they don't subscribe, they automatically downgrade to **Cuizine Free** — they don't lose access to the app, only to the AI capabilities. Their data, profile, recipes, and any AI-generated content from the trial remain accessible (see "Content portability" below).

## Content portability (forever commitment)

Users always retain access to content they previously created, including AI-generated content from a paid subscription, even after downgrading to Cuizine Free. A former paid user opening the app sees their old plans, their old recipes, their old meal history — they simply cannot generate new AI content until they re-upgrade. This is the right ethical posture (the data is theirs, including the data the AI helped create) and it's also a strong re-engagement hook.

## Pricing shape

Pricing is **monthly with an annual option** (annual at a meaningful discount, exact percentage to be determined during v2 planning, conventionally ~17% off monthly). Annual subscriptions are the single largest contributor to retention and revenue stability for compounding-value products and offering both lets users choose their commitment level. Specific dollar prices are deferred to v2 planning and not locked in this ADR.

## Forever commitments (not v1 deferrals)

The following are explicitly **rejected as product design**, not deferred features:

- **No credit economy.** No tokens, no balances, no per-action consumption meters.
- **No user-facing model selectors.** Users never see a model name or pick a model.
- **No premium-recipe buttons.** No "spend more for higher quality" per-action upgrades.
- **No model exposure of any kind in the UI.** Token counts, model names, latency tradeoffs — all invisible to the user.

Revisiting any of these requires a deliberate ADR superseding this one. They are not waiting to be added "when the time is right" — they are actively wrong for Cuizine and we have committed not to build them.

## Alpha posture

The v1 closed alpha (per ADR 0001) does not implement the free tier. All alpha users get full-feature Cuizine throughout the alpha because the alpha is about validating the AI agents working under real constraints with real users. The free tier and the trial mechanics are introduced at v2 launch, not before.

## Consequences

**Positive.** The free tier is genuinely free, costs Cuizine essentially nothing to run, and aligns honestly with the local-first trust posture (users can use Cuizine without ever sending data to the cloud — see updated `vision.md`). The free tier becomes a trust-building exercise rather than a feature-limited tease, and it creates a natural conversion narrative because users sit on a fully-built constraint profile that the agents would feast on. The chef metaphor (you hire a chef, you don't hand them tokens) is preserved. Tier differentiation is implicit in product behavior, never exposed as a knob. Family tier has an honest justification (genuinely more work, not price discrimination). Paid trial is generous (14 days, no card upfront) and matches the patterns of healthy compounding-value products.

**Negative.** The free tier requires real product design work to be "useful enough to attract but distinct enough to convert." Free-tier features (especially manual recipe import via schema.org) need careful UX so they don't feel like a hassle compared to AI-assisted import. There's a small risk of free-tier cannibalization of the paid tier, though we believe Cuizine is naturally protected from this because the AI capabilities are so much of the actual value. The trial flow needs to handle "graceful downgrade" carefully so users feel fine about reverting to free rather than ghosted by the product.

**Neutral.** Schema.org Recipe markup parsing for free-tier URL imports is a small but real engineering item that needs to live in the v1 build, even though it's a v2-exposed feature, because the parsing logic can be developed and tested during v1 alpha as an internal tool.

## Alternatives considered

**14-day full-feature trial then standard paywall, no free tier.** The original proposal. Rejected because it treats Cuizine as a single bundled product when it's actually two architecturally separable halves (cheap local-first + expensive cloud AI), and it leaves the cheap half locked behind a paywall it doesn't need.

**Feature-limited free tier (single profile, basic constraint engine, fast/light models, limited horizon).** Rejected because it cannot demonstrate Cuizine's magic moment, which requires the full engine and meaningful planning horizon. A user trying this would experience Cuizine as "an AI recipe generator with extra steps" — exactly the failure mode we're trying to avoid.

**Credit/token economy with per-action upgrades.** Rejected on grounds of cognitive tax, eval fragmentation, gambling-shaped dynamics in a medical context, and tone misalignment. See ADR 0006 for the parallel argument against user-facing model selectors.

**Single paid tier (no Family differentiation).** Rejected because the household feature is genuinely more substantial work and represents genuinely more value for a real, painful, daily problem. Pricing it the same as the single-user tier would undersell the work and undervalue the household use case.

**Annual-only or monthly-only.** Rejected because both shapes exclude meaningful user segments. Monthly-with-annual-option is the standard healthy SaaS pattern and lets users self-select their commitment level.

## Related

- See `vision.md` § v2 — North American public launch, § Trust posture, § What Cuizine is not
- Pairs with 0006 (no user-facing model selection — both decisions concern where complexity lives, server-side vs user-side)
- Pairs with 0004 (Family tier is the home for multi-profile household features)
- Pairs with 0003 (Instacart is paid-tier-only, since the free tier excludes cloud-incurring capabilities)
- Schema.org Recipe markup parsing for free-tier imports to be specified in `data-model.md`
