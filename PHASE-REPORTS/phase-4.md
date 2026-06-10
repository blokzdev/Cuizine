# Phase 4 report — The food data layer

**Completed:** 2026-06-10 · **Exit criteria:** met to the fake-provable limit
(live USDA/OFF calls honestly ledgered as pending verification)
**Commits:** `8b5f8b2`, `87dd6d3`, `68c8112`, + close-out commit

## Built

- **The three-layer Food Data Provider** (ADR 0012): cache → curated bundle →
  external sources → severity-scoped AI fallback, invisible to callers — the
  validator still sees only its port and did not change.
- **The curated bundle v1.0.0** (authored this phase): 48 entries covering the
  canonical fixture vocabulary with Punjabi names as `alternative_names`
  (haldi → Turmeric works), all 9 allergens, religious tags, FODMAP and
  high-glycemic categories, the kala namak sodium edge case — plus the ramps
  correction (wild allium) and, after the religious suite exposed the gap,
  `root_vegetable` tags for Jain coverage.
- **Cache mechanics** (`data-model.md` §5): unique (key, kind) lookups,
  normalized keys (lowercase/whitespace/accent-fold), TTLs — USDA 6 months,
  OFF 3 months, bundle/AI never — with a tested expiry→refetch cycle.
- **Live clients, absence-driven** (DECISION-LOG #4a): USDA FoodData Central
  (search-only — the flattened response carries all seven validator
  nutrients, so no quota is spent on detail calls; key as a header, never a
  URL) and Open Food Facts via Search-a-licious (the 2026 path; legacy search
  verified decaying) with the required User-Agent and the grams→milligrams
  sodium conversion that OFF's format demands. Key present → live; absent →
  recorded fixtures. A network failure returns Unknown — it can never crash
  a validation.
- **AI-fallback plumbing:** the categorizer seam with low-confidence caching;
  the honest fake returns null (no fake pretends to be AI). The real call
  routes through ModelProvider when Phase 5 builds it (ADR 0006).

## Tested

**298 tests green; the full gate (ktlint, Lint warnings-as-errors, tests,
validator coverage floors) passes.** New this phase: provider layer-ordering
/ TTL / correction tests; recorded-shape mapper tests (USDA nutrient numbers,
OFF unit conversion); the canonical **Sukhi-and-alliums case against real
food data** (roadmap's named Phase 4 exit case); the **allergen-exhaustive
suite** (9 allergens × literal / categorical / hidden-in-product /
safety-floor-zero-AI-calls / user-confirmed-safe = 45); the
**religious-dietary suite** (halal, kosher, Hindu vegetarian, Jain, Buddhist
= 17).

## Checkpoint decisions

- **USDA/OFF API state [research-informed]: KEEP both** — no disqualifiers;
  build facts pinned in DECISION-LOG #4a (rate limits validate the
  cache-first design).
- **AI-fallback accuracy [data-driven]: deferred to alpha** as the roadmap
  specifies — first contact with messy real-world names is the measurement.

## Founder-pending / honest notes

- **Live verification pending:** USDA and OFF clients are mapping-tested
  against recorded shapes; live calls await your key (SETUP.md Phase 4 —
  ~1 minute). Ledgered as verified-against-fake.
- **Kosher meat+dairy mixing is not checkable in v1** (DECISION-LOG #4b):
  combination rules aren't one of the five ADR-fixed constraint types. An
  honest test documents the gap; recommend an open-questions entry in
  `constraint-engine-spec.md` §11 and a possible v2 ADR. Needs your read.
- **Halal risk-tag layering** recorded as Curator prompt guidance (Phase 5):
  a halal household needs `non_halal` + `non_halal_risk` + `contains_pork` +
  `contains_alcohol` avoids written together.
- Bundle authoring remains founder-reviewable content (`roadmap.md` §11
  raises outside help — nutritionist/cultural consultant — for bundle
  depth; the current 48 entries serve the alpha fixtures and flows).

## Next

Phase 5 — agents and the orchestrator: the ModelProvider interface with the
three adapters (+ FakeModelProvider), the Curator/Chef/Pantry agents and
their prompts, rule-based intent routing, the validator regeneration loop
(orchestrator-owned, 3 attempts), the eval harness — then the real
conversation replaces the scripted one, same State, same Intents.
