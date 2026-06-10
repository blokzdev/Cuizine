# 0012 — Food data sources and taxonomy strategy

**Status:** Accepted
**Date:** 2026-04-11

**Amendment note:** This ADR's decision (the data sources, the three-layer cache/bundle/external strategy, and the AI-assisted categorization fallback) is unchanged. Following ADR 0016 (native Kotlin), the *client mechanism* for Open Food Facts is updated: rather than the `openfoodfacts-dart` Flutter package, Cuizine calls the Open Food Facts REST API directly via Retrofit/OkHttp (the same HTTP stack used for the USDA client). This is a mechanism change, not a decision change.

## Context

The constraint engine reasons about ingredients categorically, not just literally. When Sukhi tells Cuizine "I can't have onions or garlic," the engine has to know that *leeks*, *shallots*, *chives*, *spring onions*, and *ramps* are all alliums and should also be flagged for her IBS. When the Chef agent generates a recipe with "ghee," the engine has to know that's a dairy derivative for users with dairy restrictions. When the Pantry agent identifies "Patak's Korma Paste" from a manual entry, it needs to know what's actually in that jar — onion, garlic, almonds, multiple oils, possibly hidden allergens. When the v3 Observer tracks Sukhi's sodium intake over a week, it needs accurate per-ingredient sodium data.

This requires structured food knowledge — categories, allergens, nutritional composition, packaged-product compositions, ingredient relationships — at a level of detail and breadth that no two-person team can author from scratch and no single LLM can be trusted to know reliably. We need real data infrastructure from v1 because the constraint engine cannot do its central job (per pillar 1, ADR 0009, ADR 0010) without categorical reasoning.

The naive options — build a custom taxonomy in-house, depend entirely on LLM knowledge, or treat ingredients as opaque strings — are all unacceptable. There are mature, openly-licensed external food data sources that can carry the load: USDA FoodData Central for raw ingredients and nutritional composition, Open Food Facts for packaged products and global coverage. Combined with a small curated baseline that ships with the app and a per-user cache that grows with use, these sources form a layered architecture that gives the engine real categorical knowledge without building a database from scratch.

## Decision

### The three-layer architecture

Cuizine's food data layer is structured as **three concentric layers of knowledge**, each with a distinct lifecycle and role:

**Layer 1: The local cache.** A per-user, per-device record of every ingredient lookup that has ever happened on this device, stored in the local SQLite container. Grows naturally as the user's vocabulary expands. After a few weeks of typical use, almost every query is a cache hit. The cache is part of the encrypted sync container per ADR 0011, so signed-in users have their cache replicated across devices.

**Layer 2: The curated bundle.** A small, opinionated, hand-authored set of canonical entries that ships with the app binary. Contains the medically-critical categories (top common allergens, alliums, nightshades, FODMAP groups, gluten sources), the religiously-critical categories (pork, alcohol, beef, gelatin, animal-derived), the foundational ingredient identities for the cuisines Cuizine targets first (Punjabi/North Indian staples — atta, common dals, common spices, ghee, paneer), and corrections for cases where USDA or Open Food Facts is wrong, incomplete, or missing entries. Updated through app updates rather than network calls.

**Layer 3: The external sources.** USDA FoodData Central for raw and minimally-processed ingredients with high-quality nutritional composition data. Open Food Facts for packaged products and global ingredient coverage including regional foods USDA doesn't index well. Both queried only when cache and bundle miss.

### The query order

The food data abstraction layer queries the layers in this canonical sequence on every lookup:

1. **Local cache first.** If the ingredient has been looked up before on this device, return the cached entry immediately. Fastest path, no network call, most aligned with the local-first trust posture.

2. **Curated bundle second.** If the cache misses, check the curated bundle. On hit, return the bundle entry *and* write it to the local cache so future lookups skip directly to the cache. Always available even on first launch.

3. **External sources third.** USDA primary for ingredient-shaped queries, Open Food Facts primary for product-shaped queries (recognizable by barcode-shaped or branded-product-shaped names). Result is normalized to the canonical internal format, written to the local cache with high-confidence tagging, and returned. If the primary source for a query type returns nothing, the secondary source is tried.

4. **AI-assisted categorization fourth.** If all three above layers fail, the engine falls back to AI-assisted categorization — bounded by constraint severity, not blanket-applied. Mechanics in the Unknown ingredient handling section below.

### Corrections-take-precedence exception

When the curated bundle and an external source disagree about an ingredient — for example, USDA's entry is wrong or incomplete and we've authored a corrected entry in the bundle — the bundle takes precedence, *but only for entries explicitly marked as corrections*. Most bundle entries are *additions* (regional ingredients, foundational categories) and don't override external sources for ingredients those sources do know. Only entries explicitly tagged `correction: true` override external source data.

This makes the bundle act as both a baseline-of-knowledge and a correction-layer simultaneously, without confusing the two roles.

### Confidence tagging in the cache

Every cache entry carries a confidence tag from a small fixed set:

