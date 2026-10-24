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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "FormViewModel"

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

enum class BatchChangeError {
    INVALID_INPUT,
    NOT_FOUND,
}

typealias BatchMap = Map<String, Map<String, Double>>

data class FormUiState(
    // Start page:
    val quantityText: String = "",
    val explode: Boolean = false,
    val selectedItem: BomSearchResultItem? = null,
    val searchState: NetworkState<List<BomSearchResultItem>, ErpError> = NetworkState.Idle,
    val expiryDaysText: String = "",
    val expiryTyped: Boolean = false,
    val isFinished: Boolean = true,

    // Form page:
    // `fetchState` and `items` are cleared when `selectedItem`, `quantityText` or `explode` change
    val fetchState: NetworkState<BomDetails, ErpError> = NetworkState.Idle,
    val items: BatchMap = mapOf(),
    val requestId: String = newRequestId(),
    val submitState: NetworkState<ManufactureEntry, ErpError> = NetworkState.Idle,
    val printState: NetworkState<Unit, Throwable> = NetworkState.Idle,
) {
    val quantity: Double?
        get() = parseDecimal(quantityText)?.takeIf { it > 0.0 }

    val canContinue: Boolean
        get() = quantity != null && selectedItem != null

    val expiryDays: Int?
        get() = expiryDaysText.toIntOrNull()?.takeIf { it > 0 }
}

fun newRequestId(): String = UUID.randomUUID().toString()

/** Keys for the inputs kept across process death. Server results are fetched again instead. */
private object Saved {
    const val QUANTITY = "quantity"
    const val EXPLODE = "explode"
    const val SELECTED_ITEM = "selectedItem"
    const val EXPIRY = "expiry"
    const val EXPIRY_TYPED = "expiryTyped"
    const val FINISHED = "finished"
    const val ITEMS = "items"
    const val REQUEST_ID = "requestId"
}

class FormViewModel(private val erpRepository: ErpRepository, private val saved: SavedStateHandle): ViewModel() {
    private val _uiState = MutableStateFlow(restore())
    val uiState = _uiState.asStateFlow()
    private val bomSearchJob = JobSlot(viewModelScope)
    private val bomFetchJob = JobSlot(viewModelScope)
    private val _effects = Channel<FormUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private fun restore(): FormUiState {
        val defaults = FormUiState()
        return FormUiState(
            quantityText = saved[Saved.QUANTITY] ?: defaults.quantityText,
            explode = saved[Saved.EXPLODE] ?: defaults.explode,
            selectedItem = saved.get<String>(Saved.SELECTED_ITEM)?.let { decodeOrNull<BomSearchResultItem>(it) },
            expiryDaysText = saved[Saved.EXPIRY] ?: defaults.expiryDaysText,
            expiryTyped = saved[Saved.EXPIRY_TYPED] ?: defaults.expiryTyped,
            isFinished = saved[Saved.FINISHED] ?: defaults.isFinished,
            items = saved.get<String>(Saved.ITEMS)?.let { decodeOrNull<BatchMap>(it) } ?: defaults.items,
            requestId = saved[Saved.REQUEST_ID] ?: defaults.requestId,
        )
    }

    private inline fun <reified T> decodeOrNull(text: String): T? =
        runCatching { Json.decodeFromString<T>(text) }.getOrNull()

    /** Applies [transform] and writes the inputs it may have changed to the saved state. */
    private fun updateInputs(transform: (FormUiState) -> FormUiState) {
        val state = _uiState.updateAndGet(transform)
        saved[Saved.QUANTITY] = state.quantityText
        saved[Saved.EXPLODE] = state.explode
        saved[Saved.SELECTED_ITEM] = state.selectedItem?.let { Json.encodeToString(it) }
        saved[Saved.EXPIRY] = state.expiryDaysText
        saved[Saved.EXPIRY_TYPED] = state.expiryTyped
        saved[Saved.FINISHED] = state.isFinished
        saved[Saved.ITEMS] = Json.encodeToString(state.items)
        saved[Saved.REQUEST_ID] = state.requestId
    }

