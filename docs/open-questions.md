# Cuizine — Consolidated Open Questions

> The single living document that consolidates every open question from every foundation doc's Open Questions section. When a question resolves, it's migrated to an ADR (if architectural) or simply removed from this document with a note in the relevant foundation doc. When a new question surfaces during build, alpha, or any subsequent phase, it is added here with a pointer to where it originated. This doc is the discipline that prevents open questions from getting lost in individual docs and accumulating silently as unresolved debt.

## 1. Purpose & how to read this doc

This document is the **consolidated inventory of unresolved questions** across Cuizine's foundation set. It assumes you have read all the other foundation docs and the ADRs, and that you need a single place to see what remains to be decided.

Questions are organized by domain (architecture, monetization, testing, security, operations, etc.) and within each domain they're grouped by urgency — questions that need resolution before a specific milestone, questions that are deferred but tracked, and questions that are intentionally left open as policy.

Each entry includes: the question in plain language, where it originated (which foundation doc section), any tentative answer that has been proposed, and the trigger that would force resolution (a specific milestone, a specific data signal, a specific event).

This document does **not** define: new questions that haven't been raised in any foundation doc (this is a consolidation doc, not a brainstorming doc); the answers to questions (answers go in ADRs or back into the foundation doc that raised the question); or questions that have been resolved (those are removed when their resolution lands).

**When this document and a foundation doc disagree** about whether a question is open or resolved, the foundation doc wins and this doc is updated. When a question appears in both this doc and a foundation doc's Open Questions section, both entries stay in sync — this doc refers back to the source and the source is the authoritative place to update.

## 2. How this document is used

### During the v1 build

The coding agent refers to this doc when it encounters a decision that isn't specified in the foundation. Before making a speculative choice, the agent checks whether the decision is already in the open-questions list (in which case it's a deferred decision the founder has already thought about) or whether it's a new question (in which case it gets surfaced per `build-conventions.md` Section 6 and, if confirmed as architectural, added here).

### During alpha and beyond

The founder reviews this doc at key transition moments — before alpha kickoff, before v2 planning, before v3 planning — to see which open questions are being forced to resolution by the upcoming phase and which remain tractably deferred. A question that has an approaching trigger gets prioritized; a question whose trigger is still far off can wait.

### Living document discipline

This doc grows when new questions emerge and shrinks when questions are resolved. Both directions are normal. A question being open is not a failure — it's an honest acknowledgment that Cuizine doesn't know everything yet and that specific unknowns have specific resolution paths. Silent accumulation of unresolved questions is the failure mode this doc prevents.

## 3. Open questions by domain

### 3.1 Architecture and engine

**Question A1: Should we adopt a monorepo structure with separate packages for the engine, agents, and UI?**
- Origin: `build-conventions.md` Section 12
- Current answer: Tentatively no for v1 (single package)
- Tentative resolution trigger: Revisit during v2 build if codebase growth warrants

**Question A2: Should there be a separate `examples/` folder for code samples that exercise the engine in isolation?**
- Origin: `build-conventions.md` Section 12
- Current answer: Tentatively no for v1
- Tentative resolution trigger: Possibly yes if alpha generates community interest

**Question A3: How should shared state across Orbit MVI containers be handled?**
- Origin: `build-conventions.md` Section 12
- Current answer: State shared across screens lives in a repository exposed as a `Flow`; each container collects it rather than containers sharing state directly
- Tentative resolution trigger: Confirm the pattern holds up during build

**Question A4: How granular should Orbit MVI containers be?**
- Origin: `build-conventions.md` Section 12
- Current answer: Per-feature-area, with the specific line fuzzy
- Tentative resolution trigger: Iterate during alpha based on what feels maintainable

### 3.2 Data model

**Question D1: How should the food data cache's personal entries be exported vs the cache as a whole?**
- Origin: `data-model.md`, `monetization-and-billing.md` Section 8
- Current answer: Export includes only user-verified and AI-derived cache entries; bundle entries are excluded (they ship with the app)
- Tentative resolution trigger: Already resolved in `monetization-and-billing.md`; kept here for reference

