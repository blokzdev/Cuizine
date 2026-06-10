package ai.cuizine.data.food

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recorded-fixture stand-in for the USDA/OFF clients (CLAUDE.md §9:
 * key absent → fake). Entries here deliberately do NOT exist in the curated
 * bundle, so Layer 3 behavior is honestly exercisable offline. The real
 * Retrofit clients replace this binding when keys/config are present.
 */
@Singleton
class FakeExternalFoodSources
    @Inject
    constructor() : ExternalFoodSources {
        private fun entry(
            name: String,
            categories: List<String>,
            allergens: List<String> = emptyList(),
            sodiumMg: Double? = null,
            carbsG: Double? = null,
            fiberG: Double? = null,
        ) = CanonicalIngredientEntry(
            displayName = name,
            categories = categories,
            allergens = allergens,
            nutritionalComposition =
                NutritionalComposition(sodiumMg = sodiumMg, carbohydratesG = carbsG, fiberG = fiberG),
            sourceMetadata =
                SourceMetadata(source = "usda", sourceEntryId = "fixture", retrievedAt = "2026-06-10T00:00:00Z"),
        )

        private val recorded: Map<String, ExternalLookupResult> =
            mapOf(
                "quinoa" to
                    ExternalLookupResult(
                        entry("Quinoa", listOf("grain", "whole_grain"), carbsG = 64.2, fiberG = 7.0, sodiumMg = 5.0),
                        confidence = "high_usda",
                        sourceLayer = "usda",
                    ),
                "tofu" to
                    ExternalLookupResult(
                        entry("Tofu", listOf("protein", "soy_product"), allergens = listOf("soy"), sodiumMg = 7.0),
                        confidence = "high_usda",
                        sourceLayer = "usda",
                    ),
                "broccoli" to
                    ExternalLookupResult(
                        entry("Broccoli", listOf("vegetable", "cruciferous"), fiberG = 2.6, sodiumMg = 33.0),
                        confidence = "high_usda",
                        sourceLayer = "usda",
                    ),
                "oat milk" to
                    ExternalLookupResult(
                        entry("Oat milk", listOf("plant_milk"), sodiumMg = 40.0),
                        confidence = "high_off",
                        sourceLayer = "open_food_facts",
                    ),
            )

        override suspend fun lookupByName(name: String): ExternalLookupResult? =
            recorded[
                ai.cuizine.data.food.bundle.BundleLoader
                    .normalize(name),
            ]
    }

/**
 * The honest AI-categorizer fake: returns null — no fake pretends to be AI
 * (CLAUDE.md §9). The real path routes through ModelProvider in Phase 5;
 * deterministic categorizations for eval scenarios live with the Phase 5
 * FakeModelProvider.
 */
@Singleton
class NullAiIngredientCategorizer
    @Inject
    constructor() : AiIngredientCategorizer {
        override suspend fun categorize(name: String): CanonicalIngredientEntry? = null
    }
