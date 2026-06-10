# Cuizine — Launch Readiness Checklist

> The doc that turns every exit criterion, every phase completion gate, and every readiness commitment scattered through the other foundation docs into concrete, checkable gates. Where the other docs describe *what* Cuizine is and *how* it is built, this document is the operational instrument the founder picks up at each milestone moment to answer the simple question: "Are we ready?" Under the completion-driven frame from `roadmap.md`, these checklists are the only signal that matters — phases end when their gates are cleared, not when calendars say they should.

## 1. Purpose & how to read this doc

This document specifies the **concrete go/no-go checklists for every major milestone in Cuizine's lifecycle** — from v1 alpha kickoff through v3 global expansion. It assumes you have read all previous foundation docs and the ADRs, especially `roadmap.md` (which defines the phase structure these checklists gate) and `alpha-feedback-and-iteration.md` (which specifies the alpha operational model).

This document defines: the core readiness principles; the v1 alpha kickoff checklist; the v1 alpha exit checklist; the alpha-to-v2 transition completion checklist; the v2 public launch checklist; the v2-to-v3 transition checklist; the v3 launch checklist; and the cross-cutting readiness disciplines that apply at every gate.

This document does **not** define: the actual phase work (that's in `roadmap.md`); the foundation specifications (those are in the respective foundation docs); the test infrastructure (that's in `testing-strategy.md`); or the operational processes during phases (those are in `alpha-feedback-and-iteration.md` for alpha and future docs for v2+).

**When this document and a deeper-dive doc disagree,** the deeper-dive doc wins for the *substance* of a gate — what the constraint engine does, how encryption works, what the tier policy enforces — and this doc wins for *whether a gate is met*. If a checklist item is specified here but not in the doc it references, the specification here is operational guidance that will be folded back into the referenced doc on next revision.

## 2. Core readiness principles

> The disciplines that govern how the checklists are used. Every readiness decision traces back to one of these principles.

### Principle 1: Checklists are necessary but not sufficient

The checklists in this doc specify the *minimum* gates that must be cleared before moving to the next phase. Clearing them does not automatically mean Cuizine is ready — the final gate is always the founder's conviction that the phase has genuinely done its job. A checklist that's mechanically complete but feels wrong is a signal to pause, not to ship. Conversely, a checklist with a single unchecked item does not get overridden by "it feels fine" — the item is either met, waived with explicit reasoning, or blocking.

### Principle 2: Gates are binary, not gradient

Each checklist item is either met or not met. "Mostly done," "working on it," "close enough" are not acceptable states. If an item cannot be cleanly marked as met, it is not met. This principle exists because checklist drift — the gradual slide from "this needs to work" to "this mostly works" — is how products ship with real gaps. The binary discipline prevents the drift.

### Principle 3: Waivers require explicit reasoning

Sometimes a checklist item is genuinely not applicable, or its intent is met through a path the checklist did not anticipate. In those cases, the item can be waived — but only with explicit written reasoning that names why the waiver is appropriate. "Item X waived because the alternative Y satisfies the intent" is acceptable. "Item X waived because it's not important" is not. Waivers are recorded alongside the checklist and preserved for the retrospective review of each phase.

### Principle 4: Safety-floor gates are non-waivable

Gates that protect users from direct harm — the validator's severity-zero handling, the encryption integrity, the billing layer's forbidden-behaviors compliance — cannot be waived under any circumstances. These gates exist because their failure mode is real user harm, and no pragmatic consideration overrides that. If a safety-floor gate is not met, the phase cannot proceed.

### Principle 5: The checklist is a living document

The checklists in this doc are the v1 version. As Cuizine learns from real phase transitions, gaps in the checklist will surface — items that should have been gates but weren't, items that turned out to be redundant, items that were the wrong thing to check. The checklist updates to reflect that learning, with changes committed alongside the retrospective of the phase that surfaced them.

## 3. The v1 alpha kickoff checklist

> The gates that must be cleared before the first alpha user installs the APK. This is the first major milestone in Cuizine's lifecycle and the checklist is deliberately thorough — once alpha users are real, failures have real consequences.

### Foundation and specifications

