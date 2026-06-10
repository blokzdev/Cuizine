# CLAUDE.md — Operating manual for the Cuizine coding agent

You are the coding agent building **Cuizine**, a local-first native Android app
(Kotlin + Jetpack Compose) that helps people manage layered medical, religious,
cultural, and contextual dietary constraints. This file is your entry point.
Read it fully before writing any code, and re-read §"Decision protocol" and
§"Forbidden behaviors" frequently — they are the parts most easily forgotten
under the pressure of "let me just get this feature done."

> **The foundation docs are the source of truth.** When code and a foundation
> doc disagree, the doc wins until a deliberate decision changes it (per
> `docs/build-conventions.md` §2, Principle 1). You do not silently invent
> architecture that contradicts the docs. When in doubt, **surface, don't guess.**

---

## 1. Read these first (reading order)

The full set lives in `docs/` with a reading-order index at `docs/README.md`.
The ADRs live in `decisions/` (index at `decisions/README.md`). At minimum,
before touching code, read:

1. **`docs/vision.md`** — the constitution. Tone, the five pillars, the launch
   sequence, the trust posture. When a build decision conflicts with this doc,
   this doc wins.
2. **`docs/PRD.md`** — the persona (Sukhi), the v1 user journey, what's in and
   out of v1, the acceptance criteria.
3. **`docs/technical-architecture.md`** — the system map: six subsystems, the
   component list, the trust-boundary diagram, the named-dependency list.
4. **`docs/build-conventions.md`** — *your operating manual for how to write
   code*: repo structure, Kotlin/Compose + Orbit MVI patterns, naming, the
   decision protocol, the forbidden-behaviors list.
5. **`docs/roadmap.md`** — the seven-phase build sequence and the per-phase
   decision checkpoints.

Then, per the subsystem you are working on: `constraint-engine-spec.md`,
`agent-architecture.md`, `data-model.md`, `local-first-sync.md`,
`monetization-and-billing.md`, `ui-ux-spec.md`, `testing-strategy.md`,
`security-and-privacy.md`. Reference docs: `glossary.md`, `open-questions.md`,
`alpha-feedback-and-iteration.md`, `launch-readiness-checklist.md`.

## 2. What exists right now

This repository currently contains **only the foundation** — `docs/` and
`decisions/`. There is **no Android code yet.** The application module (`app/`),
`prompts/`, `assets/`, `scripts/`, and the Gradle build are created in
**Phase 1**. Do not assume any code structure exists until you have created it
per `docs/build-conventions.md` §3. At build kickoff the agent also creates the
operational ledgers at the repo root — `PROGRESS.md`, `DECISION-LOG.md`,
`SETUP.md`, `FOUNDER-FEEDBACK.md`, `PHASE-REPORTS/` — see §9.

## 3. The build sequence (where to begin)

v1 is built **UI-first** in seven phases (`docs/roadmap.md` §3). The whole UI is
built against mock containers in Phase 2, then each subsystem wires its real
implementation into the already-built UI (mock and real containers expose the
*identical* Orbit MVI `State` and `Intents`, so screens never change — they just
get a real data source).

| Phase | What gets built |
|---|---|
| **1. Scaffolding** | Repo structure, Gradle + version catalog, Room setup, Compose shell + Material 3 theme, Orbit MVI + Hilt, lint/test scaffolding. *Start here.* |
| **2. UI + mock data** | Every v1 screen + component library, backed by mock containers returning the canonical fixtures. |
| **3. Constraint engine + validator** | The heart of pillar 1. The 5 constraint types, 5 scope dimensions, active-set computation, deterministic validator, conflict resolution, hard-cases test suite. Wire into Profile UI. |
| **4. Food data layer** | Food Data Provider (cache → bundle → USDA / Open Food Facts), severity-scoped AI fallback. Feeds the validator. |
| **5. Agents + orchestrator** | Curator, Chef, lightweight Pantry, the orchestrator, the `ModelProvider` adapters, the regeneration loop, the prompts. Wire into Today/conversation UI. |
| **6. Sync + encryption + billing** | Tink encryption, recovery passphrase, Firestore sync, `TierPolicy` + `BillingService` (alpha bypass active). Wire into Settings UI. |
| **7. Integration polish** | Replace all remaining mocks, run the full Sukhi day-0→day-30 journeys on real subsystems, on-device tone/texture refinement. |

**→ The next action for a fresh build is Phase 1.** Begin with the phase-kickoff
protocol below.

## 4. The phase-kickoff protocol

Every phase begins this way (this is what activates the roadmap's per-phase
**Decision checkpoints** — `docs/roadmap.md` §2, Principle 6):

1. **Read the phase** in `docs/roadmap.md` §3 and the foundation docs it
   references.
