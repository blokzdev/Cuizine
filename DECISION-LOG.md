# DECISION-LOG.md — Checkpoint resolutions and architectural surfacings

> Every roadmap Decision-checkpoint resolution and every architectural surfacing
> made under the autonomous build loop, newest entries appended at the bottom.
> Format per entry: checkpoint/situation · evidence · decision · confidence ·
> rollback note. Changed product decisions additionally get superseding ADRs
> (`docs/build-conventions.md` §9). Entry #0 is the founder's standing delegation.

---

## #0 — Founder standing delegation (delegated mode active)

**Date:** 2026-06-10
**Type:** Founder decision (recorded verbatim in intent; source: founder kickoff prompt, 2026-06-10)

The founder has granted a standing delegation authorizing the coding agent to
resolve **all roadmap Decision checkpoints for the v1 build loop (Phases 1–7)**
autonomously, per `docs/roadmap.md` §2 Principle 6 "Operating modes":

- **[research-informed]** → keep the documented choice unless research reveals a
  disqualifier (deprecation/abandonment, security advisory, breaking change,
  clear incompatibility); otherwise take the minimal-deviation alternative and
  write a superseding ADR per `docs/build-conventions.md` §9.
- **[data-driven]** answerable in-build → answer from build/test experience at
  the named moment; log observation and course taken.
- **[data-driven]** alpha-contingent → log as explicitly deferred to alpha.
- Every resolution = one entry here. Changed decisions additionally get ADRs.
  Nothing is silently swapped.

**Scope boundaries (unchanged by this delegation):**
- Park-always items are never resolved autonomously: deviations from spec at the
  encryption boundary, the validator's deterministic logic, or `TierPolicy`
  enforcement; and cross-doc contradictions the conflict-resolution chain cannot
  resolve. These park as founder-pending blockers in `PROGRESS.md`.
- Forbidden behaviors (`docs/build-conventions.md` §11) are never unlocked.
- Conflict-resolution chain (founder-specified): vision > PRD > ADRs >
  technical-architecture/data-model > conditional docs > build-conventions >
  implementation; `glossary.md` wins on terminology.

**Review & reversal:** reviewable and reversible by the founder at any time via
`FOUNDER-FEEDBACK.md`. Founder review surface = pushed `PHASE-REPORTS/` at each
phase completion.

**Rollback note:** revoking this entry returns the build to interactive mode
(`docs/roadmap.md` §2 Principle 6 default); no code changes required.

---

## #1 — Doc corrections: Flutter-era residue in four foundation docs

**Date:** 2026-06-10 · **Type:** Architectural surfacing (doc correction, delegated mode)

**Situation:** Full-foundation audit found six residual references to the
superseded ADR 0005 (Flutter/Dart) stack that contradict ADR 0016, despite
ADR 0016's Related section recording those docs as updated:
`build-conventions.md` §3 (`analysis_options.yaml`), §6 (`final` Dart syntax),
§6 (`lib/` folder), §11 (`lib/core/config/`); `glossary.md` "Subsystem" entry
(`lib/`); `security-and-privacy.md` §10 (`lib/data/encryption/`);
`technical-architecture.md` §6 (`json_serializable`/`freezed` bullet, redundant
with the kotlinx.serialization bullet above it).

**Alternatives:** (a) leave as-is and interpret on the fly; (b) correct the
docs to match ADR 0016.

**Decision:** (b) — corrected all six, with dated correction notes on the two
substantive ones. This is the bidirectional update discipline
(`build-conventions.md` §7) completing ADR 0016's own documented update pass —
not editing docs to match wrong code (no code exists yet). Glossary's
deliberately-historical "(Superseded.)" entries left untouched.

**Confidence:** High. **Rollback:** `git revert` of the correction commit.

---

## #2 — CLAUDE.md audit result (first-iteration mandate)

**Date:** 2026-06-10 · **Type:** Process record

Audited `CLAUDE.md` and `README.md` per the founder's first-iteration mandate.
Both were already aligned with delegated mode (commit `7e49c33`): autonomous
checkpoint protocol ✓, bidirectional/ui-ux-spec living-doc discipline ✓,
forbidden behaviors ✓, fake-first credentials ✓, session recovery ✓,
README as doc index ✓. One gap: the explicit **conflict-resolution chain**
(vision > PRD > ADRs > technical-architecture/data-model > conditional docs >
build-conventions > implementation; glossary wins terminology) was not stated —
added to `CLAUDE.md` §8, citing entry #0. **Rollback:** revert the edit.

---