**Question D2: What's the right retention policy for the event log?**
- Origin: `data-model.md` Section 7
- Current answer: Indefinite on-device retention; user-triggered cleanup available via Settings
- Tentative resolution trigger: Revisit if storage becomes a concern at v2+ scale

### 3.3 Constraint engine specifics

**Question C1: How does the engine handle profiles that age out of a dependent profile relationship?**
- Origin: ADR 0004 consequences
- Current answer: The graduating profile's constraints stay intact; the dependent relationship transitions to an adult relationship
- Tentative resolution trigger: v2 design work on multi-profile households

**Question C2: When a linked partner (v3) revokes the link, what happens to meal history that was generated against the shared view?**
- Origin: `security-and-privacy.md` Section 8
- Current answer: Historical suggestions stay in each partner's own history; the link revocation affects future planning only
- Tentative resolution trigger: v3 build

**Question C3: Should contextual constraints be able to activate preferences as well as avoids?**
- Origin: `constraint-engine-spec.md` Section 3
- Current answer: Yes in principle; worth confirming with concrete alpha examples
- Tentative resolution trigger: Alpha usage patterns

### 3.4 Agent layer

**Question AG1: What's the right way to evolve the Pantry agent from v1 lightweight to v2 full?**
- Origin: `technical-architecture.md` Section 7.5
- Current answer: Bundled v2 release including barcode, receipt, and fridge photo capabilities — not piecemeal
- Tentative resolution trigger: v2 build planning

**Question AG2: Should Cuizine support user-customizable agent personas in v3?**
- Origin: `agent-architecture.md` Section 10
- Current answer: Yes, in v3; paid-tier only
- Tentative resolution trigger: v3 planning and market research

**Question AG3: How does the Observer agent surface its findings to users?**
- Origin: `agent-architecture.md` Section 10
- Current answer: Unresolved. Could be a dedicated insights tab, could be inline commentary in the suggestion flow, could be proactive messages (though push notifications are forbidden so this would need a different mechanism)
- Tentative resolution trigger: v3 design work

### 3.4a UI/UX

**Question UI1: Does the conversation FAB need a long-press or secondary affordance, or is single-tap-opens-conversation enough?**
- Origin: `ui-ux-spec.md` Section 12
- Current answer: Tentatively single-tap only, for calm and simplicity; revisit if in-build use suggests the "intents speed-dial" shortcut earns its complexity
- Tentative resolution trigger: Phase 2 UI build, on-device feel

**Question UI2: How much specialization should the ThinkingIndicator reveal?**
- Origin: `ui-ux-spec.md` Section 12
- Current answer: A single calm line ("considering your constraints…") that surfaces depth without exposing the agent roster; tuned in-build
- Tentative resolution trigger: Phase 5 (real routing wired in)

**Question UI3: Should Today show a single suggestion or a small set to choose from?**
- Origin: `ui-ux-spec.md` Section 12
- Current answer: Tentatively single (calmer, more decisive), with regenerate as the escape hatch
- Tentative resolution trigger: Alpha feedback

**Question UI4: Where exactly does the not-a-medical-provider disclaimer appear, and how often?**
- Origin: `ui-ux-spec.md` Section 12, `security-and-privacy.md` Principle 2
- Current answer: Tentatively the first constraint conversation, the About surface, and the passphrase context — honest without anxious repetition
- Tentative resolution trigger: Phase 2 UI build / alpha feedback

**Question UI5: Does the Profile surface need search/filter once a user has many constraints?**
- Origin: `ui-ux-spec.md` Section 12
- Current answer: Probably not in v1 (low constraint counts); the surface should not preclude adding it
- Tentative resolution trigger: Alpha usage patterns

