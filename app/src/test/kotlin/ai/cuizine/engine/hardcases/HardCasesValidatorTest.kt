package ai.cuizine.engine.hardcases

import ai.cuizine.engine.ports.FoodDataPort
import ai.cuizine.engine.ports.IngredientFacts
import ai.cuizine.engine.ports.IngredientResolution
import ai.cuizine.engine.types.AvoidException
import ai.cuizine.engine.types.AvoidPayload
import ai.cuizine.engine.types.AvoidTarget
import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.ContextualPayload
import ai.cuizine.engine.types.ContextualState
import ai.cuizine.engine.types.LimitPayload
import ai.cuizine.engine.types.LimitTarget
import ai.cuizine.engine.types.RequirePayload
import ai.cuizine.engine.types.RequireTarget
import ai.cuizine.engine.types.Severity
import ai.cuizine.engine.types.SuggestionIngredient
import ai.cuizine.engine.types.SuggestionInput
import ai.cuizine.engine.types.Threshold
import ai.cuizine.engine.validator.SuggestionValidator
import ai.cuizine.shared.fixtures.AishaFixtures
import ai.cuizine.shared.fixtures.SukhiFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hard-cases slice for the deterministic validator (`testing-strategy.md` §5
 * category 5; `constraint-engine-spec.md` §7): every step and branch of the
 * mechanical contract — the Step 2/4 unknown-ingredient × severity matrix,
 * the five type-specific Step 3 checks, Step 5 aggregation, and the
 * statelessness guarantee. The validator is exercised directly as the pure
 * function §7 declares it to be; active-set computation (Step 1) is the
 * engine's job and is covered in ConstraintGraphEngineTest.
 */
class HardCasesValidatorTest {
    // ── Test port (canonical FixtureFoodData pattern) ────────────────────

    private class FixtureFoodData : FoodDataPort {
        val known =
            mapOf(
                "onion" to IngredientFacts("onion", categories = setOf("allium", "vegetable", "fodmap_high")),
                "garlic" to IngredientFacts("garlic", categories = setOf("allium", "fodmap_high")),
                "spinach" to IngredientFacts("spinach", categories = setOf("vegetable", "leafy_green")),
                "beef" to IngredientFacts("beef", categories = setOf("meat", "non_halal_risk")),
                "bacon" to
                    IngredientFacts(
                        "bacon",
                        categories = setOf("meat", "pork", "non_halal"),
                        nutritionPer100g = mapOf("sodium_mg" to 1700.0),
                    ),
                "watermelon" to IngredientFacts("watermelon", categories = setOf("fruit", "hydrating")),
                "soy sauce" to
                    IngredientFacts("soy sauce", nutritionPer100g = mapOf("sodium_mg" to 5500.0)),
                "oats" to
                    IngredientFacts(
                        "oats",
                        categories = setOf("grain", "whole_grain"),
                        nutritionPer100g = mapOf("fiber_g" to 8.0),
                    ),
            )
        var aiAnswer: IngredientFacts? = null
        var aiCalls = 0

        override suspend fun lookup(ingredientName: String): IngredientResolution =
            known[ingredientName.lowercase()]?.let { IngredientResolution.Known(it, "high_bundle") }
                ?: IngredientResolution.Unknown

        override suspend fun categorizeWithAi(ingredientName: String): IngredientFacts? {
            aiCalls += 1
            return aiAnswer
        }
    }

    private val foodData = FixtureFoodData()
    private val validator = SuggestionValidator(foodData)

    // ── Suggestion + constraint builders (fixture helper style) ──────────

    private fun meal(vararg names: String) =
        SuggestionInput(
            mealName = "test meal",
            ingredients = names.map { SuggestionIngredient(it, 100.0, "g") },
        )

    private fun mealOf(vararg ingredients: SuggestionIngredient) =
        SuggestionInput(mealName = "test meal", ingredients = ingredients.toList())

    private fun avoidWithSeverity(
        severity: Severity,
        targetKind: String,
        targetValue: String,
        exceptions: List<AvoidException> = emptyList(),
    ) = SukhiFixtures.avoidBeef.copy(
        id = "hardcase-avoid-$targetValue-${severity.storageValue}",
        severity = severity,
        payload =
            AvoidPayload(
                target = AvoidTarget(kind = targetKind, value = targetValue),
                exceptions = exceptions,
            ),
    )

