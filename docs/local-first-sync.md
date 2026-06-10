# Cuizine — Local-First Sync, Encryption, and Recovery

> The doc where vision.md's trust posture becomes runtime behavior. ADR 0011 made the policy decisions about Cuizine's optional backend and capability tiers; this document operationalizes those decisions into concrete encryption, key derivation, sync protocol, and recovery flows. Where `data-model.md` (`data-model.md`) specified *which tables get encrypted and synced*, this doc specifies *how* the encryption and sync actually happen at runtime, what the recovery passphrase is and how the user receives it, and what happens at the most consequential boundaries — signed-out to signed-in, signed-in to signed-out, and multi-device conflict. Any gap here is a trust gap users will feel directly.

## 1. Purpose & how to read this doc

This document specifies the **runtime behavior of the local-first sync, encryption, and recovery layer for Cuizine v1**, with forward-references to v2 and v3. It assumes you have read `vision.md`, `PRD.md`, `technical-architecture.md`, `data-model.md`, and the ADRs — especially ADR 0011 (optional backend with capability tiers) and ADR 0012 (food data sources, particularly the cache-as-encrypted-sync property). Concepts established there are not re-derived here.

This document defines: the core principles this layer is designed against; the encryption library choice and cipher; the Argon2id key derivation function with its parameters; the recovery passphrase generation, presentation, and recovery flow; the sync protocol against Firestore as an encrypted blob store; the multi-device conflict resolution policy; the signed-out to signed-in transition (the most consequential single flow); the signed-in to signed-out transition; the operational concerns (Firestore quotas, retry policies, network failures, sync intervals, battery); the explicit list of forbidden behaviors that would erode the trust posture; and the open questions this spec acknowledges.

This document does **not** define: the formal threat model for the data layer (that's `security-and-privacy.md`, which depends on this doc); the schema of the tables being encrypted (that's `data-model.md`, already complete); Firebase project configuration or deployment specifics (those live in build conventions and the deployment guide); or the user-facing copy for the recovery passphrase flow (that lives in the prompts/copy directory and gets iterated separately).

**When this document and a deeper-dive doc disagree,** this doc wins for runtime behavior of encryption, sync, and recovery; `data-model.md` wins for table schemas; `security-and-privacy.md` wins for threat modeling and risk analysis. Cross-cutting conflicts trigger a deliberate decision rather than silent resolution.

## 2. Core principles

> The non-negotiable commitments this layer is designed against. Every implementation decision in this doc traces back to one of these principles. If a future change to the sync layer would violate one of these principles, that change requires a new ADR before any code is written.

### Principle 1: Zero plaintext at Cuizine

Cuizine the company can never read user content. This is the strongest of the principles and the one that constrains every other decision. The encryption is applied client-side before any data leaves the device. The encryption key is derived from material Cuizine does not hold (the user's account credentials and a recovery passphrase the user keeps). Cuizine's servers see only opaque encrypted bytes of user content — the one piece of plaintext metadata the backend necessarily holds is the account identity needed to authenticate (an email address via Firebase Auth), and it is never linked to anything readable. There is no master key, no escrow, no admin override, no "we can recover your data if you contact support." If the user loses both their account credentials and their recovery passphrase, their data is unrecoverable — and this is a feature, not a bug. The price of zero-trust-in-Cuizine is that recovery is the user's responsibility.

This principle is named first because it is the one most likely to be eroded silently. The temptation to "just have a backup key for support cases" is real and must be resisted forever.

### Principle 2: Single source of truth on the device

The user's local SQLite database is the canonical source of truth for their data. Firestore holds an encrypted backup that exists only to enable cross-device sync — it is not the master copy. When a conflict arises between local state and remote state, the resolution policy favors local state (with the merge logic specified in Section 6) because the local state is what the user just saw and acted on. The remote state is a snapshot from another device that may be stale relative to what's in front of them.

This principle is what makes Cuizine "local-first" in a meaningful sense rather than just "encrypted cloud-first." The app must remain fully functional with no network connection at all — and not in a degraded read-only mode, but in full read-write mode. Sync happens when the network is available; decisions never wait for sync.

### Principle 3: Recovery without Cuizine's help

If the user wants to recover their data on a new device, they sign in with their Google account and enter their recovery passphrase. Both pieces are required. Cuizine is not part of the recovery loop in any way — Cuizine the company cannot expedite recovery, cannot reset the passphrase, cannot offer a "forgot passphrase" link that does anything useful. The user's relationship is with their own credentials and their own passphrase. This is the structural commitment that makes Principle 1 credible.

### Principle 4: No silent state

Every state transition in this layer is observable to the user when it matters and invisible to the user when it doesn't. Sync activity in the background is silent (the user doesn't need to know about every successful upload). Sync failures that affect the user's ability to use the app are visible (with calm, plain-language messages). Recovery flows are step-by-step and the user always knows what stage they're at. Half-states — "your data is partially encrypted, partially uploaded" — are forbidden by design. Either a transition completes fully or it rolls back to its starting state.

This principle protects against the worst failure mode of any sync system: the moment when the user thinks their data is safe but it actually isn't, or thinks they've lost data that's actually still recoverable.

### Principle 5: Operations are bounded and predictable

The sync layer has no unbounded loops, no unbounded retries, no unbounded resource consumption. Every operation has a timeout, a retry budget, and a graceful failure path. The user's battery is precious and the user's data plan may be metered, so background sync is rate-limited and respects platform-level constraints (Wi-Fi vs cellular, low-power mode, doze mode). Sync activity should be invisible because it's well-behaved, not because it's hidden.

## 3. Encryption architecture

### The library

Cuizine uses **Google Tink** (`com.google.crypto.tink:tink-android`, already named in `technical-architecture.md` Section 6) for all encryption operations. Tink is a well-maintained, widely-audited cryptographic library from Google that provides misuse-resistant APIs for the modern primitives Cuizine needs, and it deliberately hides low-level implementation details that are easy to get wrong. The choice is locked unless a specific weakness surfaces; if a stronger library emerges later, the encryption layer is abstracted enough that swapping libraries is a contained change. (Argon2id, which Tink does not provide directly, is supplied via a dedicated, well-regarded Argon2 library for the JVM/Android — see the key derivation section below.)

### The cipher

Cuizine encrypts user data with **AES-256-GCM** (Galois/Counter Mode). AES-256 is the standard for symmetric encryption in 2026, GCM provides authenticated encryption (so we can detect tampering as well as preserve confidentiality), and Tink exposes it directly through its `AEAD` primitive. Each encrypted blob carries its own randomly-generated 96-bit IV (nonce), generated fresh for every encryption operation — IVs are never reused with the same key, ever. The IV is stored alongside the ciphertext (it is not secret). (Tink manages IV generation internally as part of its AEAD primitive, which is one of the misuse-resistance benefits of using it.)

