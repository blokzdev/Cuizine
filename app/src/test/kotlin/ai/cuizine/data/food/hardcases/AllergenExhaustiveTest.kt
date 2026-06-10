package ai.cuizine.data.food.hardcases

import ai.cuizine.core.di.SchemaMetadataSeedCallback
import ai.cuizine.data.database.CuizineDatabase
import ai.cuizine.data.food.AiIngredientCategorizer
import ai.cuizine.data.food.CanonicalIngredientEntry
import ai.cuizine.data.food.ExternalFoodSources
import ai.cuizine.data.food.ExternalLookupResult
import ai.cuizine.data.food.FoodDataProvider
import ai.cuizine.data.food.SourceMetadata
import ai.cuizine.data.food.bundle.BundleLoader
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.engine.types.AddedContext
import ai.cuizine.engine.types.AvoidException
import ai.cuizine.engine.types.AvoidPayload
import ai.cuizine.engine.types.AvoidTarget
import ai.cuizine.engine.types.ConstraintScope
import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.HouseholdScope
import ai.cuizine.engine.types.ProfileScope
import ai.cuizine.engine.types.ProvenanceRecord
import ai.cuizine.engine.types.Severity
import ai.cuizine.engine.types.SuggestionIngredient
import ai.cuizine.engine.types.SuggestionInput
import ai.cuizine.engine.types.TemporalScope
import ai.cuizine.engine.validator.SuggestionValidator
import ai.cuizine.shared.fixtures.SukhiFixtures
import ai.cuizine.shared.types.Constraint
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Fixed clock so cache TTLs never interfere with the matrix. */
private const val FIXED_NOW = "2026-06-10T12:00:00Z"

/** Resolves nowhere: not in the bundle, not in the external fixtures. */
private const val UNKNOWN_INGREDIENT = "unlabelled market snack"

/**
 * The allergen-exhaustive suite (`testing-strategy.md`: 9 allergens × 5
 * scenarios), run against the REAL three-layer Food Data Provider
 * (cache → bundle → external, ADR 0012) feeding the deterministic validator
 * (ADR 0010; `constraint-engine-spec.md` §4/§7).
 *
 * Per allergen, against an Inviolable category-kind Avoid:
 * 1. LITERAL — the bundle ingredient that IS the allergen violates.
 * 2. CATEGORICAL — a different carrier of the allergen tag violates
 *    (bundle carrier where one exists, external fixture otherwise).
 * 3. HIDDEN-IN-PRODUCT — a composite whose name never says the allergen
 *    violates because its `allergens` set carries the tag.
 * 4. AI-FALLBACK-REJECT — an unknown ingredient under an Inviolable avoid is
 *    rejected outright with ZERO AI-categorizer invocations (§7 Step 4, the
 *    safety floor).
 * 5. USER-CONFIRMED-SAFE — an exceptions entry carves out one named
 *    ingredient while every other carrier still violates (the classic
 *    "no dairy, but ghee is fine").
 */
@RunWith(RobolectricTestRunner::class)
class AllergenExhaustiveTest {
    private lateinit var database: CuizineDatabase
    private lateinit var provider: FoodDataProvider
    private lateinit var validator: SuggestionValidator
    private var aiCalls = 0

    /** External-source carrier fixture (the FoodDataProviderTest injection pattern). */
    private fun carrier(
        displayName: String,
        vararg allergens: String,
        categories: List<String> = listOf("composite_product"),
    ): Pair<String, ExternalLookupResult> =
        BundleLoader.normalize(displayName) to
            ExternalLookupResult(
                entry =
                    CanonicalIngredientEntry(
                        displayName = displayName,
                        categories = categories,
                        allergens = allergens.toList(),
                        sourceMetadata =
                            SourceMetadata(source = "usda", sourceEntryId = "fixture", retrievedAt = FIXED_NOW),
                    ),
                confidence = "high_usda",
                sourceLayer = "usda",
            )

