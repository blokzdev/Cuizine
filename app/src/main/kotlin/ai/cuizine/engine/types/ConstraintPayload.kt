package ai.cuizine.engine.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The discriminated payload union for `constraints.payload_json`
 * (`data-model.md` §4). The discriminator is the `constraints.type` COLUMN,
 * not a field inside the JSON — each subtype (de)serializes independently and
 * the repository picks the subtype from [ConstraintType] at read time.
 * All JSON field names are snake_case per `data-model.md` §2.
 */
sealed interface ConstraintPayload

/** What an Avoid constraint targets: an ingredient, a category, or a nutritional property. */
@Serializable
data class AvoidTarget(
    val kind: String,
    val value: String,
    val reference: String? = null,
)

/** A carve-out from an Avoid constraint (e.g. "ghee is fine despite dairy avoidance"). */
@Serializable
data class AvoidException(
    val kind: String,
    val value: String,
    val reference: String? = null,
    val reason: String? = null,
)

@Serializable
data class AvoidPayload(
    val target: AvoidTarget,
    val exceptions: List<AvoidException> = emptyList(),
) : ConstraintPayload

@Serializable
data class PreferTarget(
    val kind: String,
    val value: String,
)

@Serializable
data class PreferPayload(
    val target: PreferTarget,
    val strength: String,
) : ConstraintPayload

@Serializable
data class Threshold(
    val value: Double,
    val unit: String,
)

@Serializable
data class RequireTarget(
    val kind: String,
    val value: String,
    val threshold: Threshold? = null,
)

@Serializable
data class RequirePayload(
    val target: RequireTarget,
    val window: String,
) : ConstraintPayload

@Serializable
data class LimitTarget(
    val kind: String,
    val value: String,
)

@Serializable
data class LimitPayload(
    val target: LimitTarget,
    val ceiling: Threshold,
    val window: String,
    @SerialName("hard_or_soft") val hardOrSoft: String,
) : ConstraintPayload

@Serializable
data class ContextualState(
    val flag: String,
    val value: String? = null,
    val unit: String? = null,
)

@Serializable
data class ContextualPayload(
    val state: ContextualState,
    /** IDs of constraints this contextual state activates. */
    val triggers: List<String> = emptyList(),
) : ConstraintPayload
