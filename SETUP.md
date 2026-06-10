# SETUP.md — Founder setup guide (living doc)

> Everything only the founder can supply: credentials, accounts, console
> actions. The app **builds, runs, and passes all tests with zero credentials
> present** — every external dependency has a deterministic fake selected
> automatically when its key/config is absent (`CLAUDE.md` §9, fake-first
> policy). Supplying a credential flips that dependency to its real client.
>
> **Single local home for all keys/config: `local.properties`** (gitignored).
> Never commit this file. Exact entry names are listed per item below as each
> phase reaches them.

## Status legend

- ⬜ not yet needed (phase not reached)
- 🟡 needed for live verification of a completed phase
- ✅ verified working

---

## Phase 1 — Toolchain prerequisites (founder machine)

Verified 2026-06-10 — nothing for you to do right now:

- ✅ JDK 21 installed (`JAVA_HOME=C:\Program Files\Java\jdk-21`)
- ✅ Android SDK at `C:\Users\ganes\AppData\Local\Android\Sdk`
- ⬜ **Android emulator AVD** — needed from Phase 2 for on-device walkthroughs
  and instrumented tests. Verification step and any missing-package
  instructions will be added once the Gradle build exists.

## Phase 4 — USDA FoodData Central API key ⬜

- **What:** free API key for USDA FoodData Central (Open Food Facts needs **no**
  key). Signup flow + exact `local.properties` entry name to be documented when
  Phase 4 begins.

## Phase 5 — LLM provider API keys ⬜

- **What:** Anthropic API key, Google AI (Gemini) API key, OpenAI API key.
- Without them, agents run against `FakeModelProvider` (deterministic,
  fixture-based — all eval-harness scenarios covered). Signup flows + exact
  entry names documented when Phase 5 begins.

## Phase 6 — Firebase project ⬜

- **What:** Firebase project creation, Android app registration,
  `google-services.json` placement, Auth (Sign in with Google) + Firestore
  enablement. Until then: Firebase Local Emulator Suite only.
- Exact console steps documented when Phase 6 begins.

---

## FINAL VERIFICATION (run after completing all setup)

*To be finalized at end of Phase 7.* Will contain: the exact gradle/test
sequence to run per credential, the on-device journey checklist, and the
message to send the agent to kick off live verification (live eval harness
against real providers, real Firebase sync flows, real food-data lookups, full
PRD §4 journeys on-device).
