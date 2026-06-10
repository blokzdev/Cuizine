# 0003 — Instacart as the sole grocery integration

**Status:** Accepted
**Date:** 2026-04-11

## Context

Cuizine's pillar 2 (pantry-aware planning) and the broader sourcing capability require integration with grocery retailers — to know what's in stock, what prices are, what's on sale, and to enable shopping list handoff or delivery. The naive approach is to integrate with major retailers individually: Lobloaws/PC Express, Sobeys/Voilà, Metro, Walmart, etc. for the Canadian market, with parallel integrations for US chains in v2. Each individual integration is a separate API surface, separate auth, separate product catalogue normalisation, separate pricing logic, separate maintenance burden, separate breakage when any chain changes its backend.

For a two-person team building toward a Play Store launch, an N-fronts integration war is engineering effort that is both enormous and *invisible to users* — they don't care that Cuizine integrates with five chains instead of one; they care that they can get their groceries. Meanwhile, Instacart already operates as the abstraction layer over the major North American retailers and has done the hard work of unified product catalogues, pricing, stock, and delivery.

## Decision

**Cuizine integrates with Instacart as the sole grocery integration in v1 and v2** — within the paid tiers (Cuizine and Cuizine Family). The free local-first tier (introduced in v2, see ADR 0007) does not include Instacart integration, since the free tier explicitly excludes cloud-incurring capabilities. The Sourcing Agent calls Instacart for retailer queries, product lookups, pricing, stock checks, and (where supported) shopping list handoff.

**The Sourcing Agent is architected with a clean internal interface** — `SourcingProvider` with methods like `searchProducts`, `getStock`, `createList`, `estimatePrice` — so that Instacart is one *implementation* of that interface, not the interface itself. This means:

- v3 expansion to non-Instacart markets (UK, Australia) is implementing a second `SourcingProvider`, not refactoring the agent.
- Fallback options (manual shopping list mode, direct retailer integrations as a backup) can be added as additional implementations of the same interface.
- If Instacart's API terms change, pricing changes, or vendor relationship breaks, swapping providers is a contained engineering task rather than a rewrite.

We **depend on Instacart as a current choice** while **architecting around the possibility that this changes**. This is the same pattern applied to AIDb-as-future-backend and to MIRIX-as-non-dependency.

## Consequences

**Positive.** Massive reduction in v1 and v2 engineering scope for sourcing. Unified product catalogue, pricing, and stock data come "for free" from Instacart. The Sourcing Agent's prompts stay clean (one provider to reason about). v2 covers Canada and US together with no additional integration work because Instacart already serves both. Engineering attention can focus on the constraint engine and agent reasoning — the parts users actually feel — instead of N parallel integration tarpits.

**Negative.** Vendor lock-in for a load-bearing capability. If Instacart raises developer pricing, restricts food-planning apps, or otherwise changes terms, Cuizine has a real problem. Instacart's product catalogue is broad but not exhaustive — some niche/cultural ingredients (which matter for pillar 5) may not be well-represented. Users in regions Instacart doesn't serve well are out of scope until v3.

**Neutral.** v3 must include a non-Instacart sourcing strategy as a hard requirement, since UK/Australia/other English-speaking markets are not Instacart territory.

## Alternatives considered

**Individual chain integrations from v1.** Rejected as an N-fronts war we cannot win and that users cannot see. Engineering effort would dominate v1 and v2 with no user-visible payoff.

**Build a sourcing-agnostic internal catalog and let users manually mark items as available.** Rejected for v1/v2 because it dumps the integration work onto the user, kills the magic of pantry-aware planning, and undermines pillar 2. This is acceptable only as a *fallback mode* for users in unsupported regions, not as a default.

**Wait until v3 to integrate any sourcing layer, and ship v1/v2 with manual lists only.** Rejected because pantry-aware planning is one of the five pillars and manual-only sourcing would make it second-class. Pillar 2 deserves a real sourcing backbone from v2 launch.

## Related

- See `vision.md` § Sourcing posture, § What Cuizine is not
- Pairs with 0001 (NA v2 launch is enabled by Instacart's coverage of both countries)
- Future architecture doc must define the `SourcingProvider` interface explicitly
