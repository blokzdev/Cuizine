package ai.cuizine.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `constraints` (`data-model.md` §4) — the constraint graph's persistent form.
 * Scope is flattened into indexed columns for the active-set hot path plus a
 * full `scope_json`; the payload is the type-discriminated union from
 * `engine/types` (discriminated by the [type] column, not in-JSON).
 *
 * Closed string sets (type, severity, temporal_scope_kind,
 * household_scope_mode) are enforced at the engine API layer, not by SQLite
 * CHECK (DECISION-LOG.md #1c; `data-model.md` §4 notes).
 */
@Entity(
    tableName = "constraints",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
        ),
    ],
    indices = [
        Index(value = ["profile_id", "type", "severity"], name = "idx_constraints_profile_active"),
        Index(value = ["profile_id", "temporal_scope_kind"], name = "idx_constraints_profile_temporal"),
        Index(value = ["expires_at"], name = "idx_constraints_expires_at"),
    ],
)
data class ConstraintEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    /** avoid | prefer | require | limit | contextual ([ai.cuizine.engine.types.ConstraintType]). */
    @ColumnInfo(name = "type") val type: String,
    /** inviolable | medical | religious_cultural | preference ([ai.cuizine.engine.types.Severity]). */
    @ColumnInfo(name = "severity") val severity: String,
    /** always | weekly | daily_window | date_bounded | phase_bounded | composite. */
    @ColumnInfo(name = "temporal_scope_kind") val temporalScopeKind: String,
    /** JSON array of contextual flags, or null. */
    @ColumnInfo(name = "contextual_scope_flags") val contextualScopeFlags: String? = null,
    /** null = anywhere. */
    @ColumnInfo(name = "location_scope_country") val locationScopeCountry: String? = null,
    /** self | specific_profiles | whole_household. */
    @ColumnInfo(name = "household_scope_mode") val householdScopeMode: String,
    /** [ai.cuizine.engine.types.ConstraintScope] JSON. */
    @ColumnInfo(name = "scope_json") val scopeJson: String,
    /** [ai.cuizine.engine.types.ConstraintPayload] subtype JSON, discriminated by [type]. */
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    /** [ai.cuizine.engine.types.ProvenanceRecord] JSON. */
    @ColumnInfo(name = "provenance_json") val provenanceJson: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "modified_at") val modifiedAt: String,
    @ColumnInfo(name = "removed_at") val removedAt: String? = null,
    /** Contextual-constraint TTL; null = no expiry. */
    @ColumnInfo(name = "expires_at") val expiresAt: String? = null,
    @ColumnInfo(name = "schema_version", defaultValue = "1") val schemaVersion: Int = 1,
)
