package ai.cuizine.engine.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Provenance for every constraint (`data-model.md` §4,
 * `constraint-engine-spec.md` §9): where it came from, when, in the user's
 * own words, with the full modification trail. Provenance is what lets
 * Cuizine answer "why does this rule exist?" honestly.
 */
@Serializable
data class ProvenanceRecord(
    val source: String,
    @SerialName("added_at") val addedAt: String,
    @SerialName("added_context") val addedContext: AddedContext,
    @SerialName("original_phrasing") val originalPhrasing: String? = null,
    val confidence: String,
    @SerialName("modification_history") val modificationHistory: List<ModificationRecord> = emptyList(),
)

@Serializable
data class AddedContext(
    val flow: String,
    @SerialName("question_index") val questionIndex: Int? = null,
    @SerialName("triggering_message_excerpt") val triggeringMessageExcerpt: String? = null,
    @SerialName("rejected_suggestion_id") val rejectedSuggestionId: String? = null,
)

@Serializable
data class ModificationRecord(
    val at: String,
    val source: String,
    val reason: String? = null,
    @SerialName("previous_payload_json") val previousPayloadJson: String? = null,
    @SerialName("previous_scope_json") val previousScopeJson: String? = null,
)
