# Cuizine — Alpha Feedback and Iteration

> The deliberately lean operational playbook for the v1 alpha period. Per `roadmap.md` Section 4, the alpha is a learning mechanism, not a destination, and every piece of infrastructure we add to the alpha process is friction we have to push through before v2 can start. This doc specifies the minimum discipline needed to capture learning from alpha users and translate it into foundation updates, prompt refinements, and code improvements — and it specifies nothing beyond that minimum.

## 1. Purpose & how to read this doc

This document specifies the **operational playbook for the v1 alpha period** — from the moment the first alpha user installs the APK to the moment the alpha exit criteria from `roadmap.md` Section 4 are met and the alpha-to-v2 transition begins. It assumes you have read all previous foundation docs, the ADRs, and especially `roadmap.md` Section 4 (which specifies what the alpha is for, what it is not for, the exit criteria, and the discipline against lingering).

This document defines: the feedback capture mechanism; the founder review cadence; how feedback flows into prompt iterations, foundation doc updates, and code changes; the event log review process; the alpha user communication discipline; and what is deliberately not part of the alpha process.

This document does **not** define: the alpha exit criteria (those live in `roadmap.md` Section 4); the test suite that validates the constraint engine (that lives in `testing-strategy.md`); the prompt versioning structure (that lives in `build-conventions.md` Section 8); the agent decision protocol (that lives in `build-conventions.md` Section 6); or the alpha kickoff readiness criteria (those will live in `launch-readiness-checklist.md`).

**When this document and a deeper-dive doc disagree,** `roadmap.md` wins for alpha scope and exit criteria. `build-conventions.md` wins for prompt iteration mechanics and the agent decision protocol. `testing-strategy.md` wins for what and how to test. This doc wins for the operational rhythm of the alpha period.

## 2. The alpha's three learning goals (restated for operational context)

Per `roadmap.md` Section 4, the alpha has three explicit learning goals. Every operational decision in this playbook serves one or more of these goals. If an operational activity doesn't serve any of them, it is overhead to be eliminated.

1. **Validating the constraint engine's correctness in real use.** The hard cases test suite exercises the engine against authored scenarios; the alpha exercises it against the unpredictable variety of real dietary lives.

2. **Validating the calm-precise-warm tone against real users.** The tone from `vision.md` is a design target that becomes real only when users encounter it. Alpha is when the tone meets reality.

3. **Surfacing gaps in the foundation we didn't anticipate.** Every structured plan this large has blind spots. Alpha users find them.

## 3. Feedback capture mechanism

> The two channels through which alpha feedback reaches the founder. No additional channels are built for alpha. The discipline: keep the infrastructure smaller than the learning it produces.

### Channel 1: Event log exports

Per PRD § 5, alpha users can initiate an event log export through Settings → Share feedback with Cuizine founder. The export is generated client-side and shared via the platform's share sheet (email, messaging, etc.) to the founder.

The event log contains structured records per `data-model.md` Section 7: constraint operations with timestamps and provenance, suggestion outputs with validation metadata, contextual state changes, rejection events with reasons, and operational events (sync, billing state changes). The log does not contain the user's free-text conversation input verbatim — it contains the structured output of the agents' interpretation of that input, which is what the founder reviews.

The founder receives these exports and reads them manually. There is no dashboard, no analytics pipeline, no automated processing. The alpha population is small enough (15-25 users per ADR 0001) that manual reading is sufficient and more informative than any automated analysis could be.

### Channel 2: Direct conversation

The founder has ongoing contact with each alpha user through whatever communication channel is natural for the relationship (email, messaging, in-person). The founder asks informally about their experience — "how has Cuizine been working for you?" "anything surprising?" "anything frustrating?" — and listens.

Direct conversation produces qualitative insight that event logs cannot: "the suggestions are fine but they feel a bit clinical," "I wasn't sure what to do when it asked me about my blood sugar," "my daughter tried it and got confused by the constraint conversation." These are the signals that inform tone refinement and UX adjustment.

There is no structured survey, no interview protocol, no user research framework. The conversations are informal and relational, matching the alpha's scale and the founder's personal relationship with each user.

### What is NOT a feedback channel

- **In-app feedback buttons or forms.** Not built for alpha. The event log export and direct conversation are sufficient.
- **App Store reviews.** Cuizine is not in the Play Store during alpha; it's distributed via APK.
- **Social media monitoring.** Cuizine has no public presence during alpha.
- **Analytics or telemetry dashboards.** Not built. Per ADR 0011 and the forbidden behaviors lists, content telemetry is forbidden; operational telemetry exists but is not processed through dashboards during alpha.
- **Crash reporting services (Crashlytics, Sentry, etc.).** Per `technical-architecture.md` Section 6, crash reporting is not in v1. The alpha population is small enough that crashes are reported through direct conversation.