    private fun sodiumLimit(
        ceilingMg: Double,
        hardOrSoft: String,
    ) = SukhiFixtures.limitSodium.copy(
        id = "hardcase-limit-sodium-$hardOrSoft",
        payload =
            LimitPayload(
                target = LimitTarget(kind = "nutritional_property", value = "sodium_mg"),
                ceiling = Threshold(value = ceilingMg, unit = "mg"),
                window = "per_day",
                hardOrSoft = hardOrSoft,
            ),
    )

    private fun fiberRequire(thresholdGrams: Double) =
        AishaFixtures.iftarHydration.copy(
            id = "hardcase-require-fiber",
            severity = Severity.Medical,
            payload =
                RequirePayload(
                    target =
                        RequireTarget(
                            kind = "nutritional_property",
                            value = "fiber_g",
                            threshold = Threshold(value = thresholdGrams, unit = "g"),
                        ),
                    window = "single_meal",
                ),
        )

    // ── §7 Step 2/4: the unknown-ingredient × severity matrix ────────────

    @Test
    fun step4_unknownIngredientAgainstInviolableRejectsWithZeroAiCalls() =
        runTest {
            // §7 Step 4: the safety floor — never AI-categorize against Inviolable.
            val inviolablePeanut = avoidWithSeverity(Severity.Inviolable, "category", "peanut")
            foodData.aiAnswer = IngredientFacts("mystery", categories = setOf("snack"))
            val result = validator.validate(listOf(inviolablePeanut), meal("mystery snack mix"))
            assertFalse(result.passed)
            assertEquals(Severity.Inviolable, result.violations.single().severity)
            assertEquals(0, foodData.aiCalls)
        }

    @Test
    fun step4_unknownIngredientMedicalAiSuccessAddsDisclosureAndContinues() =
        runTest {
            // §7 Step 4: Medical + unknown → AI sub-call, check passes → disclosure, no rejection.
            foodData.aiAnswer = IngredientFacts("ramps", categories = setOf("vegetable"))
            val result =
                validator.validate(listOf(SukhiFixtures.avoidAlliumsDuringFlare), meal("ramps"))
            assertTrue(result.passed)
            assertTrue(result.disclosureNotes.any { it.contains("not 100% sure") })
        }

    @Test
    fun step4_unknownIngredientMedicalAiCategorizationMatchingAvoidTargetViolates() =
        runTest {
            // §7 Step 4: Medical + unknown → AI categorization matches the avoid target → violation.
            foodData.aiAnswer = IngredientFacts("ramps", categories = setOf("allium", "vegetable"))
            val result =
                validator.validate(listOf(SukhiFixtures.avoidAlliumsDuringFlare), meal("ramps"))
            assertFalse(result.passed)
            assertEquals(SukhiFixtures.avoidAlliumsDuringFlare.id, result.violations.single().constraintId)
            assertEquals("ramps", result.violations.single().ingredientName)
        }

    @Test
    fun step4_unknownIngredientMedicalAiNullIsViolation() =
        runTest {
            // §7 Step 4: Medical + unknown and no categorization possible → cannot verify → violation.
            foodData.aiAnswer = null
            val result =
                validator.validate(listOf(SukhiFixtures.avoidAlliumsDuringFlare), meal("ramps"))
            assertFalse(result.passed)
            assertTrue(
                result.violations
                    .single()
                    .reason
                    .contains("could not be identified or categorized"),
            )
        }

    @Test
    fun step4_unknownIngredientReligiousCulturalAiSuccessMirrorsMedicalDisclosure() =
        runTest {
            // §7 Step 4: Religious & Cultural shares the Medical unknown-ingredient path.
            foodData.aiAnswer = IngredientFacts("seitan", categories = setOf("plant_protein"))
            val result = validator.validate(listOf(SukhiFixtures.avoidBeef), meal("mystery cut"))
            assertTrue(result.passed)
            assertTrue(result.disclosureNotes.any { it.contains("not 100% sure") })
        }

