package bg.butcher.pda.hardware

import android.util.Log
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.util.formatDecimal
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
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

fun ManufactureEntry.toLabel() = LabelData(
    batchNumber = batchNumber,
    itemName = itemName,
    quantity = "${formatDecimal(quantity)} $uom",
    productionDate = productionDate,
    expiryDate = expiryDate,
    stockEntryId = stockEntryId,
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
