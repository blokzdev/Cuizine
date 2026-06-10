package ai.cuizine.data.repository

import ai.cuizine.data.database.daos.ConstraintDao
import ai.cuizine.engine.ports.ConstraintStore
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.shared.types.Constraint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Room-backed implementation of the engine's persistence port
 * (`data-model.md` §4 physical ↔ `constraint-engine-spec.md` logical).
 */
@Singleton
class RoomConstraintStore
    @Inject
    constructor(
        private val constraintDao: ConstraintDao,
        private val clock: EngineClock,
    ) : ConstraintStore {
        override suspend fun loadLive(profileId: String): List<Constraint> =
            constraintDao.getLiveForProfile(profileId).map(ConstraintMapping::toDomain)

        override suspend fun loadAll(profileId: String): List<Constraint> =
            constraintDao.getAllForProfile(profileId).map(ConstraintMapping::toDomain)

        override suspend fun loadById(constraintId: String): Constraint? =
            constraintDao.getById(constraintId)?.let(ConstraintMapping::toDomain)

        override suspend fun insert(constraint: Constraint) {
            constraintDao.insert(ConstraintMapping.toEntity(constraint, createdAtIso = clock.nowIso()))
        }

        override suspend fun update(constraint: Constraint) {
            val existing = constraintDao.getById(constraint.id)
            constraintDao.update(
                ConstraintMapping.toEntity(
                    constraint,
                    createdAtIso = existing?.createdAt ?: clock.nowIso(),
                    modifiedAtIso = clock.nowIso(),
                ),
            )
        }

        override suspend fun markRemoved(
            constraintId: String,
            removedAtIso: String,
        ) {
            constraintDao.markRemoved(constraintId, removedAtIso)
        }
    }
