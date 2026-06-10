package ai.cuizine.ui.state.today

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * The first Orbit container test — doubles as the Phase 1 smoke test for
 * Orbit 11 on Kotlin 2.3 (DECISION-LOG.md #1a risk note).
 */
class TodayViewModelTest {
    @Test
    fun onAppOpened_showsWelcome() =
        runTest {
            TodayViewModel().test(this) {
                containerHost.onAppOpened()
                expectState { copy(isWelcomeVisible = true) }
            }
        }
}
