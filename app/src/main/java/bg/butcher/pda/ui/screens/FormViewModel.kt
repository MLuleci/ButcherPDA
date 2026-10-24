package bg.butcher.pda.ui.screens

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import bg.butcher.pda.MainApplication
import bg.butcher.pda.R
import bg.butcher.pda.data.ErpRepository
import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.network.JobSlot
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.util.parseDecimal
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

sealed interface FormUiEffect {
    data class ShowToast(@StringRes val messageResId: Int): FormUiEffect
    data object GotoNextPage: FormUiEffect
}

enum class FetchBatchError {
    NETWORK_ERROR,
    REQUEST_FAILED,
    FORBIDDEN,
    INVALID_INPUT,
    NOT_FOUND,
}

data class FormUiState(
    // Select page:
    val quantityText: String = "",
    val explode: Boolean = false,
    val selectedItem: BomSearchResultItem? = null,
    val searchQuery: String = "",
    val searchState: NetworkState<List<BomSearchResultItem>, ErpError> = NetworkState.Idle,

    // Assembly page:
    // `fetchState` is cleared when `selectedItem`, `quantityText`, or `explode` are changed
    val fetchState: NetworkState<BomDetails, ErpError> = NetworkState.Idle,
    val items: Map<String, Map<String, Double>> = mapOf(),
    val expiryDaysText: String = "",
    val isFinished: Boolean = true,
    val submitState: NetworkState<ManufactureEntry, ErpError> = NetworkState.Idle,
) {
    val quantity: Double?
        get() = parseDecimal(quantityText)?.takeIf { it > 0.0 }

    val canContinue: Boolean
        get() = quantity != null && selectedItem != null

    val expiryDays: Int?
        get() = expiryDaysText.toIntOrNull()?.takeIf { it > 0 }
}

class FormViewModel(private val erpRepository: ErpRepository, private val state: SavedStateHandle): ViewModel() {
    private val _uiState = state.getMutableStateFlow("uiState", FormUiState())
    val uiState = _uiState.asStateFlow()
    private val bomSearchJob = JobSlot(viewModelScope)
    private val bomFetchJob = JobSlot(viewModelScope)
    private val _effects = Channel<FormUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun searchForBom(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchState = NetworkState.Idle) }
            bomSearchJob.cancel()
        } else {
            bomSearchJob.replace(300.milliseconds) {
                _uiState.update { it.copy(searchState = NetworkState.Loading) }
                erpRepository.searchBom(query)
                    .onSuccess { results -> _uiState.update { it.copy(searchState = NetworkState.Success(results)) } }
                    .onFailure { e ->
                        Log.e("AssemblyViewModel", "searchForBom", e)
                        _uiState.update { it.copy(searchState = NetworkState.Error(ErpError.from(e))) }
                    }
            }
        }
    }

    fun fetchBomDetails() {
        viewModelScope.launch {
            val state = _uiState.value
            val quantity = state.quantity
            val selectedItem = state.selectedItem

            if (quantity == null || selectedItem == null) {
                _effects.send(FormUiEffect.ShowToast(R.string.incomplete_message))
                return@launch
            }

            bomFetchJob.replace {
                _uiState.update { it.copy(fetchState = NetworkState.Loading) }
                erpRepository.getBomDetails(state.selectedItem.name, quantity, state.explode)
                    .onSuccess { results ->
                        _uiState.update {
                            it.copy(
                                fetchState = NetworkState.Success(
                                    results
                                )
                            )
                        }
                        _effects.send(FormUiEffect.GotoNextPage)
                    }
                    .onFailure { e ->
                        Log.e("AssemblyViewModel", "fetchBomDetails", e)
                        _uiState.update { it.copy(fetchState = NetworkState.Error(ErpError.from(e))) }
                        _effects.send(FormUiEffect.ShowToast(R.string.error_message))
                    }
            }
        }
    }

    suspend fun fetchBatchDetails(batchNumber: String): NetworkState<BatchDetails, FetchBatchError> {
        val batchNumber = batchNumber.trim()
        if (batchNumber.isEmpty()) {
            return NetworkState.Error(FetchBatchError.INVALID_INPUT)
        }

        val result = erpRepository.getBatch(batchNumber)
        return result.fold(
            onSuccess = { item -> NetworkState.Success(item) },
            onFailure = { error ->
                val erpError = ErpError.from(error)
                NetworkState.Error(
                    when {
                        erpError is ErpError.Network -> FetchBatchError.NETWORK_ERROR
                        erpError is ErpError.Forbidden -> FetchBatchError.FORBIDDEN
                        erpError is ErpError.Http && erpError.isNotFound -> FetchBatchError.NOT_FOUND
                        else -> FetchBatchError.REQUEST_FAILED
                    }
                )
            }
        )
    }

    fun setSelectedBom(bom: BomSearchResultItem) {
        _uiState.update { it.copy(selectedItem = bom, fetchState = NetworkState.Idle) }
    }

    fun setQuantity(quantity: String) {
        _uiState.update { it.copy(quantityText = quantity, fetchState = NetworkState.Idle) }
    }

    fun setDaysUntilExpiry(days: String) {
        _uiState.update { it.copy(expiryDaysText = days) }
    }

    fun setExplode(explode: Boolean) {
        _uiState.update { it.copy(explode = explode, fetchState = NetworkState.Idle) }
    }

    fun setIsFinished(isFinished: Boolean) {
        _uiState.update { it.copy(isFinished = isFinished) }
    }

    fun upsertBatch(itemCode: String, batchNumber: String, quantity: Double) {
        require(itemCode.isNotEmpty()) { "Item code cannot be empty" }
        require(batchNumber.isNotEmpty()) { "Batch number cannot be empty" }
        require(quantity > 0.0) { "Quantity cannot be zero or negative, was $quantity" }
        _uiState.update { currentState ->
            val items = currentState.items.toMutableMap()
            val batches = items[itemCode]?.toMutableMap() ?: mutableMapOf()
            batches[batchNumber] = quantity
            items[itemCode] = batches
            currentState.copy(items = items)
        }
    }

    fun removeBatch(itemCode: String, batchNumber: String) {
        require(itemCode.isNotEmpty()) { "Item code cannot be empty" }
        _uiState.update { currentState ->
            val items = currentState.items.toMutableMap()
            val batches = items[itemCode]?.toMutableMap()
            if (batches != null && batches.containsKey(batchNumber)) {
                batches.remove(batchNumber)
                if (batches.isEmpty()) {
                    items.remove(itemCode)
                } else {
                    items[itemCode] = batches
                }
            }
            currentState.copy(items = items)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val savedStateHandle = createSavedStateHandle()
                val application = (this[APPLICATION_KEY] as MainApplication)
                FormViewModel(erpRepository = application.container.erpRepository, state = savedStateHandle)
            }
        }
    }
}