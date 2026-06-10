package ai.cuizine.core.di

import ai.cuizine.BuildConfig
import ai.cuizine.data.database.daos.ProfileDao
import ai.cuizine.data.food.AiIngredientCategorizer
import ai.cuizine.data.food.ExternalFoodSources
import ai.cuizine.data.food.FakeExternalFoodSources
import ai.cuizine.data.food.FoodDataProvider
import ai.cuizine.data.food.NullAiIngredientCategorizer
import ai.cuizine.data.food.OffSearchApi
import ai.cuizine.data.food.RealExternalFoodSources
import ai.cuizine.data.food.UsdaApi
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
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
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

    /**
     * Absence-driven Layer 3 (CLAUDE.md §9): `cuizine.usda.api.key` present
     * in local.properties → live USDA+OFF clients; absent → recorded
     * fixtures. The network stack is only constructed in the live branch.
     */
    @Provides
    @Singleton
    fun provideExternalFoodSources(
        fake: FakeExternalFoodSources,
        clock: EngineClock,
    ): ExternalFoodSources {
        val usdaKey = BuildConfig.CUIZINE_USDA_API_KEY
        if (usdaKey.isBlank()) return fake

        val json = Json { ignoreUnknownKeys = true }
        val contentType = "application/json".toMediaType()
        val userAgent =
            "Cuizine/${BuildConfig.VERSION_NAME} (${BuildConfig.CUIZINE_OFF_CONTACT.ifBlank { "alpha build" }})"
        val baseClient =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header("User-Agent", userAgent)
                            .build(),
                    )
                }.build()
        // The USDA key travels as a header, never in URLs/logs (DECISION-LOG #4a).
        val usdaClient =
            baseClient
                .newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header(UsdaApi.API_KEY_HEADER, usdaKey)
                            .build(),
                    )
                }.build()
        val usdaApi =
            Retrofit
                .Builder()
                .baseUrl(UsdaApi.BASE_URL)
                .client(usdaClient)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(UsdaApi::class.java)
        val offApi =
            Retrofit
                .Builder()
                .baseUrl(OffSearchApi.BASE_URL)
                .client(baseClient)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(OffSearchApi::class.java)
        return RealExternalFoodSources(usdaApi, offApi, clock)
    }

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
