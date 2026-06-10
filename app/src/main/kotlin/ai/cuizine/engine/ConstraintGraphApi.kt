package ai.cuizine.engine

import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.SuggestionInput
import ai.cuizine.engine.types.ValidationResult
import ai.cuizine.shared.types.Constraint

/**
 * The constraint graph API — exactly eleven operations
 * (`constraint-engine-spec.md` §6). Every agent and UI path goes through
 * this contract; there is no other read or write path to the graph. New
 * operations require a deliberate decision (ADR-level if architectural).
 */
interface ConstraintGraphApi {
    // ── Reads ────────────────────────────────────────────────────────────
    suspend fun queryActive(
        profileId: String,
        atTimeIso: String? = null,
    ): ActiveSet

    suspend fun queryAll(profileId: String): List<Constraint>

    suspend fun queryByType(
        profileId: String,
        type: ConstraintType,
        atTimeIso: String? = null,
    ): ActiveSet

    suspend fun queryProvenance(constraintId: String): ai.cuizine.engine.types.ProvenanceRecord?

    suspend fun queryConflicts(
        profileId: String,
        atTimeIso: String? = null,
    ): List<GraphConflict>

    // ── Writes (Curator-only by architecture; UI edits go through it) ────
    suspend fun addConstraint(
        profileId: String,
        constraint: Constraint,
    ): Constraint

    suspend fun updateConstraint(
        profileId: String,
        constraintId: String,
        changes: ConstraintChanges,
    ): Constraint

    suspend fun removeConstraint(
        profileId: String,
        constraintId: String,
    )

    suspend fun setContextualState(
        profileId: String,
        flag: String,
        value: String? = null,
        expiresAtIso: String? = null,
    ): Constraint

    // ── Validation ───────────────────────────────────────────────────────
    suspend fun validateSuggestion(
        profileId: String,
        suggestion: SuggestionInput,
        atTimeIso: String? = null,
    ): ValidationResult

    suspend fun resolveConflict(
        profileId: String,
        conflictId: String,
        userChoice: ConflictChoice,
    )
}

/** The active set with audit attribution (`constraint-engine-spec.md` §5). */
data class ActiveSet(
    val atTimeIso: String,
    val entries: List<ActiveSetEntry>,
) {
    val constraints: List<Constraint> get() = entries.map { it.constraint }
}

data class ActiveSetEntry(
    val constraint: Constraint,
    /** Scope dimensions that restricted activation (non-default and true). */
    val decisiveDimensions: List<String>,
)

/** A structural conflict present in the active set itself (§6 queryConflicts). */
data class GraphConflict(
    val id: String,
    val constraintIds: List<String>,
    val involvesInviolable: Boolean,
    /** Mechanical description, e.g. "require target overlaps avoid target". */
    val reason: String,
)

/** Field-level changes for updateConstraint (§6); null = unchanged. */
data class ConstraintChanges(
    val severity: ai.cuizine.engine.types.Severity? = null,
    val scope: ai.cuizine.engine.types.ConstraintScope? = null,
    val payload: ai.cuizine.engine.types.ConstraintPayload? = null,
    /** Reason recorded in the modification history (§9). */
    val reason: String? = null,
)

/** The user's pick on a surfaced conflict (§8 Step 4) — session-scoped only. */
data class ConflictChoice(
    /** Constraint IDs the user chose to relax for this session. Never Inviolable. */
    val relaxedConstraintIds: List<String>,
    val optionLabel: String,
)

/** Structured write-rejection (§4: disallowed combinations are never coerced). */
class ConstraintWriteException(
    val code: String,
    message: String,
) : IllegalArgumentException(message)
