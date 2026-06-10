# Phase 3 report — The constraint engine and validator

**Completed:** 2026-06-10 · **Exit criteria:** met (engine passes the hard-cases
suite; Profile surface driven by the real engine; conflict surface backed by
the real conflict algorithm — all verified on-device)
**Commits:** `eeec208`, `c0b0a86`, `5fb4a30`, + close-out commit

## Built

- **The 11-operation constraint graph API** (`constraint-engine-spec.md` §6)
  and the engine behind it: write-time tier enforcement with structured
  errors (never coercion), two-pass active-set computation in the spec's
  dimension order, per-profile active-set caching, session-scoped
  relaxations, structural conflict detection.
- **Scope evaluation** (§5): all six temporal kinds including composite
  AND/OR and solar daily windows (NOAA approximation behind an injectable
  port — DECISION-LOG #3a), contextual requires/excludes flags, coarse
  location, v2-ready household/profile dimensions. DST transitions tested
  both directions.
- **The deterministic validator** (§7, ADR 0010) — the safety floor, built
  exactly as specified: five steps, severity-scoped unknown-ingredient
  handling (Inviolable rejects with ZERO AI calls — asserted), soft-limit
  and prefer disclosures, fasting `incompatible_with`, stateless and pure.
- **Conflict mechanics** (§8, ADR 0009): failure-pattern analysis, silent
  preference-relaxation candidates, option construction — Inviolables never
  pickable (skip straight to honest no-meal-found), none-of-these always
  present, session relaxations never persist.
- **Persistence**: Room-backed store; all five payload types + scope +
  provenance round-trip value-equal through the JSON columns.
- **The first two mock→real swaps, screens untouched:** `ProfileRepository`
  is real (engine + Room; onboarding answers land as constraint rows with
  provenance). The suggestion path now runs scripted Chef meals through the
  REAL validator and REAL conflict planning (`ValidatedMockSuggestionRepository`)
  — on-device, the masoor dal card carries a live prefer-misalignment
  disclosure, and a scripted beef meal produces a real Religious & Cultural
  conflict with the constraint's own provenance phrasing in the option label.

## Tested

**224 tests green; Kover enforces validator 95% line / 85% branch (and scope
evaluation) in the per-commit gate.** Hard-cases categories vs
`testing-strategy.md` §5 minimums: severity ≥16 ✓(18), types ≥25 ✓(27), scope
≥15 ✓(38), active-set ≥10 ✓, validator ≥30 ✓(37), conflict ≥15 ✓, provenance
≥10 ✓. The suite is append-only from here.

**The suite caught two real engine bugs before any user could** (the reason
it exists): Preference-tier rejections and contextual-expiry timezone
handling — both fixed to spec, plus a §7 Step 4 letter-of-the-spec fix.
Details: DECISION-LOG #3b.

## Checkpoint decisions

- **Validator 3-retry bound [data-driven]:** unchanged at 3; first real
  contact comes with the Phase 5 Chef — resurfaces there (the loop itself is
  orchestrator-owned per §7 and lands in Phase 5).
- **Active-set cache invalidation [data-driven]:** behavior-tested
  (write/contextual invalidation); recomputation measurement deferred to the
  named moment (post-hard-cases real query patterns, Phase 5/7).

## Deviations & founder-pending notes

- **None at the park-always core.** The validator and conflict policy are
  implemented exactly as specified; the two fixes above made the code MORE
  conformant.
- **Founder-pending (doc wording, non-blocking):** §3 says v1 require windows
  "support single-meal and daily", but §7's stateless per-suggestion contract
  cannot enforce daily without meal history. Built per §7 (daily requires
  stored, per-meal-checked); recommend a one-line §3 clarification —
  awaiting your sign-off since it brushes validator-adjacent spec text
  (DECISION-LOG #3b-4).
- Solar windows use per-IANA-zone representative coordinates (8 Canadian
  zones; deterministic fallback elsewhere) — DECISION-LOG #3a; contained
  upgrade path if alpha needs true-location solar.
- Allergen-exhaustive (45+) and religious-dietary (15+) suites land with
  Phase 4's real food categories, per testing-strategy's own sequencing.
- Property-based testing deferred to Phase 4 with rationale (#3b).

## Next

Phase 4 — the food data layer: Food Data Provider (cache → curated bundle →
USDA/OFF clients with recorded fixtures → severity-scoped AI fallback),
`food_data_cache` operations and TTLs, the curated bundle asset, and the
allergen/religious exhaustive suites. The `StaticFoodDataPort` binding is
replaced; the validator doesn't change.