- [ ] **All foundation docs are complete and consistent with each other.** No contradictions between docs, no open TODOs that block v1 scope, no unresolved cross-references.
- [ ] **All ADRs in `decisions/` are in their correct status** — Accepted, or Superseded where a later ADR replaced them (ADR 0005 superseded by ADR 0016) — and the rationale in each still reflects the current v1 decisions.
- [ ] **The `decisions/README.md` file lists all ADRs with correct titles and status.**
- [ ] **The `docs/README.md` file lists all foundation docs in reading order.**

### Code completeness

- [ ] **All six v1 build phases from `roadmap.md` Section 3 are substantially complete.**
- [ ] **The full Sukhi journey from PRD § 4 works end-to-end in the app** — day 0 onboarding through day 30 cumulative state, exercised manually by the founder multiple times.
- [ ] **The constraint engine API from `constraint-engine-spec.md` Section 6 is fully implemented** with all eleven operations passing their respective unit tests.
- [ ] **The validator's deterministic five-step algorithm from `constraint-engine-spec.md` Section 7 is fully implemented** and exercised by the hard cases test suite.
- [ ] **The three v1 agents (Curator, Chef, Pantry) are implemented** with their full input/output contracts from `agent-architecture.md` Sections 4-6.
- [ ] **The orchestrator's rule-based intent routing from `agent-architecture.md` Section 3 is implemented** and deterministic.
- [ ] **The encryption layer from `local-first-sync.md` Section 3 is implemented** with AES-256-GCM and Argon2id key derivation.
- [ ] **The recovery passphrase generation and confirmation flow from `local-first-sync.md` Section 4 works** end-to-end against a real Firebase project.
- [ ] **The signed-out to signed-in transition from `local-first-sync.md` Section 7 works** with all rollback semantics verified.
- [ ] **The v1 alpha bypass from `monetization-and-billing.md` Section 9 is implemented** and functioning correctly — alpha users see no billing UI, no payment prompts, no subscription flows.
- [ ] **The food data layer from ADR 0012 is functional** with the local cache, the curated bundle loaded, USDA and Open Food Facts clients working, and severity-scoped AI fallback operating correctly.
- [ ] **The data model from `data-model.md` is fully implemented** with all tables, indices, the migrator module with empty registry, and the test-only migrations exercised.

### Test completeness

- [ ] **The hard cases test suite from `testing-strategy.md` Section 5 is passing** — all seven categories, minimum target test counts per category met.
- [ ] **The validator's exhaustive test suite from `testing-strategy.md` Section 7 is passing** with near-100% line coverage on the validator module.
- [ ] **The encryption round-trip tests from `testing-strategy.md` Section 8 are passing** — all fifteen-plus tests including wrong-passphrase-fails-closed, tampered-ciphertext-fails-closed, IV uniqueness.
- [ ] **The recovery passphrase tests from `testing-strategy.md` Section 8 are passing** — all twelve-plus tests including the 30-second pause after failed attempts.
- [ ] **The transition flow tests from `testing-strategy.md` Section 8 are passing** — all thirty-plus tests covering every step's failure mode with rollback verification.
- [ ] **The schema migration tests from `testing-strategy.md` Section 9 are passing** — all seven empty-registry tests, with the synthetic test-only migrations exercising the migrator code path.
- [ ] **The integration tests for Sukhi's journeys from `testing-strategy.md` Section 10 are passing** — day 0, day 4, day 5, day 8, day 30.
- [ ] **The automated agent eval scenarios from `testing-strategy.md` Section 6 are passing** for the safety-floor cases (peanut/tree nut/sesame detection, religious dietary violation detection, inviolable constraint enforcement).
- [ ] **The full test suite runs in under five minutes** for the fast tests (unit and integration combined).
- [ ] **The linter and static analysis pass with zero warnings.** ktlint and Android Lint clean.
- [ ] **The format check passes.** `ktlint --format` produces no changes (code is already formatted).

### Operational readiness

- [ ] **Firebase project is configured** with the correct security rules from `local-first-sync.md` Section 5, Firestore in production mode, Authentication enabled for Google Sign-In.
- [ ] **Cloud Functions proxy is deployed and tested** with the three LLM provider integrations (Anthropic, Google, OpenAI) functioning per ADR 0006.
- [ ] **API keys for the LLM providers are stored in Firebase secure environment variables**, not in the APK.
- [ ] **The APK build pipeline works end-to-end** producing a signed APK that installs correctly on a test device.
- [ ] **The APK is signed with a production signing key** stored securely (not checked into the repo).
- [ ] **Firebase project quotas are verified** to handle the alpha user count without hitting limits (15-25 users per ADR 0001).

