package ai.cuizine.ui.state.settings

import ai.cuizine.data.repository.AccountRepository
import ai.cuizine.data.repository.ExportService
import ai.cuizine.data.repository.ProfileRepository
import ai.cuizine.shared.types.AccountState
import ai.cuizine.shared.types.RecoveryPassphrase
import ai.cuizine.shared.types.TierState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

/**
 * The Settings feature area (`ui-ux-spec.md` §5.5): account & sync (signed-out
 * is first-class), unconditional export, honest deletion, plain disclaimers,
 * and the minimal v1 alpha tier surface — no billing UI exists in v1.
 */
data class SettingsState(
    val accountState: AccountState = AccountState.SignedOut,
    val tier: TierState? = null,
    val isSigningIn: Boolean = false,
    val isExporting: Boolean = false,
    val revealedPassphrase: RecoveryPassphrase? = null,
    val isDeleteConfirmVisible: Boolean = false,
)

sealed interface SettingsSideEffect {
    /** Hand the export JSON to the platform share sheet (Flow F). */
    data class ShareExport(
        val exportJson: String,
    ) : SettingsSideEffect

    /** Account deleted — the app returns to first-run. */
    data object RestartToFirstRun : SettingsSideEffect
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
        private val profileRepository: ProfileRepository,
        private val exportService: ExportService,
    ) : ViewModel(),
        ContainerHost<SettingsState, SettingsSideEffect> {
        override val container: Container<SettingsState, SettingsSideEffect> =
            container(SettingsState())

        init {
            combine(
                accountRepository.observeAccountState(),
                accountRepository.observeTier(),
            ) { account, tier -> account to tier }
                .onEach { (account, tier) ->
                    intent { reduce { state.copy(accountState = account, tier = tier) } }
                }.launchIn(viewModelScope)
        }

        fun onSignInWithGoogle() =
            intent {
                reduce { state.copy(isSigningIn = true) }
                val passphrase = accountRepository.signInWithGoogle()
                reduce { state.copy(isSigningIn = false, revealedPassphrase = passphrase) }
            }

        fun onPassphraseConfirmedSaved() = intent { reduce { state.copy(revealedPassphrase = null) } }

        fun onSignOut() = intent { accountRepository.signOut() }

        fun onTriggerSync() = intent { accountRepository.triggerSync() }

        fun onExport() =
            intent {
                reduce { state.copy(isExporting = true) }
                val json = exportService.buildExport()
                reduce { state.copy(isExporting = false) }
                postSideEffect(SettingsSideEffect.ShareExport(json))
            }

        /** Alpha feedback channel (`data-model.md` §7): always user-initiated. */
        fun onShareFeedback() =
            intent {
                val json = exportService.buildFeedbackExport()
                postSideEffect(SettingsSideEffect.ShareExport(json))
            }

        fun onRequestDelete() = intent { reduce { state.copy(isDeleteConfirmVisible = true) } }

        fun onCancelDelete() = intent { reduce { state.copy(isDeleteConfirmVisible = false) } }

        fun onConfirmDelete() =
            intent {
                reduce { state.copy(isDeleteConfirmVisible = false) }
                accountRepository.deleteAccount()
                profileRepository.clearAllUserData()
                postSideEffect(SettingsSideEffect.RestartToFirstRun)
            }
    }