    @Test
    fun step4_unknownIngredientReligiousCulturalAiNullMirrorsMedicalViolation() =
        runTest {
            // §7 Step 4: Religious & Cultural + unknown + AI null → violation, like Medical.
            foodData.aiAnswer = null
            val result = validator.validate(listOf(SukhiFixtures.avoidBeef), meal("mystery cut"))
            assertFalse(result.passed)
            assertTrue(
                result.violations
                    .single()
                    .reason
                    .contains("religious_cultural"),
            )
        }

    @Test
    fun step4_unknownIngredientPreferenceUsesAiWithoutDisclosure() =
        runTest {
            // §7 Step 4: Preference tier triggers the AI sub-call but never the disclosure note.
            val preferenceAllium = avoidWithSeverity(Severity.Preference, "category", "allium")
            foodData.aiAnswer = IngredientFacts("ramps", categories = setOf("vegetable"))
            val result = validator.validate(listOf(preferenceAllium), meal("ramps"))
            assertTrue(result.passed)
            assertEquals(1, foodData.aiCalls)
            assertTrue(result.disclosureNotes.isEmpty())
        }

    @Test
    fun step4_unknownIngredientPreferenceAiNullProducesNoViolation() =
        runTest {
            // §7 Step 4: Preference + unknown + AI null → the check is silently skipped.
            val preferenceAllium = avoidWithSeverity(Severity.Preference, "category", "allium")
            foodData.aiAnswer = null
            val result = validator.validate(listOf(preferenceAllium), meal("ramps"))
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
        }

    // ── §7 Step 3: avoid branch ──────────────────────────────────────────

    @Test
    fun avoid_singleViolatingIngredientAmongCleanOnesIsFlaggedAlone() =
        runTest {
            // §7 Step 3 avoid: only the matching ingredient violates, not its neighbours.
            val result =
                validator.validate(listOf(SukhiFixtures.avoidBeef), meal("spinach", "beef", "watermelon"))
            assertFalse(result.passed)
            assertEquals("beef", result.violations.single().ingredientName)
        }

    @Test
    fun avoid_multipleMatchingIngredientsAccumulateOneViolationEach() =
        runTest {
            // §7 Step 3 avoid: each matching ingredient produces its own violation.
            val result =
                validator.validate(
                    listOf(SukhiFixtures.avoidAlliumsDuringFlare),
                    meal("onion", "garlic", "spinach"),
                )
            assertEquals(2, result.violations.size)
            assertEquals(setOf("onion", "garlic"), result.violations.map { it.ingredientName }.toSet())
        }

    @Test
    fun avoid_nutritionalPropertyTargetMatchesWhenPropertyPresentAboveZero() =
        runTest {
            // §7 Step 3 avoid: nutritional_property target kind matches any amount > 0 per 100 g.
            val avoidSodium = avoidWithSeverity(Severity.Medical, "nutritional_property", "sodium_mg")
            val result = validator.validate(listOf(avoidSodium), meal("bacon", "spinach"))
            assertFalse(result.passed)
            assertEquals("bacon", result.violations.single().ingredientName)
        }

    @Test
    fun avoid_ingredientKindExceptionSparesOnlyTheNamedIngredient() =
        runTest {
            // §7 Step 3 avoid: an ingredient-kind exception carves out one ingredient, not the category.
            val alliumExceptGarlic =
                avoidWithSeverity(
                    Severity.Medical,
                    "category",
                    "allium",
                    exceptions = listOf(AvoidException(kind = "ingredient", value = "garlic")),
                )
            val result = validator.validate(listOf(alliumExceptGarlic), meal("onion", "garlic"))
            assertFalse(result.passed)
            assertEquals("onion", result.violations.single().ingredientName)
        }

    @Test
    fun avoid_categoryKindExceptionSparesWholeCategory() =
        runTest {
            // §7 Step 3 avoid: a category-kind exception spares every ingredient in that category.
            val meatExceptPork =
                avoidWithSeverity(
                    Severity.ReligiousCultural,
                    "category",
                    "meat",
                    exceptions = listOf(AvoidException(kind = "category", value = "pork")),
                )
            val result = validator.validate(listOf(meatExceptPork), meal("bacon", "beef"))
            assertFalse(result.passed)
            assertEquals("beef", result.violations.single().ingredientName)
        }

    // ── §7 Step 3: require branch ────────────────────────────────────────

