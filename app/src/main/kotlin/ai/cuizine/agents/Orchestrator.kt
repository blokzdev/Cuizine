package ai.cuizine.agents

import ai.cuizine.agents.providers.AgentKind
import ai.cuizine.agents.providers.ModelTier
import ai.cuizine.engine.ConflictChoice
import ai.cuizine.engine.ConstraintGraphApi
import ai.cuizine.engine.conflict.ConflictAnalyzer
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.engine.types.AddedContext
import ai.cuizine.engine.types.ConstraintScope
import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.HouseholdScope
import ai.cuizine.engine.types.ProfileScope
import ai.cuizine.engine.types.ProvenanceRecord
import ai.cuizine.engine.types.Severity
import ai.cuizine.engine.types.SuggestionInput
import ai.cuizine.engine.types.TemporalScope
import ai.cuizine.engine.types.ValidationResult
import ai.cuizine.shared.types.AlphaEventLogger
import ai.cuizine.shared.types.Constraint
import ai.cuizine.shared.types.agents.ChefInput
import ai.cuizine.shared.types.agents.ChefOutput
import ai.cuizine.shared.types.agents.ConstraintOperation
import ai.cuizine.shared.types.agents.CuratorInput
import ai.cuizine.shared.types.agents.CuratorOutput
import ai.cuizine.shared.types.agents.HistoryTurn
import ai.cuizine.shared.types.agents.PantryInput
import ai.cuizine.shared.types.agents.PantryOutput
import ai.cuizine.shared.types.agents.UserIntent
import ai.cuizine.shared.types.agents.decodePayload
import ai.cuizine.shared.types.agents.toSummary
import ai.cuizine.shared.types.scopeSummary
import java.util.UUID

/**
 * The orchestrator (`agent-architecture.md` §3): deterministic code — no LLM
 * reasoning of its own, rule-based intent routing, typed input construction,
 * the bounded regeneration loop, conflict resolution, and event logging.
 * Agents never touch the UI; the UI never touches agents — everything
 * crosses here.
 */
