package ai.cuizine.data.database

import ai.cuizine.core.di.SchemaMetadataSeedCallback
import ai.cuizine.data.database.entities.ConstraintEntity
import ai.cuizine.data.database.entities.ProfileEntity
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Schema-integrity tests (`testing-strategy.md` §9): foreign keys enforced,
 * soft-deleted rows stay readable, the food-cache lookup is unique, and the
 * schema_metadata row seeds on first launch.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaIntegrityTest {
    private lateinit var database: CuizineDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, CuizineDatabase::class.java)
                .addCallback(SchemaMetadataSeedCallback)
                .allowMainThreadQueries()
                .build()
        // Force open so callbacks run.
        database.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun profile(id: String = "profile-1") =
        ProfileEntity(
            id = id,
            displayName = "Myself",
            relationshipType = "self",
            timezone = "America/Toronto",
            createdAt = NOW,
            modifiedAt = NOW,
        )

    private fun constraint(
        id: String = "constraint-1",
        profileId: String = "profile-1",
    ) = ConstraintEntity(
        id = id,
        profileId = profileId,
        type = "avoid",
        severity = "medical",
        temporalScopeKind = "always",
        householdScopeMode = "self",
        scopeJson = "{}",
        payloadJson = "{}",
        provenanceJson = "{}",
        createdAt = NOW,
        modifiedAt = NOW,
    )

    @Test
    fun foreignKeys_rejectConstraintForMissingProfile() =
        runTest {
            val failure =
                runCatching {
                    database.constraintDao().insert(constraint(profileId = "no-such-profile"))
                }.exceptionOrNull()
            assertTrue(
                "Expected SQLiteConstraintException, got $failure",
                failure is SQLiteConstraintException,
            )
        }

    @Test
    fun foreignKeys_acceptConstraintForExistingProfile() =
        runTest {
            database.profileDao().insert(profile())
            database.constraintDao().insert(constraint())
            assertNotNull(database.constraintDao().getById("constraint-1"))
        }

    @Test
    fun softDelete_removedRowsStayReadable() =
        runTest {
            database.profileDao().insert(profile())
            database.profileDao().update(profile().copy(removedAt = NOW))
            // Excluded from the active view…
            assertTrue(database.profileDao().getActive().isEmpty())
            // …but never gone (`data-model.md` §2 soft deletes).
            assertNotNull(database.profileDao().getById("profile-1"))
        }

    @Test
    fun activeConstraintQuery_excludesRemovedAndExpired() =
        runTest {
            database.profileDao().insert(profile())
            database.constraintDao().insert(constraint(id = "live"))
            database.constraintDao().insert(constraint(id = "removed").copy(removedAt = NOW))
            database.constraintDao().insert(constraint(id = "expired").copy(expiresAt = "2020-01-01T00:00:00Z"))
            database.constraintDao().insert(constraint(id = "future").copy(expiresAt = "2099-01-01T00:00:00Z"))

            val active = database.constraintDao().getActiveForProfile("profile-1", NOW)
            assertEquals(setOf("live", "future"), active.map { it.id }.toSet())
        }

    @Test
    fun foodCacheLookup_isUniquePerKeyAndKind() {
        val db = database.openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO food_data_cache (id, lookup_key, lookup_kind, canonical_entry_json, confidence, " +
                "source_layer, first_cached_at, last_refreshed_at, schema_version) " +
                "VALUES ('a', 'onion', 'ingredient_name', '{}', 'high_bundle', 'bundle', '$NOW', '$NOW', 1)",
        )
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(
                "INSERT INTO food_data_cache (id, lookup_key, lookup_kind, canonical_entry_json, confidence, " +
                    "source_layer, first_cached_at, last_refreshed_at, schema_version) " +
                    "VALUES ('b', 'onion', 'ingredient_name', '{}', 'high_usda', 'usda', '$NOW', '$NOW', 1)",
            )
        }
    }

    @Test
    fun schemaMetadata_seedsVersionOneOnFirstLaunch() =
        runTest {
            val metadata = database.schemaMetadataDao().get()
            assertNotNull(metadata)
            assertEquals(1, metadata?.currentVersion)
        }

    private companion object {
        const val NOW = "2026-06-10T12:00:00Z"
    }
}
