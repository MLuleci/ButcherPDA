package bg.butcher.pda.ui.screens

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import bg.butcher.pda.MainApplication
import bg.butcher.pda.R
import bg.butcher.pda.data.ErpRepository
import bg.butcher.pda.hardware.FakeLabelPrinter
import bg.butcher.pda.hardware.LabelPrinter
import bg.butcher.pda.hardware.ScannedCode
import bg.butcher.pda.hardware.parseScan
import bg.butcher.pda.hardware.toLabel
import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.StockEntryRow
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.network.JobSlot
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.Details
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "DetailsViewModel"

sealed interface DetailsUiEffect {
    data class ShowDetails(val batch: String) : DetailsUiEffect

    /** A scan that didn't open a batch; [rejected] when the code itself is the problem. */
    data class ShowToast(@StringRes val messageResId: Int, val rejected: Boolean = false) : DetailsUiEffect
}

sealed interface Ingredients {
    data object NotMadeHere : Ingredients
    data class Loaded(val rows: List<StockEntryRow>) : Ingredients
}

data class DetailsUiState(
    val batchNumber: String,
    val batch: NetworkState<BatchDetails, ErpError> = NetworkState.Idle,
    // Only loaded once the batch has
    val ingredients: NetworkState<Ingredients, ErpError> = NetworkState.Idle,
    val printState: NetworkState<Unit, Throwable> = NetworkState.Idle,
)

/** Only batches made here have a Manufacture entry behind them; others may come from a receipt. */
val BatchDetails.isMadeHere: Boolean get() = defaultBom != null

class DetailsViewModel(
    batchNumber: String,
    private val erpRepository: ErpRepository,
    private val labelPrinter: LabelPrinter = FakeLabelPrinter(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailsUiState(batchNumber))
    val uiState = _uiState.asStateFlow()
    private val loadJob = JobSlot(viewModelScope)
    private val scanJob = JobSlot(viewModelScope)
    private val _effects = Channel<DetailsUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadBatch()
    }

    fun loadBatch() {
        loadJob.replace {
            _uiState.update { it.copy(batch = NetworkState.Loading, ingredients = NetworkState.Idle) }
            erpRepository.getBatch(_uiState.value.batchNumber)
                .onSuccess { batch ->
                    _uiState.update { it.copy(batch = NetworkState.Success(batch)) }
                    loadIngredients(batch)
                }
                .onFailure { e ->
                    Log.e(TAG, "loadBatch", e)
                    _uiState.update { it.copy(batch = NetworkState.Error(ErpError.from(e))) }
                }
        }
    }

    /** Retries only the ingredients, keeping the batch on screen. */
    fun retryIngredients() {
        val batch = (_uiState.value.batch as? NetworkState.Success)?.data ?: return
        loadJob.replace { loadIngredients(batch) }
    }

    /** Prints the batch's current amount; a label can be printed again, e.g. for a torn one. */
    fun printLabel() {
        val state = _uiState.value
        val batch = (state.batch as? NetworkState.Success)?.data?.takeIf { it.isMadeHere } ?: return
        if (state.printState is NetworkState.Loading) return

        _uiState.update { it.copy(printState = NetworkState.Loading) }
        viewModelScope.launch {
            labelPrinter.print(batch.toLabel())
                .onSuccess { _uiState.update { it.copy(printState = NetworkState.Success(Unit)) } }
                .onFailure { e ->
                    Log.e(TAG, "printLabel", e)
                    _uiState.update { it.copy(printState = NetworkState.Error(e)) }
                    _effects.send(DetailsUiEffect.ShowToast(R.string.printer_not_ready))
                }
        }
    }

    /** Shows the scanned batch instead, or keeps this one with a toast if it can't be found. */
    fun onScan(raw: String) {
        val code = parseScan(raw)
        if (code !is ScannedCode.Batch) {
            _effects.trySend(DetailsUiEffect.ShowToast(R.string.not_a_batch_label, rejected = true))
            return
        }
        scanJob.replace {
            erpRepository.getBatch(code.number)
                .onSuccess { _effects.send(DetailsUiEffect.ShowDetails(it.name)) }
                .onFailure { e ->
                    val error = ErpError.from(e)
                    val notFound = error is ErpError.Http && error.isNotFound
                    _effects.send(
                        DetailsUiEffect.ShowToast(if (notFound) R.string.batch_not_found else R.string.error_message, notFound)
                    )
                }
        }
    }

    private suspend fun loadIngredients(batch: BatchDetails) {
        val stockEntry = batch.referenceName
        if (!batch.isMadeHere || stockEntry == null) {
            _uiState.update { it.copy(ingredients = NetworkState.Success(Ingredients.NotMadeHere)) }
            return
        }
        _uiState.update { it.copy(ingredients = NetworkState.Loading) }
        erpRepository.getStockEntry(stockEntry)
            .onSuccess { entry ->
                _uiState.update { it.copy(ingredients = NetworkState.Success(Ingredients.Loaded(entry.ingredients))) }
            }
            .onFailure { e ->
                Log.e(TAG, "loadIngredients", e)
                _uiState.update { it.copy(ingredients = NetworkState.Error(ErpError.from(e))) }
            }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as MainApplication
                val route = createSavedStateHandle().toRoute<Details>()
                DetailsViewModel(route.batch, application.container.erpRepository, application.container.labelPrinter)
            }
        }
    }
}
