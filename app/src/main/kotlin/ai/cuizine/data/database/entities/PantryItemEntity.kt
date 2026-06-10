package ai.cuizine.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `pantry_items` (`data-model.md` §6) — v1 minimal manual pantry. Often empty
 * in v1; the Chef falls back to the inferred-pantry heuristic (PRD §5).
 * `consumed_at` (used up) is distinct from `removed_at` (soft delete).
 */
@Entity(
    tableName = "pantry_items",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
        ),
        ForeignKey(
            entity = FoodDataCacheEntity::class,
            parentColumns = ["id"],
            childColumns = ["canonical_entry_id"],
        ),
    ],
    indices = [
        Index(value = ["profile_id"], name = "idx_pantry_active"),
        Index(value = ["canonical_entry_id"], name = "idx_pantry_canonical_entry_id"),
    ],
)
data class PantryItemEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "ingredient_name") val ingredientName: String,
    @ColumnInfo(name = "canonical_entry_id") val canonicalEntryId: String? = null,
    @ColumnInfo(name = "quantity_value") val quantityValue: Double? = null,
    @ColumnInfo(name = "quantity_unit") val quantityUnit: String? = null,
    @ColumnInfo(name = "added_at") val addedAt: String,
    @ColumnInfo(name = "modified_at") val modifiedAt: String,
    /** null = still in the pantry. */
    @ColumnInfo(name = "consumed_at") val consumedAt: String? = null,
    @ColumnInfo(name = "removed_at") val removedAt: String? = null,
    @ColumnInfo(name = "schema_version", defaultValue = "1") val schemaVersion: Int = 1,
)