- **`high-confidence-bundle`** — from the curated bundle. Authoritative, hand-reviewed, never auto-changed.
- **`high-confidence-usda`** — from USDA. Authoritative for nutritional and category data; 6-month TTL.
- **`high-confidence-off`** — from Open Food Facts. Trusted but with a 3-month TTL because OFF is community-maintained.
- **`low-confidence-ai`** — from AI-assisted categorization. Provisional until confirmed.
- **`high-confidence-user-verified`** — was originally `low-confidence-ai` and has been explicitly confirmed by user action. Promoted from low to high confidence.

The validator from ADR 0010 reads confidence tags and uses them in its decision-making, particularly at the boundary between AI-derived categorizations and medically-critical constraints.

### Unknown ingredient handling and AI-assisted categorization

When the cache, bundle, and external sources all fail to identify an ingredient, the engine falls back to AI-assisted categorization through a small structured-prompt LLM call. **The fallback is scoped by constraint severity**, with three distinct paths corresponding to the severity tiers from ADR 0009:

**For inviolable constraints** (allergies marked as inviolable, hard religious prohibitions): the engine **cannot AI-fallback**. An unknown ingredient encountered in a recipe being checked against an inviolable constraint causes the suggestion to be **rejected**. The Chef regenerates without that ingredient. The cost is occasional false rejections; the alternative is occasional false passes, which can hospitalize someone. False passes on inviolable constraints are catastrophic enough that the conservative behavior is correct. **This is the safety floor and it is non-negotiable.**

**For medical and religious constraints** (severity tiers 2 and 3 from ADR 0009): the engine performs an AI-assisted categorization call. The flow:

1. Send the unknown ingredient name to a cheap-tier LLM (Claude Haiku or Gemini Flash per ADR 0006) with a tightly-scoped structured-output prompt requesting categories, allergens, dietary properties, and nutritional rough-estimate.
2. Normalize the structured response into a canonical cache entry tagged `low-confidence-ai`.
3. The validator's deterministic rule check runs against the AI-derived entry, exactly as it would against any other entry. If it indicates a violation, the suggestion is rejected and the Chef regenerates.
4. If the AI-derived categorization indicates the ingredient is *safe* for the user's constraints, the validator passes the suggestion *but* the orchestrator surfaces a small inline note: *"I'm not 100% sure about [ingredient] — if you know it bothers you, swap it out."* Calm, brief, matches the calm-precise-warm voice. Acknowledges uncertainty honestly without panicking the user.
5. The cache entry remains `low-confidence-ai` until a user action upgrades it.

**For preference constraints** (severity tier 4): the AI fallback runs the same way but *without* the inline disclosure. Preferences are by definition lower-stakes; a small wrong categorization just means a slightly mismatched suggestion, not a medical incident. The cache entry is still tagged `low-confidence-ai` for audit purposes, but the user is not interrupted.

### Confidence promotion

A `low-confidence-ai` entry graduates to `high-confidence-user-verified` when a user action confirms it: the user accepts a suggestion containing the ingredient and reports the meal was fine; the user explicitly confirms an inline disclosure note; or the user manually edits the entry through Settings (a v2 capability). After promotion, the entry behaves like any high-confidence entry and the user is no longer interrupted by uncertainty disclosures for it.

The corollary: if a user reports that an AI-categorized ingredient *did* trigger a constraint violation, the entry is *not* simply demoted — it is marked as a correction, the underlying assumption is logged as a severity-zero incident per ADR 0010, and the alpha founder reviews it. The audit path makes "Sukhi being misled about ramps once" the kind of failure we never see twice.

### The Food Data Provider abstraction layer

All of the above is exposed to the rest of the constraint engine through a single abstraction layer — the **Food Data Provider** interface — that the Curator, Chef, Pantry agent, and validator all call. The interface presents a unified API: "give me the canonical entry for this ingredient name (or barcode)." The internal multi-layer query orchestration is invisible to callers.

This is the same abstraction-over-providers pattern as ADR 0006's `ModelProvider` and ADR 0011's data layer abstraction over Firebase. Callers don't depend on internals; internals can evolve (add caching, swap sources) without changing callers; testing is much easier through mocking.

### Caching behavior

- All entries from external sources are cached immediately. Network round-trips happen at most once per ingredient per user per device.
- Cache TTLs are long: USDA 6 months, OFF 3 months, bundle and user-verified entries never expire. AI-derived entries have no TTL but are subject to upgrade or correction.
- Cache entries are part of the encrypted sync container per ADR 0011. Signed-in users get their cache replicated across devices.
- Cache is per-user, never shared between users. Per-user storage cost is small (a few hundred KB for typical vocabulary sizes) and the per-user shape is consistent with the trust posture.

### Dependencies introduced

- **USDA FoodData Central API.** Free with API key registration, REST interface, no commercial restrictions.
- **Open Food Facts API.** Free, open-data licensed (commercial use allowed), community-maintained, global product coverage.
- **Open Food Facts REST API via Retrofit/OkHttp.** The Open Food Facts API is accessed directly over HTTP using the same Retrofit/OkHttp stack as the USDA client (per ADR 0016), rather than a language-specific client package.
- **Cheap-tier LLM** (Claude Haiku, Gemini Flash) routed through `ModelProvider` for AI-assisted categorization (per ADR 0006).

