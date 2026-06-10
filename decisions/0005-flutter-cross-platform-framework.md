# 0005 — Flutter as the cross-platform framework

**Status:** SUPERSEDED by ADR 0016 (2026-04-15)
**Date:** 2026-04-11
**Superseded note:** This decision chose Flutter + Drift + Riverpod, partly for eventual iOS readiness. When Cuizine committed to Android-only (ADR 0015), the cross-platform rationale fell away and the framework decision was reopened on Android-only merits. ADR 0016 supersedes this ADR with a native Kotlin + Jetpack Compose + Orbit MVI + Room stack. This ADR is retained for the historical record; its reasoning no longer governs the build. See ADR 0015 and ADR 0016.

## Context

Cuizine ships Android-first in v1 (closed alpha APK distribution) and v2 (Play Store launch), with iOS as a v3 expansion target alongside English-speaking global markets. The choice of frontend framework is a foundational decision because every screen, every state-management pattern, every offline-first behavior, and every animation will be written against it. The decision affects iteration speed during vibecoding (which determines how quickly the coding agent can produce and refine real UI), the cost of v3 iOS expansion (rebuild vs flag-flip), and the maturity of the local-first ecosystem available to us.

The relevant options are native Android (Kotlin + Jetpack Compose), Flutter (Dart, single codebase targeting Android, iOS, desktop, and web), and React Native (JavaScript/TypeScript with Expo).

## Decision

**Cuizine is built in Flutter.** The codebase targets Android in v1 and v2. iOS becomes a flag-flip in v3 by virtue of Flutter's cross-platform architecture, not a parallel rebuild.

The local-first SQLite layer will use **Drift** (formerly Moor) as the type-safe SQLite library, since it's the most mature and best-documented option in the Flutter ecosystem and matches the structured-data needs of the constraint engine cleanly.

State management approach is deferred to `build-conventions.md` but will be a deliberate, single-pattern choice (likely Riverpod or Bloc) documented and enforced consistently across the codebase, so the agent doesn't drift between paradigms mid-build.

## Consequences

**Positive.** v3 iOS expansion becomes a flag-flip rather than a full second codebase rebuild — same architectural discipline as Canada-first / NA-ready (ADR 0001) and dependent-profiles-from-day-one (ADR 0004). Flutter's hot reload and controlled rendering dramatically speed up the vibecoding iteration loop, which matters more than people realize for AI-assisted builds. The Flutter ecosystem has invested heavily in offline-first patterns (Drift, Sembast, Isar), which fits Cuizine's local-first trust posture well. Cross-platform consistency is meaningfully better than React Native's, which matters for an app whose UI quality is part of the trust story.

**Negative.** The coding agent (and most LLM coding agents) have seen more React/Kotlin in their training data than Flutter/Dart. The agent will be slightly less fluent in idiomatic Flutter than in idiomatic React Native or native Android. Mitigation: `build-conventions.md` will be unusually explicit about Flutter patterns (state management, folder structure, naming, widget composition rules) to compensate. This is real upfront discipline cost.

Dart-the-language is quietly stable but is not the JavaScript ecosystem. Some niche packages we might want for v3 features may not exist in Flutter and will need to be written as platform channels in Kotlin/Swift. This is a bounded cost and a known pattern.

**Neutral.** Cuizine bets on Google's continued investment in Flutter. Flutter has been stable and improving for years and Google's commitment looks healthy at the time of this decision, but it's worth naming as a dependency.

## Alternatives considered

**Native Android (Kotlin + Jetpack Compose).** Rejected because v3 iOS would require a full second codebase rebuild — every screen, every state pattern, every form, written twice. For a two-person team with an AI coding agent, the rebuild cost is brutal and there's no architectural mitigation.

**React Native (with Expo).** Considered seriously and rejected. The agent would likely be more fluent here, which is a real benefit. But cross-platform consistency is weaker (more platform-specific code), the offline-first ecosystem is less mature than Flutter's (no Drift equivalent), the new architecture migration is still ongoing, and the "feels native" quality varies more across screens. For an app that needs heavy local-first state management, complex offline behavior, and possibly on-device AI inference later, Flutter's stronger control plane is the better fit.

**Native Android now, Flutter or RN later.** Rejected as a false economy. Rewriting the app in v3 to support iOS would consume more engineering effort than the v3 features themselves. Better to take the slightly steeper Flutter learning curve in v1 and have the cross-platform foundation ready when v3 needs it.

## Related

- See `vision.md` § Launch strategy (the v3 iOS option this enables)
- Pairs with 0001 (architectural readiness for future expansion baked in from day one)
- Build conventions for Flutter patterns to be defined in `build-conventions.md`
