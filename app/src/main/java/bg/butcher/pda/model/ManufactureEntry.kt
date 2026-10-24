package bg.butcher.pda.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* This data class describes the response from the API
 * in response to a create_manufacture_entry(...) request.
 * See: https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L138
 */

@Serializable
data class ManufactureEntry(
    @SerialName(value = "name")
    val stockEntryId: String,
    @SerialName(value = "batch_no")
    val batchNumber: String,
    @SerialName(value = "item_code")
    val itemCode: String,
    @SerialName(value = "item_name")
    val itemName: String,
    @SerialName(value = "qty")
    val quantity: Double,
    val uom: String,
    @SerialName(value = "must_be_whole_number")
    val wholeNumber: Boolean = false,
    @SerialName(value = "production_date")
    val productionDate: LocalDate,
    @SerialName(value = "expiry_date")
    val expiryDate: LocalDate,
    @SerialName(value = "is_finished")
    val isFinished: Boolean
)
