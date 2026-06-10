package ai.cuizine.data.food

import ai.cuizine.core.di.SchemaMetadataSeedCallback
import ai.cuizine.data.database.CuizineDatabase
import ai.cuizine.data.food.bundle.BundleLoader
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.engine.ports.IngredientResolution
import ai.cuizine.engine.types.SuggestionIngredient
import ai.cuizine.engine.types.SuggestionInput
import ai.cuizine.engine.validator.SuggestionValidator
import ai.cuizine.shared.fixtures.SukhiFixtures
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The three-layer Food Data Provider (ADR 0012; `data-model.md` §5): layer
 * ordering, caching with TTLs, bundle corrections outranking external
 * sources — and the canonical Sukhi-and-alliums case (`roadmap.md` Phase 4
 * exit: categorical reasoning against real food data).
 */
@RunWith(RobolectricTestRunner::class)
class FoodDataProviderTest {
    private lateinit var database: CuizineDatabase
    private lateinit var provider: FoodDataProvider
    private var nowIso = "2026-06-10T12:00:00Z"
    private val externalCalls = mutableListOf<String>()

    private val external =
        object : ExternalFoodSources {
            val delegate = FakeExternalFoodSources()

            override suspend fun lookupByName(name: String): ExternalLookupResult? {
                externalCalls += name
                return delegate.lookupByName(name)
            }
        }

    private var aiEntry: CanonicalIngredientEntry? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, CuizineDatabase::class.java)
                .addCallback(SchemaMetadataSeedCallback)
                .allowMainThreadQueries()
                .build()
        provider =
            FoodDataProvider(
                cacheDao = database.foodDataCacheDao(),
                bundleLoader = BundleLoader(context),
                externalSources = external,
                aiCategorizer =
                    object : AiIngredientCategorizer {
                        override suspend fun categorize(name: String): CanonicalIngredientEntry? = aiEntry
                    },
                clock = EngineClock { nowIso },
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun bundleHit_resolvesWithCulturalVocabulary() =
        runTest {
            // "haldi" resolves through alternative_names to Turmeric (PRD §5
            // ingredient vocabulary).
            val result = provider.lookup("haldi")
            val known = result as IngredientResolution.Known
            assertEquals("turmeric", known.facts.canonicalName)
            assertEquals("high_bundle", known.confidence)
        }

    @Test
    fun layerOrder_bundleBeforeExternal_cacheBeforeBundle() =
        runTest {
            // Bundle item: external is never consulted.
            provider.lookup("rajma")
            assertTrue(externalCalls.isEmpty())
            // External item: consulted once, then served from cache.
            provider.lookup("quinoa")
            provider.lookup("quinoa")
            assertEquals(1, externalCalls.count { it == "quinoa" })
        }

    @Test
    fun externalHit_cachedWithSixMonthUsdaTtl_thenExpires() =
        runTest {
            provider.lookup("quinoa")
            // Five months later: still cached (no second external call).
            nowIso = "2026-11-01T12:00:00Z"
            provider.lookup("quinoa")
            assertEquals(1, externalCalls.count { it == "quinoa" })
            // Seven months later: TTL expired → external consulted again.
            nowIso = "2027-01-15T12:00:00Z"
            provider.lookup("quinoa")
            assertEquals(2, externalCalls.count { it == "quinoa" })
        }

    @Test
    fun unknownEverywhere_returnsUnknown() =
        runTest {
            assertEquals(IngredientResolution.Unknown, provider.lookup("xylotherm pods"))
        }

    @Test
    fun aiCategorization_cachedAsLowConfidence() =
        runTest {
            aiEntry =
                CanonicalIngredientEntry(
                    displayName = "Mystery green",
                    categories = listOf("vegetable", "leafy_green"),
                    sourceMetadata = SourceMetadata(source = "ai_fallback", retrievedAt = nowIso),
                )
            val facts = provider.categorizeWithAi("mystery green")
            assertNotNull(facts)
            // Second ask is served from cache even if the categorizer changes.
            aiEntry = null
            assertNotNull(provider.categorizeWithAi("mystery green"))
        }

    @Test
    fun sukhiAndAlliums_theCanonicalCategoricalCase() =
        runTest {
            // `roadmap.md` Phase 4: "The Sukhi-and-alliums case passes against
            // real food data." Ramps is a wild allium, fixed by a bundle
            // CORRECTION — exactly the medically-relevant miscategorization
            // the bundle exists to catch (ADR 0012).
            val validator = SuggestionValidator(provider)
            val flareAvoid = SukhiFixtures.avoidAlliumsDuringFlare.copy(isActiveNow = true)
            val meal =
                SuggestionInput(
                    mealName = "Spring greens sauté",
                    ingredients =
                        listOf(
                            SuggestionIngredient("ramps", 50.0, "g"),
                            SuggestionIngredient("spinach", 100.0, "g"),
                        ),
                )
            val verdict = validator.validate(listOf(flareAvoid), meal)
            assertFalse("ramps must be caught as an allium during a flare", verdict.passed)
            assertEquals("ramps", verdict.violations.single().ingredientName)
            // And the same meal without ramps passes.
            val safe =
                validator.validate(
                    listOf(flareAvoid),
                    SuggestionInput(
                        mealName = "Palak sauté",
                        ingredients = listOf(SuggestionIngredient("spinach", 100.0, "g")),
                    ),
                )
            assertTrue(safe.passed)
        }
}
