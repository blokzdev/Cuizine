package ai.cuizine.data.mock

import ai.cuizine.data.repository.AccountRepository
import ai.cuizine.data.repository.ExportService
import ai.cuizine.data.repository.PantryRepository
import ai.cuizine.data.repository.ProfileRepository
import ai.cuizine.data.repository.SuggestionRepository
import ai.cuizine.engine.types.Severity
import ai.cuizine.shared.fixtures.SukhiFixtures
import ai.cuizine.shared.types.AccountState
import ai.cuizine.shared.types.Constraint
import ai.cuizine.shared.types.CookingFor
import ai.cuizine.shared.types.CulturalContext
import ai.cuizine.shared.types.MealSuggestion
import ai.cuizine.shared.types.PantryEntry
import ai.cuizine.shared.types.ProfileOverview
import ai.cuizine.shared.types.RecoveryPassphrase
import ai.cuizine.shared.types.RejectionReason
import ai.cuizine.shared.types.TierState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 mock repositories (`ui-ux-spec.md` §10): deterministic, in-memory,
 * seeded with the canonical Sukhi fixtures so every Flow A–F is walkable with
 * zero LLM calls. Replaced binding-by-binding as Phases 3–6 land
 * (DECISION-LOG.md #2b).
 */
@Singleton
class MockProfileRepository
    @Inject
    constructor() : ProfileRepository {
        // Starts at first-run (null) so Flow A is the real day-0 experience;
        // the Welcome screen's skip-to-app loads the full Sukhi fixture.
        private val profileFlow = MutableStateFlow<ProfileOverview?>(null)
        private val constraintsFlow = MutableStateFlow(SukhiFixtures.constraints)

        override fun observeProfile(): Flow<ProfileOverview?> = profileFlow

        override fun observeConstraints(): Flow<List<Constraint>> = constraintsFlow

        override suspend fun completeOnboarding(
            displayName: String,
            culturalContext: CulturalContext,
            cookingFor: CookingFor,
            initialConstraints: List<Constraint>,
        ) {
            profileFlow.value =
                SukhiFixtures.profile.copy(
                    displayName = displayName,
                    culturalContext = culturalContext,
                    cookingFor = cookingFor,
                )
            constraintsFlow.value = initialConstraints
        }

        override suspend fun updateConstraintSeverity(
            constraintId: String,
            severity: Severity,
        ) {
            constraintsFlow.update { constraints ->
                constraints.map { if (it.id == constraintId) it.copy(severity = severity) else it }
            }
        }

        override suspend fun removeConstraint(constraintId: String) {
            constraintsFlow.update { constraints -> constraints.filterNot { it.id == constraintId } }
        }

        override suspend fun clearAllUserData() {
            resetForOnboarding()
        }

        fun resetForOnboarding() {
            profileFlow.value = null
            constraintsFlow.value = emptyList()
        }

        fun addConstraint(constraint: Constraint) {
            constraintsFlow.update { it + constraint }
        }
    }

@Singleton
class MockSuggestionRepository
    @Inject
    constructor() : SuggestionRepository {
        private val current = MutableStateFlow<MealSuggestion?>(null)
        private val generating = MutableStateFlow(false)
        private val history = MutableStateFlow(SukhiFixtures.savedSuggestions)
        private var cycleIndex = 0

        override fun observeCurrentSuggestion(): Flow<MealSuggestion?> = current

        override fun observeIsGenerating(): StateFlow<Boolean> = generating

        override fun observeHistory(): Flow<List<MealSuggestion>> = history

        override suspend fun requestSuggestion() {
            serveNextFromCycle()
        }

        override suspend fun acceptSuggestion(suggestionId: String) {
            val accepted = current.value ?: return
            if (accepted.id == suggestionId) {
                history.update { listOf(accepted) + it.filterNot { saved -> saved.id == suggestionId } }
            }
        }

        override suspend fun rejectSuggestion(
            suggestionId: String,
            reason: RejectionReason,
            freeText: String?,
        ) {
            // The next suggestion visibly improves on the rejected dimension
            // (PRD §7): the fixture cycle is authored so "don't have the
            // ingredients" after masoor dal lands on pantry-only aloo methi.
            if (reason == RejectionReason.MissingIngredients) {
                serve(SukhiFixtures.alooMethi)
            } else {
                serveNextFromCycle()
            }
        }

        override suspend fun regenerate() {
            serveNextFromCycle()
        }

        override suspend fun chooseConflictResolution(optionId: String) {
            generating.value = true
            delay(THINKING_MILLIS)
            generating.value = false
            current.value =
                when (optionId) {
                    "fixture-conflict-opt-no-tadka" -> SukhiFixtures.rajmaBrownRice
                    else -> SukhiFixtures.khichdi
                }
        }

        private suspend fun serveNextFromCycle() {
            val next = SukhiFixtures.suggestionCycle[cycleIndex % SukhiFixtures.suggestionCycle.size]
            cycleIndex += 1
            serve(next)
        }

        private suspend fun serve(suggestion: MealSuggestion) {
            generating.value = true
            delay(THINKING_MILLIS)
            generating.value = false
            current.value = suggestion
        }

        private companion object {
            // Long enough that the ThinkingIndicator is visibly exercised.
            const val THINKING_MILLIS = 1200L
        }
    }

@Singleton
class MockPantryRepository
    @Inject
    constructor() : PantryRepository {
        private val entries = MutableStateFlow(SukhiFixtures.pantry)

        override fun observeEntries(): Flow<List<PantryEntry>> = entries

        override suspend fun addEntry(
            name: String,
            quantityValue: Double?,
            quantityUnit: String?,
        ) {
            entries.update {
                it +
                    PantryEntry(
                        id = "mock-pantry-${UUID.randomUUID()}",
                        name = name,
                        quantityValue = quantityValue,
                        quantityUnit = quantityUnit,
                        group = "Recently added",
                    )
            }
        }

        override suspend fun updateEntry(entry: PantryEntry) {
            entries.update { list -> list.map { if (it.id == entry.id) entry else it } }
        }

        override suspend fun removeEntry(entryId: String) {
            entries.update { list -> list.filterNot { it.id == entryId } }
        }
    }

@Singleton
class MockAccountRepository
    @Inject
    constructor() : AccountRepository {
        private val accountState = MutableStateFlow<AccountState>(AccountState.SignedOut)
        private val tier =
            MutableStateFlow(
                TierState(isAlphaBypassActive = true, tierDisplayName = "Alpha — all features open"),
            )

        override fun observeAccountState(): Flow<AccountState> = accountState

        override fun observeTier(): Flow<TierState> = tier

        override suspend fun signInWithGoogle(): RecoveryPassphrase {
            delay(600)
            accountState.value =
                AccountState.SignedIn(
                    email = "sukhi@example.ca",
                    displayName = "Sukhi",
                    lastSyncAt = null,
                    isSyncEnabled = true,
                )
            return SukhiFixtures.recoveryPassphrase
        }

        override suspend fun signOut() {
            accountState.value = AccountState.SignedOut
        }

        override suspend fun triggerSync() {
            val signedIn = accountState.value as? AccountState.SignedIn ?: return
            delay(800)
            accountState.value = signedIn.copy(lastSyncAt = "just now")
        }

        override suspend fun deleteAccount() {
            accountState.value = AccountState.SignedOut
        }
    }

@Singleton
class MockExportService
    @Inject
    constructor() : ExportService {
        override suspend fun buildExport(): String {
            delay(400)
            // Honest placeholder shape; the real exporter serializes the full
            // store (Phase 6/7). Mock keeps the share-sheet flow walkable.
            return """{"cuizine_export":"mock","profile":"${SukhiFixtures.PROFILE_ID}"}"""
        }
    }
