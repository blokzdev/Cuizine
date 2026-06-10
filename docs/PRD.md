# Cuizine — Product Requirements Document (PRD)

> The constitution lives in `vision.md`. This doc translates the constitution into specifics: who we're building for, what they will experience, what's in v1, what's out, and how we know we're done. When this doc and the vision conflict, the vision wins; when this doc and a downstream architecture doc conflict, this doc wins until updated by deliberate decision.

## 1. Purpose & scope

This PRD specifies the product requirements for **v1 of Cuizine** — the closed Canadian alpha, distributed by APK to 15-25 deliberately-recruited users, validating the multi-agent constraint engine on real layered constraints in real culturally-specific lives.

This PRD does **not** specify v2 or v3 in detail. v2 and v3 features are referenced where they affect v1 architectural decisions (so we don't build something v2 will have to tear out), but their full requirements are deferred to future PRD revisions.

This PRD assumes the reader has read `vision.md` and the ADRs in `decisions/`. Concepts established there are not re-derived here.

## 2. Primary persona

> A primary persona is an internal design tool, not a user-facing artifact. It is a specific imagined human used to keep design decisions honest and focused. Designing for one specific person produces a better product than designing for "everyone." If Cuizine works beautifully for the primary persona, it will work well for a much broader range of users — because the constraint engine, the cultural fluency layer, and the calm-precise-warm tone all generalize. Specificity in design produces better generality, not worse.

### Sukhpreet "Sukhi" Kaur Dhillon

**Age:** 53
**Location:** Brampton, Ontario
**Lives with:** Her husband Harpreet (56), her daughter Simran (16, in grade 11), and her mother-in-law Beeji (78), who moved in two years ago. Her older son Arjun (21) is away at university in Waterloo and comes home most weekends.
**Background:** Born in a village outside Ludhiana, Punjab. Moved to Canada at 24 after marriage. Worked for many years as a personal support worker at a long-term care facility; recently reduced to part-time after her diagnosis. Speaks Punjabi at home, English at work, both fluently.
**Cooking life:** Cooks dinner for the household most nights. Learned to cook from her mother and her dadi. Her food is the household's anchor — it is how she expresses care, how the family stays connected to Punjab, and what her mother-in-law and her teenager *both* expect on the table. She does not own a meal-planning app and has never used one. She uses WhatsApp daily, occasionally watches recipe videos on YouTube, and has an Android phone (a Samsung mid-range, two years old) that her son set up for her.

### The moment of need

Six weeks ago, Sukhi's family doctor told her she has Type 2 diabetes. Two weeks ago, after a particularly bad stretch of bloating, cramping, and unpredictable bathroom emergencies, a gastroenterologist told her she also has IBS — likely IBS-M (mixed type), though the diagnosis is still being refined. She is now managing two overlapping conditions with two different sets of dietary rules that sometimes contradict each other. The diabetes nutritionist gave her a printed handout about carbohydrate counting and glycemic index. The gastroenterologist mentioned the low-FODMAP diet but didn't explain it well, and the handout he gave her listed foods she'd never heard of in English and couldn't find in her usual stores.

She is scared. She is also embarrassed — she has cared for elderly diabetic patients at her job for years and feels like she should have seen this coming. She has not told her mother-in-law about the IBS diagnosis because she finds the symptoms shameful to discuss across generations. She has told her husband about both diagnoses, but he is supportive in a vague, unhelpful way ("we'll figure it out, beta") that does not actually help her plan tomorrow's dinner.

She is overwhelmed by the contradictions in the advice she has received. The diabetes handout says to eat more whole grains and legumes; the IBS handout says many of those exact foods are high-FODMAP and likely triggers. She wants to keep cooking the food her family knows and loves — saag, dal, roti, sabzi, sometimes a chicken curry on Sundays — but she now believes that "her" food is the food that's hurting her, and she does not know how to live with that. She has started skipping meals out of confusion and has lost four kilograms in six weeks, which her doctor told her not to do.

Her daughter Simran is the one who installed Cuizine on her phone, after seeing it mentioned in a Facebook group for caregivers of South Asian seniors with chronic illness. Simran said "Mama, just try it for a week, please."

### What Sukhi needs from Cuizine

She does not need another nutritionist. She has had two and they contradicted each other.
She does not need a recipe app. She has YouTube and her own memory.
She does not need a calorie tracker. She has tried those before and they made her anxious.

She needs **something that holds all the rules for her, in the background of her life, so she can stop thinking about them.** She needs to be able to say "I want to make rajma tonight" and have something tell her *how* she can make rajma in a way that won't spike her blood sugar and won't trigger her IBS — using ingredients she actually has in her kitchen and ingredients she actually knows how to find at her local FreshCo or Patel Brothers. She needs the answer to feel like *her food*, not a substitute for her food. She needs to feel less alone with this.

She needs the app to understand that her kitchen has dal and atta and ghee and ajwain and asafoetida and that "rice" means basmati specifically. She needs it to know that fasting on Tuesdays is a thing she does. She needs it to not lecture her about ghee. She needs it to handle the fact that her mother-in-law can eat anything and her daughter is a vegetarian (newly, since starting university applications) and her husband is fine with whatever, and that all four of them eat off the same table almost every night.

### Who Sukhi is *not*

Sukhi is not a power user. She will not configure settings. She will not read tooltips. She will not understand the words "constraint graph" or "agent" or "LLM." She will not appreciate complexity surfaced as choice. She will appreciate the app *quietly being right* without making her work for it.

Sukhi is not desperate enough to pay for something that doesn't immediately help her. She has medication costs, her son's tuition, and a mortgage. She will give Cuizine roughly two weeks of patience before she decides whether it's helping or just adding to her cognitive load. The first week is the most important week of her relationship with the product. If the first week feels like *another* thing to manage, she will uninstall it without telling her daughter.

Sukhi is not a stranger to technology, but she is not a stranger to *abandoning* technology either. She has tried two diabetes apps before and uninstalled both within a week — one was too American, one was too clinical. She remembers both experiences specifically. The bar Cuizine has to clear is "feels different from those, immediately."

### What it looks like when Cuizine is working for Sukhi

Three weeks into using Cuizine, Sukhi opens the app on a Tuesday evening. She has fasted today (it's a Tuesday, she always fasts on Tuesdays for Hanuman). She types, in English, "what should I make for dinner that everyone will eat." The app already knows she has a head of cauliflower in the fridge that needs using, that her daughter doesn't eat meat, that her mother-in-law loves anything with ginger, that Sukhi herself can't have onions or garlic in any meaningful quantity tonight because her gut has been off all day, that her blood sugar has been running high this week, and that today is a Tuesday so she's been fasting and her dinner needs to be both light and grounding. It suggests **gobi sabzi with hing and cumin instead of onion-garlic, served with phulka roti made from a low-FODMAP atta blend, with a side of cucumber raita using lactose-free yogurt for her gut.** She has every ingredient. The recipe is in her own cooking idiom. There is no preamble, no upsell, no warning label about her conditions. It just says: here is what to cook. She cooks it. Her mother-in-law approves. Her daughter eats two helpings. Sukhi feels, for the first time in six weeks, that she has not lost her kitchen.

That is the moment Cuizine is built for. Every screen, every interaction, every default value, every word of copy in v1 should be evaluated against the question: *would this get Sukhi closer to that Tuesday evening, or further from it?*

## 3. Secondary personas

> Secondary personas exist in this document so that important user types have a face attached to them when edge cases arise. They do not drive v1 design decisions — Sukhi does. But when an edge case affects one of them and we have to make a call, we make the call with them in mind rather than with an abstract user in mind.

### Marcus Chen — the caregiver (v2 lead persona)

Marcus is 42, lives in Mississauga, and is the primary caregiver for his father, Robert (74), who has stage-3 chronic kidney disease and lives with Marcus, his wife Janelle, and their two children (ages 9 and 12). Marcus is a project manager at a logistics company, comfortable with apps, and has been managing his father's increasingly complex food restrictions on a shared Google Doc that no longer scales. He cooks dinner for the household four nights a week; his wife cooks the other three. The 9-year-old has a tree nut allergy.

Marcus is the user for whom **Cuizine Family** (v2) is the headline feature. He needs to manage his father's CKD diet (low sodium, low potassium, low phosphorus, controlled protein) as a *dependent profile*, his daughter's nut allergy as another *dependent profile*, and his own preferences as the *primary profile* — and he needs Cuizine to plan meals that work for all of them at the same table. He is the reason ADR 0004 exists.

In v1, Marcus is an edge case: he might use Cuizine for himself but he is not the user we are designing for. We mention him in this PRD because his needs shape architectural decisions in v1 (the data model treats profile as first-class from day one) even though his features ship in v2.

### Aisha Rahman — the observant temporal-constraint user

Aisha is 34, lives in Brampton, is a software engineer, married with no children. She is an observant Muslim and during Ramadan she fasts sunrise-to-sunset. The rest of the year she has no medical constraints, only religious ones (halal) and personal preferences (mostly pescatarian for environmental reasons, but flexible). During Ramadan her eating life inverts: two meals (suhoor before dawn, iftar at sunset), specific foods that hold her energy through the fast, specific cultural traditions for breaking the fast, and a meaningful spiritual relationship with food that no app has ever respected.

Aisha is the user who tests the **temporal scoping** of pillar 1 most directly. Her constraint set is *almost entirely time-bound* — most of her constraints turn on for 30 days a year and turn off afterward, with sub-day variations during the fast itself. She is the reason the constraint engine schema treats temporal scopes as a first-class field rather than an afterthought.

In v1, Aisha is also an edge case: a few alpha users will be like her, and the engine has to handle them, but Sukhi's needs drive the primary design.

## 4. The v1 user journey

> This section walks Sukhi through her first 60+ days with Cuizine. It is written as a narrative because narratives surface the moments where things can go wrong; flowcharts hide them. Every numbered moment below is a decision point where Sukhi could abandon the product, and the design implications of each are named explicitly. When the agent is building any v1 screen, the test is: *does this screen serve the corresponding moment in Sukhi's journey, or does it work against it?*

### Day 0 — Installation

Sukhi's daughter Simran installs Cuizine on Sukhi's Samsung from a sideloaded APK link — alpha distribution does not go through the Play Store (per ADR 0001). Simran says "Mama, just try it for a week, please" and hands the phone back. Sukhi taps the icon for the first time later that evening, after dinner has already been cooked and the kitchen is clean. She is tired. She has not eaten dinner herself because nothing on the table felt safe for both her diabetes and her IBS, and she filled up on chai and biscuits instead.

**What v1 must do at this moment:** the very first screen must not be a form. It must not ask her name. It must not request permissions. It must not show a dashboard with empty states. It must show *one* warm sentence that names what Cuizine is in her own emotional language ("Cuizine helps you cook food you love, even when your body has new rules") and *one* low-friction action ("Let's start with what you cook"). The bar is "Sukhi understands what this is in three seconds and is willing to tap once."

**Failure mode to avoid:** any first screen that feels like the start of a 10-step setup wizard. Sukhi has already tried two diabetes apps and uninstalled both. The third one has approximately 90 seconds to feel different from those.

### Day 0 — First five minutes (the constraint conversation)

Cuizine begins with what we will internally call **the constraint conversation**. This is not a form. It is a guided exchange with the **Curator agent** that asks Sukhi, in plain language, three to five questions over a few minutes. The questions are sequenced so that *the easiest, most affirming question comes first* and the harder, more emotional questions come later, after Sukhi has already invested a small amount of trust.

The first question is something like: *"What kind of food do you usually cook at home?"* Free-text answer, voice input optionally available, examples provided ("Punjabi, Italian, Caribbean, Filipino, mixed — whatever it is, just tell me"). The Curator parses this into structured cultural-context fields (cuisine of origin, regional ingredient availability, household food traditions — pillar 5 lighting up immediately on day zero, per ADR 0002). Sukhi types "Punjabi mostly, some North Indian" and feels mildly seen.

The second question asks about *who she's cooking for*. Not yet about household profile management — that's v2 — but about the *context* of her cooking life, which the engine needs to understand even when planning only for her. "Just me, just my partner, my whole family, mostly for others?" Sukhi says her family. The Curator gently asks if there are dietary needs in the family the engine should be aware of when suggesting meals — Sukhi mentions her daughter is newly vegetarian, her mother-in-law eats anything, her husband is fine with whatever. These get recorded as *cooking context*, not as separate profiles (single-profile in v1, per ADR 0004) — but the data is captured in a way that v2 can transform into proper dependent profiles without re-asking. This is the architectural readiness from ADR 0004 in concrete UX form.

The third question is the medical one, and it's the hardest. The Curator asks: *"Are there foods you're trying to avoid right now, for any reason — health, religion, preference?"* Note the framing: not "what's wrong with you," not "list your conditions," not a dropdown of diseases. It's a question about *food*, asked with care. Sukhi can answer however feels natural — she might say "I have diabetes and IBS, and I'm not supposed to have a lot of sugar, and onions and garlic make me sick lately." The Curator parses this into structured constraints with appropriate severity (diabetes → medical, IBS triggers → medical, all marked as user-stated rather than clinically-validated, per the language we agreed on in vision.md's alpha exit criteria), and quietly creates the constraint graph entries.

The Curator does *not* lecture, validate, dispute, or expand. It does not say "have you considered the low-FODMAP diet?" It does not say "you should consult a doctor." It receives the information warmly and moves on. **This restraint is load-bearing.** A Curator that adds value here would feel like every other diabetes app Sukhi has uninstalled.

A fourth and fifth question handle preferences ("Are there foods you really love that you don't want to give up?") and patterns ("Any days of the week that are different — fasting, special meals, anything?"). Sukhi mentions she fasts on Tuesdays. The Curator records this as a temporal constraint with a recurring weekly scope, per the temporal-scoping discipline that pillar 1 requires (and that secondary persona Aisha exists in this PRD to keep us honest about).

**At the end of the constraint conversation — five minutes in, maybe seven — Sukhi has a real, structured constraint profile.** She does not know this. From her perspective, she answered some questions about her food. From Cuizine's perspective, the constraint engine now contains: cultural context (Punjabi, North Indian, immigrant household), cooking-for context (family of four at the table most nights — her son Arjun is away at university — vegetarian daughter, traditional mother-in-law), three medical constraints (T2 diabetes, IBS-M general, IBS-M onion/garlic specific) all marked as user-stated, two preferences (loves dal, loves rajma), and one temporal constraint (Tuesday fast, recurring). The entire structure of the constraint engine that the rest of the product depends on has been built from a five-minute conversation. **This is the magic of the local-first model: nothing has been sent to the cloud yet. The Curator agent's parsing happened on a single brief inference call (since Sukhi is on the paid trial — the alpha is full-access per ADR 0007), but the resulting structured data lives entirely on Sukhi's device.**

**What v1 must do at this moment:** the constraint conversation must feel like a conversation, not a survey. Each question must be answerable in free text (or by tapping suggested answers for users who prefer not to type). Each answer must visibly produce *something* — not an empty acknowledgment. If Sukhi says "I have diabetes," she should see a small, gentle moment of "got it — I'll keep that in mind for everything from now on" rather than a checkbox getting checked. The Curator's tone must be calm, precise, warm — exactly the default voice committed to in vision.md.

**Failure mode to avoid:** treating constraint entry as form-filling. Any version of v1 that has a multi-screen "add your conditions" form with checkboxes will fail Sukhi. The conversation framing is non-negotiable.

### Day 0 — The first meal suggestion

After the constraint conversation, Cuizine offers *one* immediate value: a meal suggestion for *tomorrow*. Not a week's plan. Not a recipe library to browse. One suggestion, for one meal, that respects everything Sukhi just told it.

The **Chef agent** generates a suggestion using the constraint graph that was just built. For Sukhi specifically, this might be: "For tomorrow's dinner, you could make masoor dal with spinach and a side of cucumber raita. This works with your diabetes (low glycemic load, high fiber), doesn't trigger your IBS (no onion or garlic, which I'll always remember to avoid for you), and is ready in about 30 minutes. Want me to show you how I'd cook it?"

That last sentence is important. Cuizine offers to walk Sukhi through *how it would cook the dish*, not "here's a recipe from our database." The Chef agent generates the recipe in Sukhi's idiom — measurements in the units she uses, ingredients named the way she'd name them ("rai" for mustard seeds, "haldi" for turmeric, with English in parentheses for ambiguous cases), techniques described the way her mother would have described them. This is pillar 5 making first contact with reality.

**What v1 must do at this moment:** the first suggestion is the most important moment of the whole product, because it is where Sukhi decides whether Cuizine is *real* or just clever marketing. The suggestion must be (1) something Sukhi can imagine cooking, (2) something that visibly respects her constraints without lecturing about them, (3) framed in her cultural idiom, and (4) actionable tonight or tomorrow with ingredients she can plausibly have. The Chef agent's prompts must be designed to satisfy all four simultaneously, every time.

**Failure mode to avoid:** generating a "diabetic-friendly" suggestion that is grilled chicken and salad. Generating a recipe for "masoor dal" in cup measurements with American ingredient names. Suggesting something that requires asafoetida when the user is in a context where that might not be available. Lecturing in the recipe intro about why this dish is good for diabetes. Any of these failures, on day zero, costs us the user.

### Days 1-7 — The first week (the most important week)

Sukhi cooks the suggested meal on day 1. It's good. She doesn't tell Simran she liked it. The next day, she opens Cuizine again — this time without Simran prompting her — and asks for another suggestion. This is the moment we needed to earn.

Throughout the first week, the **Chef agent** is generating suggestions one at a time, on demand, with the constraint graph as the source of truth. The **Pantry agent** is *not yet* heavily involved, because Sukhi has not entered her pantry — and v1 does not require her to. Pantry-aware planning (pillar 2) is supported but not mandatory; users who don't want to track pantry get suggestions based on "ingredients you're likely to have given your cuisine and cooking history," which the Chef agent can infer from the constraint graph's cultural context. Sukhi is in this mode for her first week.

By day 4 or 5, Sukhi will probably have a moment of friction. The Chef will suggest something with an ingredient she doesn't have, or a technique she doesn't like, or a dish her family wouldn't accept. **What v1 must do here:** every suggestion has a small, low-friction "this isn't quite right" affordance that lets Sukhi say *why* — ideally with one tap (suggested reasons: "don't have the ingredients," "family wouldn't like it," "too much work tonight," "wrong for how I'm feeling," "other") and an optional free-text. The Curator agent quietly absorbs this feedback and updates the constraint graph (or a parallel preferences layer) with the new information. The next suggestion is better. **This feedback loop is the second-most-important feature of v1, after the constraint conversation itself**, because it is where Cuizine *learns to be Sukhi's chef* rather than a generic chef who happens to know her rules.

**Failure mode to avoid:** rejection feedback that asks Sukhi to write a paragraph explaining why. The friction must be near-zero or she will silently stop using the product instead of telling it what's wrong.

### Days 7-21 — Becoming the primary tool

By the end of the first week, Sukhi has either uninstalled Cuizine or is starting to use it daily. There is very little middle ground — products like this are loved or ghosted, rarely tolerated. If she's still here on day 8, the second and third weeks are about *deepening* the relationship.

In this window, several things happen for the first time:

**The first temporal-context test.** Sukhi opens Cuizine on a Tuesday. She has been fasting all day. The Chef agent — because the Tuesday-fast temporal constraint was captured in the original conversation — suggests a *light, grounding* dinner appropriate for breaking a fast in her tradition (not any old meal). Sukhi notices. This is pillar 4 (instant context adaptation) showing up in her real life, and it is the moment many users will mentally upgrade Cuizine from "an AI recipe app" to "something that actually pays attention."

**The first multi-constraint conflict.** Sukhi's blood sugar runs high one morning. She mentions this to Cuizine in a free-text input ("my sugar was 9.2 this morning"). The Curator agent receives this as a temporary contextual signal (not a permanent constraint) and the Chef's suggestion for that day's meals quietly leans more toward lower-glycemic options. Sukhi may or may not notice consciously, but the system is responding. **What v1 must do here:** the free-text input affordance must exist and must work — users need to be able to *tell Cuizine things* in plain language and have them register. This is the Curator agent's continuous-listening role.

**The first family-context moment.** Sukhi asks Cuizine for a meal that "everyone will eat tonight." The Chef agent uses the cooking-for context (four at the table most nights — vegetarian daughter, traditional mother-in-law, easygoing husband — out of a five-person household, with her son Arjun usually away at university) to suggest something that satisfies all of them. This is the household concept *in v1 form* — without dependent profiles, without multi-profile reasoning, but using the cooking-context fields the Curator captured on day zero. Sukhi gets a meal that works for everyone. **This is what makes Sukhi naturally want to upgrade to Cuizine Family in v2.**

**The first recipe-reuse moment.** Sukhi cooks the same dal twice in two weeks because she liked it. Cuizine remembers and offers it back without making her search. The local-first history is doing its job.

### Days 21-60 — Becoming background

By the third week, if Cuizine is working, Sukhi has stopped *thinking* about food rules. The constraint engine is doing it for her, in the background of her life, exactly as the vision's success-in-one-sentence promised. She uses Cuizine roughly daily for meal suggestions, occasionally to log what she actually cooked, occasionally to mention how she's feeling (a free-text note that updates her contextual state). She has uninstalled neither of the two old diabetes apps from before — they were already gone — but she has *replaced them*, and the replacement feels nothing like them.

By day 60, if she's still using Cuizine as her primary meal-planning tool, **she meets one of the alpha exit criteria from vision.md**: "alpha users have used Cuizine as their primary meal-planning tool for 60+ consecutive days." She is one of fifteen-plus such users. The closed alpha is on track to graduate to v2.

The last thing to note about days 21-60: Cuizine should *quietly become less interruptive* as it learns Sukhi. Early days have more questions, more clarifications, more "got it." Later days are smoother, more confident, less chatty. **The v1 product must have a sense that it is learning to leave the user alone, not adding more features as it goes.** Many AI products fail this test — they keep finding new things to interrupt with. Cuizine must not.

## 5. v1 feature scope

> Every feature listed here is justified by reference to a pillar, the persona, or an ADR. No orphan features. The scope is deliberately tight — v1 is a closed alpha proving the engine, not a public launch proving the market.

### Constraint engine and profile (the foundation)

- **The constraint conversation** — the guided 5-minute dialogue handled by the Curator agent that builds the user's structured constraint graph from natural-language input. *Justified by: pillar 1; Sukhi's day-0 journey; ADR 0002 (cultural fluency from day zero).*
- **Single-profile data model** with profile-as-first-class entity and explicit ownership field, even though only one profile per account exists in v1. *Justified by: ADR 0004 (architectural readiness for v2 multi-profile).*
- **Constraint graph storage** with typed constraints, severity levels (medical / religious / preference / context), temporal scopes (recurring weekly, date-bounded, indefinite), conflict resolution rules, and provenance (when added, by whom, original phrasing). *Justified by: pillar 1; Aisha persona; the multi-condition Sukhi case.*
- **Continuous constraint updates via free-text input** — the user can tell Cuizine things at any time ("my sugar was high this morning," "I'm avoiding dairy this week," "I'm in Toronto for a few days") and the Curator agent updates the relevant graph fields. *Justified by: pillar 4; Sukhi's days 7-21 journey.*

### The Chef agent (recipe and meal generation)

- **Single-meal suggestion generation** — Sukhi asks for a meal, the Chef returns one suggestion appropriate to her current constraint state, cultural context, time of day, and recent history. *Justified by: pillar 1; pillar 5; Sukhi's day-0 journey.*
- **Inline recipe walkthrough** — when Sukhi accepts a suggestion, the Chef generates the cooking instructions in her own idiom (units, ingredient names, techniques) rather than retrieving a generic recipe. *Justified by: pillar 5; ADR 0002.*
- **Low-friction rejection feedback** — every suggestion includes one-tap "this isn't quite right" affordances with structured reasons (don't have ingredients / family wouldn't like it / too much work / wrong for how I'm feeling / other) plus optional free text. *Justified by: Sukhi's day 4-7 friction; the feedback loop is the second-most-important feature in v1.*
- **Recipe history and reuse** — meals Sukhi has cooked before are remembered locally and offered back when relevant. *Justified by: local-first trust posture; Sukhi's "first recipe-reuse moment."*
- **"What works for everyone" mode** — Sukhi can ask for a meal that satisfies her cooking-for context (the family she records on day 0), even though v1 is single-profile. The Chef uses the captured cooking-context fields as inputs to its reasoning. *Justified by: Sukhi's "first family-context moment"; sets up natural v2 upgrade per ADR 0004.*

### The Pantry agent (lightweight in v1)

- **Optional manual pantry tracking** — Sukhi can tell Cuizine what she has on hand if she wants to. She is not required to. *Justified by: pillar 2; not making Sukhi do work she won't do.*
- **Inferred pantry context** — when Sukhi has not entered a pantry, the Chef agent assumes she has the basic staples appropriate to her cultural context (atta, common dals, common spices) and only suggests recipes that don't require unusual ingredients. *Justified by: pillar 2 in degraded form; Sukhi's first week journey.*

### Local-first storage and sync

- **SQLite via Room** for the local data layer (per ADR 0016), holding the constraint graph, recipes, history, preferences, and pantry.
- **End-to-end encrypted cross-device sync** so Sukhi can use Cuizine on a tablet later if she wants to, without the data ever being readable by us. *Justified by: vision.md trust posture; ADR 0007's "free tier without ever sending data to cloud" promise (note: the alpha is on the paid trial, but the architecture must support the free-tier path).*
- **Export-and-walk-away** — Sukhi can export her full data at any time. *Justified by: vision.md trust posture.*

### The multi-agent orchestration layer

- **Curator, Chef, and a lightweight Pantry agent** — three agents in v1, not the full six. The Planner, Sourcing, and Observer agents are *not* in v1 scope (see Section 6). *Justified by: scope discipline; the agent topology will be specified in `agent-architecture.md`.*
- **Per-agent provider routing** via the `ModelProvider` interface, with Anthropic Claude as the default for the Curator and Chef agents and a cheaper model (Gemini Flash or Claude Haiku) for the Pantry agent's fuzzy matching. *Justified by: ADR 0006.*
- **Automatic model escalation based on task stakes** — invisible to the user. *Justified by: ADR 0006.*

### Onboarding, settings, and the "calm tone" requirements

- **The constraint conversation as the entire onboarding flow** — no separate setup wizard, no permissions screens before they're needed, no empty dashboards. *Justified by: Sukhi's day-0 journey.*
- **Settings are minimal in v1** — only essentials (export data, basic notification preferences, sign out). No model selection, no token displays, no advanced options. *Justified by: ADR 0006; ADR 0007's forever commitments; Sukhi's "not a power user" framing.*
- **All copy follows the calm-precise-warm voice** as defined in vision.md. v1 copy is hand-written, not AI-generated, and must be reviewed for tone consistency before alpha. *Justified by: vision.md tone & personality.*

### Alpha-specific instrumentation

- **Local event logging** — every meal suggestion, every acceptance, every rejection (with reason), every constraint update — stored locally on the user's device, exportable by the user when they want to share with the founder for alpha feedback. *Justified by: alpha-specific need for the founder to understand what's working without violating local-first trust posture.*
- **No telemetry to Cuizine servers** — alpha users opt into sharing event logs with the founder explicitly, on their own initiative, by exporting and sending. *Justified by: vision.md "no analytics on personal data, ever."*

## 6. Explicitly out of v1

> Every item below is excluded from v1 deliberately. The reasons are listed because the agent will be tempted to add some of these "while it's already in there" — and we need to prevent that.

**Multi-profile and household features.** No dependent profiles, no household planning, no shared pantries. Single profile only. *Reason: ADR 0004 ships these in v2; v1 ships only the architectural readiness.*

**Linked partners and cross-account anything.** No sharing, no co-planning, no peer profiles. *Reason: ADR 0004 ships these in v3.*

**The Planner agent.** No week-ahead planning, no multi-day meal plans, no "plan my whole week" feature. v1 is single-meal-on-demand only. *Reason: pillar 1 leads in v1; the Planner is a v2 capability built on top of the proven engine.*

**The Sourcing agent and Instacart integration.** No grocery integration, no shopping lists generated for delivery, no stock checks. *Reason: ADR 0003; v1 alpha users will work with their existing grocery habits.*

**The Observer agent (long-term health intelligence).** No trends, no predictive warnings, no historical analytics. *Reason: pillar 3 is honestly a v3 feature because it requires accumulated user data to be meaningful (per vision.md and ADR 0007).*

**AI-powered receipt scanning, fridge photo ingestion, or any vision-model-based pantry capture.** Manual pantry entry only in v1. *Reason: scope; vision-model integration is meaningful additional engineering surface and not required to prove the engine.*

**Automatic recipe import from URLs (with AI parsing).** Users can manually transcribe recipes they want to remember; AI-assisted import is a v2 feature. Schema.org Recipe markup parsing for URL imports is built and tested *internally* during v1 alpha (per ADR 0007) but not exposed in the UI yet. *Reason: scope; v1 is about the constraint engine, not the recipe library.*

**Configurable agent personas / tone selection.** v1 ships one default voice (calm, precise, warm). Configurable personas are a v3 feature. *Reason: ADR 0006; vision.md.*

**User-facing model selection or credit economy.** Not now, not ever. *Reason: ADR 0006; ADR 0007.*

**Platform support.** Android only, across all versions (per ADR 0015). There is no iOS, web, or desktop version planned; v3 "global expansion" is geographic (Android in new markets), not cross-platform. *Reason: Cuizine's South-Asian-skewing target audience is heavily Android, and single-platform focus suits a solo founder. See ADR 0015 for the full rationale.*

**Multi-language support.** English only in alpha. *Reason: scope; v1 alpha is recruited from English-speaking Canadian network.*

**Push notifications and engagement loops.** No reminders, no streaks, no daily nags. The product earns the user's daily attention by being useful, not by pestering them. *Reason: vision.md tone; Sukhi will silently uninstall a product that nags her.*

**Onboarding tutorials, tooltips, or help screens.** The constraint conversation is the only onboarding. If a v1 feature requires a tutorial to use, the v1 feature is wrong. *Reason: Sukhi will not read tooltips.*

**Account creation as a separate step from first use.** Cuizine works immediately on first launch. Account creation, if it exists at all in v1, is deferred until the user has experienced value and only happens if needed for sync. *Reason: Sukhi's day-0 journey; the first 90 seconds are sacred.*

**Social features of any kind.** No sharing recipes, no community, no profiles others can see. *Reason: vision.md "Not a social network or recipe-sharing community."*

**Calorie tracking, macro tracking, or any quantified-self framing.** *Reason: vision.md "Not a calorie tracker"; will alienate Sukhi.*

**Medical disclaimers as headline UI copy.** Required legal disclaimers exist in the settings/about screens, not in onboarding or in the meal suggestions themselves. *Reason: Sukhi needs to feel cared-for, not legally-protected-against. The disclaimers must exist; they must not be the user's first impression.*

## 7. Acceptance criteria for v1

> v1 is "done" when the closed alpha can ship to its first user. Done is *not* "perfect," and done is *not* "all five pillars complete." Done is a specific, observable bar — listed below — that protects against perfectionism while maintaining the quality the persona requires. When in doubt, the answer is "ship to one alpha user, see what breaks, fix it, ship to the next." Done is iterative within the alpha, not a single cliff.

### Build-complete criteria (the bar to ship to the first alpha user)

The following must be true before APK distribution to the first alpha user:

- **The constraint conversation works end-to-end on a real Android device.** A new user can install the APK, open Cuizine for the first time, complete the constraint conversation in 5-10 minutes, and have a valid structured constraint graph stored locally. No crashes, no dead ends, no lost input.
- **The Chef agent can generate a culturally-appropriate meal suggestion** for any one of the alpha test personas (the "hard cases" suite — see `testing-strategy.md`) with the user's full constraint graph respected. The output must be in the user's cultural idiom, must not violate any medical or religious constraint marked in the graph, and must be cookable with plausibly-available ingredients.
- **The "this isn't quite right" rejection feedback loop works**, and the next suggestion after a rejection visibly improves on the rejected dimension. This is the second-most-important mechanism in v1 after the constraint conversation itself.
- **Free-text constraint updates work** — a user can type "my sugar was high this morning" or "I'm in Toronto for a few days" and the Curator agent updates the constraint graph appropriately, with the change visible in the next suggestion.
- **Local-first storage works correctly under realistic load.** A user with 60 days of accumulated meal history, a full constraint graph, and a few hundred recipes does not experience noticeable lag or storage issues on a mid-range Android device (the Samsung-mid-range-two-years-old benchmark, per Sukhi's persona).
- **The constraint engine schema is versioned and migrate-able** (per ADR 0004's requirement that v2 multi-profile is an enabling code path on existing data, and per the foundation `data-model.md` migration commitment). v1 ships with version 1 of the schema; the migration tooling exists even though it has nothing to migrate yet.
- **The three v1 agents (Curator, Chef, lightweight Pantry) follow the per-agent provider routing pattern** from ADR 0006, with at least Anthropic and one alternate provider implemented as `ModelProvider` adapters. Single-provider shortcuts are not allowed even though only one provider is used at first; the architecture must be in place.
- **Anthropic API zero-data-retention is enabled** for all inference calls in v1. (Per the trust posture in vision.md, this is non-negotiable.)
- **No cross-profile data sharing exists** because no cross-profile data exists in v1. The single-profile-per-account constraint is enforced at the data layer, not just by UI absence. ADR 0004's architectural readiness must not become architectural promiscuity.
- **The hand-written copy** for the constraint conversation, the meal suggestion framing, the rejection prompts, and the (minimal) settings screens has been reviewed by the founder for tone consistency with vision.md's calm-precise-warm voice. Copy review is a human checkpoint — the agent does not get to ship copy without it.
- **Local event logging works**, exports cleanly, and the founder has tested the export-and-receive flow personally on at least one device.

### Alpha-success criteria (the bar to graduate v1 → v2)

These are reproduced from `vision.md` for visibility:

- **15+ alpha users** have used Cuizine as their primary meal-planning tool for **60+ consecutive days**.
- **Zero constraint violations** in that window for any constraint the user has marked as medical or religious.
- **Stated willingness to recommend** Cuizine to someone the user cares about, captured in conversation by the founder (not via an in-app prompt).
- **All five pillars functional** — not necessarily complete, but functional. For v1, "functional" specifically means: pillar 1 (layered constraints) is the lead and is robust; pillars 2 and 5 (pantry-aware planning and cultural fluency) work in degraded form (inferred pantry, cultural context as captured by the Curator); pillar 4 (instant context adaptation) works for the temporal and contextual cases the Curator handles; pillar 3 (long-term health intelligence) is *honestly absent in v1* and the alpha exit criteria do not require it.

We do not move these goalposts. We do not let perfectionism delay us once they are met.

### Quality bar within the alpha

The alpha is iterative, not a single ship-and-pray event. Within the 60-day alpha window, the quality bar is:

- **Bugs that affect a single user are fixed within 24-48 hours** when the founder can reproduce them.
- **Bugs that affect the constraint engine's correctness on medical or religious constraints are fixed immediately.** A meal suggestion that violates a user's diabetes constraint or their halal observance is a *severity-zero* incident, not a typo. Stop everything, fix it, talk to the user, understand why it happened.
- **Bugs that affect the calm-precise-warm tone are taken seriously** — they're not severity-zero, but they're not "we'll get to it." If a user says Cuizine sounded preachy or cold, we fix the prompt or the copy and we ship the fix.
- **No new features ship during the alpha unless they fix something the alpha revealed.** Feature creep during the alpha is forbidden. The alpha tests what exists; v2 planning happens after.

## 8. Open questions

> This section is honest about what we have not yet decided. Every entry below is a real question that will need an answer before or during the build, and not pretending we have an answer is more useful than pretending we do. When an answer is reached, the resolution moves to the appropriate doc (PRD update, ADR, architecture spec) and the entry here is removed.

### Onboarding and first-touch

- **What is the *exact* sequence of questions in the constraint conversation?** This PRD names the shape (5-7 questions, easiest first, medical question framed as "foods you're avoiding") but does not lock the wording. The wording will need real iteration with alpha users; the v1 build should ship with a *first-attempt* version that the founder has hand-written and is willing to throw away.
- **What does the very first screen actually look like, visually?** The PRD specifies what it must achieve (3-second comprehension, one tap, no setup wizard feel) but not the visual treatment. This is a `frontend-design` skill question that arises during build.
- **How does the user trigger their first meal suggestion** after the constraint conversation completes? Auto-suggested? Or does Cuizine wait for the user to ask? The PRD assumes auto-suggested ("Cuizine offers *one* immediate value: a meal suggestion for tomorrow") but this is a UX call that should be tested early in alpha and may flip.

### Constraint engine specifics

- **How are user-stated medical constraints distinguished from clinically-validated ones in the schema?** The PRD's framing is "user has marked as medical" but the data model needs to record provenance precisely. *To be defined in `data-model.md`.*
- **How does the engine handle constraints the user mentions but doesn't formally add?** Example: in a free-text update, Sukhi says "I think dairy might be bothering me too." Is this a soft constraint? A flag for the next suggestion? A prompt to add it formally? *Worth testing in alpha.*
- *(Constraint conflict resolution policy resolved in ADR 0009 — the four-tier severity model with explicit user-visible escalation, never silent overruling.)*

### Chef agent specifics

- **How long should a meal suggestion's response time be, end-to-end?** The Chef agent makes one or more LLM calls per suggestion, and the user is waiting. Sub-2-second is ideal but probably not achievable on first try. Sub-5-second is acceptable for v1. Sub-10-second is the absolute ceiling. *To be measured during build.*
- **How does the Chef agent handle "I want to make X tonight" requests** where the user names a dish? The PRD describes both directions (Cuizine suggests, user requests) but the latter is harder. *Likely a v1 stretch goal, not a launch requirement.*
- *(Post-generation constraint validation resolved in ADR 0010 — deterministic validator runs on every Chef output, regenerates on failure with bounded retries, surfaces honest fallback message when retries exhaust.)*

### Pantry and ingredients

- **How do we model "Sukhi probably has these staples" without making her enter them?** The Chef agent's "inferred pantry context" needs a heuristic or a small model layer. Could be as simple as "Punjabi household → assume atta, common dals, common spices" or as complex as a learned model. v1 should ship with the simple version. *To be defined in `agent-architecture.md`.*
- **What ingredient vocabulary does the Pantry agent normalize against?** USDA? A custom Cuizine catalog? Crowdsourced? *Significant decision deferred to `data-model.md`.*

### Local-first sync and storage

- **Which encrypted sync library or service** are we using? Options include building on top of an existing CRDT library, using a service like Turso/PowerSync/ElectricSQL with E2E encryption layered on top, or rolling our own encrypted blob sync. *To be decided in `local-first-sync.md`. This is a real architectural decision that should probably become its own ADR.*
- **How do we handle the case where a user signs in on a second device and the local data is out of sync?** Conflict resolution policy needed. *To be defined in `local-first-sync.md`.*

### Alpha logistics

- **Who exactly are the first 15-25 alpha users?** This is a recruitment question, not a build question, but it should be answered before the build is complete because it shapes what hard cases the engine must handle. *Founder action item.*
- **How are alpha users supported when something breaks?** WhatsApp group? Direct text message? Email? The support channel needs to be defined and tested before the first APK ships. *Founder action item.*
- **Do alpha users sign anything?** A short, plain-language agreement about what data is collected (event logs, only when exported by them), what is not (no telemetry), and what we're asking of them (use Cuizine as primary meal-planning tool, talk to founder when things break or work). Not a legal contract — a trust document. *Founder action item.*

### Things parked from earlier conversations

- **Curated expert-authored template library** (registered dietitians or religious scholars publishing reviewed starter profiles users can adopt). Discussed in ADR 0004's alternatives. Parked here for v3 consideration.
- **Configurable agent personas in v3.** ADR 0006 mentions this; the specific design (how many personas, how the user picks one, whether per-agent or app-wide) is not yet decided.
- **The exact pricing of the Cuizine and Cuizine Family tiers** in v2. Locked in shape (monthly with annual option, three tiers) by ADR 0007; specific dollar amounts deferred to v2 planning.

## 9. Cross-references

This PRD is part of a larger document set. The full set, now complete:

### Foundation documents

- **`vision.md`** — the constitution. Read first. When this PRD and the vision conflict, the vision wins.
- **`PRD.md`** — this document. Translates the vision into v1 requirements.
- **`technical-architecture.md`** — high-level system architecture: the six subsystems, trust boundaries, the dependency list, and the v2/v3 deltas.
- **`constraint-engine-spec.md`** — the schema and behavior of the constraint engine. The single most important doc after this PRD.
- **`agent-architecture.md`** — the full agent topology, prompts, tools, and routing.
- **`data-model.md`** — all persistent data structures, including profile ownership and the v3-upgrade-path data carry-forward.
- **`local-first-sync.md`** — the encryption and sync architecture.
- **`ui-ux-spec.md`** — the design language, navigation model, screen inventory, component library, and the UI-first build approach.

### Decision records (ADRs)

- **ADR 0001** — Canadian-first alpha, North American v2 *(amended by ADR 0015: v3 expansion is geographic, not cross-platform)*
- **ADR 0002** — Cultural fluency as the fifth pillar
- **ADR 0003** — Instacart as the sole grocery integration
- **ADR 0004** — Multi-profile households: structure, ownership, and dependent-flow variants
- **ADR 0005** — Flutter as the cross-platform framework *(superseded by ADR 0016)*
- **ADR 0006** — Multi-provider per-agent routing with automatic model escalation
- **ADR 0007** — Three-tier subscription with truly free local-first base tier
- **ADR 0008** — BYOK deferred to v3
- **ADR 0009** — Constraint conflict resolution policy
- **ADR 0010** — Post-generation constraint validation
- **ADR 0011** — Optional backend with capability tiers (Firebase platform, client-side encryption)
- **ADR 0012** — Food data sources and taxonomy strategy
- **ADR 0015** — Android-only scope (no iOS or other platforms)
- **ADR 0016** — Native Kotlin + Jetpack Compose + Orbit MVI + Room *(supersedes ADR 0005)*

### Operational documents

- **`roadmap.md`** — the completion-driven v1→v2→v3 plan and the seven-phase, UI-first v1 build sequence.
- **`build-conventions.md`** — code style, folder structure, Kotlin/Compose/Orbit MVI patterns, the agent-asks-vs-agent-decides rules.
- **`testing-strategy.md`** — the risk-tiered coverage model and the "hard cases" test suite for the constraint engine.
- **`security-and-privacy.md`** — threat model and trust commitments.
- **`alpha-feedback-and-iteration.md`** — the lean alpha feedback playbook.
- **`launch-readiness-checklist.md`** — the binary go/no-go gates for each milestone.
- **`prompts/`** — per-agent system prompts as versioned files (authored during the Phase 5 agent build).

### Living documents

- **`decisions/`** — the ADR folder. Active.
- **`open-questions.md`** — the consolidated open-questions inventory across all domains.
- **`glossary.md`** — domain terms (constraint scope, active fast, pantry confidence, etc.).

### How this PRD is meant to be used

This PRD is the second-most-read document by the coding agent (after `vision.md`). It is the answer to "what are we actually building, for whom, and what's in or out." The agent will reference Sukhi specifically when making UI judgment calls, and will reference Section 5 (feature scope) and Section 6 (out of scope) when deciding whether to build something. The acceptance criteria in Section 7 are the agent's "definition of done" for v1.

When the agent encounters a question this PRD does not answer, the discipline is: (1) check the vision, (2) check the relevant ADR, (3) check the architecture docs, and (4) ask the founder before guessing. The PRD should not be silently extrapolated.

---

*End of PRD v1 (initial draft). Next revision will incorporate alpha learnings and may significantly evolve Section 7's acceptance criteria, Section 8's open questions, and the persona section as Sukhi contacts reality. The vision and the ADRs are more stable than this document; the PRD is expected to learn.*
