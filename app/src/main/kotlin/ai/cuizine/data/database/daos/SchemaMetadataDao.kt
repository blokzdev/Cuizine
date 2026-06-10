package ai.cuizine.data.database.daos

import ai.cuizine.data.database.entities.SchemaMetadataEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SchemaMetadataDao {
    @Query("SELECT * FROM schema_metadata WHERE id = 1")
    suspend fun get(): SchemaMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(metadata: SchemaMetadataEntity)
}
