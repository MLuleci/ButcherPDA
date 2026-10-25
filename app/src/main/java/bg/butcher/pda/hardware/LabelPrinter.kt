package bg.butcher.pda.hardware

import android.util.Log
import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.util.formatDecimal
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import okhttp3.HttpUrl
import kotlin.time.Duration.Companion.milliseconds

data class LabelData(
    val batchNumber: String,
    val itemName: String,
    // Already formatted with its unit, e.g. "20.5 Kg"
    val quantity: String,
    val productionDate: LocalDate?,
    val expiryDate: LocalDate?,
    val stockEntryId: String?,
)

/** The batch's ERPNext page, which the PDA reduces back to the batch number when scanned (`parseScan`). */
fun batchUrl(baseUrl: HttpUrl, batchNumber: String): String =
    baseUrl.newBuilder()
        .encodedPath("/")
        .addPathSegments("app/batch")
        .addPathSegment(batchNumber)
        .build()
        .toString()

fun ManufactureEntry.toLabel() = LabelData(
    batchNumber = batchNumber,
    itemName = itemName,
    quantity = "${formatDecimal(quantity)} $uom",
    productionDate = productionDate,
    expiryDate = expiryDate,
    stockEntryId = stockEntryId,
)

/** From Details: the batch's current amount, in its stock unit. */
fun BatchDetails.toLabel() = LabelData(
    batchNumber = name,
    itemName = itemName,
    quantity = "${formatDecimal(batchQuantity ?: 0.0)} ${stockUom.orEmpty()}".trim(),
    productionDate = manufacturingDate,
    expiryDate = expiryDate,
    stockEntryId = referenceName,
)

fun interface LabelPrinter {
    suspend fun print(label: LabelData): Result<Unit>
}

class FakeLabelPrinter : LabelPrinter {
    override suspend fun print(label: LabelData): Result<Unit> {
        Log.i("FakeLabelPrinter", "Would print $label")
        delay(500.milliseconds)
        return Result.success(Unit)
    }
}
