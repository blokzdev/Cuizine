# Cuizine — Vision

*The kitchen that knows you.*

> One-page north star. When in doubt, re-read this. Every other doc serves it.

## What Cuizine is

Cuizine is an AI-native food companion for people whose eating life has become too complex for willpower, spreadsheets, or generic recipe apps to handle. It holds the full picture of who you are — your medical constraints, your beliefs, your pantry, your week, your body's trends, the cuisine you grew up with — and quietly makes sure every meal it suggests respects all of it, all the time.

It is not a recipe app with filters. It is a persistent, evolving understanding of one human's relationship with food, expressed through meals.

## Who it's for (v1)

**The newly-diagnosed.** Someone whose doctor said "Type 2 diabetes" or "stage 3 kidney disease" or "PCOS" or "IBS" last month, and who is now drowning in a pile of rules they didn't have before. They are scared, motivated, and willing to pay for relief. They are the user we serve first because their pain is acute, their need is daily, and the cost of getting it wrong is something they already understand. If Cuizine works for them, it works.

Adjacent users — observant religious households, pregnant and postpartum women, multi-restriction families, athletes, and the caregivers who cook for any of them — inherit the same engine and join in later milestones. v1 is built around the newly-diagnosed and their household.

## The five-pillar thesis

Cuizine's eventual product is the seamless coming-together of five capabilities no existing app combines:

1. **Layered constraints respected automatically** — medical, religious, preference, temporal, contextual, all active at once, with conflict resolution and provenance.
2. **Pantry-aware planning** — what's in the fridge, what's expiring, what's on sale, woven into every suggestion.
3. **Long-term health intelligence** — trends, predictions, gentle warnings before problems compound.
4. **Instant context adaptation** — Ramadan starts tomorrow, you're flying to Tokyo Friday, your blood pressure spiked yesterday: the app simply absorbs it and keeps going.
5. **Cultural & contextual fluency** — Cuizine understands food as culture, not just nutrients. It knows the cuisine you grew up with, the ingredients available where you actually live, the traditions your calendar follows, and the meaning food carries in your household — and it weaves all of that into every suggestion, so eating well within new constraints never means abandoning who you are.

Pillars 1-4 are *what* Cuizine does. Pillar 5 is *who it does it for* — and it's the pillar that makes the other four feel like Cuizine is built for *you* specifically, not for an average user who doesn't exist.

**v1 leads with pillar 1.** The other four are scaffolded in milestone by milestone. We say this explicitly because a v1 that promises all five equally delivers none of them well. Pillar 1 is the foundation everything else stands on, and it is the pillar our first user feels most. **Pillar 5 is architected for from day one** even though its full expression matures across v2 and v3 — because it's the difference between an app that works for an "average user" and an app that works for the actual culturally-specific humans we're serving.

## The magic moment

A newly-diagnosed user adds three constraints in onboarding, opens the app a week later during Ramadan while travelling, and the meal Cuizine suggests is something they can actually eat — a culturally-familiar dish, made with ingredients available where they are, that respects their diagnosis without them having to think about any of it. That moment is when they tell a friend.

## Tone & personality

Calm, precise, warm. A trusted advisor who happens to understand both medicine and food, who never lectures, who never panics, and who treats the user as an intelligent adult navigating something hard. Not a chirpy chatbot, not a clinical robot, not a pushy chef.

Configurable agent personas (warmer, more direct, more clinical) are a v3 feature. The default voice ships first and ships well.

## Launch strategy

Cuizine grows in three deliberate stages. Each stage has explicit entry and exit criteria, and we do not skip stages.

### v1 — Closed alpha (Canada)

Distributed by APK to 15-25 deliberately-chosen users in Canada whose lives, between them, span the full constraint landscape we eventually want to serve: chronic conditions, religious observance, pregnancy, allergies, travel, sport, and a range of cultural backgrounds. They use Cuizine as their primary meal-planning tool for months. We talk to each of them. We fix what breaks. We do not optimise for strangers.

**v1 is Canadian for go-to-market reasons** — single regulatory jurisdiction under PIPEDA, focused alpha pool drawn from our actual social graph in Brampton and beyond, clean messaging — but **the engine, agents, data model, and integrations are architected for North American readiness from day one**. Nothing in the codebase assumes a single country. The only thing Canada-only about v1 is who we let in.

v1 ships single-profile (one user, their own data), but the data model and agents are designed *as if* multi-profile already exists, so v2's household features are enabling code paths that already exist rather than a rebuild.

**Exit criteria for v1 → v2:**
- 15+ alpha users have used Cuizine as their primary meal-planning tool for 60+ consecutive days
- Zero constraint violations in that window for any constraint the user has marked as medical or religious
- A stated willingness from those users to recommend Cuizine to someone they care about
- All five pillars are *functional* (not necessarily complete — functional)

We do not move these goalposts. We do not let perfectionism delay us once they are met.

