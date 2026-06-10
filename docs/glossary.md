# Cuizine — Glossary

> The canonical term definitions. Every time a foundation doc says "the active set" or "contextual constraint" or "severity tier" or "linked partner" or "the recovery passphrase," this document is the single source of truth for what those terms mean. When a term is used in a foundation doc without immediate definition, the glossary is where readers confirm the meaning. When docs disagree about a term, this doc wins for the definition and the disagreeing doc is updated to match.

## 1. Purpose & how to read this doc

This document is the **consolidated terminology reference** for Cuizine's foundation docs. It assumes you have read most or all of the other foundation docs and the ADRs, and that you need a quick reference for a specific term's canonical meaning.

Terms are organized alphabetically. Each entry includes: the term, a concise definition in plain language, the primary reference doc (the one that defines the term most authoritatively), and cross-references to other docs that use the term meaningfully.

When a term has a product-facing meaning (what the user sees) and a technical meaning (how the code implements it), both are specified. When a term is ambiguous across docs, the ambiguity is named and resolved — this doc commits to a single definition and flags the docs that need alignment.

This document does **not** define: terms from external standards (HTTP, SQL, Kotlin, Jetpack Compose, Firebase — those are defined elsewhere), terms that are used only once in a single doc with inline definition, or terms that are proper nouns without Cuizine-specific meaning (company names, product names from other companies).

**When this document and a foundation doc disagree on a term's meaning,** this doc wins and the foundation doc is updated on next revision. When a term is used in multiple foundation docs with consistent meaning, this doc reflects that consistency. When foundation docs introduce a new term after this glossary's writing, the glossary is updated in the next revision cycle.

## 2. Terms

### A

