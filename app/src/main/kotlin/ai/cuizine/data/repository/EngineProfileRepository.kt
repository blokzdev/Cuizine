package ai.cuizine.data.repository

import ai.cuizine.data.database.CuizineDatabase
import ai.cuizine.data.database.daos.ConstraintDao
import ai.cuizine.data.database.daos.ProfileDao
import ai.cuizine.data.database.entities.ProfileEntity
import ai.cuizine.engine.ConstraintChanges
import ai.cuizine.engine.ConstraintGraphApi
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.engine.types.Severity
import ai.cuizine.shared.types.Constraint
import ai.cuizine.shared.types.CookingFor
import ai.cuizine.shared.types.CulturalContext
import ai.cuizine.shared.types.ProfileOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real (Phase 3) ProfileRepository: profile rows in Room, every
 * constraint path through the eleven-operation engine API — never the DAO
 * directly (`constraint-engine-spec.md` §6: no other write path exists).
 * Exposes the exact same contract the Phase 2 mock did (DECISION-LOG #2b);
 * the Profile screens did not change when this binding landed.
 */
@Singleton
class EngineProfileRepository
    @Inject
    constructor(
        private val engine: ConstraintGraphApi,
        private val profileDao: ProfileDao,
        private val constraintDao: ConstraintDao,
        private val database: CuizineDatabase,
        private val clock: EngineClock,
    ) : ProfileRepository {
        private val json = ConstraintMapping.json

        override fun observeProfile(): Flow<ProfileOverview?> =
            profileDao.observeFirstActive().map { entity -> entity?.toOverview() }

        override fun observeConstraints(): Flow<List<Constraint>> =
            constraintDao.observeLiveForProfile(PRIMARY_PROFILE_ID).map { entities ->
                // Active-state annotation comes from the engine so the Profile
                // surface reflects real scope evaluation, not just liveness.
                val activeIds =
                    engine
                        .queryActive(PRIMARY_PROFILE_ID)
                        .constraints
                        .map { it.id }
                        .toSet()
                entities
                    .map(ConstraintMapping::toDomain)
                    .map { it.copy(isActiveNow = it.id in activeIds) }
            }

        override suspend fun completeOnboarding(
            displayName: String,
            culturalContext: CulturalContext,
            cookingFor: CookingFor,
            initialConstraints: List<Constraint>,
        ) {
            val now = clock.nowIso()
            val existing = profileDao.getById(PRIMARY_PROFILE_ID)
            val entity =
                ProfileEntity(
                    id = PRIMARY_PROFILE_ID,
                    displayName = displayName,
                    relationshipType = "self",
                    culturalContextJson = json.encodeToString(culturalContext),
                    cookingForJson = json.encodeToString(cookingFor),
                    timezone = DEFAULT_TIMEZONE,
                    createdAt = existing?.createdAt ?: now,
                    modifiedAt = now,
                )
            if (existing == null) profileDao.insert(entity) else profileDao.update(entity)
            initialConstraints.forEach { constraint ->
                engine.addConstraint(
                    PRIMARY_PROFILE_ID,
                    constraint.copy(
                        id = "constraint-${UUID.randomUUID()}",
                        profileId = PRIMARY_PROFILE_ID,
                        scope =
                            constraint.scope.copy(
                                profile = constraint.scope.profile.copy(profileId = PRIMARY_PROFILE_ID),
                            ),
                    ),
                )
            }
        }

        override suspend fun updateConstraintSeverity(
            constraintId: String,
            severity: Severity,
        ) {
            engine.updateConstraint(
                PRIMARY_PROFILE_ID,
                constraintId,
                ConstraintChanges(severity = severity, reason = "severity changed in Profile"),
            )
        }

        override suspend fun removeConstraint(constraintId: String) {
            engine.removeConstraint(PRIMARY_PROFILE_ID, constraintId)
        }

        override suspend fun clearAllUserData() {
            // The one hard-delete path (`data-model.md` §2): export-and-walk-away.
            database.clearAllTables()
        }

        private fun ProfileEntity.toOverview(): ProfileOverview =
            ProfileOverview(
                id = id,
                displayName = displayName,
                culturalContext =
                    culturalContextJson?.let { json.decodeFromString<CulturalContext>(it) }
                        ?: CulturalContext(),
                cookingFor =
                    cookingForJson?.let { json.decodeFromString<CookingFor>(it) }
                        ?: CookingFor(householdSize = 1),
                timezone = timezone,
            )

        companion object {
            /** v1 is single-profile; the synthetic local profile id (`data-model.md` §3). */
            const val PRIMARY_PROFILE_ID = "profile-primary"
            const val DEFAULT_TIMEZONE = "America/Toronto"
        }
    }
