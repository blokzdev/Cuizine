package ai.cuizine.data.database.migrations

import ai.cuizine.core.di.SchemaMetadataSeedCallback
import ai.cuizine.data.database.CuizineDatabase
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The empty-registry migrator tests required at v1 ship even though no real
 * migration exists yet (`testing-strategy.md` §9, `data-model.md` §8).
 */
@RunWith(RobolectricTestRunner::class)
class ConstraintGraphMigratorTest {
    private lateinit var database: CuizineDatabase
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, CuizineDatabase::class.java)
                .addCallback(SchemaMetadataSeedCallback)
                .allowMainThreadQueries()
                .build()
        db = database.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun currentVersion(): Int =
        db.query("SELECT current_version FROM schema_metadata WHERE id = 1").use { cursor ->
            assertTrue("schema_metadata row must be seeded on first launch", cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun lastMigrationToVersion(): Int? =
        db.query("SELECT last_migration_to_version FROM schema_metadata WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getInt(0)
        }

    private fun migrationEventCount(): Int =
        db
            .query(
                "SELECT COUNT(*) FROM event_log WHERE event_type = " +
                    "'${ConstraintGraphMigrator.EVENT_TYPE_MIGRATION_COMPLETED}'",
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

    /** A synthetic, additive v1→v2 step — never shipped (`testing-strategy.md` §9). */
    private val syntheticV1ToV2: MigrationStep = { database ->
        database.execSQL("ALTER TABLE profiles ADD COLUMN synthetic_test_column TEXT")
    }

    private val syntheticV2ToV3: MigrationStep = { database ->
        database.execSQL("ALTER TABLE profiles ADD COLUMN synthetic_test_column_two TEXT")
    }

    @Test
    fun test_migrator_no_op_when_versions_match() =
        runTest {
            ConstraintGraphMigrator().migrate(db, fromVersion = 1, toVersion = 1)
            assertEquals(1, currentVersion())
            assertEquals(0, migrationEventCount())
        }

    @Test
    fun test_migrator_throws_on_backward_migration() {
        val migrator = ConstraintGraphMigrator()
        val failure =
            assertThrows(IllegalStateException::class.java) {
                runTest { migrator.migrate(db, fromVersion = 2, toVersion = 1) }
            }
        assertTrue(failure.message.orEmpty().contains("update the app"))
        assertEquals(1, currentVersion())
    }

    @Test
    fun test_migrator_throws_on_missing_migration_step() {
        val migrator = ConstraintGraphMigrator()
        val failure =
            assertThrows(IllegalStateException::class.java) {
                runTest { migrator.migrate(db, fromVersion = 1, toVersion = 3) }
            }
        assertTrue(failure.message.orEmpty().contains("No migration path"))
        assertEquals(1, currentVersion())
    }

    @Test
    fun test_migrator_runs_single_step_migration() =
        runTest {
            val migrator = ConstraintGraphMigrator(mapOf((1 to 2) to syntheticV1ToV2))
            migrator.migrate(db, fromVersion = 1, toVersion = 2)
            assertEquals(2, currentVersion())
            assertEquals(2, lastMigrationToVersion())
            assertEquals(1, migrationEventCount())
        }

    @Test
    fun test_migrator_chains_multi_step_migrations() =
        runTest {
            val migrator =
                ConstraintGraphMigrator(
                    mapOf((1 to 2) to syntheticV1ToV2, (2 to 3) to syntheticV2ToV3),
                )
            migrator.migrate(db, fromVersion = 1, toVersion = 3)
            assertEquals(3, currentVersion())
            assertEquals(3, lastMigrationToVersion())
            assertEquals(2, migrationEventCount())
        }

    @Test
    fun test_migrator_rolls_back_on_step_failure() {
        val failing: MigrationStep = { database ->
            database.execSQL("ALTER TABLE profiles ADD COLUMN doomed_column TEXT")
            error("Synthetic migration failure")
        }
        val migrator = ConstraintGraphMigrator(mapOf((1 to 2) to failing))
        assertThrows(IllegalStateException::class.java) {
            runTest { migrator.migrate(db, fromVersion = 1, toVersion = 2) }
        }
        // The transaction rolled back: version unchanged, no event, no column.
        assertEquals(1, currentVersion())
        assertNull(lastMigrationToVersion())
        assertEquals(0, migrationEventCount())
        db.query("SELECT * FROM profiles LIMIT 0").use { cursor ->
            assertTrue("doomed_column must not survive rollback", cursor.getColumnIndex("doomed_column") < 0)
        }
    }

    @Test
    fun test_migrator_idempotency() =
        runTest {
            val migrator = ConstraintGraphMigrator(mapOf((1 to 2) to syntheticV1ToV2))
            migrator.migrate(db, fromVersion = 1, toVersion = 2)
            // Re-running at the already-current version is a no-op.
            migrator.migrate(db, fromVersion = 2, toVersion = 2)
            assertEquals(2, currentVersion())
            assertEquals(1, migrationEventCount())
        }
}
