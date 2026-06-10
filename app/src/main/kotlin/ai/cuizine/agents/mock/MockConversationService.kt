package ai.cuizine.agents.mock

import ai.cuizine.data.repository.ProfileRepository
import ai.cuizine.shared.fixtures.SukhiFixtures
import ai.cuizine.shared.types.ConversationAuthor
import ai.cuizine.shared.types.ConversationContext
import ai.cuizine.shared.types.ConversationService
import ai.cuizine.shared.types.ConversationTurn
import ai.cuizine.shared.types.InlineResult
import ai.cuizine.shared.types.ThinkingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Phase 2 scripted conversation (`ui-ux-spec.md` §10): the Curator
 * "understands" the canonical inputs, onboarding walks the five-question
 * day-0 script (PRD §4), and everything plays with zero LLM calls. The real
 * orchestrator replaces this binding in Phase 5 behind the same contract.
 */
@Singleton
class MockConversationService
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
    ) : ConversationService {
        private val turns = MutableStateFlow<List<ConversationTurn>>(emptyList())
        private val thinking = MutableStateFlow(ThinkingState())
        private var context: ConversationContext = ConversationContext.General
        private var onboardingStep = 0

        // Conversation handling is one-at-a-time: Orbit intents run
        // concurrently (only reductions serialize), so the service owns its
        // ordering — as the real orchestrator will.
        private val sendMutex = Mutex()

        override fun observeTurns(): Flow<List<ConversationTurn>> = turns

        override fun observeThinking(): Flow<ThinkingState> = thinking

        override fun startSession(context: ConversationContext) {
            this.context = context
            turns.value = emptyList()
            when (context) {
                ConversationContext.Onboarding -> {
                    // Onboarding only runs at first-run (profile == null) —
                    // the root branches there; nothing to reset.
                    onboardingStep = 0
                    cuizineSays(ONBOARDING_QUESTIONS[0])
                }

                ConversationContext.General -> {
                    cuizineSays("I'm listening — tell me anything about food, your day, or what you feel like eating.")
                }

                is ConversationContext.AboutSuggestion -> {
                    cuizineSays(
                        "I picked this because it fits everything you've told me — low glycemic load, " +
                            "nothing that troubles your stomach, and flavours from your own kitchen. " +
                            "What would you like to know?",
                    )
                }
            }
        }

        override suspend fun send(text: String) {
            sendMutex.withLock {
                userSays(text)
                think("Considering what you've told me…")
                when (context) {
                    ConversationContext.Onboarding -> advanceOnboarding(text)
                    else -> respondInGeneral(text)
                }
                doneThinking()
            }
        }

        override suspend fun confirmOnboarding() {
            profileRepository.completeOnboarding(
                displayName = SukhiFixtures.profile.displayName,
                culturalContext = SukhiFixtures.profile.culturalContext,
                cookingFor = SukhiFixtures.profile.cookingFor,
                initialConstraints = SukhiFixtures.constraints,
            )
            cuizineSays(
                "That's everything I need to start. I'll keep all of it in mind for every " +
                    "meal — you never have to repeat yourself.",
            )
        }

        private suspend fun advanceOnboarding(answer: String) {
            delay(SCRIPTED_THINK_MILLIS)
            val ack = ONBOARDING_ACKS.getOrNull(onboardingStep) ?: "Got it."
            val captured = onboardingCapture(onboardingStep)
            cuizineSays(ack, captured)
            onboardingStep += 1
            ONBOARDING_QUESTIONS.getOrNull(onboardingStep)?.let { cuizineSays(it) }
        }

        private fun onboardingCapture(step: Int): InlineResult? =
            when (step) {
                2 -> InlineResult.CapturedConstraint(SukhiFixtures.limitRefinedCarbs)
                4 -> InlineResult.CapturedConstraint(SukhiFixtures.tuesdayVegetarian)
                else -> null
            }

        private suspend fun respondInGeneral(text: String) {
            delay(SCRIPTED_THINK_MILLIS)
            val lower = text.lowercase()
            when {
                "sugar" in lower || "glucose" in lower -> {
                    cuizineSays(
                        "Got it — I'll keep meals gentler on your sugar for the next little while. " +
                            "You don't need to do anything else.",
                        InlineResult.CapturedContext(
                            flag = "blood_sugar_elevated",
                            acknowledgement = "Noted: this morning's reading",
                        ),
                    )
                }

                "stomach" in lower || "flare" in lower || "ibs" in lower -> {
                    cuizineSays(
                        "I'm sorry it's a rough stomach day. No onion or garlic until you tell me " +
                            "it's settled — and I'll lean gentler in general.",
                        InlineResult.CapturedContext(flag = "ibs_flare", acknowledgement = "Noted: stomach flare"),
                    )
                }

                "avoid" in lower || "dairy" in lower -> {
                    cuizineSays(
                        "Understood — that's off the list for now. I'll quietly steer around it, " +
                            "and you can change your mind any time in your Profile.",
                    )
                }

                "meal" in lower || "dinner" in lower || "suggest" in lower || "eat" in lower -> {
                    cuizineSays(
                        "Here's a thought for tonight.",
                        InlineResult.SuggestedMeal(SukhiFixtures.masoorDal),
                    )
                }

                else -> {
                    cuizineSays(
                        "I didn't quite catch that — could you say it another way? " +
                            "I'm good with plain words: what you ate, how you feel, what you're avoiding.",
                    )
                }
            }
        }

        private fun userSays(text: String) {
            turns.update { it + ConversationTurn(newId(), ConversationAuthor.User, text) }
        }

        private fun cuizineSays(
            text: String,
            inlineResult: InlineResult? = null,
        ) {
            turns.update { it + ConversationTurn(newId(), ConversationAuthor.Cuizine, text, inlineResult) }
        }

        private fun think(line: String) {
            thinking.value = ThinkingState(isThinking = true, revealedLine = line)
        }

        private fun doneThinking() {
            thinking.value = ThinkingState()
        }

        private fun newId() = "mock-turn-${UUID.randomUUID()}"

        private companion object {
            const val SCRIPTED_THINK_MILLIS = 900L

            // The five-question day-0 script (PRD §4): easiest first, medical
            // framed as avoidance-for-any-reason, never a disease dropdown.
            val ONBOARDING_QUESTIONS =
                listOf(
                    "What kind of food do you usually cook at home?",
                    "Who do you usually cook for?",
                    "Are there foods you're trying to avoid right now — for any reason? Health, religion, preference, anything.",
                    "And the other side: what do you and your family love to eat?",
                    "Any days of the week that are different — fasting days, special meals, traditions?",
                )
            val ONBOARDING_ACKS =
                listOf(
                    "That sounds like a wonderful kitchen. I'll suggest food that belongs in it.",
                    "Got it — cooking for the whole family. I'll keep everyone at the table in mind.",
                    "Thank you for telling me. I'll keep that in mind for everything from now on — quietly.",
                    "Noted, and happily. Favourites matter as much as rules.",
                    "Lovely — I'll remember how your week flows.",
                )
        }
    }
