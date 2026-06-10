# Cuizine — Monetization and Billing

> The doc that operationalizes ADR 0007 (three-tier subscription) and ADR 0011 (the four-quadrant capability matrix) into concrete billing flows, tier enforcement, trial mechanics, and content portability. Where ADR 0007 made the policy commitments, this document specifies how those commitments become runtime behavior the user actually experiences. The discipline: every dollar Cuizine ever asks a user for is named here, every feature gate is named here, every trial mechanic is named here, every transition between tiers is named here. If a billing-related behavior isn't in this doc, it doesn't exist.

## 1. Purpose & how to read this doc

This document specifies the **monetization and billing layer for Cuizine across v1, v2, and v3**, with v1 alpha bypassing payment entirely and v2 launch turning on the live billing flows. It assumes you have read `vision.md`, `PRD.md`, `technical-architecture.md`, `data-model.md`, `local-first-sync.md`, and the ADRs — especially ADR 0007 (three-tier subscription policy with truly-free local-first base, 14-day trial, content portability forever) and ADR 0011 (the four-quadrant capability matrix that the billing layer enforces).

This document defines: the core principles the billing layer is designed against; the three tiers in concrete feature-by-feature terms across v1/v2/v3; the 14-day trial mechanics with the hybrid payment-method-not-required model; the runtime tier enforcement architecture and where checks live; the Google Play in-app purchase integration via the Google Play Billing Library; the upgrade and downgrade transition flows including their interaction with the encrypted sync container; the content portability mechanics that fulfill the forever commitment; the v1 alpha bypass that lets early users access full features without payment; the v3 BYOK quadrant that enables signed-out paid; the explicit list of forbidden billing behaviors; and the open questions this spec acknowledges.

This document does **not** define: the financial model (revenue projections, cost per user, pricing optimization — those belong in business documents, not the foundation set); the marketing copy for the tiers (lives in the prompts/copy directory); the legal terms of service or refund policy (lives with `security-and-privacy.md` and the actual ToS document); or the Google Play store listing (part of v2 launch deployment).

**When this document and a deeper-dive doc disagree,** this doc wins for tier definitions, feature gating, billing flows, and trial mechanics. `local-first-sync.md` wins for how billing transitions interact with encrypted sync. `data-model.md` wins for how tier state is persisted. ADR 0007 wins as the foundation policy that this doc is not allowed to contradict.

## 2. Core principles

> The non-negotiable commitments this layer is designed against. Every implementation decision in this doc traces back to one of these principles. If a future change to the billing layer would violate one, that change requires a new ADR before any code is written.

### Principle 1: Truly free really means free

The free tier is a complete, useful experience that a user can use forever without ever paying Cuizine anything. Not a stripped-down demo. Not a teaser. Not a dark pattern that becomes unusable after a hidden time limit. A real product that someone with no money to spend on apps can rely on for as long as they want.

Specifically: the free tier always includes the constraint engine, the Curator agent, the Chef agent, manual constraint conversation, manual rejection feedback, and the local-first single-device experience from ADR 0011's quadrants. These features are the *core* of what Cuizine does, and they are the user's forever. The paid tiers add capabilities (cross-device sync, household profiles, week-ahead planning, sourcing integration) but they do not unlock anything that's been hidden from the free tier — they add genuinely new things on top.

This principle is named first because it is the one most likely to erode silently. The temptation to "just gate one more thing for paid" is real and must be resisted forever. Every feature added to v2 and v3 is evaluated against the principle: does this make the free tier *less* useful, or does it *add* on top? If it makes free less useful, the answer is no.

### Principle 2: Content portability is a forever commitment

Anything a user has put into Cuizine — constraints, recipes, history, preferences, household configurations — they can export, in a structured machine-readable format, at any time, for free, without losing anything. This applies to free-tier users, paid users, trial users, lapsed users, deleted accounts (during the export window before deletion completes), and users on every plan that exists or ever will exist. The export is the user's data, not Cuizine's.

Per ADR 0007, this is a commitment Cuizine makes for the lifetime of the product. Pricing changes do not change the export commitment. Tier consolidation does not change it. Feature deprecation does not change it. The export is forever.

### Principle 3: No dark patterns

Cuizine does not auto-charge without explicit opt-in. Cuizine does not hide cancellation behind multiple confirmation screens. Cuizine does not remind users about expiring trials in panic-inducing ways. Cuizine does not use countdown timers or scarcity language to pressure decisions. Cuizine does not show inflated "savings" by comparing to fictional reference prices. Cuizine does not gate access to user data behind a paywall, ever — exports work for everyone regardless of tier.

This principle is the sharpest distinction between Cuizine's billing layer and standard SaaS billing layers. Conversion optimization that violates this principle is forbidden, even when the optimization works. The trust posture from `vision.md` extends into the billing layer specifically because billing is the place where most products' relationships with users break down.

### Principle 4: No surveillance for billing

The billing layer reads only what it needs to enforce tiers — subscription status, trial state, payment method presence — and it does not read user content for any billing purpose. Billing telemetry is operational only: subscription events (upgrade, downgrade, lapse, refund, cancel), tier check counts (anonymized), and platform-level events from Google Play. Content telemetry remains forbidden per ADR 0011 and this layer does not introduce exceptions.

### Principle 5: Existing users keep their original price

When Cuizine raises prices for new users (which will happen as the product matures and costs grow), existing users keep the price they signed up at, for as long as they remain continuously subscribed. A user who joined at $5/month stays at $5/month even after new users are paying $7/month. If they cancel and rejoin, they pay the current price. This is the loyalty commitment from ADR 0007 and it is the credibility deposit that lets us raise prices later without breaking trust with early adopters.

## 3. The three tiers in concrete terms

> Per ADR 0007, Cuizine offers three tiers: Free, Cuizine, and Cuizine Family. This section specifies what each tier contains, feature by feature, across v1, v2, and v3. The tier definitions are the contract Cuizine makes with users — adding a feature to a tier is allowed without ADR; removing a feature from a tier is forbidden without an ADR and a deprecation period.

### Free tier

**Always free, forever, on every version.**

What's included:

- **The full constraint engine.** All four severity tiers, all five constraint types, all five scope dimensions, the deterministic validator, the conflict resolution algorithm. Every architectural commitment in `constraint-engine-spec.md` applies to free-tier users.
- **The Curator agent.** The full constraint conversation flow, free-text updates, contextual state changes, plain-language explanations.
- **The Chef agent.** Meal suggestions, regeneration on rejection, cultural fluency, the validated happy path.
- **The lightweight Pantry agent (v1 form).** Manual entry, fuzzy matching, ingredient resolution.
- **Single profile.** One user, one constraint graph, one set of recipes.
- **Local-first experience.** Full offline functionality, the local SQLite database, the at-rest encryption from `data-model.md` Section 9 Layer 1.
- **Content portability.** Export everything at any time, in a structured machine-readable format. Per Principle 2.
- **Recipe and cooking history.** Every suggestion the Chef has produced, every meal the user has cooked, the rejection feedback, all stored locally.
- **The food data layer with Layers 1 and 2.** Local cache, curated bundle, AI-fallback for unknown ingredients (with severity scoping per ADR 0012). External API access (USDA, Open Food Facts) is **also included** for free-tier signed-out users — there's no business reason to gate the food data layer.

