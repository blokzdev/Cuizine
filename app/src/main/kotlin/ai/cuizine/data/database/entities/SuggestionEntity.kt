package ai.cuizine.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `suggestions` (`data-model.md` §6) — every Chef output ever shown (or
 * rejected by validation), with the validator verdict and the user's action.
 * The rejected-with-reason history is the second-most-important v1 data after
 * the constraint graph itself (PRD §4).
 */
@Entity(
    tableName = "suggestions",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
        ),
    ],
    indices = [
        Index(
            value = ["profile_id", "generated_at"],
            orders = [Index.Order.ASC, Index.Order.DESC],
            name = "idx_suggestions_profile_time",
        ),
        Index(
            value = ["profile_id", "user_action", "generated_at"],
            orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.DESC],
            name = "idx_suggestions_profile_action",
        ),
    ],
)
data class SuggestionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "generated_at") val generatedAt: String,
    /** 'chef' in v1; 'planner' / 'planner_via_chef' arrive in v2. */
    @ColumnInfo(name = "generated_by_agent") val generatedByAgent: String,
    @ColumnInfo(name = "meal_name") val mealName: String,
    @ColumnInfo(name = "cultural_context") val culturalContext: String? = null,
    /** Full ChefOutput JSON (typed contract lands in Phase 5 per agent-architecture.md). */
    @ColumnInfo(name = "suggestion_json") val suggestionJson: String,
    /** passed | failed_regenerated | conflict_resolved | user_rejected. */
    @ColumnInfo(name = "validation_result") val validationResult: String,
    @ColumnInfo(name = "validation_metadata_json") val validationMetadataJson: String,
    /** null | accepted | rejected | ignored. */
    @ColumnInfo(name = "user_action") val userAction: String? = null,
    @ColumnInfo(name = "user_action_at") val userActionAt: String? = null,
    /** Structured rejection reason from PRD §5. */
    @ColumnInfo(name = "rejection_reason") val rejectionReason: String? = null,
    @ColumnInfo(name = "rejection_free_text") val rejectionFreeText: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "modified_at") val modifiedAt: String,
    @ColumnInfo(name = "removed_at") val removedAt: String? = null,
    @ColumnInfo(name = "schema_version", defaultValue = "1") val schemaVersion: Int = 1,
)
