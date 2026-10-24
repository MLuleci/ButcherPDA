package bg.butcher.pda.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* This data class describes the response from the Frappe
 * REST API listing documents for the BOM doctype.
 * The returned fields are required to be part of the
 * request (see `NetworkErpRepository.searchBom`).
 */

@Serializable
data class BomSearchResultItem(
    val name: String, // BOM's ID
    val item: String, // Item's ID
    @SerialName("item_name")
    val itemName: String, // Item's display name
    val uom: String, // Item's UOM
    // Check fields arrive as 0/1 from the list API
    @SerialName("is_default")
    val isDefault: Int,
    @SerialName("shelf_life_in_days")
    val shelfLife: Int? = null,
    @SerialName("must_be_whole_number")
    val wholeNumber: Int = 0,
) {
    val isDefaultBom: Boolean get() = isDefault == 1
    val isWholeNumber: Boolean get() = wholeNumber == 1
}
