# DECISION-LOG.md — Checkpoint resolutions and architectural surfacings

> Every roadmap Decision-checkpoint resolution and every architectural surfacing
> made under the autonomous build loop, newest entries appended at the bottom.
> Format per entry: checkpoint/situation · evidence · decision · confidence ·
> rollback note. Changed product decisions additionally get superseding ADRs
> (`docs/build-conventions.md` §9). Entry #0 is the founder's standing delegation.

---

## #0 — Founder standing delegation (delegated mode active)

**Date:** 2026-06-10
**Type:** Founder decision (recorded verbatim in intent; source: founder kickoff prompt, 2026-06-10)

The founder has granted a standing delegation authorizing the coding agent to
resolve **all roadmap Decision checkpoints for the v1 build loop (Phases 1–7)**
autonomously, per `docs/roadmap.md` §2 Principle 6 "Operating modes":

- **[research-informed]** → keep the documented choice unless research reveals a
  disqualifier (deprecation/abandonment, security advisory, breaking change,
  clear incompatibility); otherwise take the minimal-deviation alternative and
  write a superseding ADR per `docs/build-conventions.md` §9.
- **[data-driven]** answerable in-build → answer from build/test experience at
  the named moment; log observation and course taken.
- **[data-driven]** alpha-contingent → log as explicitly deferred to alpha.
- Every resolution = one entry here. Changed decisions additionally get ADRs.
  Nothing is silently swapped.

**Scope boundaries (unchanged by this delegation):**
- Park-always items are never resolved autonomously: deviations from spec at the
  encryption boundary, the validator's deterministic logic, or `TierPolicy`
  enforcement; and cross-doc contradictions the conflict-resolution chain cannot
  resolve. These park as founder-pending blockers in `PROGRESS.md`.
- Forbidden behaviors (`docs/build-conventions.md` §11) are never unlocked.
- Conflict-resolution chain (founder-specified): vision > PRD > ADRs >
  technical-architecture/data-model > conditional docs > build-conventions >
  implementation; `glossary.md` wins on terminology.

**Review & reversal:** reviewable and reversible by the founder at any time via
`FOUNDER-FEEDBACK.md`. Founder review surface = pushed `PHASE-REPORTS/` at each
phase completion.

**Rollback note:** revoking this entry returns the build to interactive mode
(`docs/roadmap.md` §2 Principle 6 default); no code changes required.

---

## #1 — Doc corrections: Flutter-era residue in four foundation docs

**Date:** 2026-06-10 · **Type:** Architectural surfacing (doc correction, delegated mode)

**Situation:** Full-foundation audit found six residual references to the
superseded ADR 0005 (Flutter/Dart) stack that contradict ADR 0016, despite
ADR 0016's Related section recording those docs as updated:
`build-conventions.md` §3 (`analysis_options.yaml`), §6 (`final` Dart syntax),
§6 (`lib/` folder), §11 (`lib/core/config/`); `glossary.md` "Subsystem" entry
(`lib/`); `security-and-privacy.md` §10 (`lib/data/encryption/`);
`technical-architecture.md` §6 (`json_serializable`/`freezed` bullet, redundant
with the kotlinx.serialization bullet above it).

**Alternatives:** (a) leave as-is and interpret on the fly; (b) correct the
docs to match ADR 0016.

**Decision:** (b) — corrected all six, with dated correction notes on the two
substantive ones. This is the bidirectional update discipline
(`build-conventions.md` §7) completing ADR 0016's own documented update pass —
not editing docs to match wrong code (no code exists yet). Glossary's
deliberately-historical "(Superseded.)" entries left untouched.

**Confidence:** High. **Rollback:** `git revert` of the correction commit.

---

## #1a — Phase 1 checkpoint: dependency & library currency `[research-informed]` — RESOLVED

**Date:** 2026-06-10 · **Checkpoint:** `docs/roadmap.md` §3 Phase 1, checkpoint 1

**Evidence:** 7-cluster web research (official release notes, Google Maven,
GitHub releases; full matrix + sources in the research output, key facts below)
cross-checked for mutual compatibility.

