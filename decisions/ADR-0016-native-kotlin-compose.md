# ADR 0016 — Native Kotlin + Jetpack Compose (Superseding Flutter/Drift)

**Status:** Accepted
**Date:** 2026-04-15
**Supersedes:** ADR 0005 (Flutter + Drift)
**Related:** ADR 0015 (Android-only scope), ADR 0006 (multi-provider routing — unaffected), ADR 0012 (food data sources — unaffected)

**Amendment note:** The core decision — native Kotlin + Jetpack Compose + Orbit MVI + Room — is unchanged. The original rationale included a "workflow fit" point about bootstrapping the UI in Google AI Studio and continuing in Claude Code. That bootstrapping approach has since been dropped in favor of building the UI directly in Claude Code, UI-first, per `ui-ux-spec.md` Section 10 and the roadmap's Phase 2 (the entire UI built against mock containers before any subsystem exists). The AI-Studio references below are retained as historical reasoning but are no longer operative; the stack decision stands on its other merits (native feel, first-party Jetpack integration, the founder's preference for MVI's separation of concerns, and the state-machine fit of Cuizine's domain), none of which depend on how the code is initially generated.

## Context

ADR 0005 chose Flutter as Cuizine's UI framework and Drift as its persistence layer. A significant part of the Flutter rationale was cross-platform readiness — the expectation (from ADR 0001) that v3 would add iOS, and Flutter would let that happen without a rewrite.

ADR 0015 reverses the iOS assumption: Cuizine is now Android-only across all versions. This removes the primary reason Flutter was chosen and reopens the framework decision on Android-only merits.

On Android-only merits, native Kotlin with Jetpack Compose is the stronger choice:

1. **Native feel.** Jetpack Compose is Google's first-party modern UI toolkit. An app built in Compose inherits Android's design language, motion, system theming (Material You / dynamic color), edge-to-edge behavior, and predictive back gesture natively. Flutter renders its own widgets and gets close, but native Compose delivers the last increment of platform polish that matters for a calm, trust-conveying app like Cuizine.

2. **Performance on the low-end floor.** Native Kotlin has a marginal but real edge on cold-start time, memory footprint, and scroll smoothness — which matters for Cuizine's commitment to work well on low-end Android devices (the 2022 budget device with 3GB RAM in the performance tests).

