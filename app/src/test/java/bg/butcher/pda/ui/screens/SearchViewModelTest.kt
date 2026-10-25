package bg.butcher.pda.ui.screens

import androidx.lifecycle.SavedStateHandle
import bg.butcher.pda.data.BATCH_PAGE_SIZE
import bg.butcher.pda.data.ErpRepository
import bg.butcher.pda.data.FakeErpRepository
import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BatchFilters
import bg.butcher.pda.ui.state.NetworkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: ErpRepository = FakeErpRepository(),
        saved: SavedStateHandle = SavedStateHandle(),
    ) = SearchViewModel(repository, saved)

    private fun TestScope.effectsOf(vm: SearchViewModel): List<SearchUiEffect> {
        val effects = mutableListOf<SearchUiEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        return effects
    }

    private val SearchUiState.batches: List<BatchDetails>
        get() = (results as NetworkState.Success).data

    @Test
    fun opening_listsTheLatestBatches() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val batches = vm.uiState.value.batches
        assertTrue(batches.isNotEmpty())
        assertTrue(batches.all { it.defaultBom != null })
        val produced = batches.mapNotNull { it.manufacturingDate }
        assertEquals(produced.sortedDescending(), produced)
    }

    @Test
    fun typing_searchesAfterAPauseAndNeverNavigates() = runTest(dispatcher) {
        val vm = viewModel()
        val effects = effectsOf(vm)
        advanceUntilIdle()

        vm.setQuery("saus")
        advanceTimeBy(100)
        vm.setQuery("sausa")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.batches.all { it.itemName.contains("Sausage") })
        assertTrue(effects.isEmpty())
    }

    @Test
    fun changingFilters_searchesAgainFromTheFirstPage() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setFilters(BatchFilters(producedWithinDaysText = "", hideExpired = true))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.batches.all { it.expiryDate != null })
        assertEquals(BatchFilters(producedWithinDaysText = "", hideExpired = true), vm.uiState.value.filters)
    }

    @Test
    fun loadMore_appendsTheNextPage() = runTest(dispatcher) {
        val first = (1..BATCH_PAGE_SIZE).map { batch("B$it") }
        val second = listOf(batch("B${BATCH_PAGE_SIZE + 1}"))
        val starts = mutableListOf<Int>()
        val repository = object : ErpRepository by FakeErpRepository() {
            override suspend fun searchBatches(query: String, filters: BatchFilters, start: Int): Result<List<BatchDetails>> {
                starts += start
                return Result.success(if (start == 0) first else second)
            }
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canLoadMore)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(0, BATCH_PAGE_SIZE), starts)
        assertEquals(first + second, vm.uiState.value.batches)
        assertFalse(vm.uiState.value.canLoadMore)
    }

    @Test
    fun scan_opensTheBatchWhateverTheFilters() = runTest(dispatcher) {
        val vm = viewModel()
        val effects = effectsOf(vm)
        vm.setFilters(BatchFilters(producedWithinDaysText = "0", hideExpired = true))
        advanceUntilIdle()

        // An expired batch that the filters leave out
        vm.onScan("https://erp.example.bg/app/batch/pork_sausageS001")
        advanceUntilIdle()

        assertEquals(listOf(SearchUiEffect.ShowDetails("PORK_SAUSAGES001", fromScan = true)), effects)
    }

    @Test
    fun scan_ofAnUnknownBatchOrOtherCodeShowsAnError() = runTest(dispatcher) {
        val vm = viewModel()
        val effects = effectsOf(vm)
        advanceUntilIdle()

        vm.onScan("NOPE.0001")
        advanceUntilIdle()
        assertEquals(SearchLookupError.NotFound, vm.uiState.value.lookupError)

        vm.onScan("https://example.com/not-a-batch")
        assertEquals(SearchLookupError.NotABatchLabel, vm.uiState.value.lookupError)
        assertEquals(listOf(SearchUiEffect.ScanRejected, SearchUiEffect.ScanRejected), effects)
    }

    @Test
    fun enter_opensAnExactBatchOrKeepsTheList() = runTest(dispatcher) {
        val vm = viewModel()
        val effects = effectsOf(vm)
        advanceUntilIdle()

        // Not a scan, so a miss isn't rejected either
        vm.setQuery("saus")
        vm.onSubmit()
        advanceUntilIdle()
        assertTrue(effects.isEmpty())
        assertNull(vm.uiState.value.lookupError)

        vm.setQuery("PORK_SAUSAGES002")
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(listOf(SearchUiEffect.ShowDetails("PORK_SAUSAGES002")), effects)
    }

    @Test
    fun savedState_restoresQueryAndFilters() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = viewModel(saved = saved)
        vm.setQuery("saus")
        vm.setFilters(BatchFilters(hideExpired = true))

        val restored = viewModel(saved = saved).uiState.value

        assertEquals("saus", restored.query)
        assertEquals(BatchFilters(hideExpired = true), restored.filters)
    }

    private fun batch(name: String) = BatchDetails(name = name, item = "FG", itemName = "Product", defaultBom = "BOM-FG")
}
