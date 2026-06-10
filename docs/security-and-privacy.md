# Cuizine — Security and Privacy

> The doc where Cuizine's trust commitments meet adversarial reality. Where `local-first-sync.md` specified the implementation of encryption and recovery, this document specifies *who could attack it, how, and what Cuizine's response is*. It enumerates threat actors, attack surfaces, residual risks Cuizine acknowledges honestly, the responsible disclosure policy, the regulatory compliance posture across v1/v2/v3 jurisdictions, and the v3 cross-account peer coordination security model. Without this doc, Cuizine's trust commitments are aspirational; with this doc, they are accountable.

## 1. Purpose & how to read this doc

This document specifies the **security and privacy posture for Cuizine across v1, v2, and v3**, with v1 alpha applying the framework at small scale and v2/v3 expanding it as the user base grows and crosses regulatory boundaries. It assumes you have read all previous foundation docs and the ADRs.

This document defines: the core security and privacy principles; the threat model with explicit adversaries ranked by likelihood and impact; the attack surfaces by architectural layer; the residual risks Cuizine acknowledges honestly; the responsible disclosure policy with the v1 founder-only review and the v2 formal program; the regulatory compliance posture covering PIPEDA (Canada v1), the explicitly-not-HIPAA stance for v2 US launch, and GDPR for v3 global; the v3 cross-account peer coordination security model; the data subject rights (access, correction, deletion, portability) and how each is operationally fulfilled; the security review and incident response process; the explicit list of commitments Cuizine makes and the explicit list of attacks Cuizine cannot defend against; and the open questions this spec acknowledges.

This document does **not** define: legal terms of service or privacy policy text (those are separate legal documents written with counsel using this doc as input); penetration test reports or audit reports (operational artifacts that come later); the actual encryption library or sync protocol (those are in `local-first-sync.md`); or product copy for the in-app disclaimers (that lives in the prompts/copy directory).

**When this document and a deeper-dive doc disagree,** this doc wins for security posture, threat modeling, and regulatory framing. `local-first-sync.md` wins for cryptographic implementation. `monetization-and-billing.md` wins for billing-specific privacy. The legal documents (ToS, privacy policy) win for legally binding language but must be consistent with this doc's posture.

## 2. Core security and privacy principles

> The non-negotiable commitments this layer is designed against. Every security decision in this doc traces back to one of these principles. They are also the commitments Cuizine makes to users, in the form most stripped of marketing language.

### Principle 1: Cuizine is honest about what it can and cannot protect

