# Architectural Decision Records (ADRs)

This folder records the *why* behind Cuizine's significant architectural choices
— the answer to "why did we do it this way?" six months from now. The foundation
docs in [`../docs/`](../docs/README.md) reference these ADRs as decision context.

## When to write a new ADR

Write one when a choice between architectural options has been made, a foundation
doc needs to change in a way that affects multiple downstream docs, a principle
is being clarified, or a previously-deferred decision is being resolved. Do
**not** write one for local implementation details, bug fixes, refactors without
behavioral change, or prompt iterations. Full discipline in
[`../docs/build-conventions.md`](../docs/build-conventions.md) §9.

ADR numbers are **monotonic and never reused**. The next ADR after 0016 is 0017.
A superseded ADR gets a fresh number and references the one it supersedes.

## Format

```markdown
# {NNNN} — {Short title}

**Status:** Accepted | Superseded | Proposed
**Date:** YYYY-MM-DD

## Context
## Decision
## Consequences
## Alternatives considered
## Related
```

## The records

| ADR | Title | Status |
|---|---|---|
| [0001](0001-canadian-first-alpha-north-american-v2.md) | Canadian-first alpha, North-American-ready v2 | Accepted |
| [0002](0002-cultural-fluency-as-fifth-pillar.md) | Cultural fluency as the fifth pillar | Accepted |
| [0003](0003-instacart-as-sole-grocery-integration.md) | Instacart as the sole grocery integration | Accepted |
| [0004](0004-multi-profile-households-structure-ownership-flows.md) | Multi-profile households: structure, ownership, flows | Accepted |
| [0005](0005-flutter-cross-platform-framework.md) | Flutter cross-platform framework | **Superseded by 0016** |
| [0006](0006-multi-provider-per-agent-routing.md) | Multi-provider, per-agent routing | Accepted |
| [0007](0007-three-tier-subscription-with-free-local-first.md) | Three-tier subscription with a free local-first tier | Accepted |
| [0008](0008-byok-deferred-to-v3.md) | BYOK deferred to v3 | Accepted |
| [0009](0009-constraint-conflict-resolution-policy.md) | Constraint conflict-resolution policy | Accepted |
| [0010](0010-post-generation-constraint-validation.md) | Post-generation constraint validation | Accepted |
| [0011](0011-optional-backend-with-capability-tiers.md) | Optional backend with capability tiers (canonical) | Accepted |
| [0011 (draft)](0011-minimal-backend-thin-llm-proxy.md) | Minimal backend / thin LLM proxy (early draft) | **Superseded / absorbed by canonical 0011** |
| [0012](0012-food-data-sources-and-taxonomy-strategy.md) | Food data sources and taxonomy strategy | Accepted |
| [ADR-0015](ADR-0015-android-only-scope.md) | Android-only scope | Accepted |
| [ADR-0016](ADR-0016-native-kotlin-compose.md) | Native Kotlin + Jetpack Compose + Orbit MVI + Room | Accepted |

> **Note on the two 0011 files and the numbering gaps.** There are two ADR-0011
> files by design: `0011-optional-backend-with-capability-tiers.md` is the
> canonical decision; `0011-minimal-backend-thin-llm-proxy.md` is an earlier,
> narrower draft retained for historical context (it self-documents as
> superseded). ADR 0005 (Flutter) was superseded by ADR 0016 (native Kotlin).
> Numbers 0013 and 0014 are reserved — they are written during the alpha-to-v2
> transition (v3 peer-coordination security model and v2 pricing finalization
> respectively, per `../docs/roadmap.md` §5).
