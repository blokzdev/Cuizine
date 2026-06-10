package ai.cuizine.core.di

import ai.cuizine.data.mock.MockAccountRepository
import ai.cuizine.data.mock.MockExportService
import ai.cuizine.data.mock.MockPantryRepository
import ai.cuizine.data.repository.AccountRepository
import ai.cuizine.data.repository.ExportService
import ai.cuizine.data.repository.PantryRepository
import ai.cuizine.data.repository.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Remaining mock bindings (`ui-ux-spec.md` §10; DECISION-LOG.md #2b). The
 * replacement ledger: ProfileRepository → real in Phase 3;
 * SuggestionRepository + ConversationService → real in Phase 5
 * (AgentsModule); AccountRepository + ExportService → Phase 6;
 * PantryRepository's persistence → Phase 6/7. No binding here survives to
 * the alpha build (Phase 7 exit criterion).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MockBindingsModule {
    // ProfileRepository is REAL since Phase 3 (engine + Room).
    @Binds
    abstract fun bindProfileRepository(impl: ai.cuizine.data.repository.EngineProfileRepository): ProfileRepository

    @Binds
    abstract fun bindPantryRepository(impl: MockPantryRepository): PantryRepository

    @Binds
    abstract fun bindAccountRepository(impl: MockAccountRepository): AccountRepository

    @Binds
    abstract fun bindExportService(impl: MockExportService): ExportService
}
