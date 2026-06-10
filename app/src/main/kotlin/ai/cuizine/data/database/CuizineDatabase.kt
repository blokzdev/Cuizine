package ai.cuizine.data.database

import ai.cuizine.data.database.daos.ConstraintDao
import ai.cuizine.data.database.daos.ProfileDao
import ai.cuizine.data.database.daos.SchemaMetadataDao
import ai.cuizine.data.database.entities.AccountEntity
import ai.cuizine.data.database.entities.ConstraintEntity
import ai.cuizine.data.database.entities.CookedMealEntity
import ai.cuizine.data.database.entities.EventLogEntity
import ai.cuizine.data.database.entities.FoodDataCacheEntity
import ai.cuizine.data.database.entities.PantryItemEntity
import ai.cuizine.data.database.entities.ProfileEntity
import ai.cuizine.data.database.entities.SchemaMetadataEntity
import ai.cuizine.data.database.entities.SuggestionEntity
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The single local source of truth (`data-model.md`; ADR 0016). Nine tables,
 * schema-versioned from day one; the constraint-graph schema version (managed
 * by [ai.cuizine.data.database.migrations.ConstraintGraphMigrator] via
 * `schema_metadata`) is deliberately distinct from Room's own database
 * version below.
 */
@Database(
    entities = [
        AccountEntity::class,
        ProfileEntity::class,
        ConstraintEntity::class,
        FoodDataCacheEntity::class,
        SuggestionEntity::class,
        CookedMealEntity::class,
        PantryItemEntity::class,
        EventLogEntity::class,
        SchemaMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CuizineDatabase : RoomDatabase() {
    abstract fun schemaMetadataDao(): SchemaMetadataDao

    abstract fun profileDao(): ProfileDao

    abstract fun constraintDao(): ConstraintDao

    abstract fun foodDataCacheDao(): ai.cuizine.data.database.daos.FoodDataCacheDao
}
