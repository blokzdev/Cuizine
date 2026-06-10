package ai.cuizine.engine.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The five constraint types (`constraint-engine-spec.md` §4). This set cannot
 * change without an ADR (`data-model.md` §8). The `constraints.type` column
 * stores [storageValue] and discriminates which [ConstraintPayload] subtype
 * `payload_json` deserializes to.
 */
@Serializable
enum class ConstraintType(
    val storageValue: String,
) {
    @SerialName("avoid")
    Avoid("avoid"),

    @SerialName("prefer")
    Prefer("prefer"),

    @SerialName("require")
    Require("require"),

    @SerialName("limit")
    Limit("limit"),

    @SerialName("contextual")
    Contextual("contextual"),
    ;

    companion object {
        fun fromStorage(value: String): ConstraintType =
            entries.firstOrNull { it.storageValue == value }
                ?: throw IllegalArgumentException("Unknown constraint type stored value: $value")
    }
}
