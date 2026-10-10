package bg.butcher.pda

import bg.butcher.pda.fake.FakeNetworkErpRepository
import bg.butcher.pda.ui.screens.AssemblyViewModel
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.time.Instant

class AssemblyViewModelTest {
    @Test
    fun assemblyViewModel_setQuantity_validQuantity() {
        val vm = AssemblyViewModel(FakeNetworkErpRepository())
        vm.setQuantity(5.0f)
        assertEquals(5.0f, vm.uiState.value.quantity)
    }

    @Test
    fun assemblyViewModel_setDaysUntilExpiry_validDays() {
        val before = Instant.now()
        val vm = AssemblyViewModel(FakeNetworkErpRepository())
        vm.setDaysUntilExpiry(5)
        val after = vm.getExpiryDate().toInstant()

        assertEquals(5, vm.uiState.value.daysUntilExpiry)
        assertTrue(before.isBefore(after))
    }

    @Test
    fun assemblyViewModel_setExplode_isChanged() {
        val vm = AssemblyViewModel(FakeNetworkErpRepository())
        vm.setExplode(true)
        assertTrue(vm.uiState.value.explode)
    }

    @Test
    fun assemblyViewModel_setIsFinished_isChanged() {
        val vm = AssemblyViewModel(FakeNetworkErpRepository())
        vm.setIsFinished(false)
        assertFalse(vm.uiState.value.isFinished)
    }

    @Test
    fun assemblyViewModel_upsertBatch_validInput() {
        val vm = AssemblyViewModel(FakeNetworkErpRepository())
        val itemCode = "item1"
        val batchNo = "I.001"
        val qty = 1.0f
        vm.upsertBatch(itemCode, batchNo, qty)

        assertTrue(vm.uiState.value.items.containsKey(itemCode))
        val batches = vm.uiState.value.items[itemCode]!!
        assertTrue(batches.containsKey(batchNo))
        assertEquals(qty, batches[batchNo])
    }

    @Test
    fun assemblyViewModel_upsertBatch_overwrites() {
        val vm = AssemblyViewModel(FakeNetworkErpRepository())
        val itemCode = "item1"
        val batchNo = "I.001"
        vm.upsertBatch(itemCode, batchNo, 1.0f)
        vm.upsertBatch(itemCode, batchNo, 2.0f)

        assertTrue(vm.uiState.value.items.containsKey(itemCode))
        val batches = vm.uiState.value.items[itemCode]!!
        assertTrue(batches.containsKey(batchNo))
        assertEquals(2.0f, batches[batchNo])
    }

    @Test
    fun assemblyViewModel_removeBatch_removeLast() {
        val vm = AssemblyViewModel(FakeNetworkErpRepository())
        val itemCode = "item1"
        vm.upsertBatch(itemCode, "I.001", 1.0f)
        vm.removeBatch(itemCode, "I.001")

        assertFalse(vm.uiState.value.items.containsKey(itemCode))
    }

    @Test
    fun assemblyViewModel_removeBatch_removeOne() {
        val vm = AssemblyViewModel(FakeNetworkErpRepository())
        val itemCode = "item1"
        vm.upsertBatch(itemCode, "I.001", 1.0f)
        vm.upsertBatch(itemCode, "I.002", 1.0f)
        vm.removeBatch(itemCode, "I.001")

        assertTrue(vm.uiState.value.items.containsKey(itemCode))
        val batches = vm.uiState.value.items[itemCode]!!
        assertFalse(batches.containsKey("I.001"))
        assertTrue(batches.containsKey("I.002"))
    }
}