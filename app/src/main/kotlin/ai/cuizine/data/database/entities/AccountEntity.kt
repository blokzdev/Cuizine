package ai.cuizine.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `accounts` (`data-model.md` §3). One row per signed-in account; the id is
 * the Firebase Auth UID, not a client UUID. Signed-out users have NO accounts
 * row. Never part of the encrypted sync container. No password, hash, or
 * token is ever stored locally.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "email") val email: String? = null,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    /** 'google' is the only v1 provider (ADR 0011). */
    @ColumnInfo(name = "provider") val provider: String,
    /** ISO 8601 UTC (`data-model.md` §2). */
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "modified_at") val modifiedAt: String,
    @ColumnInfo(name = "schema_version", defaultValue = "1") val schemaVersion: Int = 1,
)
