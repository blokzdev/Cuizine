package ai.cuizine.core.di

import ai.cuizine.data.database.daos.ProfileDao
import ai.cuizine.data.mock.StaticFoodDataPort
import ai.cuizine.data.repository.EngineProfileRepository
import ai.cuizine.data.repository.RoomConstraintStore
import ai.cuizine.engine.ConstraintGraphApi
import ai.cuizine.engine.ConstraintGraphEngine
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.engine.ports.FoodDataPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton

/**
 * Phase 3 wiring: the real constraint engine over Room. The Food Data port
 * stays a deterministic fake until Phase 4 (fake-first, CLAUDE.md §9).
 */
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {
    @Provides
    @Singleton
    fun provideEngineClock(): EngineClock = EngineClock { Instant.now().toString() }

    @Provides
    @Singleton
    fun provideFoodDataPort(port: StaticFoodDataPort): FoodDataPort = port

    @Provides
    @Singleton
    fun provideConstraintGraphApi(
        store: RoomConstraintStore,
        foodData: FoodDataPort,
        clock: EngineClock,
        profileDao: ProfileDao,
    ): ConstraintGraphApi =
        ConstraintGraphEngine(
            store = store,
            foodData = foodData,
            clock = clock,
            profileTimezone = { profileId ->
                profileDao.getById(profileId)?.timezone
                    ?: EngineProfileRepository.DEFAULT_TIMEZONE
            },
        )
}
