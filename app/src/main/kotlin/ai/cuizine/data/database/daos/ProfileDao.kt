package ai.cuizine.data.database.daos

import ai.cuizine.data.database.entities.ProfileEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProfileDao {
    @Insert
    suspend fun insert(profile: ProfileEntity)

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    /** Active (non-removed) profiles; soft-deleted rows stay readable via [getById]. */
    @Query("SELECT * FROM profiles WHERE removed_at IS NULL")
    suspend fun getActive(): List<ProfileEntity>

    /** Reactive single-profile view (v1 is single-profile per account). */
    @Query("SELECT * FROM profiles WHERE removed_at IS NULL LIMIT 1")
    fun observeFirstActive(): kotlinx.coroutines.flow.Flow<ProfileEntity?>

    /** Hard delete — ONLY for the delete-account path (`data-model.md` §2). */
    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}
