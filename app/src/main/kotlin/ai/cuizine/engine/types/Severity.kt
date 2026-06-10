package ai.cuizine.engine.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The four severity tiers (ADR 0009; `constraint-engine-spec.md` §4). This set
 * cannot change without an ADR (`data-model.md` §8). Stored as the snake_case
 * string in the `constraints.severity` column; unknown stored values fail
 * loudly at read time — the code is the enforcement locus for the DDL CHECK
 * (DECISION-LOG.md #1c).
 */
@Serializable
enum class Severity(
    val storageValue: String,
) {
    @SerialName("inviolable")
    Inviolable("inviolable"),

    @SerialName("medical")
    Medical("medical"),

    @SerialName("religious_cultural")
    ReligiousCultural("religious_cultural"),

    @SerialName("preference")
    Preference("preference"),
    ;

    companion object {
        fun fromStorage(value: String): Severity =
            entries.firstOrNull { it.storageValue == value }
                ?: throw IllegalArgumentException("Unknown severity stored value: $value")
    }
}
