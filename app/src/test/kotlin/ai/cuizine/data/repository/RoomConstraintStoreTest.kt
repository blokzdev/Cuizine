package ai.cuizine.data.repository

import ai.cuizine.core.di.SchemaMetadataSeedCallback
import ai.cuizine.data.database.CuizineDatabase
import ai.cuizine.data.database.entities.ProfileEntity
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.shared.fixtures.AishaFixtures
import ai.cuizine.shared.fixtures.SukhiFixtures
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The physical half of the engine's persistence (`data-model.md` §4): every
 * payload type round-trips through the JSON columns byte-faithfully enough
 * to be value-equal, soft removal behaves, and the live/all split holds.
 */
@RunWith(RobolectricTestRunner::class)
class RoomConstraintStoreTest {
    private lateinit var database: CuizineDatabase
    private lateinit var store: RoomConstraintStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, CuizineDatabase::class.java)
                .addCallback(SchemaMetadataSeedCallback)
                .allowMainThreadQueries()
                .build()
        store = RoomConstraintStore(database.constraintDao(), EngineClock { NOW })
        // FK target rows for the two fixture profiles.
        kotlinx.coroutines.runBlocking {
            database.profileDao().insert(profileRow(SukhiFixtures.PROFILE_ID))
            database.profileDao().insert(profileRow(AishaFixtures.PROFILE_ID))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun profileRow(id: String) =
        ProfileEntity(
            id = id,
            displayName = "Fixture",
            relationshipType = "self",
            timezone = "America/Toronto",
            createdAt = NOW,
            modifiedAt = NOW,
        )

    @Test
    fun allFivePayloadTypes_roundTripValueEqual() =
        runTest {
            val samples = SukhiFixtures.constraints + AishaFixtures.constraints
            samples.forEach { store.insert(it) }
            val sukhiLoaded = store.loadLive(SukhiFixtures.PROFILE_ID).associateBy { it.id }
            val aishaLoaded = store.loadLive(AishaFixtures.PROFILE_ID).associateBy { it.id }
            samples.forEach { original ->
                val loaded = (sukhiLoaded[original.id] ?: aishaLoaded[original.id])
                assertNotNull("missing ${original.id}", loaded)
                assertEquals(original.type, loaded?.type)
                assertEquals(original.severity, loaded?.severity)
                assertEquals(original.payload, loaded?.payload)
                assertEquals(original.scope, loaded?.scope)
                assertEquals(original.provenance, loaded?.provenance)
            }
        }

    @Test
    fun markRemoved_movesRowFromLiveToAllOnly() =
        runTest {
            store.insert(SukhiFixtures.avoidBeef)
            store.markRemoved(SukhiFixtures.avoidBeef.id, NOW)
            assertTrue(store.loadLive(SukhiFixtures.PROFILE_ID).none { it.id == SukhiFixtures.avoidBeef.id })
            assertTrue(store.loadAll(SukhiFixtures.PROFILE_ID).any { it.id == SukhiFixtures.avoidBeef.id })
        }

    @Test
    fun loadById_findsRowAndReturnsNullForUnknown() =
        runTest {
            store.insert(SukhiFixtures.preferRajma)
            assertNotNull(store.loadById(SukhiFixtures.preferRajma.id))
            assertNull(store.loadById("no-such-id"))
        }

    private companion object {
        const val NOW = "2026-06-10T12:00:00Z"
    }
}
