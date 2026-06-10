package ai.cuizine.data.mock

import ai.cuizine.engine.ports.FoodDataPort
import ai.cuizine.engine.ports.IngredientFacts
import ai.cuizine.engine.ports.IngredientResolution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The fake-first Food Data Provider (CLAUDE.md §9): a deterministic built-in
 * facts table covering the canonical fixture ingredients. Phase 4 replaces
 * this binding with the real three-layer provider (cache → bundle →
 * USDA/OFF → severity-scoped AI fallback); the engine never knows the
 * difference. `categorizeWithAi` returns null — no fake pretends to be AI.
 */
@Singleton
class StaticFoodDataPort
    @Inject
    constructor() : FoodDataPort {
        private val facts: Map<String, IngredientFacts> =
            listOf(
                IngredientFacts("onion", categories = setOf("allium", "vegetable", "fodmap_high")),
                IngredientFacts("garlic", categories = setOf("allium", "fodmap_high")),
                IngredientFacts("leek", categories = setOf("allium", "vegetable", "fodmap_high")),
                IngredientFacts("spinach", categories = setOf("vegetable", "leafy_green")),
                IngredientFacts("methi", categories = setOf("vegetable", "leafy_green")),
                IngredientFacts("potatoes", categories = setOf("vegetable", "starch")),
                IngredientFacts("masoor dal", categories = setOf("legume", "dal")),
                IngredientFacts("moong dal", categories = setOf("legume", "dal")),
                IngredientFacts("rajma", categories = setOf("legume", "dal")),
                IngredientFacts("brown basmati", categories = setOf("grain", "whole_grain")),
                IngredientFacts("rice", categories = setOf("grain", "refined_grain")),
                IngredientFacts("atta", categories = setOf("grain", "whole_grain")),
                IngredientFacts("haldi", categories = setOf("spice")),
                IngredientFacts("jeera", categories = setOf("spice")),
                IngredientFacts("rai", categories = setOf("spice")),
                IngredientFacts("garam masala", categories = setOf("spice")),
                IngredientFacts("ginger", categories = setOf("aromatic")),
                IngredientFacts("green chilli", categories = setOf("vegetable", "spice")),
                IngredientFacts("tomatoes", categories = setOf("vegetable")),
                IngredientFacts("ghee", categories = setOf("dairy", "fat")),
                IngredientFacts("dahi (for raita)", categories = setOf("dairy")),
                IngredientFacts("kheera", categories = setOf("vegetable", "hydrating")),
                IngredientFacts("kheera (cucumber)", categories = setOf("vegetable", "hydrating")),
                IngredientFacts("watermelon", categories = setOf("fruit", "hydrating")),
                IngredientFacts("beef", categories = setOf("meat")),
                IngredientFacts(
                    "soy sauce",
                    nutritionPer100g = mapOf("sodium_mg" to 5500.0),
                ),
            ).associateBy { it.canonicalName }

        override suspend fun lookup(ingredientName: String): IngredientResolution {
            val key = ingredientName.trim().lowercase()
            val found = facts[key] ?: facts.entries.firstOrNull { key.startsWith(it.key) }?.value
            return found?.let { IngredientResolution.Known(it, "high_bundle") }
                ?: IngredientResolution.Unknown
        }

        override suspend fun categorizeWithAi(ingredientName: String): IngredientFacts? = null
    }
