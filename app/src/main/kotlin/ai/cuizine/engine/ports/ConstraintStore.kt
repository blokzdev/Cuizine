package ai.cuizine.engine.ports

import ai.cuizine.shared.types.Constraint

/**
 * The engine's persistence port. The engine is pure Kotlin
 * (`build-conventions.md` §3); the data layer implements this over Room.
 * Soft delete only — `removeConstraint` is a `removed_at` mark, never a hard
 * delete (`constraint-engine-spec.md` §6).
 */
interface ConstraintStore {
    /** All live (non-removed, non-expired-checked-elsewhere) constraints. */
    suspend fun loadLive(profileId: String): List<Constraint>

    /** Full graph including soft-removed rows (queryAll / export). */
    suspend fun loadAll(profileId: String): List<Constraint>

    suspend fun loadById(constraintId: String): Constraint?

    suspend fun insert(constraint: Constraint)

    suspend fun update(constraint: Constraint)

    suspend fun markRemoved(
        constraintId: String,
        removedAtIso: String,
    )
}

/** Engine clock port — parameterizable time for testing and replay (`constraint-engine-spec.md` §7). */
fun interface EngineClock {
    fun nowIso(): String
}