    @Test
    fun require_thresholdScaledByQuantityFailsAtHalfPortion() =
        runTest {
            // §7 Step 3 require: 8 g fiber per 100 g × 50 g = 4 g < the 6 g threshold → violation.
            val result =
                validator.validate(
                    listOf(fiberRequire(thresholdGrams = 6.0)),
                    mealOf(SuggestionIngredient("oats", 50.0, "g")),
                )
            assertFalse(result.passed)
            assertTrue(
                result.violations
                    .single()
                    .reason
                    .contains("fiber_g"),
            )
        }

    @Test
    fun require_thresholdScaledByQuantityPassesAtFullPortion() =
        runTest {
            // §7 Step 3 require: 8 g fiber per 100 g × 100 g = 8 g ≥ the 6 g threshold → satisfied.
            val result =
                validator.validate(
                    listOf(fiberRequire(thresholdGrams = 6.0)),
                    mealOf(SuggestionIngredient("oats", 100.0, "g")),
                )
            assertTrue(result.passed)
        }

    @Test
    fun require_categorySatisfiedThroughFactsCategories() =
        runTest {
            // §7 Step 3 require: the required category is satisfied via the resolved facts' categories.
            val result = validator.validate(listOf(AishaFixtures.iftarHydration), meal("watermelon"))
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
        }

    @Test
    fun require_unknownIngredientCannotSatisfyRequirement() =
        runTest {
            // §7 Step 3 require: an unresolved ingredient never satisfies a require —
            // even a perfect AI categorization is not consulted on this path.
            // Medical tier so the unsatisfied require rejects (§4: Preference
            // tier never rejects alone).
            foodData.aiAnswer = IngredientFacts("mystery elixir", categories = setOf("hydrating"))
            val medicalHydration =
                AishaFixtures.iftarHydration.copy(
                    id = "hardcase-require-medical",
                    severity = ai.cuizine.engine.types.Severity.Medical,
                )
            val result = validator.validate(listOf(medicalHydration), meal("mystery elixir"))
            assertFalse(result.passed)
            assertEquals(ConstraintType.Require, result.violations.single().constraintType)
        }

    @Test
    fun require_nonSingleMealWindowIsNotEnforcedInV1() =
        runTest {
            // §3 Type 3: daily/weekly require accounting needs cross-meal history (v2+);
            // v1 enforces only the single_meal window, so a per_day require is skipped.
            val dailyHydration =
                AishaFixtures.iftarHydration.copy(
                    id = "hardcase-require-daily",
                    payload =
                        RequirePayload(
                            target = RequireTarget(kind = "category", value = "hydrating"),
                            window = "per_day",
                        ),
                )
            val result = validator.validate(listOf(dailyHydration), meal("spinach"))
            assertTrue(result.passed)
        }

    // ── §7 Step 3: limit branch ──────────────────────────────────────────

    @Test
    fun limit_sumsAcrossIngredientsWithMixedGramAndKilogramUnits() =
        runTest {
            // §7 Step 3 limit: 20 g soy sauce (1100 mg) + 0.1 kg bacon (1700 mg) = 2800 mg > 2000 mg.
            val result =
                validator.validate(
                    listOf(sodiumLimit(ceilingMg = 2000.0, hardOrSoft = "hard")),
                    mealOf(
                        SuggestionIngredient("soy sauce", 20.0, "g"),
                        SuggestionIngredient("bacon", 0.1, "kg"),
                    ),
                )
            assertFalse(result.passed)
            assertTrue(
                result.violations
                    .single()
                    .reason
                    .contains("2800"),
            )
        }

    @Test
    fun limit_unitlessIngredientContributesHundredGramBasis() =
        runTest {
            // §7 Step 3 limit: no usable quantity → deterministic 100 g basis → full 5500 mg counted.
            val result =
                validator.validate(
                    listOf(sodiumLimit(ceilingMg = 5000.0, hardOrSoft = "hard")),
                    mealOf(SuggestionIngredient("soy sauce")),
                )
            assertFalse(result.passed)
            assertTrue(
                result.violations
                    .single()
                    .reason
                    .contains("5500"),
            )
        }

    @Test
    fun limit_propertyAbsentFromFactsContributesZero() =
        runTest {
            // §7 Step 3 limit: an ingredient with no entry for the property contributes 0 to the sum.
            val result =
                validator.validate(
                    listOf(sodiumLimit(ceilingMg = 1.0, hardOrSoft = "hard")),
                    meal("spinach"),
                )
            assertTrue(result.passed)
        }

