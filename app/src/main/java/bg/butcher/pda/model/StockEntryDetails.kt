package bg.butcher.pda.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* The parts of a Stock Entry document that Details shows.
 * `create_manufacture_entry` adds one ingredient row per batch used.
 */

@Serializable
data class StockEntryRow(
    @SerialName("item_code")
    val itemCode: String,
    @SerialName("item_name")
    val itemName: String,
    @SerialName("qty")
    val quantity: Double,
    val uom: String,
    @SerialName("batch_no")
    val batchNumber: String? = null,
    // Check fields arrive as 0/1
    @SerialName("is_finished_item")
    val isFinishedItem: Int = 0,
)

@Serializable
data class StockEntryDetails(
    val name: String,
    @SerialName("posting_date")
    val postingDate: LocalDate,
    val items: List<StockEntryRow>,
) {
    val ingredients: List<StockEntryRow> get() = items.filter { it.isFinishedItem == 0 }
}
