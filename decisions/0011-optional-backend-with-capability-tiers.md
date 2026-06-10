# 0011 — Optional backend with capability tiers (Firebase platform, client-side encryption)

**Status:** Accepted
**Date:** 2026-04-11

**Amendment note:** This ADR's decision (the optional-backend model, the four-quadrant capability matrix, Firebase as the backend platform, client-side encryption with Firestore as an opaque blob store) is unchanged. Following ADR 0016 (native Kotlin), references below to Firebase's "Flutter ecosystem support" as part of the rationale are updated to Firebase's native Android SDK support — Firebase has first-party, mature Android SDKs (Auth, Firestore, Cloud Functions, Analytics), so the rationale holds at least as strongly on native Android as it did under Flutter. This is a wording correction, not a decision change.

## Context

The vision and ADR 0007 establish Cuizine as local-first with strong trust commitments — user data lives on the device, end-to-end encryption for any sync, no analytics on personal data, and the user can export and walk away at any time. ADR 0007 introduced a free local-first tier where the user can use Cuizine without ever sending data to the cloud at all, alongside paid tiers (Cuizine and Cuizine Family) that activate the multi-agent AI system.

These commitments left an architectural question unanswered: does v1 alpha have any Cuizine-owned backend service at all? The naive options were "real backend from v1" (operational complexity from day one, slightly weaker trust posture because requests flow through Cuizine servers) and "no backend ever in v1" (operational simplicity but API key management problems and a hard wall in front of any feature that needs server coordination).

The right answer is neither extreme. The optional-backend-with-capability-tiers model honors both populations Cuizine serves: privacy-maximalist users who want their data to never touch Cuizine's servers get a fully-functional product, and convenience-prioritizing users who want sync, family features, and managed billing get a richer experience by signing in. Neither is "the real Cuizine" with the other a degraded version — both are first-class.

This ADR also addresses whether multi-profile household features (per ADR 0004) require a backend, since that question has a non-obvious answer that affects the architecture.

## Decision

### The principle

**Cuizine is fully functional in pure local-first mode with no account, no Cuizine backend, and no Cuizine servers in the picture. Users who want capabilities that require a backend can opt into them by signing in. Until they sign in, the app behaves exactly as if Cuizine has no backend at all.**

This is the same trust philosophy as ADR 0007's free local-first tier, applied to the *backend dimension* rather than the *AI dimension*. The two opt-ins are independent: the AI dimension (free vs paid) and the backend dimension (signed-out vs signed-in) can be combined in multiple valid states.

### The capability matrix

| | **Signed out** | **Signed in** |
|---|---|---|
| **Free tier** | Pure local-first. Constraint engine, manual recipe management, household structure setup, all data tools. No AI. No backend. No Cuizine servers in the picture at all. | Local-first data plus cross-device encrypted sync (via Firestore as a blob store). Still no AI. The user's encrypted container is replicated across their devices but Cuizine cannot read it. |
| **Paid tier** (Cuizine / Cuizine Family) | *Not possible in v1 or v2.* Paid tiers require an account because the account holds the billing relationship and authenticates the LLM proxy calls. v3 may enable this cell via BYOK (see ADR 0008) — a user supplies their own provider API key and gets AI features without a Cuizine account. | Full AI agents (Curator, Chef, Pantry in v1; Planner and Sourcing added in v2). Cross-device sync. Family tier (v2) adds dependent profiles and household planning. Billing through standard app store mechanisms. |

Three valid states in v1 and v2, one impossible state, one v3-enabled state via BYOK. Clean.

### Backend platform: Firebase

Cuizine's backend services are built on **Firebase**, specifically:

- **Firebase Authentication** for sign-in. v1 alpha ships with **Sign in with Google** as the only option (most Android users have a Google account already and the OAuth flow is cleanest). v2 may add email sign-in as needed. Firebase Auth gives us identity without running our own auth infrastructure.
- **Cloud Firestore** as an *encrypted blob store* — not as a queryable database of user data. The device encrypts the user's full container with a key derived from the user's account credentials, then stores the encrypted blob in Firestore. Firestore sees blob size, last update time, and account ID. It does not see user data. We cannot read the contents even if compelled to. This is "client-side encryption with cloud blob storage," and it preserves the trust posture even with a backend in the picture.
- **Cloud Functions** for the LLM proxy layer. Signed-in users' AI calls hit a Cloud Function that holds the provider API keys (Anthropic, Gemini, OpenAI per ADR 0006) and forwards the request upstream. The Cloud Function does not log inference content, does not store anything, and does not persist any data about the request beyond the bare minimum needed to enforce rate limits and abuse prevention. Anthropic API zero-data-retention is enabled on the upstream side per the v1 build-complete criteria in PRD § 7.

### Firebase services we explicitly do *not* use

