package ai.cuizine.core.di

import ai.cuizine.agents.mock.MockConversationService
import ai.cuizine.data.mock.MockAccountRepository
import ai.cuizine.data.mock.MockExportService
import ai.cuizine.data.mock.MockPantryRepository
import ai.cuizine.data.mock.MockProfileRepository
import ai.cuizine.data.mock.MockSuggestionRepository
import ai.cuizine.data.repository.AccountRepository
import ai.cuizine.data.repository.ExportService
import ai.cuizine.data.repository.PantryRepository
import ai.cuizine.data.repository.ProfileRepository
import ai.cuizine.data.repository.SuggestionRepository
import ai.cuizine.shared.types.ConversationService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Phase 2 mock bindings (`ui-ux-spec.md` §10; DECISION-LOG.md #2b). Each later
 * phase replaces its own bindings with real implementations — Phase 3 takes
 * [ProfileRepository]'s constraint paths, Phase 5 takes [ConversationService]
 * and [SuggestionRepository], Phase 6 takes [AccountRepository]/[ExportService].
 * No binding here survives to the alpha build (Phase 7 exit criterion).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MockBindingsModule {
    // ProfileRepository is REAL since Phase 3 (EngineProfileRepository over
    // the constraint engine + Room) — the first mock binding replaced, with
    // screens untouched (the State/Intents seam held).
    @Binds
    abstract fun bindProfileRepository(impl: ai.cuizine.data.repository.EngineProfileRepository): ProfileRepository

    // Phase 3: the Chef is still scripted, but every served meal passes
    // through the REAL validator and real conflict planning.
    @Binds
    abstract fun bindSuggestionRepository(
        impl: ai.cuizine.data.mock.ValidatedMockSuggestionRepository,
    ): SuggestionRepository

    @Binds
    abstract fun bindPantryRepository(impl: MockPantryRepository): PantryRepository

    @Binds
    abstract fun bindAccountRepository(impl: MockAccountRepository): AccountRepository

    @Binds
    abstract fun bindExportService(impl: MockExportService): ExportService

    @Binds
    abstract fun bindConversationService(impl: MockConversationService): ConversationService
}
