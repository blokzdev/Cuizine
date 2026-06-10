# 0004 — Multi-profile households: structure, ownership, and dependent-flow variants

**Status:** Accepted
**Date:** 2026-04-11

## Context

The constraint engine, once it works, is a structured representation of one human's relationship with food. Real households have multiple people with multiple constraints that need to coexist and interact at the dinner table. Caregivers, parents, partners, and adult children cooking for elderly parents are all under-served by every existing food app, and the constraint engine is uniquely suited to solve their problem.

Initial framings of this idea explored "experience sharing" (letting users walk in others' shoes) and a community template marketplace. Both were rejected: they failed the test of serving real human motivation, risked turning serious medical and religious realities into experience tourism, and would have required trust-and-safety infrastructure no two-person team can staff. The reframed insight is that real households need multi-profile *planning*, not experience sharing.

Two distinct relationship types emerged from the analysis, with different ownership and permission semantics — and within those types, multiple UX variants are needed to handle the real range of household members. A worked example helps: Sukhi Kaur Dhillon's household (the v1 primary persona, defined in `PRD.md`) contains four meaningfully different people whose food planning Cuizine would touch — her elderly mother-in-law Beeji (78, no smartphone use), her teenage daughter Simran (16, full agency, own apps, no reason to use Cuizine herself), her husband Harpreet (56, adult, wouldn't want to use Cuizine but is being included in meal planning), and her university-age son Arjun (21, occasional household member). Each of these requires a slightly different design treatment, even though all four can be modeled with the same underlying data structure.

The design decisions split into three layers: (1) what *kinds* of profile relationships exist and when each ships, (2) the *ownership semantics* of profiles in the data model, and (3) the *UX variants* of the dependent flow for different kinds of household members.

## Decision

### Layer 1: Profile relationship types and shipping milestones

**v2 ships *dependent profiles* and the *household concept*** as the headline of the **Cuizine Family** tier — the third of three subscription tiers introduced at v2 launch (Cuizine Free / Cuizine / Cuizine Family — see ADR 0007).

A dependent profile is a profile owned and managed by the primary user on behalf of someone in their household. The primary user can compose self plus dependents into a *household* — a named planning context that the engine reasons across simultaneously when planning meals. This is the feature that solves "I'm cooking for my diabetic husband, my celiac kid, and my mother-in-law who's visiting from India," and it's the feature that justifies the Family tier's higher price point relative to the standard Cuizine tier.

**v3 ships *linked partner profiles*.** Cross-account sync, peer permission flows, and adversarial-peer-safe encrypted data sharing arrive in v3 once the v1/v2 user base has proven the underlying primitives. A linked partner is a separate Cuizine user with their own account, who has explicitly granted the primary user planning permissions over their profile, with peer-appropriate visibility (you see what your linked partner can and cannot eat, not their full medical history).

**Experience sharing and community templates are explicitly out of scope, permanently.** They are listed as non-goals in `vision.md` and will not be revisited without a new ADR superseding this one.

### Layer 2: Profile ownership semantics

**Profile ownership is a first-class field in the data model from v1.** Every profile has an explicit *owner* (a Cuizine account). This is true even though v1 ships single-profile.

- **In v1:** every profile is owned by the account that created it. v1 users have exactly one profile (their own).
- **In v2:** a single account can own multiple profiles — its own primary profile plus dependent profiles for household members. All owned profiles live in the owner's encrypted container. There is no cross-account data sharing in v2.
- **In v3:** profiles can also be *linked* to an account without being owned by it — the linked-partner case. The profile is owned by another account but accessible for planning by the linking account, with peer-appropriate visibility scoping.

**Architectural discipline from v1:** the data model treats *profile* as the first-class entity (with its own constraint graph, pantry, history) and a *user account* as a holder of one or more profiles. v1 ships with exactly one profile per account, but the schema, agents, and APIs are designed *as if* multi-profile already exists. Every agent takes a profile (or set of profiles, for household planning) as an explicit parameter — even when v1 only ever passes one. The ownership field exists from line one of code, even though v1 only ever sets it to a single value. This costs almost nothing in v1 and makes v2 multi-profile an *enabling code path* rather than a rebuild.

### Layer 3: Dependent-flow UX variants

The v2 dependent flow has **three UX variants based on relationship type**, all sharing the same underlying data model. The owning user picks the relationship type when adding a dependent. The data structure is the same across all three; only the UX wrapping differs.

#### Silent dependent flow

For household members who are not going to engage with technology themselves: young children, elderly parents who don't use smartphones, anyone in a primary care relationship. The owning user creates the profile, enters constraints and preferences, and uses it for household planning. The dependent has no Cuizine account, no login, no notification, no involvement. Their profile is *the owner's data about them*. **Beeji is the example case.**

#### Consent-aware dependent flow

For household members who have agency and a phone but don't want to use Cuizine themselves: teenagers, adult children, anyone who could participate but isn't going to. The owning user creates the profile with explicit framing in the UI: "This is a profile for someone you cook for. We recommend letting them know." The owning user can optionally generate a *share link* that lets the dependent view (but not edit) what's recorded about them. The dependent can request changes via the share interface; the owner approves or declines them. The profile still lives in the owner's container; the dependent has no Cuizine account. **Simran is the example case.**

