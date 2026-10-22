package bg.butcher.pda.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BatchDetails(
    val name: String,
    val item: String,
    @SerialName("item_name")
    val itemName: String,
    val uom: String,
)
