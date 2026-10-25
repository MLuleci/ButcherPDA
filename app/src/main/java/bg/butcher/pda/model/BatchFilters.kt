package bg.butcher.pda.model

import kotlinx.serialization.Serializable

enum class BatchSort(val field: String, val defaultDescending: Boolean) {
    PRODUCED("manufacturing_date", defaultDescending = true),
    EXPIRES("expiry_date", defaultDescending = false),
    // The `item.item_name as item_name` alias; `tabItem`.item_name fails as an unknown column
    PRODUCT("item_name", defaultDescending = false),
}

@Serializable
data class BatchFilters(
    val sort: BatchSort = BatchSort.PRODUCED,
    val descending: Boolean = BatchSort.PRODUCED.defaultDescending,
    // Text, so the field can be empty (no limit)
    val producedWithinDaysText: String = DEFAULT_DAYS.toString(),
    val hideExpired: Boolean = false,
) {
    val producedWithinDays: Int? get() = producedWithinDaysText.trim().toIntOrNull()?.takeIf { it >= 0 }
    val isDefault: Boolean get() = this == BatchFilters()

    /** Picking a sort also picks its usual direction. */
    fun withSort(sort: BatchSort) = copy(sort = sort, descending = sort.defaultDescending)

    companion object {
        const val DEFAULT_DAYS = 30
    }
}
