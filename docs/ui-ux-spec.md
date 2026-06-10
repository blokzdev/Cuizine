# Cuizine — UI/UX Specification

> The skeleton and the nervous system of Cuizine's interface — not its final pixels. This document locks the things that are expensive to change later and genuinely architectural: the design language, the navigation model, the screen inventory, the component set, the mapping from UI to the Orbit MVI state model, the voice made concrete, and the accessibility floor. It deliberately leaves fine visual detail to be settled in-build, where Compose's hot-reload makes iteration cheap. When this doc and the vision conflict, the vision wins; when this doc and the PRD conflict, the PRD wins; when this doc and an ADR conflict, the ADR wins; when this doc and a downstream implementation detail conflict, this doc wins until updated by deliberate decision. On terminology, `glossary.md` wins.

## 1. Purpose & how to read this doc

This document specifies **how Cuizine looks, feels, and is navigated** — the design language, the navigation model, the inventory of v1 screens, the reusable component library, the key user flows, the in-product voice, and the accessibility floor. It is written to be built against directly: the UI is built **first, before any real subsystem** — Phase 2 of the roadmap, immediately after scaffolding — constructed with mock data and placeholder repositories so that every screen renders and every interaction flows before the constraint engine, agents, sync, or billing exist. The later phases then wire the real subsystems into a UI that already matches the architecture.

