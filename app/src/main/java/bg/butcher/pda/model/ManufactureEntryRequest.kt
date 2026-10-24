package bg.butcher.pda.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ManufactureEntryRequest(
    val bom: String,
    @SerialName("qty")
    val quantity: Double,
    // item_code -> batch_no -> qty
    val items: Map<String, Map<String, Double>>,
    @SerialName("expiry_date")
    val expiryDate: LocalDate,
    val explode: Boolean,
    @SerialName("is_finished")
    val isFinished: Boolean,
    @SerialName("request_id")
    val requestId: String,
)