**Question UI6: How is the "tell Cuizine something" contextual affordance on Today visually distinguished from the FAB so they don't feel redundant?**
- Origin: `ui-ux-spec.md` Section 12
- Current answer: Tentatively the FAB is the always-present front door and the contextual affordance is a quieter inline entry; tune in-build
- Tentative resolution trigger: Phase 2 UI build

### 3.5 Monetization and billing

**Question M1: What's the right launch price within the lower band ($5-7 USD/month for Cuizine, $10-14 for Cuizine Family)?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: Lower band committed; specific number decided during v2 launch prep based on cost projections from real alpha usage
- Tentative resolution trigger: Alpha-to-v2 transition (ADR 0014 will formalize)

**Question M2: Should Cuizine offer a student pricing tier?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: Tentatively deferred to v3 measurement
- Tentative resolution trigger: v3 pricing review

**Question M3: Should yearly pricing be a meaningful discount over monthly?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: Tentatively the standard ~17% discount on annual, framed honestly
- Tentative resolution trigger: v2 launch prep

**Question M4: What is the right day for the mid-trial offer to appear?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: Day 7 (midpoint), possibly revisit based on v2 data
- Tentative resolution trigger: v2 launch period metrics

**Question M5: Should users who let their trial expire be eligible for a second trial later?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: Currently no; revisit if alpha feedback suggests strong demand
- Tentative resolution trigger: Alpha feedback review

**Question M6: What happens if a user starts a Cuizine trial and then upgrades mid-trial to Cuizine Family?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: Probably the Cuizine trial converts to a Family trial with the remaining days; Family trial eligibility is consumed. Specify during v2 build.
- Tentative resolution trigger: v2 build of the billing layer

**Question M7: What is the right BYOK pricing exactly?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: About half the standard price, with the specific number depending on v2 cost data
- Tentative resolution trigger: v3 planning

**Question M8: Should BYOK users get a visual indicator in the UI that they're on BYOK mode?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: Tentatively yes; calm Settings indicator, not celebratory
- Tentative resolution trigger: v3 build

**Question M9: How does Cuizine handle BYOK users whose API costs spike unexpectedly?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: Tentatively yes, a local-only estimated usage screen in Settings that tracks call counts
- Tentative resolution trigger: v3 build

**Question M10: What's the right strategy for handling Google Play's commission?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: Acknowledged; factored into the $5-7 band pricing to net Cuizine $3.50-6/month after Google's cut
- Tentative resolution trigger: Already handled in principle

**Question M11: Should Cuizine offer a web-based subscription option in addition to Google Play?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: Tentatively no for v2; revisit if the commission cost becomes meaningful at v3 scale
- Tentative resolution trigger: v3 cost analysis

**Question M12: How does Cuizine handle refund requests beyond the 48-hour Play Store window?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: Tentatively yes to all refund requests within 30 days, no questions asked
- Tentative resolution trigger: v2 launch period experience

**Question M13: Is the existing-users-keep-original-price commitment sustainable at scale?**
- Origin: `monetization-and-billing.md` Section 12
- Current answer: Tentatively committed forever, accepting loss-leader early users
- Tentative resolution trigger: Revisit only if the math becomes existential

### 3.6 Testing

**Question T1: What is the right scoring rubric for automated agent eval scenarios?**
- Origin: `testing-strategy.md` Section 13
- Current answer: Probably exact-match for safety-floor cases, fuzzy-match for borderline
- Tentative resolution trigger: Alpha iteration

**Question T2: How often should the full eval suite run?**
- Origin: `testing-strategy.md` Section 13
- Current answer: Rhythm set by the founder in CI configuration; depends on build activity
- Tentative resolution trigger: First CI setup during v1 build

**Question T3: Should the eval harness output be reviewed by the founder on a fixed cadence?**
- Origin: `testing-strategy.md` Section 13
- Current answer: Probably regular review (rhythm driven by eval run frequency) plus immediately on regression
- Tentative resolution trigger: Alpha operations

