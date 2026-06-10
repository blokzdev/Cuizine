# ADR 0015 — Android-Only Scope (No iOS or Other Platforms)

**Status:** Accepted
**Date:** 2026-04-15
**Supersedes:** The cross-platform/iOS aspects of ADR 0001 (which named iOS launch as part of v3) and the cross-platform motivation in ADR 0005 (Flutter was chosen partly for iOS readiness)
**Related:** ADR 0016 (native Kotlin + Jetpack Compose), ADR 0001 (launch sequence), ADR 0005 (now superseded re: framework)

## Context

The original foundation (ADR 0001, ADR 0005, `roadmap.md` Section 8, `technical-architecture.md` Section 7.5) assumed Cuizine would expand to iOS during v3 global expansion. This assumption drove the choice of Flutter as a cross-platform framework in ADR 0005 — the architectural readiness pattern held that v1/v2 would be Android-only in practice but the codebase would be iOS-ready, so v3 could add an iOS target without a rewrite.

During foundation review, the founder reconsidered the platform strategy and decided to focus exclusively on Android for the foreseeable future, with no planned expansion to iOS or other platforms.

The reasoning that supports an Android-only focus:

1. **Cuizine's target audience skews heavily Android.** The primary persona (Sukhi, a Punjabi Canadian in Brampton) is part of a South Asian immigrant community, and South Asian populations both in diaspora and in eventual v3 markets (India is overwhelmingly Android) skew strongly toward Android. iOS skews toward higher-income North American and Western European users, which is not the center of gravity for Cuizine's "newly-diagnosed, layered-constraint, strong-cultural-identity" persona. Android-only is well-matched to who Cuizine actually serves, not merely an acceptable compromise.

2. **Global Android market share (~70%+) and the specific composition of Cuizine's audience** mean the addressable market under Android-only remains large and is concentrated in exactly the user segments Cuizine targets.

3. **Solo-founder, completion-driven scope.** Maintaining a single platform dramatically reduces engineering surface, testing matrix, platform-specific bug classes, and operational complexity. For a solo founder vibecoding with an AI agent, this focus is force-multiplying.

4. **The iOS assumption was the single largest constraint forcing the cross-platform framework choice.** Removing it unlocks the option to use the best-in-class native Android stack (see ADR 0016).

## Decision

**Cuizine is an Android-only application across all versions (v1, v2, v3).** There is no planned iOS, web, desktop, or other-platform version.

- v1 alpha: Android (APK distribution), as before.
- v2 public launch: Android via Google Play Store, North America, as before.
- v3 global expansion: Android in new English-speaking geographies (UK, EU, and other markets). The "global expansion" of v3 is geographic, not cross-platform. iOS is explicitly out of scope.

This decision is revisitable in principle — if real data after v2 launch shows substantial demand from iOS users that materially affects Cuizine's mission or viability, a future ADR can reopen it. But Android-only is the committed posture, not a temporary stopgap, and the architecture should be built for it wholeheartedly rather than hedged.

## Consequences

### Positive

- Unlocks the native Kotlin + Jetpack Compose stack (ADR 0016) for best-in-class Android feel, performance, and platform integration.
- Dramatically simplifies the testing matrix (one platform, no cross-platform behavioral differences to reconcile).
- Simplifies several v3 work items: the cross-account linked-partner feature, the global-expansion compliance work, and the testing strategy all get simpler without a second platform.
- Removes the iOS-readiness constraint that shaped the framework choice, allowing the stack to be chosen purely on Android merits.
- Reduces operational overhead (one app store, one signing process, one set of platform conventions).

### Negative

- Caps the total addressable market by excluding iOS users (a smaller slice for Cuizine's persona, but non-zero).
- Some investors or acquirers reflexively expect iOS; Android-only may require explanation in those contexts.
- If the decision is ever reversed, adding iOS to a native Kotlin codebase means a separate native iOS (Swift) app or a cross-platform rebuild — a larger cost than adding iOS to a Flutter codebase would have been. This is the accepted trade-off: we are trading future cross-platform optionality for present native quality and focus.

### Neutral

- Most of the foundation is unaffected because it was written at the architecture level rather than the platform level. The constraint engine, agent architecture, data model logical schema, sync/encryption design, monetization, security posture, and roadmap phase structure all survive unchanged.

## Alternatives considered

1. **Keep Flutter and stay iOS-ready (the original plan).** Rejected because the founder has decided iOS is not a goal, which removes the primary reason Flutter was chosen and leaves Cuizine paying the small but real costs of a cross-platform abstraction layer for a benefit it will never use.

2. **Android-first but iOS-flexible (build native Android now, accept a rebuild later if iOS becomes necessary).** Considered. This is effectively what ADR 0016 enables — native now, with a future rebuild as the (accepted) cost if the platform decision ever reverses. The difference from this ADR is framing: this ADR commits to Android-only as the posture, while remaining honestly revisitable, rather than treating iOS as a deferred-but-expected milestone.

3. **Kotlin Multiplatform (KMP) to keep a cross-platform door open with native Android UI.** Considered and rejected for now. KMP would let business logic be shared with a future iOS app while using native UI on each platform. But it adds real complexity (KMP tooling, multiplatform module structure, careful dependency selection) for a benefit (iOS readiness) we just decided not to need. If iOS is genuinely off the table, KMP's main advantage is moot, and the simpler choice is single-platform native Android. This is why ADR 0016 chooses Room over SQLDelight and standard Android patterns over Decompose — the multiplatform-oriented options lose their rationale under Android-only.

## Related

- ADR 0001 — launch sequence; amended so that v3 global expansion is geographic (Android in new markets) rather than cross-platform (iOS)
- ADR 0005 — Flutter/Drift; superseded by ADR 0016
- ADR 0016 — native Kotlin + Jetpack Compose + Orbit MVI + Room
- `roadmap.md` Section 8 — v3 expansion plan, updated to remove iOS
- `technical-architecture.md` Section 7.5 — v2/v3 deltas, updated to remove iOS
- `launch-readiness-checklist.md` Section 8 — v3 launch checklist, updated to remove iOS gates
