package ai.cuizine.shared.types

/**
 * The profile as the Profile surface consumes it (`ui-ux-spec.md` §5.4).
 * Single profile in v1; the shape already speaks "household" so v2
 * multi-profile slots in without a contract change (ADR 0004).
 */
data class ProfileOverview(
    val id: String,
    val displayName: String,
    val culturalContext: CulturalContext,
    val cookingFor: CookingFor,
    val timezone: String,
)
