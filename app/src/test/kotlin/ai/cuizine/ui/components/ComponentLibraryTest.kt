package ai.cuizine.ui.components

import ai.cuizine.engine.types.Severity
import ai.cuizine.shared.fixtures.SukhiFixtures
import ai.cuizine.shared.types.ConversationAuthor
import ai.cuizine.shared.types.ConversationTurn
import ai.cuizine.shared.types.InlineResult
import ai.cuizine.ui.theme.CuizineTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Behavior-focused checks on the §6 component library (`testing-strategy.md`
 * §3: UI layer tests are behavioral, not coverage-targeted). Severity is
 * never color-only; honest copy is actually present.
 */
@RunWith(RobolectricTestRunner::class)
class ComponentLibraryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun severityIndicator_alwaysCarriesATextLabel() {
        compose.setContent {
            CuizineTheme(useDynamicColor = false) {
                SeverityIndicator(severity = Severity.Medical)
            }
        }
        compose.onNodeWithText("Medical").assertIsDisplayed()
    }

    @Test
    fun suggestionCard_showsMealAndFitNotes() {
        compose.setContent {
            CuizineTheme(useDynamicColor = false) {
                SuggestionCard(
                    suggestion = SukhiFixtures.masoorDal,
                    state = SuggestionCardState.Presented,
                )
            }
        }
        compose.onNodeWithText("Masoor dal with spinach").assertIsDisplayed()
        compose.onNodeWithText("· Low glycemic load, high fiber").assertIsDisplayed()
    }

    @Test
    fun conversationMessage_rendersInlineCapturedConstraint() {
        compose.setContent {
            CuizineTheme(useDynamicColor = false) {
                ConversationMessage(
                    turn =
                        ConversationTurn(
                            id = "t1",
                            author = ConversationAuthor.Cuizine,
                            text = "Thank you for telling me.",
                            inlineResult = InlineResult.CapturedConstraint(SukhiFixtures.tuesdayVegetarian),
                        ),
                )
            }
        }
        compose.onNodeWithText("Thank you for telling me.").assertIsDisplayed()
        compose.onNodeWithText("Vegetarian on Tuesdays").assertIsDisplayed()
    }

    @Test
    fun passphraseReveal_showsAllSixWordsAndHonestCopy() {
        compose.setContent {
            CuizineTheme(useDynamicColor = false) {
                PassphraseReveal(passphrase = SukhiFixtures.recoveryPassphrase)
            }
        }
        SukhiFixtures.recoveryPassphrase.words.forEachIndexed { index, word ->
            compose.onNodeWithText("${index + 1}.  $word").assertIsDisplayed()
        }
        compose
            .onNodeWithText(
                "These six words are the only way to read your synced data on a new device. " +
                    "Cuizine cannot recover them for you — there is no master key and no " +
                    "override. Write them somewhere safe, on paper.",
            ).assertIsDisplayed()
    }

    @Test
    fun emptyState_isAnInvitationWithOptionalAction() {
        compose.setContent {
            CuizineTheme(useDynamicColor = false) {
                EmptyState(
                    title = "Your pantry is empty",
                    body = "Add what you have, or skip this for now.",
                    action = { CuizineButton(text = "Add an item", onClick = {}) },
                )
            }
        }
        compose.onNodeWithText("Your pantry is empty").assertIsDisplayed()
        compose.onNodeWithText("Add an item").assertIsDisplayed()
    }

    @Test
    fun gracefulRefusal_isOneSentenceOneOption() {
        compose.setContent {
            CuizineTheme(useDynamicColor = false) {
                GracefulRefusal(
                    sentence = "Household planning arrives with Cuizine Family.",
                    optionLabel = "Back to Today",
                    onOptionClick = {},
                )
            }
        }
        compose.onNodeWithText("Household planning arrives with Cuizine Family.").assertIsDisplayed()
        compose.onNodeWithText("Back to Today").assertIsDisplayed()
    }
}