### The key derivation function: Argon2id

Cuizine derives the master encryption key from the user's credentials and recovery passphrase using **Argon2id**, the modern memory-hard KDF that won the 2015 password hashing competition and is the OWASP-recommended default for new applications. Argon2id is memory-hard, which means an attacker who has somehow obtained the encrypted blob and is trying to brute-force the key cannot easily parallelize the attack across cheap GPUs or ASICs the way they could against PBKDF2.

**Parameters for v1:**
- **Memory cost:** 64 MiB. Tunes the memory-hardness of the derivation — high enough to make GPU attacks expensive, low enough to fit comfortably on a typical mid-range Android device.
- **Time cost (iterations):** 3. Combined with the memory cost, this targets roughly 500ms-1s of derivation time on a typical mid-range Android phone in 2026. The user feels this only at sign-in and at sync-key-rederivation moments, not during ongoing operation.
- **Parallelism:** 1 lane. Parallelism is unhelpful on mobile devices where threading is already constrained, and a single lane is the simplest and most predictable.
- **Output length:** 32 bytes (256 bits), the AES-256 key size.

These parameters are revisable based on alpha measurement — if 500ms turns out to be 3 seconds on common alpha-user devices, we tune down; if it's 100ms, we tune up. The discipline is: target a sub-second derivation time on representative devices, and revisit annually as device capabilities improve.

### Salt and IV strategy

- **Salt for the KDF**: a per-user random 16-byte salt, generated once on account creation, stored in Firestore alongside the encrypted blobs. The salt is not secret; its purpose is to defeat rainbow tables and to ensure that two users with identical credentials and passphrases (extraordinarily unlikely) still derive different keys.
- **IV for AES-GCM**: a per-encryption random 96-bit nonce, generated fresh for every blob upload. Stored as a prefix to the ciphertext. Never reused with the same key.

### What the master key looks like

The master encryption key is a 256-bit symmetric key, derived as:

```
key = Argon2id(
  password = utf8(account_token || ":" || recovery_passphrase),
  salt = user_salt_from_firestore,
  memory = 64 MiB,
  time = 3,
  parallelism = 1,
  output_length = 32 bytes
)
```