What's not included (i.e., where Cuizine and Cuizine Family add value):

- **Cross-device sync.** Free-tier users use Cuizine on a single device. If they want their data on multiple devices, they upgrade. (Free-tier users *can* sign in for cloud backup without crossing into the paid tier — see Section 7 on the four quadrants — but the active multi-device sync experience is paid.)
- **Multi-profile households (v2+).** Free-tier users have one profile.
- **Week-ahead meal planning (v2+).** Free-tier users get individual meal suggestions on demand, not pre-planned weeks.
- **Sourcing and grocery integration (v2+).** Free-tier users get shopping lists they can act on themselves; the Instacart integration and structured grocery handoff are paid.
- **Long-term health intelligence (v3+).** The Observer agent is paid because it requires accumulated data and meaningful per-user inference cost.
- **Configurable agent personas (v3+).** The default tone is the same for everyone; customization is paid.
- **BYOK (v3+).** Bring Your Own Keys is part of the paid offering even though it doesn't cost Cuizine inference money — see Section 10.

### Cuizine tier

**Paid: $5-7 USD/month or $50-70/year (depending on launch positioning, locked at signup per Principle 5).**

Includes everything in Free, plus:

- **Cross-device sync.** Encrypted sync container per `local-first-sync.md`, replicating the user's data across all their devices automatically.
- **Week-ahead meal planning (v2+).** The Planner agent generates structured 7-day meal plans that minimize conflicts, balance nutrition and culture, and reuse ingredients across meals.
- **Sourcing and grocery handoff (v2+).** The Sourcing agent translates plans and individual meals into shopping lists, with optional Instacart integration per ADR 0003.
- **The full Pantry agent (v2+).** Barcode scanning, receipt scanning, fridge photo ingestion (the v2 ingestion bundle from `technical-architecture.md` Section 7.5).
- **Long-term health intelligence (v3+).** The Observer agent runs over the user's accumulated history and surfaces trends, predictions, and gentle warnings.
- **Configurable agent personas (v3+).** The user can pick a preferred tone for the Chef, the Curator, and the app overall.
- **BYOK option (v3+).** The user can supply their own API keys and bypass the Cloud Function proxy, enabling the signed-out × paid quadrant.

### Cuizine Family tier

**Paid: $10-14 USD/month or $100-140/year (1.5-2x the Cuizine tier price, locked at signup).**

Includes everything in Cuizine, plus:

- **Multi-profile households (v2+).** Up to **8 profiles per household** in v2, with the structure from ADR 0004 (silent dependents, consent-aware dependents, adult dependents, linked partners in v3).
- **Cross-profile household planning (v2+).** The Planner agent reasons across all profiles in the household simultaneously, producing meals that work for everyone at the table.
- **Linked partner profiles (v3+).** Each adult in the household can have their own account and their own paid subscription, linked through the household structure rather than a single account owning everything.
- **Per-profile preferences and history (v2+).** Each profile in the household has its own constraint graph, its own meal history, and its own provenance trail. The household is a composition of profiles, not a merged blob.

### What the tiers do NOT differentiate on

- **Privacy.** Every tier gets the same trust posture, the same encryption, the same forbidden-behaviors list. Privacy is not a paid feature.
- **Validation rigor.** Every tier gets the same post-generation validator, the same conflict resolution, the same severity tier handling. Safety is not a paid feature.
- **Cultural fluency.** Every tier gets the same Chef agent with the same cultural depth from ADR 0002. Pillar 5 is not a paid feature.
- **Content portability.** Every tier can export everything. Per Principle 2.
- **Customer support quality.** Every tier gets the same support during alpha (direct from the founder); v2 and v3 will have queue-based support, but free users get the same queue as paid users. Support quality is not differentiated.

## 4. The 14-day trial mechanics

> Per ADR 0007, Cuizine offers a 14-day trial of paid features to anyone signing up for a paid plan. The trial mechanics need to balance conversion (the practical reality that trials are how products earn their first paid users) with Principle 3 (no dark patterns). This section specifies the hybrid model that threads the needle.

### When the trial starts

The trial starts when the user takes a deliberate action to begin it. Specifically:

1. The user is on the free tier (or signed out entirely).
2. The user navigates to Settings → "Try Cuizine" or "Try Cuizine Family" — or taps a contextual entry point on a feature they've discovered through the discoverability surface from Section 3.
3. The user sees a screen explaining what the trial includes, how long it lasts, and what happens at the end. The explanation is calm and accurate. There are no countdown timers, no urgency language, no scarcity tactics.
4. The user taps "Start trial." The trial begins immediately.

The trial **does not require a payment method upfront.** This is the deliberate departure from standard SaaS pattern, justified by Principle 3 and the reasoning in Section 4.4 below.

### What the trial includes

A trial unlocks **all features of the chosen tier** for 14 days. A user trying Cuizine gets cross-device sync, the Planner (when v2 ships), the Sourcing agent, the Pantry agent's full capabilities, etc. A user trying Cuizine Family gets all of that plus multi-profile households. There is no "limited trial" — the trial is the real product, time-limited.

### The trial countdown

The user can see how many days are left in their trial through:

1. A small, calm indicator in Settings → Subscription that shows "Trial: 11 days remaining."
2. **No** countdown timers or banners on other surfaces. The constraint conversation, the meal suggestion screen, and the rejection feedback flow do not show trial countdowns.
3. **No** push notifications about the trial. The user is not pinged about expiration. Per Principle 3.

### The mid-trial offer (the hybrid mechanic)

On day 7 of the trial (the midpoint), the app surfaces **one calm prompt** offering the user the option to add a payment method early in exchange for a concrete benefit. The prompt:

- Appears once, on the user's next interaction with Settings or with the suggestion surface (not interrupting an in-progress flow like the constraint conversation).
- Is dismissable with a single tap, and the dismissal is permanent for this trial period (it does not reappear).
- Frames the benefit honestly: "You're halfway through your trial. If you want to keep these features after day 14, you can add a payment method now. We'll preserve any constraint changes you make in the last few days, and you won't experience the brief disruption when the trial ends. You can cancel any time before the trial expires and you won't be charged."
- Does **not** use urgency language, countdown framing, or scarcity tactics.
- Provides a clear "Not now" option that's just as prominent as the "Add payment method" option.

This is the hybrid mechanic from the question I asked you. It threads the needle: it provides a meaningful conversion uplift over the pure no-prompt approach (because users who *want* to convert do so before they forget) while preserving the no-dark-patterns principle (because the prompt is calm, single-occurrence, dismissable, and honest about its purpose).

### What happens when the trial ends with no payment method on file

On day 14 at midnight in the user's local timezone, the trial expires. The app's behavior:

1. The paid features become unavailable. Cross-device sync stops. The Planner agent (v2+) is no longer available. The Sourcing agent (v2+) is no longer available. The user is back on the free tier.
2. **No data is lost.** Constraints, recipes, history, household configurations — all of it remains intact, even data that was created during the trial. The data is the user's, per Principle 2.
3. The user sees a calm message on next interaction: "Your trial has ended. You're back on the Free tier. All your data is still here — nothing has been lost. If you want to come back to the paid features, you can subscribe in Settings any time." The message appears once, dismissable.
4. **No data is gated behind a "subscribe to access" wall.** The user can read, modify, delete, and export everything they created during the trial without paying.
5. Features that simply don't work on the free tier (e.g., cross-device sync) gracefully degrade — the data is still there on the device, it just doesn't actively sync. If the user signs back into a paid tier later, sync resumes seamlessly.

### What happens when the trial ends with a payment method on file (mid-trial conversion)

If the user added a payment method during the trial (via the Section 4.4 mid-trial offer), the trial transitions automatically to a paid subscription on day 14:

1. Google Play charges the user the first month's subscription.
2. The paid features remain unlocked seamlessly. There is no disruption.
3. The user receives a calm in-app confirmation: "Your trial has ended and your Cuizine subscription is now active. Thanks for being here." Plus a standard Google Play receipt notification (which the platform handles).
4. The subscription enters the normal monthly renewal cycle.

The user can cancel at any time before day 14 to avoid being charged. Cancellation during the trial:

1. Removes the payment method from the planned charge.
2. Allows the user to continue using paid features for the remainder of the 14 days.
3. On day 14, behaves as the no-payment-method case above.

### Trial eligibility

A user is eligible for a trial **once per tier per account.** Sukhi can trial Cuizine once and trial Cuizine Family once, but she cannot trial Cuizine twice. This prevents trial cycling but is generous enough to let users explore both paid tiers honestly. Account is determined by Firebase Auth UID; trial state is stored in the encrypted sync container (per `data-model.md` Section 9, in a new `subscription_state` table specified in Section 5 below).

If a user signs out and back in with a different account, the new account gets its own trial eligibility. Cuizine does not attempt to detect "the same human with two accounts" — the cost of the workaround far exceeds the cost of the lost potential revenue, and any detection mechanism would require user identification beyond what we're willing to do.

## 5. Tier enforcement at runtime

> The architecture for how the orchestrator and agents check the user's tier on every relevant operation. The principle: enforcement is centralized, not scattered. There is one place in the code that knows what tier a user is on, one place that decides what that tier permits, and one place that surfaces graceful refusal when a feature is gated.

### The `subscription_state` table

A new table in the encrypted sync container that tracks the user's current subscription state.

```
CREATE TABLE subscription_state (
  id INTEGER PRIMARY KEY CHECK (id = 1),  -- single row per user
  current_tier TEXT NOT NULL,             -- 'free', 'cuizine', 'cuizine_family'
  tier_started_at TEXT NOT NULL,
  tier_expires_at TEXT,                   -- NULL for active subscriptions, set for trials and lapsed
  trial_status TEXT,                      -- NULL or 'active' or 'expired_no_payment' or 'expired_converted'
  trial_started_at TEXT,
  trial_expires_at TEXT,
  trial_tier TEXT,                        -- which tier was being trialed
  payment_method_present INTEGER NOT NULL DEFAULT 0,
  google_play_subscription_id TEXT,       -- the Google Play subscription identifier when active
  locked_price_per_month_cents INTEGER,   -- the price the user signed up at (Principle 5)
  locked_price_per_year_cents INTEGER,    -- the price the user signed up at (Principle 5)
  locked_currency TEXT,                   -- e.g., 'USD', 'CAD'
  v1_alpha_bypass INTEGER NOT NULL DEFAULT 0,  -- the alpha bypass flag from Section 9
  
  trial_eligibility_used_cuizine INTEGER NOT NULL DEFAULT 0,
  trial_eligibility_used_cuizine_family INTEGER NOT NULL DEFAULT 0,
  
  schema_version INTEGER NOT NULL DEFAULT 1,
  modified_at TEXT NOT NULL
);
```

This table is part of the encrypted sync container — the user's tier state syncs across their devices like everything else. The single-row constraint enforces that one user has one subscription state. The locked price columns implement Principle 5 (existing users keep their original price).

### The `TierPolicy` module

A single Kotlin class that owns all tier-related logic. Conceptual shape:

```kotlin
object TierPolicy {
    fun currentTier(state: SubscriptionState): Tier
    fun isFeatureAllowed(feature: Feature, tier: Tier): Boolean
    fun checkFeature(feature: Feature, state: SubscriptionState): FeatureGateResult
    fun featuresAvailable(tier: Tier): List<Feature>
    fun canUpgrade(from: Tier, to: Tier): TierTransitionResult
    fun canDowngrade(from: Tier, to: Tier): TierTransitionResult
}
```

The `Feature` enum is a closed set covering every gated feature in Cuizine (cross-device sync, week-ahead planning, sourcing, multi-profile households, observer, etc.). Adding a new feature to the enum requires adding it to the tier-feature mapping; you cannot ship a gated feature without going through `TierPolicy`.

The `FeatureGateResult` is a structured object returned on every check:

```kotlin
data class FeatureGateResult(
    val allowed: Boolean,
    val reason: TierGateReason? = null,
    val requiredTier: Tier? = null,
    val userMessage: String? = null,  // the calm refusal message when not allowed
)
```

When a feature is gated, the orchestrator (per `agent-architecture.md` Section 3) does not invoke the agent that would have provided the feature — instead, it surfaces the user-facing message from the gate result. The agent layer never sees a gated request. This is important: it means agents don't have to know about tiers, and tier policy can change without touching agent code.

### Where checks happen

Tier checks live at exactly two layers:

1. **The orchestrator's intent routing layer** (per `agent-architecture.md` Section 3). When the user's intent maps to a feature, the orchestrator calls `TierPolicy.checkFeature` before invoking the agent. If the feature is gated, the orchestrator routes to the graceful refusal flow instead.

2. **The UI layer's feature visibility logic.** When the UI is rendering a surface that includes a gated feature (e.g., the Settings screen showing the multi-profile household creation button), the UI calls `TierPolicy.featuresAvailable` to decide whether to show the entry point at all. Hidden entry points are preferred over visible-but-disabled entry points because they're calmer.

Tier checks **do not** live inside agents, inside the constraint engine, inside the food data provider, or inside the validator. Those layers operate on the data they're given; the gating happens upstream.

### Graceful refusal UX

When a free-tier user attempts to access a gated feature (which happens rarely because the UI hides the entry points, but can still happen via deep links or settings exploration), the refusal message is calm and informative:

- **Single sentence on the gate:** "Cross-device sync is part of the Cuizine subscription."
- **One concrete option:** "See Cuizine →" linking to the Settings tier-comparison screen.
- **No urgency, no scarcity, no countdown.** Per Principle 3.
- **No data shown that isn't already accessible to the user.** The refusal message doesn't tease "imagine if you could see X" — it just states the gate clearly and offers the path forward.

## 6. Google Play in-app purchase integration

> v1 alpha doesn't process payments (per Section 9's bypass), but v2 launch turns on live Google Play in-app purchase integration. This section specifies how that integration works at the architectural level — not Google Play's API in detail, but the wrapper layer around it that the rest of Cuizine talks to.