    @Test
    fun limit_hardCeilingExceededIsViolation() =
        runTest {
            // §7 Step 3 limit: a hard limit over its ceiling is a violation, not a note.
            val result =
                validator.validate(
                    listOf(sodiumLimit(ceilingMg = 2000.0, hardOrSoft = "hard")),
                    meal("soy sauce"),
                )
            assertFalse(result.passed)
            assertEquals(ConstraintType.Limit, result.violations.single().constraintType)
        }

    @Test
    fun limit_softCeilingExceededAtSameSumIsDisclosureOnly() =
        runTest {
            // §7 Step 3 limit: the identical sum over a soft ceiling discloses and still passes.
            val result =
                validator.validate(
                    listOf(sodiumLimit(ceilingMg = 2000.0, hardOrSoft = "soft")),
                    meal("soy sauce"),
                )
            assertTrue(result.passed)
            assertTrue(result.disclosureNotes.any { it.contains("runs a little high") })
        }

    @Test
    fun limit_sumExactlyAtCeilingPasses() =
        runTest {
            // §7 Step 3 limit: only a sum that *exceeds* the ceiling triggers; equality passes.
            val result =
                validator.validate(
                    listOf(sodiumLimit(ceilingMg = 5500.0, hardOrSoft = "hard")),
                    meal("soy sauce"),
                )
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
        }

    // ── §7 Step 3: prefer branch ─────────────────────────────────────────

    @Test
    fun prefer_duplicateMisalignedHighPrefersDiscloseExactlyOnce() =
        runTest {
            // §7 Step 5: identical disclosure strings are de-duplicated in the aggregate.
            val duplicate = SukhiFixtures.preferRajma.copy(id = "hardcase-prefer-rajma-dupe")
            val result =
                validator.validate(listOf(SukhiFixtures.preferRajma, duplicate), meal("spinach"))
            assertTrue(result.passed)
            assertEquals(1, result.disclosureNotes.size)
        }

    @Test
    fun prefer_mealNameAlignmentIsCaseInsensitive() =
        runTest {
            // §7 Step 3 prefer: alignment via the meal name, matched case-insensitively.
            val suggestion =
                SuggestionInput(
                    mealName = "Sunday RAJMA chawal",
                    ingredients = listOf(SuggestionIngredient("spinach", 100.0, "g")),
                )
            val result = validator.validate(listOf(SukhiFixtures.preferRajma), suggestion)
            assertTrue(result.passed)
            assertTrue(result.disclosureNotes.isEmpty())
        }

    @Test
    fun prefer_ingredientNameAlignmentSuppressesDisclosure() =
        runTest {
            // §7 Step 3 prefer: alignment can also come from an ingredient name containing the target.
            val result =
                validator.validate(
                    listOf(SukhiFixtures.preferRajma),
                    mealOf(SuggestionIngredient("Rajma beans", 100.0, "g")),
                )
            assertTrue(result.passed)
            assertTrue(result.disclosureNotes.isEmpty())
        }

    @Test
    fun prefer_mediumStrengthMisalignmentStaysSilent() =
        runTest {
            // §7 Step 3 prefer: only high-strength misalignment earns the quiet note.
            val result = validator.validate(listOf(SukhiFixtures.preferAloo), meal("spinach"))
            assertTrue(result.passed)
            assertTrue(result.disclosureNotes.isEmpty())
        }

    // ── §7 Step 3: contextual incompatible_with ──────────────────────────

    @Test
    fun contextual_emptyIngredientSuggestionPassesDuringFast() =
        runTest {
            // §7 Step 3 contextual: "any_meal" incompatibility only triggers on a non-empty meal.
            val emptySuggestion = SuggestionInput(mealName = "fasting window", ingredients = emptyList())
            val result = validator.validate(listOf(AishaFixtures.ramadanFast), emptySuggestion)
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
        }

    @Test
    fun contextual_nonEmptySuggestionDuringFastIsViolation() =
        runTest {
            // §7 Step 3 contextual: a fasting state incompatible with any_meal rejects real meals.
            val result = validator.validate(listOf(AishaFixtures.ramadanFast), meal("watermelon"))
            assertFalse(result.passed)
            assertEquals(ConstraintType.Contextual, result.violations.single().constraintType)
        }

