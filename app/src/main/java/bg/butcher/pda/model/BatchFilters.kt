package bg.butcher.pda.model

import kotlinx.serialization.Serializable

enum class BatchSort(val field: String, val defaultDescending: Boolean) {
    PRODUCED("manufacturing_date", defaultDescending = true),
    EXPIRES("expiry_date", defaultDescending = false),
    // The `item.item_name as item_name` alias; `tabItem`.item_name fails as an unknown column
    PRODUCT("item_name", defaultDescending = false),
}

/**
 * Which items' batches to list. "Made here" means the item has a default BOM; raw materials are bought in and have
 * none, e.g. a supplier's batch entered on a Material Receipt.
 */
enum class BatchKind { MANUFACTURED, RAW, ALL }

@Serializable
data class BatchFilters(
    val sort: BatchSort = BatchSort.PRODUCED,
    val descending: Boolean = BatchSort.PRODUCED.defaultDescending,
    // Text, so the field can be empty (no limit)
    val producedWithinDaysText: String = DEFAULT_DAYS.toString(),
    val hideExpired: Boolean = false,
    val kind: BatchKind = BatchKind.MANUFACTURED,
) {
    val producedWithinDays: Int? get() = producedWithinDaysText.trim().toIntOrNull()?.takeIf { it >= 0 }
    val isDefault: Boolean get() = this == BatchFilters()

    /** Picking a sort also picks its usual direction. */
    fun withSort(sort: BatchSort) = copy(sort = sort, descending = sort.defaultDescending)

    /**
     * Picking raw materials also clears the day limit: a bought-in batch often has no production date, and a date
     * limit leaves those out.
     */
    fun withKind(kind: BatchKind) =
        if (kind == BatchKind.RAW) copy(kind = kind, producedWithinDaysText = "") else copy(kind = kind)

    companion object {
        const val DEFAULT_DAYS = 30
    }
}