### The library

Cuizine uses the **Google Play Billing Library** (`com.android.billingclient:billing-ktx`), the official Google library for in-app purchase and subscription integration on Android. Since Cuizine is Android-only across all versions (ADR 0015), there is no other platform billing surface to integrate — Google Play is the sole billing channel.

### Product configuration

Cuizine creates the following Google Play subscription products:

- **`cuizine_monthly`** — Cuizine tier, monthly billing
- **`cuizine_yearly`** — Cuizine tier, annual billing (with the standard "save ~15%" framing handled by Google Play, not by Cuizine's own UI)
- **`cuizine_family_monthly`** — Cuizine Family tier, monthly billing
- **`cuizine_family_yearly`** — Cuizine Family tier, annual billing

The product IDs are stable across v2 and v3 — Cuizine never changes them, because changing product IDs would orphan existing subscribers from a Google Play perspective. Future tier additions get new product IDs.

### The `BillingService` wrapper

A single Kotlin class that wraps the Google Play Billing Library and exposes a clean API to the rest of Cuizine. Conceptual shape:

```kotlin
class BillingService {
    suspend fun loadProducts(): List<ProductDetails>
    suspend fun initiatePurchase(productId: String): PurchaseResult
    suspend fun restorePurchases(): RestoreResult
    val purchaseUpdates: Flow<PurchaseUpdate>
    suspend fun openManageSubscriptions()
}
```

The wrapper exists so that Cuizine's tier enforcement layer (`TierPolicy`) doesn't depend directly on the Google Play Billing Library — the wrapper translates Google Play's events into Cuizine's tier state updates, and the rest of the app sees only the tier abstraction. This is the same abstraction-over-providers pattern as `ModelProvider` from ADR 0006 and the Food Data Provider from ADR 0012. Trade-off accepted: a small amount of extra code in the wrapper for a much cleaner separation of concerns.

### Subscription lifecycle events

The `BillingService` listens to purchase updates from the Google Play Billing Library and translates each one into a tier state update:

- **`purchased`** — The user has just completed a purchase. Update `subscription_state.current_tier` and `google_play_subscription_id`. Lock the price into `locked_price_per_month_cents` etc. per Principle 5. Trigger a sync push to update other devices.
- **`restored`** — The user installed Cuizine on a new device and the purchase was restored. Update tier state; do not re-lock the price (the existing locked price is preserved).
- **`pending`** — A purchase is awaiting confirmation (rare, mostly for non-card payment methods). Tier state remains as-is; the UI shows a "verifying purchase" indicator.
- **`failed`** — The purchase failed. Surface a calm error to the user and return them to the previous tier state.
- **`canceled`** — The user canceled a purchase mid-flow. No tier state change.
- **`subscription_renewed`** — A monthly or yearly renewal succeeded. Update `tier_expires_at`; no other state change.
- **`subscription_lapsed`** — A renewal failed (declined card, expired card, user canceled). Update tier state to free, but **preserve all data** (per Principle 1 and Principle 2). Surface a calm in-app message; do not panic the user.
- **`subscription_refunded`** — Google Play processed a refund. Update tier state to free as if the subscription had ended at the refund timestamp. Preserve data per Principle 2.

Each lifecycle event is logged to the event log per `data-model.md` Section 7 with `event_type = subscription_lifecycle` and an appropriate severity (most are info; lapses and refunds are warn).

### Receipt validation

For v2 launch, receipt validation is **client-side only** — the Google Play Billing Library surfaces purchase tokens and signed purchase data that are verifiable on the device, and the result is trusted. This is sufficient because Google Play's purchase data is signed and verifiable client-side, and the alpha-then-v2 scale doesn't require server-side receipt validation infrastructure.

For v3 (when the user base may include adversaries who try to fake receipts), server-side receipt validation may become necessary. This is deferred to v3 planning — the architecture supports adding it as a Cloud Function call without changing the rest of the layer.

### What `BillingService` does NOT do

- It does not store payment method details. Google Play handles all payment data; Cuizine never sees a card number.
- It does not bypass Google Play's subscription management UI for cancellation. When the user wants to cancel, the wrapper opens Google Play's standard "manage subscriptions" screen via `openManageSubscriptions()`. This is required by Google Play policy and is also the right behavior — cancellation should be one tap and one screen, not a Cuizine-controlled flow that could become a dark pattern.
- It does not implement promo codes, discount campaigns, or A/B tested pricing in v1. Promo codes may be added in v2 if alpha feedback shows demand; A/B tested pricing is forbidden by the existing-users-keep-original-price principle and the no-dark-patterns principle.

## 7. Upgrade and downgrade transitions

> The transitions between tiers — Free → Cuizine, Cuizine → Cuizine Family, Cuizine Family → Cuizine, Cuizine → Free — are where billing meets the encrypted sync container, and the discipline from `local-first-sync.md` (no half-states, atomic transitions, rollback semantics) applies fully.

### Upgrade: Free → Cuizine

The simplest upgrade. The user goes from no paid features to having paid features.

1. The user initiates the purchase via the Settings → Subscribe flow.
2. `BillingService.initiatePurchase('cuizine_monthly')` (or yearly) opens Google Play's purchase sheet.
3. The user completes the purchase.
4. `BillingService` receives the `purchased` event and updates `subscription_state.current_tier` to `'cuizine'`, locks the price, and stores the Google Play subscription ID.
5. **If the user was previously signed out**, the Free → Cuizine upgrade triggers the signed-out → signed-in transition from `local-first-sync.md` Section 7, because Cuizine tier requires signed-in mode for cross-device sync. The transition follows that section's full rollback semantics. The user sees the recovery passphrase reveal screen as part of this combined flow.
6. **If the user was already signed-in (free + signed-in is a valid quadrant)**, the upgrade is a simple state change with no encrypted sync transition.
7. The orchestrator's tier policy module immediately reflects the new tier on the next intent routing call. Previously-gated features become available without app restart.
8. A sync push is triggered to propagate the tier state to other devices.

### Upgrade: Cuizine → Cuizine Family

A user already on Cuizine wants to add household profiles.

1. The user initiates the upgrade via Settings → Manage Subscription → Upgrade to Family.
2. Google Play handles the proration automatically — the user is charged the difference for the current period, and the subscription is updated to the Family tier.
3. `BillingService` receives a `purchased` event for the Family product ID.
4. `subscription_state.current_tier` updates to `'cuizine_family'`.
5. The locked price columns update to reflect the Family price (this is the one case where the locked price changes — the user is opting into a new tier, so a new price lock applies for the Family tier specifically).
6. The user is taken to a brief onboarding flow for adding household profiles, but no profiles are created automatically. The household is empty until the user adds members.

### Downgrade: Cuizine Family → Cuizine

A user on Family decides they no longer need household features.

1. The user initiates the downgrade via Google Play's manage subscriptions screen (Cuizine cannot initiate downgrades directly because Google Play owns the subscription state — Cuizine surfaces a "manage subscription" link that opens Google Play's UI).
2. Google Play schedules the downgrade for the end of the current billing period. The user retains Family features until then.
3. At the end of the billing period, `BillingService` receives an event indicating the tier has changed to Cuizine.
4. `subscription_state.current_tier` updates to `'cuizine'`.
5. **The household profiles are NOT deleted.** They remain in the database, but they become inaccessible — the orchestrator's tier policy hides the multi-profile UI and refuses to operate on dependent profiles. The user's primary profile (themselves) becomes the only active one.
6. The user sees a calm in-app message: "You're now on the Cuizine plan. Your household profiles are still saved — they'll be available again if you upgrade back to Family."
7. **Downgrade is reversible without data loss for as long as the household profiles are preserved.** There is no time limit. The user can re-upgrade in 6 months and find their household intact.