- **Firebase Analytics.** No telemetry on personal data, ever, per the trust posture from `vision.md`. Even anonymized analytics are not used because they create a tempting drift toward "well, we already collect X, why not also Y."
- **Firebase Cloud Messaging (push notifications).** No notifications in v1 per PRD § 6 ("Push notifications and engagement loops" — explicitly out of v1 scope).
- **Firebase Crashlytics in v1.** Crash reporting may be desirable in v2 but Crashlytics by default uploads stack traces that could include user-content fields. Adding it requires careful redaction logic that the v1 alpha does not need (the alpha population is small enough that the founder can debug crashes from logs the user voluntarily exports). Revisit for v2.
- **Firebase Hosting, Realtime Database, ML Kit, Remote Config.** Not needed.

### Encryption model for sync

The client-side encryption uses a **hybrid key derivation**: the encryption key is derived from a combination of (a) the user's account credentials (so they can recover their data on a new device by signing in) and (b) a recovery passphrase shown to the user once during account setup, which they are prompted to write down. This is the same pattern Signal and Bitwarden use, and it is the only honest way to do client-side encryption with sync — pure-account-derived keys mean Cuizine's compromise compromises everyone, and pure-device-derived keys mean device loss is permanent data loss. Neither is acceptable.

The exact encryption approach (AES-256-GCM with PBKDF2 or Argon2 for key derivation, library choice) is deferred to `local-first-sync.md` and may itself become an ADR if the design has non-obvious tradeoffs.

### Household features and the backend

Per ADR 0004, v2 dependent profiles live entirely within the primary user's encrypted container. There is no cross-account data sharing in v2 — Sukhi creating dependent profiles for Beeji, Simran, and Harpreet does not create new Cuizine accounts for them, and their data is part of Sukhi's container, not separate accounts.

This means household features in v2 do **not** require a backend in the cross-account-data-sharing sense. The data sharing is intra-account, not inter-account.

However, household features in v2 *do* interact with the backend in two ways:

1. **They require an account.** Household features are part of the Cuizine Family tier, which is a paid tier, and paid tiers require accounts (per the matrix above). So a Family tier user is signed in by definition, and the backend is in the picture for billing and AI proxying even though it is not in the picture for cross-account data sharing.

2. **They strongly benefit from sync.** A Family tier user managing dependent profiles on their phone will likely also want to access them on a tablet or other device. The sync is still entirely within their own account — dependent profile data is part of their encrypted container — but multi-device access requires the encrypted blob to be in Firestore. Single-device household management is technically possible but practically much less useful.

**v3 linked-partner profiles, by contrast, do require backend coordination in the cross-account sense.** When Sukhi adds Harpreet as a linked partner in v3 (his own account, his own profile, peer-shared with Sukhi), there must be a coordination layer between their two accounts. This is a real cross-account data-sharing relationship and it requires backend infrastructure that v2 does not have. The backend that v2 ships will need to grow to support v3's linked partners — likely with a new permissions model and a peer-to-peer encrypted sharing protocol — but this growth is contained to the backend layer and does not require rebuilding the device-side architecture.

### Telemetry and observability

Cuizine collects **no telemetry on user content or behavior** at any time, in any tier, signed-in or signed-out. This is a forever commitment, not a v1 deferral.

Cuizine *may* collect **operational telemetry** from signed-in users — Cloud Functions invocation counts, error rates, latency percentiles — for the purpose of running the service reliably. This telemetry is aggregate and anonymous, contains no user content, and is governed by Firebase's standard observability infrastructure. It is the kind of metrics any service operator needs to know whether their service is up. It is not the kind of telemetry that would violate the trust posture.

For signed-out users, there is no telemetry of any kind because there is no backend interaction.

### v1 alpha posture

The v1 closed alpha runs the optional-backend model from day one — the backend exists, signed-in mode is available, and all alpha users will be signed in (because the alpha is full-feature paid trial per ADR 0007, and paid implies signed-in). The signed-out / pure-local-first capability is *built* in v1 even though no alpha user uses it, because building it later is meaningfully more work than building it from the start, and because the alpha is the right time to verify that the signed-out path actually works correctly.

The alpha is the moment when the architectural commitment to "fully functional without an account" is most easily honored, because the codebase is small. After v2, the temptation to add account-required features creeps in constantly, and the discipline only holds if the unsigned-in path is a first-class supported state from day one.

## Consequences

**Positive.** The trust posture is meaningfully stronger than a backend-required model — users genuinely have the option to use Cuizine without ever interacting with our infrastructure. Privacy-maximalist users get a real product, not a degraded one. Convenience users get sync, family features, and managed billing through a clean Firebase-backed flow. Firebase is operationally cheap (essentially free at v1 and v2 scales), has mature first-party Android SDKs, and is battle-tested. Client-side encryption with Firestore as a blob store gives us cloud sync without compromising the "we cannot read your data" promise. The four-quadrant matrix is simple to communicate to users and easy for the agent to reason about. v3 BYOK fills in the previously-impossible signed-out-paid quadrant naturally.