**Question T4: Should the canonical Sukhi profile evolve as alpha teaches us more about realistic constraint graphs?**
- Origin: `testing-strategy.md` Section 13
- Current answer: Yes, with discipline — fixture changes need their own commit and rationale
- Tentative resolution trigger: Alpha period

**Question T5: Should we have adversarial input fixtures?**
- Origin: `testing-strategy.md` Section 13
- Current answer: Probably yes, in `fixtures/adversarial/`; deferred to alpha if real cases surface
- Tentative resolution trigger: Alpha period

**Question T6: What's the right response when a performance budget is exceeded in tests?**
- Origin: `testing-strategy.md` Section 13
- Current answer: Fail the test loudly, require manual override with comment for environmental exceptions
- Tentative resolution trigger: First performance regression in build

**Question T7: Should we measure tail latency (p95, p99) in addition to median?**
- Origin: `testing-strategy.md` Section 13
- Current answer: Tentatively yes for interactive operations; deferred to implementation
- Tentative resolution trigger: Performance testing phase of v1 build

**Question T8: How do we enforce the near-100% coverage target on high-risk modules?**
- Origin: `testing-strategy.md` Section 13
- Current answer: Coverage tool in CI with 95% threshold on high-risk modules, manual override with rationale
- Tentative resolution trigger: First CI setup

**Question T9: Should branch coverage be measured separately from line coverage for the validator?**
- Origin: `testing-strategy.md` Section 13
- Current answer: Tentatively yes for validator, no for rest of codebase
- Tentative resolution trigger: CI setup

**Question T10: Should integration tests exercise the actual UI or bypass it?**
- Origin: `testing-strategy.md` Section 13
- Current answer: Compose UI tests with mocked agents (the current approach), to catch UI-layer bugs
- Tentative resolution trigger: First integration test written

**Question T11: What's the right cadence for adding new integration tests?**
- Origin: `testing-strategy.md` Section 13
- Current answer: One per significant feature plus regression tests on bugs, not per commit
- Tentative resolution trigger: Normal build discipline

### 3.7 Security and privacy

**Question S1: How does the threat model change as Cuizine's user base grows?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Will change meaningfully at v2 scale; needs periodic review post-v2 launch with rhythm set by user base growth
- Tentative resolution trigger: Post-v2 threat model review

**Question S2: Should the threat model explicitly consider nation-state threats for specific user populations?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Currently out of scope; revisit if elevated-risk user populations emerge
- Tentative resolution trigger: Emergence of journalist/activist/dissident user populations during alpha or v2

**Question S3: What is the right identity verification mechanism for v3 linking partners?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Tentatively both email and QR code, with QR as strongly-recommended default
- Tentative resolution trigger: v3 build

**Question S4: Should linked partners see each other's cultural context fields even in inter-cultural relationships?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Currently yes; maybe an opt-out per-field to be added
- Tentative resolution trigger: v3 build

**Question S5: How does revocation propagate if one partner is offline when the other revokes?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Firestore security rules check link status on every read; stale local cache is a race window
- Tentative resolution trigger: v3 security review

**Question S6: Should linked partner data be included in either party's export?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Probably yes with clear labeling
- Tentative resolution trigger: v3 build

**Question S7: What happens if a Canadian user of v1 alpha moves to the US during alpha?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Probably no action required; verify with counsel before v2
- Tentative resolution trigger: Legal counsel consultation before v2 launch

**Question S8: When does the v3 GDPR DPIA need to be completed?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Before v3 processing begins (i.e., before v3 launch in EU markets)
- Tentative resolution trigger: v3 planning

**Question S9: Should Cuizine proactively offer GDPR-style rights to non-EU users?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Tentatively yes — strongest standard applied universally
- Tentative resolution trigger: v3 planning

**Question S10: What's the right channel for security reporters to contact Cuizine during v1 alpha?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Probably an email address published in alpha onboarding materials
- Tentative resolution trigger: Alpha kickoff preparation

