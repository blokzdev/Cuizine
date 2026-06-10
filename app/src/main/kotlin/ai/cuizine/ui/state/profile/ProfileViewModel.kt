package ai.cuizine.ui.state.profile

import ai.cuizine.data.repository.ProfileRepository
import ai.cuizine.engine.types.Severity
import ai.cuizine.shared.types.Constraint
import ai.cuizine.shared.types.ProfileOverview
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
 * The Profile feature area (`ui-ux-spec.md` §5.4): the constraint graph in
 * human terms — the user's own words first, severity always visible, scope
 * and provenance one tap away. Single profile in v1; the household section
 * stays structurally ready for v2 (ADR 0004).
 */
data class ProfileState(
    val profile: ProfileOverview? = null,
    val constraints: List<Constraint> = emptyList(),
)

sealed interface ProfileSideEffect {
    data class OpenConstraintDetail(
        val constraintId: String,
    ) : ProfileSideEffect

    /** Adding a constraint is a conversation, not a form (`ui-ux-spec.md` §5.4). */
    data object OpenConversation : ProfileSideEffect
}

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
    ) : ViewModel(),
        ContainerHost<ProfileState, ProfileSideEffect> {
        override val container: Container<ProfileState, ProfileSideEffect> =
            container(ProfileState())

        init {
            combine(
                profileRepository.observeProfile(),
                profileRepository.observeConstraints(),
            ) { profile, constraints -> ProfileState(profile = profile, constraints = constraints) }
                .onEach { newState -> intent { reduce { newState } } }
                .launchIn(viewModelScope)
        }

        fun onConstraintClicked(constraintId: String) =
            intent { postSideEffect(ProfileSideEffect.OpenConstraintDetail(constraintId)) }

        fun onAddConstraint() = intent { postSideEffect(ProfileSideEffect.OpenConversation) }

        /** The warn-before-lowering confirmation lives in the UI; this applies the decision. */
        fun onSeverityChanged(
            constraintId: String,
            severity: Severity,
        ) = intent { profileRepository.updateConstraintSeverity(constraintId, severity) }

        fun onRemoveConstraint(constraintId: String) = intent { profileRepository.removeConstraint(constraintId) }
    }

/** One quiet line summarizing where a constraint applies (`ui-ux-spec.md` §5.4). */
fun Constraint.scopeSummary(): String {
    val temporal =
        when (scope.temporal.kind) {
            "always" -> {
                "Always"
            }

            "weekly" -> {
                scope.temporal.weekdays
                    ?.joinToString(", ") { day -> day.replaceFirstChar(Char::uppercase) + "s" }
                    ?: "Weekly"
            }

            "daily_window" -> {
                "Certain hours each day"
            }

            "date_bounded" -> {
                "For a set period"
            }

            "phase_bounded" -> {
                "During a life phase"
            }

            "composite" -> {
                "On a combined schedule"
            }

            else -> {
                ""
            }
        }
    val contextual =
        scope.contextual.requiredFlags
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { flag -> "while " + flag.replace('_', ' ') }
    return listOfNotNull(temporal.takeIf { it.isNotEmpty() }, contextual).joinToString(" · ")
}
