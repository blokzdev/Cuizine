package ai.cuizine.core.di

import ai.cuizine.data.database.daos.ProfileDao
import ai.cuizine.data.food.AiIngredientCategorizer
import ai.cuizine.data.food.ExternalFoodSources
import ai.cuizine.data.food.FakeExternalFoodSources
import ai.cuizine.data.food.FoodDataProvider
import ai.cuizine.data.food.NullAiIngredientCategorizer
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

    // Phase 4: the real three-layer Food Data Provider (cache → bundle →
    // external → AI fallback). External sources and the AI categorizer are
    // absence-driven fakes until keys/config exist (CLAUDE.md §9).
    @Provides
    @Singleton
    fun provideFoodDataPort(provider: FoodDataProvider): FoodDataPort = provider

    @Provides
    @Singleton
    fun provideExternalFoodSources(fake: FakeExternalFoodSources): ExternalFoodSources = fake

    @Provides
    @Singleton
    fun provideAiCategorizer(fake: NullAiIngredientCategorizer): AiIngredientCategorizer = fake

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