**Active set.** The subset of a user's constraint graph that is currently in effect, computed by applying the scope evaluation logic from `constraint-engine-spec.md` Section 5 to the full constraint graph at the current moment and context. The active set is what the Chef and validator reason against during any given meal suggestion. Changes automatically when temporal scope boundaries are crossed (e.g., Tuesday transitioning to Wednesday flips Sukhi's Tuesday-vegetarian constraint out of the active set) or when contextual state changes (e.g., an IBS-flare flag being set activates Sukhi's onion/garlic avoid constraints). *Primary reference: `constraint-engine-spec.md` Section 5. Also used in: `agent-architecture.md`, `testing-strategy.md`, `data-model.md`.*

**ADR.** Architectural Decision Record. A short document that captures the context, decision, consequences, and alternatives considered for a specific architectural choice. Cuizine's ADRs live in `decisions/` and are numbered monotonically (0001, 0002, etc.). New ADRs are written when decisions rise to the level of "this affects how the system works" per `build-conventions.md` Section 9. *Primary reference: `build-conventions.md` Section 9. Also used in: every foundation doc that cites specific decisions.*

**Agent.** An LLM-backed reasoning component that handles a specific kind of task within Cuizine — Curator (constraint conversation), Chef (meal suggestions), Pantry (ingredient tracking), Planner (week-ahead planning, v2), Sourcing (grocery handoff, v2), Observer (long-term health intelligence, v3). Each agent conforms to the archetype from `agent-architecture.md` Section 2: stateless between invocations, structured input/output via typed contracts, tool access enforced by the orchestrator. *Primary reference: `agent-architecture.md`. Also used in: `technical-architecture.md`, `testing-strategy.md`.*

**Agent archetype.** The common interface every Cuizine agent conforms to: a deterministic typed input, a deterministic typed output, the system prompt in `prompts/`, the provider routing via `ModelProvider`, and the orchestrator as the only caller. Agents are not persistent objects; they are stateless reasoning operations. *Primary reference: `agent-architecture.md` Section 2.*

**AI fallback.** The mechanism from ADR 0012 where unknown food ingredients are categorized by LLM inference when they are not in the cache, bundle, or external services. The fallback is severity-scoped — it runs with disclosure for Medical and Religious & Cultural constraints, runs silently for Preference constraints, and does NOT run for Inviolable constraints (Inviolable + unknown ingredient = reject). *Primary reference: ADR 0012. Also used in: `constraint-engine-spec.md` Section 7, `testing-strategy.md` Section 5.*

**Alpha bypass.** The v1-only mechanism from `monetization-and-billing.md` Section 9 where every alpha user has their `v1_alpha_bypass` flag set to 1, which causes the `TierPolicy` module to treat them as if they are on the Cuizine Family tier without any subscription. Removed cleanly at v2 launch via a single migration. Alpha users see no billing UI, no payment prompts, no subscription flows during v1. *Primary reference: `monetization-and-billing.md` Section 9. Also used in: `roadmap.md` Section 5, `launch-readiness-checklist.md`.*

**Alpha period.** The phase from alpha kickoff through the alpha-to-v2 transition, per `roadmap.md` Section 4. A deliberately lean phase with three explicit learning goals (constraint engine correctness validation, tone validation, foundation gap surfacing) and five exit criteria. Duration is driven by completion, not calendar. *Primary reference: `roadmap.md` Section 4. Also used in: `alpha-feedback-and-iteration.md`, `launch-readiness-checklist.md`.*

**Anthropic.** The company behind Claude, one of the three LLM providers Cuizine routes through per ADR 0006. Cuizine uses Anthropic's zero-data-retention option to minimize upstream visibility. *Primary reference: ADR 0006. Also used in: `security-and-privacy.md` Section 3.*

### B

**Barcode scanning.** A v2+ Pantry agent capability from `technical-architecture.md` Section 7.5 that uses the device camera (CameraX + ML Kit barcode scanning) and the Open Food Facts REST API's barcode lookup to identify products the user has in their pantry. Bundled with receipt scanning and fridge photo ingestion as a single v2 capability. *Primary reference: `technical-architecture.md` Section 7.5.*

**BillingService.** The Kotlin wrapper class from `monetization-and-billing.md` Section 6 that abstracts over the Google Play Billing Library. Translates Google Play's purchase events into Cuizine's tier state updates, isolating the rest of the code from direct Google Play dependencies. *Primary reference: `monetization-and-billing.md` Section 6.*

**BYOK.** Bring Your Own Keys. A v3 capability from ADR 0008 where users supply their own LLM provider API keys and route inference calls directly from their device to the provider, bypassing Cuizine's Cloud Function proxy. The only path to the signed-out × paid quadrant from ADR 0011. Priced at roughly half the standard Cuizine subscription to reflect Cuizine's reduced cost-to-serve. *Primary reference: ADR 0008. Also used in: `monetization-and-billing.md` Section 10, `security-and-privacy.md` Section 5, `roadmap.md` Section 8.*

### C

**Cache (food data).** Layer 1 of the three-layer food data architecture from ADR 0012. A per-user, per-device record of every ingredient lookup that has ever happened, stored in the local SQLite container and replicated via the encrypted sync container for signed-in users. The cache grows as the user's vocabulary expands, and after typical use, most lookups are cache hits. *Primary reference: ADR 0012. Also used in: `data-model.md` Section 5, `constraint-engine-spec.md` Section 7.*

**Chef.** The v1 agent that produces meal suggestions. Takes the active constraint set and user preferences, produces structured meal output that is then checked by the validator. Can be re-invoked with rejection feedback to regenerate. *Primary reference: `agent-architecture.md` Section 5.*

**Cloud Function proxy.** The Firebase Cloud Function from ADR 0011 that forwards signed-in users' inference requests to the upstream LLM providers. Holds the upstream API keys securely, does not log inference content, and exists purely to protect API keys from APK extraction. Signed-out users bypass the proxy and route directly to the providers. *Primary reference: ADR 0011. Also used in: `security-and-privacy.md` Section 4, `local-first-sync.md` Section 5.*

**Coding agent.** The AI-assisted development agent the founder uses to vibecode Cuizine (currently Claude Code). Referred to as "the coding agent" throughout the foundation docs, deliberately tool-neutral — the architecture does not depend on which IDE or agent builds it. *Primary reference: contextual; no single foundation doc section defines this.*

**Constraint.** A single rule about food that applies to a user — something to avoid, something to prefer, something required, something to limit, or a contextual flag. Every constraint in Cuizine has a type, a severity, a scope, and provenance. Constraints live in the constraint graph and are evaluated by the validator during meal suggestions. *Primary reference: `constraint-engine-spec.md` Sections 3-5. Also used in: every foundation doc.*

**Constraint engine.** The deterministic (non-LLM) reasoning layer that manages the constraint graph, evaluates scope to produce the active set, and runs the validator. The engine is pure Kotlin and has no Android or Compose dependencies per `build-conventions.md` Section 3. It is the heart of pillar 1 and gets the largest single body of tests (the hard cases test suite). *Primary reference: `constraint-engine-spec.md`. Also used in: every foundation doc.*

**Constraint graph.** The full collection of a user's constraints, stored in the `constraints` table per `data-model.md` Section 3. A graph (not just a list) because constraints interact — contextual constraints can modify the scope of other constraints, conflicting constraints can force resolution, temporal constraints can activate and deactivate together. *Primary reference: `constraint-engine-spec.md` Section 2.*

**Constraint type.** One of the five semantic kinds of constraint from `constraint-engine-spec.md` Section 3: avoid, prefer, require, limit, contextual. Each type has a specific payload schema (see Discriminated union payload) and specific validator behavior. *Primary reference: `constraint-engine-spec.md` Section 3. Also used in: `data-model.md` Section 3, `testing-strategy.md` Section 5.*

**Contextual constraint.** A constraint type from `constraint-engine-spec.md` Section 3 that represents a situational flag the user has reported rather than a permanent rule — "my gut has been off all day," "my blood sugar ran high this morning," "I'm in Toronto for a few days." Always has a short expiration, always comes from user free-text input via the Curator agent, and always interacts with other constraints (typically as a scope modifier). *Primary reference: `constraint-engine-spec.md` Section 3. Also used in: `agent-architecture.md`.*

**Content portability.** The commitment from `monetization-and-billing.md` Principle 2 that users can export all their Cuizine data at any time, in any tier, without conditions. The export is a structured JSON document per Section 8. Called a "forever commitment" because pricing changes, tier consolidation, and feature deprecation cannot affect it. *Primary reference: `monetization-and-billing.md` Sections 2 and 8. Also used in: `security-and-privacy.md` Section 9.*

**Content telemetry.** Any telemetry that captures or derives from user content (constraints, suggestions, conversation text, meal history). Forbidden across all versions of Cuizine per ADR 0011 and multiple foundation docs. Operational telemetry (error counts, latency, sync event counts) is allowed and necessary; content telemetry is not. *Primary reference: ADR 0011. Also used in: `security-and-privacy.md` Principle 5, `monetization-and-billing.md` Section 11.*

**Conversation FAB.** The centered floating action button in the bottom navigation, per `ui-ux-spec.md` Section 4. The front door to the conversation with Cuizine. Tapping it opens the unified Cuizine conversation — one entity, with the orchestrator routing internally to the Curator, Chef, or Pantry agent (per ADR 0006); the user never picks an agent. Elevated above the four tabs because the conversation is the soul of the product. *Primary reference: `ui-ux-spec.md` Section 4. Also used in: `agent-architecture.md`.*

**Cuizine (product).** The AI-native food companion app this foundation set specifies. Five pillars: layered constraints, pantry-aware planning, long-term health intelligence, instant context adaptation, cultural and contextual fluency. *Primary reference: `vision.md`.*

**Cuizine (paid tier).** The mid-tier paid subscription per `monetization-and-billing.md` Section 3. Unlocks cross-device sync, week-ahead planning (v2+), sourcing (v2+), long-term health intelligence (v3+), and other capabilities beyond the free tier. Priced in the lower band ($5-7/month USD). *Primary reference: `monetization-and-billing.md` Section 3.*

**Cuizine Family (paid tier).** The household tier per `monetization-and-billing.md` Section 3. Everything in Cuizine plus multi-profile households (up to 8 profiles in v2), cross-profile household planning, linked partner profiles (v3). Priced at 1.5-2x Cuizine ($10-14/month USD). *Primary reference: `monetization-and-billing.md` Section 3.*

**Curated bundle.** Layer 2 of the three-layer food data architecture from ADR 0012 — a hand-authored, app-bundled JSON dataset of high-confidence ingredient entries (categories, allergens, common cultural ingredients) shipped with the app so core lookups work offline and don't depend on external services. Sits between the per-user local cache (Layer 1) and the external sources USDA + Open Food Facts (Layer 3); bundle entries never expire. Loaded behind the Food Data Provider interface. *Primary reference: ADR 0012. Also used in: `data-model.md` Section 5, `constraint-engine-spec.md` Section 7.*

**Curator.** The v1 agent that handles constraint conversations — interpreting user free-text input about their dietary life, classifying it into typed constraints with severity and scope, and writing to the constraint graph. The agent whose prompt iteration matters most for tone. *Primary reference: `agent-architecture.md` Section 4.*

### D

**Decision checkpoint.** A named, phase-anchored moment in `roadmap.md` where a premade decision the foundation itself flags as a judgment call or data-contingent guess is re-confirmed or challenged before that phase's work begins. Each is tagged `[data-driven]`, `[research-informed]`, or `[both]`. Checkpoints are resolved interactively at phase gates by default, or autonomously under a founder standing delegation (delegated mode), with every resolution logged in `DECISION-LOG.md` and any changed product decision producing a superseding ADR. *Primary reference: `roadmap.md` Section 2, Principle 6.*

**Deterministic validator.** See Validator.

**Discriminated union payload.** The pattern in `data-model.md` Section 3 and `constraint-engine-spec.md` Section 3 where each constraint type has its own payload schema (stored as JSON in the `payload_json` column), and the `constraint_type` column is the discriminator that tells the code how to parse the payload. The five discriminated payloads: AvoidPayload, PreferPayload, RequirePayload, LimitPayload, ContextualPayload. *Primary reference: `constraint-engine-spec.md` Section 3, `data-model.md` Section 3.*

**Drift.** *(Superseded.)* The type-safe persistence library for Flutter originally chosen in ADR 0005. Replaced by Room when Cuizine moved to native Kotlin (ADR 0016). See **Room**. *Primary reference: ADR 0016 (supersedes ADR 0005).*

### E

**Encrypted sync container.** The blob of user data that is encrypted on the device before being uploaded to Firestore, per `local-first-sync.md` Section 3. Contains the user's full constraint graph, suggestion history, cooked meals, pantry, food data cache personal entries, and subscription state. Decryption requires the user's recovery passphrase; Cuizine's infrastructure holds only opaque ciphertext. *Primary reference: `local-first-sync.md` Section 3. Also used in: `security-and-privacy.md` Section 4, `monetization-and-billing.md` Section 5.*

**Event log.** The structured activity log per `data-model.md` Section 7 and PRD § 5. Captures constraint operations, suggestions, validation results, rejection events, and operational events. Exportable by users via Settings → Share feedback with Cuizine founder. Manually reviewed by the founder during alpha per `alpha-feedback-and-iteration.md` Section 3. *Primary reference: `data-model.md` Section 7. Also used in: PRD § 5, `alpha-feedback-and-iteration.md`.*

**Existing-users-keep-original-price.** The loyalty commitment from `monetization-and-billing.md` Principle 5. When Cuizine raises prices for new users, existing users keep the price they signed up at for as long as they remain continuously subscribed. The credibility deposit that lets Cuizine raise prices later without breaking trust with early adopters. *Primary reference: `monetization-and-billing.md` Section 2 and Section 3.*

**Export-and-walk-away.** The trust posture commitment from `vision.md` and `local-first-sync.md` Section 8 that lets users export all their data and delete their Cuizine account cleanly, with the data actually being deleted (no hidden retention) and the export being comprehensive (nothing gated behind a paywall). *Primary reference: `vision.md`. Also used in: `monetization-and-billing.md` Section 8, `security-and-privacy.md` Section 9.*

### F

**Firebase.** The Google backend platform Cuizine uses per ADR 0011 and `technical-architecture.md` Section 6. Specifically: Firebase Authentication for user accounts, Firestore for the encrypted sync container storage, Firebase Cloud Functions for the LLM proxy, Firebase environment variables for secure API key storage. *Primary reference: ADR 0011. Also used in: `local-first-sync.md`, `technical-architecture.md`.*

**Firestore.** Google's NoSQL document database, used as Cuizine's cloud-side blob store per `local-first-sync.md` Section 5. Holds the encrypted sync container as an opaque document per user, plus v3 linked partner subcollections. *Primary reference: `local-first-sync.md` Section 5.*

**Flutter.** *(Superseded.)* The cross-platform UI framework originally chosen in ADR 0005, partly for eventual iOS readiness. Replaced by native Kotlin + Jetpack Compose when Cuizine committed to Android-only (ADR 0015) and reopened the framework decision on Android merits (ADR 0016). See **Jetpack Compose** and **Kotlin**. *Primary reference: ADR 0016 (supersedes ADR 0005).*

**Food Data Provider.** The abstraction from `technical-architecture.md` Section 3 and ADR 0012 that unifies the three-layer food data architecture (local cache, curated bundle, external sources USDA+OFF) behind a single interface. The validator queries the Food Data Provider and gets back category, allergen, and nutritional information regardless of which layer served the data. *Primary reference: ADR 0012.*

**Forbidden behaviors.** The explicit lists in `local-first-sync.md` Section 10, `monetization-and-billing.md` Section 11, and `build-conventions.md` Section 11 of things Cuizine deliberately does NOT do. Each list is the discipline artifact that prevents silent drift when a future change seems reasonable but would violate a commitment. *Primary reference: the three referenced sections. Also used in: `launch-readiness-checklist.md` Section 3.*

**Foundation doc.** Any of the documents in `docs/` that specify Cuizine's architecture, operations, and commitments. Distinct from ADRs (which capture specific decisions) and from working documents (which are created during build and live elsewhere). *Primary reference: `build-conventions.md` Section 7.*

**Free tier.** The forever-free tier of Cuizine per `monetization-and-billing.md` Principle 1 and Section 3. Includes the full constraint engine, Curator, Chef, and Pantry (v1 lightweight); the local-first experience on a single device; content portability; recipe and cooking history; and the full food data layer. Everything a real user can rely on forever without ever paying Cuizine. *Primary reference: `monetization-and-billing.md` Section 3.*

**Fridge photo ingestion.** A v2+ Pantry agent capability from `technical-architecture.md` Section 7.5 that uses multimodal AI to identify ingredients from a photograph of the user's fridge. Bundled with barcode scanning and receipt scanning as a single v2 capability. *Primary reference: `technical-architecture.md` Section 7.5.*

### G

**GCM.** Galois/Counter Mode, the authenticated encryption mode used with AES-256 per `local-first-sync.md` Section 3. Provides both confidentiality and integrity — tampered ciphertext fails to authenticate and decryption fails safely. *Primary reference: `local-first-sync.md` Section 3.*

**GDPR.** General Data Protection Regulation. The EU (and post-Brexit UK equivalent) privacy framework that Cuizine will comply with at v3 launch per `security-and-privacy.md` Section 7. More rigorous than PIPEDA or US state laws, requiring lawful basis for processing, DPIA for special category data, breach notification within 72 hours, and specific data subject rights. *Primary reference: `security-and-privacy.md` Section 7. Also used in: `roadmap.md` Section 8.*

**Glossary.** This document.

### H

**Hard cases test suite.** The organized test suite from `testing-strategy.md` Section 5 that exercises every safety-floor behavior of the constraint engine. Seven categories mapped to `constraint-engine-spec.md` sections. The single largest body of tests in Cuizine's test suite and the one the founder reviews most carefully. *Primary reference: `testing-strategy.md` Section 5.*

**Hilt.** Google's first-party dependency injection library for Android (built on Dagger), used as Cuizine's DI framework per ADR 0016. Provides the wiring that lets the layers (engine, agents, data, billing, UI ViewModels) receive their dependencies without manual construction, and lets tests substitute fakes cleanly. *Primary reference: ADR 0016. Also used in: `build-conventions.md` Section 4.*

**HIPAA.** Health Insurance Portability and Accountability Act. The US framework for healthcare providers, health plans, and their business associates. Cuizine is **explicitly not a HIPAA-covered entity** per `security-and-privacy.md` Principle 2. *Primary reference: `security-and-privacy.md` Principle 2 and Section 7.*

**Household.** A collection of one or more profiles per ADR 0004. v1 ships single-profile (trivially, one household = one profile). v2 adds multi-profile households for Cuizine Family tier with three profile variants (silent dependent, consent-aware dependent, recommended-disclosure dependent). v3 adds linked partner profiles with the cross-account security model from `security-and-privacy.md` Section 8. *Primary reference: ADR 0004. Also used in: `data-model.md` Section 3, `monetization-and-billing.md` Section 3.*

### I

**Inviolable constraint.** The highest severity tier per ADR 0009 and `constraint-engine-spec.md` Section 4. A constraint whose violation would be an absolute disaster — severe allergies with anaphylaxis risk, constraints explicitly marked by the user as non-negotiable. Validator rejects any violation without AI fallback. Conflict resolver never offers for relaxation. *Primary reference: ADR 0009. Also used in: `constraint-engine-spec.md` Sections 4 and 7, `testing-strategy.md` Section 5.*

### J

**Jetpack Compose.** Google's modern declarative UI toolkit for native Android, Cuizine's UI layer per ADR 0016. Replaces the Flutter widget approach from the superseded ADR 0005. Cuizine uses Compose with Material 3 / Material You theming, with UI state held in Orbit MVI ViewModels and collected into composables via `collectAsStateWithLifecycle()`. *Primary reference: ADR 0016. Also used in: `build-conventions.md` Section 4, `technical-architecture.md`.*

### K

**Kotlin.** The programming language Cuizine is written in per ADR 0016, replacing Dart from the superseded ADR 0005. The constraint engine is pure Kotlin with no Android dependencies; the rest of the app uses Kotlin with the Android and Jetpack libraries. *Primary reference: ADR 0016. Also used in: `build-conventions.md`, every implementation-touching doc.*

**Instacart.** The grocery delivery service per ADR 0003 that Cuizine integrates with in v2 through the Sourcing agent. Chosen as the sole grocery integration for v2 to limit operational complexity. *Primary reference: ADR 0003. Also used in: `agent-architecture.md` Section 10, `roadmap.md` Section 6.*

### L

**Limit constraint.** A constraint type from `constraint-engine-spec.md` Section 3 with a ceiling value and window (per-meal, daily, weekly). Examples: "sodium under 2000mg/day," "refined carbs under 45g/meal." Hard vs soft enforcement. Validator aggregates across ingredients and rejects or flags per severity. *Primary reference: `constraint-engine-spec.md` Section 3.*

**Linked partner.** A v3 household feature from ADR 0004 and `security-and-privacy.md` Section 8 where two adults with their own Firebase Auth accounts can connect their Cuizine accounts to enable cross-account household meal planning. Uses the per-link encryption model from Section 8 — a per-pair symmetric key derived via X25519 key exchange, with each partner sharing only a minimal view of their constraints with the other. Revocable unilaterally by either partner. Will graduate to ADR 0013 at v3 planning. *Primary reference: `security-and-privacy.md` Section 8. Also used in: ADR 0004, `monetization-and-billing.md` Section 3, `roadmap.md` Section 8.*

**Local-first.** The architectural posture from `vision.md` and `local-first-sync.md` Section 2 where the app is fully functional on a single device without any network connectivity, and the cloud is an optional enhancement (for cross-device sync) rather than a requirement. Privacy, correctness, and core functionality live on the device. *Primary reference: `local-first-sync.md` Section 2. Also used in: every foundation doc.*

### M

**Medical constraint.** A severity tier per ADR 0009 and `constraint-engine-spec.md` Section 4 for constraints rooted in medical diagnoses or conditions. Co-equal with Religious & Cultural (neither takes precedence). Validator rejects violations and triggers regeneration. AI fallback for unknown ingredients runs with disclosure note. *Primary reference: ADR 0009.*

**Mock containers.** The Orbit MVI containers used during the Phase 2 UI build, backed by mock repositories and mock agents that return the canonical Sukhi/Aisha/Marcus fixtures instead of calling real subsystems, per `ui-ux-spec.md` Section 10. They expose the identical `State` shapes and `Intents` as the real containers will, so screens built against them do not change when the real engine, agents, sync, and billing are wired in during later phases. *Primary reference: `ui-ux-spec.md` Section 10. Also used in: `roadmap.md` Section 3.*

**ModelProvider.** The Kotlin interface from `agent-architecture.md` Section 8 and ADR 0006 that abstracts over the three LLM providers (Anthropic, Google, OpenAI). Enables per-agent provider routing based on task characteristics (reasoning depth, cultural fluency, speed, cost) rather than a single-provider-for-everything architecture. BYOK in v3 reconfigures this interface to route through the user's keys instead of the Cloud Function proxy. *Primary reference: ADR 0006. Also used in: `agent-architecture.md` Section 8.*

**Multi-profile household.** See Household. The v2+ capability where a Cuizine Family subscriber can add dependent profiles (children, elderly parents, housemates) with three variants of consent flow. *Primary reference: ADR 0004.*

### O

**Observer.** A v3 agent per `agent-architecture.md` Section 10 that runs on a schedule (not in response to immediate user requests) and reviews the user's accumulated history to surface long-term health trends, predictions, and gentle warnings. Requires long context window capability. Deferred to v3 because it needs accumulated data to be meaningful. *Primary reference: `agent-architecture.md` Section 10.*

**Orbit MVI.** Cuizine's state management approach per ADR 0016, replacing Riverpod from the superseded ADR 0005. A Model-View-Intent library layered on top of Android's ViewModel + StateFlow: each feature area has a container (ViewModel) owning an immutable State, processing Intents through reducers, and emitting one-time Side Effects. Chosen because it provides MVI's structured separation of concerns on top of standard Android primitives, and because the MVI pattern maps naturally onto Cuizine's state-machine-shaped flows. *Primary reference: ADR 0016. Also used in: `build-conventions.md` Section 4.*

**OpenAI.** One of the three LLM providers Cuizine routes through per ADR 0006. *Primary reference: ADR 0006.*

**Open Food Facts (OFF).** An external food database queried via its REST API (using Retrofit) per ADR 0012. Layer 3 of the three-layer food data architecture, along with USDA. Provides product-level ingredient lists and allergen information. *Primary reference: ADR 0012.*

**Orchestrator.** The component from `agent-architecture.md` Section 3 that routes user intents to the appropriate agent using rule-based (not LLM-based) routing, enforces tool access permissions, runs the post-generation validator on agent outputs, and handles the regeneration loop. Deterministic code, not an LLM. *Primary reference: `agent-architecture.md` Section 3.*

### P

**Pantry agent.** A v1 lightweight agent that evolves into a v2+ first-class capability per `technical-architecture.md` Section 7.5. v1 handles manual pantry entry; v2 adds barcode scanning, receipt scanning, and fridge photo ingestion. *Primary reference: `agent-architecture.md` Section 6.*

**Per-link encryption key.** The per-pair symmetric AES-256 key used to encrypt the shared views exchanged between two linked partners in v3. Derived via X25519 ephemeral key exchange with HKDF-SHA256 per `security-and-privacy.md` Section 8. Held only on the two partner devices; Cuizine's infrastructure never sees it. *Primary reference: `security-and-privacy.md` Section 8.*

**PIPEDA.** Personal Information Protection and Electronic Documents Act. Canada's federal privacy law for private-sector organizations. Applies to Cuizine from v1 (Canadian alpha) onward. *Primary reference: `security-and-privacy.md` Section 7.*

**Planner agent.** A v2+ agent per `agent-architecture.md` Section 10 that generates structured week-ahead meal plans balancing nutrition, cultural appropriateness, and ingredient reuse. Invokes the Chef as a sub-routine for individual meals, with per-meal validator invocation. *Primary reference: `agent-architecture.md` Section 10. Also used in: `roadmap.md` Section 6.*

**Preference constraint.** The lowest severity tier per ADR 0009 for constraints expressing likes and dislikes. Validator does not reject violations but produces disclosure notes. Conflict resolver silently relaxes preferences when needed. AI fallback for unknown ingredients runs silently. *Primary reference: ADR 0009.*

**Prefer constraint.** A constraint type from `constraint-engine-spec.md` Section 3 with strength (low/medium/high) that biases the Chef's ranking without rejecting violations. *Primary reference: `constraint-engine-spec.md` Section 3.*

**Primary persona.** Sukhi per PRD § 3. A 53-year-old Punjabi Canadian woman in Brampton, ON, with T2 diabetes and IBS-M, cooking for a household of five. Represents the primary user Cuizine is built for: newly-diagnosed with chronic conditions, layered constraints, strong cultural identity, high value at stake. *Primary reference: PRD § 3.*

**prompts/.** The directory where versioned agent system prompts live (Curator, Chef, Pantry, plus Planner/Sourcing in v2 and Observer in v3) and user-facing copy strings (Settings messages, error strings). Separate from the codebase per `build-conventions.md` Section 8 because prompts iterate at a different cadence than code. *Primary reference: `build-conventions.md` Section 8.*

**Provenance.** The metadata on every constraint per `constraint-engine-spec.md` Section 9: when it was added, by whom (which agent or interaction), the user's original phrasing, and the modification history. Preserved through soft deletes for audit purposes. *Primary reference: `constraint-engine-spec.md` Section 9. Also used in: `data-model.md` Section 3.*

### R

**Recovery passphrase.** The six-word Diceware-style passphrase generated for each signed-in Cuizine user per `local-first-sync.md` Section 4. Used to derive the master encryption key via Argon2id. The user's secret; Cuizine holds no copy, no escrow, no admin override. Required to access the encrypted sync container on a new device. *Primary reference: `local-first-sync.md` Section 4. Also used in: `security-and-privacy.md` Section 5.*

**Regeneration.** The feedback loop from `agent-architecture.md` Section 3 where the validator's rejection causes the orchestrator to re-invoke the Chef with updated context (the violation details) to produce a new suggestion. A user-invisible loop in the happy path; if multiple regenerations fail, the conflict resolver surfaces options to the user. *Primary reference: `agent-architecture.md` Section 3.*

**Room.** Google's first-party SQLite persistence library for Android, Cuizine's local data layer per ADR 0016, replacing Drift from the superseded ADR 0005. Maps the logical schema from `data-model.md` to `@Entity` classes and `@Dao` query interfaces, with `@TypeConverter`s (via kotlinx.serialization) handling the JSON payload columns. The logical schema is unchanged from the original design; only the implementation library changed. *Primary reference: ADR 0016. Also used in: `data-model.md`, `build-conventions.md` Section 3.*

**Religious & Cultural constraint.** A severity tier per ADR 0009 co-equal with Medical. For constraints rooted in religious observance, cultural identity, or cultural dietary traditions. *Primary reference: ADR 0009.*

**Require constraint.** A constraint type from `constraint-engine-spec.md` Section 3 specifying things that must be present, with single-meal, daily, or weekly windows. Example: "at least one iron-rich food per day." *Primary reference: `constraint-engine-spec.md` Section 3.*

**Riverpod.** *(Superseded.)* The Flutter state management library originally chosen in `build-conventions.md` under ADR 0005. Replaced by Orbit MVI when Cuizine moved to native Kotlin (ADR 0016). See **Orbit MVI**. *Primary reference: ADR 0016 (supersedes ADR 0005).*

### S

**Scope (constraint scope).** The structured object on every constraint specifying when the constraint is active. Five scope dimensions per `constraint-engine-spec.md` Section 5: temporal, contextual, location, household, profile. A constraint is in the active set only when all applicable dimensions match. *Primary reference: `constraint-engine-spec.md` Section 5.*

**Secondary personas.** Aisha and Marcus per PRD § 3. Aisha is a Muslim Canadian observing Ramadan, designed to stress-test temporal scope (sunrise/sunset solar bases, composite scopes, date-bounded windows). Marcus is a v2 caregiver case with a dependent Emma profile, designed to stress-test multi-profile households. *Primary reference: PRD § 3.*

**Severity tier.** One of the four constraint severity levels per ADR 0009: Inviolable, Medical, Religious & Cultural (co-equal with Medical), and Preference. Determines validator behavior, AI fallback behavior, and conflict resolution behavior. *Primary reference: ADR 0009. Also used in: `constraint-engine-spec.md` Sections 4 and 7.*

**Severity-zero event.** A medical or religious constraint violation that reached the user — the worst failure mode Cuizine can produce. Triggers immediate investigation during alpha per `alpha-feedback-and-iteration.md` Section 4, with the standard being "zero tolerance." *Primary reference: `alpha-feedback-and-iteration.md` Section 4.*

**Shared view.** The minimal projection of a linked partner's constraint data exchanged between two v3 linked partners per `security-and-privacy.md` Section 8. Contains only the subset of constraints relevant to meal planning (active avoid, limit, and contextual at appropriate severity tiers, plus cultural context) — deliberately excludes preference constraints, provenance, suggestion history, cooked meals. *Primary reference: `security-and-privacy.md` Section 8.*

**Signed-out mode.** The architectural posture from ADR 0011 where a user uses Cuizine without creating a Firebase Auth account. Routes inference directly to LLM providers, stores all data locally (no encrypted sync container in Firestore), and enables the strongest privacy posture available short of v3 BYOK. *Primary reference: ADR 0011.*

**SKILL.md.** Files in `/mnt/skills/` that provide best-practice guidance for specific code generation scenarios. Referenced during the coding-agent build. *Primary reference: contextual; outside Cuizine's foundation set.*

**Soft delete.** The pattern from `data-model.md` Section 3 where constraints and other rows are marked with a `removed_at` timestamp rather than being hard-deleted. Preserves provenance for audit purposes and enables the sync layer's conflict resolution (tombstones propagate across devices). *Primary reference: `data-model.md` Section 3.*

**Sourcing agent.** A v2+ agent per `agent-architecture.md` Section 10 that translates Chef or Planner outputs into grocery shopping lists and handles the handoff to Instacart. Handles ingredient deduplication, quantity aggregation, and substitution when Instacart doesn't carry exact items. *Primary reference: `agent-architecture.md` Section 10.*

**Subscription state.** The user's current billing tier and associated metadata, stored in the `subscription_state` table per `monetization-and-billing.md` Section 5. Part of the encrypted sync container. Single row per user, includes the locked price fields that implement the existing-users-keep-original-price commitment. *Primary reference: `monetization-and-billing.md` Section 5.*

**Subsystem.** One of the six architectural layers from `technical-architecture.md` Section 2: data and persistence, constraint engine, agent layer, food data layer, sync and encryption, UI. Each subsystem has its own folder in `lib/` per `build-conventions.md` Section 3. *Primary reference: `technical-architecture.md` Section 2.*

**Sync container.** See Encrypted sync container.

### T

**Temporal scope.** One of the five scope dimensions from `constraint-engine-spec.md` Section 5, with six sub-kinds: always, weekly (weekday set), daily_window (time range within each day), date_bounded (calendar date range), phase_bounded (tied to another condition), composite (AND/OR of the above). Supports sunrise/sunset solar bases for religious fasts. The dimension that Aisha's persona stress-tests. *Primary reference: `constraint-engine-spec.md` Section 5.*

**Three-agent v1.** The v1 agent lineup per `agent-architecture.md`: Curator, Chef, Pantry (lightweight). Structured to grow additively — v2 adds Planner and Sourcing; v3 adds Observer — without requiring rewrites. *Primary reference: `agent-architecture.md` Section 1.*

**Tier.** One of the three Cuizine subscription tiers: Free, Cuizine, Cuizine Family. See respective entries. *Primary reference: `monetization-and-billing.md` Section 3.*

**TierPolicy.** The centralized Kotlin module from `monetization-and-billing.md` Section 5 that owns all tier-related logic — checks whether a feature is allowed for a given tier, determines the tier from subscription state, surfaces graceful refusals. The only place in the code that knows what tier a user is on. *Primary reference: `monetization-and-billing.md` Section 5.*

**Tink.** Google's cryptographic library, Cuizine's encryption implementation per ADR 0016 and `local-first-sync.md`, replacing the Dart `cryptography` package referenced under the superseded ADR 0005. Provides the misuse-resistant AES-256-GCM AEAD primitive for sync blob encryption. (Argon2id key derivation, which Tink does not provide, comes from a dedicated Argon2 library for Android.) The encryption *design* is unchanged from the original; only the implementation library changed. *Primary reference: `local-first-sync.md` Section 3, ADR 0016.*

**Tombstone.** A soft-delete marker on a constraint or other syncable row. Propagates across devices in the three-phase sync protocol so that deletions are honored globally per `local-first-sync.md` Section 6. *Primary reference: `local-first-sync.md` Section 6.*

**Trial (14-day).** The free trial of paid tiers per `monetization-and-billing.md` Section 4. Does not require payment method upfront. Includes all features of the chosen tier. Features a calm day-7 mid-trial offer for early conversion. Expires gracefully if no payment method is added, with no data loss. *Primary reference: `monetization-and-billing.md` Section 4.*

**Trust posture.** Cuizine's public commitments about what it does and does not do with user data, across layers — zero plaintext at Cuizine, client-side encryption, no master key, no content telemetry, export-and-walk-away, no dark patterns. The two-list discipline in `security-and-privacy.md` Section 11 is the accountable form of the trust posture. *Primary reference: `vision.md`, `security-and-privacy.md` Section 11. Used in: every foundation doc.*

### U

**USDA FoodData Central.** The US Department of Agriculture's food composition database per ADR 0012. Layer 3 of the three-layer food data architecture. Provides nutritional composition data for standard foods. *Primary reference: ADR 0012.*

### V

**Validator.** The deterministic post-generation component from `constraint-engine-spec.md` Section 7 and ADR 0010 that checks every agent-produced suggestion against the active constraint set before it reaches the user. Five-step algorithm: active set computation, Food Data Provider resolution, per-constraint check, severity-scoped unknown ingredient handling, aggregation. Gets the exhaustive test suite from `testing-strategy.md` Section 7 with near-100% line coverage. *Primary reference: `constraint-engine-spec.md` Section 7, ADR 0010.*

**Vibecoding.** The working style where the founder directs an AI coding agent to implement Cuizine based on the foundation docs, with the agent handling most code generation and the founder providing direction, judgment, and oversight. The working model Cuizine's build is structured around per `build-conventions.md` and `roadmap.md`. *Primary reference: contextual; no single foundation doc section defines this term.*

### Z

**Zero-data-retention (ZDR).** The option provided by Anthropic (and equivalents from Google and OpenAI) where inference content is not retained by the provider after the API call completes. Enabled by default for Cuizine's routing per ADR 0006 as a mitigation for the upstream provider visibility residual risk. *Primary reference: ADR 0006. Also used in: `security-and-privacy.md` Section 5.*

## 3. Cross-references

### What this document references

- Every foundation doc (via definitions of terms defined in them)
- Every ADR (via definitions of terms introduced in them)

### What this document defers to source docs

- The substance of every concept — this glossary provides the definition and the pointer; the depth is in the source doc
- Any concept that changes in meaning — this doc updates to match the canonical source
- The addition of new terms — new terms appear first in their originating doc, then get added here in the next revision cycle

### How the agent should use this doc

When the coding agent encounters a term it's uncertain about, this glossary is the first stop. The definition here resolves the immediate question; the "primary reference" pointer sends the agent to the doc that provides the substance.

When the agent surfaces a question that depends on terminology, using the glossary terms consistently helps the founder and the agent communicate precisely. "The active set isn't filtering by contextual scope correctly" is clearer than "the thing that the engine builds isn't filtering right."

When the agent notices that a term is used in a foundation doc with a meaning that doesn't match the glossary, the discrepancy gets surfaced — either the glossary is wrong (update the glossary) or the doc is drifting (fix the doc), but silent disagreement between the two is the failure mode to avoid.

---

*End of `glossary.md` v1 (initial draft). Next revision will add terms introduced in v2 and v3 development, update any definitions that shift as the foundation refines, and clarify any terms that turn out to be ambiguous in practice.*
