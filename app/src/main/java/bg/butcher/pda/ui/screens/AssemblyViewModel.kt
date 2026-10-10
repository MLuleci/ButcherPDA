package bg.butcher.pda.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import bg.butcher.pda.MainApplication
import bg.butcher.pda.data.ErpRepository
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.network.JobSlot
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.network.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

data class AssemblyUiState(
    // Search page:
    val searchState: NetworkState<List<BomSearchResultItem>> = NetworkState(status = NetworkStatus.IDLE),
    val bomSelected: BomSearchResultItem? = null,

    // Input page:
    val fetchState: NetworkState<BomDetails> = NetworkState(status = NetworkStatus.IDLE),
    val quantity: Float = 0.0f,
    val items: Map<String, Map<String, Float>> = mapOf(),
    val daysUntilExpiry: Int = 0,
    val explode: Boolean = false,
    val isFinished: Boolean = true,

    // Result page:
    val submitState: NetworkState<ManufactureEntry> = NetworkState(status = NetworkStatus.IDLE),
)

class AssemblyViewModel(private val erpRepository: ErpRepository): ViewModel() {
    private val _uiState = MutableStateFlow(AssemblyUiState())
    val uiState: StateFlow<AssemblyUiState> = _uiState.asStateFlow()

    private val bomSearchJob = JobSlot(viewModelScope)
    private val bomFetchJob = JobSlot(viewModelScope)
    private val batchSearchJob = JobSlot(viewModelScope)
    private val submitJob = JobSlot(viewModelScope)

    fun reset() {
        _uiState.update { AssemblyUiState() }
    }

    fun searchForBom(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchState = NetworkState()) }
            bomSearchJob.cancel()
        } else {
            bomSearchJob.replace(300.milliseconds) {
                _uiState.update { it.copy(searchState = NetworkState(status = NetworkStatus.PENDING)) }
                erpRepository.searchBom(query)
                    .onSuccess { results -> _uiState.update { it.copy(searchState = NetworkState.withSuccess(results)) } }
                    .onFailure { e ->
                        Log.e("AssemblyViewModel", "searchForBom", e)
                        _uiState.update { it.copy(searchState = NetworkState.withError(e)) }
                    }
            }
        }
    }

    fun fetchBomDetails(name: String, quantity: Float = 1f, explode: Boolean = false) {
        if (name.isBlank()) {
            _uiState.update { it.copy(fetchState = NetworkState()) }
            bomSearchJob.cancel()
        } else {
            bomFetchJob.replace {
                _uiState.update { it.copy(fetchState = NetworkState(status = NetworkStatus.PENDING)) }
                erpRepository.getBomDetails(name, quantity, explode)
                    .onSuccess { results -> _uiState.update { it.copy(fetchState = NetworkState.withSuccess(results)) } }
                    .onFailure { e ->
                        Log.e("AssemblyViewModel", "fetchBomDetails", e)
                        _uiState.update { it.copy(fetchState = NetworkState.withError(e)) }
                    }
            }
        }
    }

    fun setSelectedBom(bom: BomSearchResultItem) {
        _uiState.update { it.copy(bomSelected = bom) }
    }

    fun setQuantity(quantity: Float) {
        require(quantity > 0.0f) { "Quantity cannot be zero or negative, was $quantity" }
        _uiState.update { it.copy(quantity = quantity) }
    }

    fun setDaysUntilExpiry(days: Int) {
        require(days > 0) { "Days cannot be less than 1 was $days" }
        _uiState.update { it.copy(daysUntilExpiry = days) }
    }

    fun setExplode(explode: Boolean) {
        _uiState.update { it.copy(explode = explode) }
    }

    fun setIsFinished(isFinished: Boolean) {
        _uiState.update { it.copy(isFinished = isFinished) }
    }

    fun upsertBatch(itemCode: String, batchNumber: String, quantity: Float) {
        require(itemCode.isNotEmpty()) { "Item code cannot be empty" }
        require(batchNumber.isNotEmpty()) { "Batch number cannot be empty" }
        require(quantity > 0.0f) { "Quantity cannot be zero or negative, was $quantity" }
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

    fun getExpiryDate(): Date {
        val expiryDate = Calendar.getInstance()
        expiryDate.add(Calendar.DAY_OF_MONTH, _uiState.value.daysUntilExpiry)
        return expiryDate.getTime()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as MainApplication)
                AssemblyViewModel(erpRepository = application.container.erpRepository)
            }
        }
    }
}