**Negative.** Two opt-in dimensions (AI tier and backend account) create UX complexity in onboarding — we have to explain to users that they can use Cuizine three different ways (signed-out free, signed-in free, signed-in paid) without overwhelming them. Defaulting to signed-in is the natural move for most users, with the signed-out path discoverable but not pushed. Client-side encryption is real engineering work and the recovery passphrase UX is a known hard problem (users lose passphrases, and unlike a normal SaaS, we cannot reset them — that's the point). The hybrid key derivation needs careful design and probably its own ADR when we write the sync spec. Vendor lock-in to Firebase is a real cost — moving off Firebase later would be a meaningful migration. Mitigation: the data-layer interfaces are abstracted from Firebase specifics, so the lock-in is contained to a few adapter classes rather than spread through the codebase.

The unsigned-in free tier means a portion of the alpha test surface (sync, account-required features) is exercised only by the signed-in path. We mitigate this by requiring v1 to ship the unsigned-in path as a tested code path, even though all alpha users use the signed-in path. The unsigned-in mode gets exercised in v2 by the public free tier population.

**Neutral.** Firebase is owned by Google. Some users (and some founders) have philosophical reservations about this. The decision is pragmatic, not ideological — Firebase is the right tool for the job at our scale, the alternatives (Supabase, AWS Amplify, custom backend) are either less mature on native Android or significantly more operationally expensive. The choice is revisable; it's an ADR, not a constitutional commitment.

## Alternatives considered

**Real backend from v1, all features require an account.** Rejected. Operational complexity from day one for 15-25 alpha users is unjustified, and it weakens the trust posture by making "no account, no data leaving the device" impossible. The signed-out path costs little to support and earns a meaningful trust win.

**No backend ever in v1, embed API keys in the APK.** Rejected. API key embedding has a real leakage risk even at small scale, and the v2 transition would be a backend rebuild rather than a backend extension. The minimal-Firebase model is operationally similar to no-backend (essentially free at our scale, near-zero ops burden) but architecturally aligned with v2.

**Custom backend instead of Firebase.** Rejected for v1 and v2. Building auth, sync, and the LLM proxy from scratch is weeks of engineering effort that buys us nothing at our scale. Firebase Auth + Firestore + Cloud Functions covers our needs at near-zero cost and the native Android integration is excellent. Revisit if Firebase pricing or terms change materially.

**Supabase instead of Firebase.** Considered seriously. Supabase has the appeal of being open-source and Postgres-based (which is more familiar). Rejected for v1 because its native Android SDK support is less mature than Firebase's, and because Firebase Auth is meaningfully more battle-tested for the OAuth flows we need. Supabase becomes a more compelling alternative if we ever want to leave Firebase, and the abstraction layer this ADR commits to (data-layer interfaces independent of Firebase specifics) makes a future migration tractable.

**Firestore as a queryable database (storing user data as readable documents).** Rejected. Storing readable user data in Firestore would put Cuizine in possession of medical, religious, and household data — exactly what the trust posture says we will not have. Client-side encryption with Firestore as a blob store is the only honest way to use Firestore for a product like Cuizine.

**Required account from the first launch screen.** Rejected. Per PRD § 6 ("Account creation as a separate step from first use"), Cuizine works immediately on first launch and account creation is deferred until the user has experienced value and only happens if needed. The signed-out free tier makes this possible.

**Telemetry on user behavior with anonymization.** Rejected. The "anonymized analytics" framing is exactly the slippery slope that erodes trust postures over time. The forever commitment is "no telemetry on personal data," and the operational telemetry we do collect is genuinely operational (uptime, error rates, aggregate latency) and does not include user content.

## Related

- See `vision.md` § Trust posture
- Pairs with 0007 (the AI tier dimension; this ADR adds the orthogonal backend dimension)
- Pairs with 0008 (BYOK in v3 enables the previously-impossible signed-out-paid quadrant)
- Pairs with 0004 (household features require an account but not cross-account data sharing in v2; v3 linked partners do require cross-account backend coordination)
- Pairs with 0006 (LLM proxy in Cloud Functions implements the multi-provider routing layer for signed-in users)
- The forthcoming `local-first-sync.md` must define the encryption library, key derivation, recovery passphrase UX, and Firestore blob schema explicitly — likely with its own ADR if the design has non-obvious tradeoffs
- The forthcoming `technical-architecture.md` implements the trust boundaries diagram and the component map that this ADR establishes
- The forthcoming `security-and-privacy.md` formalizes the threat model and the "what data lives where" commitments
