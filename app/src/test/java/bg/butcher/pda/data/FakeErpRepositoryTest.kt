package bg.butcher.pda.data

import bg.butcher.pda.network.ErpError
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeErpRepositoryTest {
    private val repository = FakeErpRepository()
    private val expiry = LocalDate(2026, 11, 1)

    @Test
    fun searchBom_matchesNameAndHasNewFields() = runTest {
        val result = repository.searchBom(" meatballs ").getOrThrow()

        assertEquals(listOf("BOM-BEEF_MEATBALLS-001", "BOM-BEEF_MEATBALLS-002"), result.map { it.name }.sorted())
        assertTrue(result.all { it.isWholeNumber && it.shelfLife == 7 })
        assertEquals(1, result.count { it.isDefaultBom })
    }

    @Test
    fun getBomDetails_scalesIngredients() = runTest {
        val result = repository.getBomDetails("BOM-BEEF_MEATBALLS-001", 25.0, explode = false).getOrThrow()

        assertEquals(mapOf("BEEF" to 1.0, "SPICE" to 0.2, "ONION" to 3.0), result.items.associate { it.itemCode to it.quantity })
        assertTrue(result.items.single { it.itemCode == "ONION" }.wholeNumber)
        assertEquals(5.0, result.tolerancePercent, 0.0)
    }

    @Test
    fun getBomDetails_rejectsFractionForWholeNumberUnit() = runTest {
        val error = repository.getBomDetails("BOM-BEEF_MEATBALLS-001", 2.5, explode = false).exceptionOrNull()

        assertTrue(error is ErpError.Http && error.code == 417)
    }

    @Test
    fun getBatch_isCaseInsensitiveAndReportsNotFound() = runTest {
        assertEquals("BEEF", repository.getBatch("beef.0001").getOrThrow().item)

        val error = repository.getBatch("NOPE").exceptionOrNull()
        assertTrue(error is ErpError.Http && error.isNotFound)
    }

    @Test
    fun createManufactureEntry_createsBatchAndRepeatsForSameRequestId() = runTest {
        val items = mapOf(
            "BEEF" to mapOf("BEEF.0001" to 1.0),
            "SPICE" to mapOf("SPICE.0001" to 0.2),
            "ONION" to mapOf("ONION.0001" to 3.0),
        )
        val create = suspend {
            repository.createManufactureEntry("BOM-BEEF_MEATBALLS-001", 25.0, items, expiry, false, true, "request-1")
        }

        val first = create().getOrThrow()
        val repeat = create().getOrThrow()

        assertEquals(first, repeat)
        assertEquals(expiry, repository.getBatch(first.batchNumber).getOrThrow().expiryDate)
    }

    @Test
    fun createManufactureEntry_rejectsMismatchedItems() = runTest {
        val result = repository.createManufactureEntry(
            "BOM-BEEF_MEATBALLS-001", 25.0, mapOf("BEEF" to mapOf("BEEF.0001" to 1.0)), expiry, false, true, "request-2",
        )

        val error = result.exceptionOrNull()
        assertTrue(error is ErpError.Http && error.message!!.startsWith("Items do not match BOM"))
    }
}