### v2 — North American public launch

Play Store + cuizine.ai. Canada and the United States together, because Instacart already covers both and the engine was built ready. All five pillars functional and world-class for the North American market. Onboarding designed for strangers, not friends.

v2 ships **three subscription tiers**: a truly-free local-first base tier (Cuizine Free) where users can build their constraint profile, import and manage recipes, structure their household, and use the local-first encrypted container without ever triggering a cloud inference call; a standard paid tier (Cuizine) that lights up the full multi-agent system on top of that foundation; and a household tier (Cuizine Family) that adds multi-profile household planning. The free tier is not a trial and not a feature-gimped demo — it is a real, useful product that lives entirely on the user's device, costing Cuizine essentially nothing to run. Paid tiers offer a 14-day full-feature trial at signup with no payment method required upfront. See ADR 0007 for the full monetization model.

v2 introduces **multi-profile households** as the headline capability of the Family tier: the ability to manage constraint profiles for *dependents* — children, elderly parents, partners who aren't tech-engaged, anyone you cook for who isn't going to learn an app themselves — and to plan meals across a *household* composed of yourself and your dependents, where the engine reasons across everyone's constraints simultaneously to find meals that work for everyone at the table. This is the feature that solves "I'm cooking for my diabetic husband, my celiac kid, and my mother-in-law who's visiting from India." Dependent profiles live entirely within the primary user's encrypted container — there's no cross-account data sharing in v2, which keeps the trust posture clean and the engineering scope honest.

### v3 — Global expansion + advanced features

English-speaking markets beyond North America (UK, Australia, etc.), where a non-Instacart sourcing strategy will be needed. **Linked partner profiles**: cross-account sync and shared planning between two Cuizine users who have explicitly granted each other planning permissions, with peer-appropriate data visibility (you see what your linked partner can and cannot eat, not their full medical history). Configurable agent personas. The deeper Observer-agent capabilities in pillar 3 — long-term predictive health intelligence — that genuinely require accumulated user data to be meaningful, and so cannot honestly ship before v3. (v3 expansion is geographic — the same native Android app in new markets — not cross-platform; Cuizine is Android-only across all versions per ADR 0015.)

## What Cuizine is *not* (v1)

- Not a calorie tracker
- Not a fitness app
- Not a social network or recipe-sharing community
- Not a grocery delivery service (we integrate via Instacart; we don't deliver)
- Not a medical device or diagnostic tool
- Not a general-purpose chatbot
- Not multilingual at launch (English only in alpha)
- Not available on iOS, web, or desktop — Android-only across all versions (per ADR 0015)
- Not free forever in its full form (alpha is fully free; v2 introduces a truly-free local-first tier and paid tiers for the full multi-agent experience)
- Not built on individual grocer integrations (Instacart is the unified sourcing layer)
- Not a multi-user app in v1 (single profile only; multi-profile households arrive in v2)
- Not a shared-experience or community-template platform (ever — Cuizine is for real households, not for sampling other people's diets)

If a feature does not serve the newly-diagnosed user managing layered constraints in their cultural context, it does not belong in v1.

## Sourcing posture

Cuizine integrates with **Instacart as the sole grocery layer** in v1 and v2. Instacart is already the abstraction over the major North American retailers; building individual chain integrations would be an N-fronts war we cannot win and that users cannot see. The Sourcing Agent is architected with a clean internal interface so that adding alternative sourcing strategies in v3 (for non-Instacart markets) or fallback options (manual list, direct integrations) is a contained engineering task, not a rewrite. We depend on Instacart as a current choice; we architect for the possibility that this changes.

## Trust posture

Cuizine touches medical conditions, religious practice, household composition, cultural identity, and what's in someone's home. This data is more intimate than most health apps because it crosses body, belief, culture, and home at once. We treat it accordingly:

- **Local-first.** The user's data lives on their device as the source of truth. The free tier offers a path to use Cuizine without ever sending data to the cloud at all.
- **End-to-end encrypted sync.** Cloud backup exists; we cannot read it.
- **No analytics on personal data.** Ever.
- **The user can export and walk away.** Always.
- **Single regulatory jurisdiction in v1** (PIPEDA, Canada) so we can be deliberate about compliance instead of sprawling across regimes we haven't read.
- **No cross-account data sharing in v1 or v2.** Dependent profiles live within the primary user's container. Linked partners arrive in v3 with explicit, peer-appropriate permission models.

This is a product promise *and* an architectural constraint. It shapes everything downstream.

## Success in one sentence

Cuizine succeeds when a person who was overwhelmed by their new diagnosis stops thinking about food rules — because Cuizine is thinking about them, accurately, every day, in the background of their life, in the cultural language they actually speak, for everyone at their table.

---

*This document is the constitution. It changes rarely and only with deliberate intent. If a build decision conflicts with this doc, the doc wins until we decide otherwise — together, in writing, with a dated note in `/docs/decisions/`.*
