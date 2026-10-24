package bg.butcher.pda.data

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomDetailsItem
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.util.roundForErp
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration

private data class FakeItem(
    val code: String,
    val name: String,
    val uom: String,
    val shelfLife: Int,
) {
    val wholeNumber: Boolean get() = uom == "Nos"
}

private data class FakeBom(
    val name: String,
    val item: FakeItem,
    val isDefault: Boolean,
    val quantity: Double,
    val ingredients: List<Pair<FakeItem, Double>>,
    val tolerancePercent: Double = 5.0,
)

private val BEEF = FakeItem("BEEF", "Beef", "Kg", 14)
private val PORK = FakeItem("PORK", "Pork", "Kg", 10)
private val SPICE = FakeItem("SPICE", "Spice", "Kg", 60)
private val ONION = FakeItem("ONION", "Onion", "Nos", 20)
private val CASING = FakeItem("CASING", "Casing", "Nos", 180)

private val MEATBALLS = FakeItem("BEEF_MEATBALLS", "Meatballs (Beef)", "Nos", 7)
private val SAUSAGE = FakeItem("PORK_SAUSAGE", "Sausage (Pork)", "Kg", 5)
private val MINCE = FakeItem("MIXED_MINCE", "Mince (Mixed)", "Kg", 3)
private val KEBAPCHE = FakeItem("KEBAPCHE", "Kebapche", "Nos", 4)

private val BOMS = listOf(
    FakeBom("BOM-BEEF_MEATBALLS-001", MEATBALLS, true, 100.0, listOf(BEEF to 4.0, SPICE to 0.8, ONION to 12.0)),
    FakeBom("BOM-BEEF_MEATBALLS-002", MEATBALLS, false, 100.0, listOf(BEEF to 4.5, SPICE to 0.5)),
    FakeBom("BOM-PORK_SAUSAGE-001", SAUSAGE, true, 10.0, listOf(PORK to 8.5, SPICE to 0.3, CASING to 20.0)),
    FakeBom("BOM-MIXED_MINCE-001", MINCE, true, 1.0, listOf(BEEF to 0.6, PORK to 0.4), tolerancePercent = 3.0),
    FakeBom("BOM-KEBAPCHE-001", KEBAPCHE, true, 50.0, listOf(BEEF to 2.0, PORK to 1.5, SPICE to 0.1, ONION to 5.0)),
)

private val RAW_MATERIALS = listOf(BEEF, PORK, SPICE, ONION, CASING)

