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
- ✅ Android SDK at `C:\Users\ganes\AppData\Local\Android\Sdk` (Platform 37 +
  Build-Tools 36.0.0 auto-installed during the first build)
- ✅ Emulator: your **Pixel 8 AVD** boots and runs the app — verified at
  Phase 1 close. Nothing for you to do.
- Verify any time with: `gradlew.bat qualityGate` (all green = healthy).

## Phase 4 — USDA FoodData Central API key 🟡 (researched, ready when you are)

The app is fully functional without this — food lookups run cache → curated
bundle → recorded fixtures. The real key only matters for live verification
and for ingredients outside the bundle.

1. **Get the key (free, ~1 minute):** https://fdc.nal.usda.gov/api-key-signup/
   — first name, last name, email. The key arrives by email instantly. No
   credit card, no approval wait.
2. **Where it goes:** add one line to `E:\Local\Cuizine\local.properties`:
   `cuizine.usda.api.key=YOUR_KEY_HERE`
   (That file is gitignored; never commit it. Note: api.data.gov
   auto-deactivates keys it finds in public repos.)
3. **Limits:** 1,000 requests/hour — far above what the cache-first design
   ever sends.
4. **Verify it works:** (command will be finalized with the live-verification
   section) — a `gradlew` connected check that resolves one non-bundle
   ingredient (e.g. "quinoa") against the live API.

**Open Food Facts needs no key** — reads are anonymous with a polite
User-Agent the app sets itself. Nothing for you to do.

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
