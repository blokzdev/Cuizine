package ai.cuizine.data.food

import ai.cuizine.engine.ports.IngredientFacts
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The canonical ingredient entry (`data-model.md` §5): what
 * `food_data_cache.canonical_entry_json` holds and what the curated bundle
 * ships. The engine consumes the slimmer [IngredientFacts] view.
 */
@Serializable
data class CanonicalIngredientEntry(
    @SerialName("display_name") val displayName: String,
    @SerialName("alternative_names") val alternativeNames: List<String> = emptyList(),
    /** e.g. ["allium", "vegetable", "fodmap_high"]. */
    val categories: List<String> = emptyList(),
    /** e.g. ["peanut", "dairy", "gluten"]. */
    val allergens: List<String> = emptyList(),
    /** e.g. ["contains_pork", "contains_alcohol", "non_halal"]. */
    @SerialName("religious_tags") val religiousTags: List<String> = emptyList(),
    @SerialName("nutritional_composition") val nutritionalComposition: NutritionalComposition =
        NutritionalComposition(),
    @SerialName("source_metadata") val sourceMetadata: SourceMetadata,
) {
    fun toFacts(): IngredientFacts =
        IngredientFacts(
            canonicalName = displayName.lowercase(),
            categories = (categories + religiousTags).toSet(),
            allergens = allergens.toSet(),
            nutritionPer100g = nutritionalComposition.asMap(),
        )
}

/** Per 100 g (`data-model.md` §5). All fields optional — sparse data is normal. */
@Serializable
data class NutritionalComposition(
    @SerialName("calories_kcal") val caloriesKcal: Double? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("carbohydrates_g") val carbohydratesG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    @SerialName("fiber_g") val fiberG: Double? = null,
    @SerialName("sodium_mg") val sodiumMg: Double? = null,
    @SerialName("potassium_mg") val potassiumMg: Double? = null,
    @SerialName("phosphorus_mg") val phosphorusMg: Double? = null,
) {
    fun asMap(): Map<String, Double> =
        buildMap {
            caloriesKcal?.let { put("calories_kcal", it) }
            proteinG?.let { put("protein_g", it) }
            carbohydratesG?.let { put("carbohydrates_g", it) }
            fatG?.let { put("fat_g", it) }
            fiberG?.let { put("fiber_g", it) }
            sodiumMg?.let { put("sodium_mg", it) }
            potassiumMg?.let { put("potassium_mg", it) }
            phosphorusMg?.let { put("phosphorus_mg", it) }
        }
}

@Serializable
data class SourceMetadata(
    /** bundle | usda | open_food_facts | ai_fallback | user_verified. */
    val source: String,
    @SerialName("source_entry_id") val sourceEntryId: String? = null,
    @SerialName("retrieved_at") val retrievedAt: String,
)

/**
 * The curated bundle (`data-model.md` §5; ADR 0012): ships in the APK at
 * `assets/food_data_bundle/v1.json`, immutable per app version. Corrections
 * override external sources for matching lookup keys.
 */
@Serializable
data class CuratedBundle(
    val version: String,
    @SerialName("authored_at") val authoredAt: String,
    val entries: List<CanonicalIngredientEntry> = emptyList(),
    val corrections: List<BundleCorrection> = emptyList(),
)

@Serializable
data class BundleCorrection(
    @SerialName("lookup_key") val lookupKey: String,
    /** ingredient_name | barcode. */
    @SerialName("lookup_kind") val lookupKind: String,
    val entry: CanonicalIngredientEntry,
    val reason: String,
)
