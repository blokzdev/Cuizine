# Phase 2 report — The UI shell, component library, and mock data

**Completed:** 2026-06-10 · **Exit criteria:** met to the testable limit (founder
device walkthrough pending — see "Try it on your phone" below)
**Commits:** `6d0f68f`, `a8a5e35`, `c955d80`, `be32379`, `6133976`, + close-out commit

## Built

Every v1 surface from `ui-ux-spec.md` §5, driven end-to-end by seeded fixtures
with **zero LLM calls** (§10 mock strategy):

- **Flow A (day 0):** Welcome (one sentence, one tap, quiet skip) → the
  five-question constraint conversation with inline captured-constraint
  acknowledgments → deferred-friendly sign-in decision → six-word passphrase
  reveal (honest no-recovery copy) → Today.
- **Flows B + D:** Today's single-suggestion model — canonical masoor dal with
  quiet constraint-fit notes; one-tap structured rejection (5 reasons + free
  text) whose follow-up visibly improves on the rejected dimension; the
  deterministic third-request **honest conflict** (Tuesday vegetarian × flare
  alliums × rajma) with severity-treated constraint rows and two honest
  options; suggestion detail with ingredients/disclosures/"why this?".
- **Flow C:** the conversation overlay rising from the centered FAB over the
  current context; scripted Curator understanding ("my sugar was 9.2" →
  contextual signal captured inline).
- **Flow E:** sign-in from Settings → passphrase reveal sheet.
- **Flow F:** unconditional export → platform share sheet; delete-everything →
  honest confirm → clean return to first-run. Plus the alpha "Share feedback
  with the founder" entry (`data-model.md` §7).
- **Profile:** the constraint graph in Sukhi's own words, grouped by severity
  (icon + label + fixed color, never color alone), scope summaries, constraint
  detail with full provenance trail and a warning before loosening a medical
  rule. **Pantry:** grouped seeded list, add/edit sheet, calm empty state.
- **Component library (§6):** all 12 locked components.
- **Mock seam (DECISION-LOG #2b):** containers written once against
  repository/agent interfaces; Phase 2 binds fixture-backed mocks via Hilt;
  Phases 3–6 rebind to real implementations without touching screens.
  Visible "Sample data" pill in debug builds (§10).

## Tested

50 unit tests green at every commit (fixture coherence, 12-component
behaviors, Flow A conversation script, Flows B/D container paths, screen
renders, migrator/schema/layering from Phase 1). qualityGate (ktlint, Lint
warnings-as-errors, tests, coverage) green throughout. On-emulator
verification: Flows A–F walked on a Pixel 8 AVD with screenshots; two real
findings fixed during the walkthrough (status-bar inset in full-screen
onboarding; FAB/affordance overlap on Today).

## Checkpoint decisions

- **UI1–UI6 [data-driven]: resolved on-device** — DECISION-LOG #2c. All
  tentative answers from §12 held; nothing needed reversal.
- **Material 3 evolution [research-informed]: keep** (resolved at the Phase 1
  currency sweep; M3 1.4.0, no relevant deprecations) — DECISION-LOG #2a.
- **Orbit ergonomics (Phase 1 carry-over, due "after 2–3 real containers"):**
  now answerable — 7 containers exist. **Verdict: earning its keep.** The
  State/Intents discipline made the mock seam trivial to specify and test;
  the one sharp edge worth recording is that *intents run concurrently*
  (reductions serialize), which surprised the conversation mock and is now a
  documented contract note for the Phase 5 orchestrator. No course change.

## Deviations / living-doc updates

- `ui-ux-spec.md` §5.5: v1 Settings ships as sections of one calm screen
  (sheets/dialogs for reveal/confirm) rather than seven near-empty
  destinations — surfaces and contracts unchanged; note added to the spec.
- Aisha/Marcus fixtures deferred to Phase 3 (their value is temporal-scope and
  caregiver *testing*; no Phase 2 surface consumes them). Sukhi is complete.
- Dynamic flow assertions live at container level; Robolectric's paused main
  looper can't drive Main-dispatched Orbit intents mid-composition (noted in
  ScreenRenderTest).

## Founder-pending

- **Your device walkthrough** (whenever you like — the loop continues into
  Phase 3 meanwhile; feedback lands via `FOUNDER-FEEDBACK.md` and folds in
  under the §11 UI-evolution discipline).

## Try it on your phone (walkthrough-ready)

1. On the phone: Settings → enable "Install unknown apps" for your file
   manager (or just use ADB below).
2. Easiest path (phone plugged in, USB debugging on):
   `cd E:\Local\Cuizine`
   `.\gradlew.bat :app:installDebug`
   (Or build `.\gradlew.bat :app:assembleDebug` and copy
   `app\build\outputs\apk\debug\app-debug.apk` to the phone and open it.)
3. You'll land on the Welcome screen. Two good first runs:
   - **The full day-0 feel:** "Let's start with what you cook" and answer the
     five questions in your own words (anything works — the alpha build
     scripts the understanding); confirm; try sign-in to see the passphrase
     moment (it's a fixed sample passphrase, no real account).
   - **Straight to the kitchen:** "Skip for now" loads Sukhi's full sample
     profile. Then: Today → "Suggest a meal" → reject with "Don't have the
     ingredients" (watch it adapt) → "Something else instead" twice (the
     third ask triggers the honest-conflict surface) → pick an option.
4. Sweep the tabs: Profile (tap a rule for its provenance), Pantry, Settings
   (export, share-feedback, delete-everything all work against sample data).
5. Everything runs on sample data — the "Sample data" pill confirms it. No
   network calls, no accounts, nothing leaves the phone.

## Next

Phase 3 — the constraint engine and validator: the 11 engine operations, five
constraint types, five scope dimensions, active-set computation, the
deterministic five-step validator, four-step conflict resolution, provenance —
and the hard-cases suite (the heart of the project). Then the real engine
replaces the Profile surface's mock bindings, same State, same Intents.
