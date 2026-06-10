package ai.cuizine.data.food

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open Food Facts text-search client via Search-a-licious (DECISION-LOG #4a:
 * the legacy CGI/v2 search paths are decaying; Search-a-licious is the 2026
 * path, wrapped behind [ExternalFoodSources] because its API may evolve).
 * Reads need no key — only the REQUIRED User-Agent convention.
 */
interface OffSearchApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("langs") langs: String = "en",
        @Query("page_size") pageSize: Int = 5,
        @Query("fields") fields: String = FIELDS,
    ): OffSearchResponse

    companion object {
        const val BASE_URL = "https://search.openfoodfacts.org/"
        const val FIELDS = "code,product_name,categories_tags,allergens_tags,ingredients_analysis_tags,nutriments"
    }
}

@Serializable
data class OffSearchResponse(
    val hits: List<OffHit> = emptyList(),
)

@Serializable
data class OffHit(
    val code: String = "",
    @SerialName("product_name") val productName: String? = null,
    @SerialName("categories_tags") val categoriesTags: List<String> = emptyList(),
    @SerialName("allergens_tags") val allergensTags: List<String> = emptyList(),
    @SerialName("ingredients_analysis_tags") val ingredientsAnalysisTags: List<String> = emptyList(),
    /** Values can be String or Number — parsed defensively (#4a). */
    val nutriments: Map<String, JsonElement> = emptyMap(),
)

/** Maps an OFF search hit to the canonical entry shape. */
object OffMapper {
    fun toEntry(
        hit: OffHit,
        retrievedAtIso: String,
    ): CanonicalIngredientEntry? {
        val name = hit.productName?.takeIf { it.isNotBlank() } ?: return null

        // OFF _100g values are in GRAMS even for sodium/potassium (#4a):
        // sodium_100g 0.043 means 43 mg — convert at this boundary.
        fun grams(key: String): Double? = (hit.nutriments[key] as? JsonPrimitive)?.doubleOrNull

        fun gramsToMg(key: String): Double? = grams(key)?.times(1000.0)

        return CanonicalIngredientEntry(
            displayName = name,
            categories =
                (hit.categoriesTags + hit.ingredientsAnalysisTags)
                    .map { it.removePrefix("en:").replace('-', '_') },
            allergens = hit.allergensTags.map { it.removePrefix("en:").replace('-', '_') },
            nutritionalComposition =
                NutritionalComposition(
                    caloriesKcal = grams("energy-kcal_100g"),
                    proteinG = grams("proteins_100g"),
                    carbohydratesG = grams("carbohydrates_100g"),
                    fatG = grams("fat_100g"),
                    fiberG = grams("fiber_100g"),
                    sodiumMg = gramsToMg("sodium_100g"),
                    potassiumMg = gramsToMg("potassium_100g"),
                    phosphorusMg = gramsToMg("phosphorus_100g"),
                ),
            sourceMetadata =
                SourceMetadata(
                    source = "open_food_facts",
                    sourceEntryId = hit.code.takeIf { it.isNotBlank() },
                    retrievedAt = retrievedAtIso,
                ),
        )
    }
}
