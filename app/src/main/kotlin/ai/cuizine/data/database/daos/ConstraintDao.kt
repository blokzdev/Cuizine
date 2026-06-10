package ai.cuizine.data.database.daos

import ai.cuizine.data.database.entities.ConstraintEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ConstraintDao {
    @Insert
    suspend fun insert(constraint: ConstraintEntity)

    @Update
    suspend fun update(constraint: ConstraintEntity)

    @Query("SELECT * FROM constraints WHERE id = :id")
    suspend fun getById(id: String): ConstraintEntity?

    /**
     * The active-set hot-path load (`data-model.md` §10): indexed fetch of
     * live constraints; scope evaluation happens in Kotlin, never in SQL.
     */
    @Query(
        "SELECT * FROM constraints WHERE profile_id = :profileId AND removed_at IS NULL " +
            "AND (expires_at IS NULL OR expires_at > :nowIso)",
    )
    suspend fun getActiveForProfile(
        profileId: String,
        nowIso: String,
    ): List<ConstraintEntity>
}
