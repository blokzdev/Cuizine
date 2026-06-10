package ai.cuizine.agents

import ai.cuizine.agents.Orchestrator.Response
import ai.cuizine.data.repository.ProfileRepository
import ai.cuizine.data.repository.SuggestionRepository
import ai.cuizine.engine.conflict.ConflictAnalyzer
import ai.cuizine.shared.types.ConflictOption
import ai.cuizine.shared.types.Constraint
import ai.cuizine.shared.types.ConstraintConflict
import ai.cuizine.shared.types.ConversationAuthor
import ai.cuizine.shared.types.ConversationContext
import ai.cuizine.shared.types.ConversationService
import ai.cuizine.shared.types.ConversationTurn
import ai.cuizine.shared.types.CookingFor
import ai.cuizine.shared.types.CulturalContext
import ai.cuizine.shared.types.InlineResult
import ai.cuizine.shared.types.MealSuggestion
import ai.cuizine.shared.types.RejectionReason
import ai.cuizine.shared.types.SuggestionValidation
import ai.cuizine.shared.types.ThinkingState
import ai.cuizine.shared.types.agents.ChefOutput
import ai.cuizine.shared.types.agents.UserIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Phase 5: the REAL conversation behind the same `ConversationService`
 * contract the Phase 2 mock implemented — the user talks, the orchestrator
 * routes, the Curator writes through the engine. Screens unchanged.
 */
class OrchestratorConversationService(
    private val orchestrator: Orchestrator,
    private val profileRepository: ProfileRepository,
    private val profileId: String,
    private val scope: CoroutineScope,
) : ConversationService {
    private val turns = MutableStateFlow<List<ConversationTurn>>(emptyList())
    private val thinking = MutableStateFlow(ThinkingState())
    private var context: ConversationContext = ConversationContext.General
    private val userAnswers = mutableListOf<String>()

    override fun observeTurns(): Flow<List<ConversationTurn>> = turns

    override fun observeThinking(): Flow<ThinkingState> = thinking

    override fun startSession(context: ConversationContext) {
        this.context = context
        turns.value = emptyList()
        userAnswers.clear()
        orchestrator.clearSession(profileId)
        when (context) {
            ConversationContext.Onboarding -> {
                scope.launch {
                    think("Getting ready…")
                    val response =
                        orchestrator.handle(UserIntent.BeginConstraintConversation(profileId))
                    deliver(response)
                }
            }

            ConversationContext.General -> {
                cuizineSays("I'm listening — tell me anything about food, your day, or what you feel like eating.")
            }

            is ConversationContext.AboutSuggestion -> {
                scope.launch {
                    think("Looking at why I picked this…")
                    deliver(
                        orchestrator.handle(
                            UserIntent.RequestSuggestionExplanation(profileId, context.suggestionId),
                        ),
                    )
                }
            }
        }
    }

    override suspend fun send(text: String) {
        turns.update { it + ConversationTurn(newId(), ConversationAuthor.User, text) }
        think("Considering what you've told me…")
        val intent =
            when (context) {
                ConversationContext.Onboarding -> {
                    userAnswers += text
                    UserIntent.ContinueConstraintConversation(profileId, text)
                }

                else -> {
                    UserIntent.FreeTextUpdate(profileId, text)
                }
            }
        deliver(orchestrator.handle(intent))
    }

    override suspend fun confirmOnboarding() {
        // The constraints were written turn by turn through the engine; the
        // profile row itself is created here. Cultural context fields beyond
        // the conversation answers are a known contract gap (DECISION-LOG
        // #5b) — v1 derives a light context from the answers.
        profileRepository.completeOnboarding(
            displayName = "Myself",
            culturalContext =
                CulturalContext(
                    cuisineOrigins = listOfNotNull(userAnswers.firstOrNull()?.take(60)),
                ),
            cookingFor =
                CookingFor(
                    householdSize =
                        userAnswers.getOrNull(1)?.let { Regex("[0-9]+").find(it)?.value?.toIntOrNull() } ?: 1,
                    householdCompositionNotes = userAnswers.getOrNull(1),
                ),
            initialConstraints = emptyList(),
        )
    }

    private fun deliver(response: Response) {
        doneThinking()
        when (response) {
            is Response.ConversationReply -> {
                val inline =
                    response.appliedConstraintLabels.firstOrNull()?.let { label ->
                        InlineResult.CapturedContext(flag = label, acknowledgement = "Noted: $label")
                    }
                cuizineSays(response.message, inline)
                response.disclosures.forEach { note ->
                    cuizineSays(note)
                }
            }

            is Response.Error -> {
                cuizineSays(response.warmMessage)
            }

            else -> {
                Unit
            } // suggestion responses surface on Today, not in chat
        }
    }

    private fun cuizineSays(
        text: String,
        inline: InlineResult? = null,
    ) {
        turns.update { it + ConversationTurn(newId(), ConversationAuthor.Cuizine, text, inline) }
    }

    private fun think(line: String) {
        thinking.value = ThinkingState(isThinking = true, revealedLine = line)
    }

    private fun doneThinking() {
        thinking.value = ThinkingState()
    }

    private fun newId() = "turn-${UUID.randomUUID()}"
}

/**
 * Phase 5: the REAL suggestion pipeline behind the Phase 2 contract — Chef
 * through the orchestrator's regeneration loop, real validation, real
 * conflict planning. Screens unchanged.
 */
