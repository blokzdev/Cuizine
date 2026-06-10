package ai.cuizine.engine

import ai.cuizine.engine.ports.ConstraintStore
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.engine.ports.FoodDataPort
import ai.cuizine.engine.scope.ScopeEvaluator
import ai.cuizine.engine.types.AddedContext
import ai.cuizine.engine.types.AvoidPayload
import ai.cuizine.engine.types.ConstraintPayload
import ai.cuizine.engine.types.ConstraintScope
import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.ContextualPayload
import ai.cuizine.engine.types.ContextualState
import ai.cuizine.engine.types.HouseholdScope
import ai.cuizine.engine.types.LimitPayload
import ai.cuizine.engine.types.ModificationRecord
import ai.cuizine.engine.types.ProfileScope
import ai.cuizine.engine.types.ProvenanceRecord
import ai.cuizine.engine.types.RequirePayload
import ai.cuizine.engine.types.Severity
import ai.cuizine.engine.types.SuggestionInput
import ai.cuizine.engine.types.TemporalScope
import ai.cuizine.engine.types.ValidationResult
import ai.cuizine.engine.validator.SuggestionValidator
import ai.cuizine.shared.types.Constraint
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * The constraint engine (`constraint-engine-spec.md` §5–§8): pure Kotlin, no
 * Android imports. Persistence, food data, time, and event logging are ports.
 *
 * Trust-critical: the tier mechanics (§4), the validator delegation (§7), and
 * the never-relax-Inviolable rule (§8) are implemented exactly as specified —
 * deviations are founder-territory (CLAUDE.md §5 park-always).
 */
