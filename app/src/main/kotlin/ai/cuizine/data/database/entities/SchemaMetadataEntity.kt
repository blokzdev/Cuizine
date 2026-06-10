package ai.cuizine.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `schema_metadata` (`data-model.md` §8) — the single-row global schema
 * version record. The id is always 1 (enforced in code; the DDL CHECK has no
 * Room equivalent — DECISION-LOG.md #1c). Inserted with `current_version = 1`
 * on first launch; updated only by [ai.cuizine.data.database.migrations.ConstraintGraphMigrator].
 */
@Entity(tableName = "schema_metadata")
data class SchemaMetadataEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "current_version") val currentVersion: Int,
    @ColumnInfo(name = "initialized_at") val initializedAt: String,
    @ColumnInfo(name = "last_migration_at") val lastMigrationAt: String? = null,
    @ColumnInfo(name = "last_migration_from_version") val lastMigrationFromVersion: Int? = null,
    @ColumnInfo(name = "last_migration_to_version") val lastMigrationToVersion: Int? = null,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
