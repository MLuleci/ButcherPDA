package bg.butcher.pda.ui.screens

import androidx.lifecycle.SavedStateHandle
import bg.butcher.pda.R
import bg.butcher.pda.data.ErpRepository
import bg.butcher.pda.data.FakeErpRepository
import bg.butcher.pda.hardware.FakeLabelPrinter
import bg.butcher.pda.hardware.LabelPrinter
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.ui.state.NetworkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FormViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val meatballs = BomSearchResultItem(
        name = "BOM-BEEF_MEATBALLS-001",
        item = "BEEF_MEATBALLS",
        itemName = "Meatballs (Beef)",
        uom = "Nos",
        isDefault = 1,
        shelfLife = 7,
        wholeNumber = 1,
    )
    private val sausage = BomSearchResultItem(
        name = "BOM-PORK_SAUSAGE-001",
        item = "PORK_SAUSAGE",
        itemName = "Sausage (Pork)",
        uom = "Kg",
        isDefault = 1,
        shelfLife = 5,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        saved: SavedStateHandle = SavedStateHandle(),
        repository: ErpRepository = FakeErpRepository(),
        printer: LabelPrinter = FakeLabelPrinter(),
    ) = FormViewModel(repository, saved, printer)

    private fun failingSubmit(error: ErpError) = object : ErpRepository by FakeErpRepository() {
        override suspend fun createManufactureEntry(
            bom: String,
            quantity: Double,
            items: Map<String, Map<String, Double>>,
            expiryDate: LocalDate,
            explode: Boolean,
            isFinished: Boolean,
            requestId: String,
        ): Result<ManufactureEntry> = Result.failure(error)
    }

    // 25 meatballs: 1 kg beef, 0.2 kg spice and 3 onions
    private fun FormViewModel.readyToSubmit(): FormViewModel = loaded().apply {
        addBatch("BEEF", "BEEF.0001", 1.0)
        addBatch("SPICE", "SPICE.0001", 0.2)
        addBatch("ONION", "ONION.0001", 3.0)
    }

    private fun FormViewModel.loaded(): FormViewModel {
        setSelectedBom(meatballs)
        setQuantity("25")
        fetchBomDetails()
        dispatcher.scheduler.advanceUntilIdle()
        return this
    }

    private fun FormViewModel.withBatch(): FormViewModel = loaded().apply {
        addBatch("BEEF", "BEEF.0001", 1.0)
    }

    @Test
    fun selectingBom_prefillsExpiryFromShelfLife() {
        val vm = viewModel()

        vm.setSelectedBom(meatballs)

        assertEquals("7", vm.uiState.value.expiryDaysText)
        assertFalse(vm.uiState.value.expiryTyped)
    }

    @Test
    fun selectingAnotherBom_replacesTypedExpiryAndClearsBatches() {
        val vm = viewModel().withBatch()
        vm.setDaysUntilExpiry("3")
        val requestId = vm.uiState.value.requestId

        vm.setSelectedBom(sausage)

        val state = vm.uiState.value
        assertEquals("5", state.expiryDaysText)
        assertFalse(state.expiryTyped)
        assertTrue(state.items.isEmpty())
        assertNotEquals(requestId, state.requestId)
    }

    @Test
    fun selectingSameBom_keepsBatchesAndTypedExpiry() {
        val vm = viewModel().withBatch()
        vm.setDaysUntilExpiry("3")
        val requestId = vm.uiState.value.requestId

        vm.setSelectedBom(meatballs)

        val state = vm.uiState.value
        assertEquals("3", state.expiryDaysText)
        assertTrue(state.expiryTyped)
        assertEquals(1, state.items.size)
        assertEquals(requestId, state.requestId)
    }

    @Test
    fun changingQuantity_clearsBatches() {
        val vm = viewModel().withBatch()

        vm.setQuantity("50")

        assertTrue(vm.uiState.value.items.isEmpty())
    }

    @Test
    fun sameQuantityDifferentText_keepsBatches() {
        val vm = viewModel().withBatch()

        vm.setQuantity("25.0")

        assertEquals("25.0", vm.uiState.value.quantityText)
        assertEquals(1, vm.uiState.value.items.size)
    }

    @Test
    fun explode_clearsBatchesOnlyWhenChanged() {
        val vm = viewModel().withBatch()

        vm.setExplode(false)
        assertEquals(1, vm.uiState.value.items.size)

        vm.setExplode(true)
        assertTrue(vm.uiState.value.items.isEmpty())
    }

    @Test
    fun expiryAndFinished_keepBatches() {
        val vm = viewModel().withBatch()

        vm.setDaysUntilExpiry("10")
        vm.setIsFinished(false)

        assertEquals(1, vm.uiState.value.items.size)
    }

    @Test
    fun changingInput_cancelsFetchInFlight() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setSelectedBom(meatballs)
        vm.setQuantity("25")

        vm.fetchBomDetails()
        assertTrue(vm.uiState.value.fetchState is NetworkState.Loading)
        vm.setQuantity("50")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.fetchState is NetworkState.Idle)
    }

    @Test
    fun fetch_isSkippedWhenAlreadyLoaded() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setSelectedBom(meatballs)
        vm.setQuantity("25")
        vm.fetchBomDetails()
        advanceUntilIdle()
        val loaded = vm.uiState.value.fetchState

        vm.fetchBomDetails()

        assertTrue(loaded is NetworkState.Success)
        assertTrue(vm.uiState.value.fetchState === loaded)
    }

    @Test
    fun continue_staysOnStartWhenRunIsTooSmall() = runTest(dispatcher) {
        val vm = viewModel()
        val effects = mutableListOf<FormUiEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        vm.setSelectedBom(meatballs)
        // 3 meatballs need 0.36 onions; the only whole number within 0.5 of that is 0
        vm.setQuantity("3")

        vm.fetchBomDetails()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.fetchState is NetworkState.Success)
        assertEquals("ONION", vm.uiState.value.unworkableIngredients.single().itemCode)
        assertTrue(effects.none { it is FormUiEffect.GotoForm })
    }

    @Test
    fun continue_needsExpiry() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setSelectedBom(meatballs)
        vm.setQuantity("25")
        vm.setDaysUntilExpiry("")

        vm.fetchBomDetails()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.fetchState is NetworkState.Idle)
    }

    @Test
    fun savedState_restoresInputsButNotServerResults() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = viewModel(saved).withBatch()
        vm.setDaysUntilExpiry("3")
        vm.fetchBomDetails()
        advanceUntilIdle()

        val restored = viewModel(saved).uiState.value

        assertEquals(meatballs, restored.selectedItem)
        assertEquals("25", restored.quantityText)
        assertEquals("3", restored.expiryDaysText)
        assertTrue(restored.expiryTyped)
        assertEquals(mapOf("BEEF" to mapOf("BEEF.0001" to 1.0)), restored.items)
        assertEquals(vm.uiState.value.requestId, restored.requestId)
        assertTrue(restored.fetchState is NetworkState.Idle)
    }

    @Test
    fun reload_fetchesDetailsAfterRestore() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        viewModel(saved).withBatch()
        val restored = viewModel(saved)

        restored.reloadBomDetails()
        advanceUntilIdle()

        assertTrue(restored.uiState.value.fetchState is NetworkState.Success)
        assertEquals(1, restored.uiState.value.items.size)
    }

    @Test
    fun lookup_matchesIngredientAndPrefillsWhatIsLeft() = runTest(dispatcher) {
        val vm = viewModel().loaded()

        val first = vm.lookupBatch(" beef.0001 ") as NetworkState.Success
        assertEquals("BEEF.0001", first.data.batch.name)
        assertEquals("BEEF", first.data.ingredient.itemCode)
        assertEquals(1.0, first.data.prefill!!, 1e-9)

        vm.addBatch("BEEF", "BEEF.0001", 0.4)
        val second = vm.lookupBatch("BEEF.0002") as NetworkState.Success
        assertEquals(0.6, second.data.prefill!!, 1e-9)
    }

    @Test
    fun lookup_rejectsBatchAlreadyAdded() = runTest(dispatcher) {
        val vm = viewModel().loaded()
        vm.addBatch("BEEF", "BEEF.0001", 0.4)

        assertEquals(NetworkState.Error(BatchLookupError.AlreadyAdded), vm.lookupBatch("beef.0001"))
    }

    @Test
    fun lookup_rejectsUnknownBatch() = runTest(dispatcher) {
        val vm = viewModel().loaded()

        assertEquals(NetworkState.Error(BatchLookupError.NotFound), vm.lookupBatch("NOPE.0001"))
    }

    @Test
    fun lookup_rejectsBatchNotInRecipe() = runTest(dispatcher) {
        val vm = viewModel().loaded()

        val result = vm.lookupBatch("PORK.0001") as NetworkState.Error
        val error = result.error as BatchLookupError.NotInRecipe
        assertEquals("PORK", error.batch.item)
    }

    @Test
    fun batchChanges_returnErrorsInsteadOfThrowing() {
        val vm = viewModel().loaded()

        assertEquals(BatchChangeError.INVALID_INPUT, vm.addBatch("", "B1", 1.0))
        assertEquals(BatchChangeError.INVALID_INPUT, vm.addBatch("BEEF", "", 1.0))
        assertEquals(BatchChangeError.INVALID_INPUT, vm.addBatch("BEEF", "B1", 0.0))
        assertEquals(BatchChangeError.INVALID_INPUT, vm.addBatch("BEEF", "B1", Double.POSITIVE_INFINITY))
        assertEquals(BatchChangeError.NOT_FOUND, vm.updateBatch("BEEF", "B1", 1.0))
        assertEquals(BatchChangeError.NOT_FOUND, vm.removeBatch("BEEF", "B1"))

        assertNull(vm.addBatch("BEEF", "B1", 1.0))
        assertNull(vm.removeBatch("BEEF", "B1"))
        assertTrue(vm.uiState.value.items.isEmpty())
    }

    @Test
    fun addBatch_rejectsDuplicates() {
        val vm = viewModel().withBatch()

        assertEquals(BatchChangeError.ALREADY_ADDED, vm.addBatch("BEEF", "beef.0001", 0.5))
        assertEquals(mapOf("BEEF" to mapOf("BEEF.0001" to 1.0)), vm.uiState.value.items)
    }

    @Test
    fun addBatch_rejectsItemsNotInRecipe() {
        assertEquals(BatchChangeError.NOT_IN_RECIPE, viewModel().addBatch("BEEF", "BEEF.0001", 1.0))
        assertEquals(BatchChangeError.NOT_IN_RECIPE, viewModel().loaded().addBatch("PORK", "PORK.0001", 1.0))
    }

    @Test
    fun addBatch_rejectsFractionsForWholeNumberIngredients() {
        val vm = viewModel().loaded()

        assertEquals(BatchChangeError.INVALID_INPUT, vm.addBatch("ONION", "ONION.0001", 2.5))
        assertNull(vm.addBatch("ONION", "ONION.0001", 3.0))
    }

    @Test
    fun updateBatch_changesQuantityOnly() {
        val vm = viewModel().withBatch()

        assertNull(vm.updateBatch("BEEF", "BEEF.0001", 0.8))

        assertEquals(mapOf("BEEF" to mapOf("BEEF.0001" to 0.8)), vm.uiState.value.items)
    }

    @Test
    fun submit_savesEntryAndOpensSummary() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = viewModel(saved).readyToSubmit()
        val effects = mutableListOf<FormUiEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        assertTrue(vm.uiState.value.canSubmit)

        vm.submit()
        advanceUntilIdle()

        val entry = (vm.uiState.value.submitState as NetworkState.Success).data
        assertEquals("BEEF_MEATBALLS", entry.itemCode)
        assertFalse(vm.uiState.value.canSubmit)
        assertTrue(FormUiEffect.GotoSummary in effects)
        assertEquals(entry, (viewModel(saved).uiState.value.submitState as NetworkState.Success).data)
    }

    @Test
    fun submit_failureKeepsRequestIdForRetry() = runTest(dispatcher) {
        val vm = viewModel(repository = failingSubmit(ErpError.Http(500, null))).readyToSubmit()
        val requestId = vm.uiState.value.requestId

        vm.submit()
        advanceUntilIdle()

        val error = (vm.uiState.value.submitState as NetworkState.Error).error
        assertTrue(error.isOutcomeUnknown)
        assertEquals(requestId, vm.uiState.value.requestId)
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun startAgain_keepsSettingsAndClearsBatches() = runTest(dispatcher) {
        val vm = viewModel().readyToSubmit()
        vm.setDaysUntilExpiry("3")
        vm.submit()
        advanceUntilIdle()
        val requestId = vm.uiState.value.requestId

        vm.startAgain()

        val state = vm.uiState.value
        assertTrue(state.items.isEmpty())
        assertTrue(state.submitState is NetworkState.Idle)
        assertNotEquals(requestId, state.requestId)
        assertEquals(meatballs, state.selectedItem)
        assertEquals("25", state.quantityText)
        assertEquals("3", state.expiryDaysText)
        assertTrue(state.fetchState is NetworkState.Success)
    }

    @Test
    fun printLabel_failureShowsToast() = runTest(dispatcher) {
        val vm = viewModel(printer = { Result.failure(IllegalStateException("No paper")) }).readyToSubmit()
        val effects = mutableListOf<FormUiEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        vm.submit()
        advanceUntilIdle()

        vm.printLabel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.printState is NetworkState.Error)
        assertTrue(FormUiEffect.ShowToast(R.string.printer_not_ready) in effects)
    }
}
