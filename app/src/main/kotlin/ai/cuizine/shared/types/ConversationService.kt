package ai.cuizine.shared.types

import kotlinx.coroutines.flow.Flow

/**
 * The UI's seam to the orchestrator (`technical-architecture.md` Subsystem 4;
 * ADR 0006). The UI sends plain text and renders turns — it never knows which
 * agent answered. Phase 2 binds a scripted mock; Phase 5 binds the real
 * orchestrator behind this same contract.
 */
interface ConversationService {
    fun observeTurns(): Flow<List<ConversationTurn>>

    fun observeThinking(): Flow<ThinkingState>

    /** Where the conversation was opened from — replies stay in context (`ui-ux-spec.md` §5.6). */
    fun startSession(context: ConversationContext)

    suspend fun send(text: String)

    /** Onboarding only: confirm the captured constraints and finish day-0 setup (`ui-ux-spec.md` §5.1). */
    suspend fun confirmOnboarding()
}

sealed interface ConversationContext {
    /** The day-0 constraint conversation — onboarding IS this conversation (PRD §5). */
    data object Onboarding : ConversationContext

    /** Opened from the FAB with no specific anchor. */
    data object General : ConversationContext

    /** Opened from "why this suggestion?" on a suggestion card. */
    data class AboutSuggestion(
        val suggestionId: String,
    ) : ConversationContext
}
