package ai.cuizine.agents

import ai.cuizine.agents.providers.FakeModelProvider
import ai.cuizine.agents.providers.ProviderRouter
import ai.cuizine.core.di.SchemaMetadataSeedCallback
import ai.cuizine.data.database.CuizineDatabase
import ai.cuizine.data.database.entities.ProfileEntity
import ai.cuizine.data.food.FakeExternalFoodSources
import ai.cuizine.data.food.FoodDataProvider
import ai.cuizine.data.food.bundle.BundleLoader
import ai.cuizine.data.repository.EngineProfileRepository
import ai.cuizine.data.repository.RoomConstraintStore
import ai.cuizine.engine.ConstraintGraphEngine
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.shared.types.AlphaEventLogger
import ai.cuizine.shared.types.agents.UserIntent
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The agent layer end to end on zero credentials (`testing-strategy.md` §6
 * fast-path evals): real engine over Room, real Food Data Provider over the
 * curated bundle, real orchestrator — FakeModelProvider standing in for the
 * LLMs. Covers the §9 canonical patterns: free-text capture (A), the
 * validated happy path (B), and regeneration after failure (C).
 */
@RunWith(RobolectricTestRunner::class)
class OrchestratorIntegrationTest {
    private lateinit var database: CuizineDatabase
    private lateinit var orchestrator: Orchestrator
    private val profileId = EngineProfileRepository.PRIMARY_PROFILE_ID
    private val clock = EngineClock { "2026-06-11T22:00:00Z" } // a Thursday
    private val loggedEvents = mutableListOf<Pair<String, String>>()

    private val events =
        object : AlphaEventLogger {
            override suspend fun log(
                eventType: String,
                severity: String,
                payloadJson: String,
                profileId: String?,
            ) {
                loggedEvents += eventType to severity
            }
        }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, CuizineDatabase::class.java)
                .addCallback(SchemaMetadataSeedCallback)
                .allowMainThreadQueries()
                .build()
        runBlocking {
            database.profileDao().insert(
                ProfileEntity(
                    id = profileId,
                    displayName = "Myself",
                    relationshipType = "self",
                    timezone = "America/Toronto",
                    createdAt = clock.nowIso(),
                    modifiedAt = clock.nowIso(),
                ),
            )
        }
        val foodData =
            FoodDataProvider(
                cacheDao = database.foodDataCacheDao(),
                bundleLoader = BundleLoader(context),
                externalSources = FakeExternalFoodSources(),
                aiCategorizer =
                    ai.cuizine.data.food
                        .NullAiIngredientCategorizer(),
                clock = clock,
            )
        val engine =
            ConstraintGraphEngine(
                store = RoomConstraintStore(database.constraintDao(), clock),
                foodData = foodData,
                clock = clock,
                profileTimezone = { "America/Toronto" },
            )
        val router =
            ProviderRouter(providers = emptyMap(), fake = FakeModelProvider(), events = events)
        val invoker = AgentInvoker(router, PromptStore(context), events)
        orchestrator = Orchestrator(engine, invoker, events, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun patternA_freeTextUpdate_writesContextualStateThroughEngine() =
        runTest {
            val response =
                orchestrator.handle(
                    UserIntent.FreeTextUpdate(profileId, "my sugar was 9.2 this morning"),
                )
            val reply = response as Orchestrator.Response.ConversationReply
            assertTrue(reply.message.contains("gentler on your sugar"))
            assertTrue(reply.appliedConstraintLabels.isNotEmpty())
            // The contextual constraint actually landed in Room via the engine.
            val live = database.constraintDao().getLiveForProfile(profileId)
            assertTrue(live.any { it.type == "contextual" })
        }

    @Test
    fun patternB_mealRequest_validatedHappyPath() =
        runTest {
            val response =
                orchestrator.handle(UserIntent.RequestMealSuggestion(profileId, "dinner tonight"))
            val ready = response as Orchestrator.Response.SuggestionReady
            assertEquals("Masoor dal with spinach", ready.chefOutput.mealName)
            assertTrue(ready.verdict.passed)
            assertTrue(loggedEvents.any { it.first == "suggestion_validated_passed" })
        }

    @Test
    fun patternC_regenerationLoop_failsThenCorrects_withSeverityZeroLogged() =
        runTest {
            // Seed a religious-cultural beef avoid so the scripted "steak"
            // meal fails REAL validation on attempt 1.
            orchestrator.handle(
                UserIntent.FreeTextUpdate(profileId, "We keep vegetarian on tuesdays, and avoiding dairy"),
            )
            val avoidBeef = ai.cuizine.shared.fixtures.SukhiFixtures.avoidBeef
            val engineStore = RoomConstraintStore(database.constraintDao(), clock)
            engineStore.insert(
                avoidBeef.copy(
                    id = "test-avoid-beef",
                    profileId = profileId,
                    scope =
                        avoidBeef.scope.copy(
                            profile = avoidBeef.scope.profile.copy(profileId = profileId),
                        ),
                ),
            )

            val response =
                orchestrator.handle(UserIntent.RequestMealSuggestion(profileId, "a steak dinner"))
            val ready = response as Orchestrator.Response.SuggestionReady
            // Attempt 1 (beef keema) failed real validation; attempt 2 corrected.
            assertTrue(ready.verdict.passed)
            assertTrue(ready.chefOutput.mealName != "Beef keema with peas")
            assertTrue(loggedEvents.any { it.first == "suggestion_validated_failed" && it.second == "severity_zero" })
            assertTrue(loggedEvents.any { it.first == "suggestion_regenerated" })
        }

    @Test
    fun onboarding_beginAsksFirstQuestion_fiveTurnsCompleteWithCapturedConstraints() =
        runTest {
            val begin = orchestrator.handle(UserIntent.BeginConstraintConversation(profileId))
            val opening = begin as Orchestrator.Response.ConversationReply
            assertTrue(opening.message.contains("What kind of food"))

            val answers =
                listOf(
                    "Punjabi food, what my mother taught me",
                    "All five of us",
                    "diabetes now, and my stomach has bad days",
                    "Rajma! And anything with aloo",
                    "tuesdays we keep vegetarian",
                )
            var lastReply: Orchestrator.Response.ConversationReply? = null
            answers.forEach { answer ->
                lastReply =
                    orchestrator.handle(
                        UserIntent.ContinueConstraintConversation(profileId, answer),
                    ) as Orchestrator.Response.ConversationReply
            }
            assertTrue(lastReply!!.isConversationComplete)
            // Captured constraints landed through the engine: the diabetes
            // limit and the Tuesday-vegetarian avoid at minimum.
            val live = database.constraintDao().getLiveForProfile(profileId)
            assertTrue(live.any { it.type == "limit" && it.severity == "medical" })
            assertTrue(live.any { it.type == "avoid" && it.severity == "religious_cultural" })
        }
}