Where `account_token` is a stable per-account identifier derived from the Firebase Auth UID (so it's the same across devices and survives Firebase token refreshes), and `recovery_passphrase` is the user-controlled passphrase from Section 4.

The master key lives **only in process memory** while the app is running and is re-derived from credentials and passphrase on each fresh app launch. It is never written to disk. It is never logged. It never leaves the device. When the app is killed or backgrounded for an extended period, the key is dropped and re-derivation happens on next foreground.

### Per-blob encryption envelope

Every encrypted blob uploaded to Firestore has the following structure (encoded as base64 for transport):

```
| version (1 byte) | iv (12 bytes) | ciphertext (variable) | gcm_tag (16 bytes) |
```

The version byte enables future cipher migrations (e.g., if AES-256-GCM is ever deprecated). v1 ships with version = 1 meaning AES-256-GCM with the parameters above.

## 4. The recovery passphrase model

> Why it exists: Principle 1 (zero plaintext at Cuizine) forces a structural choice — the encryption key cannot be recovered by Cuizine, so it must be recoverable by the user from material the user holds. The recovery passphrase is that material. It is the secret half of the key derivation; the user's account credentials are the public half (in the sense that Firebase Auth can re-establish them on a new device, but only if the user provides the passphrase as well).

### Passphrase format: 6 words from a curated friendly wordlist

Cuizine generates recovery passphrases as **six space-separated words drawn randomly from a curated 7,776-word friendly English wordlist** (the standard Diceware list, freely available, in the public domain). Six words from a 7,776-word list yields approximately 77 bits of entropy — enormously strong against any realistic attack on Cuizine's threat model.

Why six words from a friendly wordlist instead of BIP-39 12-word mnemonic:

- **Friendlier tone.** Sukhi opening Cuizine and being asked to write down "abandon ability able about above absent" feels mysterious or alarming in a way that "ginger lantern thursday breakfast curry sunshine" does not. Cuizine is a food companion, not a cryptocurrency wallet, and the recovery flow should feel like Cuizine being thoughtful, not Cuizine being technical.
- **Memorability.** Six concrete-noun words from a friendly dictionary are meaningfully easier to write down on a sticky note, recognize when read back, and (for the user who chooses to) commit to memory.
- **Sufficient entropy.** 77 bits is well above the practical attack threshold for the threat model in `security-and-privacy.md`. We're not protecting against state-level adversaries with quantum computers; we're protecting against the realistic attack surface of "someone has obtained the encrypted blob and is trying to brute-force it."

The wordlist itself ships with the app binary (a small JSON file under `assets/recovery_wordlist/v1.json`) so passphrase generation works fully offline. The wordlist is versioned independently — if a future version of the wordlist refines word choices, the version is recorded in the per-account metadata so old passphrases can still be validated.

### Generation

When a user signs up for the first time (signed-in path), the app:

1. Generates a 256-bit random value using the platform's CSPRNG (`java.security.SecureRandom` on Android).
2. Maps the random value to six words from the wordlist (the standard Diceware-style mapping: each pair of characters in the random value indexes into the wordlist).
3. Presents the six-word passphrase to the user immediately, before any data is encrypted or uploaded.
4. The passphrase is held in volatile memory until the user has confirmed they have recorded it.

### Presentation and confirmation

The recovery passphrase presentation is a deliberate, full-screen flow that the user cannot dismiss without explicit confirmation. The flow:

1. **The reveal screen** shows the six words clearly (large, readable type, no autocomplete-friendly formatting), with calm explanatory text: "This is your recovery passphrase. You'll need it if you want to use Cuizine on a new device. Write it down somewhere safe — Cuizine cannot recover it for you if you lose it."
2. **The user can copy the passphrase** to the clipboard with a tap. The clipboard contents are flagged as sensitive on Android (the OS handles the masking).
3. **The confirmation step**: the user is asked to re-enter the passphrase (or a randomly-selected subset of three of the six words) to confirm they have recorded it correctly. This is a small but meaningful friction — the user is forced to actually look at what they recorded — and it catches the failure mode where a user taps "I've saved it" without saving anything.
4. **After confirmation**, the passphrase is held in memory long enough to derive the master key, the encryption is initialized, and any pending data is encrypted and uploaded. After that moment, the passphrase is dropped from memory.

The passphrase is **never** stored on disk by Cuizine after the initial setup. The user is responsible for keeping their copy of it.

### Recovery on a new device

When a user installs Cuizine on a new device and signs in:

1. The app authenticates the user with Firebase Auth (Sign in with Google).
2. The app downloads the user's salt from Firestore (the salt is not secret; only the encrypted blobs are sensitive).
3. The app prompts the user to enter their recovery passphrase.
4. The app derives the master key using Argon2id with the same parameters and salt.
5. The app downloads the encrypted blobs from Firestore.
6. The app attempts to decrypt the blobs. **If decryption succeeds**, the blobs are unpacked into the local SQLite database and the app is fully restored. **If decryption fails** (wrong passphrase, corrupted blob, version mismatch), the app surfaces a calm error: "I couldn't unlock your data with that passphrase. Could you check it and try again?"
7. After three failed attempts in a single session, the app pauses for 30 seconds before allowing another attempt. This is rate-limiting, not lockout — there is no concept of "locked out" because the data is purely cryptographic and the only resource being protected is the user's own time.

If the user has truly lost their passphrase, the app surfaces a final option: "If you've lost your passphrase, you can start fresh on this device. Your existing data on other devices is unaffected, but it cannot be unlocked here. Continue with a fresh start?" Choosing this option creates a new local profile without restoring; the encrypted blobs in Firestore remain untouched (still belong to the original passphrase) and a fresh sync container is created if the user signs back in.

### What happens when the passphrase is lost forever

If the user has lost their passphrase **and** has no other device with an active session, the encrypted blobs in Firestore are permanently unrecoverable. Cuizine the company has no way to help. The user must start fresh.

This is the consequence of Principle 1 and it is intentional. The recovery passphrase reveal screen and the post-recovery onboarding both reinforce this honestly — Cuizine never implies that support can help with passphrase loss, because doing so would create a false expectation that would erode trust the first time it broke.

### Passphrase change

A user can change their recovery passphrase at any time through Settings → Privacy → Change recovery passphrase. The flow:

1. The user enters their current passphrase to authenticate the change.
2. The app generates a new passphrase via Section 4's generation flow.
3. The app re-derives the master key with the new passphrase.
4. The app re-encrypts the local data with the new key and uploads the new encrypted blob to Firestore, replacing the old one.
5. The old key is dropped from memory.

The passphrase change is atomic: either the new blob is uploaded and the old one is replaced in a single transaction, or the change rolls back and the old passphrase remains valid. There is no in-between state.

## 5. The sync protocol against Firestore

> Cuizine uses Firestore as an opaque blob store, not as a queryable database. The data going into Firestore is already encrypted; Firestore's role is storage and replication, not querying or processing. This is the reverse of how most apps use Firestore, and it's load-bearing for the trust posture: Firestore's security rules become irrelevant because the data is meaningless to anyone who reads it.

### The Firestore document layout

Every signed-in Cuizine user has a single document in a collection structured as:

```
/cuizine_users/{firebase_auth_uid}
```

The document fields are:

- `salt` — base64-encoded 16-byte KDF salt (not secret)
- `wordlist_version` — the version of the recovery wordlist used to generate the user's passphrase
- `encryption_version` — the version byte of the encryption envelope (v1 = 1)
- `last_uploaded_at` — ISO 8601 timestamp of when the encrypted blob was last updated
- `device_id_last_writer` — opaque identifier of the device that last wrote the blob (used by the conflict resolution policy in Section 6)
- `encrypted_blob` — the base64-encoded encrypted data, structured per Section 3's per-blob envelope

The document is per-user, not per-device. All of a user's devices read from and write to the same document. There is no per-device document, no shared collection, no cross-user references.

Every field above is **non-semantic by design** — salt, version integers, timestamps, an opaque device identifier, and the opaque encrypted blob. None of them reveals anything about the user's constraints, conditions, household, or preferences. This is a hard rule: nothing semantic is ever added to this document in plaintext, however convenient it would be for a query. The complete, canonical inventory of what plaintext metadata the backend holds (this document plus the Firebase Auth identity) and why each field is unavoidable lives in `security-and-privacy.md` Section 4 ("What plaintext metadata the backend necessarily holds"); that list is authoritative and this layout must never exceed it.

Firestore security rules restrict reads and writes to the authenticated user's own document only:

```
match /cuizine_users/{userId} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
}
```

These rules are necessary but not sufficient — the real protection is that the data is encrypted before it ever reaches Firestore.

### The encrypted blob shape

The decrypted contents of `encrypted_blob` are a structured JSON document containing the user's encrypted-sync tables from `data-model.md` Section 9:

```typescript
interface DecryptedSyncContainer {
  schema_version: number;
  exported_at: string;                    // ISO 8601 UTC
  source_device_id: string;
  
  profiles: ProfileRow[];
  constraints: ConstraintRow[];
  food_data_cache: FoodDataCacheRow[];
  suggestions: SuggestionRow[];
  cooked_meals: CookedMealRow[];
  pantry_items: PantryItemRow[];
}
```

Each row array contains the full row data as it exists in the local SQLite database, with all JSON columns expanded into nested structures (so the sync container is self-describing and doesn't depend on the recipient knowing how to parse the JSON column conventions). The whole structure is serialized to JSON, encrypted with the master key, base64-encoded, and uploaded as the `encrypted_blob` field.

### The sync lifecycle

Cuizine's sync runs in three phases that compose into the full sync cycle. Each phase is bounded, retryable, and rolls back cleanly on failure.

**Phase 1: Pull.** The app reads the user's Firestore document. If `last_uploaded_at` is newer than the local "last successful pull" timestamp, there are remote changes to apply. The encrypted blob is downloaded, decrypted with the master key, and parsed into a `DecryptedSyncContainer` structure. If decryption fails (corrupted blob, wrong key, version mismatch), the pull aborts and surfaces an alert to the user.

**Phase 2: Merge.** The decrypted remote container is merged with the local SQLite state per the conflict resolution policy in Section 6. The merge produces a new local state and a list of merge events that are logged to the event log for audit purposes. If the merge encounters an unresolvable conflict, it surfaces the conflict to the user and pauses the sync.

**Phase 3: Push.** After a successful merge (or after any local write that needs to be reflected), the app serializes the current local state into a new `DecryptedSyncContainer`, encrypts it, and uploads it to Firestore. The upload is conditional on the `device_id_last_writer` field — the app reads the field before writing to detect concurrent writes from another device, and if a concurrent write has happened since the pull, the cycle restarts from Phase 1. This is an optimistic concurrency control pattern.

### Sync trigger conditions

Sync runs in response to specific triggers, not on a fixed schedule. The triggers:

- **App foreground after a period of background**: if the app has been backgrounded for more than 5 minutes, sync runs on next foreground (pull → merge → push if needed).
- **User-initiated action**: any constraint write, suggestion accept, suggestion reject, or contextual state change schedules a sync within a few seconds (debounced — multiple writes in quick succession trigger only one sync).
- **Periodic background**: if the app is in the foreground but idle, a sync runs every 15 minutes to pull any updates from other devices.
- **Manual refresh**: the user can pull-to-refresh on certain surfaces (constraint list, history) to force an immediate sync.

Sync **never** runs:

- When the device is offline (the operation queues until network returns).
- When the device is in low-power mode and the operation is not user-initiated.
- When the user is in the middle of a constraint conversation flow (sync is paused until the conversation completes to avoid mid-conversation state changes).

### Sync failure modes

- **Network unreachable**: the operation queues. The user sees no error; the next sync trigger picks it up. If the queue grows beyond reasonable size (e.g., the user has been offline for days), the app surfaces a calm "you've been offline for a while — your changes will sync when you're back online" indicator.
- **Firestore quota exceeded**: rare in practice but possible at scale. The app surfaces a calm error and retries with exponential backoff.
- **Authentication expired**: the app prompts the user to sign in again.
- **Decryption failure on pull**: the app surfaces an alert and offers the recovery flow from Section 4.
- **Merge conflict that cannot be auto-resolved**: surfaced to the user per Section 6.
- **Upload conflict (another device wrote concurrently)**: the cycle restarts from Phase 1 automatically. If this happens repeatedly (more than 3 cycles in a row), the app pauses sync and logs a severity-one event for review.

## 6. Multi-device conflict resolution

> The same user editing their constraint graph on two devices while offline is a real case (Sukhi at home on her phone, Sukhi at work on her tablet, both with patchy connectivity). When both come back online, the merge has to decide which version of each row wins, and the policy must be deterministic, predictable, and respectful of what the user actually did.

### The default policy: last-write-wins per row, scoped to the row's `modified_at`

For most rows in most situations, the device with the more recent `modified_at` timestamp wins. This is simple, deterministic, and matches user intuition — the most recent edit reflects the most recent intent.

The merge runs row-by-row across the encrypted-sync tables. For each row identified by primary key:

1. **Row exists only locally**: keep local. Push to Firestore on next phase.
2. **Row exists only remotely**: insert remote into local.
3. **Row exists in both, identical**: no change.
4. **Row exists in both, different**: compare `modified_at`. The newer one wins. The losing version is logged to the event log for audit.

This works cleanly for most tables — `food_data_cache` entries have stable canonical content and rarely conflict; `suggestions` and `cooked_meals` are append-mostly and rarely modified after creation; `pantry_items` are simple enough that last-write-wins is correct.

### The exception: constraint graph rows

For the `constraints` table, last-write-wins is **not always correct**. Two devices editing the same constraint at the same time (e.g., Sukhi adjusting the severity of her IBS constraint on her phone while her tablet auto-updates the modification history with a contextual scope change) can produce a merge that loses real user intent.

The policy for constraint rows is **last-write-wins on the structural fields, append-only merge on the `provenance_json.modification_history` array.** Specifically:

- The flattened columns (`type`, `severity`, `temporal_scope_kind`, `contextual_scope_flags`, etc.) and the `scope_json` and `payload_json` fields use last-write-wins.
- The `provenance_json.modification_history` array is merged: both versions' history entries are unioned (deduplicated by `at` timestamp + `source` + `reason`), sorted by `at` timestamp, and the merged history is preserved on the winning row.

This means even when one version of a constraint loses the structural fight, its history is preserved in the merged provenance — the audit trail captures both edits even when only one of them shapes the final structure.

### The deeper exception: a real semantic conflict

If two devices added *different* constraints with the same `id` (which should be impossible because IDs are client-generated UUIDs, but defensive code is correct here) or if the merge produces a structurally invalid state (e.g., a constraint that fails the disallowed-combinations check from `constraint-engine-spec.md` Section 4), the merge **cannot** resolve automatically.

In this case:

1. The sync pauses.
2. The conflict is logged to the event log as `severity_one` (a real architectural issue worth founder review).
3. The app surfaces a calm message to the user: "Your devices have conflicting versions of one of your constraints. Could you tell me which one is correct?" with the two versions presented side-by-side.
4. The user picks one. The chosen version is written to local state and pushed to Firestore.

Surfacing a conflict to the user is a last resort. The expectation is that this almost never happens in practice — the primary key collision case is impossible by construction, and the structurally-invalid case requires a specific ordering of edits that's unlikely in normal use.

### Tombstones for deleted rows

Soft-deleted rows (those with `removed_at` set) are included in the sync container so deletions propagate across devices. A row with `removed_at` set on one device must remain deleted when synced to another device. The merge treats `removed_at` as part of the row state — last-write-wins applies, but a row whose newer version has `removed_at IS NOT NULL` stays deleted.

This means a user who deletes a constraint on their phone and then adds the same constraint back on their tablet will see the addition (the tablet's version is newer and has `removed_at IS NULL`). The deletion is preserved in the modification history for audit purposes.

### Why not CRDTs (the substantive version)

CRDTs (conflict-free replicated data types) are the principled answer to multi-device merging — they guarantee that any two replicas, given the same set of operations applied in any order, will converge to the same final state without ever needing to surface a conflict to the user. The convergence comes from designing operations to be mathematically commutative and associative: adding to a set, removing from a set, incrementing a counter, all of these can be encoded so that the merge result is deterministic regardless of order. This is a real and beautiful property.

Cuizine considered CRDTs carefully and rejected them for v1. The decision deserves a substantive explanation rather than a brief dismissal, because the decision is revisitable and the conditions under which we'd revisit matter.

**The frequency of true concurrent edit conflicts in Cuizine's actual usage pattern is very low.** This is the most important factor and the one most easily overlooked. CRDTs really earn their keep in apps where multiple users actively co-edit the same data all the time — collaborative documents like Notion or Linear, where two humans typing in the same prose at the same time is the *normal* case, not the edge case. Cuizine's usage pattern is fundamentally different:

- **In v1**, every user has a single profile they edit alone. The only multi-device case is the same human switching between their phone and tablet. The realistic "conflict" scenario is "Sukhi adds a constraint on her phone in the kitchen, then later opens her tablet on the couch" — by which time the phone's write has already synced to Firestore. The narrow window for a real concurrent edit requires Sukhi to be offline on both devices simultaneously and to edit the same row on both, and even then she is one person making one set of decisions, so the conflicts are usually "I changed my mind twice in two places" rather than "two people disagree about a constraint."
- **In v2**, the household feature introduces multiple profiles per account, but they're still all controlled by one person (Sukhi managing Beeji's profile, Simran's profile, etc.). One user, one set of intents, multiple profiles — still not the concurrent-editing-by-different-humans case.
- **In v3**, the linked partner feature is the first time two genuinely different humans edit related data on different accounts. But the data being edited is per-profile, not shared — Sukhi edits her profile, Harpreet edits his linked profile, and the household active set is *computed at query time* from individual profile graphs rather than stored as a single object both can edit. The cross-account merge surface is "what does Sukhi see of Harpreet's profile" rather than "Sukhi and Harpreet both edited the same row."

So the realistic frequency of true concurrent edit conflicts is very low across all three versions. The conflict resolution policy in this section will surface a side-by-side resolution UI in those rare cases, and that UX is acceptable because it happens rarely.

**The audit trail Cuizine needs is incompatible with how CRDTs naturally produce history.** This is the second deepest reason. `constraint-engine-spec.md` Section 9 commits the engine to a structured, human-readable record of how every constraint came to exist — `source`, `added_at`, `original_phrasing`, `modification_history`. This is what makes the disclosure UX possible ("you told me on day 1 that you can't have onions or garlic"), what makes alpha feedback review tractable, and what makes the conflict resolution policy from ADR 0009 work mechanically.

CRDTs don't naturally produce this kind of audit trail. They produce *operation logs* — records of every CRDT op applied to converge replicas — but those logs are structured for the convergence algorithm, not for human comprehension. Translating from a CRDT operation log to "here are the human-meaningful changes to this constraint" is its own engineering problem, and the result is essentially what the `modification_history` field in `provenance_json` already gives us with last-write-wins. We'd be paying CRDT complexity costs to get back to roughly the same audit shape.

**The implementation, storage, and debugging costs are real.** CRDT libraries for Kotlin/JVM exist (Automerge has JVM bindings and there are Kotlin-native CRDT implementations) but are less battle-tested than the manual merge approach. CRDT-tracked rows often carry per-row metadata several times the size of the row itself; while compaction helps, it doesn't eliminate the overhead. And the debugging surface is meaningfully harder — when something goes wrong with a CRDT-backed sync, the failure mode is usually "the merge converged but the result is surprising" rather than "the merge failed with a clear error," and tracing back through operation logs is much harder than tracing back through last-write-wins decisions. For an alpha where the founder needs to debug user-reported issues quickly, the simpler model is more valuable.

**The asymmetric cost of being wrong matters.** If we ship v1 with last-write-wins and conflicts turn out to be more common than expected, the cost is some users seeing the side-by-side conflict resolution UI occasionally, plus some severity-one events in alpha logs that the founder reviews. None of these are catastrophic — they're recoverable, they're visible, and they generate exactly the signal we'd need to decide whether to add CRDTs. The user experience cost is "an occasional moment of friction," not "data loss" or "inconsistency."

If we ship v1 with CRDTs and they turn out to be unnecessary, the cost is meaningful implementation complexity, larger storage footprint, harder debugging during alpha, and significantly more time spent designing and testing the merge layer that we could have spent on the constraint engine, the agents, or the user experience. Every week spent on CRDT correctness is a week not spent on the core thesis Cuizine is trying to prove.

The asymmetry favors the simpler approach. Being wrong on the side of simplicity is recoverable and instructive. Being wrong on the side of complexity is sunk cost.

**Architectural readiness for adding CRDTs in v2 or v3.** The data layer is structured in a way that makes a CRDT migration tractable if alpha data shows we need it. Every row has a primary key. Every row has a `modified_at`. Every row has versioning. The encryption envelope has a version byte that supports format migration. The migrator from `data-model.md` Section 8 handles upgrades. The specific path to adding CRDTs later: the encryption envelope's version byte bumps, the merge logic in Phase 2 of the sync lifecycle becomes CRDT-aware, the data model adds a per-row CRDT metadata column, and the migrator handles the upgrade. This is real work but it is contained work that lives in a few specific places, not a fundamental rewrite.

**The explicit revisit criteria.** We will revisit the CRDT decision if alpha data shows any of the following:

- The user-facing conflict resolution UI from this section is being triggered more than once per user per month on average
- Severity-one merge events are happening at a rate that affects the founder's ability to triage alpha feedback
- A v2 or v3 feature surfaces a real concurrent-editing pattern that last-write-wins cannot handle gracefully (the most likely candidate is v3's linked partner feature if it ever needs to support shared editable resources rather than per-profile ones)

Until one of those conditions is met, last-write-wins with the constraint-row modification history merging is the right answer for Cuizine. This is not "the easy thing because the hard thing is hard" — it is "the right thing because the hard thing solves a problem we don't have." The architectural readiness pattern means we can change our minds later, with real data in hand, without rebuilding the system.

## 7. The signed-out to signed-in transition

> The most consequential single flow in this document. When a user has been using Cuizine in signed-out mode (local-only, no Firebase, no encrypted sync container) and then chooses to sign in for the first time, the existing local data needs to be encrypted and uploaded to Firestore. Mistakes here can leave the app in a half-encrypted state — some data encrypted-and-uploaded, other data still local-only, sync state unclear — which violates Principle 4 (no silent state) catastrophically. This section specifies the flow step-by-step with rollback semantics.

### What the user sees

The user-facing flow is calm and brief:

1. The user taps "Sign in" in Settings or in the upgrade-to-paid flow.
2. The user authenticates with Sign in with Google. The Google flow runs in its standard form.
3. The user sees the recovery passphrase reveal screen from Section 4 (this is the same flow as a fresh signup — the passphrase is generated, presented, and confirmed regardless of whether there is existing local data).
4. The user sees a calm progress indicator: "Securing your data... this will take a moment." The indicator is honest — actual time depends on the size of the local database, typically 1-5 seconds.
5. The user sees a confirmation: "You're signed in. Your data is now backed up and synced across your devices."

The user does not see the underlying transition steps. They are designed to be invisible and atomic from the user's perspective.

### The transition steps with rollback semantics

The transition runs as a sequence of atomic steps. At every step, if the step fails, the previous state is restored cleanly. There is no point at which a partial transition is allowed to persist — either the transition completes fully or the app remains in signed-out mode with no data lost.

**Step 1: Authenticate with Firebase.** The user signs in with Google through `firebase_auth`. Firebase returns an authenticated session and a Firebase Auth UID. If authentication fails (user cancels, network error, Firebase outage), the app remains signed-out and surfaces a calm error to the user.

**Step 2: Generate the recovery passphrase.** The app generates a fresh six-word passphrase per Section 4, presents it to the user, and waits for the user to confirm via the re-entry or pick-three-of-six step. If the user dismisses the passphrase reveal without confirming, the app rolls back: signs out of Firebase, returns the user to the signed-out state, no data is touched. Sukhi is back where she started, no transition has happened.

**Step 3: Generate the user's salt and create the Firestore document.** The app generates a 16-byte random salt, creates the user's Firestore document at `/cuizine_users/{firebase_auth_uid}` with the salt and metadata fields populated, and leaves the `encrypted_blob` field empty. This is the first write to Firestore; if it fails (network, quota, permission), the app rolls back: signs out, drops the passphrase from memory, returns to signed-out state. The Firestore document creation is its own boundary; either it succeeds and we proceed, or we treat the transition as not having started.

**Step 4: Derive the master encryption key.** The app derives the master key using Argon2id with the passphrase, the salt, and the parameters from Section 3. This step is deterministic and local; it doesn't fail unless the device runs out of memory mid-derivation, in which case the app surfaces an error and rolls back (deletes the Firestore document, signs out, drops the passphrase).

**Step 5: Snapshot the local database.** The app captures a complete snapshot of all encrypted-sync tables (per `data-model.md` Section 9) into a single `DecryptedSyncContainer` structure in memory. The snapshot is read-only — the live database is not yet modified. If the snapshot fails (database I/O error, out of memory), the app rolls back as above.

**Step 6: Encrypt the snapshot.** The app encrypts the serialized `DecryptedSyncContainer` with the master key, producing the per-blob envelope from Section 3. The result is a single byte string ready for upload. If encryption fails (extraordinarily unlikely with a correctly-implemented library, but defensively handled), the app rolls back.

**Step 7: Upload the encrypted blob to Firestore.** The app writes the encrypted blob to the `encrypted_blob` field of the user's Firestore document, along with the `last_uploaded_at`, `device_id_last_writer`, and version metadata fields. This is the moment of truth — once this write succeeds, the user's data is durably backed up and recoverable on a new device. If the upload fails (network, quota, permission), the app rolls back: deletes the Firestore document (the empty one from Step 3), signs out, drops the passphrase, returns to signed-out state.

**Step 8: Mark the local database as signed-in.** The app updates a single record in the `accounts` table (creating the row if needed) with the Firebase Auth UID, the wordlist version, and the `signed_in_at` timestamp. From this point forward, the app is in signed-in mode and the sync layer treats subsequent writes as needing to update the encrypted blob. This step is the only step that modifies the local database, and it happens *after* the Firestore upload has succeeded — so even if everything else fails, the local data is unchanged.

**Step 9: Drop the passphrase from memory.** The passphrase is no longer needed; the master key has been derived and is held in memory for the session. The passphrase is held in a `CharArray` (not a `String`) so it can be explicitly overwritten after use — true zeroization is best-effort on the JVM, since the garbage collector may have moved or copied the value, but using a mutable `CharArray` and clearing it is meaningfully better than relying on an immutable `String`, and we do what we can.

**Step 10: Show the user the success confirmation.** The progress indicator transitions to the success message. The transition is complete.

### What happens if the user kills the app mid-transition

If the user force-quits the app between Steps 1 and 7, the next launch detects the half-state by reading the Firestore document: if the document exists but `encrypted_blob` is null or `last_uploaded_at` is null, the half-state is recognized. The app's recovery logic at launch is:

1. Sign out of Firebase locally (drop the session token).
2. Delete the partial Firestore document (or mark it for cleanup if delete fails).
3. Return to the signed-out state with all local data intact.
4. Surface a calm message on next interaction: "Your sign-in didn't complete last time. Your local data is unchanged. Want to try signing in again?"

This is the architectural expression of Principle 4: there is no half-state that persists across launches. Either sign-in completes or it never happened, from the user's perspective.

### What happens to the local data on a successful transition

Nothing. The local data remains exactly where it was, unchanged in structure. The only difference is that subsequent writes will trigger sync to update the encrypted blob in Firestore. Sign-in is *additive* — it adds a backup, it does not transform the local data.

### The "fresh start vs restore" branch on signed-out devices with existing data

The transition above assumes the user has existing local data they want to keep. But there's a second case: the user is signing in on a new device that already has some local data from a signed-out session, *and* the user's Firebase account already has an encrypted blob from a previous device. This is the "I used Cuizine on my old phone signed in, then I started using Cuizine on my new phone signed out, and now I want to sign in on my new phone" case.

The app detects this case by checking whether the user's Firestore document already has a non-empty `encrypted_blob` field (Step 3 becomes "create or read the document"). If it does, the app surfaces a deliberate choice to the user before proceeding:

1. **"Restore from backup"** — the app downloads the existing encrypted blob, prompts for the recovery passphrase, decrypts, and merges with the local data using the conflict resolution policy from Section 6. Local data is preserved unless it conflicts with restored data, in which case last-write-wins applies. This is the right choice when the new local data is small (a couple of constraints added in signed-out mode that the user wants to keep) and the backup contains the bulk of their accumulated work.
2. **"Start fresh on this device"** — the app proceeds with the transition above, generating a new passphrase and overwriting the existing Firestore blob. The previous backup is irrecoverable from this point. The local data on this device becomes the new source of truth. This is the right choice when the user has decided their old data is no longer relevant.

The two options are presented with full honesty: "You have an existing backup. Restoring it will combine your old data with what you have now. Starting fresh will erase your old backup and start from what's on this device. Which do you want?" There is no default — the user has to pick.

## 8. The signed-in to signed-out transition

The inverse case is less risky but still important to specify. A signed-in user may want to sign out for several reasons: switching to a different account, downgrading to the free tier (which can be either signed-in or signed-out), or simply preferring the local-only trust posture going forward.

### What the user sees

1. The user taps "Sign out" in Settings.
2. The app surfaces a calm confirmation: "Signing out will keep your data on this device but disconnect it from the cloud backup. Your other signed-in devices won't be affected. Continue?"
3. On confirmation, the user sees a brief progress indicator and then a confirmation: "You're signed out. Your data is still here on this device."

### The transition steps

**Step 1: Confirm intent.** The user has explicitly confirmed they want to sign out.

**Step 2: Final sync push.** Before signing out, the app pushes any pending local changes to Firestore so the encrypted blob is current. This protects against data loss if the user later decides to sign in again on the same or a different device. If the push fails (network unavailable), the app surfaces a soft warning: "I couldn't sync your latest changes before signing out. Your local data is safe, but your cloud backup may be slightly behind." The user can choose to retry or proceed anyway.

**Step 3: Drop the master encryption key from memory.** The key is no longer needed; the local database remains accessible through the at-rest Android Keystore encryption (Layer 1 from `data-model.md` Section 9), independent of the master key.

**Step 4: Sign out of Firebase.** The Firebase session token is dropped. The user is no longer authenticated.

**Step 5: Mark the local database as signed-out.** The `accounts` table row is updated to record the sign-out timestamp. The local data is unchanged in content.

**Step 6: Show the success confirmation.**

After sign-out, the local data continues to function exactly as before — Cuizine works fully offline, the Curator and Chef and validator all run normally, no functionality is lost. The only difference is that no further writes are pushed to Firestore, and the encrypted blob in Firestore remains as it was at Step 2.

### What is NOT deleted

Sign-out does not delete the encrypted blob in Firestore. The user's data remains backed up, and signing back in (on the same device or another) restores access via the recovery passphrase flow. This is intentional: sign-out is a *disconnection*, not a *deletion*. Users who want to delete their data entirely use the dedicated "Export and delete" flow from `vision.md`'s trust posture, which is a separate Settings option that explicitly hard-deletes the Firestore document and the local database after exporting.

## 9. Operational concerns

> The boring-but-load-bearing details. Sync layers fail in production for operational reasons more often than for cryptographic ones, and getting these right is what makes the difference between "sync works" and "sync works reliably for thousands of users on flaky networks with low-end devices."

### Firestore quotas and rate limits

Firebase's free tier (Spark plan) for v1 alpha provides generous quotas: 50,000 document reads/day, 20,000 writes/day, 1 GiB of stored data per project. At 15-25 alpha users, the usage should fit comfortably within free-tier limits. The math: each user produces maybe 5-20 sync write operations per day (depending on activity), and a typical encrypted blob is well under 1 MiB. Total reads and writes for the alpha population are roughly 100-500 per day, well below the 20K/50K limits.

For v2 launch (per ADR 0001), the Firebase project upgrades to Blaze (pay-as-you-go) and the cost per user per month is expected to be on the order of cents at typical usage. Cost projections are a v2-planning concern, not a v1 concern.

### Retry policies

Network operations (reads from Firestore, writes to Firestore, blob uploads) follow an exponential backoff retry policy with bounded retries:

- **First retry:** after 1 second
- **Second retry:** after 4 seconds
- **Third retry:** after 16 seconds
- **Fourth retry:** after 60 seconds (capped)
- **Fifth attempt and beyond:** no further retries; surface the failure and queue the operation for the next sync trigger

The retry budget is reset on user-initiated actions. A user tapping "retry" is treated as a fresh request, not a continuation of a failed one.

### Network failure handling

Cuizine treats network failure as a normal operating condition, not an exception. The app must work fully offline at all times. Specific behaviors:

- **Pull failures:** the local data is used as-is. No alert to the user (this is normal). The pull retries on the next sync trigger.
- **Merge failures:** if a merge can't proceed because the remote data couldn't be downloaded, the local data continues to be authoritative until the next successful pull.
- **Push failures:** the operation is queued and retried on the next sync trigger. The user sees no error during normal use; if the queue depth exceeds a threshold (e.g., 10 pending operations), the app surfaces a soft "you've been offline for a while" indicator.

### Battery and metered data considerations

The sync layer respects platform-level signals about device state:

- **Low-power mode (Android):** sync is paused except for user-initiated operations. Background periodic syncs do not run.
- **Doze mode (Android):** sync is paused in the background. The next sync runs when doze ends.
- **Cellular vs Wi-Fi:** by default, the app syncs on both. A future Settings option may let users restrict sync to Wi-Fi only — flagged as a v2 consideration, not a v1 requirement.
- **Background data restrictions:** if the user has disabled background data for Cuizine, the app respects this and only syncs in the foreground.

### Sync interval tuning

The 15-minute periodic background sync interval is a starting point, not a commitment. Alpha measurement may show that 15 minutes is too aggressive (battery drain noticeable) or too conservative (multi-device users see noticeable lag between devices). The interval is tuned based on alpha data and may settle anywhere between 5 minutes and 1 hour.

### Database size considerations

The encrypted sync container's size grows with the user's accumulated data. A user with a typical year of activity may have a few thousand suggestions, a few hundred cooked meals, a couple dozen constraints, and several thousand cache entries. The total uncompressed size is typically 5-20 MiB. After encryption and base64 encoding, the Firestore blob is roughly 7-30 MiB.

Firestore documents have a 1 MiB size limit, so the encrypted blob cannot be stored as a single field once it grows beyond about 750 KiB (allowing for overhead). When this happens, the sync layer **chunks the blob across multiple documents** in a subcollection:

```
/cuizine_users/{firebase_auth_uid}/blob_chunks/{chunk_id}
```

Each chunk holds up to ~750 KiB of the encrypted blob, and the parent document holds metadata about the chunking (number of chunks, total size, content hash for integrity checking). Pull and push operations read/write the chunks in sequence.

The chunking threshold is conservatively set so most v1 alpha users will fit in a single document. v2 launch will surface the chunking case more frequently as users accumulate data; the chunking logic ships in v1 to be ready.

## 10. What is forbidden

> Explicit list of things this layer must never do. Each item below is a behavior that would erode the trust posture or violate one of the core principles. The list exists to prevent silent drift — when the agent or future-us is tempted to "just add this small thing," this list is the discipline that says no.

- **Storing the master encryption key on disk.** Ever. Not in a keychain, not in shared preferences, not in a "secure" file. The key is derived on each session from credentials and passphrase and lives only in memory.
- **Storing the recovery passphrase anywhere except the user's own copy.** Cuizine never persists the passphrase after the initial setup confirmation. Not on the device, not in Firebase, not in logs.
- **Implementing a "forgot passphrase" flow that does anything useful.** A "I lost my passphrase" button can offer the "start fresh" option (Section 4) but it cannot reset, recover, or work around the passphrase. There is no admin override.
- **Logging user content (decrypted) to any persistent log.** Operational telemetry is allowed (per ADR 0011); content telemetry is forbidden. This includes Crashlytics fields, error messages that include constraint details, and any debug output that captures sync container contents.
- **Sending decrypted data over any network connection that isn't the local device's own LLM provider call.** The encryption boundary is the device. Sync to Firestore is encrypted. Period.
- **Allowing schema migrations to silently change which tables are in the encrypted sync container.** Per `data-model.md` Section 9, the table-to-layer mapping is locked, and changes require an ADR.
- **Caching the master key longer than the app session.** When the app is killed, the key is gone. Re-derivation from credentials and passphrase is required on the next launch. We do not implement "remember me" for the master key.
- **Implementing any feature that requires reading user data on Cuizine's servers.** Cloud Functions can route LLM requests; Cloud Functions can hold service credentials; Cloud Functions cannot decrypt user data. Any feature that would require server-side decryption is rejected at the design phase.
- **Auto-uploading event logs.** Per PRD § 5, event logs are local-only. They are exportable by the user on demand but never auto-uploaded.
- **Implementing a master key escrow, backup admin key, or "support recovery" path.** Forever. This is the single most important forbidden item and the one that needs to be defended most actively against well-intentioned future requests.

## 11. Open questions

### Encryption

- **Should we add a per-row encryption layer in addition to the per-blob encryption?** The current design encrypts the entire sync container as a single blob, which means decrypting any data requires decrypting the whole container. This is fine for v1's small data sizes but may become inefficient at v2/v3 scales. A future optimization could encrypt individual rows with a key derived from the master key, allowing partial decryption. Deferred to v2 measurement.
- **Should the IV for each blob upload incorporate a deterministic counter rather than pure randomness?** GCM nonces are safe to randomize, but a deterministic counter would prevent any chance of nonce reuse. The randomization approach is sufficient for our threat model. Revisit if security review surfaces concerns.

### Recovery passphrase UX

- **Should we offer a "memorable phrase" mode** where the user can type their own passphrase rather than accepting the generated one? Convenience for users who already have password managers; risk for users who choose weak phrases. Tentatively no for v1. Revisit if alpha feedback suggests strong demand.
- **Should the wordlist be localized?** A French-speaking user receiving a French wordlist would feel more natural than receiving English words. v1 ships English-only because alpha is Canadian and English is sufficient; v3 global expansion will need localized wordlists. Deferred to v3.
- **Should we offer an alternative non-text recovery mechanism**, like a QR code or downloadable recovery file? Some users find written words awkward but would accept a file they save somewhere. Deferred to alpha feedback.

### Sync protocol

- **What is the right chunking threshold for the encrypted blob?** Section 9 specifies ~750 KiB per chunk, but the optimal value depends on Firestore's actual performance characteristics with our access pattern. Measure during alpha.
- **Should the sync layer support partial pulls** (only pull the rows that have changed since the last sync) rather than pulling the whole encrypted blob? This is a meaningful optimization for v2/v3 but adds complexity. Deferred until measurement justifies it.
- **How does sync interact with the food data cache's TTL refresh logic?** When a cache entry's TTL expires and the engine refetches from USDA or OFF, the new entry is written locally and should propagate via sync. The current design handles this through the normal sync trigger, but the interaction has edge cases (multiple devices refreshing the same entry concurrently) worth testing.

### Multi-device behavior

- **What is the right UX for the "your devices have conflicting versions" surface from Section 6?** The current design says "calm side-by-side resolution UI" but the specific copy and visual treatment are not designed yet. Deferred to UI design.
- **How does the conflict resolution policy interact with severity-zero events** that arise from a merge surfacing a contradiction in constraint data? Currently logged as severity_one; if a real medical-tier conflict surfaces, should it be severity_zero? Probably yes if the contradiction would have caused a constraint violation. Refine during alpha.

### Operational

- **What is the right alpha-period limit on sync retries before alerting the founder?** A user whose sync has failed 50 times in a row is having a real problem and the founder should know. Threshold and notification mechanism are TBD.
- **Should we ship a "sync diagnostics" Settings screen** during alpha so users can see when their last sync was, what state the sync queue is in, and whether the encryption envelope is healthy? Useful for alpha debugging but adds UI surface that doesn't belong in the v2 launch. Tentatively yes for alpha, hide or remove for v2.

## 12. Cross-references

### What this document references

- `vision.md` — for the trust posture and the "export and walk away" commitment
- `PRD.md` — for the alpha context, the user journey, and the event log discipline
- `technical-architecture.md` — for the position of the sync layer in the subsystem map and the dependency on the Tink crypto library
- `data-model.md` — for the tables that get encrypted, the schema versioning model, and the encryption boundary mapping
- `constraint-engine-spec.md` — for the provenance model that the conflict resolution policy preserves
- ADR 0011 — optional backend with capability tiers, the policy this doc operationalizes
- ADR 0012 — food data sources, particularly the cache-as-encrypted-sync property
- ADR 0007 — three-tier subscription, particularly the truly-free local-first base tier that depends on the signed-out path working flawlessly

### What this document defers to deeper-dive docs

- **`security-and-privacy.md`** — the formal threat model, the residual risk analysis, the responsible disclosure policy, the upstream provider data-retention audit
- **`build-conventions.md`** — Firebase project setup, deployment configuration, environment variables, key rotation policies for service credentials (not user keys)
- **`testing-strategy.md`** — the test suite for the sync layer, including encryption round-trip tests, recovery flow tests, transition flow tests with simulated failures, and conflict resolution scenarios
- The prompts and copy directory — the user-facing text for the recovery passphrase reveal screen, the conflict resolution UI, the transition success messages

### What this document does *not* defer (decisions made here)

- Tink as the encryption library
- AES-256-GCM as the cipher
- Argon2id as the KDF, with the v1 parameters specified in Section 3
- The 6-word friendly wordlist passphrase format
- The Firestore single-document-per-user pattern, with subcollection chunking for blobs over 750 KiB
- The three-phase sync lifecycle (pull → merge → push) with optimistic concurrency control
- The last-write-wins conflict resolution policy with append-only modification history merging on the constraints table
- The substantive CRDT analysis and the explicit revisit criteria
- The signed-out to signed-in transition flow with rollback semantics
- The signed-in to signed-out transition flow
- The forbidden behaviors list

### How the agent should use this doc

When the coding agent builds the sync layer, Section 3 (encryption architecture) and Section 4 (recovery passphrase) are the authoritative cryptographic decisions. Section 5 (sync protocol) is the authoritative network protocol. Section 6 (conflict resolution) is the authoritative merge policy. Sections 7-8 (transition flows) must be implemented exactly as specified — these are the flows where "almost right" is wrong, and the rollback semantics must be tested explicitly.

Section 10 (what is forbidden) is the discipline list. The agent must not implement any of the forbidden behaviors regardless of how reasonable they seem in the moment. If a future requirement appears to need one of the forbidden behaviors, that requirement is the signal to stop and ask, not to proceed with the implementation. The trust posture is fragile in exactly the places this list defends.

When the agent encounters a question this document does not answer, the discipline from PRD § 9 applies: check the vision, check the relevant ADR, check the data model doc, ask the founder before guessing. The sync and encryption layer is the part of the system where mistakes have the highest blast radius — they affect every user, they erode trust silently, and they are extraordinarily expensive to fix after launch.

---

*End of `local-first-sync.md` v1 (initial draft). Next revision will incorporate any operational learnings from the v1 alpha, any measurements from real device performance, and any design decisions from `security-and-privacy.md` that surface implementation requirements. The principles in Section 2 are stable; the parameters in Section 3 and the operational policies in Section 9 are expected to evolve based on measurement.*
