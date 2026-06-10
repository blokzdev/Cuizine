package ai.cuizine.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `profiles` (`data-model.md` §3). Profile is first-class with explicit
 * ownership from day one (ADR 0004) even though v1 is single-profile.
 * Signed-out mode uses one synthetic local profile with `account_id` NULL.
 * In v1: `account_id == owner_account_id`, `linked_account_id == NULL`.
 */
@Entity(
    tableName = "profiles",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
        ),
    ],
    indices = [
        Index(value = ["account_id"], name = "idx_profiles_account_id"),
        Index(value = ["owner_account_id"], name = "idx_profiles_owner"),
    ],
)
data class ProfileEntity(
    /** Client-generated UUID v4 (`data-model.md` §2). */
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "account_id") val accountId: String? = null,
    @ColumnInfo(name = "display_name") val displayName: String,
    /** self | silent_dependent | consent_aware_dependent | adult_dependent | linked_partner_v3. */
    @ColumnInfo(name = "relationship_type") val relationshipType: String,
    @ColumnInfo(name = "owner_account_id") val ownerAccountId: String? = null,
    @ColumnInfo(name = "linked_account_id") val linkedAccountId: String? = null,
    /** [ai.cuizine.shared.types.CulturalContext] JSON; null until the day-0 conversation captures it. */
    @ColumnInfo(name = "cultural_context_json") val culturalContextJson: String? = null,
    /** [ai.cuizine.shared.types.CookingFor] JSON. */
    @ColumnInfo(name = "cooking_for_json") val cookingForJson: String? = null,
    /** IANA timezone string, e.g. "America/Toronto". */
    @ColumnInfo(name = "timezone") val timezone: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "modified_at") val modifiedAt: String,
    /** Soft delete (`data-model.md` §2): non-null means removed. */
    @ColumnInfo(name = "removed_at") val removedAt: String? = null,
    @ColumnInfo(name = "schema_version", defaultValue = "1") val schemaVersion: Int = 1,
)
