package ai.cuizine.data.database.daos

import ai.cuizine.data.database.entities.EventLogEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EventLogDao {
    @Insert
    suspend fun insert(event: EventLogEntity)

    /** Hot path 4 (`data-model.md` §10): inserts must stay cheap; reads are rare. */
    @Query("SELECT * FROM event_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<EventLogEntity>

    @Query("SELECT COUNT(*) FROM event_log WHERE event_severity = :severity")
    suspend fun countBySeverity(severity: String): Int
}
