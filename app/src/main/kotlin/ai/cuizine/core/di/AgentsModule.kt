package ai.cuizine.core.di

import ai.cuizine.agents.AgentInvoker
import ai.cuizine.agents.Orchestrator
import ai.cuizine.agents.OrchestratorConversationService
import ai.cuizine.agents.OrchestratorSuggestionRepository
import ai.cuizine.agents.PromptStore
import ai.cuizine.agents.providers.FakeModelProvider
import ai.cuizine.agents.providers.ModelProvider
import ai.cuizine.agents.providers.ProviderId
import ai.cuizine.agents.providers.ProviderRouter
import ai.cuizine.data.database.CuizineDatabase
import ai.cuizine.data.database.daos.EventLogDao
import ai.cuizine.data.repository.EngineProfileRepository
import ai.cuizine.data.repository.ProfileRepository
import ai.cuizine.data.repository.RoomAlphaEventLogger
import ai.cuizine.data.repository.SuggestionRepository
import ai.cuizine.engine.ConstraintGraphApi
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.shared.types.AlphaEventLogger
import ai.cuizine.shared.types.ConversationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Phase 5 wiring: the real orchestrator and agents behind the same UI
 * contracts the Phase 2 mocks implemented. Providers are absence-driven
 * (CLAUDE.md §9): with no LLM keys configured, every agent call routes to
 * the deterministic [FakeModelProvider]; real Anthropic/Google/OpenAI
 * adapters bind into the providers map as their keys appear.
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentsModule {
    @Provides
    @Singleton
    fun provideEventLogDao(db: CuizineDatabase): EventLogDao = db.eventLogDao()

    @Provides
    @Singleton
    fun provideAlphaEventLogger(impl: RoomAlphaEventLogger): AlphaEventLogger = impl

    @Provides
    @Singleton
    fun provideProviderRouter(
        fake: FakeModelProvider,
        events: AlphaEventLogger,
    ): ProviderRouter {
        // No keys yet → the providers map is empty and everything resolves to
        // the fake. Real adapters slot in here key-by-key (DECISION-LOG #5a).
        val providers = emptyMap<ProviderId, ModelProvider>()
        return ProviderRouter(providers = providers, fake = fake, events = events)
    }

    @Provides
    @Singleton
    fun provideAgentInvoker(
        router: ProviderRouter,
        promptStore: PromptStore,
        events: AlphaEventLogger,
    ): AgentInvoker = AgentInvoker(router, promptStore, events)

    @Provides
    @Singleton
    fun provideOrchestrator(
        engine: ConstraintGraphApi,
        invoker: AgentInvoker,
        events: AlphaEventLogger,
        clock: EngineClock,
    ): Orchestrator = Orchestrator(engine, invoker, events, clock)

    @Provides
    @Singleton
    fun provideConversationService(
        orchestrator: Orchestrator,
        profileRepository: ProfileRepository,
    ): ConversationService =
        OrchestratorConversationService(
            orchestrator = orchestrator,
            profileRepository = profileRepository,
            profileId = EngineProfileRepository.PRIMARY_PROFILE_ID,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )

    @Provides
    @Singleton
    fun provideSuggestionRepository(orchestrator: Orchestrator): SuggestionRepository =
        OrchestratorSuggestionRepository(
            orchestrator = orchestrator,
            profileId = EngineProfileRepository.PRIMARY_PROFILE_ID,
        )
}
