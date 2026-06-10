package ai.cuizine.data.food.hardcases

import ai.cuizine.core.di.SchemaMetadataSeedCallback
import ai.cuizine.data.database.CuizineDatabase
import ai.cuizine.data.food.FakeExternalFoodSources
import ai.cuizine.data.food.FoodDataProvider
import ai.cuizine.data.food.NullAiIngredientCategorizer
import ai.cuizine.data.food.bundle.BundleLoader
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.engine.types.AvoidPayload
import ai.cuizine.engine.types.AvoidTarget
import ai.cuizine.engine.types.Severity
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Religious-dietary hard cases (`testing-strategy.md` §5: halal, kosher,
 * Hindu vegetarian, Jain, Buddhist) run through the REAL three-layer
 * FoodDataProvider (cache → curated bundle → external; ADR 0012) and the
 * deterministic validator (`constraint-engine-spec.md` §7). All constraints
 * sit at the ReligiousCultural tier — co-equal with Medical (§4) — unless a
 * test says otherwise. Category-kind avoid targets match against
 * facts.categories ∪ facts.allergens, and the bundle's religious_tags are
 * folded into categories by `CanonicalIngredientEntry.toFacts()`.
 */
@RunWith(RobolectricTestRunner::class)
class ReligiousDietaryTest {
    private lateinit var database: CuizineDatabase
    private lateinit var validator: SuggestionValidator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, CuizineDatabase::class.java)
                .addCallback(SchemaMetadataSeedCallback)
                .allowMainThreadQueries()
                .build()
        val provider =
            FoodDataProvider(
                cacheDao = database.foodDataCacheDao(),
                bundleLoader = BundleLoader(context),
                externalSources = FakeExternalFoodSources(),
                aiCategorizer = NullAiIngredientCategorizer(),
                clock = EngineClock { "2026-06-10T12:00:00Z" },
            )
        validator = SuggestionValidator(provider)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── Builders (SukhiFixtures helper pattern, adapted inline) ──────────

    private fun meal(vararg names: String) =
        SuggestionInput(
            mealName = "test meal",
            ingredients = names.map { SuggestionIngredient(it, 100.0, "g") },
        )

    private fun avoid(
        id: String,
        targetKind: String,
        targetValue: String,
        severity: Severity = Severity.ReligiousCultural,
    ) = SukhiFixtures.avoidBeef.copy(
        id = "religious-avoid-$id",
        severity = severity,
        humanLabel = "Avoid $targetKind '$targetValue'",
        payload = AvoidPayload(target = AvoidTarget(kind = targetKind, value = targetValue)),
    )

    // ── HALAL ────────────────────────────────────────────────────────────

    @Test
    fun halal_containsPorkAvoidCatchesBaconAndPork() =
        runTest {
            // §7 Step 3 avoid: the bundle's contains_pork religious tag is a
            // category-kind match for both Pork and Bacon; rice is untouched.
            val result =
                validator.validate(listOf(avoid("pork", "category", "contains_pork")), meal("bacon", "pork", "rice"))
            assertFalse(result.passed)
            assertEquals(setOf("bacon", "pork"), result.violations.map { it.ingredientName }.toSet())
        }

    @Test
    fun halal_containsAlcoholAvoidCatchesCookingWine() =
        runTest {
            // §7 Step 3 avoid: Cooking wine carries contains_alcohol; the
            // alcohol never "cooks off" into a pass.
            val result =
                validator.validate(
                    listOf(avoid("alcohol", "category", "contains_alcohol")),
                    meal("cooking wine", "chicken"),
                )
            assertFalse(result.passed)
            assertEquals("cooking wine", result.violations.single().ingredientName)
        }

    @Test
    fun halal_nonHalalAvoidCatchesPorkBaconAndCookingWine() =
        runTest {
            // §7 Step 3 avoid: the non_halal tag spans pork products AND
            // alcohol — one constraint catches all three.
            val result =
                validator.validate(
                    listOf(avoid("non-halal", "category", "non_halal")),
                    meal("pork", "bacon", "cooking wine", "rice"),
                )
            assertFalse(result.passed)
            assertEquals(setOf("pork", "bacon", "cooking wine"), result.violations.map { it.ingredientName }.toSet())
        }

    @Test
    fun halal_nonHalalRiskAvoidCatchesGelatinWithHonestRiskReason() =
        runTest {
            // §7 Step 3 avoid + the trust posture: Gelatin is tagged
            // non_halal_risk (source-animal unknown), not non_halal — the
            // violation names the RISK category honestly rather than
            // overclaiming certainty.
            val result =
                validator.validate(listOf(avoid("gelatin", "category", "non_halal_risk")), meal("gelatin", "mango"))
            assertFalse(result.passed)
            val violation = result.violations.single()
            assertEquals("gelatin", violation.ingredientName)
            assertTrue(violation.reason.contains("non_halal_risk"))
        }

    @Test
    fun halal_safeVegetarianMealPassesAllHalalAvoidsWithoutFalsePositives() =
        runTest {
            // §7 Step 3 avoid: plain category tags (legume/dal, leafy_green,
            // dairy) never false-positive against the halal tag family.
            // Deliberately NOT a chicken meal: halal status of meat depends on
            // slaughter, which is not a species-level bundle fact — the suite
            // never certifies a meat as halal.
            val halalAvoids =
                listOf(
                    avoid("pork", "category", "contains_pork"),
                    avoid("alcohol", "category", "contains_alcohol"),
                    avoid("non-halal", "category", "non_halal"),
                    avoid("risk", "category", "non_halal_risk"),
                )
            val result = validator.validate(halalAvoids, meal("masoor dal", "spinach", "yogurt"))
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
            assertTrue(result.disclosureNotes.isEmpty())
        }

    // ── KOSHER ───────────────────────────────────────────────────────────

    @Test
    fun kosher_nonKosherAvoidCatchesShrimpAndBacon() =
        runTest {
            // §7 Step 3 avoid: shellfish (Shrimp) and pork products (Bacon)
            // both carry the non_kosher tag; one constraint catches both.
            val result =
                validator.validate(listOf(avoid("kosher", "category", "non_kosher")), meal("shrimp", "bacon", "rice"))
            assertFalse(result.passed)
            assertEquals(setOf("shrimp", "bacon"), result.violations.map { it.ingredientName }.toSet())
        }

    @Test
    fun kosher_nonKosherAvoidLetsFinFishPass() =
        runTest {
            // §7 Step 3 avoid: Fish (fins and scales) carries no non_kosher
            // tag — only shellfish does — so it passes the same constraint.
            val result = validator.validate(listOf(avoid("kosher", "category", "non_kosher")), meal("fish"))
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
        }

    @Test
    fun kosher_meatAndDairyMixingIsNotMechanicallyCheckableInV1() =
        runTest {
            // KNOWN v1 LIMITATION (founder report): kashrut's meat+dairy
            // mixing prohibition is a cross-ingredient COMBINATION rule, and
            // `constraint-engine-spec.md` §3's five types all evaluate
            // ingredients (or property sums) independently — no combination
            // rule exists. A beef + milk meal therefore passes a non_kosher
            // avoid today. This test documents current behavior; it is not an
            // endorsement of it.
            val result = validator.validate(listOf(avoid("kosher", "category", "non_kosher")), meal("beef", "milk"))
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
        }

    // ── HINDU VEGETARIAN ─────────────────────────────────────────────────

    @Test
    fun hinduVegetarian_meatCategoryAvoidCatchesBeefAndChicken() =
        runTest {
            // §7 Step 3 avoid: Beef and Chicken both carry the meat tag (as
            // category and religious tag); spinach is untouched.
            val result =
                validator.validate(listOf(avoid("veg", "category", "meat")), meal("beef", "chicken", "spinach"))
            assertFalse(result.passed)
            assertEquals(setOf("beef", "chicken"), result.violations.map { it.ingredientName }.toSet())
        }

    @Test
    fun hinduVegetarian_fishIsCaughtOnlyViaTheMeatForSomeHouseholdTag() =
        runTest {
            // §7 Step 3 avoid: the bundle tags Fish meat_for_some, NOT meat —
            // pescatarian-leaning households differ. A plain meat avoid lets
            // fish pass; the household that counts fish as meat adds the
            // meat_for_some avoid and catches it.
            val plainMeat = validator.validate(listOf(avoid("veg", "category", "meat")), meal("fish"))
            assertTrue(plainMeat.passed)
            val strictHousehold =
                validator.validate(listOf(avoid("fish-too", "category", "meat_for_some")), meal("fish"))
            assertFalse(strictHousehold.passed)
            assertEquals("fish", strictHousehold.violations.single().ingredientName)
        }

    @Test
    fun hinduVegetarian_sukhiAvoidBeefAloneLetsChickenPass() =
        runTest {
            // §7 Step 3 avoid + PRD §2: Sukhi's pattern is an ingredient-kind
            // beef avoid, not blanket vegetarianism — chicken passes, beef
            // does not.
            val chickenMeal = validator.validate(listOf(SukhiFixtures.avoidBeef), meal("chicken", "rice"))
            assertTrue(chickenMeal.passed)
            val beefMeal = validator.validate(listOf(SukhiFixtures.avoidBeef), meal("beef"))
            assertFalse(beefMeal.passed)
            assertEquals("beef", beefMeal.violations.single().ingredientName)
        }

    @Test
    fun hinduVegetarian_eggIsCaughtOnlyWhenTheHouseholdSaysSo() =
        runTest {
            // §7 Step 3 avoid: egg handling differs by household. Egg carries
            // non_vegetarian_for_some, not meat — a plain meat avoid lets it
            // pass; the eggless household's explicit avoid catches it.
            val meatOnly = validator.validate(listOf(avoid("veg", "category", "meat")), meal("egg"))
            assertTrue(meatOnly.passed)
            val egglessHousehold =
                validator.validate(listOf(avoid("eggless", "category", "non_vegetarian_for_some")), meal("egg"))
            assertFalse(egglessHousehold.passed)
            assertEquals("egg", egglessHousehold.violations.single().ingredientName)
        }

    // ── JAIN ─────────────────────────────────────────────────────────────

    @Test
    fun jain_alliumAvoidCatchesOnionGarlicLeekAndRampsViaCorrection() =
        runTest {
            // §7 Step 3 avoid + ADR 0012: Onion, Garlic, and Leek carry the
            // allium category in bundle entries; ramps resolves through the
            // bundle CORRECTION (the wild allium external sources
            // miscategorize) and is caught the same way.
            val result =
                validator.validate(
                    listOf(avoid("jain-allium", "category", "allium")),
                    meal("onion", "garlic", "leek", "ramps"),
                )
            assertFalse(result.passed)
            assertEquals(
                setOf("onion", "garlic", "leek", "ramps"),
                result.violations.map { it.ingredientName }.toSet(),
            )
        }

    @Test
    fun jain_asafoetidaTheAlliumSubstitutePassesTheAlliumAvoid() =
        runTest {
            // §7 Step 3 avoid: hing is tagged allium_substitute, not allium —
            // the canonical allium-free workaround is never false-positived.
            val result =
                validator.validate(listOf(avoid("jain-allium", "category", "allium")), meal("asafoetida", "moong dal"))
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
        }

    @Test
    fun jain_rootVegetableAvoidCatchesPotatoOnionGarlicAndGinger() =
        runTest {
            // Bundle gap found by this suite's first authoring pass, fixed in
            // the same phase: Potato, Onion, Garlic, and Ginger now carry the
            // root_vegetable tag, so Jain root-vegetable avoidance holds.
            val constraint = listOf(avoid("jain-root", "category", "root_vegetable"))
            listOf("potato", "onion", "garlic", "ginger").forEach { ingredient ->
                val result = validator.validate(constraint, meal(ingredient))
                assertFalse("$ingredient must be caught as a root vegetable", result.passed)
            }
            // Above-ground produce stays available.
            assertTrue(validator.validate(constraint, meal("spinach", "tomato")).passed)
        }

    // ── BUDDHIST ─────────────────────────────────────────────────────────

    @Test
    fun buddhist_fivePungentAlliumAvoidCatchesGarlicAndLeekNotGinger() =
        runTest {
            // §7 Step 3 avoid: the five-pungent avoidance rides the same
            // allium category as the Jain path; Ginger is an aromatic, not an
            // allium, and stays available.
            val result =
                validator.validate(listOf(avoid("pungent", "category", "allium")), meal("garlic", "leek", "ginger"))
            assertFalse(result.passed)
            assertEquals(setOf("garlic", "leek"), result.violations.map { it.ingredientName }.toSet())
        }

    @Test
    fun buddhist_meatAvoidCatchesChickenWhileExternalTofuPasses() =
        runTest {
            // §7 Step 3 avoid + ADR 0012 layering: the meat avoid rides the
            // Hindu-veg path, and tofu — resolved through the EXTERNAL layer
            // (recorded USDA fixture), not the bundle — passes as a
            // protein/soy_product with no meat tag.
            val result =
                validator.validate(listOf(avoid("buddhist-veg", "category", "meat")), meal("chicken", "tofu"))
            assertFalse(result.passed)
            assertEquals("chicken", result.violations.single().ingredientName)
        }
}