### Privacy implications

Every query to USDA or Open Food Facts is technically a network request that includes the ingredient name. This is not user content in the strong sense, but it is potentially inferable: USDA logs seeing repeated queries for "asafoetida" and "kala chana" could let an adversary infer South Asian users.

We mitigate this in three ways:

1. **The cache-first architecture means external queries decrease over time.** Privacy *improves with time* — a property unique to cache-first designs.
2. **Queries can be batched and order-randomized** within latency budgets, reducing timing-based inference. Implementation discipline rather than structural change.
3. **Signed-in users could in principle have all queries proxied through the Cloud Function** so upstream sources see only Cuizine's IP. Deferred to v2 evaluation; not a v1 commitment because it slightly weakens the "no Cuizine backend in the picture" promise in another direction.

For signed-out users, queries go directly device-to-source per the v1 alpha model from ADR 0011.

## Consequences

**Positive.** The constraint engine has real categorical knowledge from day one — Sukhi's IBS-and-alliums case works correctly because the engine knows what alliums are. USDA and Open Food Facts give us coverage and quality we could never author ourselves, on a free-licensed basis. The cache-first architecture means the engine becomes faster, more offline-tolerant, and *more privacy-preserving* over time, which is a beautiful property emerging from the layering. The AI-fallback path is bounded by severity in a way that preserves ADR 0010's deterministic validation guarantee for inviolable constraints while still helping users where help is safe to give. The Food Data Provider abstraction makes internals swappable and testable.

**Negative.** Two external data dependencies (USDA, Open Food Facts), both accessed over their REST APIs, where we previously had zero. Each can fail, change, or be deprecated. The curated bundle is real ongoing work — entries authored, reviewed, updated as external coverage gaps surface. The AI-fallback adds a new failure mode (hallucinated categorization) which we mitigate through severity scoping but cannot eliminate entirely. Privacy implication of external queries is real and named honestly with mitigations that are genuine but not absolute.

**Neutral.** The food data layer becomes load-bearing infrastructure that needs its own tests, monitoring, and evaluation harness. The bundle is a small but real ongoing maintenance commitment.

## Alternatives considered

**Build a custom taxonomy in-house from scratch.** Rejected. Months of work, narrow coverage, never finished. Two-person team cannot maintain a global food database; nobody can.

**Depend entirely on LLM knowledge.** Rejected. LLMs hallucinate; the validator from ADR 0010 exists precisely to prevent LLM-output from silently violating constraints.

**Treat all ingredients as opaque strings.** Rejected. Fails the Sukhi-and-IBS case immediately. Categorical reasoning is non-negotiable for pillar 1.

**USDA only, no Open Food Facts.** Rejected. USDA's coverage is strong for American ingredients and weak for global and packaged products. OFF fills the gaps.

**Open Food Facts only, no USDA.** Considered. OFF has broader coverage but USDA has higher data quality for nutritional composition (matters for v3's Observer agent). Both together are complementary.

**Single-layer architecture (just external sources, no cache and no bundle).** Rejected. Slow, network-dependent, fragile when offline, weakens privacy over time rather than strengthening it.

**Two-layer architecture (cache + external sources, no curated bundle).** Considered. Loses the medically-critical baseline available on first launch, the correction layer, and the regional-ingredient additions. Bundle is small enough that the cost is negligible and benefits are real.

**Shared global cache server.** Rejected. Would require backend service we don't have and would create a centralized data point that violates the per-user-encrypted-container principle from ADR 0011.

**LLM-as-validator.** Rejected (same rejection as ADR 0010, restated for completeness). LLM validators can hallucinate as easily as LLM generators.

**Always-fall-back-to-AI for unknown ingredients regardless of severity.** Rejected. Allowing AI categorization to determine whether a peanut-allergic user can safely eat an unknown ingredient is exactly the failure mode we are preventing. Severity scoping is the only safe shape.

## Related

- See `vision.md` § Trust posture
- Pairs with 0006 (AI-fallback uses `ModelProvider` and routes to cheap-tier providers)
- Pairs with 0009 (severity tiers from this ADR's AI-fallback scoping are exactly the tiers from ADR 0009)
- Pairs with 0010 (food data layer is what the validator's deterministic rule checks read from; AI-fallback is the carefully-bounded exception to "no LLM in the validator path")
- Pairs with 0011 (cache lives in the encrypted container and is synced via Firestore for signed-in users)
- The forthcoming `constraint-engine-spec.md` must define the Food Data Provider interface, the canonical internal entry format, and the validator's interaction with confidence tags
- The forthcoming `data-model.md` must specify the cache schema, the bundle format, and the canonical entry fields
- The forthcoming `testing-strategy.md` must give the food data layer first-class treatment, including evals for AI-fallback accuracy
- The forthcoming `security-and-privacy.md` must address the external query privacy implication and the v2 Cloud-Function-proxy option for it