Cuizine names the things it can defend against (most adversaries trying to read user data through Cuizine's infrastructure) and names the things it cannot defend against (a user's device being compromised, an upstream LLM provider being compelled to disclose during a request, a sophisticated attacker who somehow obtains both the user's account credentials and recovery passphrase). The honest version of "we protect your data" is "we protect your data against these specific threats with these specific measures, and here are the threats we cannot fully defend against." This honesty is the foundation of trust. Vague claims age badly; specific claims with named limitations earn lasting trust.

### Principle 2: Cuizine is not a HIPAA-covered entity

Cuizine is a consumer wellness tool, not a medical device. Cuizine does not partner with healthcare providers in ways that would create a covered relationship. Cuizine does not enter into Business Associate Agreements. Cuizine does not market itself as a medical device or as clinical-grade. Throughout the product, Cuizine clearly disclaims that it is not medical advice and that users should work with their healthcare providers for medical decisions.

This is a deliberate choice, not a regulatory necessity. HIPAA does not automatically apply to direct-to-consumer wellness apps; pursuing HIPAA compliance is opting in to a framework, not being subject to one. Cuizine has chosen not to opt in because the operational and engineering cost of compliance would slow v2 launch substantially while not meaningfully improving user safety beyond what the architectural commitments already provide. The trust posture from `vision.md`, the local-first encryption from `local-first-sync.md`, and the deterministic validator from ADR 0010 already exceed HIPAA's floor on most dimensions.

The explicit disclaimer language that appears in the product:

- During the constraint conversation: "Cuizine is a food companion, not a medical advisor. Anything you tell me about your health helps me suggest meals that work for you, but please work with your doctor, dietitian, or other healthcare professional for medical decisions."
- In the Settings → About screen: "Cuizine is not medical advice. The constraints you set tell me what to suggest, but I am not a substitute for professional medical guidance. Please share Cuizine's suggestions with your healthcare team if you have specific medical needs."
- In the recovery passphrase reveal screen, when the user is told their data is encrypted: "Cuizine encrypts your data so even we cannot read it. We are not a HIPAA-covered entity, and Cuizine is not designed for clinical use, but we apply strong privacy protections by default."

### Principle 3: Privacy is not a paid feature

Per `monetization-and-billing.md` Principle 4 and Section 3, every tier of Cuizine gets the same privacy posture, the same encryption, the same data control, the same export rights, and the same protections in this document. Free-tier users are not second-class with respect to privacy. The principle is named here too because the security posture must explicitly enforce it — there is no quiet exception where paid users get a stronger threat model than free users.

### Principle 4: User control is the foundation of consent

Every piece of user data in Cuizine exists because the user chose to put it there, and the user retains the right to read, modify, export, or delete it at any time without conditions. The trust posture rests on this user control being real, not nominal. The data subject rights specified in Section 9 are the operational mechanisms that make user control real, and they apply to every user on every tier in every jurisdiction.

### Principle 5: Cuizine does not weaponize user data against users

Cuizine never uses user data to manipulate, deceive, pressure, or harm the user. Cuizine never builds psychological profiles to optimize against the user's interests. Cuizine never sells, rents, licenses, or otherwise transfers user data to third parties. Cuizine never aggregates user data across users for any purpose other than operational telemetry that contains no user content. The data the user puts into Cuizine serves the user, period. This is the principle that distinguishes Cuizine from products that treat user data as a resource to extract.

### Principle 6: Threats and incidents are surfaced honestly

When Cuizine encounters a security incident — a vulnerability discovered, a data exposure suspected, a sync failure that left data in an unexpected state — Cuizine's response is to inform affected users honestly and promptly. Cuizine does not minimize, hide, or delay incident communication. The discipline is "tell users what happened, what we know, what we don't know, what we're doing about it, and what they should do." The cost of honest communication is reputational risk in the moment; the cost of dishonest communication is lasting trust damage that cannot be repaired.

## 3. The threat model

> Who Cuizine is defending against. The threat model enumerates adversaries explicitly, ranked by likelihood and impact, so that defenses can be calibrated to real risks rather than to vague "security." Threats not in this model are either not realistic for Cuizine's scale and architecture, or are acknowledged in Section 5 as residual risks Cuizine cannot defend against.

### Adversary 1: Casual opportunists

**Who they are.** Someone who finds a logged-out phone on a coffee shop table and tries to access it. A family member who wants to see what someone has been doing on their phone. A coworker who borrows a device for a moment. Low-skill, high-frequency, no specific target — they're opportunistic rather than persistent.

**Likelihood:** High. Realistic threat that affects nearly all users.

**Impact:** Medium. They can see what's currently on screen and possibly navigate the app, but the at-rest encryption from `data-model.md` Section 9 Layer 1 protects the database file from offline extraction.

**Cuizine's defenses:**
- The Android Keystore at-rest encryption ensures the database file cannot be read without unlocking the device.
- The lock-screen of the device itself is the primary defense — Cuizine does not implement its own lock screen because the OS-level lock is more robust and more usable.
- Sensitive content (constraint values, suggestion content) is not displayed on the lock screen via notifications, because Cuizine does not send notifications.
- The clipboard contents from the recovery passphrase reveal screen are flagged as sensitive on Android so the OS handles masking.

**Acceptable residual risk:** A user who unlocks their phone in front of a casual opportunist exposes whatever the app is currently displaying. This is acceptable because the alternative (forcing a re-authentication for every app open) would be unusable.

### Adversary 2: Determined personal adversaries

**Who they are.** A controlling partner trying to monitor a user's eating, religious observance, or health status. A family member with sustained access to the device who wants to read the user's data over time. A stalker who has obtained physical access to a victim's phone. Higher skill, lower frequency, specific target — they're persistent against a particular user.

**Likelihood:** Medium. Real threat that affects some users substantially.

**Impact:** High. If successful, they can read the user's full constraint graph, suggestion history, and possibly the user's conversation patterns — which can reveal religious practice, medical conditions, household composition, and identity in ways that have real safety consequences for the user.

**Cuizine's defenses:**
- The at-rest encryption protects the database file, but a determined adversary with the unlocked device can read the displayed content.
- The recovery passphrase from `local-first-sync.md` Section 4 is the secondary protection: even if the adversary steals the user's phone and the device is somehow unlocked, they cannot install Cuizine on a different device and access the user's encrypted blob without the recovery passphrase.
- The signed-out mode option from ADR 0011 means a user in a high-risk situation can use Cuizine without ever creating a Firestore document, eliminating the cross-device attack surface entirely.
- The export-and-walk-away flow from `vision.md` lets a user leave Cuizine entirely with their data, so they are not held in a hostile data relationship.

**Acceptable residual risk:** A user whose device is in the persistent control of an adversary cannot be fully protected by Cuizine's architecture. The architecture mitigates several attack vectors, but the fundamental problem of "I do not have private use of my own device" is outside Cuizine's scope. Cuizine does not pretend to solve this.

### Adversary 3: Network attackers

**Who they are.** Someone on the same Wi-Fi network as the user, an ISP that monitors traffic, a state actor that intercepts cellular data, an attacker who has compromised network infrastructure between the device and Firebase. Low-to-medium skill (because off-the-shelf tools handle most network attacks), variable frequency, no specific target initially.

**Likelihood:** Medium. Realistic for users on public Wi-Fi or in surveillance-heavy network environments.

**Impact:** Low against Cuizine specifically. Network attackers see TLS-encrypted traffic to Firebase (which they cannot read) and TLS-encrypted traffic to LLM providers (which they cannot read). The contents of the encrypted sync container are double-encrypted (TLS at the transport layer plus AES-256-GCM at the application layer), so a network attacker who somehow defeated TLS would still face the application-layer encryption.

**Cuizine's defenses:**
- All Firebase traffic uses TLS 1.2+ as enforced by the Firebase SDKs.
- All LLM provider traffic uses TLS 1.2+ as enforced by the provider SDKs.
- The application-layer AES-256-GCM encryption from `local-first-sync.md` Section 3 is applied before data leaves the device for the encrypted sync container, so even a TLS compromise does not expose user content.
- Cuizine does not cache or store TLS session keys persistently.

**Acceptable residual risk:** A network attacker who compromises both TLS (which would require defeating modern PKI) and the application-layer encryption (which would require defeating AES-256-GCM with a 256-bit key) is a sophisticated state-level adversary that is beyond Cuizine's threat model. We do not pretend to defend against this case.

### Adversary 4: Cuizine-the-company itself (insider threat)

**Who they are.** A current or former Cuizine employee who wants to read user data for personal curiosity, malice, or external pressure. A founder who succumbs to a "support team needs to help users" rationalization for adding a backdoor. A future Cuizine acquired by a larger company that wants to monetize user data.

**Likelihood:** Low currently (single-founder, all employees trustworthy). Higher in v3+ as the company grows.

**Impact:** Catastrophic if successful. Every user's data would be at risk simultaneously.

**Cuizine's defenses:**
- The architectural commitment from `vision.md` and `local-first-sync.md` Principle 1: Cuizine cannot read user content because the encryption key is derived from material Cuizine does not hold. There is no master key, no escrow, no admin override.
- The forbidden behaviors lists in `local-first-sync.md` Section 10 and `monetization-and-billing.md` Section 11 explicitly forbid implementing any backdoor, escrow mechanism, or "support recovery" flow.
- The bidirectional update discipline from `build-conventions.md` Section 7 means that any change that would weaken the trust posture requires a new ADR and explicit founder decision.
- The principles in this doc commit Cuizine the company to never building the infrastructure that would enable insider access.

**Acceptable residual risk:** A future Cuizine that decides to abandon its principles cannot be prevented by architectural means alone. The trust posture relies on a continued institutional commitment. The architectural commitments make abandonment expensive (it would require a major reengineering effort) but not impossible. The honest framing: Cuizine commits to never building the infrastructure that would enable insider access, and users should evaluate Cuizine's behavior over time to see whether that commitment is honored.

### Adversary 5: Upstream LLM providers (Anthropic, Google, OpenAI)

**Who they are.** The companies whose models Cuizine routes inference through. They see inference content during the API call itself, even though they have data retention commitments and zero-data-retention options where available.

**Likelihood:** Continuous (every signed-in inference request goes through them).

**Impact:** Variable. The providers' commitments are credible but not absolute — they could be compelled by legal process to disclose, or could change their retention policies, or could experience a data breach.

**Cuizine's defenses:**
- Per ADR 0006, Cuizine uses providers' zero-data-retention options where available (Anthropic API has explicit ZDR; Google and OpenAI offer equivalents).
- Inference requests are scoped to the specific task — they include the active constraint set and the current request, not the user's full history or identity.
- The Cloud Function proxy from ADR 0011 forwards but does not log inference content; the moment of forwarding is the only window where Cuizine's infrastructure touches the request.
- The v3 BYOK option from ADR 0008 lets users bypass Cuizine's routing entirely and use their own provider keys, eliminating Cuizine's involvement in inference for those users.
- The signed-out mode from ADR 0011 routes inference directly from the device to the providers (not through Cuizine's Cloud Function), but this is the same trust commitment to the providers themselves.

**Acceptable residual risk:** Users who care most about minimizing upstream provider visibility have two paths: the v3 BYOK option for the strongest possible stance, or the signed-out mode for the strongest possible stance with Cuizine. Users who prefer convenience accept the named risk of upstream provider visibility during inference. Cuizine names this risk explicitly throughout the product so users can choose with full information.

### Adversary 6: Sophisticated attackers (state-level, organized crime)

**Who they are.** Adversaries with substantial resources who target Cuizine specifically because they believe its user data is valuable. State actors investigating dietary patterns as proxies for religious or cultural identity. Organized crime targeting dietary information for extortion or fraud. Sophisticated researchers who want to break Cuizine's encryption to publish.

**Likelihood:** Very low at v1/v2 scale. Possibly elevated for specific users in specific contexts (journalists in surveillance regimes, activists, etc.).

**Impact:** Variable depending on success. Most realistic outcome is partial information disclosure for specific targeted users; full systemic compromise is unlikely against the architecture.

**Cuizine's defenses:**
- The architectural commitments and the encryption layer make systemic compromise expensive and slow.
- The lack of a master key or escrow means there is no "single key to compromise" — every user's data would need to be compromised individually.
- The local-first architecture means an attack on Cuizine's servers cannot reveal user content.
- The sophisticated attacker has many easier targets than Cuizine; we are not the highest-value target for most actors.

**Acceptable residual risk:** Cuizine cannot defend against a state-level adversary with substantial resources targeting a specific user. The architectural commitments make this very hard, but not impossible. Cuizine's honest position: we are not your tool for evading sophisticated adversaries; we are your tool for thoughtful food companionship with strong privacy as a default.

## 4. Attack surfaces by layer

> For each architectural layer, a structured enumeration of what attacks are possible and what Cuizine's defenses are. The pattern: each layer's surface is bounded by deliberate design choices, and each defense is named explicitly.

### Layer: The user's device (local data)

**Attack vectors:**
- Physical theft of an unlocked device → adversary reads what is currently displayed
- Physical theft of a locked device → adversary attempts to extract the database file
- Malicious app installed on the same device → app reads Cuizine's data through OS-level access
- Backup extraction from device backups (cloud or local)

**Defenses:**
- Android Keystore at-rest encryption protects the database file when the device is locked
- Cuizine's database lives in the app's private data directory, which is not readable by other apps without root access (and root access voids the OS security model entirely)
- Cuizine does not include sensitive data in OS-level backups (per the Android `android:allowBackup="false"` configuration in the manifest)
- The device's own lock screen is the primary defense; Cuizine relies on it rather than implementing its own

### Layer: The encrypted sync container in Firestore

**Attack vectors:**
- Adversary compromises the user's Firebase Auth credentials → attempts to download the encrypted blob
- Adversary compromises Firebase infrastructure → attempts to read all encrypted blobs
- Adversary obtains the encrypted blob and attempts brute-force decryption
- Adversary attempts to corrupt or delete the encrypted blob

**Defenses:**
- The Firebase Auth credentials only let the adversary download the blob, not decrypt it — the recovery passphrase is required for decryption per `local-first-sync.md` Section 4
- AES-256-GCM with Argon2id key derivation makes brute-force decryption computationally infeasible (Argon2id parameters are calibrated to 500ms-1s per attempt, making millions-of-attempts attacks impractical)
- The GCM authentication tag detects any tampering; corrupted blobs cannot decrypt
- Firestore security rules restrict reads and writes to the authenticated user's own document, preventing cross-user attacks
**Acknowledged weakness:** Firebase's own infrastructure security is the responsibility of Google. Cuizine trusts Google's commitments at the infrastructure layer but cannot independently guarantee them; this is named honestly rather than papered over.

### What plaintext metadata the backend necessarily holds

> The trust posture rests on "Cuizine cannot read user content." That claim is precise, not absolute — having an account at all requires the backend to hold a small amount of non-content metadata in plaintext. This subsection enumerates *exactly* what that is and why each field is unavoidable. It is the canonical list: the backend holds these fields and **no others** in plaintext. Anything semantic — constraints, suggestions, history, household composition, cultural context, dietary inferences — lives only inside the encrypted blob and on the device, never here. This list is both a trust artifact (specific claims with named limitations earn lasting trust) and a build guardrail (a reviewer can reject any change that adds a plaintext field not on this list).

**Firebase Auth holds, in plaintext:**
- The account identity used to authenticate — an email address and/or a federated provider identifier (e.g., the Google account ID for Sign in with Google). This is irreducible: authentication means matching a login against a stored identifier, and that identifier cannot itself be encrypted under a key derived from the authentication (the dependency is circular). Every local-first app with optional accounts has this property.
- Standard Firebase Auth record fields (account creation timestamp, last sign-in timestamp). These are operational, not semantic.

**Firestore holds, in plaintext, alongside each user's encrypted blob:**
- `last_uploaded_at` — the timestamp of the most recent sync, needed for the pull/push conflict logic in `local-first-sync.md` Section 5.
- The blob's size (an inherent property of storing bytes) and the document's own metadata (document ID keyed to the user, Firestore's internal timestamps).
- A schema version integer and a wordlist version integer, needed so an older client can recognize a newer blob format and so old passphrases remain validatable (per `local-first-sync.md` Sections 3-4).
- For v3 linked partners only: the link metadata named in Section 8 (when a link was created, how often shared views are written, the sizes of the encrypted views) — explicitly acknowledged there as non-sensitive.

**What this metadata can and cannot reveal.** An insider or a compromise of Firebase can learn *that* a given email has a Cuizine account, *when* they last synced, and *roughly how much* data they have. It cannot learn *anything about what that data is* — not a single constraint, not a condition, not a cuisine, not a household member. The sensitive inferences the encryption exists to protect (dietary patterns as proxies for religion, health, or identity, per Adversary 6 and Risk 3) are never derivable from the plaintext metadata, because no semantic field is ever stored here. The discipline that makes this true is simple and absolute: **nothing semantic is ever added to the plaintext layer for convenience.** The temptation — "it's just a tag, just a count, just a preference, and it would make a query easier" — is the exact failure mode this list exists to prevent.

### Layer: The Cloud Function LLM proxy (signed-in users)

**Attack vectors:**
- Adversary compromises Cuizine's Cloud Function infrastructure → attempts to log or intercept inference requests
- Adversary obtains Cuizine's upstream provider API keys → attempts to make malicious calls
- Adversary intercepts the function's request/response in transit → attempts to read content

**Defenses:**
- The function does not log inference content (per ADR 0011 and the forbidden behaviors lists)
- The function holds upstream provider keys in Firebase's secure environment variables, not in code
- All function traffic uses TLS
- The function has rate limiting to prevent abuse
- The function's role is purely forwarding — it does not store, transform, or process inference content beyond what is necessary to route it

**Acknowledged weakness:** The moment of forwarding is the one window where Cuizine's infrastructure holds inference content in memory. This is named honestly in `local-first-sync.md` Section 5 and acknowledged here. The mitigation is: signed-out users avoid this entirely, BYOK users (v3) avoid this entirely, and signed-in users accept this as a named tradeoff for the convenience of cross-device sync.

### Layer: The food data layer (USDA, Open Food Facts queries)

**Attack vectors:**
- Network adversary observes the queries Cuizine sends to USDA or Open Food Facts → infers the user's dietary patterns from query content
- The external services log queries → Cuizine's queries become part of those services' data
- Adversary compromises the external services → reads query history

**Defenses:**
- Per ADR 0012, the cache-first architecture means external queries decrease over time as the user's vocabulary fills the local cache. After a few weeks of typical use, almost every query is a cache hit and no network call happens.
- Queries are batched and randomized within latency budgets to reduce timing-based inference
- The future v2 option of routing queries through the Cloud Function proxy can hide individual user requests from the external services (deferred to v2 evaluation; not a v1 commitment)

**Acknowledged weakness:** Individual ingredient queries to USDA/OFF are not encrypted at the application layer — they're just HTTPS requests with the ingredient name in them. A determined adversary observing those requests could infer cultural or dietary patterns. This is named in `local-first-sync.md` Section 5's privacy implications and acknowledged here.

### Layer: The agent layer (LLM-backed reasoning)

**Attack vectors:**
- Prompt injection attacks that manipulate agents into producing harmful outputs
- Adversarial inputs designed to confuse the Curator into mis-classifying severity tiers
- Inputs designed to extract Cuizine's system prompts via prompt leakage
- Inputs designed to circumvent the validator by tricking the Chef into producing structurally-passing-but-semantically-wrong outputs

**Defenses:**
- The deterministic post-generation validator from ADR 0010 is the safety floor — even if an agent is manipulated, the validator catches violations before users see them
- Structured output via constrained generation (per `agent-architecture.md` Section 2) limits the agent's ability to produce arbitrary text that could be misused
- The orchestrator's intent routing is rule-based, not LLM-based, so prompt injection cannot redirect a user's intent to a different agent
- The forbidden behaviors lists prevent agents from being given write access to operations they shouldn't perform
- System prompts are versioned in `prompts/` and changes are reviewed; prompt leakage is acceptable because the prompts themselves contain no secrets

**Acknowledged weakness:** Agents are LLMs and LLMs can be manipulated. The validator catches violations of constraints, but it cannot catch every form of subtle manipulation (e.g., a Chef agent that produces a culturally insensitive suggestion that doesn't violate any explicit constraint). Manual review during alpha is the secondary defense.

### Layer: The billing layer

**Attack vectors:**
- Adversary attempts to bypass tier enforcement by manipulating the local subscription state
- Adversary attempts to trigger refunds or chargebacks fraudulently
- Adversary attempts to use the trial mechanism multiple times by creating multiple accounts
- Adversary attempts to access paid features by exploiting bugs in the `TierPolicy` module

**Defenses:**
- The subscription state is stored in the encrypted sync container, so local manipulation requires defeating the encryption (which is computationally infeasible)
- Google Play handles all payment processing and refund decisions; Cuizine does not have a separate payment surface that could be attacked
- The trial eligibility tracking from `monetization-and-billing.md` Section 5 limits multiple trials per account, with the explicit acknowledgment from Section 4 that Cuizine does not attempt to detect "the same human with two accounts"
- The `TierPolicy` module's centralized enforcement makes bypass via direct code modification require modifying the local app binary, which is detectable through Google Play's app integrity APIs

**Acceptable residual risk:** A determined adversary could create multiple accounts to trial multiple times, or could modify the local app binary to bypass tier checks. Cuizine accepts this risk because the alternative — invasive detection mechanisms — would violate the privacy posture and would harm legitimate users.

## 5. Residual risks acknowledged honestly

> The things Cuizine cannot fully defend against, named explicitly with the reasoning. This section is the honest version of "what we can't protect you from." Each item is a real limitation, not a theoretical one.

### Risk 1: A user's device under hostile control

**The risk:** A user whose device is in the sustained control of an adversary (controlling partner, abusive family member, stalker) cannot be fully protected by Cuizine. The adversary can see what the app displays, can navigate through the app while the user isn't looking, can read the user's history.

**Why Cuizine cannot fully defend:** The fundamental problem is that Cuizine cannot distinguish between a legitimate user and an adversary using the same unlocked device. Any defense (re-authentication, biometric prompts, hidden content) would either be defeatable or would harm legitimate users.

**Mitigations applied:**
- The recovery passphrase prevents the adversary from accessing the user's data on a different device they control
- The signed-out mode minimizes the data footprint that the adversary can access
- The export-and-walk-away flow lets the user leave Cuizine with their data
- Cuizine does not display sensitive content in notifications (because Cuizine does not send notifications)

**Honest acknowledgment:** If a user is in this situation, Cuizine is not a complete solution. We strongly encourage users in such situations to seek support from organizations specialized in helping people leave hostile relationships. Resources like the National Domestic Violence Hotline and equivalent regional services exist for exactly this kind of need.

### Risk 2: Upstream LLM provider visibility during inference

**The risk:** When a signed-in user's request is processed by Cuizine's Cloud Function proxy and forwarded to Anthropic, Google, or OpenAI, the inference content is visible to those providers during the API call. This visibility is bounded by the providers' data retention commitments (Anthropic offers explicit zero-data-retention; Google and OpenAI offer equivalents), but it is not zero.

**Why Cuizine cannot fully defend:** Cuizine's value depends on LLM inference, and LLM inference requires sending content to a provider that can read it. The only way to eliminate this visibility is to run inference entirely on-device, which is technically possible (small models exist) but does not match Cuizine's quality target for the constraint engine and agent layer.

**Mitigations applied:**
- Zero-data-retention is enabled with all providers where available
- Inference requests are scoped narrowly (active constraint set + current request, not full history)
- The Cloud Function proxy does not log inference content
- Signed-out users route inference directly from device to provider, eliminating Cuizine's involvement
- v3 BYOK users supply their own keys, eliminating Cuizine's routing entirely

**Honest acknowledgment:** Users who care most about minimizing upstream provider visibility have the v3 BYOK path or the signed-out mode. Users who prefer convenience accept this as a named tradeoff. Cuizine names this risk explicitly throughout the product so the choice is informed.

### Risk 3: External food data query inference

**The risk:** Queries to USDA or Open Food Facts are HTTPS requests with the ingredient name in plaintext (the request URL). A network adversary or the external service itself could observe these queries and infer cultural or dietary patterns from the queried ingredients. For example, repeated queries for "asafoetida" and "kala chana" suggest South Asian users.

**Why Cuizine cannot fully defend:** Encrypting the queries would require Cuizine to operate its own food data service, which would defeat the purpose of using authoritative external sources. The external services are public services that operate on plaintext queries.

**Mitigations applied:**
- The cache-first architecture from ADR 0012 means external queries decrease over time. After a few weeks of typical use, most queries are cache hits and no network call happens.
- Queries are batched and order-randomized within latency budgets to reduce timing-based inference
- The future v2 option of routing queries through the Cloud Function proxy can hide per-user requests from the external services (deferred)

**Honest acknowledgment:** This is a real privacy implication that Cuizine names in `local-first-sync.md` Section 5 and here. Users whose threat model includes inference from external service logs should be aware that Cuizine cannot fully eliminate this exposure.

### Risk 4: Insider threat at Cuizine itself

**The risk:** A current or future Cuizine employee, contractor, or acquirer could attempt to add a backdoor, build an escrow mechanism, or otherwise compromise the architectural commitments to "Cuizine cannot read user content."

**Why Cuizine cannot fully defend:** Architectural commitments require institutional follow-through. A future Cuizine that decides to abandon its principles cannot be prevented by code alone — the code can be changed.

**Mitigations applied:**
- The architectural commitments make abandonment expensive (a major reengineering effort, not a small change)
- The forbidden behaviors lists in `local-first-sync.md` Section 10 and `monetization-and-billing.md` Section 11 explicitly forbid implementing any backdoor or escrow
- The bidirectional update discipline from `build-conventions.md` Section 7 requires explicit founder decision for any change that weakens the trust posture
- The principles in this doc commit Cuizine to never building the infrastructure that would enable insider access

**Honest acknowledgment:** Users should evaluate Cuizine's behavior over time to see whether the institutional commitment is honored. The architectural commitments are necessary but not sufficient; the test is whether Cuizine continues to honor them as the company evolves.

### Risk 5: Loss of recovery passphrase

**The risk:** A user who loses their recovery passphrase and has no other device with an active Cuizine session cannot recover their encrypted Firestore blob. The data is permanently inaccessible.

**Why Cuizine cannot fully defend:** This is the consequence of Principle 1 from `local-first-sync.md` Section 2 (zero plaintext at Cuizine). The passphrase is the user's secret, and Cuizine cannot help recover it without compromising the trust posture for everyone.

**Mitigations applied:**
- The passphrase reveal screen and confirmation flow from `local-first-sync.md` Section 4 strongly encourage the user to record the passphrase
- The fresh-start option lets the user begin anew without losing the architectural posture
- The export commitment means a user who exports their data periodically has a backup that doesn't depend on the passphrase

**Honest acknowledgment:** Cuizine names this honestly throughout the recovery flow. The trade-off is real: zero plaintext at Cuizine means no recovery without the user's secret. This is a feature, not a bug, but it is a feature that some users will experience as a loss.

### Risk 6: Sophisticated state-level adversaries targeting specific users

**The risk:** A user who is the target of a sophisticated state-level adversary (journalist in a surveillance regime, activist, dissident) cannot rely on Cuizine to provide defense against that level of threat.

**Why Cuizine cannot fully defend:** State-level adversaries have resources that exceed any consumer app's defensive capabilities. They can compel disclosure from infrastructure providers (Firebase, Anthropic, etc.), they can compromise endpoint devices through targeted malware, they can use legal process to obtain data even from companies that resist.

**Mitigations applied:**
- The architectural commitments make systemic compromise expensive
- The signed-out mode minimizes the data footprint available to compulsion
- The lack of a master key means there is no single target

**Honest acknowledgment:** Cuizine is not a tool for evading sophisticated state-level adversaries. We are a thoughtful food companion with strong privacy as a default for ordinary users. Users with elevated threat models should use tools designed for their specific needs (Signal for messaging, Tor for browsing, etc.) and should not rely on Cuizine alone for protection.

## 6. Responsible disclosure policy

> How security researchers and users report vulnerabilities, and what Cuizine commits to in response. Per the answer to the upfront question, v1 alpha runs a founder-only review process and v2 launch introduces the formal program.

### v1 alpha: founder-only review

During the v1 alpha period, Cuizine does not have a formal disclosure program. The alpha population is small enough (15-25 users per ADR 0001) that the realistic security report volume is near-zero, and any reports that do come in go directly to the founder anyway.

**For alpha users:** if you discover a security issue, contact the founder directly using the same channel you use for general alpha feedback. Reports are reviewed within 48 hours and addressed in the next release if feasible.

**For external security researchers:** Cuizine is not in a public release state during v1 alpha, so external security research is not actively encouraged or rewarded. If you discover an issue and want to report it, contact the founder directly (the contact information is in the alpha onboarding materials). We will respond within 7 days but do not commit to bug bounty payments at this stage.

**The discipline of acknowledging this honestly:** Cuizine does not pretend to have a formal program it isn't operationally ready to honor. Saying "we have a security policy" when the actual reality is "the founder reads emails" is the kind of dishonesty that breaks trust the first time it's tested. We say what is actually true: alpha is small, the founder handles security personally, and the formal program activates at v2 launch.

### v2 launch: formal disclosure program

When Cuizine launches publicly in v2, the formal disclosure program activates with these elements:

- **Security email:** `security@cuizine.ai` (or the equivalent at the actual domain) with monitoring during business hours and acknowledgment within 48 hours
- **Disclosure policy page:** published on cuizine.ai with the scope of the program, the response timelines, and the legal safe harbor language
- **Response timelines:** acknowledgment within 48 hours, initial assessment within 7 days, fix or mitigation within 30 days for critical issues, with longer windows for less severe issues
- **Coordinated disclosure:** Cuizine commits to working with reporters on a disclosure timeline, typically 90 days from initial report to public disclosure unless the issue is being actively exploited
- **Legal safe harbor:** reporters acting in good faith are not subject to legal action by Cuizine, even if their research touches grey areas

**Bounty program (v2 or later):** the formal v2 program does not commit to bug bounty payments initially. A bounty program may be added later if alpha and early v2 experience suggests it would attract useful research without overwhelming operational capacity. If added, the bounty structure will follow standard practice (rewards proportional to severity, with the highest rewards for issues that compromise the trust posture).

### What gets reported

- **In scope:** vulnerabilities in Cuizine's app, the Cloud Function proxy, the Firestore security rules, the encryption layer, the recovery flow, the billing layer, and the agent prompt structures
- **Out of scope:** vulnerabilities in third-party services Cuizine uses (Firebase, Anthropic, Google, OpenAI, Open Food Facts, USDA) — those should be reported directly to the relevant vendor
- **Out of scope:** social engineering attacks on Cuizine staff (we are too small to have a meaningful staff to attack)
- **Out of scope:** physical attacks on Cuizine offices (we don't have offices)
- **Out of scope:** denial-of-service attacks (these are operational concerns, not vulnerabilities, and they are addressed through Firebase's infrastructure)

### What Cuizine commits to in response

When a vulnerability is reported (in any era):

1. **Acknowledge promptly** — within 48 hours of receipt for the v2 program; within 1 week for v1 alpha.
2. **Investigate honestly** — assess the vulnerability against the actual code and threat model, not against marketing claims.
3. **Communicate transparently with the reporter** — explain what we found, what we're doing about it, and why, in plain language.
4. **Fix or mitigate** — within the response timelines above, prioritized by severity.
5. **Notify affected users if necessary** — if the vulnerability has actually compromised user data or could have, affected users are informed honestly per Principle 6 from Section 2.
6. **Credit the reporter if they wish** — published acknowledgment on the disclosure policy page (v2 onward) for reporters who want recognition.

## 7. Regulatory compliance posture

> The legal frameworks that apply to Cuizine across v1, v2, and v3 jurisdictions, with explicit notes on what compliance requires and how Cuizine meets it. This doc specifies the technical posture; the actual ToS and privacy policy documents (written with legal counsel) translate this posture into legally binding language.

### v1: PIPEDA (Canada)

**The framework:** PIPEDA (Personal Information Protection and Electronic Documents Act) is Canada's federal privacy law for private-sector organizations. It applies to Cuizine because v1 alpha is Canadian-only (per ADR 0001) and Cuizine collects personal information from Canadian users.

**What PIPEDA requires:**
- **Accountability:** Cuizine must designate someone responsible for PIPEDA compliance. For v1 alpha, this is the founder.
- **Identifying purposes:** Cuizine must explain why it collects personal information at or before the time of collection. The constraint conversation flow includes calm, plain-language explanations.
- **Consent:** Cuizine must obtain meaningful consent for the collection, use, and disclosure of personal information. The signup flow obtains consent through the standard Sign in with Google flow plus the explicit constraint conversation.
- **Limiting collection:** Cuizine must collect only what is necessary for identified purposes. The architectural commitment from `local-first-sync.md` to local-first storage and the no-surveillance principle from `monetization-and-billing.md` Principle 4 satisfy this.
- **Limiting use, disclosure, and retention:** personal information must not be used or disclosed for purposes other than those for which it was collected, and must not be retained longer than necessary. Cuizine satisfies this through the architectural commitments and the export-and-delete flow.
- **Accuracy:** personal information must be accurate. The constraint conversation explicitly invites correction, and the modification history in the provenance model preserves correction records.
- **Safeguards:** personal information must be protected by security safeguards appropriate to its sensitivity. The encryption layer from `local-first-sync.md` substantially exceeds PIPEDA's minimum requirements.
- **Openness:** Cuizine must make its policies and practices readily available. This document, the privacy policy, and the in-app explanations satisfy this.
- **Individual access:** users must be able to access their personal information on request and challenge its accuracy. The export-everything flow from `monetization-and-billing.md` Section 8 satisfies access; the modification flow satisfies challenge.
- **Challenging compliance:** users must be able to challenge Cuizine's PIPEDA compliance. Contact information for compliance challenges is provided in the privacy policy.

**Cuizine's PIPEDA posture:** compliant by design. The architectural commitments meet or exceed PIPEDA's requirements. The privacy policy will document the posture in legally binding language.

### v2: US launch, explicitly not HIPAA-covered

**The framework:** v2 launches in the US (per ADR 0001), which adds US privacy laws to the picture. The most relevant frameworks are state-level privacy laws (CCPA in California, similar laws in Virginia, Colorado, and other states) and the question of whether Cuizine is HIPAA-covered.

**HIPAA — explicitly not applicable:**
Per Principle 2 from Section 2 above, **Cuizine is not a HIPAA-covered entity**. This is a deliberate choice, not a regulatory necessity. HIPAA applies to healthcare providers, health plans, healthcare clearinghouses, and their business associates. A direct-to-consumer wellness app where users voluntarily share dietary information is not automatically covered.

Cuizine's posture is to:
- Not market as a medical device
- Not partner with healthcare providers in ways that would create a covered relationship
- Not enter into Business Associate Agreements
- Clearly disclaim "Cuizine is not medical advice" throughout the product
- Not use clinical terminology that would imply medical authority

The disclaimer language from Principle 2 appears in the constraint conversation, the Settings → About screen, and the recovery passphrase reveal screen, and is reinforced throughout the product copy.

**State privacy laws (CCPA, VCDPA, CPA, etc.):**
California's CCPA and similar state laws give consumers rights to know what personal information is collected, to delete it, to opt out of sale, and to non-discrimination for exercising these rights. Cuizine's architectural commitments satisfy these:
- **Right to know:** the export flow from `monetization-and-billing.md` Section 8 lets users see exactly what Cuizine has about them
- **Right to delete:** the export-and-delete flow lets users delete their data permanently
- **Right to opt out of sale:** Cuizine never sells user data per Principle 5, so this right is satisfied trivially (there is nothing to opt out of)
- **Non-discrimination:** Cuizine's privacy commitments apply equally to all users on all tiers per Principle 3

**Cuizine's v2 US posture:** compliant with state privacy laws by design. Not HIPAA-covered, with explicit disclaimers. The privacy policy will document this in legally binding language.

### v3: GDPR (global expansion to UK, EU, and beyond)

**The framework:** v3 expands Cuizine to the UK, EU, and other English-speaking markets per ADR 0001. The General Data Protection Regulation applies in the EU and (post-Brexit) similar law applies in the UK. GDPR is significantly more rigorous than PIPEDA or US state laws.

**What GDPR requires that's relevant:**
- **Lawful basis for processing:** every data processing activity must have one of six lawful bases (consent, contract, legal obligation, vital interests, public task, legitimate interests). For Cuizine, the bases are consent (for the constraint data the user provides) and contract (for the operational data needed to deliver the service).
- **Data Protection Officer:** large-scale processors of special category data must designate a DPO. Cuizine processes data that includes religious affiliation and health data (special categories under GDPR), which means a DPO may be required at v3 scale. To be assessed during v3 planning.
- **Data Protection Impact Assessment (DPIA):** processing of special category data requires a DPIA. Cuizine will conduct one before v3 launch.
- **Privacy by design and by default:** systems must be designed with privacy considerations from the start. Cuizine's architectural commitments satisfy this trivially — privacy is the design.
- **Data subject rights:** right to access, rectification, erasure, restriction of processing, data portability, objection, and rights regarding automated decision-making. Cuizine's existing flows (export, delete, modification) substantially satisfy these. The "rights regarding automated decision-making" point requires careful framing because Cuizine uses LLM agents — to be addressed in the v3 privacy policy.
- **Breach notification:** data breaches must be reported to supervisory authorities within 72 hours and to affected users without undue delay. Cuizine's incident response process from Section 10 (specified later in this doc) satisfies this.
- **International data transfers:** transferring personal data outside the EU requires specific safeguards. Cuizine's architecture means most data lives on the user's device, but the encrypted sync container is stored in Firestore, which is operated by Google. Standard Contractual Clauses or equivalent will be put in place.

**Cuizine's v3 GDPR posture:** compliant by design with the architectural commitments providing most of what GDPR requires natively. The remaining work for v3 is operational (DPO assessment, DPIA, privacy policy revision, breach notification process formalization, transfer safeguards).

### A note on jurisdictional complexity

Cuizine's expansion across jurisdictions creates real legal complexity that this doc cannot fully address. The architectural commitments (local-first, encrypted, no surveillance, export-and-delete) are designed to satisfy the most restrictive realistic regulatory regime, which means they tend to satisfy less restrictive regimes as well. But the specific operational requirements — appointing a DPO, completing a DPIA, drafting privacy policies with the right legal language for each jurisdiction — require legal counsel and cannot be fully specified in this technical document.

The technical posture stays consistent: Cuizine commits to local-first encryption, zero plaintext at Cuizine, content portability, deletion on request, and honest disclosure. The legal expression of these commitments varies by jurisdiction.

## 8. The v3 cross-account peer coordination security model

> This section addresses the "deserves its own ADR" question flagged in `technical-architecture.md` Section 7.5 about the v3 linked partner feature. It is the first time in Cuizine's history that user content crosses an account boundary at all, and it needs a careful security model that preserves the trust posture while enabling the genuinely useful v3 household capability.

### What v3 linked partners introduce

Per ADR 0004, v3 introduces **linked partner profiles** — a mechanism where two adults, each with their own Firebase Auth account, can connect their Cuizine accounts to form a household where the Planner agent reasons across both their constraint graphs when suggesting meals. This is distinct from the v2 Family tier dependent profiles (where one account owns multiple profiles of varying relationship types) because linked partners are *peer* accounts, each with its own paid subscription, its own recovery passphrase, its own master key, and its own sovereignty over its own data.

### Why this is hard

Every other piece of data in Cuizine lives inside a single user's encrypted container, encrypted with a key only that user possesses. The linked partner case breaks that assumption: when Sukhi's Planner needs to reason about a meal that works for both Sukhi and Harpreet, it needs to see a *peer-appropriate view* of Harpreet's constraints — not his full profile, not his raw constraint graph, but enough information about his dietary needs that the Planner can generate meals that respect them.

The naive solution would be to decrypt Harpreet's container on Sukhi's device, which is catastrophic: it would require Sukhi's device to hold Harpreet's key, which would break Principle 1 (zero plaintext at Cuizine doesn't help if one user's device can read another user's data). The right solution is to design a *sharing protocol* that lets Harpreet explicitly share a scoped view of his constraints with Sukhi, encrypted with a key that only Sukhi can use, produced by Harpreet's device with Harpreet's full consent.

### The design

The v3 linked partner security model has four components:

**Component 1: The shared view schema.** When two accounts link, they exchange a *shared view* — a deliberately minimal projection of the constraint graph containing only what the Planner agent needs to reason about household meals. The schema is fixed and specified below, so both accounts know exactly what they're sharing. Shared views contain:

- Active avoid constraints at Inviolable, Medical, and Religious & Cultural severity (but not Preference — preferences stay private)
- Active limit constraints with their ceilings (needed for household-level nutritional reasoning)
- Active contextual constraints that affect meal planning (but stripped of any free-text original phrasing)
- Cultural context fields (cuisine origins, household traditions, ingredient vocabulary hints) needed for cultural fluency
- A timestamp indicating when the view was last generated

Shared views **do not contain:**
- The user's full constraint graph (only the meal-planning-relevant subset)
- Provenance records with original phrasing or modification history
- Suggestion history, cooked meals, or pantry data
- Preference constraints
- Profile display names beyond what the link requires
- Event log entries
- Food data cache entries

The discipline: the shared view is the *minimum* needed to enable household planning, not a general-purpose view of the other user's data.

**Component 2: Per-link encryption keys derived via authenticated key exchange.** When Sukhi invites Harpreet to link their accounts, and Harpreet accepts, a per-link symmetric encryption key is established via an authenticated key exchange protocol. The protocol:

1. Sukhi's device generates an ephemeral X25519 keypair and sends the public key to Harpreet via Firestore (through a per-link coordination document)
2. Harpreet's device generates its own ephemeral X25519 keypair and sends its public key back
3. Both devices perform the X25519 key agreement to derive a shared secret
4. The shared secret is fed through HKDF-SHA256 with a per-link salt to produce a 256-bit symmetric key
5. Both devices store the resulting key in their own encrypted containers (so it survives sync but is never visible to Cuizine)

This per-link key is what encrypts the shared views that flow between the linked accounts. Neither Sukhi nor Harpreet can read the other's full data, but both can read the shared views exchanged under this key.

**Component 3: Shared views live in a peer-shared Firestore subcollection.** When Harpreet generates a shared view from his current constraint state, his device encrypts it with the per-link key and writes it to a subcollection under his user document:

```
/cuizine_users/{harpreet_uid}/shared_views/{sukhi_uid}
```

Sukhi's device, authorized to read this specific document via Firestore security rules (see below), downloads the encrypted view, decrypts it with the per-link key, and uses it when generating household meal suggestions. The view is regenerated whenever Harpreet's constraint graph changes meaningfully.

The symmetric arrangement applies: Sukhi also writes her shared view to `/cuizine_users/{sukhi_uid}/shared_views/{harpreet_uid}`, and Harpreet's device reads it when he generates household meals.

**Component 4: Firestore security rules enforce link authorization.** The per-link `shared_views` document is readable only by the two linked accounts. The security rules check the linked-partner relationship through a `links` collection:

```
match /cuizine_users/{userId}/shared_views/{peerId} {
  allow read: if request.auth != null 
    && request.auth.uid == peerId
    && exists(/databases/$(database)/documents/links/$(linkId(userId, peerId)))
    && get(/databases/$(database)/documents/links/$(linkId(userId, peerId))).data.status == 'active';
  allow write: if request.auth != null && request.auth.uid == userId;
}
```

The `links` collection holds a small record for each active link (containing the two user IDs, the link creation timestamp, and the active/revoked status), and the rules verify both existence and active status on every read.

### The user experience

From Sukhi's perspective, linking with Harpreet is a deliberate multi-step flow:

1. Sukhi navigates to Settings → Household → Add linked partner
2. She enters Harpreet's Cuizine account email (or scans a QR code from his device for more robust identity verification)
3. Harpreet receives an in-app invitation on his next app open
4. Harpreet sees a detailed consent screen explaining what he is agreeing to: "Sukhi is asking to link your accounts for household meal planning. If you accept, Cuizine will share a limited view of your dietary constraints with Sukhi's device — your avoid constraints, your medical and religious restrictions, your cultural context. Sukhi will not see your preferences, your meal history, your past conversations, or anything else about your account. You can revoke the link at any time."
5. Harpreet accepts or declines. On acceptance, the key exchange from Component 2 runs and the link is established.
6. On both devices, the Planner agent can now reason about household meals when requested.

### Revocation

Either partner can revoke the link at any time through Settings → Household → Manage linked partners. Revocation:

1. Marks the `links` document status as `revoked` (soft delete for audit purposes)
2. Deletes both `shared_views` documents from Firestore
3. Drops the per-link encryption key from the local storage of both devices
4. Notifies the other partner on their next app open that the link has been revoked
5. Resets both accounts to their pre-link state: all their constraint data, suggestion history, and local state remain intact

Revocation is unilateral — either partner can revoke without the other's consent. This is important for the safety case where a linked partner becomes a hostile adversary (Adversary 2 from the threat model). A user in that situation can revoke the link and the adversary cannot block the revocation.

### What this model protects and what it does not

**Protected:**
- Neither partner can read the other's full constraint graph, suggestion history, cultural context beyond what's in the shared view, or any preference data
- Cuizine cannot read any of the shared data — the per-link key is held only on the two devices
- A third party with access to Firestore would see encrypted shared views and the links collection but cannot decrypt any view
- Revocation is immediate and unilateral

**Not protected:**
- A linked partner who has obtained the other's full password and recovery passphrase could read everything, not just the shared view. This is the same general risk as "a controlling partner" from the threat model — linking a hostile partner is not a use case Cuizine can defend against through cryptography.
- The shared view, while minimal, does reveal *that* each partner has certain types of constraints (e.g., Sukhi learns Harpreet has a medical avoid for peanuts, even if she doesn't learn why). Linked partners are opting into this level of mutual visibility by choice.
- Firestore sees the metadata of the link (when it was created, how often shared views are written, the sizes of the encrypted views). This metadata is not sensitive enough to be concerning, but it is named for completeness.

### Why this deserves its own ADR

This entire section is the substance of what will become ADR 0013 when v3 planning begins. The writing of ADR 0013 is a formal event that will happen when the v3 build starts — it will consolidate this section's design into the standard ADR format and be referenced by every v3 document that touches the linked partner feature. For now, this section is the working specification.

## 9. Data subject rights

> The operational mechanisms that fulfill user rights under PIPEDA, CCPA, GDPR, and the equivalent frameworks. Each right is named, mapped to a Cuizine flow, and specified with the expected user experience.

### Right to access (what we have)

Users have the right to see everything Cuizine holds about them. Cuizine fulfills this through the **export-everything flow** from `monetization-and-billing.md` Section 8. The export generates a structured JSON document containing every piece of user data — constraints, recipes, suggestions, cooked meals, pantry items, food data cache personal entries, profile metadata, and optionally the full event log.

**Expected user experience:**
- User navigates to Settings → Privacy → Export my data
- User reviews what will be included (with the option to include or exclude the event log)
- User taps Export
- The app generates the JSON document client-side in seconds
- The platform share sheet lets the user save the file via email, cloud storage, or any share target
- No Cuizine server involvement required — the export works even if the user is signed-out

**Regulatory mapping:**
- PIPEDA: right to access personal information held
- CCPA: right to know
- GDPR: Article 15 (right of access)

### Right to correct (what's wrong)

Users have the right to correct inaccurate personal information. Cuizine fulfills this through two mechanisms:

**Mechanism 1: The Curator's normal update flow.** When a user says "actually, I'm not allergic to soy, I just don't like it" during a free-text update, the Curator recognizes this as a constraint modification and updates the existing constraint (changing its severity from Medical to Preference, preserving the modification history). This is the normal path for correcting dietary information.

**Mechanism 2: The Settings → Privacy → Review my data flow (v2+).** A dedicated Settings screen where users can see every constraint, every provenance record, and every profile field, with direct edit capabilities. This is heavier than the Curator flow but necessary for corrections that don't fit naturally into conversational updates. Deferred to v2 build.

**Regulatory mapping:**
- PIPEDA: right to challenge accuracy
- CCPA: right to correct (California's CPRA update)
- GDPR: Article 16 (right to rectification)

### Right to delete (the export-and-walk-away flow)

Users have the right to delete everything Cuizine holds about them. Cuizine fulfills this through the **export-and-delete flow** from `vision.md`'s trust posture commitment and `local-first-sync.md` Section 8's signed-in-to-signed-out transition.

**Expected user experience:**
- User navigates to Settings → Privacy → Delete my account
- User sees a calm confirmation screen: "Deleting your account will erase everything on this device and in the cloud backup. This can't be undone. Before deleting, would you like to export your data? We strongly recommend it — your export is the only way to recover what you've built if you change your mind."
- User optionally exports first (same export flow as above)
- User confirms deletion
- The app performs the hard-deletion sequence:
  1. Delete the local SQLite database file
  2. Delete the encrypted blob and all subcollections from the user's Firestore document
  3. Delete the user's Firebase Auth record (requires re-authentication for confirmation per Firebase's security model)
  4. Clear all local keychain entries, cached tokens, and session state
  5. Show a brief confirmation: "Your Cuizine account has been deleted. Thanks for being here."
- If the network is unavailable for the Firestore deletion, the app queues the deletion and completes it on next network availability. The local data is deleted immediately regardless.

**What gets deleted:**
- All local data on the device
- The encrypted blob and all subcollections from Firestore
- The user's Firebase Auth record
- Any linked partner relationships (v3) — the other partner is notified of the revocation on their next app open
- Any active subscription (Google Play handles the billing-side cancellation separately)

**What does NOT get deleted:**
- Operational telemetry that doesn't contain user content (aggregate event counts, error rates) — these are not personal data and are retained for operational purposes
- Financial records required by Google Play for accounting — Google Play handles these independently of Cuizine

**Regulatory mapping:**
- PIPEDA: right to withdraw consent (which triggers deletion of data held under that consent)
- CCPA: right to delete
- GDPR: Article 17 (right to erasure / "right to be forgotten")

### Right to portability (take your data elsewhere)

Users have the right to receive their data in a structured, commonly used, machine-readable format, and to transmit it to another service. Cuizine fulfills this through the export format specified in `monetization-and-billing.md` Section 8: a structured JSON document with a documented schema that any program can read.

**The forever commitment:** per `monetization-and-billing.md` Principle 2, content portability is a forever commitment. Cuizine will not change the export format in ways that break backward compatibility, will not deprecate the export capability, and will not gate exports behind any payment or tier restriction.

**Regulatory mapping:**
- GDPR: Article 20 (right to data portability)
- CCPA: supports the "right to know" with portable format

### Right to restrict processing (pause without deleting)

Users have the right to restrict processing of their data without requiring its deletion. Cuizine fulfills this through the **signed-in to signed-out transition** from `local-first-sync.md` Section 8, which disconnects the account from the cloud backup while preserving the encrypted blob. The user's data stays in Firestore but no active processing happens — no sync, no agent invocations on behalf of that account, nothing.

A stronger form of restriction is available by using the app in signed-out mode, which involves no Cuizine server processing at all. Users who want maximum restriction without deletion can sign out and continue using the app locally.

**Regulatory mapping:**
- GDPR: Article 18 (right to restriction of processing)

### Right to object (I don't want to be processed this way)

Users have the right to object to specific types of processing. Cuizine's architectural commitments already satisfy most realistic objections:

- Objections to data sharing: Cuizine doesn't share data, so nothing to object to
- Objections to marketing: Cuizine doesn't send marketing, so nothing to object to
- Objections to profiling: Cuizine doesn't build user profiles for marketing or advertising purposes, so nothing to object to
- Objections to automated decision-making: the LLM agents make suggestions, not decisions with legal or significant effects on the user; users can always reject suggestions, and the validator's rule-based logic for safety-floor constraints is deterministic and explainable

For any objection that isn't preemptively satisfied by the architectural commitments, the user can contact Cuizine support and the objection is handled case-by-case.

**Regulatory mapping:**
- GDPR: Article 21 (right to object) and Article 22 (automated decision-making)

### Rights regarding automated decision-making (v3 consideration)

GDPR Article 22 gives users the right not to be subject to decisions based solely on automated processing that produce legal effects or similarly significant effects. Cuizine's LLM agents do not produce decisions with legal or significant effects — they produce meal suggestions that the user can accept, reject, or modify freely. The validator's deterministic rule logic produces constraint-violation verdicts, but those are not decisions about the user; they are decisions about suggestions that protect the user.

Cuizine's posture on automated decision-making:
- The user is always in control of accepting or rejecting any suggestion
- The validator's logic is deterministic and can be explained in plain language (the disclosure flow from `constraint-engine-spec.md` Section 9)
- The agent architecture's confidence tagging from ADR 0012 makes uncertainty visible to users
- No agent decisions affect the user's legal status, financial situation, or access to services outside Cuizine

This posture will be documented in the v3 privacy policy for GDPR compliance.

## 10. Security review and incident response

> The discipline for ongoing security review during build, and the response process when a vulnerability or incident is discovered. The security posture from the earlier sections is only real if it is actually maintained over time, and this section specifies how that maintenance happens.

### Ongoing security review during build

Per `build-conventions.md` Section 6's agent decision protocol, any change that touches the encryption boundary, the validator's deterministic logic, the `TierPolicy` module's gate decisions, or the Cloud Function proxy is flagged as architectural and surfaced to the founder before proceeding. This is the first line of defense against accidental security regressions.

The discipline extends beyond the agent decision protocol:

- **Every change to `data/encryption/` (the `ai.cuizine.data.encryption` package)** gets a human review before commit. No exceptions. The encryption module is small enough that every change is scrutable, and the consequences of a bug are severe enough to justify the friction.
- **Every new external dependency** is reviewed against the threat model. Does this dependency introduce a new attack surface? Does it have a history of security issues? Is it actively maintained? The review is surfaced through the dependency addition flow from `build-conventions.md` Section 4.
- **Every change to the Firestore security rules** gets a human review and is tested against the scenarios in `testing-strategy.md` Section 8.
- **Every prompt change that affects the Curator's constraint classification** gets a manual review per `build-conventions.md` Section 8, because mis-classifying a constraint (e.g., marking something as Preference when it should be Medical) can lead to safety floor violations.

### Security review cadence

- **Per-commit:** the agent surfaces any security-touching change before committing
- **Regular during build:** the founder reviews accumulated commits against the threat model, looking for drift that individual commit reviews might have missed. Rhythm set by the founder based on commit volume.
- **Pre-release:** every release (v1 alpha ship, v2 launch, v3 expansion) gets a full security review of the accumulated changes since the last release
- **Periodic after v2 launch:** the full threat model and residual risks section are reviewed to check whether the landscape has changed (new threats, new mitigations available, new regulatory requirements). Rhythm set by the founder based on user base growth and threat landscape shifts.

### Incident response process

When a security incident is identified — whether through a reporter, an automated alert, a user complaint, or internal discovery — Cuizine follows this process:

**Stage 1: Triage (first hour).**
- Acknowledge receipt of the report if from an external source
- Determine whether the issue is real or a false positive
- Determine the severity: is this a theoretical vulnerability, an exploitable bug, an active exploitation, or an actual data exposure?
- If active exploitation is suspected, take immediate containment action (e.g., disable the affected feature, rotate keys, block suspicious Firestore access patterns)

**Stage 2: Assessment (first 24 hours).**
- Understand the scope: which users are affected, what data is at risk, how long has the vulnerability existed, is there evidence of active exploitation
- Determine whether user notification is required per the breach notification criteria below
- Draft internal communication for the team (during v1 alpha, this is just the founder; during v2+, it's the team)

**Stage 3: Mitigation (first 72 hours for critical, 30 days for less severe).**
- Develop a fix or mitigation for the vulnerability
- Deploy the fix through the normal release process (or through an emergency release for critical issues)
- Verify the fix through testing

**Stage 4: Communication (as soon as mitigation is in place, or as required by regulatory timelines).**
- Notify affected users honestly per Principle 6: what happened, what we know, what we don't know, what we're doing about it, what they should do
- Notify supervisory authorities if required by GDPR (within 72 hours of becoming aware) or other applicable regimes
- Update the security disclosure page with a summary of the incident (v2+)
- Credit the reporter if they requested it and if the report was responsibly disclosed

**Stage 5: Retrospective (within 1 week of mitigation).**
- Document what happened, how it was discovered, what worked in the response, and what didn't
- Identify whether the incident reveals a gap in the threat model, the test suite, the build conventions, or any other foundation doc
- Update the relevant foundation doc with the learning

### Breach notification criteria

Cuizine notifies affected users when:

- User content was actually exposed to an unauthorized party (confirmed exposure, not theoretical vulnerability)
- A vulnerability existed that *could* have caused exposure and Cuizine cannot rule out that it did
- The incident reveals that Cuizine was not honoring a trust commitment (e.g., a logging bug that captured inference content)
- Regulatory frameworks require notification (GDPR requires notification for most personal data breaches; CCPA and PIPEDA have their own thresholds)

Cuizine does **not** proactively notify users when:

- A vulnerability was discovered and fixed before any exposure was possible (though the fix is still documented publicly)
- A theoretical attack was prevented by defense-in-depth (though the near-miss is analyzed internally)
- A third-party service had an incident that didn't affect Cuizine's own data (though Cuizine is transparent about dependencies if asked)

The discipline is to err on the side of notification when uncertain. The cost of unnecessary notification is user anxiety and some operational overhead; the cost of failing to notify is lasting trust damage.

### Post-incident commitments

After any incident that results in user notification, Cuizine commits to:

- A public post-mortem within 30 days (v2 onwards) on the security disclosure page, describing what happened, what was fixed, and what we learned
- An update to this document's threat model if the incident revealed a gap
- An update to the test suite to prevent regression
- An honest assessment in the periodic security review (post-v2) of whether the incident response worked well or needs improvement

## 11. What Cuizine commits to and what Cuizine cannot guarantee

> The explicit two-list discipline parallel to the forbidden behaviors lists in other docs. The first list names what Cuizine commits to — the promises users can rely on. The second list names what Cuizine cannot guarantee — the honest limits of the promises. Both lists exist because trust requires specificity.

### What Cuizine commits to

- **Zero plaintext at Cuizine.** Cuizine the company cannot read user content. The architectural commitment is named in `local-first-sync.md` Principle 1 and holds across every version of the product.
- **Client-side encryption before upload.** Every byte of user content that leaves the device for the encrypted sync container is encrypted client-side first. Cuizine's servers see only opaque ciphertext.
- **No master key, no escrow, no admin override.** There is no single key Cuizine can use to read user data. The trust posture relies on this commitment being absolute and forever.
- **No selling, renting, licensing, or transferring user data to third parties.** Ever. For any purpose. This is the commitment most emphatically forbidden by the forbidden behaviors lists in every relevant doc.
- **No content telemetry.** Operational telemetry (counts, error rates, latency) is allowed and useful. Content telemetry (what the user ate, what the user said, what the Chef suggested) is forbidden.
- **Content portability forever.** Users can export their data at any time, in any tier, without conditions. The export format will remain stable with backward-compatible evolution only.
- **Deletion on request.** Users can delete their account and data at any time, and the deletion actually happens — no "recovery window," no silent retention, no "we have to keep this for legal reasons" that extends beyond actual legal requirements.
- **Honest incident communication.** When something goes wrong, Cuizine tells users honestly what happened and what they should do.
- **No dark patterns.** The forbidden behaviors list from `monetization-and-billing.md` Section 11 is a real commitment enforced across the product.
- **Privacy is not a paid feature.** Every tier gets the same privacy posture.
- **The architectural commitments survive acquisition.** Should Cuizine ever be acquired, the acquisition must honor the trust posture. Any acquirer that cannot honor it is not an acceptable acquirer. (This is a commitment the founder makes; institutional commitments survive acquisition only when they are specifically written into the acquisition terms.)
- **Regulatory compliance by design.** PIPEDA, CCPA, state privacy laws, and GDPR are satisfied by the architectural commitments rather than retrofitted. The compliance is structural, not cosmetic.

### What Cuizine cannot guarantee

- **Defense against a compromised device.** If the user's device is compromised (malware, physical compromise, rooting), Cuizine's in-app protections are bypassed. The user's own device security is the foundation.
- **Defense against a determined personal adversary with sustained device access.** Per Risk 1 in Section 5, a user whose device is in the sustained control of an adversary cannot be fully protected. Cuizine mitigates but does not solve this.
- **Zero visibility for upstream LLM providers during inference.** Per Risk 2 in Section 5, inference content is visible to Anthropic, Google, or OpenAI during the API call. The mitigation is zero-data-retention options, but the risk is not zero.
- **Zero inference from external food data queries.** Per Risk 3 in Section 5, individual ingredient queries to USDA/Open Food Facts are plaintext HTTPS requests. The cache-first architecture reduces frequency over time but does not eliminate the initial queries.
- **Zero institutional drift over time.** Per Risk 4 in Section 5, a future Cuizine that abandons its principles cannot be prevented by architectural means alone. The commitment is to never build the infrastructure that would enable insider access, but architectural commitments require institutional follow-through.
- **Recovery of lost recovery passphrases.** Per Risk 5 in Section 5, a user who loses both their account credentials and their recovery passphrase cannot recover their encrypted blob. This is a feature of the trust posture, not a bug.
- **Defense against sophisticated state-level adversaries targeting specific users.** Per Risk 6 in Section 5, state-level threats exceed Cuizine's defensive capabilities. Users with elevated threat models should use tools specifically designed for their needs.
- **HIPAA-level clinical assurances.** Per Principle 2 in Section 2, Cuizine is explicitly not a HIPAA-covered entity. Users should work with their healthcare providers for clinical decisions; Cuizine is a food companion, not a medical device.
- **Perfect agent behavior.** LLM agents are fallible, and Cuizine's Chef agent can produce occasional culturally awkward, nutritionally suboptimal, or unexpectedly bland suggestions. The deterministic validator catches safety-floor violations, but subjective quality is an ongoing iteration. Users should expect Cuizine to improve over time, not to be perfect on day one.
- **100% sync reliability on unreliable networks.** Sync is designed to tolerate failure gracefully, but on truly pathological networks the user may experience sync delays or failures that require retry. The local-first architecture ensures the app is usable regardless.
- **Defense against users who violate their own trust boundaries.** If a user shares their recovery passphrase with someone who then reads their data, Cuizine cannot retroactively unshare. The passphrase is the user's secret and its confidentiality is the user's responsibility.

### The meta-commitment

The most important commitment Cuizine makes is to keep both of these lists accurate and current. As Cuizine evolves, new commitments may be added to the first list (strengthening the trust posture) and new limitations may be added to the second list (as we discover what we cannot guarantee). But no commitment will ever be removed from the first list without extraordinary justification and explicit user communication, and no limitation will ever be hidden from the second list to make Cuizine look stronger than it is.

Honest documentation of both lists is how Cuizine earns lasting trust. Marketing claims that outrun the architecture break trust the first time they are tested. These lists are designed to never outrun the architecture.

## 12. Open questions

### Threat model evolution

- **How does the threat model change as Cuizine's user base grows?** At v1 alpha scale, sophisticated adversaries are uninterested. At v2 launch with thousands of users, Cuizine becomes a more valuable target for organized crime or scraping-for-data operations. The threat model needs periodic review post-v2 launch, with rhythm set by user base growth.
- **Should the threat model explicitly consider nation-state threats for specific user populations?** Currently listed as out-of-scope (Adversary 6). But journalists, activists, or dissidents might use Cuizine, and the threat model should acknowledge their elevated risk even if Cuizine cannot fully defend them. Revisit if such user populations emerge during alpha.

### The v3 linked partner model

- **What is the right identity verification mechanism for linking partners?** Section 8 mentions "email or QR code from the device." The email path has a real vulnerability — someone could enter a typo or an attacker could claim a similar email. The QR code path is stronger but adds friction. Probably offer both, with the QR code as the strongly-recommended default. Refine during v3 build.
- **Should linked partners see each other's cultural context fields even when they have different cultural backgrounds?** Currently yes (cultural context is part of the shared view because the Planner needs it). But a linked partner in an inter-cultural relationship might not want their cultural identity automatically shared. Maybe an opt-out per-field. Refine during v3 build.
- **How does revocation propagate if one partner is offline when the other revokes?** The currently-specified approach relies on the `links` collection status check in Firestore security rules, so the offline partner's app cannot access revoked shared views once they come back online. But there's a race window where stale shared view data could be cached locally. Deferred to v3 security review.
- **Should linked partner data be included in either party's export?** If Sukhi exports her data, does it include the shared view Harpreet sent her? Probably yes (it's data Sukhi has on her device), but the export needs to be clear about which data is her own vs received from a linked partner. Deferred to v3 build.

### Regulatory posture

- **What happens if a Canadian user of v1 alpha moves to the US during alpha?** Their jurisdiction changes but their data is already under PIPEDA's framework. Probably no action required (PIPEDA's commitments are compatible with US state law requirements), but worth verifying with counsel before v2 launch.
- **When does the v3 GDPR DPIA need to be completed?** GDPR requires the DPIA to be completed before processing begins, which means before v3 launch. The scope and depth of the DPIA depend on the risk profile of the v3 features. Start drafting during v3 architectural planning.
- **Should Cuizine proactively offer GDPR-style rights to non-EU users?** The architectural commitments already satisfy most of them, so offering them explicitly is just documentation work. Tentatively yes — the trust posture is strengthened by universal application of the strongest standard.

### Disclosure and incident response

- **What's the right channel for security reporters to contact Cuizine during v1 alpha?** Section 6 says "directly to the founder" but the specific channel (email, form, Signal, etc.) isn't specified. Probably an email address published in the alpha onboarding materials, with the understanding that response times are best-effort during alpha.
- **What is the minimum incident that triggers user notification?** Section 10 gives criteria but there's inherent judgment at the margin. Iterate based on actual incidents during alpha.
- **When should Cuizine introduce a security bulletin subscription?** A way for security-conscious users to subscribe to notifications about security fixes even when they don't affect their specific account. Deferred to v2 launch.

### Incident preparedness

- **Has Cuizine actually rehearsed the incident response process?** No, not yet. A tabletop exercise walking through a simulated incident would reveal gaps in the current process. Schedule one during v1 alpha.
- **What happens if the founder is unavailable during a critical incident?** Currently the single point of failure for v1 alpha response. Mitigated by the small scale (incidents are unlikely) but a real risk. Probably okay for alpha; needs a real plan by v2 launch.

## 13. Cross-references

### What this document references

- `vision.md` — for the trust posture that this doc defends
- `PRD.md` — for the alpha context and the scale of v1 operations
- `technical-architecture.md` — for the subsystem decomposition and the v3 cross-account question that Section 8 answers
- `constraint-engine-spec.md` — for the provenance and disclosure mechanics that data subject rights depend on
- `agent-architecture.md` — for the validator's role in the safety floor and the agent layer's attack surface
- `data-model.md` — for the encryption boundary specifications
- `local-first-sync.md` — for the principles and the detailed encryption, recovery, and transition flows
- `monetization-and-billing.md` — for the content portability commitment, the tier-neutral privacy commitment, and the billing layer's attack surface
- `build-conventions.md` — for the ongoing security review discipline
- `testing-strategy.md` — for the test suite that verifies the security posture holds
- ADR 0004 — multi-profile households, which Section 8's v3 linked partner model extends
- ADR 0006 — multi-provider routing, which Section 3's upstream provider adversary assessment depends on
- ADR 0008 — BYOK deferred to v3, which Section 5's residual risk mitigation for upstream provider visibility relies on
- ADR 0009 — severity tier model, which the safety floor commitments rest on
- ADR 0010 — post-generation validation, which protects the agent layer from manipulation
- ADR 0011 — optional backend with capability tiers, the four quadrant matrix that shapes the threat model across sign-in states
- ADR 0012 — food data sources, which creates the external query inference risk

### What this document defers to deeper-dive docs

- **Legal ToS and privacy policy documents** — written separately by legal counsel, using this doc as input. The legal documents translate this posture into binding language for each jurisdiction.
- **`roadmap.md`** — the milestone-level plan for when v2 launches (triggering the HIPAA disclaimer rollout and the US state privacy law compliance) and when v3 ships the linked partner feature (triggering ADR 0013 formalization and the GDPR DPIA)
- **`alpha-feedback-and-iteration.md`** — the alpha process, including how security feedback flows into the foundation docs and the incident response rehearsal
- **`launch-readiness-checklist.md`** — the go/no-go gates that include security posture completeness criteria
- The v3 ADR 0013 (to be written) — the formal architectural decision record consolidating Section 8's v3 linked partner model into the standard ADR format when v3 planning begins

### What this document does *not* defer (decisions made here)

- The six core security and privacy principles (Section 2)
- The six-adversary threat model with likelihood, impact, defenses, and acceptable residual risk for each (Section 3)
- The attack surface analysis across six architectural layers (Section 4)
- The six residual risks acknowledged honestly with mitigations and limitations (Section 5)
- The responsible disclosure policy with the v1 founder-only review and the v2 formal program (Section 6)
- The regulatory compliance posture covering PIPEDA, explicit not-HIPAA for v2, and GDPR for v3 (Section 7)
- The v3 cross-account peer coordination security model with the shared view schema, the per-link key derivation, the Firestore subcollection structure, and the revocation flow (Section 8)
- The data subject rights with operational mechanisms for access, correction, deletion, portability, restriction, objection, and automated decision-making (Section 9)
- The security review cadence and incident response process (Section 10)
- The dual commitments-and-limitations list and the meta-commitment to keep it honest (Section 11)

### How the agent should use this doc

When the coding agent builds Cuizine's security-sensitive components, this doc is the framework for understanding *why* each architectural constraint exists. The agent does not need to re-derive the threat model — it applies the existing model to its implementation decisions. When an implementation choice might affect the security posture, the agent surfaces the question per `build-conventions.md` Section 6.

Section 11 (the commitments-and-limitations list) is the specific reference for what Cuizine has publicly committed to. Changes that would weaken any commitment in the list are forbidden without extraordinary justification, and any such change requires a new ADR and explicit founder decision.

Section 5 (the residual risks) is the reference for what Cuizine honestly cannot defend against. When a user asks "can Cuizine protect me from X?", the answer comes from Sections 5 and 11 together — either Cuizine can protect them (and the commitment is in Section 11) or it cannot (and the limitation is in Section 5). There is no hedging between the two.

Section 8 (the v3 linked partner model) is the working specification for what will become ADR 0013 when v3 planning begins. The agent should treat Section 8 as binding for v3 work, and flag any implementation questions about it during v3 build.

When the agent encounters a question this doc does not answer, the discipline from PRD § 9 applies: check the vision, check the relevant ADR, check the foundation docs, ask the founder before guessing. Security decisions made silently erode the trust posture in ways that are hard to detect and hard to recover from. The cost of asking is small; the cost of silent drift is large.

---

*End of `security-and-privacy.md` v1 (initial draft). Next revision will incorporate any security learnings from the v1 alpha, any changes to the threat model as the user base grows, and any regulatory developments that affect the compliance posture. The core principles in Section 2 are stable; the specific commitments in Section 11 are expected to refine as Cuizine's experience with real adversaries grows. Section 8 (the v3 linked partner model) will graduate to ADR 0013 when v3 planning begins, and this section will then cross-reference the ADR rather than holding the working specification.*