### Downgrade: Cuizine → Free (subscription lapse or cancellation)

A user on Cuizine cancels, or their subscription lapses.

1. The cancellation/lapse event arrives via `BillingService.purchaseUpdates`.
2. `subscription_state.current_tier` updates to `'free'`. `tier_expires_at` is set to the timestamp.
3. **Cross-device sync stops.** The encrypted sync container in Firestore is **NOT deleted** — it remains as a backup that the user can restore from if they upgrade back. But active sync pushes and pulls cease.
4. The user sees a calm in-app message: "You're now on the Free tier. Your data is still here. Cross-device sync is paused; if you'd like to resume it, you can re-subscribe in Settings any time."
5. Other paid features (week-ahead planning, sourcing, etc.) become unavailable per the tier policy.
6. **All user content is preserved.** Constraints, recipes, history, household profiles (if Family), pantry, food data cache. None of it is deleted. The free tier user can continue using their data on the device they're currently on.

### Why downgrade preserves everything

Per Principle 2 (content portability is forever) and Principle 1 (truly free really means free), Cuizine cannot delete user data as a consequence of downgrade. The free tier is a complete experience that includes "all the data you've accumulated." Treating downgrade as data loss would be a dark pattern (creating fear of losing data to discourage downgrades) and a violation of the trust commitment.

The cost is real: storage on the device for users who downgrade and never re-upgrade. This cost is borne by the device, not by Cuizine, so it doesn't affect Cuizine's economics.

### What happens to the encrypted sync container on downgrade

The encrypted blob in Firestore remains as it was at the moment of downgrade. Cuizine does not delete it. The user could re-upgrade in 5 years and find their data restored from the same blob, assuming Firebase has retained it. (Firebase's free-tier retention policies may garbage-collect inactive documents over very long periods; that's a Firebase consideration, not Cuizine's choice.)

If the user explicitly chooses "delete my account" — which is a separate Settings option, not a side effect of downgrade — then the Firestore document is hard-deleted as part of the export-and-walk-away flow from `vision.md`'s trust posture and `local-first-sync.md` Section 8.

## 8. Content portability mechanics

> Per Principle 2, content portability is a forever commitment. This section specifies what that actually means at the implementation level: the export format, the entry points, the guarantees, and the interaction with deleted accounts.

### What "portability" guarantees

A user can, at any time, on any tier, export every piece of data Cuizine holds about them in a structured machine-readable format that:

- Contains every constraint in their constraint graph with full provenance
- Contains every meal suggestion the Chef has produced for them
- Contains every cooked meal record they've added
- Contains every pantry item they've tracked
- Contains every contextual state they've reported (within the data model's retention window)
- Contains the relevant subset of their food data cache (their personalized ingredient knowledge from ADR 0012)
- Contains their profile metadata (cultural context, household configuration if Family tier)
- Contains the full event log if the user opts to include it

The export is structured JSON, schema-versioned, and self-describing — it can be read by any program, archived, fed to another system, or imported back into Cuizine on a fresh install. The export format is documented and stable across versions; new fields can be added but existing fields cannot be removed without a deprecation notice.

### The export format

The exported file is a single JSON document with this structure:

```typescript
interface CuizineExport {
  export_format_version: number;          // current: 1
  exported_at: string;                    // ISO 8601 UTC
  exported_by_app_version: string;
  exported_by_user_account_id: string | null;  // null for signed-out exports
  
  profile: {
    display_name: string;
    cultural_context: CulturalContext;
    cooking_for: CookingFor;
    timezone: string;
    created_at: string;
  };
  
  household_profiles: ProfileExport[];    // empty array for non-Family tier
  
  constraints: ConstraintExport[];        // every constraint with full provenance
  
  food_data_cache_personal: CacheEntryExport[];  // user-verified entries and AI-derived entries; bundle entries excluded as they ship with the app
  
  suggestions: SuggestionExport[];        // every suggestion with validation metadata
  cooked_meals: CookedMealExport[];
  pantry_items: PantryItemExport[];
  
  event_log: EventExport[] | null;        // null if user opted not to include
  
  schema_version: number;                 // the data model schema version this export was generated from
}
```

Each sub-type (`ProfileExport`, `ConstraintExport`, etc.) is a clean projection of the corresponding database row with sensitive fields preserved verbatim and internal-only fields stripped. The export is a *user-facing* document, so internal IDs are preserved but technical metadata (`schema_version` on individual rows, soft-delete `removed_at` fields, etc.) is omitted unless they're meaningful for the user.

### Where the export is initiated

Two entry points exist:

1. **Settings → Privacy → Export my data.** A deliberate user-initiated flow available to every user on every tier. The user taps "Export," reviews what's included (with options to exclude the event log if they prefer), and taps "Generate file." The app produces the JSON document and uses the platform's share sheet to let the user save it via email, file system, cloud storage, or any other share target. The user is in control; Cuizine never has a direct upload path for exports.

