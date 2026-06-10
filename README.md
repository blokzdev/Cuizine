# Cuizine

**The kitchen that knows you.**

Cuizine is an AI-native food companion for people whose eating life has become
too complex for willpower, spreadsheets, or generic recipe apps to handle. It
holds the full picture of who you are — your medical constraints, your beliefs,
your pantry, your week, the cuisine you grew up with — and quietly makes sure
every meal it suggests respects all of it, all the time.

It is not a recipe app with filters. It is a persistent, evolving understanding
of one human's relationship with food, expressed through meals.

> **Status:** Foundation complete, pre-Phase-1 build. This repository currently
> holds the foundation document set (`docs/`) and the architectural decision
> records (`decisions/`). The Android codebase is built against that foundation
> in seven phases — see [`docs/roadmap.md`](docs/roadmap.md) §3.

---

## Who it's for (v1)

**The newly-diagnosed.** Someone whose doctor said "Type 2 diabetes" or "stage 3
kidney disease" or "PCOS" or "IBS" last month, and who is now drowning in dietary
rules they didn't have before. Their pain is acute, their need is daily, and the
cost of getting it wrong is something they already understand. If Cuizine works
for them, it works. The canonical persona is **Sukhi** — a 53-year-old Punjabi
home cook in Brampton, Ontario, newly managing Type 2 diabetes and IBS while
still cooking for a four-generation household (see [`docs/PRD.md`](docs/PRD.md) §2).

## The five-pillar thesis

1. **Layered constraints respected automatically** — medical, religious,
   preference, temporal, contextual, all active at once, with conflict
   resolution and provenance. *(v1 leads here.)*
2. **Pantry-aware planning** — what's in the fridge, what's expiring, what's on
   sale, woven into every suggestion.
3. **Long-term health intelligence** — trends, predictions, gentle warnings.
4. **Instant context adaptation** — Ramadan starts tomorrow, you're flying to
   Tokyo Friday, your blood pressure spiked: the app absorbs it and keeps going.
5. **Cultural & contextual fluency** — food as culture, not just nutrients.

## What Cuizine is *not*

Not a calorie tracker, not a fitness app, not a social network, not a grocery
delivery service, not a medical device, not multilingual at launch, and **not
cross-platform — Android-only across all versions** (per [ADR 0015](decisions/ADR-0015-android-only-scope.md)).

---

## Tech stack

| Layer | Choice | Reference |
|---|---|---|
| Language / UI | **Kotlin + Jetpack Compose** (Material 3) | [ADR 0016](decisions/ADR-0016-native-kotlin-compose.md) |
| State management | **Orbit MVI** on ViewModel + StateFlow | [ADR 0016](decisions/ADR-0016-native-kotlin-compose.md) |
| Persistence | **Room** (SQLite), local-first source of truth | [ADR 0016](decisions/ADR-0016-native-kotlin-compose.md) |
| DI | **Hilt** | [ADR 0016](decisions/ADR-0016-native-kotlin-compose.md) |
| LLM providers | **Anthropic + Google (Gemini) + OpenAI** via a `ModelProvider` interface | [ADR 0006](decisions/0006-multi-provider-per-agent-routing.md) |
| Backend (optional) | **Firebase** — Auth, Firestore (encrypted blob sync), Cloud Functions (LLM proxy) | [ADR 0011](decisions/0011-optional-backend-with-capability-tiers.md) |
| Encryption | **Tink** — client-side AES-256-GCM, Argon2id key derivation | [`docs/local-first-sync.md`](docs/local-first-sync.md) |
| Food data | **USDA FoodData Central + Open Food Facts**, cache-first | [ADR 0012](decisions/0012-food-data-sources-and-taxonomy-strategy.md) |
| Grocery (v2) | **Instacart** as the sole sourcing layer | [ADR 0003](decisions/0003-instacart-as-sole-grocery-integration.md) |
| Billing (v2) | **Google Play Billing** | [ADR 0007](decisions/0007-three-tier-subscription-with-free-local-first.md) |

The v1 agent set is deliberately small — **Curator** (owns the constraint
graph), **Chef** (generates suggestions), and a lightweight **Pantry** agent —
coordinated by a deterministic **Orchestrator** with a non-skippable
**Post-Generation Validator** (per [ADR 0010](decisions/0010-post-generation-constraint-validation.md)).
Planner + Sourcing agents arrive in v2; the Observer agent in v3.

## Trust posture

Cuizine touches medical conditions, religious practice, household composition,
and cultural identity at once. Accordingly:

- **Local-first.** The user's device is the source of truth.
- **End-to-end encrypted sync.** Cloud backup exists; Cuizine cannot read it.
- **No analytics on personal data. Ever.** No ad SDKs, no A/B framework, no
  social SDKs (see [`docs/technical-architecture.md`](docs/technical-architecture.md) §6).
- **Export and walk away, always.**

## Launch sequence

- **v1 — Closed alpha (Canada).** Sideloaded APK to 15–25 hand-picked users.
  Single-profile, all five pillars functional. ([ADR 0001](decisions/0001-canadian-first-alpha-north-american-v2.md))
- **v2 — North American public launch.** Play Store + cuizine.ai, three
  subscription tiers, multi-profile households.
- **v3 — Global expansion.** New English-speaking markets (Android), Observer
  agent, linked partners, BYOK.

---

## Repository layout

```
Cuizine/
├── docs/         Foundation document set (vision, PRD, architecture, specs)
├── decisions/    Architectural Decision Records (ADRs)
├── CLAUDE.md     Operating manual for the coding agent — read this first
└── README.md     This file
```

The Android application module (`app/`), `prompts/`, `assets/`, `scripts/`, and
the Gradle build are created in **Phase 1** of the build per
[`docs/build-conventions.md`](docs/build-conventions.md) §3.

## Where to start

- **New to the project?** Read [`docs/vision.md`](docs/vision.md) (the
  constitution), then [`docs/PRD.md`](docs/PRD.md), then
  [`docs/technical-architecture.md`](docs/technical-architecture.md).
- **Building it?** Read [`CLAUDE.md`](CLAUDE.md) and
  [`docs/build-conventions.md`](docs/build-conventions.md), then start Phase 1
  of [`docs/roadmap.md`](docs/roadmap.md) §3.
- **Full reading order:** [`docs/README.md`](docs/README.md).