    private val externalFixtures: Map<String, ExternalLookupResult> =
        mapOf(
            // Categorical carriers for allergens with a single bundle entry.
            carrier("Peanut butter", "peanut", categories = listOf("spread")),
            carrier("Cashew", "tree_nut", categories = listOf("tree_nut")),
            carrier("Mayonnaise", "egg", categories = listOf("condiment")),
            carrier("Tofu", "soy", categories = listOf("protein", "soy_product")),
            carrier("Anchovy", "fish", categories = listOf("seafood")),
            carrier("Crab", "shellfish", categories = listOf("seafood", "shellfish")),
            carrier("Tahini", "sesame", categories = listOf("condiment")),
            // Hidden-in-product composites: the name never says the allergen.
            carrier("Satay marinade", "peanut"),
            carrier("Korma paste", "tree_nut"),
            carrier("Naan mix", "dairy"),
            carrier("Hakka noodles", "egg"),
            carrier("Veggie cutlet mix", "wheat"),
            carrier("Vegetable bouillon cube", "soy"),
            carrier("Worcestershire sauce", "fish"),
            carrier("Tom yum paste", "shellfish"),
            carrier("Hummus spread", "sesame"),
        )

    private val external =
        object : ExternalFoodSources {
            override suspend fun lookupByName(name: String): ExternalLookupResult? =
                externalFixtures[BundleLoader.normalize(name)]
        }