This document **locks structure and language, not final pixels.** It specifies the design tokens (so the Compose theme is determined), the navigation (so the app's shape is determined), the screen inventory and the state each screen renders (so the screens map cleanly onto Orbit MVI containers), the component set (so the building blocks are named and consistent), and the accessibility non-negotiables (because those are architectural and costly to retrofit). It does **not** specify exact spacing values for every element, final copy for every string, precise animation curves, or the pixel-level layout of each screen. Those are settled in-build, on-device, where they are cheap to iterate. Section 11 draws this boundary explicitly.

This document assumes the reader has read `vision.md` (the five pillars, the calm-precise-warm voice, the trust posture), `PRD.md` (the personas Sukhi, Aisha, Marcus and the day 0→30 journey), `agent-architecture.md` (the Curator, Chef, Pantry agents and the orchestrator that routes between them), `build-conventions.md` Section 4 (the Jetpack Compose + Orbit MVI + Material 3 commitment), and `constraint-engine-spec.md` (the constraint types, severity tiers, and the active set). Read this doc when building any UI surface, when naming a component, or when deciding how a screen should behave in its empty, loading, or error state.

When this doc specifies a behavior that a deeper-dive doc also touches, the deeper-dive doc owns the *substance* (what a severity tier means, what the validator does, what a tier gate enforces) and this doc owns the *presentation* (how the severity tier is shown, how a validated suggestion is displayed, how a gate's refusal looks). If they disagree on substance, the deeper-dive doc wins and this doc is updated.

## 2. Design principles

> The calm-precise-warm voice from `vision.md`, translated from adjectives into concrete UI rules. Every interface decision traces back to one of these.

### Principle 1: Calm is the absence of urgency

Cuizine never manufactures urgency. There are no countdown timers, no streaks, no badges, no "you haven't logged in for 3 days" guilt, no red notification dots competing for attention, no celebratory confetti. The trust posture from `vision.md` and the forbidden-behaviors lists make this a hard rule, not a stylistic preference. Calm shows up visually as generous whitespace, a restrained color palette where color carries meaning rather than decoration, motion that settles rather than bounces, and a default screen state that feels like a quiet kitchen rather than a busy dashboard. When in doubt, the calmer option wins.

### Principle 2: Precise means the interface is legible and honest

Precision is how Cuizine earns trust with someone managing a medical condition. It shows up as: information shown exactly when relevant and not before; numbers and facts presented plainly without false confidence; disclosure notes that say what Cuizine knows and doesn't know (the AI-fallback disclosure from ADR 0012, the not-a-medical-provider disclaimer from `security-and-privacy.md` Principle 2); and never hiding a constraint violation or a limitation behind a cheerful UI. Precise also means the interface does not pretend to a confidence it lacks — a suggestion Cuizine is unsure about is presented with that uncertainty visible, not smoothed over.

### Principle 3: Warm means the interface treats the user as a capable adult it respects

Warmth is not cuteness, not mascots, not exclamation points. It is the tone of a knowledgeable friend who happens to understand food, medicine, and culture — and who never condescends. Warmth shows up as: microcopy that is human and unhurried; acknowledgment of the user's effort and context without flattery; cultural fluency that feels like being understood rather than categorized; and error and refusal states that are gentle and never blame the user. Sukhi should feel that Cuizine is *on her side*, not auditing her.

### Principle 4: The conversation is the soul, surfaced everywhere

The constraint conversation — the user telling Cuizine about their life in their own words, and Cuizine understanding — is the most distinctive thing the product does. It is given a prominent, elevated home (the centered FAB, Section 4) and is also reachable contextually from other surfaces. The user always talks to **Cuizine, one entity**; the specialized agents behind the curtain (Curator, Chef, Pantry) are never exposed as a roster to choose from. Specialization is *revealed in the conversation* (Cuizine can show it is reasoning about nutrition and constraints together), never *operated through a menu* — per ADR 0006's orchestrator model.

### Principle 5: Hidden over disabled, calm over loud

When a feature isn't available — a gated tier feature for a free user, an action that doesn't apply in the current state — the preferred treatment is to **hide the entry point**, not show it disabled. A disabled button is a small recurring frustration and a tease; an absent button is calm. When something must be communicated as unavailable (e.g., a deep-linked gated feature), the graceful-refusal UX from `monetization-and-billing.md` Section 5 applies: one calm sentence, one concrete option, no urgency.

## 3. The design language

> The Material 3 / Material You foundation expressed as tokens, so it maps directly to a Compose theme in `ui/theme/`. Specific final values are set in-build; this section locks the system and the intent, not every hex code.

### Material 3 as the base

Cuizine is built on Material 3 (Material You), per ADR 0016. This is a deliberate choice that gives Cuizine native Android feel for free: dynamic color, system theming, predictive back, edge-to-edge, and the platform's motion and accessibility conventions. Cuizine uses Material 3 components as the foundation and customizes through the theme rather than fighting the framework. The theme lives in `ui/theme/` as `Color.kt`, `Type.kt`, `Shape.kt`, and `Theme.kt`.

### Color

The color system is built on Material 3's tonal/role-based scheme (primary, secondary, tertiary, surface, background, error, and their on-colors and containers) rather than a fixed palette of named colors. The intent the theme expresses:

- **A calm, warm base.** The overall feeling is closer to a warm neutral kitchen than a cold clinical app. Surfaces are soft, not stark white or stark black.
- **Color carries meaning, not decoration.** The strongest color accents are reserved for things that matter: the severity indicator on a constraint, a validation result, the primary action. Decorative color is minimal.
- **Dynamic color, constrained.** Material You's dynamic color (deriving the palette from the user's wallpaper) is supported because it makes the app feel native and personal — but constrained so that the *semantic* colors (severity indicators, error states, validation results) remain legible and consistent regardless of the wallpaper-derived palette. Meaning-bearing color is never left to chance.
- **Severity has a consistent visual language.** The four severity tiers (Inviolable, Medical, Religious & Cultural, Preference) have a consistent, accessible visual treatment wherever they appear — and that treatment never relies on color alone (see Section 9).

Light and dark themes are both first-class from v1. Dark is not an afterthought.

### Typography

A clear type scale built on Material 3's type system (display, headline, title, body, label roles). The intent: highly legible at the body level (Sukhi is 53 and may read without glasses some of the time), with a calm, slightly warm typeface rather than a cold geometric one. Dynamic type scaling is honored fully (Section 9) — the layout must not break when the user scales text up. Numbers (nutritional values, limits, blood-sugar figures the user reports) are rendered in a treatment that makes them easy to read precisely.

### Spacing and layout

A consistent spacing scale (a base unit and multiples of it) so that rhythm is even across the app. The intent is generous, calm spacing — content breathes. Screens use a single-column, scrollable layout as the default; Cuizine is a reading-and-conversing app more than a dense-controls app. Touch targets meet the accessibility floor (Section 9).

### Shape and elevation

Material 3 shape system (rounded corners as the default language) tuned to feel soft and approachable rather than sharp. Elevation is used sparingly and meaningfully — the FAB is elevated because it is the primary action; most surfaces sit calmly at low elevation. Cards (the suggestion card, the constraint card) use shape and subtle elevation to feel like discrete, handle-able objects.

### Iconography

A single consistent icon set (Material Symbols as the base). Icons are used to aid recognition, never as decoration. Every icon that conveys meaning is paired with a text label or has an accessible content description (Section 9) — Cuizine never relies on an icon alone to communicate something important.

### Motion

Motion follows Material 3 motion principles, tuned calm: transitions settle rather than bounce, durations are gentle, and motion communicates spatial relationships (where a screen came from, where a thing went) rather than drawing attention to itself. There is no celebratory or attention-grabbing animation. The most important motion is the conversation surface appearing from the FAB — it should feel like Cuizine arriving to listen, a smooth rise rather than a jarring modal.

## 4. Navigation model

> Four task-shaped tabs plus a centered conversation FAB. This is locked (it shapes every screen). Per Material 3, five is the bottom-nav ceiling; using four tabs plus a FAB keeps the bar uncluttered and elevates the conversation above the destinations.

### The bottom navigation bar

Cuizine uses a Material 3 bottom navigation bar with **four destinations**, framed around what the user is trying to *do* rather than around the app's internal architecture:

- **Today** — the home and default destination. The outcome-oriented surface: "what should I cook?" This is where meal suggestions appear and where the accept / reject / regenerate loop lives. The Chef's output surfaces here. Today is the warm front door — opening Cuizine lands here.
- **Pantry** — what's on hand. Lightweight in v1 (manual entry of ingredients the user has). Grows into the richer scanning capabilities in v2. The Pantry agent's surface.
- **Profile** — the constraint graph made visible, in human terms. Where the user reviews and edits what Cuizine knows about them: their constraints, their household, their cultural context. This is the management surface for everything the Curator has captured.
- **Settings** — account, sync status, the recovery passphrase, export, delete-account, the disclaimers, and tier information. Where the user goes deliberately, not by default.

### The conversation FAB

A **centered floating action button** sits in the bottom bar, elevated above the four destinations. It is the front door to the conversation with Cuizine. Tapping it opens the **unified Cuizine conversation** — one entity, with the orchestrator routing internally to the Curator, Chef, or Pantry agent per ADR 0006. The user never picks an agent; they talk to Cuizine and Cuizine figures out internally what kind of help is needed.

The FAB is the most prominent single element in the navigation because the conversation is the soul of the product (Principle 4). Its treatment is aesthetic and inviting — this is the one place the interface actively invites interaction rather than waiting calmly. When tapped, the conversation surface rises smoothly (Section 3, Motion) — Cuizine arriving to listen.

The conversation is also reachable **contextually** from other surfaces — for example, a calm "tell Cuizine something" affordance on Today, or a "why this suggestion?" entry point on a suggestion card that opens the conversation already in context. The FAB is the dedicated front door; it is not the only door. This keeps the conversation both a destination and connective tissue.

### Navigation behavior

- The four tabs preserve their own back stacks; switching tabs does not lose scroll position or in-progress state.
- The conversation opened from the FAB is a surface over the current context, not a fifth tab — dismissing it returns the user to where they were.
- Deep links (e.g., from a gated-feature refusal, or a future notification-free reminder mechanism) resolve to the appropriate tab or surface.
- Back behavior follows Android conventions (predictive back supported, per Material 3).

### Why five top-level destinations is now closed

Using four tabs plus the FAB sits at the comfortable Material 3 maximum. This is a deliberate constraint: future features (v2 planning, v3 capabilities) attach *inside* these five surfaces rather than adding new tabs. A "meal plan" surface in v2 is a feature reachable from Today or the conversation, not a sixth tab. This forces new capability to find a home in an existing mental category rather than proliferating navigation — a healthy discipline, named here so it is a decision rather than a future surprise.

## 5. Screen inventory (v1)

> Every v1 screen, each specified structurally: its purpose, the state it renders (mapped to an Orbit MVI `State`), the intents it can fire, the side effects it handles, and its empty / loading / error states. This is the contract the UI build implements against — pixel-level layout is settled in-build. Each screen's state maps to an Orbit MVI container (a `ViewModel`) per `build-conventions.md` Section 4.

For each screen below, "State" is the immutable data the container exposes, "Intents" are what the user/system can trigger, and "Side effects" are one-time events (navigation, transient messages). In the UI build phase these are backed by **mock containers** returning seeded fixture data (Section 10); the later phases swap in the real repositories and agents without changing the screen.

### 5.1 Onboarding & first-run

**Welcome / value framing.** Purpose: the first thing a new user sees — a calm, brief statement of what Cuizine is ("the kitchen that knows you"), no account required to begin. State: which onboarding step is active. Intents: begin, skip-to-app. Side effects: navigate to first constraint conversation. Empty/loading/error: minimal — this is static.

**First constraint conversation.** Purpose: the warm entry into telling Cuizine about your dietary life — the Curator's first surface, framed as a conversation rather than a form. This is where Sukhi first types something like "I'm diabetic and my stomach doesn't handle onions well." State: the conversation messages, the in-progress input, whether Cuizine is "thinking," the constraints captured so far. Intents: send-message, edit-captured-constraint, confirm-and-continue. Side effects: navigate to Today once the user is ready; show a gentle confirmation as constraints are understood. Empty: a calm prompt inviting the first message. Loading: Cuizine "thinking" indicator. Error: a gentle "I didn't quite catch that — could you say it another way?" never a hard failure.

**Sign-in decision (deferred-friendly).** Purpose: let the user understand they can use Cuizine fully without an account (local-first, per ADR 0011), and choose to sign in for cross-device sync if they want. State: signed-in status, what sign-in unlocks. Intents: sign-in-with-google, continue-without-account. Side effects: launch the sign-in flow; on success, the recovery passphrase reveal. This is presented without pressure — signed-out is a first-class, fully-functional state.

### 5.2 Today (tab 1)

**Today home.** Purpose: the outcome-oriented home — "what should I cook?" Shows the current/most recent meal suggestion, an affordance to ask for a suggestion, and a calm contextual entry to the conversation. State: the current suggestion (if any), whether a suggestion is being generated, the active contextual flags (e.g., an IBS flare the user reported today), a "tell Cuizine something" affordance. Intents: request-suggestion, accept-suggestion, reject-suggestion (with reason), regenerate, open-conversation-in-context. Side effects: open the conversation; show the rejection-reason affordance. Empty: a warm invitation to ask for the first suggestion. Loading: Cuizine composing a suggestion (the Chef working, then the validator checking). Error: a gentle fallback if generation fails, with a retry.

**Suggestion detail.** Purpose: the full view of a meal suggestion — what it is, why it fits the user's constraints (the specialization *revealed*), the ingredients, and any disclosure notes (AI-fallback disclosure, soft-limit-approached notes). State: the suggestion, its constraint-fit explanation, ingredient list, disclosure notes, validation result. Intents: accept, reject-with-reason, regenerate, ask-why (opens conversation in context), save-to-history. Side effects: open conversation; confirm save. Empty: n/a (only reached with a suggestion). Loading: n/a. Error: if the suggestion failed validation and could not be regenerated cleanly, the conflict-resolution surface (below) appears.

**Conflict resolution surface.** Purpose: the rare but important case where the Chef cannot produce a suggestion that satisfies all active constraints, and Cuizine must surface the conflict honestly and offer options (per `constraint-engine-spec.md` Section 8). State: the conflicting constraints, the candidate resolutions, their severity implications. Intents: choose-resolution, relax-a-preference, ask-for-help (conversation). Side effects: regenerate with the chosen resolution; open conversation. This surface is where precision and warmth matter most — it never blames the user and never silently violates a constraint.

### 5.3 Pantry (tab 2)

**Pantry list.** Purpose: what the user has on hand; lightweight manual entry in v1. State: the pantry items, grouped sensibly; whether an add/edit is in progress. Intents: add-item, edit-item, remove-item. Side effects: confirm changes. Empty: a calm "your pantry is empty — add what you have, or skip this for now" (the pantry is optional in v1, never nagging). Loading: minimal. Error: gentle.

**Add/edit pantry item.** Purpose: capture a single ingredient the user has. State: the item being edited, validation of the entry. Intents: save, cancel. Side effects: return to pantry list. Kept deliberately simple in v1.

### 5.4 Profile (tab 3)

**Profile home.** Purpose: the constraint graph made human — the user reviews everything Cuizine knows about them, organized so it is legible, not a raw data dump. State: the profile (name, optional details), the constraints grouped by type and severity, the household (single profile in v1), the cultural context. Intents: edit-constraint, add-constraint (opens conversation), remove-constraint, edit-profile-details, open-conversation. Side effects: open conversation; confirm changes. Empty: for a brand-new user, a gentle invitation to tell Cuizine more. Loading: minimal (local data). Error: gentle.

**Constraint detail / edit.** Purpose: view and adjust a single constraint — its type, severity, scope, and provenance (when and how Cuizine learned it, per `constraint-engine-spec.md` Section 9). State: the constraint, its full scope, its provenance, its severity. Intents: edit-severity, edit-scope, remove (soft delete), view-provenance. Side effects: confirm changes; warn before lowering the severity of a medical or inviolable constraint (precision and safety). The provenance display is part of the trust posture — the user can always see why Cuizine believes something about them.

**Household (v1 single-profile, v2-ready).** Purpose: in v1, the single profile; the surface is structured so multi-profile households (v2, ADR 0004) slot in without redesign. State: the household's profiles (one in v1). Intents: edit-profile. The v2 add-profile entry point is absent in v1 (hidden, not disabled, per Principle 5).

### 5.5 Settings (tab 4)

> **v1 implementation note (Phase 2, 2026-06-10, per §11's living-doc discipline).** At v1's
> scale these surfaces ship as *sections of one calm scrolling Settings screen* (with the
> passphrase reveal and confirmations as sheets/dialogs) rather than separate navigation
> destinations — seven near-empty destinations read as crowded ceremony, not calm. The
> surfaces, their State/Intents, and their copy are exactly as specified below; only the
> navigation packaging is consolidated. Split into destinations when v2 content (billing,
> household management) gives the sub-surfaces real depth. The alpha feedback entry
> ("Share feedback with the Cuizine founder", `data-model.md` §7) also lives here.

**Settings home.** Purpose: the deliberate-destination surface for account, data, and trust. State: signed-in status, sync status, tier, the list of settings sections. Intents: navigate to each sub-surface. Calm, organized, not crowded.

**Account & sync.** Purpose: sign-in status, sync status, what syncing means (the cloud holds encrypted blobs it cannot read — stated plainly). State: account, sync state, last-sync indication. Intents: sign-in, sign-out, trigger-sync. Side effects: launch sign-in; on sign-out, the appropriate warning about local-only data.

**Recovery passphrase reveal.** Purpose: show the six-word recovery passphrase (per `local-first-sync.md` Section 4) at the moment of sign-in, with the gravity it deserves — this is the user's only key, and Cuizine holds no copy. State: the passphrase, whether it has been confirmed seen. Intents: reveal, confirm-saved, copy. Side effects: proceed once confirmed. This screen is deliberately weighty and precise — it explains, calmly and clearly, that there is no recovery if the passphrase is lost, because there is no master key or admin override (the trust posture). The 30-second pause-after-failed-attempts behavior lives in the unlock flow, not here.

**Export my data.** Purpose: the content-portability commitment made real (per `monetization-and-billing.md` Section 8) — export everything, any tier, no conditions. State: export in progress / ready. Intents: start-export, share-export. Side effects: hand the file to the platform share sheet. Calm and unconditional — never gated, never discouraged.

**Delete account.** Purpose: the export-and-walk-away commitment — delete cleanly, with the data actually deleted. State: confirmation step. Intents: confirm-delete (with a clear, calm confirmation), cancel. Side effects: perform deletion; return to first-run. Honest about what is and isn't recoverable.

**About & disclaimers.** Purpose: the not-a-medical-provider disclaimer (per `security-and-privacy.md` Principle 2) and the honest statement of what Cuizine is and isn't. State: static content. This is where the not-HIPAA, not-clinical posture is stated plainly — not buried, not alarmist, just honest.

**Tier & subscription (v1: minimal; v2: full).** Purpose: in v1 alpha, this is minimal (alpha users have full features via the bypass, per `monetization-and-billing.md` Section 9 — no billing UI). The surface exists structurally so the v2 billing UI slots in. In v1, it calmly states the alpha status. The v2 tier-comparison, trial, and upgrade surfaces are absent in v1 (hidden, not stubbed-with-disabled-buttons).

### 5.6 The conversation surface (FAB)

**Cuizine conversation.** Purpose: the unified conversation — the heart of the product. The user talks to Cuizine in free text; the orchestrator routes internally; specialization is revealed in the conversation, never operated. State: the conversation history, the in-progress input, whether Cuizine is thinking, any in-context anchor (e.g., opened from a suggestion's "why?"), constraints or suggestions surfaced inline. Intents: send-message, act-on-an-inline-result (e.g., accept a suggestion that came up in conversation, confirm a constraint Cuizine understood). Side effects: navigate to a relevant surface if the conversation produces an outcome that lives there; dismiss back to prior context. Empty: a calm, inviting prompt — the conversational equivalent of Cuizine looking up and listening. Loading: the "thinking" indicator, which can calmly reveal *what kind* of thinking ("considering your constraints and what's in season…") to surface specialization without exposing the agent roster. Error: gentle, conversational recovery — never a stack trace, never a hard wall.

## 6. Core component library

> The reusable composables that the screens are built from. Naming them here keeps them consistent and prevents one-off reinventions. Each is a named Compose component living in `ui/components/`, with variants and states. Final visual detail is set in-build; the component's existence, purpose, and states are locked.

- **CuizineButton.** The calm primary/secondary/text button, themed per Section 3. Variants: primary (the one strong action on a screen), secondary, text. States: enabled, loading, (no aggressive disabled — prefer hidden per Principle 5).
- **SuggestionCard.** The meal suggestion as a handle-able object: title, a brief why-it-fits line, and an entry to detail. States: presented, accepted, being-regenerated.
- **ConstraintChip / ConstraintRow.** A single constraint shown compactly, with its severity treatment (color + icon + label, never color alone — Section 9) and type. Used in Profile and inline in the conversation.
- **SeverityIndicator.** The consistent visual language for the four severity tiers, accessible and consistent everywhere a severity appears. Never color-only.
- **ConversationMessage.** A single message in the conversation — user message vs Cuizine message, with the calm visual distinction. Cuizine's messages can carry inline results (a suggestion, a captured constraint) as embedded components.
- **ThinkingIndicator.** Cuizine's calm "thinking" state, optionally with a revealed-specialization line ("considering your constraints…"). Settles, doesn't spin frantically.
- **DisclosureNote.** The precise, calm note that states what Cuizine knows or doesn't — the AI-fallback disclosure, the soft-limit-approached note, the not-a-medical-provider reminder where contextually appropriate. Visually distinct as "information," never alarmist.
- **GracefulRefusal.** The component implementing `monetization-and-billing.md` Section 5's refusal UX: one calm sentence, one concrete option, no urgency. Used for gated features reached via deep link.
- **EmptyState.** The reusable calm empty state — a warm invitation rather than a blank void or an error. Every list/surface that can be empty uses it.
- **InlineError.** The gentle error treatment — conversational, never blaming, always with a way forward.
- **CuizineScaffold.** The app shell: the four-tab bottom bar plus the centered conversation FAB, used by the top-level destinations so navigation is consistent.
- **PassphraseReveal.** The weighty, precise component for the recovery passphrase — the six words shown clearly with the gravity and the honest no-recovery explanation.

## 7. Key flows

> The defining user journeys, described as paths through the screen inventory and tied to the PRD § 4 journeys. These are what the UI build phase demonstrates end-to-end (with mock data) to prove the interface is complete and navigable before any real logic exists.

**Flow A — Sukhi's day 0 onboarding.** Welcome → first constraint conversation (she types her diabetes, her IBS, her family's preferences in her own words; Cuizine understands and reflects back what it captured) → optional sign-in decision → (if signed in) recovery passphrase reveal → lands on Today, where a first suggestion invitation waits. The emotional arc: from "another app to set up" to "oh — it actually understood me."

**Flow B — Asking for and refining a meal.** Today → request-suggestion → ThinkingIndicator (Chef composing, validator checking, specialization revealed) → SuggestionCard → suggestion detail (the why-it-fits, the disclosure notes) → either accept (saved to history) or reject-with-reason → regenerate → a new suggestion that honors the rejection. The invisible regeneration loop (per `agent-architecture.md` Section 3) is felt as Cuizine simply trying again thoughtfully, not as an error.

**Flow C — Telling Cuizine something via the FAB.** Any surface → tap the conversation FAB → the conversation rises → Sukhi reports "my sugar was 9.2 this morning" → Cuizine understands this as a contextual signal, reflects it, and adjusts (the Curator captures it; today's suggestions account for it) → dismiss back to where she was. The conversation as living connective tissue.

**Flow D — The honest conflict.** Today → request-suggestion → the Chef cannot satisfy all active constraints → conflict resolution surface appears, naming the conflict plainly and offering options (relax a preference? a different approach?) → Sukhi chooses → regenerate. Precision and warmth at the hardest moment; Cuizine never silently violates a constraint and never blames her.

**Flow E — Recovery passphrase at sign-in.** Sign-in decision → Google sign-in → PassphraseReveal (the six words, the calm gravity, the honest "there is no recovery if this is lost — we hold no copy") → confirm saved → cross-device sync now available. The trust posture made tangible.

**Flow F — Export and walk away.** Settings → export my data → the file is prepared → platform share sheet → done. Unconditional, calm, never discouraged. And, if the user chooses, delete account → clean deletion. The relationship is honest in both directions: Cuizine earns staying, never traps.

## 8. Voice & copy in the UI

> How the calm-precise-warm voice shows up in the actual words on screen. Final copy is set in-build (and lives in `prompts/copy/` per `build-conventions.md` Section 8), but the principles are locked here.

- **Microcopy is human and unhurried.** "What are you in the mood for?" not "SELECT MEAL PARAMETERS." "Tell me a bit about how you eat" not "Configure dietary profile."
- **Errors never blame.** "I didn't quite catch that — could you say it another way?" not "Invalid input." The user is never at fault.
- **Disclosure is precise and calm.** When Cuizine is inferring rather than certain (the AI fallback), it says so plainly without alarm: a quiet note, not a warning banner. When it is not a medical provider, it says so honestly where it matters, without lawyering.
- **Refusals are gentle and concrete.** The graceful-refusal pattern: one calm sentence on what's gated, one concrete option, no urgency, no scarcity.
- **No celebration, no guilt.** No "Great job!", no streaks, no "You've been away — we missed you." Warmth is steady respect, not emotional manipulation.
- **Cultural fluency is felt, not announced.** Cuizine demonstrates understanding of cultural and religious food contexts through how it responds, not by labeling the user.
- **The disclaimers are honest, not defensive.** The not-a-medical-provider, not-HIPAA posture (per `security-and-privacy.md` Principle 2) is stated where it belongs (the first constraint conversation, About, the passphrase context) clearly and without fear-mongering.

## 9. Accessibility floor

> Non-negotiable, and architectural — costly to retrofit, so locked here. These are not aspirations; they are requirements every screen meets from the first build.

- **Touch targets** meet the Android minimum (48dp) everywhere.
- **Contrast** meets WCAG AA for text and meaningful UI against their backgrounds, in both light and dark themes, including when dynamic color is active (the semantic colors remain legible regardless of wallpaper-derived palette — Section 3).
- **Never color alone.** Every meaning carried by color (severity tiers most importantly) is *also* carried by an icon and/or text label. A colorblind user must be able to distinguish an inviolable constraint from a preference without relying on hue.
- **Screen reader support (TalkBack).** Every interactive element has a meaningful content description; every image/icon that conveys meaning is described; the reading order is logical; the conversation is navigable.
- **Dynamic type.** The layout honors the user's system font scaling up to large sizes without breaking, clipping, or overlapping. Sukhi scaling text up must not break any screen.
- **Motion sensitivity.** Honors the system "reduce motion" setting — the calm motion becomes calmer (cross-fades instead of movement) when the user has asked for less.
- **No reliance on hearing.** Cuizine has no audio-only signals in v1; nothing important is communicated by sound alone.

## 10. Mock data strategy

> How the UI build phase produces a complete, navigable app before any real subsystem exists. This is what makes "UI first, wire up later" work cleanly.

In the UI build phase, every Orbit MVI container is backed by a **mock implementation** that returns seeded fixture data instead of calling the real engine, agents, or repositories. The mocks reuse the **canonical fixtures** from `testing-strategy.md` Section 5 — Sukhi, Aisha, and Marcus — so the UI renders realistic, foundation-consistent content from day one. Specifically:

- **Mock repositories** return the canonical Sukhi constraint graph, a seeded pantry, and a small set of saved suggestions, so Profile, Pantry, and Today all render real-looking content.
- **Mock agents** return canned, deterministic responses: the Chef returns a small set of pre-written suggestions; the Curator "understands" scripted inputs and reflects back captured constraints; the conversation plays believable Cuizine responses. This lets Flows A–F be walked end-to-end with no LLM calls.
- **Mock validation** returns pass/fail per a simple scripted rule so the suggestion and conflict-resolution surfaces can both be demonstrated.
- **Mock sync/billing** present the signed-in and alpha-bypass states so the Settings surfaces render.

The discipline: the **mock containers expose the exact same `State` shapes and accept the exact same `Intents`** as the real ones will. When the later phases implement the real engine, agents, and repositories, they implement the same interfaces, and the screens do not change. The UI is built against the contract, and the contract is honored when the real subsystems arrive. This is why the screen inventory (Section 5) specifies state and intents rather than data sources — the screen does not know or care whether its state came from a mock or the real Chef.

A visible **"mock data" indicator** is present in non-production builds during this phase so no one mistakes seeded fixtures for real behavior. It is absent from any build that could reach a real user.

## 11. What this doc does NOT specify

> The boundary. These are settled in-build, on-device, where iteration is cheap — not frozen here.

- **Exact spacing, sizing, and pixel-level layout** of each screen. The spacing *system* is locked (Section 3); the precise application per screen is in-build.
- **Final color hex values.** The color *system and intent* are locked; the exact tones are tuned in-build against real screens in both themes.
- **Final typography choices** (the specific typeface). The type *scale and intent* are locked; the face is chosen in-build.
- **Exact animation curves and durations.** The motion *principles* are locked (calm, settling); the precise timing is felt-out in-build.
- **Final copy for every string.** The voice *principles* are locked (Section 8); the words are written in-build and live in `prompts/copy/`.
- **The detailed visual design of each component's every state.** The component's *existence, purpose, variants, and states* are locked (Section 6); the visual rendering is built and refined on-device.
- **v2/v3 surfaces in detail.** This doc covers v1. The structure leaves room for v2 (multi-profile, the full billing UI, the richer Pantry) and v3 (linked-partner flows, the Observer's insights surface, BYOK setup) to slot in, but their detailed design is deferred to those versions.

The principle: lock what is expensive to change and architectural; defer what is cheap to change and visual. When in doubt about which side of the line something falls on, ask whether getting it wrong would force a structural redo (lock it) or just a visual tweak (defer it).

**This UI is not finished when Phase 2 ends — it keeps evolving as real subsystems land.** Phase 2 produces a complete, navigable UI on mock data, but the screens go on developing through every later phase: when each subsystem is wired in (the constraint engine in Phase 3, agents in Phase 5, sync and billing in Phase 6), real data shapes, real agent latency, and real edge cases routinely surface UI work the mocks could not — an empty-provenance constraint, a conflict the mock never produced, a multi-second think time the `ThinkingIndicator` must hold gracefully — and Phase 7 is an explicit polish pass. New v2/v3 capabilities attach inside the existing surfaces (Section 4) rather than adding navigation. So continued UI work is the expected path, not an exception. The discipline that keeps it coherent: cosmetic changes (spacing, color, copy, motion) flow freely in-build and need no doc update, while *structural* changes — a new screen, a new navigation destination, a new reusable component, a changed key flow, or a change to a screen's Orbit MVI `State`/`Intents` contract — flow back into this document. This doc is a living spec, and `build-conventions.md` Section 7 specifies exactly how those structural changes are reconciled here as the build progresses.

## 12. Open questions

- **Does the conversation FAB need a long-press or secondary affordance, or is single-tap-opens-conversation enough?** Tentatively single-tap only, for calm and simplicity. Revisit if in-build use suggests a quick-action shortcut (the "intents speed-dial" idea) earns its complexity. Originated here.
- **How much specialization should the ThinkingIndicator reveal?** "Considering your constraints…" surfaces depth without exposing the agent roster, but there's a line where it becomes noise. Tentatively a single calm line, tuned in-build. Originated here; relates to `agent-architecture.md` Section 3.
- **Should Today show a single suggestion or a small set to choose from?** Single suggestion is calmer and more decisive; a small set gives agency. Tentatively single, with regenerate as the escape hatch. Revisit with alpha feedback. Originated here; relates to PRD § 4.
- **Where exactly does the not-a-medical-provider disclaimer appear, and how often?** It must be honest and present without becoming anxious repetition. Tentatively: the first constraint conversation, the About surface, and the passphrase context. Revisit for the right balance. Originated here; relates to `security-and-privacy.md` Principle 2.
- **Does the Profile surface need a search/filter once a user has many constraints?** Probably not in v1 (constraint counts are low), but the surface should not be designed in a way that precludes adding it. Originated here.
- **How is the "tell Cuizine something" contextual affordance on Today visually distinguished from the FAB** so they don't feel redundant? Tentatively the FAB is the always-present front door and the contextual affordance is a quieter inline entry; tune in-build. Originated here.

## 13. Cross-references

- `vision.md` — the five pillars, the calm-precise-warm voice, the trust posture this doc translates into UI rules
- `PRD.md` § 4 — the Sukhi/Aisha/Marcus personas and the day 0→30 journey that the flows in Section 7 implement
- `agent-architecture.md` § 3 — the orchestrator and the unified-entity model that the conversation FAB (Section 4) honors; the regeneration loop behind Flow B
- `constraint-engine-spec.md` § 4, § 8, § 9 — the severity tiers (Section 6's SeverityIndicator), the conflict resolution (Section 5.2's surface), and provenance (Section 5.4)
- `build-conventions.md` § 4 — the Jetpack Compose + Orbit MVI + Material 3 commitment this doc's screen-to-state mapping relies on; § 8 — `prompts/copy/` where UI copy lives
- `data-model.md` — the constraint, pantry, and suggestion shapes the screens render
- `local-first-sync.md` § 4 — the recovery passphrase the reveal screen (Section 5.5) presents
- `monetization-and-billing.md` § 5 — the graceful-refusal UX (Section 6's GracefulRefusal); § 8 — content portability (Flow F); § 9 — the v1 alpha bypass that keeps billing UI absent in v1
- `security-and-privacy.md` Principle 2 — the not-a-medical-provider disclaimer placement (Section 8, Section 5.5's About)
- `testing-strategy.md` § 5 — the canonical fixtures the mock data strategy (Section 10) reuses
- `roadmap.md` § 3 — the build phases; the UI is built in Phase 2 (immediately after scaffolding), ahead of the engine/agent/sync/billing phases that wire into it
- ADR 0016 — native Kotlin + Jetpack Compose + Orbit MVI + Material 3, the stack this doc is designed for
- ADR 0006 — the orchestrator/no-user-facing-agent-selector decision the conversation model (Section 4) implements
- `glossary.md` — canonical terms

---

*This document is the UI/UX specification. It locks the design language, navigation, screen inventory, component set, and accessibility floor — the structure and the nervous system — and deliberately leaves final pixels to in-build iteration. It changes with deliberate intent, and the visual fine-tuning that happens in-build is folded back here when it rises to the level of a structural decision.*