**Question S11: What is the minimum incident that triggers user notification?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Judgment at the margin; iterate based on actual alpha incidents
- Tentative resolution trigger: First incident (if any)

**Question S12: When should Cuizine introduce a security bulletin subscription?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Deferred to v2 launch
- Tentative resolution trigger: v2 launch planning

**Question S13: Has Cuizine actually rehearsed the incident response process?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: No, not yet; schedule a tabletop exercise during v1 alpha
- Tentative resolution trigger: v1 alpha operational setup

**Question S14: What happens if the founder is unavailable during a critical incident?**
- Origin: `security-and-privacy.md` Section 12
- Current answer: Currently a single point of failure; probably okay for alpha, needs a real plan by v2
- Tentative resolution trigger: v2 operational readiness

### 3.8 Food data

**Question F1: Who authors the curated food data bundle from ADR 0012?**
- Origin: `roadmap.md` Section 11
- Current answer: Unresolved. Real authoring work needed for culturally-specific entries, USDA/OFF corrections, medically-critical categories. May benefit from a nutritionist or cultural consultant.
- Tentative resolution trigger: v1 Phase 3 of the build

**Question F2: Should food data queries be routed through the Cloud Function proxy to hide per-user requests from external services?**
- Origin: `security-and-privacy.md` Section 5, `local-first-sync.md` Section 5
- Current answer: Deferred to v2 evaluation; not a v1 commitment
- Tentative resolution trigger: v2 design review

### 3.9 Alpha operations

**Question AL1: Should the founder keep a lightweight alpha learning log?**
- Origin: `alpha-feedback-and-iteration.md` Section 9
- Current answer: Tentatively yes, in `docs/alpha-log.md` or equivalent
- Tentative resolution trigger: Alpha kickoff

**Question AL2: What happens if an alpha user stops using Cuizine without explanation?**
- Origin: `alpha-feedback-and-iteration.md` Section 9
- Current answer: Nothing formal; informal check-in; investigate if multiple users disengage
- Tentative resolution trigger: First alpha user disengagement

**Question AL3: Should alpha users be offered permanent unpaid status after v2 launch?**
- Origin: `alpha-feedback-and-iteration.md` Section 9
- Current answer: No — moved to free tier with trial eligibility, same as new users
- Tentative resolution trigger: Already resolved

**Question AL4: How are APK updates distributed during alpha?**
- Origin: `alpha-feedback-and-iteration.md` Section 9
- Current answer: Direct link via messaging or email; no OTA infrastructure for alpha
- Tentative resolution trigger: Alpha kickoff

**Question AL5: How are the initial 15-25 alpha users identified?**
- Origin: `roadmap.md` Section 11
- Current answer: Personal outreach, community channels, interested connections from the founder's network; no formal application
- Tentative resolution trigger: Alpha kickoff preparation

**Question AL6: What happens if alpha surfaces a foundation gap that requires a new ADR mid-alpha?**
- Origin: `roadmap.md` Section 11
- Current answer: Write the ADR, update the foundation, agent implements the change, alpha continues
- Tentative resolution trigger: First mid-alpha ADR

**Question AL7: What's the right communication channel with alpha users during the alpha?**
- Origin: `roadmap.md` Section 11
- Current answer: Direct email or messaging, not in-app
- Tentative resolution trigger: Alpha kickoff

### 3.10 Launch and transitions

**Question L1: Is the transition phase compressed if alpha exits quickly?**
- Origin: `roadmap.md` Section 11
- Current answer: Probably not — transition work has its own minimum duration
- Tentative resolution trigger: Actual transition experience

**Question L2: What happens to alpha users during the alpha-to-v2 transition?**
- Origin: `roadmap.md` Section 11
- Current answer: Keep using alpha version (with bypass still active) until v2 ships
- Tentative resolution trigger: Already resolved

