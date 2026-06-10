package ai.cuizine.data.database.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.util.UUID

/** One forward migration step: (fromVersion → fromVersion + 1). */
typealias MigrationStep = (SupportSQLiteDatabase) -> Unit

/**
 * The constraint-graph schema migrator (`data-model.md` §8). Forward-only,
 * single-step chain composition, each step transactional and recorded in
 * `schema_metadata` plus a `schema_migration_completed` event-log row.
 *
 * The registry is EMPTY at v1 — by design. The module is instantiated and
 * exercised on every launch so that the machinery is proven before the first
 * real migration exists (`testing-strategy.md` §9's named tests).
 */
class ConstraintGraphMigrator(
    private val registry: Map<Pair<Int, Int>, MigrationStep> = emptyMap(),
) {
    /**
     * Migrates the constraint graph from [fromVersion] to [toVersion].
     *
     * @throws IllegalStateException on backward migration (the app must be
     *   updated instead) or when no single-step path covers the range.
     */
    suspend fun migrate(
        db: SupportSQLiteDatabase,
        fromVersion: Int,
        toVersion: Int,
    ) {
        if (fromVersion == toVersion) return
        check(toVersion > fromVersion) {
            "Backward migration from schema v$fromVersion to v$toVersion is not supported; " +
                "the user must update the app instead."
        }

        // Verify the complete path BEFORE touching the database.
        val steps =
            (fromVersion until toVersion).map { version ->
                val step =
                    registry[version to version + 1]
                        ?: throw IllegalStateException(
                            "No migration path from schema v$version to v${version + 1}.",
                        )
                Triple(version, version + 1, step)
            }

        steps.forEach { (stepFrom, stepTo, step) ->
            db.beginTransaction()
            try {
                step(db)
                recordCompletion(db, stepFrom, stepTo)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun recordCompletion(
        db: SupportSQLiteDatabase,
        stepFrom: Int,
        stepTo: Int,
    ) {
        val now = Instant.now().toString()
        db.execSQL(
            "UPDATE schema_metadata SET current_version = ?, last_migration_at = ?, " +
                "last_migration_from_version = ?, last_migration_to_version = ? WHERE id = 1",
            arrayOf<Any?>(stepTo, now, stepFrom, stepTo),
        )
        db.execSQL(
            "INSERT INTO event_log (id, profile_id, event_type, event_severity, timestamp, " +
                "payload_json, created_at, schema_version) VALUES (?, NULL, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                UUID.randomUUID().toString(),
                EVENT_TYPE_MIGRATION_COMPLETED,
                EVENT_SEVERITY_INFO,
                now,
                """{"from_version":$stepFrom,"to_version":$stepTo}""",
                now,
                stepTo,
            ),
        )
    }

    companion object {
        const val EVENT_TYPE_MIGRATION_COMPLETED = "schema_migration_completed"
        const val EVENT_SEVERITY_INFO = "info"
    }
}