#### Recommended-disclosure dependent flow (for adult household members)

For adult household members with full agency: spouses, adult roommates, adult dependents who could in principle use Cuizine themselves but don't currently. Onboarding includes explicit copy: "Cuizine will use this profile to plan meals for [name] when you cook for them. We recommend letting [name] know you're using Cuizine to plan their meals, and discussing what restrictions or preferences they want included." Functionally identical to the consent-aware flow (share link optional, owner controls the profile), but with stronger language because the dependent is an adult and the ethical asymmetry is more pronounced. **Harpreet is the example case.**

### v3 upgrade path: dependent → linked partner

When a dependent decides they want to use Cuizine themselves (perhaps because they're now diagnosed with something, perhaps because they want their own planning tools), they can create their own Cuizine account in v3 and the existing dependent profile transitions to a linked partner profile. The data carries forward — the new owner doesn't have to re-enter anything that was already recorded about them. The transition is a permission and ownership change, not a data migration. The original owner retains planning visibility (now with peer-appropriate scoping) unless the new owner revokes it.

This commitment is a real engineering constraint: the schema for dependent profiles in v2 must be designed to be *transitionable* to a linked-partner profile in v3 without data loss — same fields, same types, just different ownership and permissions. The forthcoming `data-model.md` must reflect this.

## Consequences

**Positive.** v2 launches with a headline feature no other food app offers. The Family tier has an honest justification (genuinely more substantial work, not price discrimination) and unlocks a real revenue tier from launch day. The chronic-conditions user (Sukhi) is served not just as an individual but as part of the household she actually lives in. The dependent/linked-partner split keeps v2's trust posture clean: nothing crosses account boundaries, all data lives in one container per user. The three UX variants serve real households with mixed agency levels honestly. Pillar 5 (cultural fluency, ADR 0002) gains a household dimension — the family carries the cultural context, individuals carry the constraints, the engine reconciles them. The ethical asymmetry of "managing an adult's profile silently" is named and addressed without blocking the feature. v3's linked-partner upgrade is a clean transition because the data model already supports profile-as-first-class with explicit ownership.

**Negative.** v1 carries architectural overhead to support a feature it doesn't ship (profile abstraction, ownership field, agent parameter design). v2 onboarding for adding a dependent has UX complexity — picking the relationship type, handling disclosure copy, optionally generating share links. v2 inherits more onboarding flows, more permission UI, more sync edge cases, more failure modes to test. The temptation to add linked partners to v2 must be actively resisted. The recommended-disclosure copy may feel heavy-handed for casual cases; alpha and early v2 user research will tell us whether the wording needs adjustment.

**Neutral.** v3 engineering scope is now substantially larger because cross-account sync is genuinely hard. The schema for dependent profiles in v2 must be transitionable to linked-partner profiles in v3 without data loss — a real but contained engineering constraint.

## Alternatives considered

**Single-user only through v2, multi-profile in v3.** Rejected because dependents are too valuable a v2 differentiator to delay, and because the architectural cost of supporting them is small *if planned for from v1*.

**All multi-profile features (including linked partners) in v2.** Rejected because cross-account peer sync is meaningfully harder than single-container multi-profile. Bundling them would either delay v2 significantly or ship a half-baked linked-partner experience.

**Treat all dependents as silent (no UX variants).** Rejected. Operationally simplest but creates real ethical problems for adult dependents and gives the agent no guidance on how to handle the spouse case.

**Require all adult household members to have their own Cuizine accounts (defer them to v3 linked partners).** Rejected. This would push the entire household feature to v3 and mean v2 ships only the elderly-and-children case, which is too narrow.

**Two UX variants only (silent vs adult).** Rejected. The teenager case is meaningfully different from both the elderly-parent case and the adult-spouse case. Three variants is the minimum that honestly serves the real range of household members.

**No share links in v2 (only in v3 with linked partners).** Considered. Would simplify v2 by deferring all peer-visibility mechanics to v3. Rejected because the share link is a small, contained, read-only mechanism (not real cross-account sync) that meaningfully improves the consent-aware flow and is buildable in v2 without inheriting v3's harder cross-account problems.

**Experience sharing / community templates as a feature.** Rejected on multiple grounds: empathy-tourism framing is wrong for the product's tone, user-generated medical and religious content creates a trust-and-safety burden no two-person team can staff, and the underlying instinct (the engine is a remarkable shareable artifact) is better served by multi-profile households than by sampling other people's lives.

**Curated expert-authored template library** (registered dietitians or religious scholars publishing reviewed starter profiles users can adopt). Considered as a milder version of the community idea. Deferred to `open-questions.md` for v3 consideration. Not rejected, just not committed to yet.

## Related

- See `vision.md` § v2 — North American public launch, § v3 — Global expansion + advanced features, § What Cuizine is not, § Trust posture
- Pairs with 0007 (Cuizine Family is the home for these features in the tier model)
- Pairs with 0002 (cultural fluency operates at the household level via this structure)
- The forthcoming `data-model.md` must define the profile entity, the ownership field, the share-link mechanism, and the v3-upgrade-path data carry-forward explicitly
- The forthcoming `PRD.md` v2 section must reference the three dependent-flow variants and the relationship-type onboarding question
