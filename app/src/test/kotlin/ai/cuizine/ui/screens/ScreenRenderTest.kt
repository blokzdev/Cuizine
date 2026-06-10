package ai.cuizine.ui.screens

import ai.cuizine.data.mock.MockPantryRepository
import ai.cuizine.data.mock.MockProfileRepository
import ai.cuizine.data.mock.MockSuggestionRepository
import ai.cuizine.shared.fixtures.SukhiFixtures
import ai.cuizine.ui.screens.pantry.PantryScreen
import ai.cuizine.ui.screens.profile.ProfileScreen
import ai.cuizine.ui.screens.today.TodayScreen
import ai.cuizine.ui.state.pantry.PantryViewModel
import ai.cuizine.ui.state.profile.ProfileViewModel
import ai.cuizine.ui.state.today.TodayViewModel
import ai.cuizine.ui.theme.CuizineTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Screen-render checks against the canonical fixtures (`roadmap.md` §3 Phase
 * 2: "Compose UI tests that verify each screen renders"). ViewModels are
 * constructed directly on the mock seam — no Hilt graph needed.
 */
@RunWith(RobolectricTestRunner::class)
class ScreenRenderTest {
    @get:Rule
    val compose = createComposeRule()

    /** The mock starts at first-run; Profile rendering needs an onboarded one. */
    private fun onboardedProfiles(): MockProfileRepository =
        MockProfileRepository().also { repo ->
            runBlocking {
                repo.completeOnboarding(
                    displayName = SukhiFixtures.profile.displayName,
                    culturalContext = SukhiFixtures.profile.culturalContext,
                    cookingFor = SukhiFixtures.profile.cookingFor,
                    initialConstraints = SukhiFixtures.constraints,
                )
            }
        }

    @Test
    fun profileScreen_rendersConstraintGraphInHumanTerms() {
        val viewModel = ProfileViewModel(onboardedProfiles())
        compose.setContent {
            CuizineTheme(useDynamicColor = false) {
                ProfileScreen(
                    viewModel = viewModel,
                    onOpenConstraintDetail = {},
                    onOpenConversation = {},
                )
            }
        }
        compose.onNodeWithText("Punjabi · North Indian").assertIsDisplayed()
        // First severity group renders within the lazy viewport; deeper groups
        // are exercised in the on-emulator walkthrough (lazy lists don't
        // compose below the fold).
        compose.onNodeWithText("Keep sodium under 2,000 mg a day").assertExists()
        compose.onNodeWithText("Cooking for 5").assertIsDisplayed()
    }

    @Test
    fun pantryScreen_rendersSeededGroupsAndItems() {
        val viewModel = PantryViewModel(MockPantryRepository())
        compose.setContent {
            CuizineTheme(useDynamicColor = false) {
                PantryScreen(viewModel = viewModel)
            }
        }
        compose.onNodeWithText("Staples").assertIsDisplayed()
        compose.onNodeWithText("Masoor dal").assertIsDisplayed()
    }

    @Test
    fun todayScreen_emptyState_invitesFirstSuggestion() {
        val viewModel = TodayViewModel(MockSuggestionRepository(), MockProfileRepository())
        compose.setContent {
            CuizineTheme(useDynamicColor = false) {
                TodayScreen(
                    onOpenSuggestionDetail = {},
                    onOpenConversation = {},
                    viewModel = viewModel,
                )
            }
        }
        compose.onNodeWithText("What should we cook today?").assertIsDisplayed()
        compose.onNodeWithText("Suggest a meal").assertIsDisplayed()
        compose.onNodeWithText("Tell Cuizine something").assertIsDisplayed()
    }

    // NOTE: the dynamic request→suggestion→reject path is asserted at the
    // container level (TodayViewModelTest) — Robolectric's paused main looper
    // cannot drive Main-dispatched Orbit intents mid-composition. The visual
    // path is verified in the on-emulator Flow B walkthrough (phase report).
}
