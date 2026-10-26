package bg.butcher.pda.ui.screens

import bg.butcher.pda.R
import bg.butcher.pda.data.ErpRepository
import bg.butcher.pda.data.FakeErpRepository
import bg.butcher.pda.hardware.LabelData
import bg.butcher.pda.model.StockEntryDetails
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.ui.state.NetworkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.effectsOf(vm: DetailsViewModel): List<DetailsUiEffect> {
        val effects = mutableListOf<DetailsUiEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        return effects
    }

    @Test
    fun batchMadeHere_loadsItsIngredients() = runTest(dispatcher) {
        val vm = DetailsViewModel("PORK_SAUSAGES001", FakeErpRepository())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue((state.batch as NetworkState.Success).data.isMadeHere)
        val rows = ((state.ingredients as NetworkState.Success).data as Ingredients.Loaded).rows
        assertEquals(listOf("PORK.0001", "SPICE.0001", "CASING.0001"), rows.map { it.batchNumber })
    }

    @Test
    fun rawMaterial_skipsTheStockEntry() = runTest(dispatcher) {
        var stockEntryRequests = 0
        val repository = object : ErpRepository by FakeErpRepository() {
            override suspend fun getStockEntry(name: String): Result<StockEntryDetails> {
                stockEntryRequests++
                return Result.failure(IllegalStateException())
            }
        }
        val vm = DetailsViewModel("BEEF.0001", repository)
        advanceUntilIdle()

        assertEquals(NetworkState.Success(Ingredients.NotMadeHere), vm.uiState.value.ingredients)
        assertEquals(0, stockEntryRequests)
    }

    @Test
    fun ingredientsFailure_keepsTheBatchAndCanBeRetried() = runTest(dispatcher) {
        val fake = FakeErpRepository()
        var fail = true
        val repository = object : ErpRepository by fake {
            override suspend fun getStockEntry(name: String): Result<StockEntryDetails> =
                if (fail) Result.failure(ErpError.Network(IOException())) else fake.getStockEntry(name)
        }
        val vm = DetailsViewModel("PORK_SAUSAGES001", repository)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.batch is NetworkState.Success)
        assertTrue(vm.uiState.value.ingredients is NetworkState.Error)

        fail = false
        vm.retryIngredients()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.batch is NetworkState.Success)
        assertTrue((vm.uiState.value.ingredients as NetworkState.Success).data is Ingredients.Loaded)
    }

    @Test
    fun missingBatch_isAnError() = runTest(dispatcher) {
        val vm = DetailsViewModel("NOPE.0001", FakeErpRepository())
        advanceUntilIdle()

        val error = (vm.uiState.value.batch as NetworkState.Error).error
        assertTrue(error is ErpError.Http && error.isNotFound)
    }

    @Test
    fun scan_replacesTheBatchShownOrKeepsIt() = runTest(dispatcher) {
        val vm = DetailsViewModel("PORK_SAUSAGES001", FakeErpRepository())
        val effects = effectsOf(vm)
        advanceUntilIdle()

        // Each scan replaces any lookup still running, so let each one finish
        vm.onScan("https://erp.example.bg/app/batch/kebapcheS004")
        advanceUntilIdle()
        vm.onScan("NOPE.0001")
        advanceUntilIdle()
        vm.onScan("https://example.com/not-a-batch")

        assertEquals(
            listOf(
                DetailsUiEffect.ShowDetails("KEBAPCHES004"),
                DetailsUiEffect.ShowToast(R.string.batch_not_found, rejected = true),
                DetailsUiEffect.ShowToast(R.string.not_a_batch_label, rejected = true),
            ),
            effects,
        )
        assertEquals("PORK_SAUSAGES001", (vm.uiState.value.batch as NetworkState.Success).data.name)
    }

    @Test
    fun scan_networkFailureIsNotARejectedScan() = runTest(dispatcher) {
        val fake = FakeErpRepository()
        var fail = false
        val repository = object : ErpRepository by fake {
            override suspend fun getBatch(batchNumber: String) =
                if (fail) Result.failure(ErpError.Network(IOException())) else fake.getBatch(batchNumber)
        }
        val vm = DetailsViewModel("PORK_SAUSAGES001", repository)
        val effects = effectsOf(vm)
        advanceUntilIdle()

        fail = true
        vm.onScan("KEBAPCHES004")
        advanceUntilIdle()

        assertEquals(listOf(DetailsUiEffect.ShowToast(R.string.error_message, rejected = false)), effects)
    }

    @Test
    fun printLabel_printsTheCurrentAmountOfABatchMadeHere() = runTest(dispatcher) {
        val printed = mutableListOf<LabelData>()
        val vm = DetailsViewModel("PORK_SAUSAGES001", FakeErpRepository()) { printed += it; Result.success(Unit) }
        advanceUntilIdle()

        vm.printLabel()
        advanceUntilIdle()

        val label = printed.single()
        assertEquals("PORK_SAUSAGES001", label.batchNumber)
        assertEquals("10 Kg", label.quantity)
        assertEquals("MAT-STE-FAKE-S0001", label.stockEntryId)
        assertTrue(vm.uiState.value.printState is NetworkState.Success)
    }

    @Test
    fun printLabel_failureShowsAToastAndCanBeRetried() = runTest(dispatcher) {
        val vm = DetailsViewModel("PORK_SAUSAGES001", FakeErpRepository()) { Result.failure(IllegalStateException()) }
        val effects = effectsOf(vm)
        advanceUntilIdle()

        vm.printLabel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.printState is NetworkState.Error)
        assertEquals(listOf(DetailsUiEffect.ShowToast(R.string.printer_not_ready)), effects)
    }

    @Test
    fun printLabel_printsBoughtInBatchesToo() = runTest(dispatcher) {
        val printed = mutableListOf<LabelData>()
        val vm = DetailsViewModel("BEEF.0003", FakeErpRepository()) { printed += it; Result.success(Unit) }
        advanceUntilIdle()

        vm.printLabel()
        advanceUntilIdle()

        // A raw batch with no production date still prints, with its batch number for the QR code
        val label = printed.single()
        assertEquals("BEEF.0003", label.batchNumber)
        assertEquals("Beef", label.itemName)
        assertNull(label.productionDate)
        assertTrue(vm.uiState.value.printState is NetworkState.Success)
    }
}

