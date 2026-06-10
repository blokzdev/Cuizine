package ai.cuizine.engine.types

/**
 * The validator's suggestion input contract (`constraint-engine-spec.md` §7):
 * what the Chef hands over for checking. Distinct from the UI's presentation
 * model — this is the mechanical shape.
 */
data class SuggestionInput(
    val mealName: String,
    val ingredients: List<SuggestionIngredient>,
    val preparationSummary: String = "",
    /** Optional per-serving rough macros from the Chef; informational in v1. */
    val nutritionalRoughEstimate: Map<String, Double>? = null,
)

data class SuggestionIngredient(
    val name: String,
    val quantityValue: Double? = null,
    val quantityUnit: String? = null,
) {
    /**
     * Quantity normalized to grams for limit summation. Deterministic
     * fallback: an ingredient with no usable quantity contributes one 100 g
     * basis unit (documented in the Phase 3 report; refined with real food
     * data in Phase 4).
     */
    fun quantityGrams(): Double =
        when (quantityUnit?.lowercase()) {
            "g", "gram", "grams" -> quantityValue ?: DEFAULT_BASIS_GRAMS

            "kg" -> (quantityValue ?: 0.1) * 1000.0

            "mg" -> (quantityValue ?: 0.0) / 1000.0

            "ml" -> quantityValue ?: DEFAULT_BASIS_GRAMS

            // water-density approximation
            "l" -> (quantityValue ?: 0.1) * 1000.0

            else -> DEFAULT_BASIS_GRAMS
        }

    private companion object {
        const val DEFAULT_BASIS_GRAMS = 100.0
    }
}

/** Validation outcome (`constraint-engine-spec.md` §7 Step 5). */
data class ValidationResult(
    val passed: Boolean,
    val violations: List<Violation> = emptyList(),
    val disclosureNotes: List<String> = emptyList(),
)

data class Violation(
    val constraintId: String,
    val constraintType: ConstraintType,
    val severity: Severity,
    /** Plain mechanical reason — the Chef's regeneration context, not user copy. */
    val reason: String,
    val ingredientName: String? = null,
)