    @Test
    fun contextual_absentIncompatibleWithNeverViolates() =
        runTest {
            // §7 Step 3 contextual: states without incompatible_with act only as scope modifiers.
            val flagOnly =
                AishaFixtures.ramadanFast.copy(
                    id = "hardcase-contextual-flag-only",
                    payload = ContextualPayload(state = ContextualState(flag = "ibs_flare")),
                )
            val result = validator.validate(listOf(flagOnly), meal("onion"))
            assertTrue(result.passed)
        }

    // ── §7 Step 5: aggregation ───────────────────────────────────────────

    @Test
    fun step5_passedResultCarriesAccumulatedDisclosures() =
        runTest {
            // §7 Step 5: passed=true still surfaces every accumulated disclosure note.
            val result =
                validator.validate(
                    listOf(sodiumLimit(ceilingMg = 2000.0, hardOrSoft = "soft"), SukhiFixtures.preferRajma),
                    meal("soy sauce"),
                )
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
            assertEquals(2, result.disclosureNotes.size)
        }

    @Test
    fun step5_multipleViolationsAreAllReported() =
        runTest {
            // §7 Step 5: every violation is reported, never just the first one found.
            val hardSodium = sodiumLimit(ceilingMg = 2000.0, hardOrSoft = "hard")
            val result =
                validator.validate(listOf(SukhiFixtures.avoidBeef, hardSodium), meal("beef", "soy sauce"))
            assertFalse(result.passed)
            assertEquals(2, result.violations.size)
            assertEquals(
                setOf(SukhiFixtures.avoidBeef.id, hardSodium.id),
                result.violations.map { it.constraintId }.toSet(),
            )
        }

    @Test
    fun step5_sameInputsTwiceGiveEqualResults() =
        runTest {
            // §7: the validator is a pure function — identical inputs give identical results.
            foodData.aiAnswer = IngredientFacts("ramps", categories = setOf("vegetable"))
            val active =
                listOf(
                    SukhiFixtures.avoidAlliumsDuringFlare,
                    sodiumLimit(ceilingMg = 2000.0, hardOrSoft = "soft"),
                    SukhiFixtures.preferRajma,
                )
            val suggestion = meal("onion", "soy sauce", "ramps")
            val first = validator.validate(active, suggestion)
            val second = validator.validate(active, suggestion)
            assertEquals(first, second)
        }

    // ── §7 statelessness across invocations ──────────────────────────────

    @Test
    fun stateless_aiSubCallRunsOncePerUnknownIngredientWithinACall() =
        runTest {
            // §7 Step 4: within one invocation the AI sub-call runs at most once per
            // unknown ingredient, even when several constraints check it.
            val nightshadeAvoid = avoidWithSeverity(Severity.Medical, "category", "nightshade")
            foodData.aiAnswer = IngredientFacts("ramps", categories = setOf("vegetable"))
            validator.validate(
                listOf(SukhiFixtures.avoidAlliumsDuringFlare, nightshadeAvoid),
                meal("ramps"),
            )
            assertEquals(1, foodData.aiCalls)
        }

    @Test
    fun stateless_aiFallbackCacheIsPerCallNotPerInstance() =
        runTest {
            // §7: stateless between invocations — a reused instance re-resolves from scratch,
            // so the AI sub-call count grows once per validate call, not once per instance.
            foodData.aiAnswer = IngredientFacts("ramps", categories = setOf("vegetable"))
            validator.validate(listOf(SukhiFixtures.avoidAlliumsDuringFlare), meal("ramps"))
            validator.validate(listOf(SukhiFixtures.avoidAlliumsDuringFlare), meal("ramps"))
            assertEquals(2, foodData.aiCalls)
        }

    @Test
    fun stateless_violationsDoNotLeakIntoSubsequentCalls() =
        runTest {
            // §7: no memory of previous attempts — a failure leaves nothing behind.
            val failed = validator.validate(listOf(SukhiFixtures.avoidBeef), meal("beef"))
            assertFalse(failed.passed)
            val clean = validator.validate(listOf(SukhiFixtures.avoidBeef), meal("spinach"))
            assertTrue(clean.passed)
            assertTrue(clean.violations.isEmpty())
            assertTrue(clean.disclosureNotes.isEmpty())
        }
}