class ConstraintGraphEngine(
    private val store: ConstraintStore,
    foodData: FoodDataPort,
    private val clock: EngineClock,
    private val scopeEvaluator: ScopeEvaluator = ScopeEvaluator(),
    /** Profile timezone lookup — IANA id per profile (`data-model.md` §3). */
    private val profileTimezone: suspend (String) -> String,
) : ConstraintGraphApi {
    private val validator = SuggestionValidator(foodData)

    /** Session-scoped relaxations (§8 Step 4) — never persisted, never permanent. */
    private val sessionRelaxations = mutableMapOf<String, MutableSet<String>>()

    /** Active-set cache, invalidated on writes and contextual changes (§5). */
    private val activeSetCache = mutableMapOf<String, CachedActiveSet>()

    // ── Reads ────────────────────────────────────────────────────────────

    override suspend fun queryActive(
        profileId: String,
        atTimeIso: String?,
    ): ActiveSet {
        val at = atTimeIso ?: clock.nowIso()
        val cached = activeSetCache[profileId]
        if (cached != null && cached.atMinute == minuteOf(at)) return cached.activeSet
        val computed = computeActiveSet(profileId, at)
        activeSetCache[profileId] = CachedActiveSet(minuteOf(at), computed)
        return computed
    }

    override suspend fun queryAll(profileId: String): List<Constraint> = store.loadAll(profileId)

    override suspend fun queryByType(
        profileId: String,
        type: ConstraintType,
        atTimeIso: String?,
    ): ActiveSet {
        val active = queryActive(profileId, atTimeIso)
        return active.copy(entries = active.entries.filter { it.constraint.type == type })
    }

    override suspend fun queryProvenance(constraintId: String): ProvenanceRecord? =
        store.loadById(constraintId)?.provenance

    override suspend fun queryConflicts(
        profileId: String,
        atTimeIso: String?,
    ): List<GraphConflict> {
        val active = queryActive(profileId, atTimeIso).constraints
        val conflicts = mutableListOf<GraphConflict>()
        // Structural overlap: a require whose target is also an active avoid target.
        val avoids = active.filter { it.type == ConstraintType.Avoid }
        val requires = active.filter { it.type == ConstraintType.Require }
        for (require in requires) {
            val requireTarget = (require.payload as? RequirePayload)?.target?.value ?: continue
            for (avoid in avoids) {
                val avoidPayload = avoid.payload as? AvoidPayload ?: continue
                val overlaps = avoidPayload.target.value.equals(requireTarget, ignoreCase = true)
                val excepted =
                    avoidPayload.exceptions.any { it.value.equals(requireTarget, ignoreCase = true) }
                if (overlaps && !excepted) {
                    conflicts +=
                        GraphConflict(
                            id = "conflict-${require.id}-${avoid.id}",
                            constraintIds = listOf(require.id, avoid.id),
                            involvesInviolable =
                                require.severity == Severity.Inviolable ||
                                    avoid.severity == Severity.Inviolable,
                            reason =
                                "require target '$requireTarget' overlaps an active avoid target " +
                                    "with no exception",
                        )
                }
            }
        }
        return conflicts
    }

    // ── Writes ───────────────────────────────────────────────────────────

    override suspend fun addConstraint(
        profileId: String,
        constraint: Constraint,
    ): Constraint {
        validateWrite(constraint)
        val record =
            constraint.copy(
                id = constraint.id.ifBlank { "constraint-${UUID.randomUUID()}" },
                profileId = profileId,
            )
        require(record.provenance.source.isNotBlank()) {
            "Every constraint must carry provenance (constraint-engine-spec.md §9)."
        }
        store.insert(record)
        invalidate(profileId)
        return record
    }

    override suspend fun updateConstraint(
        profileId: String,
        constraintId: String,
        changes: ConstraintChanges,
    ): Constraint {
        val existing =
            store.loadById(constraintId)
                ?: throw ConstraintWriteException("not_found", "No constraint $constraintId")
        val updated =
            existing.copy(
                severity = changes.severity ?: existing.severity,
                scope = changes.scope ?: existing.scope,
                payload = changes.payload ?: existing.payload,
                provenance =
                    existing.provenance.copy(
                        modificationHistory =
                            existing.provenance.modificationHistory +
                                ModificationRecord(
                                    at = clock.nowIso(),
                                    source = "user_edited",
                                    reason = changes.reason,
                                ),
                    ),
            )
        validateWrite(updated)
        store.update(updated)
        invalidate(profileId)
        return updated
    }

    override suspend fun removeConstraint(
        profileId: String,
        constraintId: String,
    ) {
        store.markRemoved(constraintId, clock.nowIso())
        invalidate(profileId)
    }

    override suspend fun setContextualState(
        profileId: String,
        flag: String,
        value: String?,
        expiresAtIso: String?,
    ): Constraint {
        val now = clock.nowIso()
        val expiry = expiresAtIso ?: defaultExpiry(flag, now)
        val constraint =
            Constraint(
                id = "constraint-${UUID.randomUUID()}",
                profileId = profileId,
                type = ConstraintType.Contextual,
                severity = Severity.Preference,
                humanLabel = flag.replace('_', ' '),
                scope =
                    ConstraintScope(
                        temporal = TemporalScope(kind = "always"),
                        household = HouseholdScope(mode = "self"),
                        profile = ProfileScope(profileId = profileId),
                    ),
                payload = ContextualPayload(state = ContextualState(flag = flag, value = value)),
                provenance =
                    ProvenanceRecord(
                        source = "user_direct",
                        addedAt = now,
                        addedContext = AddedContext(flow = "free_text_update"),
                        confidence = "high_user_direct",
                    ),
                expiresAt = expiry,
            )
        store.insert(constraint)
        invalidate(profileId)
        return constraint
    }

    // ── Validation ───────────────────────────────────────────────────────

    override suspend fun validateSuggestion(
        profileId: String,
        suggestion: SuggestionInput,
        atTimeIso: String?,
    ): ValidationResult {
        val active = queryActive(profileId, atTimeIso)
        val relaxed = sessionRelaxations[profileId].orEmpty()
        val checkable =
            active.constraints.filterNot {
                // Session relaxations (§8 Step 4) — Inviolable is never relaxable,
                // enforced again here as the safety floor.
                it.id in relaxed && it.severity != Severity.Inviolable
            }
        return validator.validate(checkable, suggestion)
    }

    override suspend fun resolveConflict(
        profileId: String,
        conflictId: String,
        userChoice: ConflictChoice,
    ) {
        val all = store.loadLive(profileId).associateBy { it.id }
        userChoice.relaxedConstraintIds.forEach { id ->
            val constraint = all[id]
            if (constraint?.severity == Severity.Inviolable) {
                throw ConstraintWriteException(
                    "inviolable_relaxation",
                    "Inviolable constraints are never offered or accepted for relaxation " +
                        "(constraint-engine-spec.md §8).",
                )
            }
        }
        sessionRelaxations.getOrPut(profileId) { mutableSetOf() } += userChoice.relaxedConstraintIds
        invalidate(profileId)
    }

    /** Clears session relaxations — called at session end by the orchestrator. */
    fun clearSessionRelaxations(profileId: String) {
        sessionRelaxations.remove(profileId)
        invalidate(profileId)
    }

    // ── Internals ────────────────────────────────────────────────────────

    private suspend fun computeActiveSet(
        profileId: String,
        atIso: String,
    ): ActiveSet {
        val zone = ZoneId.of(profileTimezone(profileId))
        val at = ZonedDateTime.ofInstant(Instant.parse(atIso), zone)
        val live =
            store.loadLive(profileId).filter { constraint ->
                constraint.expiresAt == null || constraint.expiresAt > atIso
            }

        // Pass 1: contextual (Type-5) constraints define the active flags.
        // They are evaluated without contextual-scope dependencies themselves
        // (no flag-on-flag chains in v1 — kept acyclic by construction).
        val flagContext =
            ScopeEvaluator.Context(at = at, activeFlags = emptySet(), profileId = profileId)
        val activeFlags =
            live
                .filter { it.type == ConstraintType.Contextual }
                .filter { scopeEvaluator.evaluate(it.scope, flagContext).isActive }
                .mapNotNull { (it.payload as? ContextualPayload)?.state?.flag }
                .toSet()

        // Pass 2: full evaluation for every constraint, in spec dimension order.
        val context =
            ScopeEvaluator.Context(at = at, activeFlags = activeFlags, profileId = profileId)
        val entries =
            live.mapNotNull { constraint ->
                val evaluation = scopeEvaluator.evaluate(constraint.scope, context)
                if (!evaluation.isActive) return@mapNotNull null
                ActiveSetEntry(
                    constraint = constraint.copy(isActiveNow = true),
                    decisiveDimensions = decisiveDimensions(constraint.scope),
                )
            }
        return ActiveSet(atTimeIso = atIso, entries = entries)
    }

    private fun decisiveDimensions(scope: ConstraintScope): List<String> =
        buildList {
            if (scope.temporal.kind != "always") add("temporal")
            if (scope.contextual.requiredFlags.isNotEmpty() || scope.contextual.excludedFlags.isNotEmpty()) {
                add("contextual")
            }
            if (scope.location.country != null || scope.location.region != null) add("location")
        }

    /**
     * Write-time tier enforcement (`constraint-engine-spec.md` §4): the
     * disallowed combinations are rejected with structured errors, never
     * coerced.
     */
    private fun validateWrite(constraint: Constraint) {
        val payloadMatchesType =
            when (constraint.type) {
                ConstraintType.Avoid -> constraint.payload is AvoidPayload
                ConstraintType.Prefer -> constraint.payload is ai.cuizine.engine.types.PreferPayload
                ConstraintType.Require -> constraint.payload is RequirePayload
                ConstraintType.Limit -> constraint.payload is LimitPayload
                ConstraintType.Contextual -> constraint.payload is ContextualPayload
            }
        if (!payloadMatchesType) {
            throw ConstraintWriteException(
                "payload_type_mismatch",
                "Payload ${constraint.payload::class.simpleName} does not match type ${constraint.type}.",
            )
        }
        if (constraint.type == ConstraintType.Prefer && constraint.severity != Severity.Preference) {
            throw ConstraintWriteException(
                "prefer_must_be_preference",
                "prefer constraints can only carry the Preference tier (constraint-engine-spec.md §4).",
            )
        }
        if (constraint.type == ConstraintType.Contextual && constraint.severity == Severity.Inviolable) {
            throw ConstraintWriteException(
                "contextual_never_inviolable",
                "contextual constraints cannot be Inviolable (constraint-engine-spec.md §4).",
            )
        }
        val payload = constraint.payload
        if (payload is LimitPayload && payload.hardOrSoft == "soft" &&
            constraint.severity == Severity.Inviolable
        ) {
            throw ConstraintWriteException(
                "soft_limit_never_inviolable",
                "A soft limit cannot be Inviolable (constraint-engine-spec.md §4).",
            )
        }
        if (constraint.scope.profile.profileId
                .isBlank()
        ) {
            throw ConstraintWriteException("scope_malformed", "Profile scope must name its profile.")
        }
    }

    private fun defaultExpiry(
        flag: String,
        nowIso: String,
    ): String {
        val now = Instant.parse(nowIso)
        // §3 Type 5 defaults: ~7 days for travel-like states, end-of-day for
        // today-only states.
        val days = if ("travel" in flag || "trip" in flag) 7L else 1L
        return now.plusSeconds(days * 24 * 3600).toString()
    }

    private fun invalidate(profileId: String) {
        activeSetCache.remove(profileId)
    }

    private fun minuteOf(iso: String): String = iso.take(16)

    private data class CachedActiveSet(
        val atMinute: String,
        val activeSet: ActiveSet,
    )
}
