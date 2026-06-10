package ai.cuizine.shared.types

/**
 * The unified conversation surface contract (`ui-ux-spec.md` §5.6). The user
 * talks to Cuizine — never to a named agent (ADR 0006). Inline results let
 * Cuizine's replies carry actionable content.
 */
data class ConversationTurn(
    val id: String,
    val author: ConversationAuthor,
    val text: String,
    val inlineResult: InlineResult? = null,
)

enum class ConversationAuthor { User, Cuizine }

sealed interface InlineResult {
    /** A constraint Cuizine captured from what the user said, reflected for confirmation. */
    data class CapturedConstraint(
        val constraint: Constraint,
    ) : InlineResult

    /** A contextual signal noted ("got it — your sugar was high this morning"). */
    data class CapturedContext(
        val flag: String,
        val acknowledgement: String,
    ) : InlineResult

    /** An inline meal suggestion the user can act on directly. */
    data class SuggestedMeal(
        val suggestion: MealSuggestion,
    ) : InlineResult
}

/** What kind of thinking is happening — one calm line, no agent roster (`ui-ux-spec.md` §5.6). */
data class ThinkingState(
    val isThinking: Boolean = false,
    val revealedLine: String? = null,
)