**Decision: KEEP every documented stack choice** (ADR 0016 / ADR 0006 / ADR 0011
/ ADR 0012 libraries all pass keep-unless-disqualified — no deprecation,
advisory, breaking-change, or incompatibility on any *named* choice). Pinned
version matrix (catalog = `gradle/libs.versions.toml`):

- **Toolchain:** AGP 9.2.1 · Gradle 9.4.1 · Kotlin 2.3.21 · KSP 2.3.9 · JDK 21
  · compileSdk 37 · targetSdk 36 · minSdk 26 (see #1c)
- **UI:** Compose BOM 2026.06.00 (fallback 2026.05.01) · Material3 1.4.0 ·
  activity-compose 1.13.0 · lifecycle 2.10.0 · navigation-compose 2.9.8
- **Data/DI:** Room 2.8.4 (+ room gradle plugin) · Hilt 2.59.2 ·
  androidx.hilt:hilt-navigation-compose 1.3.0 · kotlinx-serialization-json
  1.11.0 · coroutines 1.11.0
- **MVI:** Orbit MVI 11.0.0 (core/viewmodel/compose/test)
- **Quality:** ktlint 1.8.0 via jlleitschuh plugin 14.2.0 (must set
  `ktlint.version` explicitly — plugin bundles 1.5) · Kover 0.9.8 · JUnit
  4.13.2 · Robolectric 4.16.1
- **Later phases (researched now, added to catalog at their phase):** Retrofit
  3.0.0 + OkHttp 5.4.0 (P4) · Tink 1.21.0 + **argon2kt 1.6.0** (P6) · Firebase
  BoM 34.14.0 — main modules, `-ktx` artifacts removed in BoM 34+ (P6) ·
  credentials 1.6.0 + googleid 1.2.0 (P6) · Play Billing 9.0.0 (P6; PBL7
  closes to new apps 2026-08-31, so 9.x is mandatory)

**Notable sub-resolutions (all keep-unless-disqualified):**
- **Kotlin 2.4.0 disqualified** (released 2026-06-03; no stable KSP supports it
  — google/ksp#2965 open; Room+Hilt need KSP). Revisit when KSP ships 2.4.
- **AGP 9 built-in Kotlin:** do NOT apply `org.jetbrains.kotlin.android`/kapt
  (hard config error). KSP2 only.
- **Navigation:** keep Navigation Compose 2.9.8 (documented choice, no
  disqualifier). Research note: Navigation 3 is now Google's recommended path
  for new Compose apps — adopting it would be an architecture change requiring
  an ADR; flagged as a candidate v2 checkpoint instead. Founder may override.
- **Argon2 library (P6):** glossary already notes Tink lacks Argon2id;
  signal-argon2 is **archived (2024-04-18) → disqualified**; argon2kt 1.6.0 is
  the maintained, 16KB-page-compliant pick (slow-maintenance; monitor; adjacent
  to encryption boundary → any future swap is founder territory).
- **Orbit 11.0.0** built against Kotlin 2.1/Compose 1.8 → smoke-tested in
  Phase 1 scaffolding (a proof container + orbit-test) before Phase 2 commits
  to it. Orbit 12 breaking major expected late 2026 — alpha-period checkpoint.

**Confidence:** High on matrix mutual-compatibility (verified pairings);
medium on BOM 2026.06.00 mirror availability (fallback pinned).
**Rollback:** versions are catalog-centralized; any pin can be reverted in one
file. Kotlin-2.4 migration debt is logged as an explicit future checkpoint.

---

## #1b — New-dependency surfacings for Phase 1 (per `build-conventions.md` §4/§6, delegated mode)

**Date:** 2026-06-10 · **Type:** Dependency surfacing (surface-in-writing + proceed)

Foundation-named dependencies need no surfacing. These are *additions or
settlements* the docs left open, each minimal and justified:

1. **Kover 0.9.8** — settles `testing-strategy.md` §7's open "JaCoCo or Kover"
   (Kover: first-party JetBrains, Kotlin-native, `koverVerify` backs the
   qualityGate thresholds). Test/build scope only.
2. **JUnit 4.13.2 + androidx.test.ext:junit** — testing-strategy implies but
   never pins; Robolectric's runner is JUnit-4-only, which anchors the choice.
3. **Robolectric 4.16.1** — named in `testing-strategy.md` §3/§10.
4. **ktlint-gradle (jlleitschuh) 14.2.0** — the Gradle vehicle for the named
   ktlint; actively maintained; 14.1+ required for AGP 9 built-in Kotlin.
5. **orbit-test, room-testing, kotlinx-coroutines-test** — first-party test
   companions of already-named libraries.
6. **androidx.core:core-ktx** — baseline Jetpack artifact (edge-to-edge etc.);
   treated as part of ADR 0016's "Kotlin + Jetpack" umbrella.
7. **NOT added (deliberately):** MockK, Turbine (not needed by Phase 1 tests —
   will surface when first needed); Konsist (maintenance-watch per research;
   layering tests are hand-rolled source-scan instead, zero new deps);
   property-based lib ("glados or equivalent" in testing-strategy is a Dart
   residue — Kotlin equivalent decided at Phase 3).

**Rollback:** all are catalog entries; removable individually.

---

## #1c — Local decisions the docs don't specify (surface-in-writing + proceed)

**Date:** 2026-06-10

1. **minSdk 26** (docs name no minSdk; floor from libs is 23). Rationale:
   `java.time` without desugaring for the data model's ISO-8601-everywhere
   convention; Android 8.0 (2017) is far below the "2022 budget device" support
   floor in `testing-strategy.md`. Rollback: lower to 23 + add desugaring.
2. **Room entities carry DDL CHECK semantics in code, not SQLite CHECKs.**
   `data-model.md` §3–7 DDL has CHECK constraints; Room `@Entity` cannot
   declare them. Mapping: closed string sets become Kotlin types/enums at the
   engine/repo layer + payload sealed hierarchy; `data-model.md` §4 itself
   notes type×severity combos are "enforced in engine API at write time, not
   SQLite CHECK". Schema-integrity tests assert rejection at the code
   enforcement locus. Doc's "mechanical translation" reading adjusted; flagged
   for the Phase 1 report. Rollback: add a `RoomDatabase.Callback` running raw
   CREATE with CHECKs (rejected now: duplicates schema, fights Room migration
   verification).
3. **Test source-set mapping** (testing-strategy uses a flat `test/`):
   JVM+Robolectric → `app/src/test/kotlin/ai/cuizine/...` (mirrors main, incl.
   `fixtures/`); instrumented/Compose-on-device → `app/src/androidTest/`.
4. **event_log.profile_id nullable** — data-model §7 DDL says NOT NULL but its
   own comment says "nullable for pre-profile events"; pre-profile events
   (e.g. `constraint_conversation_started` on day 0) exist, so nullable wins
   (chain: doc self-contradiction resolved to the reading that makes the
   specified behavior possible). Doc fix queued for the Phase 1 report.
5. **Kover thresholds activate per high-risk module at its phase** (validator
   P3, encryption/billing P6 — near-100% per testing-strategy §7); no global
   threshold in Phase 1 (scaffolding-only code would make a global number
   meaningless).

---

## #1d — Phase 1 checkpoint: Orbit MVI ergonomics `[data-driven]` — DEFERRED to named moment

**Date:** 2026-06-10 · **Checkpoint:** `docs/roadmap.md` §3 Phase 1, checkpoint 2

Resolvable only "after the first two or three real containers exist" — that
moment lands mid-Phase-2. Logged here; will be resolved with an honest
ergonomics assessment in the Phase 2 report. Phase 1 contributes the smoke
test (proof container on Orbit 11 + Kotlin 2.3/Compose 1.11).

---

## #2a — Phase 2 checkpoint surfacing (kickoff 2026-06-10)

**Checkpoints (`docs/roadmap.md` §3 Phase 2):**
1. **[data-driven] Navigation & conversation-surface bets (ui-ux-spec §12,
   UI1–UI6).** Resolution mode: build the spec's tentative answers (single
   suggestion on Today with regenerate escape hatch; FAB single-tap only; one
   calm ThinkingIndicator line; disclaimers at first conversation + About +
   passphrase context; no Profile search; contextual Today affordance quieter
   than FAB), then tune on-device running Flows A–F on fixtures. Logged
   observations land in the Phase 2 report.
2. **[research-informed] Material 3 / Material You evolution.** RESOLVED —
   research performed in the Phase 1 currency sweep (DECISION-LOG #1a):
   Material3 1.4.0 stable (Expressive graduated), no component deprecations
   affecting the §6 component set, dynamic color + predictive back + edge-to-
   edge all current. KEEP the documented design-token approach. Re-verify only
   if the Compose BOM pin moves.

## #2b — Mock seam design mapping (surface-in-writing + proceed)

**Situation:** `ui-ux-spec.md` §10 says Phase 2 ships "mock containers";
CLAUDE.md §9 mandates interface+fake DI for external dependencies. These
compose: containers are written ONCE against repository/agent interfaces;
Phase 2 binds mock implementations (returning canonical fixtures) via Hilt;
Phases 3–6 swap bindings to real implementations. The screens' State/Intents
contract — the invariant §10 actually protects — never changes; "replacing a
mock container" = rebinding its collaborators, not rewriting the container.
**Why:** identical contract guarantee, less Phase 3–6 churn, and one seam
discipline for both UI mocks and credential fakes. **Cross-refs:** ui-ux-spec
§10; roadmap §3 P2; CLAUDE.md §9; build-conventions §4 (containers hold no
business logic). **Rollback:** mechanical — extract per-screen mock containers
if a real container ever needs to diverge. **Confidence:** high.

---

## #2c — Phase 2 checkpoint: UI1–UI6 navigation/conversation bets `[data-driven]` — RESOLVED (on-device)

**Date:** 2026-06-10 · **Checkpoint:** `docs/roadmap.md` §3 Phase 2, checkpoint 1
**Evidence:** Flows A–F walked on a Pixel 8 AVD against the canonical fixtures
(screenshots in the Phase 2 report); container + render tests green.

- **Four-tabs-plus-FAB model:** holds. The centered FAB reads clearly as "talk
  to Cuizine"; tabs keep their back stacks; conversation-as-overlay (modal
  sheet) returns cleanly to prior context. KEEP.
- **UI6 FAB-vs-contextual redundancy:** both kept — the quiet "Tell Cuizine
  something" text affordance on Today plus the FAB. On-device the two did not
  feel redundant (different altitudes); the Today affordance needed a spacing
  fix to stay clear of the FAB (applied). KEEP, re-check with founder
  walkthrough feedback.
- **UI1 long-press affordance:** not added (tentative answer kept: single-tap
  only).
- **Single-suggestion-on-Today:** holds well on-device — one card, accept /
  not-quite-right / regenerate reads calm, not sparse. KEEP.
- **ThinkingIndicator reveal:** one calm line ("Composing something that fits
  your day…") with a settling pulse — felt right at mock latency (~1.2s);
  re-judge under real agent latency in Phase 5/7.
- **Disclaimer placement:** About section + (Phase 5) first conversation +
  passphrase context — per the tentative answer.
- **Structural notes fed back into `ui-ux-spec.md` (living doc):** §5.5
  consolidation note (sections of one Settings screen at v1 scale; surfaces
  and contracts unchanged) + the share-feedback entry location.

**Confidence:** medium-high (founder's own device walkthrough pending — the
Phase 2 report includes install steps). **Rollback:** every observation is a
cosmetic/packaging call; reverting any is a contained UI change.

---

## #3a — Phase 3 kickoff: spec gaps surfaced + implementation mappings (2026-06-10)

**Checkpoints surfaced (`roadmap.md` §3 Phase 3):** validator 3-retry bound and
active-set cache invalidation are both `[data-driven]` at later named moments
(first real Chef contact in Phase 5; post-hard-cases measurement) — logged,
deferred to those moments. The engine core below implements the spec as
written; none of these items deviates from the validator's specified logic.

**Gaps the spec leaves open, resolved minimal-deviation (all outside the
park-always core, all reversible):**
1. **Solar daily windows need coordinates; v1 location is coarse**
   (`constraint-engine-spec.md` §5 computes sunrise/sunset "from the user's
   location" but no lat/lon exists anywhere in the data model). Resolution: a
   `SolarTimesPort` with a NOAA-algorithm implementation over representative
   coordinates per IANA zone (v1 Canadian market zones); unknown zones fall
   back to a fixed 06:00–18:00 window. Deterministic; injectable for tests.
   Real-location solar is a contained upgrade if alpha needs it.
2. **Phase-bounded scopes reference profile-level phases that no doc defines
   storage for.** Resolution: evaluator accepts an `activePhases` set (empty
   in v1 — no v1 persona uses phases); storage lands when a feature needs it.
3. **Limit windows beyond per-meal need cross-meal history** (none exists
   until Phase 7/v2). Resolution: per_meal enforced exactly; a single meal
   exceeding a full per_day/per_week ceiling is also flagged (sound lower
   bound, no false positives). Require windows: single_meal enforced; daily
   deferred per §3 ("v1 supports single-meal and daily" — daily accounting
   needs history; surfaced honestly here).
4. **M/RC unknown ingredient with NO AI categorization available** (spec
   covers the categorization-succeeded path only): resolved conservatively as
   a violation (Chef regenerates) rather than a silent pass — the
   safety-leaning reading.
5. **Logical-vs-physical enum spelling** (spec §9 hyphens vs `data-model.md`
   underscores): data-model wins for stored strings per the docs' own
   precedence rule; Phase 1 storage values stand.
6. **Quantity normalization for limit sums:** g/kg/mg/ml/l converted;
   unitless ingredients contribute one 100 g basis unit (documented in code;
   refined with real food data in Phase 4).

**Rollback:** each is an isolated function or port; none touches the §7
algorithm's step order or severity rules.

---

## #3b — Hard-cases fleet findings + Phase 3 resolutions (2026-06-10)

The parallel test-authoring pass (4 agents, ~150 tests) reported four
spec-vs-engine discrepancies. Resolutions:

1. **Preference-tier rejection (CONFIRMED ENGINE BUG, fixed):** §4 Tier 4 says
   Preference never causes rejection alone; the validator was rejecting on
   Preference-tier avoid matches. Fixed at Step 5 aggregation (quiet note
   instead). This is a conformance fix TO the spec, not a deviation.
2. **Contextual default expiry (CONFIRMED ENGINE BUG, fixed):** today-only
   flags now expire at end of the user's LOCAL day (§3 Type 5), not now+24h.
3. **§7 Step 4 disclosure letter (fixed):** the AI-fallback disclosure now
   attaches only when the categorized check PASSES.
4. **Daily require windows (FOUNDER-VISIBLE NOTE, not a code change):** §3
   says v1 "supports single-meal and daily" require windows, but §7's own
   validator contract is per-suggestion and stateless — daily accounting
   needs cross-meal history that nothing in v1 produces. Resolved by the
   spec's internal precedence (§7 is the validator's mechanical contract):
   daily-window requires are representable, stored, and pass per-meal checks;
   enforcement begins when meal-history accounting exists (v2/Observer-era).
   Recommend a one-line doc clarification to §3 — flagged in the Phase 3
   report for founder sign-off since it touches validator-adjacent wording.

Also logged: window bounds are inclusive at both ends (spec silent;
documented behavior); weekly scope with empty weekdays is never-active (spec
silent; conservative). **Property-based testing** ("glados or equivalent" —
a Dart residue): deferred to Phase 4 with rationale — 224 deterministic
tests already exceed every §5 category minimum and the enforced coverage
floor; a Kotlin property lib (jqwik / kotest-property) is a new dependency
that earns its keep when food-data fuzzing arrives.

---

## #4a — Phase 4 checkpoint: USDA FDC / Open Food Facts API state `[research-informed]` — RESOLVED (KEEP)

**Date:** 2026-06-10 · **Checkpoint:** `docs/roadmap.md` §3 Phase 4, checkpoint 1
**Evidence:** live-docs research (fdc.nal.usda.gov api-guide/spec, api.data.gov
manual, OFF docs; full detail in the phase4-food-api-currency workflow output).

**Decision: KEEP both sources per ADR 0012 — no disqualifiers.** Build facts
pinned for the clients:
- **USDA FDC:** base `https://api.nal.usda.gov/fdc/v1`; POST `/foods/search`
  (dataType Foundation + SR Legacy for raw ingredients, Foundation ranked
  first), GET `/food/{fdcId}?format=full`. Free api.data.gov key —
  **X-Api-Key header, never the URL** (key out of logs, trust posture);
  1,000 req/h (429 + ~1h block; read X-RateLimit-Remaining). **Two nutrient
  DTO shapes** (flattened in search, nested in detail). Nutrient numbers:
  203 protein / 204 fat / 205 carbs / 291 fiber / 307 sodium / 306 potassium
  / 305 phosphorus; Foundation + SR Legacy are per-100g. No barcode, no
  structured allergens (OFF's job). SR Legacy frozen-but-served (fallback
  for raw-ingredient coverage); noted, not disqualifying.
- **OFF:** no key for reads; REQUIRED User-Agent convention; barcode +
  allergens_tags are its unique value (v1 uses name-search secondary to
  USDA; barcode is v2 Pantry). Detail in the workflow output file.
- The 1,000/h ceiling validates the cache→bundle→API tiering: the live API
  is the last resort by design.
- **AI-fallback accuracy checkpoint [data-driven]:** alpha-deferred as the
  roadmap specifies.

**New deps for this phase (pre-surfaced in #1a, activated now):** Retrofit
3.0.0 (first-party kotlinx converter coordinate), OkHttp 5.4.0;
mockwebserver3 (test scope) when client tests need it.

---

## #4b — Allergen/religious suite findings + Phase 4 resolutions (2026-06-10)

The suite-authoring pass (45 allergen + 17 religious tests) surfaced:

1. **Jain root-vegetable bundle gap (FIXED in-phase):** no bundle entry
   carried `root_vegetable`; Potato, Onion, Garlic, Ginger now do, and the
   gap test was flipped to assert the caught behavior. This is the curated
   bundle doing its ADR 0012 job.
2. **Kosher meat+dairy mixing (FOUNDER-VISIBLE v1 LIMITATION):** kashrut
   separation is a cross-ingredient COMBINATION rule; none of the five
   constraint types (ADR-fixed set) evaluates ingredient combinations — a
   beef+milk meal passes a `non_kosher` avoid today. Documented by an honest
   test. A combination-rule constraint type would be a v2 ADR; flagged in
   the Phase 4 report and worth an open-questions entry in
   `constraint-engine-spec.md` §11 at the next doc pass.
3. **Halal risk-tag layering (Curator guidance, Phase 5):** gelatin is
   `non_halal_risk`, not `non_halal` — a halal household needs the Curator
   to write BOTH avoids (plus `contains_pork`/`contains_alcohol`). Recorded
   for the Phase 5 prompt work.
4. **Fish/egg household-dependence held:** `meat_for_some` /
   `non_vegetarian_for_some` tags correctly require explicit household
   choice rather than blanket categorization.
5. **No species-level halal certification:** correct by design — halal
   status depends on slaughter, not species; the suite never certifies a
   meat as halal-safe.

---

## #2 — CLAUDE.md audit result (first-iteration mandate)

**Date:** 2026-06-10 · **Type:** Process record

Audited `CLAUDE.md` and `README.md` per the founder's first-iteration mandate.
Both were already aligned with delegated mode (commit `7e49c33`): autonomous
checkpoint protocol ✓, bidirectional/ui-ux-spec living-doc discipline ✓,
forbidden behaviors ✓, fake-first credentials ✓, session recovery ✓,
README as doc index ✓. One gap: the explicit **conflict-resolution chain**
(vision > PRD > ADRs > technical-architecture/data-model > conditional docs >
build-conventions > implementation; glossary wins terminology) was not stated —
added to `CLAUDE.md` §8, citing entry #0. **Rollback:** revert the edit.

---
