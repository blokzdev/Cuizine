package ai.cuizine.shared.types

/**
 * A pantry item as the Pantry surface consumes it (`ui-ux-spec.md` §5.3).
 * v1 pantry is optional manual entry; an empty pantry is normal, never nagged
 * about (PRD §5).
 */
data class PantryEntry(
    val id: String,
    val name: String,
    val quantityValue: Double? = null,
    val quantityUnit: String? = null,
    /** Display grouping, e.g. "Staples", "Vegetables", "Spices". */
    val group: String,
)
