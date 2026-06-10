package ai.cuizine.ui.state.today

import ai.cuizine.data.repository.ProfileRepository
import ai.cuizine.data.repository.SuggestionRepository
import ai.cuizine.shared.types.Constraint
import ai.cuizine.shared.types.ConstraintConflict
import ai.cuizine.shared.types.MealSuggestion
import ai.cuizine.shared.types.RejectionReason
import ai.cuizine.shared.types.SuggestionValidation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

/**
 * The Today surface contract (`ui-ux-spec.md` §5.2). One suggestion at a time
 * (§12 UI tentative answer), regenerate as the escape hatch; an honest
 * conflict replaces the card with the resolution surface — never a silent
 * violation. Identical for mock and real backing (DECISION-LOG #2b).
 */
data class TodayState(
    val currentSuggestion: MealSuggestion? = null,
    val isGenerating: Boolean = false,
    /** Present when the latest validation could not cleanly regenerate (Flow D). */
    val activeConflict: ConstraintConflict? = null,
    /** The conflicting constraints resolved to renderable form. */
    val conflictConstraints: List<Constraint> = emptyList(),
    val isRejectionSheetVisible: Boolean = false,
)

sealed interface TodaySideEffect {
    data object OpenSuggestionDetail : TodaySideEffect

    /** "Why this suggestion?" / "tell Cuizine something" — opens the conversation in context. */
    data object OpenConversationGeneral : TodaySideEffect

    data class OpenConversationAboutSuggestion(
        val suggestionId: String,
    ) : TodaySideEffect
}

@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        private val suggestionRepository: SuggestionRepository,
        profileRepository: ProfileRepository,
    ) : ViewModel(),
        ContainerHost<TodayState, TodaySideEffect> {
        override val container: Container<TodayState, TodaySideEffect> =
            container(TodayState())

        init {
            combine(
                suggestionRepository.observeCurrentSuggestion(),
                suggestionRepository.observeIsGenerating(),
                profileRepository.observeConstraints(),
            ) { suggestion, isGenerating, constraints ->
                val conflict = (suggestion?.validation as? SuggestionValidation.Conflict)?.conflict
                TodayState(
                    currentSuggestion = if (conflict == null) suggestion else null,
                    isGenerating = isGenerating,
                    activeConflict = conflict,
                    conflictConstraints =
                        conflict?.conflictingConstraintIds.orEmpty().mapNotNull { id ->
                            constraints.firstOrNull { it.id == id }
                        },
                )
            }.onEach { newState ->
                intent {
                    reduce { newState.copy(isRejectionSheetVisible = state.isRejectionSheetVisible) }
                }
            }.launchIn(viewModelScope)
        }

        fun onRequestSuggestion() = intent { suggestionRepository.requestSuggestion() }

        fun onAcceptSuggestion() =
            intent {
                val suggestion = state.currentSuggestion ?: return@intent
                suggestionRepository.acceptSuggestion(suggestion.id)
            }

        fun onOpenRejectionSheet() = intent { reduce { state.copy(isRejectionSheetVisible = true) } }

        fun onDismissRejectionSheet() = intent { reduce { state.copy(isRejectionSheetVisible = false) } }

        fun onRejectSuggestion(
            reason: RejectionReason,
            freeText: String?,
        ) = intent {
            val suggestion = state.currentSuggestion ?: return@intent
            reduce { state.copy(isRejectionSheetVisible = false) }
            suggestionRepository.rejectSuggestion(suggestion.id, reason, freeText)
        }

        fun onRegenerate() = intent { suggestionRepository.regenerate() }

        fun onChooseConflictResolution(optionId: String) =
            intent { suggestionRepository.chooseConflictResolution(optionId) }

        fun onOpenSuggestionDetail() = intent { postSideEffect(TodaySideEffect.OpenSuggestionDetail) }

        fun onTellCuizineSomething() = intent { postSideEffect(TodaySideEffect.OpenConversationGeneral) }

        fun onAskWhy() =
            intent {
                val suggestion = state.currentSuggestion ?: return@intent
                postSideEffect(TodaySideEffect.OpenConversationAboutSuggestion(suggestion.id))
            }
    }
