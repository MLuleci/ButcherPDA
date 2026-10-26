package bg.butcher.pda.data

import bg.butcher.pda.model.BatchFilters
import bg.butcher.pda.model.BatchKind
import bg.butcher.pda.model.BatchSort
import bg.butcher.pda.network.ErpError
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock

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

    private val noDayLimit = BatchFilters(producedWithinDaysText = "")

    @Test
    fun searchBatches_listsOnlyBatchesMadeHere() = runTest {
        val results = repository.searchBatches("", noDayLimit).getOrThrow()

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.defaultBom != null })
        assertTrue(repository.searchBatches("beef", noDayLimit).getOrThrow().none { it.item == "BEEF" })
    }

    @Test
    fun searchBatches_listsRawMaterialsOrEverything() = runTest {
        val raw = repository.searchBatches("beef", noDayLimit.withKind(BatchKind.RAW)).getOrThrow()
        val all = repository.searchBatches("", noDayLimit.withKind(BatchKind.ALL)).getOrThrow()

        assertEquals(listOf("BEEF.0001", "BEEF.0002", "BEEF.0003"), raw.map { it.name }.sorted())
        assertTrue(raw.all { it.defaultBom == null })
        assertTrue(all.any { it.defaultBom == null } && all.any { it.defaultBom != null })
    }

    @Test
    fun searchBatches_aDayLimitLeavesOutRawBatchesWithoutADate() = runTest {
        val raw = BatchFilters(producedWithinDaysText = "30", kind = BatchKind.RAW)

        val names = repository.searchBatches("beef", raw).getOrThrow().map { it.name }

        // BEEF.0003 has no production date, as when none is typed in on receipt
        assertEquals(listOf("BEEF.0001", "BEEF.0002"), names.sorted())
    }

    @Test
    fun searchBatches_matchesBatchNumberOrProductName() = runTest {
        val byName = repository.searchBatches("saus", noDayLimit).getOrThrow()
        val byNumber = repository.searchBatches(byName.first().name.takeLast(4), noDayLimit).getOrThrow()

        assertTrue(byName.isNotEmpty() && byName.all { it.itemName.contains("Sausage") })
        assertTrue(byNumber.any { it.name == byName.first().name })
    }

    @Test
    fun searchBatches_appliesFiltersAndSort() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        val hidingExpired = repository.searchBatches("", noDayLimit.copy(hideExpired = true)).getOrThrow()
        val lastWeek = repository.searchBatches("", BatchFilters(producedWithinDaysText = "7")).getOrThrow()
        val byExpiry = repository.searchBatches("", noDayLimit.withSort(BatchSort.EXPIRES)).getOrThrow()

        assertTrue(hidingExpired.all { it.expiryDate != null && it.expiryDate!! >= today })
        assertTrue(lastWeek.all { it.manufacturingDate!! >= today.minus(7, DateTimeUnit.DAY) })
        val expiries = byExpiry.mapNotNull { it.expiryDate }
        assertEquals(expiries.sorted(), expiries)
    }

    @Test
    fun getStockEntry_listsTheIngredientsOfASubmittedRun() = runTest {
        val items = mapOf(
            "BEEF" to mapOf("BEEF.0001" to 0.6, "BEEF.0002" to 0.4),
            "SPICE" to mapOf("SPICE.0001" to 0.2),
            "ONION" to mapOf("ONION.0001" to 3.0),
        )
        val entry = repository.createManufactureEntry(
            "BOM-BEEF_MEATBALLS-001", 25.0, items, expiry, false, true, "request-3",
        ).getOrThrow()

        val batch = repository.getBatch(entry.batchNumber).getOrThrow()
        val stockEntry = repository.getStockEntry(batch.referenceName!!).getOrThrow()

        assertEquals("BOM-BEEF_MEATBALLS-001", batch.defaultBom)
        assertEquals(
            listOf("BEEF.0001", "BEEF.0002", "SPICE.0001", "ONION.0001"),
            stockEntry.ingredients.map { it.batchNumber },
        )
    }
}