/** Stands in for the server on the emulator, with the same main checks so error paths can be tried. */
class FakeErpRepository(
    private val latency: Duration = Duration.ZERO,
    private val failureRate: Double = 0.0,
): ErpRepository {
    private val today: LocalDate
        get() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    // Keyed by upper-case batch number; Frappe names are case-insensitive
    private val batches = ConcurrentHashMap<String, BatchDetails>()
    private val entries = ConcurrentHashMap<String, ManufactureEntry>()
    private val entryCounter = AtomicInteger(0)

    init {
        RAW_MATERIALS.forEach { item ->
            for (i in 1..3) {
                val produced = today.minus(i, DateTimeUnit.DAY)
                addBatch(
                    BatchDetails(
                        name = "${item.code}.${i.toString().padStart(4, '0')}",
                        item = item.code,
                        itemName = item.name,
                        stockUom = item.uom,
                        manufacturingDate = produced,
                        expiryDate = produced.plus(item.shelfLife, DateTimeUnit.DAY),
                        batchQuantity = if (item.wholeNumber) 200.0 else 50.0 * i,
                        referenceDoctype = "Stock Entry",
                        referenceName = "MAT-STE-FAKE-R${item.code}$i",
                    )
                )
            }
        }
    }

    private fun addBatch(batch: BatchDetails) {
        batches[batch.name.uppercase()] = batch
    }

    private suspend fun <T> respond(block: () -> T): Result<T> {
        delay(latency)
        if (Random.nextDouble() < failureRate) {
            return Result.failure(ErpError.Network(IOException("Simulated network failure")))
        }
        return try {
            Result.success(block())
        } catch (e: ErpError) {
            Result.failure(e)
        }
    }

    private fun findBom(name: String): FakeBom =
        BOMS.firstOrNull { it.name == name } ?: throw ErpError.Http(404, "BOM $name not found")

    private fun checkQuantity(quantity: Double, item: FakeItem) {
        if (quantity <= 0.0) throw ErpError.Http(417, "Invalid quantity")
        if (item.wholeNumber && quantity % 1.0 != 0.0) {
            throw ErpError.Http(417, "Quantity must be a whole number for UOM ${item.uom}")
        }
    }

    override suspend fun searchBom(query: String): Result<List<BomSearchResultItem>> = respond {
        BOMS.filter { it.item.name.contains(query.trim(), ignoreCase = true) }
            .sortedByDescending { it.item.name }
            .map {
                BomSearchResultItem(
                    name = it.name,
                    item = it.item.code,
                    itemName = it.item.name,
                    uom = it.item.uom,
                    isDefault = if (it.isDefault) 1 else 0,
                    shelfLife = it.item.shelfLife,
                    wholeNumber = if (it.item.wholeNumber) 1 else 0,
                )
            }
    }

    override suspend fun getBatch(batchNumber: String): Result<BatchDetails> = respond {
        batches[batchNumber.trim().uppercase()] ?: throw ErpError.Http(404, "Batch $batchNumber not found")
    }

    override suspend fun getBomDetails(
        bom: String,
        quantity: Double,
        explode: Boolean
    ): Result<BomDetails> = respond {
        val fake = findBom(bom)
        checkQuantity(quantity, fake.item)
        BomDetails(
            name = fake.name,
            quantity = quantity,
            itemCode = fake.item.code,
            itemName = fake.item.name,
            uom = fake.item.uom,
            stockUom = fake.item.uom,
            shelfLife = fake.item.shelfLife,
            items = fake.ingredients.map { (item, perBom) ->
                BomDetailsItem(
                    quantity = (perBom * quantity / fake.quantity).roundForErp(),
                    itemCode = item.code,
                    itemName = item.name,
                    uom = item.uom,
                    stockUom = item.uom,
                    conversionFactor = 1.0,
                    wholeNumber = item.wholeNumber,
                )
            },
            tolerancePercent = fake.tolerancePercent,
            wholeNumber = fake.item.wholeNumber,
        )
    }

    override suspend fun createManufactureEntry(
        bom: String,
        quantity: Double,
        items: Map<String, Map<String, Double>>,
        expiryDate: LocalDate,
        explode: Boolean,
        isFinished: Boolean,
        requestId: String,
    ): Result<ManufactureEntry> = respond {
        entries[requestId]?.let { return@respond it }

        val fake = findBom(bom)
        checkQuantity(quantity, fake.item)
        val expected = fake.ingredients.map { it.first.code }.toSet()
        if (items.keys != expected) {
            val missing = (expected - items.keys).joinToString().ifEmpty { "-" }
            val extra = (items.keys - expected).joinToString().ifEmpty { "-" }
            throw ErpError.Http(417, "Items do not match BOM. Missing: $missing. Not in BOM: $extra")
        }

        val number = entryCounter.incrementAndGet()
        val stockEntryId = "MAT-STE-FAKE-${number.toString().padStart(5, '0')}"
        val batchNumber = "${fake.item.code}${number.toString().padStart(4, '0')}"
        addBatch(
            BatchDetails(
                name = batchNumber,
                item = fake.item.code,
                itemName = fake.item.name,
                stockUom = fake.item.uom,
                manufacturingDate = today,
                expiryDate = expiryDate,
                batchQuantity = quantity,
                referenceDoctype = "Stock Entry",
                referenceName = stockEntryId,
            )
        )
        ManufactureEntry(
            stockEntryId = stockEntryId,
            batchNumber = batchNumber,
            itemCode = fake.item.code,
            itemName = fake.item.name,
            quantity = quantity,
            uom = fake.item.uom,
            wholeNumber = fake.item.wholeNumber,
            productionDate = today,
            expiryDate = expiryDate,
            isFinished = isFinished,
        ).also { entries[requestId] = it }
    }
}
