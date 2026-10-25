package bg.butcher.pda.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BatchDetails(
    val name: String,
    val item: String,
    @SerialName("item_name")
    val itemName: String,
    @SerialName("stock_uom")
    val stockUom: String? = null,
    @SerialName("manufacturing_date")
    val manufacturingDate: LocalDate? = null,
    @SerialName("expiry_date")
    val expiryDate: LocalDate? = null,
    @SerialName("batch_qty")
    val batchQuantity: Double? = null,
    @SerialName("reference_doctype")
    val referenceDoctype: String? = null,
    @SerialName("reference_name")
    val referenceName: String? = null,
    // Only in list requests, through the item link. Set for items made here.
    @SerialName("default_bom")
    val defaultBom: String? = null,
)
