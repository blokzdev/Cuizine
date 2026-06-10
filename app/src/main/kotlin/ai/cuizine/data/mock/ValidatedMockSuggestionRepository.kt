package ai.cuizine.data.mock

import ai.cuizine.data.repository.SuggestionRepository
import ai.cuizine.engine.ConflictChoice
import ai.cuizine.engine.ConstraintGraphApi
import ai.cuizine.engine.conflict.ConflictAnalyzer
import ai.cuizine.engine.types.SuggestionIngredient
import ai.cuizine.engine.types.SuggestionInput
import ai.cuizine.shared.fixtures.SukhiFixtures
import ai.cuizine.shared.types.ConflictOption
import ai.cuizine.shared.types.ConstraintConflict
import ai.cuizine.shared.types.MealSuggestion
import ai.cuizine.shared.types.RejectionReason
import ai.cuizine.shared.types.SuggestionValidation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 evolution of the suggestion mock: the Chef is still scripted
 * (fixture meals — the real Chef arrives in Phase 5), but every served meal
 * now passes through the REAL validator (`engine.validateSuggestion`) and a
 * real failure runs the REAL conflict planning (`ConflictAnalyzer`). The
 * mock-vs-real seam moves one layer deeper, screens untouched.
 */
@Singleton
class ValidatedMockSuggestionRepository
    @Inject
    constructor(
        private val engine: ConstraintGraphApi,
    ) : SuggestionRepository {
        private val current = MutableStateFlow<MealSuggestion?>(null)
        private val generating = MutableStateFlow(false)
        private val history = MutableStateFlow(SukhiFixtures.savedSuggestions)
        private var cycleIndex = 0

        override fun observeCurrentSuggestion(): Flow<MealSuggestion?> = current

        override fun observeIsGenerating(): StateFlow<Boolean> = generating

        override fun observeHistory(): Flow<List<MealSuggestion>> = history

        override suspend fun requestSuggestion() = serveNextValidated()

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
            if (reason == RejectionReason.MissingIngredients) {
                serveValidated(SukhiFixtures.alooMethi)
            } else {
                serveNextValidated()
            }
        }

        override suspend fun regenerate() = serveNextValidated()

        override suspend fun chooseConflictResolution(optionId: String) {
            // "None of these" leaves things as they are (§8: always a valid pick).
            if (optionId != ConflictAnalyzer.NONE_OF_THESE_OPTION_ID) {
                val relaxed = optionId.removePrefix("relax-").removePrefix("clarify-")
                engine.resolveConflict(
                    ai.cuizine.data.repository.EngineProfileRepository.PRIMARY_PROFILE_ID,
                    conflictId = "scripted-conflict",
                    userChoice =
                        ConflictChoice(relaxedConstraintIds = listOf(relaxed), optionLabel = optionId),
                )
            }
            serveValidated(SukhiFixtures.khichdi)
        }

        private suspend fun serveNextValidated() {
            val scripted =
                MEAL_CYCLE[cycleIndex % MEAL_CYCLE.size]
            cycleIndex += 1
            serveValidated(scripted)
        }

        /**
         * Mock Chef → REAL validator. A rejection runs real conflict planning
         * over the actual violations; an Inviolable involvement would surface
         * the honest no-meal-found copy.
         */
        private suspend fun serveValidated(meal: MealSuggestion) {
            generating.value = true
            delay(THINKING_MILLIS)
            val verdict =
                engine.validateSuggestion(
                    ai.cuizine.data.repository.EngineProfileRepository.PRIMARY_PROFILE_ID,
                    meal.toSuggestionInput(),
                )
            if (verdict.passed) {
                generating.value = false
                current.value =
                    meal.copy(
                        disclosureNotes = (meal.disclosureNotes + verdict.disclosureNotes).distinct(),
                        validation = SuggestionValidation.Passed,
                    )
                return
            }
            // Real conflict planning from the real violations (§8 Steps 1+3).
            val analysis = ConflictAnalyzer.analyzeFailures(listOf(verdict))
            val active =
                engine
                    .queryActive(
                        ai.cuizine.data.repository.EngineProfileRepository.PRIMARY_PROFILE_ID,
                    ).constraints
            val blamed = active.filter { it.id in analysis.blamedConstraintIds.toSet() }
            val plan = ConflictAnalyzer.planResolution(blamed, analysis)
            generating.value = false
            current.value =
                meal.copy(
                    validation =
                        SuggestionValidation.Conflict(
                            when (plan) {
                                is ConflictAnalyzer.ResolutionPlan.NoMealFound -> {
                                    ConstraintConflict(
                                        conflictingConstraintIds = plan.inviolableConstraintIds,
                                        explanation =
                                            "I couldn't find a meal tonight that works without crossing " +
                                                "a rule we never cross. Could you tell me more about what " +
                                                "you're in the mood for?",
                                        options = emptyList(),
                                    )
                                }

                                is ConflictAnalyzer.ResolutionPlan.SurfaceToUser -> {
                                    ConstraintConflict(
                                        conflictingConstraintIds = analysis.blamedConstraintIds,
                                        explanation =
                                            "These are pulling in different directions for this meal. " +
                                                "Here are honest options — you decide.",
                                        options =
                                            plan.options.map { option ->
                                                ConflictOption(
                                                    id = option.id,
                                                    label = optionLabel(option, blamed),
                                                    severityImplication = optionImplication(option),
                                                )
                                            },
                                    )
                                }
                            },
                        ),
                )
        }

        private fun optionLabel(
            option: ConflictAnalyzer.ResolutionOption,
            blamed: List<ai.cuizine.shared.types.Constraint>,
        ): String =
            when {
                option.id == ConflictAnalyzer.NONE_OF_THESE_OPTION_ID -> {
                    "None of these — I'll figure it out myself"
                }

                option.isContextualClarification -> {
                    "Is that still going on? Maybe it's settled"
                }

                else -> {
                    val constraint = blamed.firstOrNull { it.id in option.relaxesConstraintIds }
                    "Set aside “${constraint?.humanLabel ?: "this rule"}” for tonight"
                }
            }

        private fun optionImplication(option: ConflictAnalyzer.ResolutionOption): String =
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
            }

        private fun MealSuggestion.toSuggestionInput(): SuggestionInput =
            SuggestionInput(
                mealName = mealName,
                ingredients = ingredients.map { SuggestionIngredient(name = it, 100.0, "g") },
                preparationSummary = description,
            )

        private companion object {
            const val THINKING_MILLIS = 1200L

            /** Scripted Chef meals; the third deliberately trips Tuesday-vegetarian rules. */
            val MEAL_CYCLE: List<MealSuggestion> =
                listOf(
                    SukhiFixtures.masoorDal,
                    SukhiFixtures.rajmaBrownRice,
                    // A meal that violates Tuesday-vegetarian (and halal-style
                    // avoids) so the REAL validator rejects it and the REAL
                    // conflict planner builds the options.
                    MealSuggestion(
                        id = "scripted-keema",
                        mealName = "Beef keema with peas",
                        description = "A scripted non-vegetarian meal used to exercise real validation.",
                        constraintFit = emptyList(),
                        ingredients = listOf("Beef", "Peas", "Onion", "Garam masala"),
                        approximateMinutes = 40,
                    ),
                    SukhiFixtures.alooMethi,
                    SukhiFixtures.khichdi,
                )
        }
    }