## 4. Founder review cadence

> The rhythm with which the founder reviews alpha feedback and makes decisions about what to change. The cadence is driven by feedback volume, not calendar obligation. The founder sets the rhythm based on what the alpha is producing.

### The regular review

On each review cycle (the rhythm driven by feedback volume, not calendar), the founder:

1. **Reads any new event log exports received since the last review.** Prioritizes severity-zero and severity-one events (medical/religious constraint violations, safety-floor bypasses). Any severity-zero event triggers immediate investigation regardless of the review cadence.

2. **Reviews direct conversation notes.** Identifies patterns — are multiple users reporting similar tone issues? Are constraint types being used in ways the foundation didn't anticipate? Are users confused by the same UX affordance?

3. **Categorizes the findings into three buckets:**
   - **Prompt refinements** — issues addressable by changing agent prompts without code changes
   - **Foundation doc updates** — discoveries that reveal a gap or error in the foundation specification
   - **Code fixes or improvements** — bugs, UX issues, or small feature adjustments

4. **Prioritizes the changes** within each bucket. Safety-floor issues first, tone issues second, usability issues third, nice-to-haves last.

5. **Makes the changes** (or directs the coding agent to make them), with the discipline from `build-conventions.md` governing how changes flow through tests and commits.

### Severity-zero event handling

A severity-zero event — a medical or religious constraint violation that reached the user — is the worst failure mode Cuizine can produce. If a severity-zero event appears in any event log export or is reported through direct conversation:

1. **Immediate investigation.** The founder interrupts normal work and investigates immediately, regardless of the regular review cycle.
2. **Root cause identification.** Was it a validator bug, a prompt failure, a food data gap, a constraint graph error, or something else?
3. **Fix deployed.** The fix is committed, tested, and a new APK is distributed to alpha users as soon as possible.
4. **Foundation update.** If the root cause reveals a gap in any foundation doc, the doc is updated per `build-conventions.md` Section 7.
5. **Test case added.** A new test case is added to the hard cases test suite from `testing-strategy.md` Section 5 that exercises the specific failure mode, ensuring it never recurs.
6. **Alpha user communication.** The affected user is informed honestly about what happened and what was done about it, per `security-and-privacy.md` Principle 6.

The standard for severity-zero is zero tolerance. One severity-zero event during alpha is an urgent learning opportunity. Repeated severity-zero events during alpha are a signal that the alpha exit criteria cannot be met without deeper foundation work.

## 5. How feedback flows into changes

> The three output types from the founder review and how each flows through the build discipline.

### Prompt refinements