### Alpha user preparation

- [ ] **The initial alpha user cohort is identified** — 15-25 users per ADR 0001, with a mix covering Sukhi-like primary persona cases plus a few secondary personas.
- [ ] **Each alpha user has agreed to participate** with understanding of the alpha scope and the founder's direct access.
- [ ] **The alpha onboarding materials are ready** — the APK distribution method, the first-time-setup walkthrough, the founder contact information.
- [ ] **The `scripts/alpha/grant_bypass.kts` script works** to set the `v1_alpha_bypass` flag for each alpha user after account creation.

### Security and trust

- [ ] **The `vision.md` trust posture commitments are honored** in the implemented code — no master key, no escrow, no admin override, no content telemetry.
- [ ] **The forbidden behaviors lists in `local-first-sync.md` Section 10, `monetization-and-billing.md` Section 11, and `build-conventions.md` Section 11 are verified clean** — code review confirms none of the forbidden behaviors are present.
- [ ] **The threat model from `security-and-privacy.md` Section 3 is reviewed** one final time against the actual implemented code.
- [ ] **The in-product disclaimers from `security-and-privacy.md` Principle 2 are present** in the constraint conversation, Settings → About, and the recovery passphrase reveal screen.

### Founder conviction

- [ ] **The founder has personally walked through the Sukhi journey** on a real device, as a real user would, and the product feels right.
- [ ] **The founder has read the hard cases test suite output** and is confident in the validator's correctness.
- [ ] **The founder is ready to be in direct contact with alpha users** as the primary feedback and support channel during the alpha period.
- [ ] **There are no "I'm worried about this" items** that the checklist missed. If there are, they are added to the checklist and addressed before proceeding.

**Alpha kickoff proceeds only when every item above is marked as met or explicitly waived with reasoning.** The founder's final confirmation: "We are ready for real users."

## 4. The v1 alpha exit checklist

> The gates that must be met before the alpha-to-v2 transition begins. These are the operationalization of the alpha exit criteria from `roadmap.md` Section 4.

### Learning completion

- [ ] **The constraint engine has been exercised against the full persona set in real use.** Sukhi (primary), Aisha (temporal scope), Marcus (caregiver) each have at least one real alpha user whose constraint graph shape matches the persona, and the engine has handled it correctly.
- [ ] **Severity-zero events have reached zero for a sustained period of active use.** No safety-floor violations reported by alpha users or surfaced by event log review across a meaningful stretch of continuous use by multiple users. The specific duration is founder judgment — long enough to be confident, not padded for comfort.
- [ ] **The calm-precise-warm tone has been validated** by at least several alpha users across diverse cultural contexts. Tone feedback is consistently positive, with any tone issues addressed via prompt iteration.
- [ ] **Foundation doc updates have stabilized.** Updates have slowed to minor refinements only for a sustained stretch of time — no substantive architectural changes emerging from alpha feedback.
- [ ] **The founder has conviction to move to v2.** This is the subjective gate, named honestly.

### Operational completeness