**Question L3: How do we know we're ready for the full public launch without a soft rollout safety net?**
- Origin: `roadmap.md` Section 11
- Current answer: Rigor of alpha exit criteria plus comprehensive test suite; founder's conviction as final gate
- Tentative resolution trigger: The alpha exit checklist from `launch-readiness-checklist.md` Section 4

**Question L4: What's the right initial marketing investment for v2?**
- Origin: `roadmap.md` Section 11
- Current answer: Modest — launch blog post, honest outreach, organic social; no paid marketing unless specific reason
- Tentative resolution trigger: v2 launch planning

**Question L5: When does hiring start?**
- Origin: `roadmap.md` Section 11
- Current answer: Solo founder through v1 and probably through early v2; triggered by operational overload or specific capability needs
- Tentative resolution trigger: Real circumstances post-v2 launch

**Question L6: What's the right duration to wait before starting v3 planning?**
- Origin: `roadmap.md` Section 11
- Current answer: Exit criteria from `roadmap.md` Section 7 are soft; founder's judgment is the ultimate gate
- Tentative resolution trigger: Context-dependent; driven by stability signals

**Question L7: Should Cuizine pursue partnerships with healthcare providers/nutritionists/dietitians in v3?**
- Origin: `roadmap.md` Section 11
- Current answer: Current posture is explicitly not-HIPAA; v3 might revisit if partnership opportunities emerge
- Tentative resolution trigger: v3 planning and emerging opportunities

**Question L8: Should v3 introduce dedicated team members beyond the founder?**
- Origin: `roadmap.md` Section 11
- Current answer: Probably yes given scope; timing is a separate decision from technical roadmap
- Tentative resolution trigger: v3 planning

### 3.11 Documentation and process

**Question P1: How does Cuizine decide when to deprecate a feature?**
- Origin: `roadmap.md` Section 11
- Current answer: Formal process not currently specified; should be an addition to `build-conventions.md` or its own minor doc when first real deprecation surfaces
- Tentative resolution trigger: First real deprecation decision

**Question P2: Is the foundation doc maintenance rhythm sustainable over time?**
- Origin: `roadmap.md` Section 11
- Current answer: Probably yes; will calibrate as Cuizine matures
- Tentative resolution trigger: v2 period retrospective

**Question P3: Are there checklist items that should be marked as absolutely non-waivable?**
- Origin: `launch-readiness-checklist.md` Section 10
- Current answer: Currently only safety-floor gates explicitly non-waivable; PIPEDA compliance items, privacy policy publication, and disclaimer prominence should probably be added
- Tentative resolution trigger: v2 launch readiness review

**Question P4: Who can approve a waiver when it's a solo-founder project?**
- Origin: `launch-readiness-checklist.md` Section 10
- Current answer: Founder waives own items at v1 scale; some waivers might benefit from second pair of eyes at v2+
- Tentative resolution trigger: Team growth

**Question P5: Where do post-gate retrospectives live?**
- Origin: `launch-readiness-checklist.md` Section 10
- Current answer: Probably their own `retrospectives/` folder at repo root
- Tentative resolution trigger: First retrospective (after v1 alpha kickoff)

**Question P6: Should retrospectives be public?**
- Origin: `launch-readiness-checklist.md` Section 10
- Current answer: Too early to decide; revisit after v2 launch
- Tentative resolution trigger: Post-v2 launch review

**Question P7: How often is the launch-readiness-checklist doc itself reviewed?**
- Origin: `launch-readiness-checklist.md` Section 10
- Current answer: After each gate retrospective
- Tentative resolution trigger: First gate retrospective

**Question P8: Should checklist item numbers be stable across revisions?**
- Origin: `launch-readiness-checklist.md` Section 10
- Current answer: Probably yes for main items; additions appended
- Tentative resolution trigger: First major revision

### 3.12 v3 and beyond

