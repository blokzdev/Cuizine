# Cuizine — Data Model

> The physical translation. Where `constraint-engine-spec.md` defined what a constraint *is* logically and `agent-architecture.md` defined how agents interact with it, this document specifies the SQLite tables, column types, JSON payload shapes, indices, and versioning that implement those specifications. The goal: concrete enough that two engineers implementing it independently would produce compatible databases, loose enough that reasonable variations are allowed. This is not the Room code — the Room `@Entity` and `@Dao` classes live in the codebase — but it is precise enough that the Room code is a mechanical translation.

## 1. Purpose & how to read this doc

This document specifies the **physical data model for Cuizine v1** with forward-references to v2 and v3 evolution. It assumes you have read `vision.md`, `PRD.md`, `technical-architecture.md`, `constraint-engine-spec.md`, `agent-architecture.md`, and the ADRs. Concepts established there are not re-derived here.

This document defines: the foundational conventions every table follows; the schema for accounts, profiles, and the profile-ownership model from ADR 0004; the schema for the constraint graph (constraints, provenance, modification history) implementing `constraint-engine-spec.md`; the schema for the food data layer (cache, bundle format, entries) implementing ADR 0012; the schema for recipe and history data; the schema for event logs and alpha instrumentation per PRD § 5; the schema versioning and migration strategy; the encryption at-rest and sync boundary; and the indices and performance considerations for the hot-path queries from `constraint-engine-spec.md` and `agent-architecture.md`.

This document does **not** define: the actual Room `@Entity` and `@Dao` definitions and their generated implementations (those live in the codebase under `app/src/main/kotlin/ai/cuizine/data/database/`); the migration *code* (the migrator module itself, versioned in the codebase); the network-level sync protocol (see `local-first-sync.md`); the encryption library choice and key derivation (see `local-first-sync.md`); or the concrete SQL query text for agent operations (the queries live behind the engine API from `constraint-engine-spec.md` Section 6 and are implementation-private).

**When this document and a deeper-dive doc disagree,** this doc wins for table shapes, column types, and index strategy, and the deeper-dive doc wins for network, encryption, and migration runtime behavior. If a conflict arises that cannot be cleanly split, it's a design bug and requires a deliberate decision rather than silent resolution.

## 2. Foundational conventions

> Every table in the data model follows these conventions. Stating them once means I don't have to repeat them in every section, and the agent building the database has a single place to check when in doubt. Conventions are not decorations — they are load-bearing for the migration strategy, the encryption boundary, and the query performance.

### Naming

- **Tables** use snake_case, plural nouns: `profiles`, `constraints`, `food_data_cache`, `event_log` (singular when the meaning is inherently singular, e.g. `event_log` is the log, not `event_logs`).
- **Columns** use snake_case, singular nouns: `profile_id`, `created_at`, `scope_json`.
- **JSON payload field names** inside JSON blobs also use snake_case for consistency, even though JSON conventions often use camelCase.
- **Indices** are named `idx_{table}_{column(s)}` — e.g. `idx_constraints_profile_id`, `idx_constraints_profile_severity`.
- **Foreign key constraint names** are not separately defined; SQLite's implicit naming is used.

### Primary keys

Every table's primary key is a **text column named `id`** containing a UUID (v4) generated client-side at row creation. Reasoning: client-generated UUIDs let Cuizine create rows without a round trip to a server for ID allocation (important for the signed-out-first-launch path), let sync merge work without ID conflicts across devices, and keep the schema uniform. The trade-off (slightly larger storage per row versus integer IDs) is negligible at Cuizine's data volumes.

The one exception: the `accounts` table's primary key is the Firebase Auth UID (also a text column named `id`) when the account is signed-in. Signed-out-only "accounts" don't exist as database rows — signed-out mode uses a single synthetic local profile with no account row.

### Timestamps

All timestamps are stored as **ISO 8601 strings in UTC** (not epoch milliseconds, not SQLite TIMESTAMP types). Reasoning: ISO strings are human-readable during debugging, sort correctly as text for time-range queries, survive timezone changes across sync, and are unambiguous when inspected in the raw database. The per-row cost of text-over-integer is small and the debugging benefit is real. Every table that has temporal data includes `created_at` and `modified_at` columns with this format.

The user's own local timezone is stored separately on the profile (not applied to the stored timestamps). Timezone-sensitive queries (e.g., "what meals did Sukhi cook today in her local time") convert on read, not on write.

### JSON columns

Per the hybrid strategy, some columns hold structured JSON text. JSON columns always end in `_json` as a suffix (e.g., `scope_json`, `provenance_json`, `payload_json`). The column type is SQLite's TEXT. Room handles JSON through `@TypeConverter`s that serialize from and deserialize to Kotlin data classes (via kotlinx.serialization) at the query boundary.

The discipline: **JSON columns never hold hot-path query targets**. If the engine needs to query on a field efficiently, that field lives in its own column. JSON is for fields that are loaded-and-used-as-a-whole.

### The `schema_version` field

Every row that represents user data — profiles, constraints, cache entries, recipes, event log records — includes a `schema_version` integer column. v1 ships with `schema_version = 1` on every row. When migrations run, they update the `schema_version` on the rows they migrate. This lets the engine mix rows from different schema versions during a gradual migration without corrupting data.

