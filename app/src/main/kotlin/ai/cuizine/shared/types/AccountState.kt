package ai.cuizine.shared.types

/**
 * Account/sync state for the Settings surfaces (`ui-ux-spec.md` §5.5).
 * Signed-out is first-class and fully functional (ADR 0011).
 */
sealed interface AccountState {
    data object SignedOut : AccountState

    data class SignedIn(
        val email: String,
        val displayName: String?,
        val lastSyncAt: String?,
        val isSyncEnabled: Boolean,
    ) : AccountState
}

/**
 * The six-word recovery passphrase as revealed once at sign-in
 * (`local-first-sync.md` §4). Cuizine cannot recover it — no master key,
 * no admin override; the reveal surface says so honestly.
 */
data class RecoveryPassphrase(
    val words: List<String>,
) {
    init {
        require(words.size == 6) { "Recovery passphrase is exactly six words." }
    }
}

/** Tier surface state — v1 alpha shows full features via bypass, no billing UI (`monetization-and-billing.md` §9). */
data class TierState(
    val isAlphaBypassActive: Boolean,
    val tierDisplayName: String,
)