2. **Settings → Privacy → Delete my account → Export and delete.** The export-and-delete flow for users who want to leave Cuizine entirely. The app generates the export, presents it to the user via the share sheet, and then — after the user confirms they have saved the file — proceeds with the hard-deletion of the local database and the encrypted Firestore blob (per `local-first-sync.md` Section 8 and `vision.md`'s "export and walk away" commitment).

Both flows are calm, single-screen, and unsurprising. Neither requires payment, account verification, or any friction beyond confirming the user knows what they're doing.

### What the export does NOT include

- **Cuizine's curated bundle entries.** These are not user data — they ship with the app binary and the user already has them as part of having Cuizine installed.
- **Cuizine's system prompts or agent behavior specifications.** These are Cuizine's intellectual property, not the user's data.
- **Encrypted blob metadata from Firestore.** The export is the decrypted user data, not the encrypted form. A user does not need their own encrypted blob to use the export.
- **Master encryption keys, recovery passphrases, or any authentication tokens.** These are session data, not user content.

### Importing back into Cuizine

v1 does not implement an import flow because there is no use case for it during alpha (alpha users start fresh and don't have prior exports to import). v2 ships an import flow as part of the "I lost my passphrase, started fresh, but I have my old export" recovery path. The import flow:

1. The user navigates to Settings → Privacy → Import my data on a fresh install.
2. The user selects an export JSON file via the platform's file picker.
3. Cuizine validates the export's schema version and rejects exports from versions newer than the current code can handle.
4. Cuizine presents a preview of what will be imported (X constraints, Y meal suggestions, Z cooked meals, etc.) and asks for confirmation.
5. On confirmation, the export is parsed and inserted into the local database. Existing data is preserved unless it conflicts with imported data (last-write-wins per `local-first-sync.md` Section 6).
6. The user sees a calm confirmation: "Imported X constraints and Y meals. You're ready to go."

### The forever commitment in operational terms

The export commitment is forever, which means specific operational disciplines:

- **The export format is stable.** Adding fields is allowed; removing fields requires a deprecation period of at least two major versions.
- **Old exports remain readable by new versions.** A user who exports today can import that file in v3 a year from now and have it work. This is enforced by version-aware import logic.
- **Cuizine cannot revoke export capability.** No tier downgrade, no account state, no policy change can remove the user's ability to export their data. The button is always available.
- **The export does not require Cuizine's servers.** Generated entirely client-side from the local database. A user who has been signed-out and offline for a year can still export. Cuizine being unavailable does not affect a user's ability to export.

## 9. The v1 alpha bypass

> v1 alpha users get the full feature set of Cuizine and Cuizine Family without paying anything. This is not a free trial — it's a deliberate alpha-only bypass that lets the founder learn from real users with full feature access without the friction or implementation surface of payment processing. This section specifies how the bypass works, how it's contained, and how it's removed for v2 launch.

### What the bypass does

Every alpha user has `subscription_state.v1_alpha_bypass` set to `1` (true) at account creation. When `TierPolicy` checks a feature gate, the policy module first checks the bypass flag — if it's set, the user is treated as if they're on the Cuizine Family tier with no tier restrictions. The check happens once per request and adds negligible overhead.

The bypass does **not** change the underlying tier state. `subscription_state.current_tier` remains `'free'` for alpha users. The bypass is a separate flag that only affects feature gating, not the rest of the billing layer. This means: alpha users see no subscription UI, are not charged, are not asked to enter payment methods, but their data structures are identical to a free-tier user's data structures with one extra field flipped.

### Why the bypass is structured this way

Three reasons:

1. **No special-case data model.** Alpha users go through the same `subscription_state` table, the same `TierPolicy` checks, the same upgrade/downgrade infrastructure as v2 users. Only the bypass flag differs. This means v2 launch doesn't require migrating alpha users to a new system — the bypass just gets cleared.
2. **Easy removal at v2 launch.** Removing the bypass is a single-purpose migration: set `v1_alpha_bypass = 0` for every user, and let the existing tier policy take effect. Alpha users transition cleanly to the free tier without code changes.
3. **No leakage into production.** Because the bypass is a flag, not a code path, there's no risk that v2 users accidentally inherit alpha-only behavior. The flag is initialized per user, not per app version, and the v2 launch migration ensures every user starts at `0`.

### How the bypass is set

When an alpha user creates an account, the founder sets the bypass directly in their Firestore document via the alpha onboarding flow. There is no in-app way for a user to enable their own bypass — it requires founder action. This prevents a v2 user from somehow finding the flag and flipping it.

The alpha onboarding flow (per PRD § 4 and Walkthrough A) is currently a manual process: the founder shares an APK link with an alpha user, the user installs and signs up, the founder identifies the new account in Firebase, and the founder runs a one-off script (`scripts/alpha/grant_bypass.kts`) to set the flag. The script's existence is documented but not part of the production codebase — it lives in a separate scripts directory that does not ship with the app binary.

### What alpha users see

Alpha users see no billing UI. The Settings → Subscribe entry point is hidden when `v1_alpha_bypass = 1`. The Settings → Manage Subscription entry point is hidden. There are no upgrade prompts (consistent with the no-prompts-in-flow rule from Section 3 anyway, but this makes them explicitly absent). The user experience is "Cuizine works completely, with all features, and no one is asking me for money." The user does see a small acknowledgment in Settings → About: "You're part of the Cuizine alpha. Thanks for helping us build this." Calm, brief, no celebration.

### What happens at v2 launch

When v2 launches publicly, the alpha bypass is removed via a single migration:

1. The migration runs once on every alpha user's database the next time they update the app to a v2-eligible version.
2. The migration sets `v1_alpha_bypass = 0`, sets `current_tier = 'free'`, and unlocks the trial eligibility for the user (so they can take advantage of the 14-day Cuizine and Cuizine Family trials if they want).
3. The migration logs a `v2_alpha_transition_completed` event to the event log.
4. The next launch surfaces a warm message: "Thanks for being part of the Cuizine alpha. Cuizine is now publicly available, and you've been moved to the Free tier with everything you've created intact. If you'd like to keep using paid features (cross-device sync, week-ahead planning, household profiles), you can start a 14-day trial in Settings — no payment method required."
5. Crucially, the user's data is unchanged. Constraints, recipes, history, household profiles (if they had any in alpha) are all preserved per Principles 1 and 2.

This transition is the clearest possible expression of the working pattern Cuizine has built throughout: alpha users are treated as full first-class users, and the v2 launch is additive rather than disruptive.

## 10. The v3 BYOK quadrant

> ADR 0008 deferred Bring Your Own Keys (BYOK) to v3. ADR 0011's four-quadrant matrix has signed-out × paid as a quadrant that is unreachable in v1 and v2 — the only path to it in v3 is BYOK. This section specifies how BYOK interacts with the billing layer.

### What BYOK enables

A v3 user can supply their own API keys for the LLM providers (Anthropic, Google, OpenAI) and route LLM calls directly from their device to those providers, bypassing the Cuizine Cloud Function proxy entirely. This means:

- **No Cuizine backend involvement in inference.** The user's data goes from device to LLM provider directly. Cuizine the company sees only operational telemetry (sync events, billing events) and no inference traffic at all.
- **The user pays the LLM providers directly.** Their API usage shows up on their Anthropic/Google/OpenAI bill, not on their Cuizine bill.
- **The user can be signed-out and still use paid features.** Because Cuizine's backend is not in the inference loop, the user does not need a Firebase account to use BYOK — they can configure their own keys on a signed-out device and have full Cuizine functionality with the strongest possible trust posture.

This is the only path to the signed-out × paid quadrant from ADR 0011. Without BYOK, paid features require a signed-in account because they require Cuizine's billing infrastructure.

### How BYOK is billed

This is the subtle question. The user is paying the LLM providers directly for inference, but Cuizine still maintains the app, the constraint engine, the agents, the validator, the food data layer, the cultural fluency, and everything else that makes Cuizine valuable. Should BYOK users pay Cuizine anything?

The decision: **yes, BYOK users pay Cuizine a reduced subscription fee**. The reasoning:

- **Cuizine's value is not just inference.** The app, the design, the constraint engine, the prompt engineering, the cultural depth, the validator, the data model — all of these are Cuizine's contribution and they remain valuable to BYOK users.
- **A free BYOK tier would create a perverse incentive.** Users who could afford to pay would have an incentive to set up their own LLM accounts to get Cuizine free, even when paying Cuizine the normal subscription would be simpler and the inference costs to Cuizine are small.
- **A reduced fee is honest.** Cuizine's costs for BYOK users are genuinely lower (no Cloud Function proxy resources, no upstream provider costs to absorb), so charging less reflects the reduced cost-to-serve.

The v3 BYOK pricing is:

- **Cuizine BYOK monthly:** roughly $3-4 USD/month (about half the standard Cuizine monthly price)
- **Cuizine Family BYOK monthly:** roughly $6-8 USD/month (about half the standard Family price)

The reduced price covers Cuizine's contribution (the app, the architecture, the prompts, the bundle, the ongoing maintenance) without double-charging for inference the user is already paying for.

### How BYOK is configured

In v3, the user navigates to Settings → AI Providers → Use my own API keys. The flow:

1. The user enters their API keys for one or more providers (Anthropic, Google, OpenAI). Each key is validated by making a small test call to the corresponding provider.
2. The keys are stored locally on the device, encrypted at rest with the master encryption key (per `local-first-sync.md` Section 3). They are never uploaded to Firestore, even for signed-in BYOK users.
3. The user's `subscription_state` is updated to indicate BYOK mode is active.
4. The `ModelProvider` interface from ADR 0006 is reconfigured to route through the user's keys instead of through the Cloud Function proxy.
5. If the user is currently on a non-BYOK paid tier, they're offered the option to switch to the BYOK pricing (saving money but managing their own keys), or to continue at the full price (Cuizine routes their calls and pays the upstream costs).
6. If the user is on the free tier and signing up for BYOK, they're treated as a new BYOK subscriber.

### What happens when BYOK keys fail

If a user's API key becomes invalid (revoked, exhausted quota, expired) at runtime:

- The `ModelProvider` interface returns an error.
- The orchestrator surfaces a calm message: "I couldn't reach [Provider] with the key you provided. Could you check your API key in Settings?"
- The user's data and tier state are unchanged.
- The user can update their key, switch providers, or temporarily switch to Cuizine-routed inference (with the corresponding tier upgrade).

There is no fallback to Cuizine's keys without an explicit user choice. The BYOK guarantee is that Cuizine's infrastructure stays out of the inference loop unless the user actively asks otherwise.

### BYOK and the signed-out experience

The most powerful expression of BYOK is the signed-out × paid quadrant. A user who:

- Signs out (no Cuizine backend involvement)
- Configures BYOK with their own LLM provider keys
- Pays Cuizine the reduced BYOK subscription fee through Google Play (which does not require account linking — Google Play handles purchase association via the device-level Google account, separately from Firebase Auth)

...gets the strongest trust posture Cuizine offers (no Cuizine backend in the picture, no inference proxy, no encrypted sync container, no Firestore document) while still paying for Cuizine's ongoing development and accessing all paid features. This is the trust posture maximalist's dream and it is the philosophical north star for what BYOK enables.

## 11. What is forbidden

> Explicit list of billing behaviors Cuizine never does. Each item is a behavior that would erode the trust posture or violate one of the core principles. The list parallels `local-first-sync.md` Section 10 and exists for the same reason: to prevent silent drift when the agent or future-us is tempted by a "small reasonable change" that would actually break a principle.

- **Auto-charging users without explicit opt-in.** The trial does not require a payment method upfront. Users who add a payment method during the trial are charged automatically (because they opted in by adding the method), but users who don't add one are never charged.
- **Hiding the cancellation option behind multiple confirmation screens.** Cancellation is one tap from the Settings → Manage Subscription screen, which opens Google Play's standard cancellation UI (per Google Play policy and per principle).
- **Using urgency language, scarcity tactics, or countdown timers in any billing UI.** Per Principle 3.
- **Showing "savings" by comparing to fictional reference prices.** No "Was $10/month, now $5!" framing unless the $10 price was actually charged in production at some point.
- **Gating user data behind a paywall of any kind.** Exports work for everyone on every tier. Users can read, modify, delete, and export their data on the free tier exactly as they could on the paid tier. Per Principle 2.
- **Implementing A/B tested pricing for new users.** Per Principle 5, every user signing up at a given moment in a given market gets the same price. We can change prices for new users over time, but we cannot show different prices to different users at the same time as an experiment.
- **Tracking user content for billing decisions.** The billing layer reads only tier state, trial state, and payment method presence. It does not read constraints, recipes, history, or any user content.
- **Sending push notifications about expiring trials, lapsed subscriptions, or upgrade opportunities.** Per Principle 3 and the broader vision.md commitment to no push notifications.
- **Storing payment method details on Cuizine servers.** Google Play handles all payment data; Cuizine never sees a card number, expiration date, CVV, or any payment details.
- **Charging existing users a higher price after they signed up.** Per Principle 5. Existing users keep their original price for as long as they remain continuously subscribed.
- **Raising prices retroactively for users who cancel and rejoin.** A user who cancels and rejoins pays the current price at the time of rejoining. This is honest — they're a new subscription — but the user must be told clearly what the new price is before they confirm.
- **Implementing referral schemes that reward users for bringing others into paid tiers.** Referral schemes incentivize manipulative recruitment behavior, which conflicts with the calm-precise-warm voice. Maybe revisit if alpha feedback suggests strong demand, but tentatively no forever.
- **Implementing "recovery" flows that promise to discount users out of canceling.** When a user cancels, the cancellation is honored. Cuizine does not surface "wait, before you cancel, here's 50% off!" prompts. Per Principle 3.
- **Selling user data of any kind to anyone, ever, for any purpose.** This is so absolute it almost doesn't need to be in the list, but it's in the list because the failure mode of "we'll just sell aggregated anonymized data" exists in this industry and Cuizine's commitment is to never do it.
- **Creating tier-locked privacy settings.** Users on the free tier have the same privacy options, the same opt-outs, and the same data control as users on the paid tiers. Privacy is not a paid feature.
- **Making the export process slow, complicated, or hidden.** The export must always be reachable in two taps from the home screen and complete within seconds for typical user data sizes.

## 12. Open questions

### Pricing and value proposition

- **What's the right launch price exactly?** Section 3 commits to the lower band ($5-7/month for Cuizine, $10-14 for Cuizine Family) but the specific number within that band is unresolved. Decide during v2 launch prep based on cost projections from real alpha usage.
- **Should Cuizine offer a discounted student pricing tier?** Conventional in many SaaS products and friendly to younger users. Adds operational complexity (verifying student status). Tentatively deferred to v3 measurement.
- **Should the yearly pricing be a meaningful discount over monthly?** Industry standard is ~15-20% savings on annual. Cuizine could offer this or could keep yearly priced as 12x monthly for transparency. Tentatively the standard ~17% discount on annual, framed honestly as "you save by paying upfront."

### Trial mechanics

- **What is the right day for the mid-trial offer to appear?** Section 4 specifies day 7 (midpoint), but day 5 might catch users earlier when they're in active engagement, and day 10 might give the prompt more conversion weight. Test in v2 alpha.
- **Should users who let their trial expire be eligible for a second trial later?** Section 4 says no (one trial per tier per account, ever). But a user who tried the product, decided it wasn't right at the time, and comes back six months later might benefit from another look. Revisit if alpha feedback suggests strong demand.
- **What happens if a user starts a Cuizine trial and then upgrades mid-trial to Cuizine Family?** Currently undefined. Probably: the Cuizine trial converts to a Family trial with the remaining days, and the Family trial eligibility is consumed. Specify during v2 build.

### BYOK in v3

- **What is the right BYOK pricing exactly?** Section 10 commits to "about half the standard price" but the specific number depends on Cuizine's actual cost-to-serve for non-BYOK users at v3 scale. Measure during v2 to inform v3 pricing.
- **Should BYOK users get any visual indicator in the UI that they're on BYOK mode?** Useful for trust ("Cuizine is using my keys, not theirs") but adds UI surface. Tentatively a small indicator in Settings → Subscription showing "BYOK active" — calm, not celebratory.
- **How does Cuizine handle BYOK users whose API costs spike unexpectedly?** Cuizine doesn't have visibility into the user's API usage on the provider side. If a user's bill from Anthropic spikes because they used Cuizine heavily, that's between them and Anthropic — but should Cuizine surface usage estimates anyway? Tentatively yes, with a Settings → AI Providers → Estimated usage screen that tracks call counts (not tokens or content) locally and shows estimates.

### Operational

- **What's the right strategy for handling Google Play's commission?** Google takes 15-30% of subscription revenue depending on user lifetime. Cuizine's pricing has to factor this in — the $5-7/month band needs to net Cuizine $3.50-6/month after Google's cut. Acknowledged but the math is straightforward.
- **Should Cuizine offer a web-based subscription option in addition to Google Play?** This would let Cuizine bypass Google's commission on direct subscribers but adds operational complexity (PCI compliance, payment processor integration, customer support for billing questions). Tentatively no for v2; revisit if the commission cost becomes meaningful at v3 scale.
- **How does Cuizine handle refund requests?** Google Play has a standard refund window (48 hours) that Cuizine cannot override. Beyond that, refund requests come to Cuizine and Cuizine processes them through Google Play's developer console. Tentatively: yes to all refund requests within 30 days, no questions asked, per the calm-precise-warm voice. Revisit if refund abuse becomes a real problem.

### Forever commitments

- **Is the existing-users-keep-original-price commitment sustainable in 5 years?** If costs grow significantly and we have many early users on $5/month, the unit economics of those users could become negative. Is there a graceful way to migrate them or are we committed forever? Tentatively committed forever, accepting that some early users may be loss-leaders. Revisit only if the math becomes existential to the business.

## 13. Cross-references

### What this document references

- `vision.md` — for the trust posture, the calm-precise-warm voice, the export-and-walk-away commitment
- `PRD.md` — for the alpha context and the user journey
- `technical-architecture.md` — for the position of the billing layer in the subsystem map
- `data-model.md` — for the `subscription_state` table specification and the encryption boundary
- `local-first-sync.md` — for the signed-out to signed-in transition that upgrade flows compose with
- `agent-architecture.md` — for the orchestrator's intent routing layer where tier checks live
- `constraint-engine-spec.md` — for the engine that the free tier includes in full
- ADR 0007 — three-tier subscription, the policy this doc operationalizes
- ADR 0011 — optional backend with capability tiers, the four-quadrant matrix
- ADR 0008 — BYOK deferred to v3, the policy Section 10 implements
- ADR 0006 — multi-provider per-agent routing, which BYOK reconfigures
- ADR 0001 — Canadian-first alpha, North American v2, the launch sequence the bypass and v2 transition follow
- ADR 0004 — multi-profile households, the structure Cuizine Family enables
- ADR 0003 — Instacart as the sole grocery integration, which is part of the Cuizine paid tier in v2

### What this document defers to deeper-dive docs

- **`security-and-privacy.md`** — the formal terms of service, the legal framing of the export commitment, the refund policy in legal terms, the GDPR/PIPEDA implications of the subscription data
- **`build-conventions.md`** — Google Play developer account setup, product creation in the Play Console, app signing for in-app purchases, environment variables for billing endpoints
- **`testing-strategy.md`** — the test suite for the billing layer, including subscription lifecycle simulation, trial expiration tests, upgrade/downgrade transition tests with sync layer composition, and the v1 → v2 alpha bypass migration test
- **`roadmap.md`** — the milestone-level plan for when each tier feature ships, the gating decisions for v2 launch readiness, the BYOK design work for v3
- **`alpha-feedback-and-iteration.md`** — the alpha feedback collection process, including how billing-related feedback is reviewed, how the v1 → v2 transition is communicated to alpha users, and how price points are validated before public launch
- The prompts/copy directory — the actual user-facing text for the trial reveal screen, the mid-trial offer, the trial expiration message, the downgrade confirmation, the BYOK setup flow

### What this document does *not* defer (decisions made here)

- The five core principles (Section 2) and the discipline they create
- The three tiers in concrete feature-by-feature terms across v1, v2, v3 (Section 3)
- The 14-day trial mechanics, including the no-payment-upfront default and the day-7 mid-trial offer (Section 4)
- The runtime tier enforcement architecture, including the `subscription_state` table, the `TierPolicy` module, and the two-layer enforcement model (Section 5)
- The Google Play in-app purchase integration via the `BillingService` wrapper (Section 6)
- The upgrade and downgrade transition flows, including data preservation on downgrade (Section 7)
- The export format and content portability mechanics (Section 8)
- The v1 alpha bypass implementation and its v2 launch removal (Section 9)
- The v3 BYOK quadrant pricing and configuration (Section 10)
- The 16-item forbidden behaviors list (Section 11)

### How the agent should use this doc

When the coding agent builds the billing layer, Sections 3 (tier definitions) and 5 (enforcement architecture) are the authoritative specification for what each tier contains and how gating works. Section 4 (trial mechanics) must be implemented exactly as specified — the trial flow is the place where most products' trust relationships with users break down, and "almost right" is wrong. Section 7 (upgrade and downgrade transitions) must compose correctly with `local-first-sync.md` Section 7's signed-out to signed-in transition; the rollback semantics from there apply when Free → Cuizine triggers a sign-in.

Section 11 (what is forbidden) is the discipline list. The agent must not implement any of the forbidden behaviors regardless of how reasonable they seem in the moment. If a future requirement appears to need one of the forbidden behaviors, that requirement is the signal to stop and ask, not to proceed with the implementation. The trust posture is fragile in exactly the places this list defends, and the billing layer is where the trust is most visible to the user.

When the agent encounters a question this document does not answer, the discipline from PRD § 9 applies: check the vision, check the relevant ADR, check the data model doc, check `local-first-sync.md`, ask the founder before guessing. The billing layer is where Cuizine's principles meet the realities of running a sustainable business, and the principles win when they conflict with conventions.

---

*End of `monetization-and-billing.md` v1 (initial draft). Next revision will incorporate any pricing measurements from v1 alpha, any operational learnings from real Google Play integration during v2 build, and any design decisions from `security-and-privacy.md` that affect the billing layer's legal posture. The principles in Section 2 are stable; the specific numbers in Sections 3, 4, and 10 are expected to refine based on real cost and conversion data.*
