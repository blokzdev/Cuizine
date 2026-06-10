package ai.cuizine.data.repository

import ai.cuizine.engine.types.Severity
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
import kotlinx.coroutines.flow.Flow

/**
 * The collaborator seams behind every Orbit container (DECISION-LOG.md #2b).
 * Phase 2 binds mock implementations returning canonical fixtures; Phases 3–6
 * rebind to real implementations. The interfaces — like the screens' State and
 * Intents — never change at the swap.
 */
interface ProfileRepository {
    /** null until day-0 onboarding completes. */
    fun observeProfile(): Flow<ProfileOverview?>

    fun observeConstraints(): Flow<List<Constraint>>

    suspend fun completeOnboarding(
        displayName: String,
        culturalContext: CulturalContext,
        cookingFor: CookingFor,
        initialConstraints: List<Constraint>,
    )

    /** Lowering severity of a medical/inviolable constraint warns first (`ui-ux-spec.md` §5.4). */
    suspend fun updateConstraintSeverity(
        constraintId: String,
        severity: Severity,
    )

    /** Soft delete (`data-model.md` §2). */
    suspend fun removeConstraint(constraintId: String)

    /**
     * The delete-account path (`ui-ux-spec.md` §5.5 Flow F): clean local
     * wipe, back to first-run. The only hard delete in v1 (`data-model.md` §2).
     */
    suspend fun clearAllUserData()
}

interface SuggestionRepository {
    fun observeCurrentSuggestion(): Flow<MealSuggestion?>

    fun observeIsGenerating(): Flow<Boolean>

    fun observeHistory(): Flow<List<MealSuggestion>>

    suspend fun requestSuggestion()

    suspend fun acceptSuggestion(suggestionId: String)

    suspend fun rejectSuggestion(
        suggestionId: String,
        reason: RejectionReason,
        freeText: String? = null,
    )

    suspend fun regenerate()

    suspend fun chooseConflictResolution(optionId: String)
}

interface PantryRepository {
    fun observeEntries(): Flow<List<PantryEntry>>

    suspend fun addEntry(
        name: String,
        quantityValue: Double?,
        quantityUnit: String?,
    )

    suspend fun updateEntry(entry: PantryEntry)

    suspend fun removeEntry(entryId: String)
}

interface AccountRepository {
    fun observeAccountState(): Flow<AccountState>

    fun observeTier(): Flow<TierState>

    /** Returns the passphrase to reveal exactly once (`local-first-sync.md` §4). */
    suspend fun signInWithGoogle(): RecoveryPassphrase

    suspend fun signOut()

    suspend fun triggerSync()

    suspend fun deleteAccount()
}

/** Export-and-walk-away (`ui-ux-spec.md` §5.5; unconditional on every tier). */
interface ExportService {
    /** Builds the full user-data export as a JSON string handed to the share sheet. */
    suspend fun buildExport(): String
}
