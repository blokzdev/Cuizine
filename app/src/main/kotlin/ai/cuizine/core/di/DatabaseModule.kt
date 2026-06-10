package ai.cuizine.core.di

import ai.cuizine.BuildConfig
import ai.cuizine.core.config.CuizineConfig
import ai.cuizine.data.database.CuizineDatabase
import ai.cuizine.data.database.daos.ConstraintDao
import ai.cuizine.data.database.daos.ProfileDao
import ai.cuizine.data.database.daos.SchemaMetadataDao
import ai.cuizine.data.database.entities.SchemaMetadataEntity
import ai.cuizine.data.database.migrations.ConstraintGraphMigrator
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CuizineDatabase =
        Room
            .databaseBuilder(context, CuizineDatabase::class.java, "cuizine.db")
            .addCallback(SchemaMetadataSeedCallback)
            .build()

    @Provides
    fun provideSchemaMetadataDao(db: CuizineDatabase): SchemaMetadataDao = db.schemaMetadataDao()

    @Provides
    fun provideProfileDao(db: CuizineDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideConstraintDao(db: CuizineDatabase): ConstraintDao = db.constraintDao()

    @Provides
    fun provideFoodDataCacheDao(db: CuizineDatabase): ai.cuizine.data.database.daos.FoodDataCacheDao =
        db.foodDataCacheDao()

    @Provides
    @Singleton
    fun provideMigrator(): ConstraintGraphMigrator = ConstraintGraphMigrator()

    @Provides
    @Singleton
    fun provideConfig(): CuizineConfig =
        CuizineConfig(
            isMockDataIndicatorEnabled = BuildConfig.DEBUG,
        )
}

/**
 * Seeds the single `schema_metadata` row with `current_version = 1` on first
 * launch (`data-model.md` §8) and guarantees SQLite foreign-key enforcement
 * (the DDL's REFERENCES clauses are load-bearing — `testing-strategy.md` §9).
 */
object SchemaMetadataSeedCallback : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT OR IGNORE INTO schema_metadata (id, current_version, initialized_at) VALUES (?, ?, ?)",
            arrayOf<Any?>(
                SchemaMetadataEntity.SINGLETON_ID,
                CuizineConfig.CURRENT_CONSTRAINT_GRAPH_SCHEMA_VERSION,
                Instant.now().toString(),
            ),
        )
    }
}
