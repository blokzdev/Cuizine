package ai.cuizine.data.food

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * USDA FoodData Central client (ADR 0012; build facts in DECISION-LOG #4a).
 * v1 uses search only — the flattened search response already carries the
 * seven nutrients the validator needs, so no per-food detail call is spent
 * against the 1,000/hour quota. The API key travels as an X-Api-Key HEADER
 * (OkHttp interceptor), never in the URL.
 */
interface UsdaApi {
    @POST("foods/search")
    suspend fun search(
        @Body request: UsdaSearchRequest,
    ): UsdaSearchResponse

    companion object {
        const val BASE_URL = "https://api.nal.usda.gov/fdc/v1/"
        const val API_KEY_HEADER = "X-Api-Key"
    }
}

@Serializable
data class UsdaSearchRequest(
    val query: String,
    /** Foundation first, SR Legacy as the frozen-but-broad fallback (#4a). */
    val dataType: List<String> = listOf("Foundation", "SR Legacy"),
    val pageSize: Int = 5,
    val sortBy: String = "dataType.keyword",
    val sortOrder: String = "asc",
)

@Serializable
data class UsdaSearchResponse(
    val foods: List<UsdaSearchFood> = emptyList(),
)

@Serializable
data class UsdaSearchFood(
    val fdcId: Long,
    val description: String,
    val dataType: String = "",
    val foodCategory: String? = null,
    /** The FLATTENED search-shape nutrient list (#4a: differs from detail). */
    val foodNutrients: List<UsdaSearchNutrient> = emptyList(),
)

@Serializable
data class UsdaSearchNutrient(
    @SerialName("nutrientNumber") val nutrientNumber: String = "",
    @SerialName("unitName") val unitName: String = "",
    val value: Double? = null,
)

/** Maps a USDA search hit to the canonical entry shape. */
object UsdaMapper {
    /** Stable legacy nutrient numbers → engine keys (DECISION-LOG #4a). */
    private val NUTRIENT_KEYS =
        mapOf(
            "203" to "protein_g",
            "204" to "fat_g",
            "205" to "carbohydrates_g",
            "291" to "fiber_g",
            "307" to "sodium_mg",
            "306" to "potassium_mg",
            "305" to "phosphorus_mg",
            "208" to "calories_kcal",
        )

    fun toEntry(
        food: UsdaSearchFood,
        retrievedAtIso: String,
    ): CanonicalIngredientEntry {
        val nutrition = mutableMapOf<String, Double>()
        food.foodNutrients.forEach { nutrient ->
            val key = NUTRIENT_KEYS[nutrient.nutrientNumber] ?: return@forEach
            val value = nutrient.value ?: return@forEach
            nutrition[key] = value
        }
        return CanonicalIngredientEntry(
            displayName = food.description,
            categories =
                listOfNotNull(
                    food.foodCategory
                        ?.trim()
                        ?.lowercase()
                        ?.replace(' ', '_'),
                ),
            nutritionalComposition =
                NutritionalComposition(
                    caloriesKcal = nutrition["calories_kcal"],
                    proteinG = nutrition["protein_g"],
                    carbohydratesG = nutrition["carbohydrates_g"],
                    fatG = nutrition["fat_g"],
                    fiberG = nutrition["fiber_g"],
                    sodiumMg = nutrition["sodium_mg"],
                    potassiumMg = nutrition["potassium_mg"],
                    phosphorusMg = nutrition["phosphorus_mg"],
                ),
            sourceMetadata =
                SourceMetadata(
                    source = "usda",
                    sourceEntryId = food.fdcId.toString(),
                    retrievedAt = retrievedAtIso,
                ),
        )
    }
}
