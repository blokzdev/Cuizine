package ai.cuizine.engine.conflict

import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.Severity
import ai.cuizine.engine.types.ValidationResult
import ai.cuizine.shared.types.Constraint

/**
 * The mechanical pieces of conflict resolution (ADR 0009;
 * `constraint-engine-spec.md` §8). The orchestrator owns the loop (Phase 5);
 * these pure functions implement Step 1 (failure-pattern analysis), the
 * Step 2 candidate selection, and the Step 3 option construction — including
 * the rule that Inviolable constraints are NEVER in the pickable set.
 *
 * PARK-ALWAYS territory: deviations from this policy are founder decisions.
 */
object ConflictAnalyzer {
    /** Step 1: which constraints caused the rejections, and how narrowly. */
    data class FailureAnalysis(
        /** Blamed constraint ids ordered by rejection frequency (desc). */
        val blamedConstraintIds: List<String>,
        /** True when every rejection traces to the same single constraint. */
        val isNarrow: Boolean,
    )

    fun analyzeFailures(rejections: List<ValidationResult>): FailureAnalysis {
        val frequency =
            rejections
                .flatMap { it.violations }
                .groupingBy { it.constraintId }
                .eachCount()
        val ordered = frequency.entries.sortedByDescending { it.value }.map { it.key }
        return FailureAnalysis(
            blamedConstraintIds = ordered,
            isNarrow = ordered.size == 1,
        )
    }

    /** Step 2 input: the active preference-tier constraints, silently relaxable. */
    fun preferenceRelaxationCandidates(active: List<Constraint>): List<Constraint> =
        active.filter { it.severity == Severity.Preference }

    /** The outcome shape of Step 3 planning. */
    sealed interface ResolutionPlan {
        /**
         * An Inviolable constraint is part of the conflict: Step 3 is skipped
         * entirely — no option may relax it, so the honest no-meal-found
         * fallback is the only path (§8).
         */
        data class NoMealFound(
            val inviolableConstraintIds: List<String>,
        ) : ResolutionPlan

        /** Surface up to three options + the always-present "none of these". */
        data class SurfaceToUser(
            val options: List<ResolutionOption>,
        ) : ResolutionPlan
    }

    data class ResolutionOption(
        val id: String,
        /** Constraint ids this option relaxes for the session (never Inviolable). */
        val relaxesConstraintIds: List<String>,
        /** True for the specialized clarify-the-contextual-state option (§8 edge cases). */
        val isContextualClarification: Boolean = false,
    )

    /** Always present, always valid: "none of these — I'll figure it out myself." */
    const val NONE_OF_THESE_OPTION_ID = "none-of-these"

    fun planResolution(
        blamed: List<Constraint>,
        analysis: FailureAnalysis,
    ): ResolutionPlan {
        val ordered =
            analysis.blamedConstraintIds.mapNotNull { id -> blamed.firstOrNull { it.id == id } }
        if (ordered.any { it.severity == Severity.Inviolable }) {
            return ResolutionPlan.NoMealFound(
                inviolableConstraintIds =
                    ordered.filter { it.severity == Severity.Inviolable }.map { it.id },
            )
        }
        val relaxable =
            ordered.filter {
                it.severity == Severity.Medical || it.severity == Severity.ReligiousCultural
            }
        val contextualDriver = ordered.firstOrNull { it.type == ConstraintType.Contextual }
        val options = mutableListOf<ResolutionOption>()
        relaxable.take(3).forEach { constraint ->
            options +=
                ResolutionOption(
                    id = "relax-${constraint.id}",
                    relaxesConstraintIds = listOf(constraint.id),
                )
        }
        if (contextualDriver != null && options.size < 3) {
            options +=
                ResolutionOption(
                    id = "clarify-${contextualDriver.id}",
                    relaxesConstraintIds = listOf(contextualDriver.id),
                    isContextualClarification = true,
                )
        }
        options += ResolutionOption(id = NONE_OF_THESE_OPTION_ID, relaxesConstraintIds = emptyList())
        return ResolutionPlan.SurfaceToUser(options)
    }
}
