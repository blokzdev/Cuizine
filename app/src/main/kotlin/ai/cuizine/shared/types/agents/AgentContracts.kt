package ai.cuizine.shared.types.agents

import ai.cuizine.engine.types.ConstraintPayload
import ai.cuizine.engine.types.ConstraintScope
import ai.cuizine.engine.types.Severity
import ai.cuizine.engine.types.SuggestionIngredient
import ai.cuizine.shared.types.Constraint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The typed agent contracts (`agent-architecture.md` §4–§6). All agent inputs
// and outputs are structured objects — never raw strings — validated at the
// orchestrator boundary, produced via constrained generation (§2).

/**
 * User intents (§3 routing table) — a closed set; unknown intents are
 * rejected at the boundary, never default-routed.
 */
sealed interface UserIntent {
    val profileId: String

    data class BeginConstraintConversation(
        override val profileId: String,
    ) : UserIntent

    data class ContinueConstraintConversation(
        override val profileId: String,
        val message: String,
    ) : UserIntent

    data class FreeTextUpdate(
        override val profileId: String,
        val message: String,
    ) : UserIntent

    data class RequestMealSuggestion(
        override val profileId: String,
        val requestDetails: String,
    ) : UserIntent

    data class RejectSuggestion(
        override val profileId: String,
        val suggestionId: String,
        val reason: String,
        val freeText: String?,
    ) : UserIntent

    data class RequestSuggestionExplanation(
        override val profileId: String,
        val suggestionId: String?,
    ) : UserIntent

    data class PantryIngestion(
        override val profileId: String,
        val rawName: String,
    ) : UserIntent
}

// ── Curator (§4) ─────────────────────────────────────────────────────────

@Serializable
data class CuratorInput(
    @SerialName("profile_id") val profileId: String,
    @SerialName("current_message") val currentMessage: String,
    @SerialName("conversation_history") val conversationHistory: List<HistoryTurn> = emptyList(),
    @SerialName("active_constraint_summary") val activeConstraintSummary: List<ConstraintSummary> = emptyList(),
    @SerialName("intent_type") val intentType: String,
)

@Serializable
data class HistoryTurn(
    val author: String,
    val text: String,
)

/** Compact constraint view handed to agents (full records stay engine-side). */
@Serializable
data class ConstraintSummary(
    val id: String,
    val type: String,
    val severity: String,
    val label: String,
    @SerialName("scope_summary") val scopeSummary: String,
)

@Serializable
data class CuratorOutput(
    @SerialName("constraint_operations") val constraintOperations: List<ConstraintOperation> = emptyList(),
    @SerialName("user_message") val userMessage: String,
    @SerialName("next_intent") val nextIntent: String,
    @SerialName("disclosure_notes") val disclosureNotes: List<String> = emptyList(),
)

/** Graph operations the orchestrator applies on the Curator's behalf (§4). */
@Serializable
data class ConstraintOperation(
    /** add_constraint | update_constraint | remove_constraint | set_contextual_state. */
    val operation: String,
    @SerialName("constraint_id") val constraintId: String? = null,
    val type: String? = null,
    val severity: String? = null,
    @SerialName("human_label") val humanLabel: String? = null,
    val scope: ConstraintScope? = null,
    @SerialName("payload_json") val payloadJson: String? = null,
    /** For set_contextual_state. */
    val flag: String? = null,
    val value: String? = null,
    @SerialName("original_phrasing") val originalPhrasing: String? = null,
)

// ── Chef (§5) ────────────────────────────────────────────────────────────

@Serializable
data class ChefInput(
    @SerialName("profile_id") val profileId: String,
    @SerialName("active_constraint_summary") val activeConstraintSummary: List<ConstraintSummary>,
    @SerialName("cooking_context") val cookingContext: String,
    @SerialName("recent_history") val recentHistory: List<String> = emptyList(),
    @SerialName("request_details") val requestDetails: String,
    @SerialName("attempt_number") val attemptNumber: Int = 1,
    @SerialName("previous_failure_reasons") val previousFailureReasons: List<String> = emptyList(),
)

@Serializable
data class ChefOutput(
    @SerialName("meal_name") val mealName: String,
    @SerialName("cultural_context") val culturalContext: String = "",
    val ingredients: List<ChefIngredient>,
    @SerialName("preparation_summary") val preparationSummary: String,
    @SerialName("cooking_instructions") val cookingInstructions: String,
    @SerialName("nutritional_rough_estimate") val nutritionalRoughEstimate: Map<String, Double>? = null,
    @SerialName("confidence_notes") val confidenceNotes: List<String> = emptyList(),
)

@Serializable
data class ChefIngredient(
    val name: String,
    @SerialName("quantity_value") val quantityValue: Double? = null,
    @SerialName("quantity_unit") val quantityUnit: String? = null,
    @SerialName("preparation_note") val preparationNote: String? = null,
) {
    fun toSuggestionIngredient() = SuggestionIngredient(name, quantityValue, quantityUnit)
}

// ── Pantry (§6) ──────────────────────────────────────────────────────────

@Serializable
data class PantryInput(
    @SerialName("profile_id") val profileId: String,
    /** resolve_ingredient | disambiguate | check_availability. */
    @SerialName("query_type") val queryType: String,
    @SerialName("query_string") val queryString: String,
    @SerialName("cooking_context") val cookingContext: String = "",
    @SerialName("current_pantry") val currentPantry: List<String> = emptyList(),
)

@Serializable
data class PantryOutput(
    @SerialName("resolved_name") val resolvedName: String? = null,
    val candidates: List<String> = emptyList(),
    /** high | medium | low. */
    val confidence: String = "low",
    @SerialName("disclosure_notes") val disclosureNotes: List<String> = emptyList(),
)

/** Builds the compact summary agents receive from full constraint records. */
fun Constraint.toSummary(scopeSummary: String): ConstraintSummary =
    ConstraintSummary(
        id = id,
        type = type.storageValue,
        severity = severity.storageValue,
        label = humanLabel,
        scopeSummary = scopeSummary,
    )

/** Convenience used by the orchestrator when applying Curator operations. */
fun ConstraintOperation.severityOrNull(): Severity? = severity?.let { Severity.fromStorage(it) }

/** Payload decoding helper for Curator add/update operations. */
fun ConstraintOperation.decodePayload(
    json: kotlinx.serialization.json.Json,
    type: ai.cuizine.engine.types.ConstraintType,
): ConstraintPayload? =
    payloadJson?.let { raw ->
        when (type) {
            ai.cuizine.engine.types.ConstraintType.Avoid -> {
                json.decodeFromString<ai.cuizine.engine.types.AvoidPayload>(raw)
            }

            ai.cuizine.engine.types.ConstraintType.Prefer -> {
                json.decodeFromString<ai.cuizine.engine.types.PreferPayload>(raw)
            }

            ai.cuizine.engine.types.ConstraintType.Require -> {
                json.decodeFromString<ai.cuizine.engine.types.RequirePayload>(raw)
            }

            ai.cuizine.engine.types.ConstraintType.Limit -> {
                json.decodeFromString<ai.cuizine.engine.types.LimitPayload>(raw)
            }

            ai.cuizine.engine.types.ConstraintType.Contextual -> {
                json.decodeFromString<ai.cuizine.engine.types.ContextualPayload>(raw)
            }
        }
    }
