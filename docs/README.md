# Cuizine foundation docs — index & reading order

This folder is the **foundation document set** for Cuizine: the source of truth
the codebase is built against. When code and a doc disagree, the doc wins until
a deliberate decision changes it (`build-conventions.md` §2). Architectural
decisions are recorded as ADRs in [`../decisions/`](../decisions/README.md).

## Reading order

Read top to bottom the first time. `vision.md` is the constitution — every other
doc serves it.

| # | Doc | What it gives you |
|---|---|---|
| 1 | [`vision.md`](vision.md) | The constitution: tone, the five pillars, launch sequence, trust posture. |
| 2 | [`PRD.md`](PRD.md) | Primary persona (Sukhi), v1 user journey, in/out of scope, acceptance criteria. |
| 3 | [`technical-architecture.md`](technical-architecture.md) | The system map: six subsystems, component list, trust boundaries, named dependencies. |
| 4 | [`build-conventions.md`](build-conventions.md) | How code is built: repo structure, Orbit MVI patterns, naming, decision protocol, forbidden behaviors. |
| 5 | [`roadmap.md`](roadmap.md) | The seven-phase build sequence, exit criteria, per-phase decision checkpoints. |

## Deep-dive specs (read per subsystem)

| Doc | Subsystem |
|---|---|
| [`constraint-engine-spec.md`](constraint-engine-spec.md) | The constraint graph: 5 types, 5 scopes, active-set, validator, conflict resolution, provenance. |
| [`agent-architecture.md`](agent-architecture.md) | Agent archetype, orchestrator routing, per-agent contracts and prompts, the regeneration loop. |
| [`data-model.md`](data-model.md) | Every persistent structure field-by-field, profile ownership, schema versioning & migration. |
| [`local-first-sync.md`](local-first-sync.md) | Encryption (Tink, AES-256-GCM, Argon2id), recovery passphrase, Firestore sync, conflict policy. |
| [`monetization-and-billing.md`](monetization-and-billing.md) | `TierPolicy`, `BillingService`, the three tiers, the 14-day trial, the v1 alpha bypass. |
| [`ui-ux-spec.md`](ui-ux-spec.md) | Screens, component library, navigation shell, the mock/real container seam. |
| [`security-and-privacy.md`](security-and-privacy.md) | Threat model, upstream provider retention audit, recovery-loss policy, incident response. |
| [`testing-strategy.md`](testing-strategy.md) | Hard-cases suite, validator tests, agent eval harness, integration journeys, fixtures. |

## Reference

| Doc | Use |
|---|---|
| [`glossary.md`](glossary.md) | Definitions of Cuizine terms (constraint graph, severity tiers, active set, etc.). |
| [`open-questions.md`](open-questions.md) | Consolidated deferred decisions and unresolved questions. |
| [`alpha-feedback-and-iteration.md`](alpha-feedback-and-iteration.md) | The v1 alpha feedback process and iteration cycle. |
| [`launch-readiness-checklist.md`](launch-readiness-checklist.md) | Go/no-go gates for each launch milestone. |

> These docs are living specifications, not write-once artifacts. As the build
> teaches us things, docs are updated with deliberate intent per
> `build-conventions.md` §7's bidirectional update discipline — never silently.
