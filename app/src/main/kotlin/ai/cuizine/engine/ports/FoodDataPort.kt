package ai.cuizine.engine.ports

/**
 * The validator's only external lookup dependency (`constraint-engine-spec.md`
 * §7 Step 2; ADR 0012). Phase 4 implements the real three-layer provider
 * (cache → bundle → USDA/OFF → severity-scoped AI fallback); Phase 3 tests
 * inject fixtures. The AI categorization sub-call is separate so the
 * validator can apply the severity rules of §7 Step 4 — Inviolable checks
 * never invoke it.
 */
interface FoodDataPort {
    suspend fun lookup(ingredientName: String): IngredientResolution

    /**
     * The AI-fallback categorization sub-call (ADR 0012), tagged
     * low-confidence. Returns null when no categorization is possible.
     */
    suspend fun categorizeWithAi(ingredientName: String): IngredientFacts?
}

sealed interface IngredientResolution {
    /** Canonical entry found in cache/bundle/USDA/OFF. */
    data class Known(
        val facts: IngredientFacts,
        /** Confidence tag per ADR 0012 (`data-model.md` storage values). */
        val confidence: String,
    ) : IngredientResolution

    /** The provider could not identify the ingredient. */
    data object Unknown : IngredientResolution
}

/**
 * What the engine needs to know about an ingredient — categories, allergens,
 * and nutritional composition per 100 g (`constraint-engine-spec.md` §2
 * "ingredient entry"; full canonical entry shape lives in the data layer).
 */
data class IngredientFacts(
    val canonicalName: String,
    val categories: Set<String> = emptySet(),
    val allergens: Set<String> = emptySet(),
    /** Property → amount per 100 g, e.g. "sodium_mg" → 491.0. */
    val nutritionPer100g: Map<String, Double> = emptyMap(),
)
