package bg.butcher.pda.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* This data class describes the response from the API
 * in response to a get_bom_details(...) request.
 * See: https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L64
 */

const val DEFAULT_TOLERANCE_PERCENT = 5.0

@Serializable
data class BomDetailsItem (
    @SerialName(value = "qty")
    val quantity: Double,
    @SerialName(value = "item_code")
    val itemCode: String,
    @SerialName(value = "item_name")
    val itemName: String,
    val uom: String,
    @SerialName(value = "stock_uom")
    val stockUom: String,
    @SerialName(value = "conversion_factor")
    val conversionFactor: Double,
    @SerialName(value = "must_be_whole_number")
    val wholeNumber: Boolean = false,
)

@Serializable
data class BomDetails(
    val name: String,
    @SerialName(value = "qty")
    val quantity: Double,
    @SerialName(value = "item_code")
    val itemCode: String,
    @SerialName(value = "item_name")
    val itemName: String,
    val uom: String,
    @SerialName(value = "stock_uom")
    val stockUom: String,
    @SerialName(value = "shelf_life_in_days")
    val shelfLife: Int,
    val items: List<BomDetailsItem>,
    @SerialName(value = "tolerance_percent")
    val tolerancePercent: Double = DEFAULT_TOLERANCE_PERCENT,
    @SerialName(value = "must_be_whole_number")
    val wholeNumber: Boolean = false,
)
