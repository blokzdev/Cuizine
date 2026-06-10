package ai.cuizine.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `event_log` (`data-model.md` §7) — local-only, append-only (no soft delete;
 * TTL cleanup instead), NEVER synced, NEVER auto-uploaded. Export happens only
 * through the user-initiated Settings share flow. Payloads carry no user
 * content beyond what the event type requires for audit reconstruction.
 *
 * `profile_id` is nullable for pre-profile events (DECISION-LOG.md #1c:
 * resolves the DDL/comment contradiction in `data-model.md` §7).
 */
@Entity(
    tableName = "event_log",
    indices = [
        Index(
            value = ["profile_id", "timestamp"],
            orders = [Index.Order.ASC, Index.Order.DESC],
            name = "idx_event_log_profile_time",
        ),
        Index(
            value = ["event_severity", "timestamp"],
            orders = [Index.Order.ASC, Index.Order.DESC],
            name = "idx_event_log_severity",
        ),
    ],
)
data class EventLogEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String? = null,
    /** Closed 22-value set + schema_migration_completed (`data-model.md` §7–8); enforced in code. */
    @ColumnInfo(name = "event_type") val eventType: String,
    /** info | warn | severity_one | severity_zero. */
    @ColumnInfo(name = "event_severity") val eventSeverity: String,
    @ColumnInfo(name = "timestamp") val timestamp: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "schema_version", defaultValue = "1") val schemaVersion: Int = 1,
)
