package ai.cuizine.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `cooked_meals` (`data-model.md` §6). `user_reported_outcome` is the
 * mechanism that promotes low-confidence AI food-cache entries to
 * `high_user_verified` (Phase 4 wiring).
 */
@Entity(
    tableName = "cooked_meals",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
        ),
        ForeignKey(
            entity = SuggestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["suggestion_id"],
        ),
    ],
    indices = [
        Index(
            value = ["profile_id", "cooked_at"],
            orders = [Index.Order.ASC, Index.Order.DESC],
            name = "idx_cooked_meals_profile_time",
        ),
        Index(value = ["suggestion_id"], name = "idx_cooked_meals_suggestion_id"),
    ],
)
data class CookedMealEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "cooked_at") val cookedAt: String,
    @ColumnInfo(name = "meal_name") val mealName: String,
    @ColumnInfo(name = "suggestion_id") val suggestionId: String? = null,
    @ColumnInfo(name = "ingredients_used_json") val ingredientsUsedJson: String? = null,
    @ColumnInfo(name = "user_notes") val userNotes: String? = null,
    /** good | ok | bad | null. */
    @ColumnInfo(name = "user_reported_outcome") val userReportedOutcome: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "modified_at") val modifiedAt: String,
    @ColumnInfo(name = "removed_at") val removedAt: String? = null,
    @ColumnInfo(name = "schema_version", defaultValue = "1") val schemaVersion: Int = 1,
)
