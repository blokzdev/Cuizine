package ai.cuizine.data.repository

import ai.cuizine.data.database.daos.EventLogDao
import ai.cuizine.data.database.entities.EventLogEntity
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.shared.types.AlphaEventLogger
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Local-only event log writes (`data-model.md` §7) — append-only, never uploaded. */
@Singleton
class RoomAlphaEventLogger
    @Inject
    constructor(
        private val eventLogDao: EventLogDao,
        private val clock: EngineClock,
    ) : AlphaEventLogger {
        override suspend fun log(
            eventType: String,
            severity: String,
            payloadJson: String,
            profileId: String?,
        ) {
            val now = clock.nowIso()
            eventLogDao.insert(
                EventLogEntity(
                    id = "event-${UUID.randomUUID()}",
                    profileId = profileId,
                    eventType = eventType,
                    eventSeverity = severity,
                    timestamp = now,
                    payloadJson = payloadJson,
                    createdAt = now,
                ),
            )
        }
    }