When the founder identifies a prompt issue (the Chef is being too clinical, the Curator is mis-classifying a constraint's severity, the Pantry agent is being too verbose), the flow is:

1. The founder identifies the specific prompt file in `prompts/` that needs to change.
2. The founder drafts the change (or directs the agent to draft it).
3. The change is tested against the relevant eval harness scenarios from `testing-strategy.md` Section 6 — the automated set for safety-floor behaviors, manual review for subjective quality.
4. If the eval passes, the prompt's version number is bumped and the change history is updated per `build-conventions.md` Section 8.
5. The change is committed with a descriptive message.
6. The next APK distribution to alpha users includes the updated prompt.

Prompt refinements are the most frequent output of the alpha review cycle. They should feel lightweight, not ceremonial.

### Foundation doc updates

When the founder discovers a gap or error in a foundation doc (the constraint engine spec didn't anticipate a scope interaction that alpha users triggered, the data model's schema needs a new field, the security doc's threat model needs a new adversary), the flow is:

1. The founder identifies the specific doc and section that needs to change.
2. The founder decides: is this a minor clarification (update the doc inline) or a real architectural decision (write an ADR per `build-conventions.md` Section 9)?
3. The update is drafted, reviewed against downstream docs for consistency, and committed with a descriptive message noting what changed and why.
4. Any code that depends on the updated spec is updated to match per `build-conventions.md` Section 7's bidirectional update discipline.

Foundation doc updates are less frequent than prompt refinements but more consequential. They should feel deliberate, not rushed.

### Code fixes and improvements

When the founder identifies a bug or a small improvement (a UX affordance that confuses users, a performance regression, a sync edge case that wasn't caught by tests), the flow is:

1. The issue is diagnosed.
2. The fix is implemented with tests per `build-conventions.md` Section 10's test bar.
3. The fix is committed with a descriptive message.
4. If the fix reveals a gap in the test suite, a new test is added.
5. The next APK distribution to alpha users includes the fix.

Code fixes are the most mechanical output of the alpha review cycle. They follow the normal build discipline without additional ceremony.

## 6. Alpha user communication

> How the founder communicates with alpha users during the alpha period. The discipline is calm, honest, and personal — matching the product's own voice.

### The communication tone

Alpha communication uses the same calm-precise-warm voice as the product itself. Alpha users are not beta testers being managed; they are early users being listened to. The distinction matters: "we fixed a bug you reported" is fine; "thanks for your bug report, it has been logged in our tracking system" is not. The communication is personal because the relationship is personal.

### What the founder communicates proactively

- **APK updates.** When a new version is available, the founder sends a brief message: "New version of Cuizine is ready — here's the APK link. Main changes: [2-3 bullet points]. Let me know how it goes."
- **Severity-zero fixes.** When a severity-zero event is fixed, the affected user is informed honestly: "I found an issue where Cuizine suggested [X] which conflicts with your [constraint]. I've fixed the underlying problem and added a test to prevent it from happening again. Here's the updated version."
- **Alpha exit communication.** When the alpha exit criteria are met, the founder communicates to all alpha users: "Cuizine is getting ready for public launch. Here's what that means for you: [brief explanation of the alpha-to-v2 transition from `roadmap.md` Section 5]."

### What the founder does NOT communicate proactively

- **Internal roadmap decisions.** Alpha users don't need to know about the v2 launch plan or v3 expansion.
- **Foundation doc updates.** Alpha users don't need to know about internal documentation changes.
- **Prompt version bumps.** Alpha users don't need to know that the Chef's prompt was tweaked — they just experience the improvement.
- **Technical architecture decisions.** Alpha users are users, not collaborators on the architecture.

### How the founder responds to alpha user feedback

When an alpha user reports something — through event log export, through direct conversation, or through unsolicited messaging — the founder:

1. **Acknowledges promptly.** Even if just "got it, looking into this."
2. **Investigates.** Reviews the event log, reproduces the issue if possible, identifies the root cause.
3. **Responds with the result.** Either "this was a real issue and I've fixed it — here's the updated APK" or "I looked into this and here's what I found — [explanation]."
4. **Does not over-explain.** Alpha users want to know their feedback was heard and acted on; they don't want a technical lecture.

## 7. The alpha-to-v2 transition communication

> When the alpha exit criteria from `roadmap.md` Section 4 are met, the alpha period ends. This section specifies how the transition is communicated to alpha users.

### The transition message

The founder sends a message to all alpha users:

"Thanks for being part of the Cuizine alpha. Your feedback shaped the product in ways I couldn't have anticipated, and I'm grateful. Cuizine is now getting ready for public launch on the Play Store.

Here's what that means for you: when the public version launches, you'll be moved from the alpha to the free tier. Everything you've created — your constraints, your recipes, your history — stays intact. If you'd like to keep using the paid features you've had during alpha, you can start a 14-day trial at no cost, and from there decide if the subscription is right for you.

I'll send you the update when it's ready. In the meantime, Cuizine keeps working exactly as it has been."

This message matches the v2 launch transition from `monetization-and-billing.md` Section 9 and the alpha exit communication from `roadmap.md` Section 4.

### Timing

The transition message is sent when v2 launch is imminent — close to but before the Play Store listing goes live. It is not sent the moment the alpha exit criteria are met (there's transition work to do first per `roadmap.md` Section 5). Alpha users continue using the alpha version during the transition period.

## 8. What is deliberately not part of the alpha process

> The explicit list of things Cuizine does NOT do during alpha, parallel to the "what is not tested" and "what is forbidden" sections in other docs. Each item is a thing that might be tempting to add, with the reason why we don't.

- **Analytics dashboards.** The alpha population is too small for statistical analysis. Manual event log review is more informative.
- **Structured user surveys.** The alpha population is too small for survey methodology to add value over direct conversation.
- **User research sessions (formal interviews, usability studies).** The alpha is relational, not research-based. Formal research is a v2+ capability if needed.
- **A/B testing of prompts or features.** Per `build-conventions.md` Section 8, the alpha population is too small for meaningful A/B comparison.
- **Community channels (Discord, Slack, forums).** The alpha is not a community; it's a small group of users with direct access to the founder.
- **Public changelog or release notes.** Updates are communicated personally to alpha users, not published.
- **Automated feedback processing.** Event logs are read by the founder, not processed by software.
- **User onboarding beyond the product itself.** Alpha users install the APK and use Cuizine. There is no separate onboarding deck, no tutorial video, no getting-started guide. If the product needs external explanation to be usable, that's a UX issue to fix, not a documentation gap to fill.
- **Metrics or KPIs for the alpha.** The exit criteria from `roadmap.md` Section 4 are the success measure. There are no intermediate metrics like "daily active users" or "retention rate" because the alpha population is too small for those metrics to be meaningful.
- **Formal bug tracking.** Issues are tracked through the founder's review cycle and committed fixes. There is no Jira, no Linear, no GitHub Issues for alpha. The working task tracking happens within the coding agent's own task management, not in a separate tool.

## 9. Open questions

- **Should the founder keep a lightweight "alpha learning log" — a simple Markdown file listing what was learned each week?** Useful for the foundation doc retrospective during the alpha-to-v2 transition. Low cost. Tentatively yes, in `docs/alpha-log.md` or equivalent. Not a formal document; just a running list.
- **What happens if an alpha user stops using Cuizine without explanation?** Probably nothing formal — the founder might check in informally, but alpha users are volunteers and are not obligated to continue. If multiple users disengage, that's a signal worth investigating.
- **Should alpha users be offered the option to continue as unpaid users after v2 launch, or should they be treated identically to new v2 users?** Per `monetization-and-billing.md` Section 9, they're treated identically — moved to free tier with trial eligibility. No special permanent access. This is the honest approach.
- **How are APK updates distributed?** Probably direct link via messaging or email. No over-the-air update infrastructure for alpha. The user installs the new APK manually. Simple, low-overhead, matches the alpha's scale.

## 10. Cross-references

### What this document references

- `vision.md` — for the calm-precise-warm voice that alpha communication uses
- `PRD.md` — for the event log export flow and the alpha context
- `data-model.md` — for the event log schema that structures the feedback data
- `monetization-and-billing.md` — for the alpha bypass and the v2 transition
- `build-conventions.md` — for the prompt iteration mechanics, the agent decision protocol, and the bidirectional update discipline
- `testing-strategy.md` — for the hard cases test suite and the agent eval harness
- `security-and-privacy.md` — for the honest communication principle and the severity-zero response
- `roadmap.md` — for the alpha's three learning goals, the exit criteria, and the discipline against lingering
- ADR 0001 — for the alpha population size (15-25 users)
- ADR 0011 — for the content telemetry prohibition

### What this document defers to deeper-dive docs

- **`roadmap.md`** Section 4 — the authoritative source for alpha exit criteria
- **`launch-readiness-checklist.md`** — the go/no-go gates for alpha kickoff
- **`build-conventions.md`** Section 7 — the bidirectional update discipline for foundation doc changes
- **`testing-strategy.md`** Section 5 — the hard cases test suite that grows from alpha learnings

### What this document does *not* defer (decisions made here)

- The two-channel feedback capture mechanism (event log exports + direct conversation) with nothing else
- The regular founder review cycle with severity-zero immediate handling
- The three output types (prompt refinements, foundation doc updates, code fixes) and their flow
- The alpha user communication discipline (calm, honest, personal, no over-explaining)
- The alpha-to-v2 transition communication with specific message framing
- The 10-item "what is deliberately not part of the alpha process" list

### How the agent should use this doc

During the v1 alpha period, this doc specifies the operational rhythm the founder follows. The agent's role during alpha is to implement the changes the founder identifies through the review cycle — prompt refinements, foundation doc updates, code fixes — following the build discipline from `build-conventions.md`. The agent does not independently decide to add alpha infrastructure (dashboards, surveys, analytics) that this doc does not specify. If the agent encounters a situation that seems to call for infrastructure not named here, it surfaces the question per `build-conventions.md` Section 6 rather than building it.

The agent should pay particular attention to severity-zero events: when one is identified, the agent's highest priority is implementing the fix, adding the test case, and updating any foundation doc that was revealed to have a gap. Everything else queues behind severity-zero response.

When the alpha exit criteria from `roadmap.md` Section 4 are met and the founder initiates the alpha-to-v2 transition, this doc's operational rhythm ends and the transition streams from `roadmap.md` Section 5 begin.

---

*End of `alpha-feedback-and-iteration.md` v1 (initial draft). This is deliberately the shortest foundation doc in the set. If it grows significantly during alpha, that growth is a signal that the alpha process is acquiring more complexity than it should have. The discipline is to keep the playbook smaller than the learning it produces.*
