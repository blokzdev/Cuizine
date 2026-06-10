package ai.cuizine.shared.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Profile-level JSON payloads (`data-model.md` §3). Captured by the Curator
 * during the day-0 constraint conversation; the cooking-for fields are
 * deliberately structured so v2 can transform them into dependent profiles
 * without re-asking (ADR 0004).
 */
@Serializable
data class CulturalContext(
    @SerialName("cuisine_origins") val cuisineOrigins: List<String> = emptyList(),
    @SerialName("regional_markers") val regionalMarkers: List<String> = emptyList(),
    @SerialName("household_traditions") val householdTraditions: List<String> = emptyList(),
    @SerialName("ingredient_vocabulary_hints") val ingredientVocabularyHints: List<String> = emptyList(),
)

@Serializable
data class CookingFor(
    @SerialName("household_size") val householdSize: Int,
    @SerialName("household_composition_notes") val householdCompositionNotes: String? = null,
    @SerialName("dietary_mix_summary") val dietaryMixSummary: String? = null,
)