3. **Platform integration.** Camera (the v2 Pantry agent's barcode, receipt, and fridge-photo features), Google Play Billing, biometrics, system share sheets, and background work are all first-class in native Kotlin rather than accessed through plugin wrappers.

4. **Workflow fit.** Google AI Studio's Android app builder generates native Kotlin/Compose code. Bootstrapping the UI in AI Studio and continuing development in Claude Code (which is fully fluent in Kotlin) becomes a frictionless end-to-end workflow, with no framework-translation step.

5. **No remaining downside.** The thing that made Flutter the right call — write-once-run-on-iOS-too — is precisely the thing Cuizine has chosen not to need. With iOS off the table, native Kotlin's advantages come without an offsetting cost.

## Decision

**Cuizine is built as a native Android application in Kotlin, using Jetpack Compose for UI, Orbit MVI for state management, ViewModel + StateFlow as the underlying reactive primitives, and Room for persistence.**

The full stack:

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3 / Material You)
- **State management:** Orbit MVI, layered on top of Android's ViewModel + StateFlow
- **Persistence:** Room (Google's first-party SQLite ORM for Android), replacing Drift
- **Dependency injection:** Hilt (Google's first-party DI for Android)
- **Concurrency:** Kotlin Coroutines + Flow
- **Networking (food data APIs):** Retrofit + OkHttp (or Ktor client), replacing the Dart HTTP clients
- **Billing:** Google Play Billing Library directly, replacing the `in_app_purchase` Flutter package
- **Cryptography:** Tink (Google's crypto library) or the platform crypto APIs for AES-256-GCM and Argon2id, replacing the Dart `cryptography` package
- **Build system:** Gradle (Kotlin DSL)

### Why Orbit MVI for state management

Within the structured-framework category, Orbit MVI is chosen over the alternatives because:

- **It builds on top of ViewModel + StateFlow rather than replacing them**, composing cleanly with the rest of the standard Android ecosystem (Compose, Hilt, navigation, lifecycle) and keeping Claude Code maximally effective (it knows the underlying primitives intimately).
- **The MVI pattern maps naturally onto Cuizine's domain**, which is fundamentally state-machine-shaped: the constraint conversation, the meal-suggestion flow (requesting → generating → validating → presenting → accepted/rejected → regenerating), the trial lifecycle, and the sync transitions are all explicit state machines.
- **Unidirectional data flow makes complex state debuggable** — every state change is the result of a named intent flowing through a reducer, which matters with the constraint engine, agent layer, sync layer, and billing layer all having nontrivial state.
- **First-class deterministic testability** — Orbit lets tests assert that a given intent produces a given sequence of state changes and side effects, pairing naturally with `testing-strategy.md`'s risk-tiered coverage discipline.
- **Mature, maintained, well-documented, production-proven** — satisfying the "fewest surprising failure modes" criterion.

### Why Room for persistence

Room is chosen over SQLDelight because it is Google's first-party solution, integrates most cleanly with the rest of the Jetpack stack (Compose, coroutines/Flow, Hilt), and is what AI Studio is most likely to generate — keeping the bootstrap workflow frictionless. SQLDelight's primary edge is Kotlin Multiplatform support, which is moot under the Android-only scope (ADR 0015).

## Consequences

### Positive

- Best-in-class native Android feel, performance, and platform integration.
- Frictionless AI Studio → Claude Code bootstrapping workflow (no framework translation).
- The entire stack is first-party Google (Kotlin, Compose, Room, Hilt) plus the well-established Orbit MVI, maximizing documentation, community support, and Claude Code fluency.
- The MVI architecture provides the strong separation of concerns the founder wanted, with deterministic testability that supports the safety-floor testing discipline.

### Negative

- The foundation docs that referenced Flutter/Dart/Drift/Riverpod specifics need updating (build conventions, technical architecture, data model, testing strategy, monetization billing layer, security crypto reference, glossary). This is a tractable update pass because most of the foundation is language-agnostic.
- The constraint engine, agent layer, validator, conflict resolver, etc. — all originally specified with Dart-flavored pseudocode — get reimplemented in Kotlin. The specifications (the algorithms, the contracts, the data shapes) are unchanged; only the implementation language changes.
- If ADR 0015 is ever reversed (iOS becomes a goal), this native Kotlin choice makes cross-platform harder than Flutter would have. This is the explicitly accepted trade-off from ADR 0015.

### Neutral

- The logical data model is unchanged — tables, columns, JSON payloads, indices, soft-delete semantics, schema versioning all translate from Drift to Room without conceptual change.
- The encryption design is unchanged — AES-256-GCM with Argon2id exists in Kotlin crypto libraries exactly as in Dart. The X25519 key exchange for v3 linked partners (security-and-privacy.md Section 8) is equally available.
- The agent architecture, orchestrator, provider routing (ADR 0006), and food data layer (ADR 0012) are all language-agnostic and survive unchanged in design.

## Alternatives considered

1. **Keep Flutter + Drift (ADR 0005).** Rejected because ADR 0015 removed the cross-platform rationale, and on Android-only merits native Kotlin is stronger.

2. **Standard Android state management (ViewModel + StateFlow + Compose state, no MVI framework).** Considered — it's Google's recommended baseline and what AI Studio generates. Rejected in favor of Orbit MVI because the founder explicitly wanted stronger, more opinionated separation of concerns, and Cuizine's state-machine-shaped domain benefits materially from MVI's discipline. Orbit was chosen specifically because it provides MVI *on top of* the standard primitives rather than replacing them, so this isn't a rejection of the standard stack but an enhancement of it.

3. **Decompose for state and navigation.** Rejected because its headline advantage is Kotlin Multiplatform component sharing, which is moot under Android-only (ADR 0015).

4. **Molecule (Compose-runtime-based presentation logic).** Considered for its elegance but rejected as the least conventional option with the smallest community and a subtle mental model (business logic expressed through the Compose recomposition model) that adds debugging sharp edges not worth it for a solo-founder build.

5. **SQLDelight for persistence.** Rejected in favor of Room because SQLDelight's main edge (multiplatform) is moot under Android-only, and Room integrates more cleanly with the first-party Jetpack stack.

## Related

- ADR 0005 — superseded by this ADR
- ADR 0015 — Android-only scope, the decision that reopened the framework choice
- ADR 0006 — multi-provider routing; unaffected (the ModelProvider interface is reimplemented in Kotlin but the design is unchanged)
- ADR 0012 — food data sources; unaffected in design (clients reimplemented with Retrofit/Ktor)
- `build-conventions.md` — updated for Kotlin/Compose/Orbit/Room conventions
- `technical-architecture.md` Section 6 — dependency list updated
- `data-model.md` — Drift references updated to Room
- `testing-strategy.md` — Dart test references updated to Kotlin/JUnit/Compose test references
- `monetization-and-billing.md` Section 6 — `in_app_purchase` updated to Google Play Billing Library
- `security-and-privacy.md` — crypto library reference updated
- `glossary.md` — Flutter/Drift/Riverpod entries updated to Kotlin/Compose/Room/Orbit
