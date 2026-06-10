package ai.cuizine.ui.state.today

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

/**
 * Everything the Today surface needs to render (`ui-ux-spec.md` §5.2).
 * Phase 1 carries only the welcome shell; the full suggestion contract
 * (currentSuggestion, isGenerating, contextual flags) lands with the Phase 2
 * mock containers and is identical between mock and real implementations.
 */
data class TodayState(
    val isWelcomeVisible: Boolean = false,
)

/** One-time events for the Today surface. None exist yet in Phase 1. */
sealed interface TodaySideEffect

/**
 * The Today container — the first Orbit MVI container in the codebase and the
 * Phase 1 smoke test for Orbit 11 on Kotlin 2.3 / Compose 1.11
 * (DECISION-LOG.md #1a). Containers orchestrate; they hold no business logic
 * (`build-conventions.md` §4).
 */
@HiltViewModel
class TodayViewModel
    @Inject
    constructor() :
    ViewModel(),
        ContainerHost<TodayState, TodaySideEffect> {
        override val container: Container<TodayState, TodaySideEffect> =
            container(TodayState())

        /** The app shell has come to the foreground with no profile yet. */
        fun onAppOpened() =
            intent {
                reduce { state.copy(isWelcomeVisible = true) }
            }
    }