class Orchestrator(
    private val engine: ConstraintGraphApi,
    private val invoker: AgentInvoker,
    private val events: AlphaEventLogger,
    private val clock: EngineClock,
) {
    sealed interface Response {
        data class ConversationReply(
            val message: String,
            val disclosures: List<String>,
            val isConversationComplete: Boolean,
            val appliedConstraintLabels: List<String>,
        ) : Response

        data class SuggestionReady(
            val chefOutput: ChefOutput,
            val verdict: ValidationResult,
        ) : Response

        data class ConflictToResolve(
            val blamedConstraints: List<Constraint>,
            val plan: ConflictAnalyzer.ResolutionPlan.SurfaceToUser,
        ) : Response

        data class NoMealFound(
            val message: String,
        ) : Response

        data class PantryResolved(
            val output: PantryOutput,
        ) : Response

        data class Error(
            val warmMessage: String,
        ) : Response
    }

    /** Conversation history accumulated per session — agents stay stateless (§2). */
    private val sessionHistory = mutableMapOf<String, MutableList<HistoryTurn>>()

    suspend fun handle(intent: UserIntent): Response =
        when (intent) {
            is UserIntent.BeginConstraintConversation -> {
                curatorTurn(intent.profileId, "", "begin_constraint_conversation")
            }

            is UserIntent.ContinueConstraintConversation -> {
                curatorTurn(intent.profileId, intent.message, "continue_constraint_conversation")
            }

            is UserIntent.FreeTextUpdate -> {
                curatorTurn(intent.profileId, intent.message, "free_text_update")
            }

            is UserIntent.RequestSuggestionExplanation -> {
                curatorTurn(intent.profileId, "why did you suggest this?", "request_suggestion_explanation")
            }

            is UserIntent.RequestMealSuggestion -> {
                suggestionLoop(intent.profileId, intent.requestDetails)
            }

            is UserIntent.RejectSuggestion -> {
                // Composite intent (§3): Curator records the rejection signal,
                // then the Chef regenerates with that context.
                curatorTurn(
                    intent.profileId,
                    "Not this one: ${intent.reason}. ${intent.freeText.orEmpty()}".trim(),
                    "free_text_update",
                )
                suggestionLoop(
                    intent.profileId,
                    "Something different — the last suggestion was rejected: ${intent.reason}",
                )
            }

            is UserIntent.PantryIngestion -> {
                pantryResolve(intent.profileId, intent.rawName)
            }
        }

    // ── Curator path ─────────────────────────────────────────────────────

    private suspend fun curatorTurn(
        profileId: String,
        message: String,
        intentType: String,
    ): Response {
        val history = sessionHistory.getOrPut(profileId) { mutableListOf() }
        val active = engine.queryActive(profileId).constraints
        val input =
            CuratorInput(
                profileId = profileId,
                currentMessage = message,
                conversationHistory = history.toList(),
                activeConstraintSummary = active.map { it.toSummary(it.scopeSummary()) },
                intentType = intentType,
            )
        if (message.isNotBlank()) history += HistoryTurn(author = "user", text = message)

        val result =
            invoker.invoke<CuratorInput, CuratorOutput>(
                kind = AgentKind.Curator,
                promptFile = "curator.md",
                schemaName = "curator_output",
                tier = curatorTier(message),
                input = input,
            )
        return when (result) {
            is AgentInvoker.AgentResult.Failed -> {
                Response.Error("Something went sideways on my end — could you say that again?")
            }

            is AgentInvoker.AgentResult.Ok -> {
                val output = result.value
                history += HistoryTurn(author = "cuizine", text = output.userMessage)
                val applied = applyOperations(profileId, output.constraintOperations, intentType)
                Response.ConversationReply(
                    message = output.userMessage,
                    disclosures = output.disclosureNotes,
                    isConversationComplete = output.nextIntent == "ready_for_suggestions",
                    appliedConstraintLabels = applied,
                )
            }
        }
    }

    /** §8 escalation: high-stakes medical disclosures get the strongest tier. */
    private fun curatorTier(message: String): ModelTier {
        val lower = message.lowercase()
        val highStakes =
            listOf("hospital", "icu", "diagnosed", "diagnosis", "medication", "prescribed")
                .any { it in lower }
        return if (highStakes) ModelTier.ReasoningEscalated else ModelTier.ReasoningStrong
    }

    private suspend fun applyOperations(
        profileId: String,
        operations: List<ConstraintOperation>,
        intentType: String,
    ): List<String> {
        val applied = mutableListOf<String>()
        for (op in operations) {
            runCatching {
                when (op.operation) {
                    "set_contextual_state" -> {
                        val flag = op.flag ?: return@runCatching
                        engine.setContextualState(profileId, flag, op.value)
                        applied += flag.replace('_', ' ')
                    }

                    "add_constraint" -> {
                        val type = ConstraintType.fromStorage(op.type ?: return@runCatching)
                        val payload = op.decodePayload(invoker.json, type) ?: return@runCatching
                        val record =
                            Constraint(
                                id = "constraint-${UUID.randomUUID()}",
                                profileId = profileId,
                                type = type,
                                severity = Severity.fromStorage(op.severity ?: "preference"),
                                humanLabel = op.humanLabel ?: op.originalPhrasing.orEmpty(),
                                scope = op.scope ?: defaultScope(profileId),
                                payload = payload,
                                provenance =
                                    ProvenanceRecord(
                                        source = "curator_inferred",
                                        addedAt = clock.nowIso(),
                                        addedContext = AddedContext(flow = flowFor(intentType)),
                                        originalPhrasing = op.originalPhrasing,
                                        confidence = "high_user_direct",
                                    ),
                            )
                        engine.addConstraint(profileId, record)
                        applied += record.humanLabel
                    }

                    "remove_constraint" -> {
                        op.constraintId?.let {
                            engine.removeConstraint(profileId, it)
                            applied += "removed"
                        }
                    }

                    else -> {
                        Unit
                    }
                }
            }.onFailure { failure ->
                // §4 error handling: an engine-rejected write is a
                // severity-one alpha event — the prompt should prevent it.
                events.log(
                    eventType = "agent_error",
                    severity = "severity_one",
                    payloadJson = """{"agent":"Curator","error":"engine_rejected_write"}""",
                    profileId = profileId,
                )
            }
        }
        return applied
    }

    private fun flowFor(intentType: String): String =
        if (intentType.startsWith("continue") || intentType.startsWith("begin")) {
            "constraint_conversation"
        } else {
            "free_text_update"
        }

    private fun defaultScope(profileId: String): ConstraintScope =
        ConstraintScope(
            temporal = TemporalScope(kind = "always"),
            household = HouseholdScope(mode = "self"),
            profile = ProfileScope(profileId = profileId),
        )

    // ── Chef path: the regeneration loop, exactly as §3 specifies ────────

    private suspend fun suggestionLoop(
        profileId: String,
        requestDetails: String,
    ): Response {
        events.log("suggestion_requested", "info", """{"request":"meal"}""", profileId)
        val active = engine.queryActive(profileId).constraints
        val failureReasons = mutableListOf<String>()
        val allVerdicts = mutableListOf<ValidationResult>()

        repeat(MAX_ATTEMPTS) { index ->
            val attempt = index + 1
            val chefResult =
                invokeChef(profileId, active, requestDetails, attempt, failureReasons)
                    ?: return Response.Error("Something is slow on my end — could you try again in a moment?")
            events.log("suggestion_generated", "info", """{"attempt":$attempt}""", profileId)

            val verdict = engine.validateSuggestion(profileId, chefResult.toSuggestionInput())
            if (verdict.passed) {
                events.log("suggestion_validated_passed", "info", """{"attempt":$attempt}""", profileId)
                return Response.SuggestionReady(chefResult, verdict)
            }
            allVerdicts += verdict
            failureReasons += verdict.violations.map { it.reason }
            // ADR 0010: a medical/religious violation that an agent produced
            // is the worst failure mode — severity-zero, founder-reviewed.
            val severityZero =
                verdict.violations.any {
                    it.severity == Severity.Medical || it.severity == Severity.ReligiousCultural ||
                        it.severity == Severity.Inviolable
                }
            events.log(
                "suggestion_validated_failed",
                if (severityZero) "severity_zero" else "warn",
                """{"attempt":$attempt,"violations":${verdict.violations.size}}""",
                profileId,
            )
            if (attempt < MAX_ATTEMPTS) {
                events.log("suggestion_regenerated", "info", """{"next_attempt":${attempt + 1}}""", profileId)
            }
        }

        // §3 Step 5: three failures → conflict resolution, not user-facing failure.
        events.log("conflict_resolution_triggered", "warn", """{"attempts":$MAX_ATTEMPTS}""", profileId)
        val analysis = ConflictAnalyzer.analyzeFailures(allVerdicts)
        val blamed = active.filter { it.id in analysis.blamedConstraintIds.toSet() }

        // §8 Step 2: silent preference relaxation — one extra Chef attempt.
        val relaxable = ConflictAnalyzer.preferenceRelaxationCandidates(active)
        if (relaxable.isNotEmpty()) {
            val relaxedTry =
                invokeChef(
                    profileId,
                    active,
                    requestDetails,
                    attemptNumber = MAX_ATTEMPTS + 1,
                    failureReasons =
                        failureReasons +
                            "These preferences are relaxable tonight: ${relaxable.joinToString { it.humanLabel }}",
                )
            if (relaxedTry != null) {
                val verdict = engine.validateSuggestion(profileId, relaxedTry.toSuggestionInput())
                if (verdict.passed) {
                    events.log("conflict_resolved_silently", "info", "{}", profileId)
                    return Response.SuggestionReady(relaxedTry, verdict)
                }
            }
        }

        return when (val plan = ConflictAnalyzer.planResolution(blamed, analysis)) {
            is ConflictAnalyzer.ResolutionPlan.NoMealFound -> {
                Response.NoMealFound(
                    "I couldn't find a meal that works tonight — could you tell me more about " +
                        "what you're in the mood for, or relax one of your constraints for tonight?",
                )
            }

            is ConflictAnalyzer.ResolutionPlan.SurfaceToUser -> {
                events.log("conflict_surfaced_to_user", "warn", """{"options":${plan.options.size}}""", profileId)
                Response.ConflictToResolve(blamedConstraints = blamed, plan = plan)
            }
        }
    }

    private suspend fun invokeChef(
        profileId: String,
        active: List<Constraint>,
        requestDetails: String,
        attemptNumber: Int,
        failureReasons: List<String>,
    ): ChefOutput? {
        val input =
            ChefInput(
                profileId = profileId,
                activeConstraintSummary = active.map { it.toSummary(it.scopeSummary()) },
                cookingContext = "per profile cultural context",
                requestDetails = requestDetails,
                attemptNumber = attemptNumber,
                previousFailureReasons = failureReasons,
            )
        // §8 escalation: attempts ≥ 2 get the stronger tier.
        val tier = if (attemptNumber >= 2) ModelTier.ReasoningEscalated else ModelTier.ReasoningStrong
        val result =
            invoker.invoke<ChefInput, ChefOutput>(
                kind = AgentKind.Chef,
                promptFile = "chef.md",
                schemaName = "chef_output",
                tier = tier,
                input = input,
            )
        return (result as? AgentInvoker.AgentResult.Ok)?.value
    }

    /** Applies the user's conflict pick (§3 Step 6 / engine §8 Step 4), then retries. */
    suspend fun resolveConflictAndRetry(
        profileId: String,
        relaxedConstraintIds: List<String>,
        optionLabel: String,
        requestDetails: String,
    ): Response {
        if (relaxedConstraintIds.isNotEmpty()) {
            engine.resolveConflict(
                profileId,
                conflictId = "conflict-${UUID.randomUUID()}",
                userChoice = ConflictChoice(relaxedConstraintIds, optionLabel),
            )
            events.log("conflict_user_chose_option", "info", """{"option":"$optionLabel"}""", profileId)
        }
        return suggestionLoop(profileId, requestDetails)
    }

    fun clearSession(profileId: String) {
        sessionHistory.remove(profileId)
    }

    private fun ChefOutput.toSuggestionInput(): SuggestionInput =
        SuggestionInput(
            mealName = mealName,
            ingredients = ingredients.map { it.toSuggestionIngredient() },
            preparationSummary = preparationSummary,
            nutritionalRoughEstimate = nutritionalRoughEstimate,
        )

    private suspend fun pantryResolve(
        profileId: String,
        rawName: String,
    ): Response {
        val input =
            PantryInput(
                profileId = profileId,
                queryType = "resolve_ingredient",
                queryString = rawName,
            )
        val result =
            invoker.invoke<PantryInput, PantryOutput>(
                kind = AgentKind.Pantry,
                promptFile = "pantry.md",
                schemaName = "pantry_output",
                tier = ModelTier.Light,
                input = input,
            )
        return when (result) {
            is AgentInvoker.AgentResult.Ok -> {
                Response.PantryResolved(result.value)
            }

            is AgentInvoker.AgentResult.Failed -> {
                Response.Error("I couldn't quite place that one — want to try a different name for it?")
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3 // ADR 0010 — a parameter, not a principle.
    }
}
