package ai.cuizine.data.database.daos

import ai.cuizine.data.database.entities.FoodDataCacheEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FoodDataCacheDao {
    /** The hot-path lookup (`data-model.md` §10): unique on (key, kind), TTL-aware. */
    @Query(
        "SELECT * FROM food_data_cache WHERE lookup_key = :lookupKey AND lookup_kind = :lookupKind " +
            "AND (ttl_expires_at IS NULL OR ttl_expires_at > :nowIso) LIMIT 1",
    )
    suspend fun lookup(
        lookupKey: String,
        lookupKind: String,
        nowIso: String,
    ): FoodDataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FoodDataCacheEntity)

    /** Evictable by design (`data-model.md` §2): expired rows are deletable. */
    @Query("DELETE FROM food_data_cache WHERE ttl_expires_at IS NOT NULL AND ttl_expires_at <= :nowIso")
    suspend fun evictExpired(nowIso: String)

    @Query("SELECT COUNT(*) FROM food_data_cache")
    suspend fun count(): Int
}