    /** The BOM details and chosen batches belong to one BOM, quantity and explode setting. */
    private fun changeRun(transform: (FormUiState) -> FormUiState) {
        bomFetchJob.cancel()
        updateInputs {
            transform(it).copy(
                fetchState = NetworkState.Idle,
                items = emptyMap(),
                requestId = newRequestId(),
                submitState = NetworkState.Idle,
            )
        }
    }

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
                        Log.e(TAG, "searchForBom", e)
                        _uiState.update { it.copy(searchState = NetworkState.Error(ErpError.from(e))) }
                    }
            }
        }
    }

    fun fetchBomDetails() {
        val state = _uiState.value
        if (state.fetchState is NetworkState.Success) {
            // Every input the details depend on resets `fetchState`, so these are still current
            _effects.trySend(FormUiEffect.GotoNextPage)
            return
        }
        if (state.quantity == null || state.selectedItem == null) {
            _effects.trySend(FormUiEffect.ShowToast(R.string.incomplete_message))
            return
        }
        loadBomDetails(fromStart = true)
    }

    /** For the Form page after process death: the inputs were restored, but not the BOM details. */
    fun reloadBomDetails() {
        if (_uiState.value.fetchState is NetworkState.Success) return
        loadBomDetails(fromStart = false)
    }

    private fun loadBomDetails(fromStart: Boolean) {
        val state = _uiState.value
        val quantity = state.quantity
        val selectedItem = state.selectedItem
        if (state.fetchState is NetworkState.Loading || quantity == null || selectedItem == null) return

        _uiState.update { it.copy(fetchState = NetworkState.Loading) }
        bomFetchJob.replace {
            erpRepository.getBomDetails(selectedItem.name, quantity, state.explode)
                .onSuccess { results ->
                    _uiState.update { it.copy(fetchState = NetworkState.Success(results)) }
                    if (fromStart) _effects.send(FormUiEffect.GotoNextPage)
                }
                .onFailure { e ->
                    Log.e(TAG, "loadBomDetails", e)
                    _uiState.update { it.copy(fetchState = NetworkState.Error(ErpError.from(e))) }
                    if (fromStart) _effects.send(FormUiEffect.ShowToast(R.string.error_message))
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
        if (bom.name == _uiState.value.selectedItem?.name) return
        changeRun {
            it.copy(
                selectedItem = bom,
                // A new product gets its own default, even if the old one was typed over
                expiryDaysText = bom.shelfLife?.takeIf { days -> days > 0 }?.toString().orEmpty(),
                expiryTyped = false,
            )
        }
    }

    fun setQuantity(quantity: String) {
        val current = _uiState.value.quantity
        if (current != null && parseDecimal(quantity) == current) {
            // e.g. "10" -> "10.0": the same run
            updateInputs { it.copy(quantityText = quantity) }
        } else {
            changeRun { it.copy(quantityText = quantity) }
        }
    }

    fun setDaysUntilExpiry(days: String) {
        updateInputs { it.copy(expiryDaysText = days, expiryTyped = true) }
    }

    fun setExplode(explode: Boolean) {
        if (explode == _uiState.value.explode) return
        changeRun { it.copy(explode = explode) }
    }

    fun setIsFinished(isFinished: Boolean) {
        updateInputs { it.copy(isFinished = isFinished) }
    }

    fun upsertBatch(itemCode: String, batchNumber: String, quantity: Double): BatchChangeError? {
        if (itemCode.isEmpty() || batchNumber.isEmpty() || !quantity.isFinite() || quantity <= 0.0) {
            return BatchChangeError.INVALID_INPUT
        }
        updateInputs { state ->
            val batches = state.items[itemCode].orEmpty() + (batchNumber to quantity)
            state.copy(items = state.items + (itemCode to batches))
        }
        return null
    }

    fun removeBatch(itemCode: String, batchNumber: String): BatchChangeError? {
        if (_uiState.value.items[itemCode]?.containsKey(batchNumber) != true) {
            return BatchChangeError.NOT_FOUND
        }
        updateInputs { state ->
            val batches = state.items[itemCode].orEmpty() - batchNumber
            val items = if (batches.isEmpty()) state.items - itemCode else state.items + (itemCode to batches)
            state.copy(items = items)
        }
        return null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val savedStateHandle = createSavedStateHandle()
                val application = (this[APPLICATION_KEY] as MainApplication)
                FormViewModel(erpRepository = application.container.erpRepository, saved = savedStateHandle)
            }
        }
    }
}
