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

    /** Live rows (non-removed); expiry filtering happens in the engine. */
    @Query("SELECT * FROM constraints WHERE profile_id = :profileId AND removed_at IS NULL")
    suspend fun getLiveForProfile(profileId: String): List<ConstraintEntity>

    /** Full graph including soft-removed rows (queryAll / export). */
    @Query("SELECT * FROM constraints WHERE profile_id = :profileId")
    suspend fun getAllForProfile(profileId: String): List<ConstraintEntity>

    @Query("UPDATE constraints SET removed_at = :removedAtIso, modified_at = :removedAtIso WHERE id = :constraintId")
    suspend fun markRemoved(
        constraintId: String,
        removedAtIso: String,
    )

    /** Reactive view of live rows for the UI repositories. */
    @Query("SELECT * FROM constraints WHERE profile_id = :profileId AND removed_at IS NULL")
    fun observeLiveForProfile(profileId: String): kotlinx.coroutines.flow.Flow<List<ConstraintEntity>>

    /** Hard delete — ONLY for the delete-account path (`data-model.md` §2). */
    @Query("DELETE FROM constraints")
    suspend fun deleteAll()
}