**Question V1: Should v3 revisit HIPAA if clinical partnerships emerge?**
- Origin: `security-and-privacy.md` and `roadmap.md` Section 11
- Current answer: v1/v2 posture is explicitly not-HIPAA; v3 is the logical moment to revisit if partnership opportunities present
- Tentative resolution trigger: Concrete partnership opportunity

**Question V2: Should Cuizine ever introduce a formal bug bounty program?**
- Origin: `security-and-privacy.md` Section 6
- Current answer: v1 founder-only, v2 formal program without bounty, v3+ bounty if experience warrants
- Tentative resolution trigger: v2 launch experience

**Question V3: Should Cuizine introduce community channels (Discord, forums) at any tier?**
- Origin: `alpha-feedback-and-iteration.md` Section 8 (implicit via "not part of alpha")
- Current answer: Not for alpha; could emerge at v2 or v3 if user demand warrants; not Cuizine-operated if so
- Tentative resolution trigger: v2+ user feedback

**Question V4: Should Cuizine ever reverse the Android-only decision and add iOS (or another platform)?**
- Origin: ADR 0015 (Android-only scope)
- Current answer: No, by default. Cuizine is Android-only across all versions, well-matched to its South-Asian-skewing, Android-heavy target audience. The decision is honestly revisitable but not expected to reverse. If reversed, adding iOS to the native Kotlin codebase (ADR 0016) means a separate native iOS app or a cross-platform rebuild — the explicitly accepted trade-off of going native.
- Tentative resolution trigger: Post-v2 data showing substantial, mission-relevant demand from iOS users that materially affects viability

## 4. Questions resolved since the last foundation doc revision

> This section is intentionally empty at the initial draft of this document. As questions resolve, their entries migrate here (briefly) and then are removed in the next revision cycle along with their corresponding entries in Section 3. This section is the historical record that helps the founder see what has been decided over time.

*The platform and framework questions below were resolved during foundation review and are recorded here for the historical trail.*

- **Platform scope (was open in ADR 0001's v3 iOS aspect).** Resolved: Cuizine is Android-only across all versions. iOS, web, and desktop are out of scope. v3 "global expansion" is geographic (Android in new markets), not cross-platform. *Formalized in ADR 0015.*
- **UI framework / language / persistence / state management.** Resolved: native Kotlin + Jetpack Compose + Orbit MVI + Room + Hilt + Tink, replacing the Flutter/Drift/Riverpod/`cryptography` stack from the original ADR 0005. *Formalized in ADR 0016 (supersedes ADR 0005).*
- **Riverpod API style and provider granularity (former Questions A3, A4).** Superseded by the move off Riverpod; the equivalent Orbit MVI questions now appear as the current A3 and A4.

## 5. Cross-references

### What this document references

- Every foundation doc (each question originates in one of their Open Questions sections)
- Every ADR (some questions are deferred pending resolution via future ADR)

### What this document defers to source docs

- The substance of each question — the origin doc holds the context
- The evolution of each question — when a foundation doc's Open Questions section changes, this doc tracks the change

### How the agent should use this doc

When the coding agent is about to make a decision that isn't specified in a foundation doc, this doc is the second stop (after the foundation doc itself). If the decision is here, it's a known deferred question — the agent handles it per the tentative answer or surfaces it if the tentative answer doesn't apply. If the decision is not here, it's a new question — the agent surfaces it per `build-conventions.md` Section 6 and, if confirmed as architectural, the founder adds it here.

When the agent surfaces a question that turns out to be already in this doc, the conversation is shorter — the founder recognizes the question and either applies the tentative answer or formalizes the resolution via ADR. The doc's role is to prevent the founder from re-litigating decisions that have already been considered.

---

*End of `open-questions.md` v1 (initial draft). This is a living document. Questions are added as new ones surface during build, alpha, and subsequent phases. Questions are removed (or migrated to Section 4 as resolved) as they get answered. The doc exists to prevent open questions from getting lost in individual foundation docs and accumulating silently as unresolved debt.*
