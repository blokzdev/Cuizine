package ai.cuizine.shared.types

/**
 * A validated meal suggestion as the Today/suggestion surfaces consume it
 * (`ui-ux-spec.md` §5.2; PRD §4-5). Produced by mock agents in Phase 2 and the
 * real Chef → validator pipeline in Phase 5 — identical shape.
 */
data class MealSuggestion(
    val id: String,
    /** e.g. "Masoor dal with spinach". */
    val mealName: String,
    /** The warm one-paragraph suggestion text in the user's idiom — never lectures. */
    val description: String,
    /** How the meal respects active constraints, one plain line per constraint touched. */
    val constraintFit: List<ConstraintFitNote>,
    val ingredients: List<String>,
    val approximateMinutes: Int,
    /** Quiet informational notes (AI-fallback disclosure, soft-limit-approached) — never alarmist. */
    val disclosureNotes: List<String> = emptyList(),
    val validation: SuggestionValidation = SuggestionValidation.Passed,
)

data class ConstraintFitNote(
    val constraintId: String,
    /** e.g. "low glycemic load, high fiber — works with your diabetes". */
    val note: String,
)

sealed interface SuggestionValidation {
    data object Passed : SuggestionValidation

    /** Validation failed without a clean regeneration → conflict surface (`ui-ux-spec.md` §5.2). */
    data class Conflict(
        val conflict: ConstraintConflict,
    ) : SuggestionValidation
}

/**
 * An honest conflict for the resolution surface (`ui-ux-spec.md` §5.2;
 * `constraint-engine-spec.md` §8): never blames the user, never silently
 * violates a constraint, never offers an inviolable for relaxation.
 */
data class ConstraintConflict(
    val conflictingConstraintIds: List<String>,
    /** Calm explanation of why these can't all hold for this request. */
    val explanation: String,
    val options: List<ConflictOption>,
)

data class ConflictOption(
    val id: String,
    /** e.g. "Relax 'prefer rajma' for tonight". */
    val label: String,
    /** Plain statement of what choosing this means. */
    val severityImplication: String,
)

/** One-tap structured rejection reasons (PRD §5) plus optional free text. */
enum class RejectionReason(
    val storageValue: String,
) {
    MissingIngredients("dont_have_ingredients"),
    FamilyWouldNotLikeIt("family_wouldnt_like_it"),
    TooMuchWork("too_much_work_tonight"),
    WrongForHowImFeeling("wrong_for_how_im_feeling"),
    Other("other"),
}