- [ ] **All alpha feedback captured via event log export has been reviewed** and categorized into the three buckets from `alpha-feedback-and-iteration.md` Section 4 (prompt refinements, foundation doc updates, code fixes).
- [ ] **All actionable feedback has been addressed** — either implemented, explicitly deferred with reasoning, or rejected with reasoning.
- [ ] **The alpha learning log** (per `alpha-feedback-and-iteration.md` Section 9's open question) captures the key learnings and is ready to inform the foundation retrospective.

### Quality signals

- [ ] **The hard cases test suite has grown** through the alpha based on real-world cases that the test suite did not anticipate. Every severity-zero event (if any occurred) has a new test case that exercises the specific failure mode.
- [ ] **Prompt iterations have stabilized.** The prompt files in `prompts/` are at a version the founder considers the alpha-ending baseline.
- [ ] **No critical bugs are open.** Any known bugs are either fixed, explicitly deferred to v2 scope (with reasoning in the roadmap), or marked as won't-fix (with reasoning).

**Alpha exit proceeds only when every item above is marked as met or explicitly waived.** The founder's final confirmation: "The alpha has taught us what it was supposed to teach us. We are ready to begin the v2 transition."

## 5. The alpha-to-v2 transition completion checklist

> The gates that must be cleared before v2 build begins in earnest. These operationalize the five transition streams from `roadmap.md` Section 5.

### Stream 1: Alpha bypass removal

- [ ] **The v2 migration is written** that sets `v1_alpha_bypass = 0` for every user, sets `current_tier = 'free'`, unlocks trial eligibility, and logs the transition event.
- [ ] **The migration is tested** against realistic alpha user data, including edge cases (users who never signed in, users with incomplete onboarding).
- [ ] **The warm transition message** per `monetization-and-billing.md` Section 9 is drafted and ready to be shown to alpha users on their first v2 app open.
- [ ] **The migration is staged for deployment** in the v2 launch release pipeline.

### Stream 2: Foundation doc retrospective

- [ ] **Every foundation doc has been reviewed** in light of alpha learnings, with updates committed or explicitly decided not to make.
- [ ] **Doc updates that accumulated during the alpha have been consolidated** — no inconsistencies between docs, no stale references.
- [ ] **Cross-references between docs are verified.** Every "see X" reference points to an existing doc and section.
- [ ] **The `docs/README.md` and `decisions/README.md` files are current.**
- [ ] **The retrospective itself is documented** — what the alpha taught us, what surprised us, what the foundation got right, where it needed revision.

### Stream 3: ADR writing for deferred decisions

- [ ] **ADR 0013 (v3 cross-account peer coordination security model) is written** per `security-and-privacy.md` Section 8's working specification, formalized into the standard ADR format.
- [ ] **ADR 0014 (v2 pricing finalization) is written** with specific numbers from the lower band in `monetization-and-billing.md` Section 3, informed by alpha cost data and market research.
- [ ] **Any additional ADRs for alpha-surfaced decisions are written** — specifically for items in the open questions sections of various foundation docs that alpha data has now resolved.
- [ ] **All new ADRs are in `Accepted` status** with `decisions/README.md` updated.

### Stream 4: v2 scope lock-in

- [ ] **The v2 scope is explicitly decided** and matches `roadmap.md` Section 6's nine areas.
- [ ] **Any alpha-surfaced scope adjustments are incorporated** and documented in the roadmap's v2 section.
- [ ] **Stretch items (nice-to-have for v2 but not blocking) are identified separately** from required items.
- [ ] **Safety-valve items from `roadmap.md` Section 10 are acknowledged** — Planner, Sourcing, and multi-profile households do NOT strictly block v2 launch if they encounter unexpected complexity.

### Stream 5: Launch preparation groundwork begins

- [ ] **Google Play Developer account is registered** and in good standing.
- [ ] **Firebase project upgrade path from Spark to Blaze is planned** with cost projections reviewed.
- [ ] **Privacy policy drafting has started** with legal counsel, using `security-and-privacy.md` as input.
- [ ] **Terms of Service drafting has started** with legal counsel.
- [ ] **Marketing asset preparation has started** — the `cuizine.ai` website content, the launch announcement draft, the positioning copy in the calm-precise-warm voice.
- [ ] **The formal security disclosure program infrastructure is being set up** — `security@cuizine.ai` email, disclosure policy page on `cuizine.ai`.
- [ ] **The Instacart API developer relationship is initiated** if ADR 0003's Instacart integration is in v2 scope (the API approval process can take time).

**Transition completion proceeds when every item above is met or explicitly waived.** The founder's final confirmation: "The transition has closed out the alpha and set up v2 correctly. We are ready to begin the v2 build in earnest."

## 6. The v2 public launch checklist

> The gates that must be cleared before the Play Store listing goes live. Per the full public launch commitment from `roadmap.md` Section 6, this is where Cuizine becomes available to anyone in North America who downloads it.

### v2 scope completeness

- [ ] **Area 1 (full billing layer) is complete** — Google Play product IDs created, `BillingService` tested against real Google Play responses, the 14-day trial flow with day 7 mid-trial offer working, tier enforcement active on every relevant operation, upgrade/downgrade flows tested with real Google Play events, content portability verified on real user data.
- [ ] **Area 2 (full Pantry agent) is complete** — barcode scanning, receipt scanning, fridge photo ingestion all working, camera permissions handled, the v2 Pantry agent's full reasoning functioning.
- [ ] **Area 3 (Planner agent) is complete or explicitly deferred** per the safety valve — if deferred, the Cuizine tier ships without week-ahead planning, with clear documentation of when it will arrive.
- [ ] **Area 4 (Sourcing agent) is complete or explicitly deferred** per the safety valve — if deferred, the Cuizine tier ships without Instacart integration.
- [ ] **Area 5 (multi-profile household features) is complete or explicitly deferred** per the safety valve — if deferred, Cuizine Family tier ships in a point release after v2.
- [ ] **The v2 Pantry agent's multimodal AI capability is integrated** into the `ModelProvider` interface, since v1 only exercised text-based inference.

### Testing completeness for v2

- [ ] **All v1 test suites continue to pass** — hard cases, validator exhaustive, encryption round-trip, recovery, transitions, schema migration, integration journeys, automated agent evals.
- [ ] **New tests for v2 features are in place** — billing layer tests, Pantry agent tests including multimodal cases, Planner agent tests (if in scope), Sourcing agent tests (if in scope), multi-profile household tests (if in scope).
- [ ] **The agent eval harness has grown** to include v2 agent scenarios.
- [ ] **Performance tests pass the v1 budgets** and any new v2 budgets (e.g., Planner's longer reasoning latency).
- [ ] **The v1 → v2 migration (alpha bypass removal) is tested** with realistic data.

### Play Store readiness

- [ ] **The app's Play Store listing is complete** — title, description, screenshots, promotional graphics, content rating, target audience, privacy-related data disclosures.
- [ ] **The privacy policy is published** at `cuizine.ai/privacy` and is legally sound per counsel review.
- [ ] **The terms of service are published** at `cuizine.ai/terms` and are legally sound per counsel review.
- [ ] **The app is submitted for Google Play review** and has passed.
- [ ] **Content rating questionnaire is completed accurately** — no clinical claims, no medical device framing per `security-and-privacy.md` Principle 2.
- [ ] **The app's permission requests are minimal and justified** — camera for v2 Pantry, nothing else without explicit feature need.
- [ ] **App signing configuration is production-ready** with the signing key secured.

### Operational readiness for public scale

- [ ] **Firebase project is on the Blaze plan** with cost budgets set and monitoring configured.
- [ ] **Cloud Function proxy scaling is verified** — the function handles the anticipated v2 load without errors.
- [ ] **Firestore security rules are reviewed** for v2 scale and tested against the anticipated multi-user patterns.
- [ ] **Backend monitoring is operational** — operational telemetry only, no content telemetry, per ADR 0011.
- [ ] **Error budgets and alerting are configured** — severity-zero-like issues trigger immediate alerts to the founder.
- [ ] **The support channel is established** — email-based for v2, with response time expectations published.

### Security and trust posture for public launch

- [ ] **The formal security disclosure program is active** — `security@cuizine.ai` email monitored, disclosure policy published on `cuizine.ai`, response timelines committed to.
- [ ] **The incident response process from `security-and-privacy.md` Section 10 is operational** with the stages defined.
- [ ] **A tabletop rehearsal of the incident response process has been conducted** per `security-and-privacy.md` Section 12's open question — gaps identified and addressed.
- [ ] **The threat model from `security-and-privacy.md` Section 3 has been re-reviewed** in light of v2 scale.
- [ ] **The residual risks from `security-and-privacy.md` Section 5 are honestly communicated** to users through the product and privacy policy.

### Marketing and positioning

- [ ] **The `cuizine.ai` website is live** with the landing page, pricing, privacy posture, FAQ.
- [ ] **The launch announcement is ready** — blog post on `cuizine.ai`, initial outreach channels identified.
- [ ] **The positioning copy follows `vision.md` voice and `security-and-privacy.md` Principle 2's disclaimers.**
- [ ] **No dark patterns** are present in the marketing materials, per `monetization-and-billing.md` Principle 3.

### Regulatory and legal

- [ ] **PIPEDA compliance is verified** per `security-and-privacy.md` Section 7 — all ten principles addressed by the implemented architecture.
- [ ] **US state privacy law compliance is verified** — CCPA, VCDPA, CPA rights (right to know, delete, opt out of sale, non-discrimination) are operationalized.
- [ ] **The not-HIPAA disclaimer is prominent** in the product and legal documents per `security-and-privacy.md` Principle 2.

### Alpha user transition

- [ ] **All alpha users have received the transition message** per `alpha-feedback-and-iteration.md` Section 7.
- [ ] **Alpha users' data is verified to be preserved** across the alpha-to-v2 migration.
- [ ] **Alpha users know they'll be moved to free tier with trial eligibility** and have had the chance to ask questions.

### Founder conviction

- [ ] **The founder has reviewed the final v2 build** as if a new user, on a real device, multiple times.
- [ ] **The founder has the conviction that Cuizine is ready to serve the general public** — not just ready to launch, but ready to be accountable for the product.
- [ ] **There are no "I'm worried about this" items** that the checklist missed.

**v2 public launch proceeds only when every item above is marked as met or explicitly waived.** The founder's final confirmation: "We are ready to be publicly accountable for Cuizine."

## 7. The v2-to-v3 transition checklist

> The gates that must be met before v3 planning begins. These are forward-looking and more speculative than the v1 and v2 checklists.

### v2 stability signals

- [ ] **The foundation has stabilized against real-world use** — periodic foundation reviews are producing only minor updates consistently.
- [ ] **The Planner and Sourcing agents have been used enough to validate their designs** or identify specific gaps that v3 planning should address. Agent eval harness data and user feedback both inform this.
- [ ] **The Family tier has enough real users to inform v3's linked partner feature design** — real household dynamics data replaces the pure reasoning in ADR 0013.
- [ ] **v2 is producing sustainable revenue** at a rate that supports the investment in v3.

### Readiness for v3 scope

- [ ] **The founder has capacity (or has hired the capacity) to take on v3 scope** — geographic expansion, new agents, BYOK.
- [ ] **ADR 0013 is reviewed** against real v2 Family tier data and updated if the reasoning needs revision.
- [ ] **Legal counsel is engaged for UK/EU jurisdictional compliance** and the GDPR DPIA scope is understood.
- [ ] **Google Play availability in the target v3 markets is confirmed** — any country-specific Play Console requirements for the new geographies are understood and met.

### Open question resolution

- [ ] **Open questions in `roadmap.md` Section 11 related to v3** have been reviewed and either resolved or explicitly deferred to v3 planning.
- [ ] **Open questions in `security-and-privacy.md` Section 12 related to v3** (linked partner mechanics, GDPR DPIA timing, international data transfers) are reviewed.
- [ ] **Open questions in `monetization-and-billing.md` Section 12 related to v3** (BYOK pricing, the existing-users-keep-original-price sustainability at scale) are reviewed.

**v2-to-v3 transition proceeds when every item above is met or explicitly waived.** The founder's final confirmation: "v2 has done its job. We are ready to begin v3 planning."

## 8. The v3 launch checklist

> Forward-looking. The specific items will refine during v2-to-v3 transition and v3 build based on real v2 learnings. This section specifies the shape of what v3 launch readiness will look like, knowing that details will change.

### v3 scope completeness

- [ ] **The Observer agent is complete** with its long-context reasoning, its scheduled-run architecture, and its privacy-respecting data access.
- [ ] **The linked partner feature is complete** per ADR 0013 (formalized from `security-and-privacy.md` Section 8) — shared view schema, per-link encryption, Firestore subcollection structure, revocation flow, user consent UX.
- [ ] **BYOK is complete** — the configuration UI, the `ModelProvider` reconfiguration, the reduced pricing tier, the signed-out × paid quadrant from ADR 0011 is genuinely reachable.
- [ ] **Configurable agent personas are complete** if in v3 scope — user-selectable tones for Chef, Curator, and overall app voice.
- [ ] **Google Play distribution in the new geographies is live** — localized Play Store listings, per-region content ratings, regional pricing, and any data-residency configuration are complete. (No second platform; the same native Android app in more countries, per ADR 0015.)

### Global expansion readiness

- [ ] **UK and EU markets are supported** — localization, jurisdictional compliance, regional Firestore regions if needed.
- [ ] **The GDPR DPIA is complete** and signed off per `security-and-privacy.md` Section 7.
- [ ] **A Data Protection Officer is designated if required** by GDPR at v3 user volume.
- [ ] **Standard Contractual Clauses (or equivalent) are in place** for international data transfers.
- [ ] **The privacy policy is updated** for v3 jurisdictions with counsel review.

### Trust posture maintenance

- [ ] **All v1 and v2 commitments from `security-and-privacy.md` Section 11 remain true** — no erosion of the trust posture during v3 expansion.
- [ ] **The meta-commitment from `security-and-privacy.md` Section 11** is visibly honored — commitments have grown or stayed stable, limitations have been named honestly where new ones emerged.

### Operational readiness at v3 scale

- [ ] **Multi-region backend infrastructure is tested** at anticipated v3 load.
- [ ] **The support channel has scaled** — may require more than email-based support at v3 user volumes.
- [ ] **The formal bug bounty program is considered** if disclosure program experience warrants (per `security-and-privacy.md` Section 6's open questions).

**v3 launch proceeds when every item above is met or explicitly waived.** The founder's final confirmation: "Cuizine is ready to serve users globally, on both platforms, with the full feature set the architecture has been preparing for since v1."

## 9. Cross-cutting readiness disciplines

> The commitments that apply to every gate, regardless of which launch milestone is in view.

### Checklist review cadence

Each checklist is reviewed at three moments relative to its gate:

1. **Start of the phase that leads to the gate.** The checklist is read as a framing reference — "here's what we'll need to do to clear this gate." This sets the phase's implicit priorities.
2. **Mid-phase review.** The checklist is read to identify which items are close to being met, which are at risk, and which have not been started. This informs phase adjustment.
3. **Gate review.** The checklist is worked through item by item at the actual moment of the decision. Every item is marked as met, waived with reasoning, or blocking.

The rhythm is driven by phase completion, not by calendar — per `roadmap.md` Principle 3.

### Who clears the gates

For v1 alpha kickoff and v1 alpha exit: the founder alone.

For v2 public launch: the founder plus legal counsel (for the privacy policy, terms of service, and regulatory compliance items).

For v3 launch: the founder plus legal counsel plus any additional team members in the operational and engineering roles that v3 may have hired.

The founder is always the final gate. No combination of checklist completion and third-party approval replaces the founder's conviction.

### Waiver documentation

Waivers follow a standard format:

```
Item: [checklist item text]
Status: Waived
Date: [date of waiver decision]
Reasoning: [why the waiver is appropriate]
Alternative path (if any): [how the intent is met through a different mechanism]
Revisit trigger (if any): [what would cause this waiver to be revisited]
```

Waivers are committed to the repository alongside the retrospective for the phase that waived them. Waivers are not retroactively removed — they are preserved for learning.

### Post-gate retrospective

After each gate is cleared (or not cleared, in the case of a delayed phase), a short retrospective is conducted:

- What did the checklist correctly predict would matter?
- What did the checklist miss that turned out to matter?
- What items were overweighted relative to their actual impact?
- What items were underweighted?
- What new items should be added to future versions of the checklist?

The retrospective is documented in a brief write-up that lives alongside the foundation docs. This is the mechanism that makes the checklist a living document per Principle 5.

### The "something feels wrong" override

The final discipline: if a gate is about to be cleared and something feels wrong to the founder, even without a specific checklist item that's unmet, the founder can pause. The checklist is the floor, not the ceiling. "This feels unfinished" is a valid reason to delay even when the checklist is mechanically complete. Trust the conviction.

Conversely, if the checklist is clear and the founder's conviction is strong, the gate clears. Artificial delays — for comfort, for perfection, for "one more iteration" — are the failure mode the completion-driven frame is designed to prevent. The conviction is the signal to proceed, not a reason to keep checking.

## 10. Open questions

### Checklist scope

- **Should the v1 alpha kickoff checklist include any items related to the initial alpha user cohort's diversity?** Currently it just says "a mix covering Sukhi-like primary persona cases plus a few secondary personas." Probably adequate for v1 scale, but worth refining if diverse representation is an explicit goal.
- **Should there be a mid-alpha checkpoint checklist** between kickoff and exit? Currently no — the exit checklist is the only alpha-period gate. Probably adequate for v1 scale because the alpha is small and the founder has direct contact with users. Revisit if alpha scale grows.

### Waiver governance

- **Are there items that should be marked as absolutely non-waivable** beyond the safety-floor gates from Principle 4? Perhaps the PIPEDA compliance items, the privacy policy publication, the disclaimer prominence. Probably yes, and a future revision of this doc could elevate them to non-waivable status explicitly.
- **Who can approve a waiver when it's a solo-founder project?** Currently the founder waives their own items. This is fine at v1 scale; at v2/v3 scale with team members, some waivers might benefit from a second pair of eyes. Revisit as Cuizine grows.

### Retrospective capture

- **Where do the post-gate retrospectives live?** Currently "alongside the foundation docs" but more specifically, they probably deserve their own folder — maybe `retrospectives/` at the repo root. To be formalized when the first retrospective is written (after v1 alpha kickoff).
- **Should retrospectives be public?** Some organizations publish their post-mortems and launch retrospectives as trust-building artifacts. Probably too early to decide; revisit after v2 launch.

### Checklist maintenance

- **How often is this document itself reviewed?** Per Principle 5 the checklist is a living document, but the specific rhythm isn't specified. Probably reviewed after each gate retrospective, with accumulated changes committed.
- **Should checklist item numbers be stable across revisions** so waivers and retrospectives can reference them consistently? Probably yes for the main items; additions get appended rather than inserted to preserve stable numbering.

## 11. Cross-references

### What this document references

- `vision.md` — for the trust posture that Section 3's security gates verify
- `PRD.md` — for Sukhi's journey that Section 3 verifies works end-to-end
- `technical-architecture.md` — for the subsystem decomposition that the gates verify
- `constraint-engine-spec.md` — for the engine API and validator that Section 3 verifies complete
- `agent-architecture.md` — for the agent contracts that Section 3 verifies implemented
- `data-model.md` — for the schema and migrator that Section 3 verifies
- `local-first-sync.md` — for the encryption, recovery, and transition flows
- `monetization-and-billing.md` — for the billing layer and alpha bypass
- `build-conventions.md` — for the test bar and forbidden behaviors
- `testing-strategy.md` — for the test suites that the gates verify are passing
- `security-and-privacy.md` — for the trust commitments, the disclosure program, the threat model
- `roadmap.md` — for the phase structure that these checklists gate
- `alpha-feedback-and-iteration.md` — for the alpha operational model
- All ADRs in `decisions/` — verified as still accurate (or correctly superseded) during v1 alpha kickoff
- Future ADRs (0013, 0014, and others) — verified as written and accepted during alpha-to-v2 transition

### What this document defers to deeper-dive docs

- The substance of what the gates verify (the foundation docs themselves)
- The specific test case lists (`testing-strategy.md`)
- The specific operational processes during phases (`alpha-feedback-and-iteration.md`, future v2 operational docs)
- The specific legal document content (counsel-drafted Privacy Policy, Terms of Service)
- The specific marketing content (external marketing documents not in the foundation set)

### What this document does *not* defer (decisions made here)

- The five core readiness principles (Section 2)
- The v1 alpha kickoff checklist structure and items (Section 3)
- The v1 alpha exit checklist structure and items (Section 4)
- The alpha-to-v2 transition completion checklist organized by the five transition streams (Section 5)
- The v2 public launch checklist organized by scope, testing, Play Store, operational, security, marketing, regulatory, and alpha transition areas (Section 6)
- The v2-to-v3 transition checklist (Section 7)
- The v3 launch checklist shape, acknowledging it will refine (Section 8)
- The cross-cutting disciplines — review cadence, who clears gates, waiver documentation, retrospectives, and the "something feels wrong" override (Section 9)

### How the agent should use this doc

The coding agent uses this doc primarily as a completion signal during build. When the agent is working on a phase from `roadmap.md`, it can reference the corresponding checklist in this doc to understand what "done" looks like for that phase. When the agent surfaces a question about whether a feature is complete enough to commit, the checklist items are the specific reference for answering.

The agent does not clear gates on its own. The gates are cleared by the founder — but the agent's work product is what the founder reviews when clearing them. A well-written checklist gives the agent clear direction about what the founder will check, which focuses the agent's work on the things that actually matter.

When the agent encounters a checklist item that seems impossible to meet as specified (maybe the architecture changed during build and the item doesn't apply anymore), the agent surfaces the discrepancy per `build-conventions.md` Section 6. Either the item is updated, a waiver is prepared, or the implementation is adjusted to meet the item as specified.

---

*End of `launch-readiness-checklist.md` v1 (initial draft). Next revision will incorporate learnings from the first gate clearings — v1 alpha kickoff first, then v1 alpha exit, then the v2 launch. Each gate clearing produces a retrospective that refines the checklist. The principles in Section 2 are stable; the specific checklist items in Sections 3-8 are expected to evolve as Cuizine's actual phase transitions teach us which items matter and which don't.*