    @Before
    fun setUp() {
        aiCalls = 0
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
                        override suspend fun categorize(name: String): CanonicalIngredientEntry? {
                            aiCalls += 1
                            return null
                        }
                    },
                clock = EngineClock { FIXED_NOW },
            )
        validator = SuggestionValidator(provider)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // -- Constraint + meal builders (SukhiFixtures helper pattern, adapted inline) --

    private fun inviolableAvoid(
        allergen: String,
        exceptions: List<AvoidException> = emptyList(),
    ): Constraint =
        Constraint(
            id = "hardcase-avoid-$allergen",
            profileId = SukhiFixtures.PROFILE_ID,
            type = ConstraintType.Avoid,
            severity = Severity.Inviolable,
            humanLabel = "Never any $allergen — allergy",
            scope =
                ConstraintScope(
                    temporal = TemporalScope(kind = "always"),
                    household = HouseholdScope(mode = "self"),
                    profile = ProfileScope(profileId = SukhiFixtures.PROFILE_ID),
                ),
            payload =
                AvoidPayload(
                    target = AvoidTarget(kind = "category", value = allergen),
                    exceptions = exceptions,
                ),
            provenance =
                ProvenanceRecord(
                    source = "user_direct",
                    addedAt = "2026-06-01T14:30:00Z",
                    addedContext = AddedContext(flow = "constraint_conversation", questionIndex = 2),
                    originalPhrasing = "Severe $allergen allergy — it can never appear, anywhere",
                    confidence = "high_user_direct",
                ),
            isActiveNow = true,
        )

    private fun mealOf(vararg ingredientNames: String): SuggestionInput =
        SuggestionInput(
            mealName = "Allergen hard-case meal",
            ingredients = ingredientNames.map { SuggestionIngredient(it, 100.0, "g") },
        )

    // -- Scenario assertions (constraint-engine-spec.md §4/§7) --

    /**
     * Scenarios 1–3: a resolved carrier of the allergen tag violates the
     * Inviolable avoid (§7 Step 3; category-kind targets match
     * `facts.categories` ∪ `facts.allergens`).
     */
    private suspend fun assertViolates(
        allergen: String,
        ingredientName: String,
    ) {
        val verdict = validator.validate(listOf(inviolableAvoid(allergen)), mealOf(ingredientName))
        assertFalse("'$ingredientName' must violate the inviolable $allergen avoid", verdict.passed)
        val violation = verdict.violations.single()
        assertEquals(ingredientName, violation.ingredientName)
        assertEquals(Severity.Inviolable, violation.severity)
    }

    /**
     * Scenario 4 — the safety floor (§7 Step 4): an unknown ingredient under
     * an Inviolable avoid is rejected outright; the AI categorizer is NEVER
     * consulted (zero invocations).
     */
    private suspend fun assertSafetyFloor(allergen: String) {
        val verdict = validator.validate(listOf(inviolableAvoid(allergen)), mealOf(UNKNOWN_INGREDIENT))
        assertFalse("unknown ingredient under inviolable $allergen avoid must reject", verdict.passed)
        val violation = verdict.violations.single()
        assertEquals(UNKNOWN_INGREDIENT, violation.ingredientName)
        assertEquals(Severity.Inviolable, violation.severity)
        assertEquals(ConstraintType.Avoid, violation.constraintType)
        assertEquals("the Inviolable safety floor forbids AI categorization", 0, aiCalls)
    }

    /**
     * Scenario 5 — user-confirmed-safe (§4 exceptions): the excepted
     * ingredient passes the same avoid while another carrier still violates.
     */
    private suspend fun assertExceptionCarveOut(
        allergen: String,
        excepted: String,
        stillBlocked: String,
    ) {
        val constraint =
            inviolableAvoid(
                allergen,
                exceptions =
                    listOf(
                        AvoidException(kind = "ingredient", value = excepted, reason = "user-confirmed safe"),
                    ),
            )
        val safe = validator.validate(listOf(constraint), mealOf(excepted))
        assertTrue("'$excepted' is user-confirmed safe under the $allergen avoid", safe.passed)
        val blocked = validator.validate(listOf(constraint), mealOf(stillBlocked))
        assertFalse("'$stillBlocked' must still violate the $allergen avoid", blocked.passed)
        assertEquals(stillBlocked, blocked.violations.single().ingredientName)
    }

    // -- peanut --------------------------------------------------------------

    @Test
    fun peanut_literal_peanutIsTheAllergen_rejected() = runTest { assertViolates("peanut", "peanut") }

    @Test
    fun peanut_categorical_peanutButterCarriesTheAllergen_rejected() =
        runTest { assertViolates("peanut", "peanut butter") }

    @Test
    fun peanut_hiddenInProduct_satayMarinadeCarriesPeanut_rejected() =
        runTest { assertViolates("peanut", "satay marinade") }

    @Test
    fun peanut_unknownIngredient_safetyFloorRejectsWithZeroAiCalls() = runTest { assertSafetyFloor("peanut") }

    @Test
    fun peanut_exception_peanutConfirmedSafe_peanutButterStillRejected() =
        runTest { assertExceptionCarveOut("peanut", excepted = "peanut", stillBlocked = "peanut butter") }

    // -- tree_nut ------------------------------------------------------------

    @Test
    fun treeNut_literal_almondIsTheAllergen_rejected() = runTest { assertViolates("tree_nut", "almond") }

    @Test
    fun treeNut_categorical_cashewCarriesTheAllergen_rejected() = runTest { assertViolates("tree_nut", "cashew") }

    @Test
    fun treeNut_hiddenInProduct_kormaPasteCarriesTreeNut_rejected() =
        runTest { assertViolates("tree_nut", "korma paste") }

    @Test
    fun treeNut_unknownIngredient_safetyFloorRejectsWithZeroAiCalls() = runTest { assertSafetyFloor("tree_nut") }

    @Test
    fun treeNut_exception_almondConfirmedSafe_cashewStillRejected() =
        runTest { assertExceptionCarveOut("tree_nut", excepted = "almond", stillBlocked = "cashew") }

    // -- dairy ---------------------------------------------------------------

    @Test
    fun dairy_literal_milkIsTheAllergen_rejected() = runTest { assertViolates("dairy", "milk") }

    @Test
    fun dairy_categorical_gheeCarriesTheAllergen_rejected() = runTest { assertViolates("dairy", "ghee") }

    @Test
    fun dairy_hiddenInProduct_naanMixCarriesDairy_rejected() = runTest { assertViolates("dairy", "naan mix") }

    @Test
    fun dairy_unknownIngredient_safetyFloorRejectsWithZeroAiCalls() = runTest { assertSafetyFloor("dairy") }

    @Test
    fun dairy_exception_gheeConfirmedSafe_milkStillRejected() =
        runTest { assertExceptionCarveOut("dairy", excepted = "ghee", stillBlocked = "milk") }

    // -- egg -----------------------------------------------------------------

    @Test
    fun egg_literal_eggIsTheAllergen_rejected() = runTest { assertViolates("egg", "egg") }

    @Test
    fun egg_categorical_mayonnaiseCarriesTheAllergen_rejected() = runTest { assertViolates("egg", "mayonnaise") }

    @Test
    fun egg_hiddenInProduct_hakkaNoodlesCarryEgg_rejected() = runTest { assertViolates("egg", "hakka noodles") }

    @Test
    fun egg_unknownIngredient_safetyFloorRejectsWithZeroAiCalls() = runTest { assertSafetyFloor("egg") }

    @Test
    fun egg_exception_eggConfirmedSafe_mayonnaiseStillRejected() =
        runTest { assertExceptionCarveOut("egg", excepted = "egg", stillBlocked = "mayonnaise") }

    // -- wheat ---------------------------------------------------------------

    @Test
    fun wheat_literal_wheatIsTheAllergen_rejected() = runTest { assertViolates("wheat", "wheat") }

    @Test
    fun wheat_categorical_attaCarriesTheAllergen_rejected() = runTest { assertViolates("wheat", "atta") }

    @Test
    fun wheat_hiddenInProduct_veggieCutletMixCarriesWheat_rejected() =
        runTest { assertViolates("wheat", "veggie cutlet mix") }

    @Test
    fun wheat_unknownIngredient_safetyFloorRejectsWithZeroAiCalls() = runTest { assertSafetyFloor("wheat") }

    @Test
    fun wheat_exception_attaConfirmedSafe_wheatStillRejected() =
        runTest { assertExceptionCarveOut("wheat", excepted = "atta", stillBlocked = "wheat") }

    // -- soy -----------------------------------------------------------------

    @Test
    fun soy_literal_soySauceIsTheAllergen_rejected() = runTest { assertViolates("soy", "soy sauce") }

    @Test
    fun soy_categorical_tofuCarriesTheAllergen_rejected() = runTest { assertViolates("soy", "tofu") }

    @Test
    fun soy_hiddenInProduct_bouillonCubeCarriesSoy_rejected() =
        runTest { assertViolates("soy", "vegetable bouillon cube") }

    @Test
    fun soy_unknownIngredient_safetyFloorRejectsWithZeroAiCalls() = runTest { assertSafetyFloor("soy") }

    @Test
    fun soy_exception_soySauceConfirmedSafe_tofuStillRejected() =
        runTest { assertExceptionCarveOut("soy", excepted = "soy sauce", stillBlocked = "tofu") }

    // -- fish ----------------------------------------------------------------

    @Test
    fun fish_literal_fishIsTheAllergen_rejected() = runTest { assertViolates("fish", "fish") }

    @Test
    fun fish_categorical_anchovyCarriesTheAllergen_rejected() = runTest { assertViolates("fish", "anchovy") }

    @Test
    fun fish_hiddenInProduct_worcestershireSauceCarriesFish_rejected() =
        runTest { assertViolates("fish", "worcestershire sauce") }

    @Test
    fun fish_unknownIngredient_safetyFloorRejectsWithZeroAiCalls() = runTest { assertSafetyFloor("fish") }

    @Test
    fun fish_exception_fishConfirmedSafe_anchovyStillRejected() =
        runTest { assertExceptionCarveOut("fish", excepted = "fish", stillBlocked = "anchovy") }

    // -- shellfish -----------------------------------------------------------

    @Test
    fun shellfish_literal_shrimpIsTheAllergen_rejected() = runTest { assertViolates("shellfish", "shrimp") }

    @Test
    fun shellfish_categorical_crabCarriesTheAllergen_rejected() = runTest { assertViolates("shellfish", "crab") }

    @Test
    fun shellfish_hiddenInProduct_tomYumPasteCarriesShellfish_rejected() =
        runTest { assertViolates("shellfish", "tom yum paste") }

    @Test
    fun shellfish_unknownIngredient_safetyFloorRejectsWithZeroAiCalls() = runTest { assertSafetyFloor("shellfish") }

    @Test
    fun shellfish_exception_shrimpConfirmedSafe_crabStillRejected() =
        runTest { assertExceptionCarveOut("shellfish", excepted = "shrimp", stillBlocked = "crab") }

    // -- sesame --------------------------------------------------------------

    @Test
    fun sesame_literal_sesameIsTheAllergen_rejected() = runTest { assertViolates("sesame", "sesame") }

    @Test
    fun sesame_categorical_tahiniCarriesTheAllergen_rejected() = runTest { assertViolates("sesame", "tahini") }

    @Test
    fun sesame_hiddenInProduct_hummusSpreadCarriesSesame_rejected() =
        runTest { assertViolates("sesame", "hummus spread") }

    @Test
    fun sesame_unknownIngredient_safetyFloorRejectsWithZeroAiCalls() = runTest { assertSafetyFloor("sesame") }

    @Test
    fun sesame_exception_sesameConfirmedSafe_tahiniStillRejected() =
        runTest { assertExceptionCarveOut("sesame", excepted = "sesame", stillBlocked = "tahini") }
}
