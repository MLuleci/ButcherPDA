package bg.butcher.pda.ui.screens

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import bg.butcher.pda.MainApplication
import bg.butcher.pda.data.BATCH_PAGE_SIZE
import bg.butcher.pda.data.ErpRepository
import bg.butcher.pda.hardware.ScannedCode
import bg.butcher.pda.hardware.parseScan
import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BatchFilters
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.network.JobSlot
import bg.butcher.pda.network.NetworkState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "SearchViewModel"

sealed interface SearchLookupError {
    data object NotFound : SearchLookupError
    data object NotABatchLabel : SearchLookupError
    data class Failed(val error: ErpError) : SearchLookupError
}

data class SearchUiState(
    val query: String = "",
    val filters: BatchFilters = BatchFilters(),
    val results: NetworkState<List<BatchDetails>, ErpError> = NetworkState.Idle,
    // A full last page may have more after it; Frappe doesn't send a total
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreError: ErpError? = null,
    // Shown under the field after a failed scan or Enter
    val lookupError: SearchLookupError? = null,
    val lookingUp: Boolean = false,
)

sealed interface SearchUiEffect {
    data class ShowDetails(val batch: String) : SearchUiEffect
}

private object Saved {
    const val QUERY = "query"
    const val FILTERS = "filters"
}

class SearchViewModel(private val erpRepository: ErpRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SearchUiState(
            query = saved[Saved.QUERY] ?: "",
            filters = saved.get<String>(Saved.FILTERS)
                ?.let { runCatching { Json.decodeFromString<BatchFilters>(it) }.getOrNull() }
                ?: BatchFilters(),
        )
    )
    val uiState = _uiState.asStateFlow()
    private val searchJob = JobSlot(viewModelScope)
    private val lookupJob = JobSlot(viewModelScope)
    private val _effects = Channel<SearchUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        // An empty field lists the latest batches straight away
        search(debounce = Duration.ZERO)
    }

    fun setQuery(query: String) {
        if (query == _uiState.value.query) return
        saved[Saved.QUERY] = query
        _uiState.update { it.copy(query = query, lookupError = null) }
        search(debounce = 300.milliseconds)
    }

    fun setFilters(filters: BatchFilters) {
        if (filters == _uiState.value.filters) return
        saved[Saved.FILTERS] = Json.encodeToString(filters)
        _uiState.update { it.copy(filters = filters) }
        search(debounce = Duration.ZERO)
    }

    /** Runs the current search again from the first page, e.g. after an error. */
    fun retry() = search(debounce = Duration.ZERO)

    private fun search(debounce: Duration) {
        searchJob.replace(debounce) {
            val state = _uiState.updateAndGet {
                it.copy(results = NetworkState.Loading, canLoadMore = false, loadingMore = false, loadMoreError = null)
            }
            erpRepository.searchBatches(state.query, state.filters)
                .onSuccess { page ->
                    _uiState.update { it.copy(results = NetworkState.Success(page), canLoadMore = page.size == BATCH_PAGE_SIZE) }
                }
                .onFailure { e ->
                    Log.e(TAG, "search", e)
                    _uiState.update { it.copy(results = NetworkState.Error(ErpError.from(e))) }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val loaded = (state.results as? NetworkState.Success)?.data ?: return
        if (!state.canLoadMore || searchJob.isActive) return
        _uiState.update { it.copy(loadingMore = true, loadMoreError = null) }
        // Replaced by any new search, so a late page can't land on different results
        searchJob.replace {
            erpRepository.searchBatches(state.query, state.filters, start = loaded.size)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            results = NetworkState.Success(loaded + page),
                            canLoadMore = page.size == BATCH_PAGE_SIZE,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "loadMore", e)
                    _uiState.update { it.copy(loadingMore = false, loadMoreError = ErpError.from(e)) }
                }
        }
    }

    /** A scan opens its batch whatever the filters; anything else shows an error under the field. */
    fun onScan(raw: String) {
        when (val code = parseScan(raw)) {
            is ScannedCode.Batch -> lookUp(code.number)
            is ScannedCode.Unrecognised -> _uiState.update { it.copy(lookupError = SearchLookupError.NotABatchLabel) }
        }
    }

    /** Enter: open the text as an exact batch number if there is one, and otherwise keep the list. */
    fun onSubmit() {
        val text = _uiState.value.query.trim()
        if (text.isNotEmpty()) lookUp(text, keepListOnMiss = true)
    }

    private fun lookUp(batchNumber: String, keepListOnMiss: Boolean = false) {
        lookupJob.replace {
            _uiState.update { it.copy(lookingUp = true, lookupError = null) }
            erpRepository.getBatch(batchNumber)
                .onSuccess { batch ->
                    _uiState.update { it.copy(lookingUp = false) }
                    _effects.send(SearchUiEffect.ShowDetails(batch.name))
                }
                .onFailure { e ->
                    val error = ErpError.from(e)
                    val notFound = error is ErpError.Http && error.isNotFound
                    _uiState.update {
                        it.copy(
                            lookingUp = false,
                            lookupError = when {
                                notFound && keepListOnMiss -> null
                                notFound -> SearchLookupError.NotFound
                                else -> SearchLookupError.Failed(error)
                            },
                        )
                    }
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as MainApplication
                SearchViewModel(application.container.erpRepository, createSavedStateHandle())
            }
        }
    }
}