At the database level, there is *also* a single-row `schema_metadata` table holding the current global schema version. The per-row `schema_version` is used for forward-compatible reads (if a row has version 3 and the code expects version 2, the code knows to call the migrator); the global `schema_metadata.current_version` is used to gate writes (writes always happen at the current code's version).

### Soft deletes

Rows representing user data are **never hard-deleted** in v1. Deletion is implemented as a `removed_at` timestamp column set to the deletion time. Per `constraint-engine-spec.md` Section 9, this is required for the provenance and audit trail — a removed constraint still needs to be explainable if the user asks "why did you suggest that meal last week, when I had told you to remove that constraint?" Soft deletion is the mechanism that preserves that history.

Event log rows and cache entries are the two exceptions: event log rows have their own TTL-based cleanup policy (Section 7), and cache entries can be evicted to free space (Section 5). Profile data, constraint data, recipe data, and history data are soft-deleted only.

Hard deletion happens only as part of the "export and walk away" flow from `vision.md` trust posture — when a user exports their data and explicitly deletes their account, the hard delete is triggered and runs through a dedicated cleanup path that removes everything.

### JSON payload schema documentation

JSON blob contents are specified in this doc as typed TypeScript-like interfaces for clarity. The Kotlin implementation will use `@Serializable` Kotlin data classes mirroring these type definitions, serialized via kotlinx.serialization. When a JSON payload's shape changes across schema versions, the change is documented in the migration entry for that version.

## 3. Core entities: accounts, profiles, and the profile-ownership model

> The top of the data model. Implements ADR 0004's profile-as-first-class entity and ADR 0011's account-owns-profiles relationship. In v1 every account has exactly one profile (self); the schema is designed for v2's multi-profile households and v3's linked partners to slot in as additional rows rather than schema changes.

### Table: `accounts`

Holds the Firebase Auth identity layer per ADR 0011. A row in this table exists if and only if the user has signed in. Signed-out users have no row here.

```
CREATE TABLE accounts (
  id TEXT PRIMARY KEY,                    -- Firebase Auth UID
  email TEXT,                             -- from Firebase, may be null for some providers
  display_name TEXT,                      -- from Firebase, may be null
  provider TEXT NOT NULL,                 -- 'google' (v1), 'email' (v2), etc.
  created_at TEXT NOT NULL,               -- ISO 8601 UTC
  modified_at TEXT NOT NULL,              -- ISO 8601 UTC
  schema_version INTEGER NOT NULL DEFAULT 1
);
```

**Notes.**
- There is no password, no password hash, no auth token stored locally. Auth is fully delegated to Firebase Auth and the device holds only the Firebase session token managed by the `firebase_auth` package.
- The `provider` column is a discriminator for which sign-in method produced this account. v1 ships only `'google'`.
- The `accounts` table is **not** part of the encrypted sync container — account metadata is already known to Firebase (the sync source) and encrypting it locally while Firebase holds it in plaintext adds no protection.

### Table: `profiles`

The first-class entity from ADR 0004. Every constraint graph belongs to exactly one profile; every profile has exactly one constraint graph.

```
CREATE TABLE profiles (
  id TEXT PRIMARY KEY,                    -- UUID v4
  account_id TEXT,                        -- FK to accounts.id, nullable for signed-out local profile
  display_name TEXT NOT NULL,             -- "Myself", "Beeji", "Simran", etc.
  relationship_type TEXT NOT NULL,        -- 'self', 'silent_dependent', 'consent_aware_dependent', 'adult_dependent', 'linked_partner_v3'
  owner_account_id TEXT,                  -- FK to accounts.id; the account that controls this profile (v2+)
  linked_account_id TEXT,                 -- FK to accounts.id; the account this profile belongs to if linked (v3 only)
  cultural_context_json TEXT,             -- JSON blob: cuisine of origin, regional patterns, etc.
  cooking_for_json TEXT,                  -- JSON blob: household size, dietary mix, etc.
  timezone TEXT NOT NULL,                 -- e.g., 'America/Toronto' (IANA format)
  created_at TEXT NOT NULL,
  modified_at TEXT NOT NULL,
  removed_at TEXT,                        -- soft delete
  schema_version INTEGER NOT NULL DEFAULT 1,
  
  FOREIGN KEY (account_id) REFERENCES accounts(id),
  FOREIGN KEY (owner_account_id) REFERENCES accounts(id),
  FOREIGN KEY (linked_account_id) REFERENCES accounts(id)
);

CREATE INDEX idx_profiles_account_id ON profiles(account_id) WHERE removed_at IS NULL;
CREATE INDEX idx_profiles_owner ON profiles(owner_account_id) WHERE removed_at IS NULL;
```

**Notes.**
- The distinction between `account_id`, `owner_account_id`, and `linked_account_id` implements the three relationship types from ADR 0004. In v1, every profile has `account_id = owner_account_id` and `linked_account_id = null` (the user owns their own profile). In v2, dependent profiles have `account_id = null` (no account of their own), `owner_account_id = Sukhi's account` (she controls the profile), and `linked_account_id = null`. In v3, linked partners have their own `account_id` (they're a real account), and the relationship profile row stored in Sukhi's database has `owner_account_id = Sukhi` and `linked_account_id = Harpreet's account`.
- `cultural_context_json` and `cooking_for_json` are the profile-level fields captured during the constraint conversation per PRD § 4 and pillar 5 (ADR 0002). These are NOT part of the constraint graph itself — they are profile-level metadata that the Chef agent reads as cultural input alongside the constraints.
- `timezone` is stored as an IANA timezone string (not a UTC offset) because offsets change around daylight-saving boundaries. The user's timezone can be updated through a contextual constraint (per `constraint-engine-spec.md`) but the default lives on the profile.
- The profiles table **is** part of the encrypted sync container for signed-in users. Profile data is user-sensitive (cultural context, household composition).

### JSON payload: `cultural_context_json`

```typescript
interface CulturalContext {
  cuisine_origins: string[];           // e.g., ["Punjabi", "North Indian"]
  regional_markers: string[];          // e.g., ["Brampton-area", "Canadian immigrant"]
  household_traditions: string[];      // e.g., ["Tuesday vegetarian", "Diwali sweets"]
  ingredient_vocabulary_hints: string[]; // e.g., ["uses atta", "uses common dals", "uses ghee"]
}
```

### JSON payload: `cooking_for_json`

```typescript
interface CookingFor {
  household_size: number;
  household_composition_notes: string;  // free-text summary, e.g., "husband, teen daughter vegetarian, mother-in-law traditional"
  dietary_mix_summary: string;          // free-text, e.g., "mixed — one vegetarian, one traditional omnivore"
}
```

## 4. The constraint graph: constraints and their provenance

> Implements `constraint-engine-spec.md` Sections 3-4 (constraint types and severity tiers), Section 5 (scope dimensions), and Section 9 (provenance). The heart of the data model.

### Table: `constraints`

Per the hybrid strategy from the foundational conventions: frequently-queried fields are flattened to columns with indices; rarely-queried or loaded-as-a-whole fields are stored as JSON.

```
CREATE TABLE constraints (
  id TEXT PRIMARY KEY,                    -- UUID v4
  profile_id TEXT NOT NULL,               -- FK to profiles.id
  type TEXT NOT NULL,                     -- 'avoid', 'prefer', 'require', 'limit', 'contextual'
  severity TEXT NOT NULL,                 -- 'inviolable', 'medical', 'religious_cultural', 'preference'
  
  -- Flattened scope fields for hot-path queries
  temporal_scope_kind TEXT NOT NULL,      -- 'always', 'weekly', 'daily_window', 'date_bounded', 'phase_bounded', 'composite'
  contextual_scope_flags TEXT,            -- JSON array of flag names this constraint depends on
  location_scope_country TEXT,            -- NULL means 'anywhere'
  household_scope_mode TEXT NOT NULL,     -- 'self', 'specific_profiles', 'whole_household'
  
  -- JSON blobs for the full structured data
  scope_json TEXT NOT NULL,               -- complete scope object (all five dimensions with full structure)
  payload_json TEXT NOT NULL,             -- type-specific payload (targets, exceptions, ceilings, etc.)
  provenance_json TEXT NOT NULL,          -- full provenance record
  
  -- Temporal metadata
  created_at TEXT NOT NULL,
  modified_at TEXT NOT NULL,
  removed_at TEXT,                        -- soft delete
  expires_at TEXT,                        -- for contextual constraints with TTL
  
  schema_version INTEGER NOT NULL DEFAULT 1,
  
  FOREIGN KEY (profile_id) REFERENCES profiles(id),
  CHECK (type IN ('avoid', 'prefer', 'require', 'limit', 'contextual')),
  CHECK (severity IN ('inviolable', 'medical', 'religious_cultural', 'preference')),
  CHECK (temporal_scope_kind IN ('always', 'weekly', 'daily_window', 'date_bounded', 'phase_bounded', 'composite')),
  CHECK (household_scope_mode IN ('self', 'specific_profiles', 'whole_household'))
);

CREATE INDEX idx_constraints_profile_active ON constraints(profile_id, type, severity) WHERE removed_at IS NULL;
CREATE INDEX idx_constraints_profile_temporal ON constraints(profile_id, temporal_scope_kind) WHERE removed_at IS NULL;
CREATE INDEX idx_constraints_expires_at ON constraints(expires_at) WHERE removed_at IS NULL AND expires_at IS NOT NULL;
```

**Notes.**
- The flattened `temporal_scope_kind`, `household_scope_mode`, and `location_scope_country` columns exist because the active-set computation (`constraint-engine-spec.md` Section 5) filters heavily on these dimensions. They duplicate information in `scope_json` by design — the flat columns are for query efficiency, the JSON is for loading the full structured scope.
- `contextual_scope_flags` is a JSON array (stored as text) rather than a separate table because the cardinality is small (typically 0-3 flags per constraint) and the engine loads them all when evaluating scope. A separate `constraint_contextual_dependencies` table would be more normalized but slower for the hot path.
- `expires_at` is indexed because contextual constraints with TTLs need to be filtered by expiration on every active-set computation. The partial index (only where `expires_at IS NOT NULL`) keeps the index small.
- `payload_json` schemas are specified by `type` — each of the five constraint types has a distinct payload shape. The discriminator is the `type` column; deserialization picks the right Kotlin data class (a sealed-class subtype) at read time.
- The disallowed combinations from `constraint-engine-spec.md` Section 4 (prefer at Medical severity, contextual at Inviolable, soft limit at Inviolable) are enforced in the engine API at write time, not via SQLite CHECK constraints, because CHECK constraints cannot reason about the JSON payload fields.

### JSON payload schemas by type

Each constraint's `payload_json` has a type-specific shape. All five are specified below.

**`avoid` payload:**
```typescript
interface AvoidPayload {
  target: {
    kind: 'ingredient' | 'category' | 'nutritional_property';
    value: string;                   // ingredient name, category name, or property name
    reference: string | null;        // canonical Food Data Provider entry ID if resolved
  };
  exceptions: Array<{
    kind: 'ingredient' | 'category';
    value: string;
    reference: string | null;
    reason: string;                  // why this is an exception, e.g., "Sukhi tolerates ghee specifically"
  }>;
}
```

**`prefer` payload:**
```typescript
interface PreferPayload {
  target: {
    kind: 'ingredient' | 'cuisine' | 'dish' | 'technique';
    value: string;
  };
  strength: 'low' | 'medium' | 'high';
}
```

**`require` payload:**
```typescript
interface RequirePayload {
  target: {
    kind: 'ingredient' | 'category' | 'nutritional_property';
    value: string;
    threshold: {                    // optional, for nutritional properties
      value: number;
      unit: string;                  // 'g', 'mg', 'kcal', etc.
    } | null;
  };
  window: 'single_meal' | 'daily' | 'weekly';
}
```

**`limit` payload:**
```typescript
interface LimitPayload {
  target: {
    kind: 'nutritional_property' | 'ingredient';
    value: string;
  };
  ceiling: {
    value: number;
    unit: string;
  };
  window: 'per_meal' | 'per_day' | 'per_week';
  hard_or_soft: 'hard' | 'soft';
}
```

**`contextual` payload:**
```typescript
interface ContextualPayload {
  state: {
    flag: string;                    // 'ibs_flare', 'traveling', 'blood_sugar_elevated', etc.
    value: string | number | null;   // optional, for quantitative contextual states
    unit: string | null;             // e.g., 'mmol/L' for blood sugar
  };
  triggers: string[];                // optional list of other constraint IDs this state activates
}
```

### JSON payload: `scope_json`

The full structured scope for a constraint, containing all five dimensions. The flattened columns in `constraints` duplicate information from here for query efficiency.

```typescript
interface ConstraintScope {
  temporal: {
    kind: 'always' | 'weekly' | 'daily_window' | 'date_bounded' | 'phase_bounded' | 'composite';
    weekdays?: string[];             // for 'weekly'
    daily_window_start?: string;     // HH:MM:SS local, for 'daily_window' and 'composite'
    daily_window_end?: string;       // HH:MM:SS local
    daily_window_basis?: 'clock' | 'solar_sunrise' | 'solar_sunset'; // for religious fasts tied to sunrise/sunset
    date_range_start?: string;       // ISO date, for 'date_bounded' and 'composite'
    date_range_end?: string;         // ISO date
    phase_ref?: string;              // reference to a profile-level phase, for 'phase_bounded'
    composite_operator?: 'and' | 'or'; // for 'composite'
    composite_parts?: object[];      // nested scope fragments, for 'composite'
  };
  contextual: {
    required_flags: string[];        // must all be active
    excluded_flags: string[];        // must all be inactive
  };
  location: {
    country: string | null;          // NULL = anywhere
    region: string | null;
  };
  household: {
    mode: 'self' | 'specific_profiles' | 'whole_household';
    profile_ids: string[] | null;    // populated when mode = 'specific_profiles'
  };
  profile: {
    profile_id: string;              // always equals the owning profile in v1
  };
}
```

### JSON payload: `provenance_json`

Per `constraint-engine-spec.md` Section 9.

```typescript
interface ProvenanceRecord {
  source: 'user_direct' | 'curator_inferred' | 'ai_derived_low_confidence' | 'bundle_default' | 'user_edited';
  added_at: string;                  // ISO 8601 UTC
  added_context: {
    flow: 'constraint_conversation' | 'free_text_update' | 'rejection_feedback' | 'settings_edit' | 'bundle_seed';
    question_index?: number;         // for constraint_conversation
    triggering_message_excerpt?: string; // for free_text_update
    rejected_suggestion_id?: string; // for rejection_feedback
  };
  original_phrasing: string | null;  // verbatim user text if applicable
  confidence: 'high_bundle' | 'high_usda' | 'high_off' | 'low_ai' | 'high_user_verified' | 'high_user_direct';
  modification_history: Array<{
    at: string;                      // ISO 8601 UTC
    source: string;                  // same enum as source above
    reason: string;                  // human-readable change description
    previous_payload_json: string;   // snapshot of the payload before this change
    previous_scope_json: string;     // snapshot of the scope before this change
  }>;
}
```

## 5. The food data layer: cache, bundle, and entries

> Implements ADR 0012's three-layer architecture (cache → curated bundle → external sources). The bundle ships with the app binary rather than in the database; the cache lives in the database; external source entries that get cached are written to the cache table with appropriate confidence tags.

### Table: `food_data_cache`

Per-user, per-device cache of every ingredient lookup that has happened on this device. Part of the encrypted sync container (per ADR 0011 and ADR 0012) so a signed-in user's cache is replicated across their devices.

```
CREATE TABLE food_data_cache (
  id TEXT PRIMARY KEY,                    -- UUID v4
  lookup_key TEXT NOT NULL,               -- normalized ingredient name or barcode
  lookup_kind TEXT NOT NULL,              -- 'ingredient_name', 'barcode', 'category'
  canonical_entry_json TEXT NOT NULL,     -- the full canonical entry from the source
  confidence TEXT NOT NULL,               -- matches ProvenanceRecord.confidence enum
  source_layer TEXT NOT NULL,             -- 'bundle', 'usda', 'open_food_facts', 'ai_fallback', 'user_verified'
  first_cached_at TEXT NOT NULL,
  last_refreshed_at TEXT NOT NULL,
  ttl_expires_at TEXT,                    -- NULL = never expires (bundle, user-verified)
  
  schema_version INTEGER NOT NULL DEFAULT 1,
  
  CHECK (lookup_kind IN ('ingredient_name', 'barcode', 'category')),
  CHECK (source_layer IN ('bundle', 'usda', 'open_food_facts', 'ai_fallback', 'user_verified'))
);

CREATE UNIQUE INDEX idx_food_cache_lookup ON food_data_cache(lookup_key, lookup_kind);
CREATE INDEX idx_food_cache_ttl ON food_data_cache(ttl_expires_at) WHERE ttl_expires_at IS NOT NULL;
```

**Notes.**
- `lookup_key` is a *normalized* form (lowercase, whitespace-stripped, accent-folded) so lookups hit the cache regardless of how the user wrote the ingredient name. The unique index on `(lookup_key, lookup_kind)` enforces one canonical entry per normalized form.
- `ttl_expires_at` matches the TTL from ADR 0012: USDA entries get 6 months, OFF entries 3 months, bundle and user-verified entries never expire (NULL).
- The `canonical_entry_json` column holds the full structured ingredient entry including categories, allergens, nutritional composition, and any source-specific metadata. The shape of this JSON depends on `source_layer` but has common fields across all sources — specified below.

### JSON payload: `canonical_entry_json`

```typescript
interface CanonicalIngredientEntry {
  display_name: string;              // preferred display name, localized if applicable
  alternative_names: string[];       // aliases, synonyms, translations
  categories: string[];              // e.g., ['allium', 'vegetable', 'fodmap_high', 'allergen_none']
  allergens: string[];               // e.g., ['peanut', 'dairy', 'gluten']
  religious_tags: string[];          // e.g., ['contains_pork', 'contains_alcohol', 'non_halal']
  nutritional_composition: {         // per 100g, when available
    calories_kcal?: number;
    protein_g?: number;
    carbohydrates_g?: number;
    fat_g?: number;
    fiber_g?: number;
    sodium_mg?: number;
    potassium_mg?: number;
    phosphorus_mg?: number;
    // additional fields as the food data layer evolves
  };
  source_metadata: {
    source: 'bundle' | 'usda' | 'open_food_facts' | 'ai_fallback' | 'user_verified';
    source_entry_id: string | null;  // e.g., USDA FDC ID, OFF barcode
    retrieved_at: string;            // ISO 8601 UTC
  };
}
```

### The curated bundle (not in SQLite)

The curated bundle from ADR 0012 is **not** a SQLite table. It ships as a structured JSON file bundled with the Android app binary at `assets/food_data_bundle/v1.json` (packaged under the app's `assets/` or `res/raw/`). The bundle is loaded into memory on first app launch and consulted as Layer 2 of the three-layer architecture. The reason it isn't a table: the bundle is immutable across a given app version (updated only through app updates), and treating it as a queryable memory structure is faster and simpler than a read-only table.

The bundle file structure:

```typescript
interface CuratedBundle {
  version: string;                   // e.g., "v1.0.0"
  authored_at: string;
  entries: CanonicalIngredientEntry[];
  corrections: Array<{               // corrections that override external sources
    lookup_key: string;
    lookup_kind: 'ingredient_name' | 'barcode';
    entry: CanonicalIngredientEntry;
    reason: string;                  // why this correction exists
  }>;
}
```

The `corrections` field is the "corrections-take-precedence" mechanism from ADR 0012 — entries in this list override external source data for the matching `lookup_key`.

## 6. Recipe and history data: suggestions, meals cooked, rejection feedback

> The tables holding Sukhi's accumulated product experience. These support the Chef agent's "learn to be Sukhi's chef" behavior over time, the rejection feedback loop from PRD § 4, and the Observer agent's long-term analysis in v3.

### Table: `suggestions`

Every meal suggestion the Chef (or Planner in v2) has generated for the user, regardless of whether the user accepted it.

```
CREATE TABLE suggestions (
  id TEXT PRIMARY KEY,                    -- UUID v4
  profile_id TEXT NOT NULL,               -- FK to profiles.id
  generated_at TEXT NOT NULL,
  generated_by_agent TEXT NOT NULL,       -- 'chef', 'planner' (v2), 'planner_via_chef' (v2)
  
  meal_name TEXT NOT NULL,
  cultural_context TEXT,
  suggestion_json TEXT NOT NULL,          -- full ChefOutput structure
  
  validation_result TEXT NOT NULL,        -- 'passed', 'failed_regenerated', 'conflict_resolved', 'user_rejected'
  validation_metadata_json TEXT NOT NULL, -- validator output (violations, disclosure notes, constraints checked)
  
  user_action TEXT,                       -- 'accepted', 'rejected', 'ignored', NULL if still pending
  user_action_at TEXT,
  rejection_reason TEXT,                  -- one of the structured reasons from PRD § 5 when user_action = 'rejected'
  rejection_free_text TEXT,               -- optional free-text the user added to the rejection
  
  created_at TEXT NOT NULL,
  modified_at TEXT NOT NULL,
  removed_at TEXT,                        -- soft delete
  
  schema_version INTEGER NOT NULL DEFAULT 1,
  
  FOREIGN KEY (profile_id) REFERENCES profiles(id),
  CHECK (validation_result IN ('passed', 'failed_regenerated', 'conflict_resolved', 'user_rejected')),
  CHECK (user_action IS NULL OR user_action IN ('accepted', 'rejected', 'ignored'))
);

CREATE INDEX idx_suggestions_profile_time ON suggestions(profile_id, generated_at DESC) WHERE removed_at IS NULL;
CREATE INDEX idx_suggestions_profile_action ON suggestions(profile_id, user_action, generated_at DESC) WHERE removed_at IS NULL;
```

### Table: `cooked_meals`

The history of meals the user has actually cooked (as opposed to just seen suggestions for). This is a separate table from `suggestions` because a cooked meal may or may not correspond to a prior suggestion, and the distinction matters.

```
CREATE TABLE cooked_meals (
  id TEXT PRIMARY KEY,
  profile_id TEXT NOT NULL,
  cooked_at TEXT NOT NULL,
  meal_name TEXT NOT NULL,
  suggestion_id TEXT,                     -- FK to suggestions.id if this corresponds to a suggestion, NULL otherwise
  ingredients_used_json TEXT,             -- what the user actually cooked (may differ from the suggestion)
  user_notes TEXT,                        -- optional free-text notes
  user_reported_outcome TEXT,             -- 'good', 'ok', 'bad', NULL if not reported
  
  created_at TEXT NOT NULL,
  modified_at TEXT NOT NULL,
  removed_at TEXT,
  schema_version INTEGER NOT NULL DEFAULT 1,
  
  FOREIGN KEY (profile_id) REFERENCES profiles(id),
  FOREIGN KEY (suggestion_id) REFERENCES suggestions(id)
);

CREATE INDEX idx_cooked_meals_profile_time ON cooked_meals(profile_id, cooked_at DESC) WHERE removed_at IS NULL;
```

**Notes.**
- `user_reported_outcome` is the mechanism the Food Data Provider uses to promote `low_confidence_ai` cache entries to `high_user_verified` when the user confirms that a meal with an AI-categorized ingredient was fine.
- The `cooked_meals` table feeds the Observer agent in v3 for long-term trend analysis.
- The v1 Pantry agent is lightweight so pantry tracking is mostly out of scope for v1. A `pantry_items` table is specified in Section 7 below because it has to exist for the v1 "inferred pantry context" logic to work, but it's minimal.

### Table: `pantry_items` (v1 minimal)

```
CREATE TABLE pantry_items (
  id TEXT PRIMARY KEY,
  profile_id TEXT NOT NULL,
  ingredient_name TEXT NOT NULL,
  canonical_entry_id TEXT,                -- FK to food_data_cache.id if resolved
  quantity_value REAL,
  quantity_unit TEXT,
  added_at TEXT NOT NULL,
  modified_at TEXT NOT NULL,
  consumed_at TEXT,                       -- NULL = still in pantry, set when consumed
  removed_at TEXT,                        -- soft delete (distinct from consumed_at)
  
  schema_version INTEGER NOT NULL DEFAULT 1,
  
  FOREIGN KEY (profile_id) REFERENCES profiles(id),
  FOREIGN KEY (canonical_entry_id) REFERENCES food_data_cache(id)
);

CREATE INDEX idx_pantry_active ON pantry_items(profile_id) WHERE consumed_at IS NULL AND removed_at IS NULL;
```

**Notes.**
- v1's Pantry agent is lightweight — users are not required to maintain the pantry — so this table will often be sparse or empty. That's fine.
- The v2 additions (barcode scanning, receipt scanning, fridge photo ingestion per technical-architecture.md § 7.5) write into this same table. The v1 schema is deliberately forward-compatible with v2's heavier use.

## 7. Event logs and alpha instrumentation

> Per PRD § 5, every meal suggestion, every acceptance, every rejection, every constraint update is logged locally on the user's device. Event logs are **never auto-uploaded** — they live on the device and are exportable by the user when they want to share with the founder for alpha feedback. This section specifies the schema for the event log and the export path. The event log is load-bearing for alpha because it is the only path by which the founder learns how Cuizine is actually being used.

### Table: `event_log`

A local append-only log of every meaningful event in the user's interaction with Cuizine.

```
CREATE TABLE event_log (
  id TEXT PRIMARY KEY,                    -- UUID v4
  profile_id TEXT NOT NULL,               -- FK to profiles.id, nullable for pre-profile events
  event_type TEXT NOT NULL,               -- enumerated below
  event_severity TEXT NOT NULL,           -- 'info', 'warn', 'severity_one', 'severity_zero'
  timestamp TEXT NOT NULL,                -- ISO 8601 UTC
  
  payload_json TEXT NOT NULL,             -- full event details, type-specific
  
  created_at TEXT NOT NULL,
  schema_version INTEGER NOT NULL DEFAULT 1,
  
  FOREIGN KEY (profile_id) REFERENCES profiles(id),
  CHECK (event_severity IN ('info', 'warn', 'severity_one', 'severity_zero'))
);

CREATE INDEX idx_event_log_profile_time ON event_log(profile_id, timestamp DESC);
CREATE INDEX idx_event_log_severity ON event_log(event_severity, timestamp DESC);
```

**Event type enumeration (v1).** The event types are a closed set; new types require a schema migration and a named decision:

- `constraint_conversation_started`
- `constraint_conversation_completed`
- `constraint_added`
- `constraint_updated`
- `constraint_removed`
- `contextual_state_set`
- `suggestion_requested`
- `suggestion_generated`
- `suggestion_validated_passed`
- `suggestion_validated_failed`
- `suggestion_regenerated`
- `suggestion_accepted`
- `suggestion_rejected`
- `meal_cooked`
- `conflict_resolution_triggered`
- `conflict_resolved_silently`
- `conflict_surfaced_to_user`
- `conflict_user_chose_option`
- `unknown_ingredient_ai_fallback`
- `provider_fallback_triggered`
- `agent_error`
- `validator_rejected_unknown_in_inviolable` (the severity-zero case from ADR 0010)

### Event severity tiers

- **`info`** — routine events. Most events are info-level: every successful suggestion, every accepted meal, every constraint update, every contextual state change. Info events are the bulk of the log.
- **`warn`** — unusual but not bug-like. A user rejecting three consecutive suggestions with the same reason. A validator running the AI fallback for a medical constraint. A user reporting an ingredient that triggered a constraint despite passing validation (this is the feedback loop that upgrades or demotes cache entries per ADR 0012).
- **`severity_one`** — something that should not be happening but isn't catastrophic. Malformed agent output after retries. A Curator producing a disallowed constraint combination. A Chef producing a culturally nonsensical suggestion. The founder reviews severity-one events during alpha to identify prompt issues and architectural gaps.
- **`severity_zero`** — a medical or religious constraint was violated, or the validator's safety floor was bypassed. This should *never* happen if the system is working correctly. Any severity-zero event is a stop-everything incident per ADR 0010 and PRD § 7.

### The `payload_json` schema (by event type)

Each event type has a specific payload shape. The discipline: the payload is complete enough that a reviewer reading the event log without access to the rest of the database can understand what happened. Examples of payload shapes for the most important types:

**`suggestion_validated_failed` payload:**
```typescript
interface SuggestionValidatedFailedPayload {
  suggestion_id: string;
  attempt_number: number;
  violated_constraint_ids: string[];
  violated_constraint_types: string[];
  violated_severities: string[];
  regeneration_triggered: boolean;
}
```

**`validator_rejected_unknown_in_inviolable` payload (severity-zero):**
```typescript
interface UnknownInInviolablePayload {
  suggestion_id: string;
  unknown_ingredient_name: string;
  inviolable_constraint_id: string;
  inviolable_constraint_target: string;
  user_facing_action: 'rejected_regenerated' | 'fallback_message_shown';
}
```

**`unknown_ingredient_ai_fallback` payload:**
```typescript
interface UnknownIngredientAiFallbackPayload {
  ingredient_name: string;
  checking_constraint_id: string;
  checking_constraint_severity: string;  // 'medical' | 'religious_cultural' | 'preference'
  ai_derived_categories: string[];
  ai_derived_allergens: string[];
  disclosure_shown_to_user: boolean;
  final_verdict: 'passed' | 'failed';
}
```

These payloads are the audit trail — a reviewer can reconstruct exactly what the system did and why from the event log alone, which is the condition for alpha feedback to be actionable.

### Event log export

The export path is a deliberate, user-initiated flow:

1. The user navigates to Settings → "Share feedback with Cuizine founder."
2. The app presents the events currently in the log, grouped by day, with severity-zero and severity-one events highlighted.
3. The user optionally adds a free-text note explaining what they want to share.
4. The user taps "Export." The app generates a JSON file containing the selected events (the user can opt in or out of specific events) plus the user's note plus a redacted snapshot of their constraint graph (with `original_phrasing` and other free-text fields optionally stripped per the user's choice).
5. The app uses the platform's share sheet to let the user send the file via email, messaging, or file sharing — Cuizine never has a direct upload path for event logs.

The philosophical commitment from `vision.md` — "the user can export and walk away" — applies to event logs specifically. The founder learns nothing about alpha usage unless a user explicitly chooses to share it, and the user remains in full control of what gets shared.

### Event log retention

The event log is NOT capped at a fixed size in v1. At typical usage (maybe 10-50 events per day for an active user), the log grows slowly — a year of active use produces maybe 10-20 MB of event data, which is fine. The engine can implement a background cleanup for events older than 365 days if storage becomes a concern, but the v1 default is "keep everything."

## 8. Schema versioning and migrations

> Per `constraint-engine-spec.md` Section 10, v1 ships with `schema_version = 1` and the migrator module exists even though it has nothing to migrate yet. This section specifies the concrete implementation.

### The `schema_metadata` table

A single-row table holding the current global schema version and related metadata.

```
CREATE TABLE schema_metadata (
  id INTEGER PRIMARY KEY CHECK (id = 1),  -- enforces single row
  current_version INTEGER NOT NULL,
  initialized_at TEXT NOT NULL,
  last_migration_at TEXT,
  last_migration_from_version INTEGER,
  last_migration_to_version INTEGER
);
```

On first launch, the engine inserts a single row with `current_version = 1`. On every subsequent launch, the engine reads this row to determine the current version and checks whether a migration is needed.

### The migrator module

The migrator is a single Kotlin class with this conceptual shape:

```kotlin
class ConstraintGraphMigrator {
    // registry maps (fromVersion, toVersion) to migration functions
    private val registry: Map<Pair<Int, Int>, MigrationFn> = mapOf(
        // empty in v1; grows as migrations are written
    )

    suspend fun migrate(db: SupportSQLiteDatabase, fromVersion: Int, toVersion: Int) {
        if (fromVersion == toVersion) return
        if (fromVersion > toVersion) {
            throw IllegalStateException(
                "Cannot migrate backward from v$fromVersion to v$toVersion; user must update app"
            )
        }

        // compose a chain of single-step migrations
        for (v in fromVersion until toVersion) {
            val step = registry[v to (v + 1)]
                ?: throw IllegalStateException("No migration path from v$v to v${v + 1}")
            db.transaction {
                step(db)
                updateSchemaMetadata(db, v + 1)
            }
        }
    }
}
```

Note: Room provides its own `Migration` abstraction (`Migration(from, to)` objects registered on the database builder). The registry pattern above maps cleanly onto Room's migration list — each entry becomes a Room `Migration` object — while preserving the explicit single-step-chain discipline and the schema-metadata bookkeeping that this doc requires. The agent may implement this either as a wrapper over Room's migration mechanism or as a thin layer Room delegates to; either is acceptable as long as the behavior (forward-only, single-step chain, transactional, metadata-tracked) holds.

At v1 ship, `registry` is empty. The migrator module exists, is instantiated on every app launch, and runs `migrate(db, currentVersion, codeExpectedVersion)` which is a no-op when both are 1. The empty registry is **exercised by tests** so that the *first* real migration (v1 → v2) is a new entry in an existing system rather than a new system. Tests that should exist at v1 ship:

- `test_migrator_no_op_when_versions_match`
- `test_migrator_throws_on_backward_migration`
- `test_migrator_throws_on_missing_migration_step`
- `test_migrator_runs_single_step_migration` (using a synthetic test migration from v1 to v2 that isn't shipped but exercises the code path)

### Migration conventions

Per `constraint-engine-spec.md` Section 10's discipline:

- **Migrations are additive only by convention.** A migration can add new tables, add new columns with sensible defaults, add new indices, add new enum values to CHECK constraints (by recreating the constraint), and add new fields inside JSON payloads (by leaving old JSON documents unchanged and writing new ones with the new fields). Migrations **cannot** rename or remove existing columns without a deprecation period spanning at least two major versions.

- **Each migration runs inside a transaction.** If any step fails, the whole migration rolls back and the database remains at the previous version. Partial migrations are forbidden.

- **Each migration updates both the per-row `schema_version` fields and the global `schema_metadata.current_version`** at completion. The per-row update is lazy in some cases — a migration that only adds a new column can leave existing rows with `schema_version = 1` as long as the reading code knows how to handle old rows with missing columns. The global update is immediate.

- **Each migration is idempotent.** Running the same migration twice should be a no-op (not an error, not a partial re-application). This protects against edge cases where the database state is ambiguous.

- **Migrations are logged** to the event log with `event_type = schema_migration_completed` and a payload identifying the from/to versions and any row-count metrics.

### What cannot change without an ADR

Per `constraint-engine-spec.md` Section 10:

- The four severity tiers
- The five constraint types
- The profile-as-first-class commitment from ADR 0004
- The stateless validator commitment from ADR 0010
- The three-layer food data architecture from ADR 0012
- The local-first data ownership principle

Changes to any of these trigger a new ADR before any migration is written. Schema migrations that extend these concepts (adding a new field to provenance, adding a new scope shape, adding a new contextual state vocabulary entry) are additive and allowed without a new ADR.

## 9. Encryption at rest and the sync boundary

> ADR 0011 commits Cuizine to local-first encrypted sync. This section specifies which tables are included in which encryption and sync layers. The exact encryption library and key derivation are specified in `local-first-sync.md` — this section specifies only the tables-to-layer mapping.

### Three layers of on-device protection

**Layer 1: Android Keystore at-rest encryption.** The SQLite database file is stored using Android's at-rest encryption (per standard Android app data practices). This protects data if the device is lost and the attacker can't unlock it. This layer applies to **every table**, including the `accounts`, `food_data_cache`, and `event_log` tables, because at-rest encryption is free and uniform.

**Layer 2: Sync container encryption.** For signed-in users, a subset of tables is packaged into an encrypted blob and uploaded to Firestore for cross-device sync per ADR 0011. The client-side encryption is applied *before* upload, and Cuizine cannot read the contents. This layer applies to tables containing user-sensitive data that needs to move across the user's devices.

**Layer 3: Nothing.** Some tables are excluded from sync entirely — they live only on the device they were created on and are not part of the encrypted blob. This is correct for tables that are device-local by nature (crash reports, if we ever add them; some parts of the event log; account metadata that Firebase Auth already holds).

### Tables in the encrypted sync container (signed-in users only)

- **`profiles`** — core user identity and cultural context
- **`constraints`** — the full constraint graph
- **`food_data_cache`** — the user's ingredient cache (per ADR 0012, cache is synced so a new device doesn't have to re-fetch from USDA/OFF)
- **`suggestions`** — the user's meal suggestion history
- **`cooked_meals`** — the user's cooking history
- **`pantry_items`** — the user's pantry state

### Tables NOT in the encrypted sync container

- **`accounts`** — account metadata is already known to Firebase Auth, encrypting it locally adds no protection
- **`event_log`** — device-local by design. Per PRD § 5, event logs never auto-upload. If a user wants to share alpha feedback across devices, they can export and re-import manually.
- **`schema_metadata`** — per-device schema version, not user data

### Encryption key derivation

Per ADR 0011, the encryption key is derived from a combination of the user's account credentials (so they can recover their data on a new device by signing in) and a recovery passphrase shown to the user once during account setup. The exact key derivation function (PBKDF2 vs Argon2, iteration counts, salt strategy) is specified in `local-first-sync.md`. This doc commits only to the *principle* of hybrid key derivation and the tables subject to it.

### The signed-out case

Signed-out users have no encrypted sync container at all — the Firestore path is never touched. Their data lives entirely on their device under Layer 1 (Android Keystore at-rest encryption) and nowhere else. This matches the signed-out column in ADR 0011's capability matrix and provides the strongest trust posture Cuizine offers.

If a signed-out user later signs in, the existing local data is encrypted into a sync container and uploaded at that moment. The transition from signed-out to signed-in is handled at the application layer and is specified in `local-first-sync.md`.

## 10. Indices and performance considerations

> This section documents the query patterns Docs #4 and #5 imply and the indices that support them. It is not exhaustive — the implementation may add or remove indices based on observed performance during alpha — but it specifies the hot paths the schema is committed to supporting efficiently.

### The hot paths

**Hot path 1: Active constraint set computation.** Called on every Chef query, every validator check, and every Planner reasoning step. Queries look like:

```sql
-- Conceptual query (actual Room-generated SQL varies):
SELECT * FROM constraints 
WHERE profile_id = ? 
  AND removed_at IS NULL 
  AND (expires_at IS NULL OR expires_at > ?)
  -- Scope evaluation happens in Kotlin code after loading
```

Supported by: `idx_constraints_profile_active` (partial index on `(profile_id, type, severity) WHERE removed_at IS NULL`) and `idx_constraints_expires_at`. The scope evaluation is deliberately done in Kotlin code, not in SQL, because the scope dimensions have conditional logic that would be painful to express in SQL and the constraint count per profile is small enough (tens to low hundreds) that in-memory filtering is fast.

**Hot path 2: Food data cache lookup.** Called on every ingredient resolution. Queries look like:

```sql
SELECT * FROM food_data_cache 
WHERE lookup_key = ? AND lookup_kind = ?
  AND (ttl_expires_at IS NULL OR ttl_expires_at > ?)
```

Supported by: the unique index `idx_food_cache_lookup` on `(lookup_key, lookup_kind)`. Every cache lookup is O(log n) and typically satisfies from memory because SQLite caches hot pages aggressively.

**Hot path 3: Suggestion history retrieval.** Called when the Chef needs recent history context or the Observer (v3) analyzes trends. Queries look like:

```sql
SELECT * FROM suggestions 
WHERE profile_id = ? 
  AND removed_at IS NULL 
  AND generated_at > ? 
ORDER BY generated_at DESC 
LIMIT ?
```

Supported by: `idx_suggestions_profile_time` on `(profile_id, generated_at DESC) WHERE removed_at IS NULL`. The descending order matches the query pattern so the index reads are sequential.

**Hot path 4: Event log insertion.** Called on every event (potentially dozens per user session). Event inserts must be cheap because they happen constantly. Indices on `event_log` are kept minimal (`profile_id + timestamp` and `severity + timestamp`) to avoid write overhead.

**Hot path 5: Cooked meals history.** Similar to Hot path 3 but for the `cooked_meals` table, feeding the Observer's trend analysis in v3.

### Active-set cache invalidation

Per `constraint-engine-spec.md` Section 5, the active-set computation has a query-level cache with invalidation on graph writes and contextual state changes. The cache lives in the engine's runtime memory, not in SQLite — it's not persistent. The invalidation strategy is fine-grained:

- Adding or removing a constraint on a profile → invalidates cached active sets for that profile only
- Setting or clearing a contextual state → invalidates cached active sets for the profile only
- Time passing across a temporal scope boundary → invalidates lazily on the next query that would have been affected (the engine does not proactively recompute at clock-driven boundaries)

The cache is a simple `Map<CacheKey, ActiveSet>` with a TTL of a few seconds for time-sensitive queries and longer for queries without temporal dependencies. The exact TTL strategy is deferred to implementation and may be tuned during alpha.

### What we will NOT do for performance

- **We will not denormalize the constraint graph** into precomputed active sets stored on disk. The active-set computation is fast enough as in-memory filtering after a simple indexed load, and denormalization would create invalidation complexity that outweighs the benefit.
- **We will not shard the event log** by day or other time dimension. At v1 alpha scale the log is small, and sharding would add query complexity for no current benefit.
- **We will not cache the food data cache in a separate in-memory structure beyond what SQLite itself does.** SQLite's page cache handles hot reads well and adding another layer would introduce invalidation complexity.
- **We will not use SQLite full-text search in v1.** There is no hot path that needs it. If v2 or v3 surfaces a need, it can be added as a schema extension.

## 11. Open questions

### Schema details

- **Are the five constraint type payload schemas from Section 4 complete?** We designed them from Sukhi's cases and the secondary personas, but alpha may surface patterns we haven't imagined. If an alpha user's real constraint doesn't fit any of the five payloads, the first response is to check whether it fits with a small payload extension (additive, no ADR needed); only if it requires a new constraint type does an ADR get written.
- **Should `provenance_json.modification_history` have a size cap?** A heavily-modified constraint could accumulate a long modification history, which would bloat individual rows and slow sync. Probably yes, with a cap of ~20 history entries and the oldest entries being summarized into a single "older history" entry when the cap is exceeded. Deferred to implementation.

### Event log

- **What is the right retention policy for event logs in production?** v1 ships with "keep everything" and a possible future cleanup of events older than 365 days. The v2 launch population will tell us whether this is sustainable. If storage becomes a concern, the cleanup policy can be tightened without schema changes.
- **Should severity-zero events be replicated to the sync container** even though event logs generally aren't synced? There's an argument for syncing severity-zero events specifically so the founder can learn about them even if the user doesn't explicitly export. There's a counter-argument that this violates the "event logs never auto-upload" discipline and the principled answer is no. Tentatively: no, severity-zero events are still local-only. Revisit if alpha data shows critical incidents being lost.

### Indices and performance

- **Is the partial index on `constraints WHERE removed_at IS NULL` actually faster than a full index?** Partial indices are smaller but have more complex query planning. Measure during alpha and simplify if the benefit is marginal.
- **When does the active-set cache's in-memory TTL matter?** If TTL is too short, hot paths re-query unnecessarily. If TTL is too long, contextual state changes may take a moment to propagate. Alpha will tell us the right value.

### Encryption and sync

- **Should `event_log` ever be included in the encrypted sync container?** Section 9 currently says no, but if the user has multiple devices and wants their alpha feedback data to move between them, manual export-and-import is awkward. Maybe a future v2 capability.
- **How does the schema handle the signed-out-to-signed-in transition?** When a user signs in for the first time after a period of signed-out use, the existing local data needs to be encrypted and uploaded to Firestore. The schema doesn't need to change, but the transition flow needs to be careful about ordering (encrypt first, upload second, don't leave the app in a half-encrypted state). Deferred to `local-first-sync.md`.

### v2 readiness gaps

- **How does the schema handle multiple profiles being added to a single account during v2?** The schema supports it (profiles table has `account_id` and `owner_account_id`), but the UI flow for adding a dependent profile hasn't been specified and it may surface constraints we haven't anticipated. Deferred to v2 planning.
- **Will v2's Planner agent need new tables for week-ahead meal plans** as first-class entities rather than just sequences of suggestions? Probably yes — a `meal_plans` table holding named plans with metadata about the planning window, the household composition, and the constraint satisfaction across the plan. Deferred to v2 planning.

## 12. Cross-references

### What this document references

- `vision.md` — for tone, trust posture, the "export and walk away" commitment
- `PRD.md` — for Sukhi, the user journey, the v1 feature scope, alpha instrumentation
- `technical-architecture.md` — for where the data layer sits in the subsystem decomposition
- `constraint-engine-spec.md` — for the logical specification this doc physicalizes
- `agent-architecture.md` — for the typed input/output contracts that round-trip through the database
- ADR 0004 — profile ownership model implemented in the `profiles` table
- ADR 0016 — native Kotlin/Room stack and the SQLite target (supersedes ADR 0005's Flutter/Drift)
- ADR 0009 — severity tier model reflected in the `constraints.severity` column and CHECK constraint
- ADR 0010 — validator schema requirements, severity-zero event type
- ADR 0011 — optional backend model shaping which tables are synced
- ADR 0012 — food data layer implemented in `food_data_cache` and the curated bundle format

### What this document defers to deeper-dive docs

- **`local-first-sync.md`** — the actual encryption library, key derivation function, the sync protocol, the recovery passphrase UX, the signed-out-to-signed-in transition
- **`build-conventions.md`** — the Kotlin/Room code style, folder structure, how `@Entity`/`@Dao` classes are organized, migration file naming
- **`testing-strategy.md`** — the test suite for the schema itself, including the migrator's empty-registry tests and the hard-cases test data seed
- **`security-and-privacy.md`** — the formal threat model for the data layer, the export flow legal posture

### What this document does *not* defer (decisions made here)

- The naming conventions, primary key strategy, timestamp format, JSON column strategy, and soft-delete discipline (Section 2)
- The table schemas for accounts, profiles, constraints, food_data_cache, suggestions, cooked_meals, pantry_items, event_log, schema_metadata (Sections 3-8)
- The JSON payload schemas for scope, provenance, the five constraint types, the canonical ingredient entry, and the event payloads
- The tables-to-encryption-layer mapping (Section 9)
- The hot-path indices and the anti-optimization commitments (Section 10)

### How the agent should use this doc

When the coding agent builds the data layer, Sections 2-8 are the authoritative specification for the Room schema. The tables (Room `@Entity`s), columns, indices, CHECK constraints, and JSON payload shapes are the concrete commitments. Section 9 is the encryption boundary — the agent must get this right because a mistake here would break the trust posture. Section 10 is the performance guidance — the agent should not add indices beyond what's specified without a named justification, and should not denormalize or over-cache without measuring first.

When the agent encounters a question this doc does not answer — particularly in the JSON payload shapes, which are more extensible than the table schemas — the discipline from PRD § 9 applies: check the vision, check the constraint engine spec, check the relevant ADR, ask the founder before guessing. The data layer is where the most architectural commitments become physical, and silent drift here means rebuilding later.

---

*End of `data-model.md` v1 (initial draft). Next revision will incorporate any schema needs that surface during the writing of `local-first-sync.md`, `build-conventions.md`, and `testing-strategy.md`, and any learnings from the v1 alpha build. The vision, the ADRs, and the constraint engine spec are more stable than this document; the data model is expected to learn from contact with implementation and real users.*