2. **Surface that phase's Decision checkpoints** — each is tagged
   `[data-driven]`, `[research-informed]`, or `[both]`. For
   `[research-informed]` checkpoints (library versions, provider/API currency,
   Material 3 evolution), research current state first. Research informs the
   proposal; it never by itself authorizes a change.
3. **Resolve each checkpoint per the active operating mode**
   (`docs/roadmap.md` §2, Principle 6, "Operating modes"):
   - **Interactive mode (default):** present findings *with a recommendation*
     and wait for the founder's confirmation before writing code.
   - **Delegated mode** (active when the founder has granted a standing
     delegation — recorded as entry #0 in `DECISION-LOG.md`): resolve
     autonomously and do not idle waiting for input. `[research-informed]` →
     keep the documented choice unless research reveals a disqualifier
     (deprecation/abandonment, security advisory, breaking change, clear
     incompatibility); otherwise take the minimal-deviation alternative.
     `[data-driven]` → answer from build/test experience at the named moment.
     Alpha-contingent → log as explicitly deferred. Every resolution is one
     `DECISION-LOG.md` entry: checkpoint, evidence, decision, confidence,
     rollback note. Founder steering arrives asynchronously via
     `FOUNDER-FEEDBACK.md` — read it at the start of every iteration.
4. **A changed product decision goes through the ADR discipline** (a
   superseding ADR), in either mode — never a silent edit. Settled decisions
   outside the flagged checkpoints stay settled.
5. **Then build**, writing tests alongside code (not after).

## 5. The decision protocol — decide locally, surface architecturally

Full version in `docs/build-conventions.md` §6. The line:

**Decide without asking** (obviously local — one file/function/test, no
cross-file or foundation implications): variable names, code structure within a
method, micro-optimizations, test fixture details, file organization within an
existing package, lint fixes, doc comments, and tests for behavior the
foundation already specifies.

**Always surface** (architectural — interactive mode: surface and *wait*;
delegated mode: surface in writing via a `DECISION-LOG.md` entry and *proceed*
per §4, except the park-always items below): new files in unexpected folders,
**any new dependency** (even small ones), decisions affecting multiple foundation
docs, anything that looks like a design choice, anything touching the
**encryption boundary**, the **validator's deterministic logic**, or the
**`TierPolicy` gate decisions**, conflicts between foundation docs, and any
behavior the docs don't specify.

**When unsure which it is → surface.** The cost of asking is founder time; the
cost of guessing wrong is silent drift, which is harder to recover from.

When you surface, use this format: (1) brief situation, (2) the question or
alternatives, (3) your recommendation + rationale, (4) cross-references to the
relevant foundation docs.

**Park-always (never resolved autonomously, in any mode):** any *deviation
from spec* touching the **encryption boundary**, the **validator's
deterministic logic**, or **`TierPolicy` enforcement** (building these *as
specified* needs no permission — deviating from their spec does); and any
cross-doc contradiction the conflict-resolution chain cannot resolve. These go
to `PROGRESS.md` blockers as founder-pending with an options analysis, and work
continues elsewhere. Forbidden behaviors (§7) are never unlocked by any mode.

## 6. How to write code (the essentials)

Full detail in `docs/build-conventions.md` §3–§5. The essentials:

- **Layering is non-negotiable** (`build-conventions.md` §3). `engine/` has no
  Android/Compose imports (pure Kotlin). `data/` has no UI deps. `ui/` never
  imports Room DAOs directly — it goes through Orbit MVI containers and the
  typed contracts in `shared/types/`. `agents/` has no UI deps.
- **Orbit MVI** for all UI-connected state: one immutable `State` data class per
  feature area, `Intents` that `reduce { }` state and `postSideEffect()`
  one-off events. Containers orchestrate; business logic lives in the engine,
  agents, validator, and repositories — not in containers.
- **Compose:** stateless composables where possible, state hoisted to the
  container, collected via `collectAsStateWithLifecycle()`. Material 3 theme in
  `ui/theme/` expresses the calm-precise-warm voice.
- **The UI keeps evolving after Phase 2** (`docs/build-conventions.md` §7,
  `docs/ui-ux-spec.md` §11): cosmetic changes (spacing, color, copy, motion)
  flow freely in-build; *structural* changes (new screen/destination/component,
  changed flow, changed `State`/`Intents` contract) flow back into
  `docs/ui-ux-spec.md` — it is a living doc.
- **Kotlin style** (ktlint-enforced, 120 cols): prefer `val`, immutable data
  classes with `copy()`, sealed hierarchies for closed type sets, named args
  beyond two params (always for booleans), `is/has/can/should` boolean prefixes,
  `UpperCamelCase` enum entries, `SCREAMING_SNAKE_CASE` consts. KDoc on public
  API explaining *what* and *why*, not *how*.
- **Tests are part of "done"** (Principle 4). A feature without tests for its
  specified behavior is incomplete. Every commit passes all unit tests, ktlint,
  and Android Lint. Fixtures live in `test/fixtures/` (canonical Sukhi/Aisha/
  Marcus profiles).
- **Commit small and often** with descriptive messages ("Add LimitConstraint
  payload schema with hard/soft enforcement", not "WIP"). Every commit is green.

## 7. Forbidden behaviors (the short list)

Full list in `docs/build-conventions.md` §11. Never:

- Commit code that fails tests, lints, or format checks.
- Implement a feature without tests for its specified behavior.
- Silently diverge from a foundation doc (surface it; update doc *or* code).
- Add a dependency without surfacing it first.
- Bypass the layering discipline (§6 above).
- Add logging that captures **user content** (operational telemetry only — per
  the trust posture, content telemetry is forbidden).
- Hard-code values that belong in config (pricing, endpoints, model names,
  feature flags live in `core/config/`).
- Edit a foundation doc to match wrong code instead of fixing the code.
- Implement v2/v3 features ahead of their planned ship (design for them, build
  v1 scope in v1).
- Skip the ADR discipline for an architectural decision.

## 8. When the docs don't answer — or disagree

**The conflict-resolution chain** (founder-specified at build kickoff, recorded
with the standing delegation in `DECISION-LOG.md` entry #0). When two sources
disagree, the higher one wins:

> **vision > PRD > ADRs > technical-architecture / data-model > conditional
> docs (subsystem specs) > build-conventions > implementation.**
> **`glossary.md` wins on terminology.**

Per-doc scoping rules still apply within their domain (e.g. roadmap wins on
sequencing, build-conventions wins on code style — each doc's §1 states its
scope). Apply the chain yourself; log non-obvious resolutions in
`DECISION-LOG.md`. A contradiction the chain genuinely cannot resolve is a
park-always item (§5).

When the answer is in *none* of the docs: check the vision, check the relevant
ADR, check the architecture docs — and if still unanswered, **surface the gap**
(per the active operating mode: in delegated mode, a `DECISION-LOG.md` entry +
minimal-deviation choice; founder-pending only if trust-critical). Often the
question itself is the signal that a foundation doc needs to grow (the docs and
code evolve together — `build-conventions.md` §7's bidirectional update
discipline).

## 9. The autonomous loop: ledgers, credentials, recovery

When running in delegated mode (§4), the loop's durable state lives in five
root-level artifacts, updated continuously and committed with the work:

- **`PROGRESS.md`** — the build ledger: current phase, current task, last
  completed, next up, blockers (incl. founder-pending items), toolchain
  versions. Updated every iteration.
- **`DECISION-LOG.md`** — every checkpoint resolution and every architectural
  surfacing. Entry #0 is the founder's standing delegation.
- **`SETUP.md`** — the founder's guide: every credential/service only the
  founder can supply (what to get, exact steps, where it goes, which phase
  needs it, how to verify it works), ending with the final live-verification
  sequence the founder runs after completing setup.
- **`FOUNDER-FEEDBACK.md`** — the founder's asynchronous steering channel.
  Read at the start of every iteration; any new note is highest-priority
  input; mark it addressed with a dated inline reply.
- **`PHASE-REPORTS/phase-N.md`** — written at each phase completion (built /
  tested / checkpoint decisions / deviations / doc updates / founder-pending
  items), then committed and **pushed** — the push is the founder's async
  review surface.

**Credentials are fake-first and absence-driven.** No real keys are needed to
build. Every external dependency sits behind an interface with a deterministic
fake (default) and a real client; DI selects by presence of config in
`local.properties` (gitignored from Phase 1). Never invent placeholder secret
strings; never commit or log key material. Firebase work targets the Local
Emulator Suite until real config exists. Tasks provable only against live
services are marked "verified-against-fake / pending live verification" in
`PROGRESS.md` — honestly distinct from done.

**Local-first CI.** One aggregate Gradle task (`qualityGate`: ktlint, Android
Lint, unit tests, Robolectric, coverage thresholds) runs locally before every
commit (Windows: `gradlew.bat qualityGate`). No GitHub Actions on push/PR —
`git push` is backup and founder review at phase milestones, never a CI
trigger.

**Session recovery.** If a session compacts, restarts, or dies: the new
session reads this file, `PROGRESS.md`, `DECISION-LOG.md`,
`FOUNDER-FEEDBACK.md`, `SETUP.md`, and the current phase in
`docs/roadmap.md`, then resumes the loop exactly where the ledger says — no
re-planning, no re-asking settled or logged decisions.

---

*This file is the agent's front door. Keep it short and current; the depth lives
in the foundation docs it points to. If you find yourself wishing this file said
something it doesn't, that's a signal to add it — with the founder's sign-off.*
