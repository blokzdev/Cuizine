package ai.cuizine.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `food_data_cache` (`data-model.md` §5) — Layer 1 of the three-layer food
 * data architecture (ADR 0012). Evictable; soft-delete does not apply.
 * TTLs: USDA 6 months, OFF 3 months, bundle/user_verified never (null).
 */
@Entity(
    tableName = "food_data_cache",
    indices = [
        Index(value = ["lookup_key", "lookup_kind"], name = "idx_food_cache_lookup", unique = true),
        Index(value = ["ttl_expires_at"], name = "idx_food_cache_ttl"),
    ],
)
data class FoodDataCacheEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    /** Normalized: lowercase, whitespace-stripped, accent-folded. */
    @ColumnInfo(name = "lookup_key") val lookupKey: String,
    /** ingredient_name | barcode | category. */
    @ColumnInfo(name = "lookup_kind") val lookupKind: String,
    /** CanonicalIngredientEntry JSON (payload class lands in Phase 4). */
    @ColumnInfo(name = "canonical_entry_json") val canonicalEntryJson: String,
    /** Shares the ProvenanceRecord.confidence enum (`data-model.md` §5). */
    @ColumnInfo(name = "confidence") val confidence: String,
    /** bundle | usda | open_food_facts | ai_fallback | user_verified. */
    @ColumnInfo(name = "source_layer") val sourceLayer: String,
    @ColumnInfo(name = "first_cached_at") val firstCachedAt: String,
    @ColumnInfo(name = "last_refreshed_at") val lastRefreshedAt: String,
    @ColumnInfo(name = "ttl_expires_at") val ttlExpiresAt: String? = null,
    @ColumnInfo(name = "schema_version", defaultValue = "1") val schemaVersion: Int = 1,
)
