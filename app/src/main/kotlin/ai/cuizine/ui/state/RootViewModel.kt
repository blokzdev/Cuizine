package ai.cuizine.ui.state

import ai.cuizine.core.config.CuizineConfig
import ai.cuizine.data.repository.ProfileRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

/**
 * Root branching: first run goes to onboarding; an onboarded profile goes to
 * the four-tab shell. The flip to the shell is an explicit event (not a
 * reactive profile observation) because the conversation step completes the
 * profile *before* the sign-in decision finishes Flow A.
 */
data class RootState(
    val phase: RootPhase = RootPhase.Loading,
    /** Visible "sample data" marker — required in mock-backed builds (`ui-ux-spec.md` §10). */
    val isMockIndicatorVisible: Boolean = false,
)

enum class RootPhase { Loading, Onboarding, Shell }

sealed interface RootSideEffect

@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        profileRepository: ProfileRepository,
        config: CuizineConfig,
    ) : ViewModel(),
        ContainerHost<RootState, RootSideEffect> {
        override val container: Container<RootState, RootSideEffect> =
            container(RootState(isMockIndicatorVisible = config.isMockDataIndicatorEnabled))

        init {
            viewModelScope.launch {
                val firstProfile = profileRepository.observeProfile().first()
                intent {
                    reduce {
                        state.copy(phase = if (firstProfile == null) RootPhase.Onboarding else RootPhase.Shell)
                    }
                }
            }
        }

        fun onOnboardingFinished() = intent { reduce { state.copy(phase = RootPhase.Shell) } }

        /** Delete-account (Flow F): a clean return to first-run. */
        fun onAccountDeleted() = intent { reduce { state.copy(phase = RootPhase.Onboarding) } }
    }