class OrchestratorSuggestionRepository(
    private val orchestrator: Orchestrator,
    private val profileId: String,
) : SuggestionRepository {
    private val current = MutableStateFlow<MealSuggestion?>(null)
    private val generating = MutableStateFlow(false)
    private val history = MutableStateFlow<List<MealSuggestion>>(emptyList())
    private var lastRequestDetails = "dinner tonight"

    override fun observeCurrentSuggestion(): Flow<MealSuggestion?> = current

    override fun observeIsGenerating(): Flow<Boolean> = generating

    override fun observeHistory(): Flow<List<MealSuggestion>> = history

    override suspend fun requestSuggestion() {
        lastRequestDetails = "dinner tonight"
        run(UserIntent.RequestMealSuggestion(profileId, lastRequestDetails))
    }

    override suspend fun acceptSuggestion(suggestionId: String) {
        val accepted = current.value ?: return
        if (accepted.id == suggestionId) {
            history.update { listOf(accepted) + it.filterNot { saved -> saved.id == suggestionId } }
        }
    }

    override suspend fun rejectSuggestion(
        suggestionId: String,
        reason: RejectionReason,
        freeText: String?,
    ) {
        run(
            UserIntent.RejectSuggestion(
                profileId = profileId,
                suggestionId = suggestionId,
                reason = reason.storageValue,
                freeText = freeText,
            ),
        )
    }

    override suspend fun regenerate() {
        lastRequestDetails = "something different from the last suggestion"
        run(UserIntent.RequestMealSuggestion(profileId, lastRequestDetails))
    }

    override suspend fun chooseConflictResolution(optionId: String) {
        val conflict =
            (current.value?.validation as? SuggestionValidation.Conflict)?.conflict
        val option = conflict?.options?.firstOrNull { it.id == optionId }
        generating.value = true
        val response =
            orchestrator.resolveConflictAndRetry(
                profileId = profileId,
                relaxedConstraintIds =
                    if (option == null || option.id == ConflictAnalyzer.NONE_OF_THESE_OPTION_ID) {
                        emptyList()
                    } else {
                        listOf(option.id.removePrefix("relax-").removePrefix("clarify-"))
                    },
                optionLabel = option?.label ?: optionId,
                requestDetails = lastRequestDetails,
            )
        generating.value = false
        current.value = map(response)
    }

    private suspend fun run(intent: UserIntent) {
        generating.value = true
        val response = orchestrator.handle(intent)
        generating.value = false
        current.value = map(response)
    }

    private fun map(response: Response): MealSuggestion? =
        when (response) {
            is Response.SuggestionReady -> {
                response.chefOutput.toMealSuggestion(response.verdict.disclosureNotes)
            }

            is Response.ConflictToResolve -> {
                conflictSuggestion(response.blamedConstraints, response.plan)
            }

            is Response.NoMealFound -> {
                MealSuggestion(
                    id = "no-meal-${UUID.randomUUID()}",
                    mealName = "",
                    description = "",
                    constraintFit = emptyList(),
                    ingredients = emptyList(),
                    approximateMinutes = 0,
                    validation =
                        SuggestionValidation.Conflict(
                            ConstraintConflict(
                                conflictingConstraintIds = emptyList(),
                                explanation = response.message,
                                options = emptyList(),
                            ),
                        ),
                )
            }

            else -> {
                null
            }
        }

    private fun conflictSuggestion(
        blamed: List<Constraint>,
        plan: ConflictAnalyzer.ResolutionPlan.SurfaceToUser,
    ): MealSuggestion =
        MealSuggestion(
            id = "conflict-${UUID.randomUUID()}",
            mealName = "",
            description = "",
            constraintFit = emptyList(),
            ingredients = emptyList(),
            approximateMinutes = 0,
            validation =
                SuggestionValidation.Conflict(
                    ConstraintConflict(
                        conflictingConstraintIds = blamed.map { it.id },
                        explanation =
                            "These are pulling in different directions for this meal. " +
                                "Here are honest options — you decide.",
                        options =
                            plan.options.map { option ->
                                ConflictOption(
                                    id = option.id,
                                    label =
                                        when {
                                            option.id == ConflictAnalyzer.NONE_OF_THESE_OPTION_ID -> {
                                                "None of these — I'll figure it out myself"
                                            }

                                            option.isContextualClarification -> {
                                                "Is that still going on? Maybe it's settled"
                                            }

                                            else -> {
                                                val constraint =
                                                    blamed.firstOrNull { it.id in option.relaxesConstraintIds }
                                                "Set aside “${constraint?.humanLabel ?: "this rule"}” for tonight"
                                            }
                                        },
                                    severityImplication =
                                        when {
                                            option.id == ConflictAnalyzer.NONE_OF_THESE_OPTION_ID -> {
                                                "Nothing changes. Tonight's cooking is fully yours."
                                            }

                                            option.isContextualClarification -> {
                                                "If it's settled, the related rules quietly step back."
                                            }

                                            else -> {
                                                "Tonight only — it comes right back tomorrow."
                                            }
                                        },
                                )
                            },
                    ),
                ),
        )

    private fun ChefOutput.toMealSuggestion(disclosures: List<String>): MealSuggestion =
        MealSuggestion(
            id = "suggestion-${UUID.randomUUID()}",
            mealName = mealName,
            description = "$preparationSummary\n\n$cookingInstructions".trim(),
            constraintFit = emptyList(),
            ingredients = ingredients.map { it.name },
            approximateMinutes = 0,
            disclosureNotes = (disclosures + confidenceNotes).distinct(),
        )
}
