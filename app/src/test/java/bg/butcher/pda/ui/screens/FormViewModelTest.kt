package bg.butcher.pda.ui.screens

import androidx.lifecycle.SavedStateHandle
import bg.butcher.pda.data.FakeErpRepository
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.network.NetworkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = FormViewModel(FakeErpRepository(), saved)

    private fun FormViewModel.withBatch(): FormViewModel {
        setSelectedBom(meatballs)
        setQuantity("25")
        upsertBatch("BEEF", "BEEF.0001", 1.0)
        return this
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
    fun upsertAndRemoveBatch_returnErrorsInsteadOfThrowing() {
        val vm = viewModel()

        assertEquals(BatchChangeError.INVALID_INPUT, vm.upsertBatch("", "B1", 1.0))
        assertEquals(BatchChangeError.INVALID_INPUT, vm.upsertBatch("BEEF", "", 1.0))
        assertEquals(BatchChangeError.INVALID_INPUT, vm.upsertBatch("BEEF", "B1", 0.0))
        assertEquals(BatchChangeError.INVALID_INPUT, vm.upsertBatch("BEEF", "B1", Double.POSITIVE_INFINITY))
        assertEquals(BatchChangeError.NOT_FOUND, vm.removeBatch("BEEF", "B1"))

        assertNull(vm.upsertBatch("BEEF", "B1", 1.0))
        assertNull(vm.removeBatch("BEEF", "B1"))
        assertTrue(vm.uiState.value.items.isEmpty())
    }
}
