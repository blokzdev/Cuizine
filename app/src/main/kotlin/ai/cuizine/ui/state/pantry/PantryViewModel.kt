package ai.cuizine.ui.state.pantry

import ai.cuizine.data.repository.PantryRepository
import ai.cuizine.shared.types.PantryEntry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

/**
 * The Pantry feature area (`ui-ux-spec.md` §5.3): optional manual entry,
 * grouped sensibly, never nagging — an empty pantry is a fine pantry (PRD §5).
 */
data class PantryState(
    val entriesByGroup: Map<String, List<PantryEntry>> = emptyMap(),
    val editingEntry: PantryEntry? = null,
    val isAddSheetVisible: Boolean = false,
)

sealed interface PantrySideEffect

@HiltViewModel
class PantryViewModel
    @Inject
    constructor(
        private val pantryRepository: PantryRepository,
    ) : ViewModel(),
        ContainerHost<PantryState, PantrySideEffect> {
        override val container: Container<PantryState, PantrySideEffect> =
            container(PantryState())

        init {
            pantryRepository
                .observeEntries()
                .onEach { entries ->
                    intent {
                        reduce {
                            state.copy(entriesByGroup = entries.groupBy { it.group })
                        }
                    }
                }.launchIn(viewModelScope)
        }

        fun onOpenAddSheet() = intent { reduce { state.copy(isAddSheetVisible = true, editingEntry = null) } }

        fun onEditEntry(entry: PantryEntry) =
            intent { reduce { state.copy(isAddSheetVisible = true, editingEntry = entry) } }

        fun onDismissSheet() = intent { reduce { state.copy(isAddSheetVisible = false, editingEntry = null) } }

        fun onSaveEntry(
            name: String,
            quantityValue: Double?,
            quantityUnit: String?,
        ) = intent {
            val editing = state.editingEntry
            reduce { state.copy(isAddSheetVisible = false, editingEntry = null) }
            if (editing == null) {
                pantryRepository.addEntry(name, quantityValue, quantityUnit)
            } else {
                pantryRepository.updateEntry(
                    editing.copy(name = name, quantityValue = quantityValue, quantityUnit = quantityUnit),
                )
            }
        }

        fun onRemoveEntry(entryId: String) = intent { pantryRepository.removeEntry(entryId) }
    }
