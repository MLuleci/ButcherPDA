package bg.butcher.pda.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* This data class describes the response from the Frappe
 * REST API listing documents for the BOM doctype.
 * The returned fields are required to be part of the
 * request (see `MyAppApiService` definition).
 */

@Serializable
data class BomSearchResultItem(
    val name: String, // BOM's ID
    val item: String, // Item's ID
    @SerialName("item_name")
    val itemName: String, // Item's display name
    val uom: String, // Item's UOM
    @SerialName("is_default")
    val isDefault: Int, // Actually a Boolean
)

@Serializable
data class BomSearchResult(
    val data: List<BomSearchResultItem>
)
