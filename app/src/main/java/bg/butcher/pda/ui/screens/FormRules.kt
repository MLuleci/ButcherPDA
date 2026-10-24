package bg.butcher.pda.ui.screens

import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomDetailsItem
import bg.butcher.pda.model.DEFAULT_TOLERANCE_PERCENT
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.util.roundForErp
import bg.butcher.pda.util.validateQuantity
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

// Half a gram in kg; absorbs rounding in sums. Matches the server's `+ 0.0005`.
private const val EPSILON = 0.0005

// Whole-number units can't use a percentage: 10% of 2 onions is less than one onion
private const val WHOLE_NUMBER_MARGIN = 0.5

enum class Coverage { MISSING, UNDER, OK, OVER }

val FormUiState.bom: BomDetails?
    get() = (fetchState as? NetworkState.Success)?.data

/** What the BOM details request needs. */
val FormUiState.canFetch: Boolean
    get() = selectedItem != null &&
        validateQuantity(quantityText, selectedItem.isWholeNumber) == null

val FormUiState.canContinue: Boolean
    get() = canFetch && expiryDays != null

fun FormUiState.chosenQuantity(itemCode: String): Double = items[itemCode].orEmpty().values.sum()

// Frappe matches batch names case-insensitively
fun FormUiState.hasBatch(batchNumber: String): Boolean =
    items.values.any { batches -> batches.keys.any { it.equals(batchNumber, ignoreCase = true) } }

/** What's still needed of [ingredient], rounded for whole-number units. Null when nothing is. */
fun FormUiState.prefillQuantity(ingredient: BomDetailsItem): Double? {
    val remaining = ingredient.quantity - chosenQuantity(ingredient.itemCode)
    val amount = if (ingredient.wholeNumber) remaining.roundToLong().toDouble() else remaining.roundForErp()
    return amount.takeIf { it > 0.0 }
}

fun FormUiState.allowedRange(ingredient: BomDetailsItem): ClosedFloatingPointRange<Double> {
    val margin = if (ingredient.wholeNumber) {
        WHOLE_NUMBER_MARGIN
    } else {
        ingredient.quantity * (bom?.tolerancePercent ?: DEFAULT_TOLERANCE_PERCENT) / 100
    }
    return (ingredient.quantity - margin)..(ingredient.quantity + margin)
}

fun FormUiState.coverage(ingredient: BomDetailsItem): Coverage {
    val chosen = chosenQuantity(ingredient.itemCode)
    val range = allowedRange(ingredient)
    return when {
        chosen == 0.0 -> Coverage.MISSING
        chosen < range.start - EPSILON -> Coverage.UNDER
        chosen > range.endInclusive + EPSILON -> Coverage.OVER
        else -> Coverage.OK
    }
}

/** The whole amounts allowed for a whole-number ingredient. Never 0: a batch can't be empty. */
fun FormUiState.wholeNumberChoices(ingredient: BomDetailsItem): LongRange {
    val range = allowedRange(ingredient)
    return maxOf(1L, ceil(range.start - EPSILON).toLong())..floor(range.endInclusive + EPSILON).toLong()
}

/** Whole-number ingredients the run is too small for: no amount can be submitted. */
val FormUiState.unworkableIngredients: List<BomDetailsItem>
    get() = bom?.items.orEmpty().filter { it.wholeNumber && wholeNumberChoices(it).isEmpty() }

val FormUiState.canSubmit: Boolean
    get() = expiryDays != null &&
        bom?.items?.all { coverage(it) == Coverage.OK } == true &&
        submitState !is NetworkState.Loading &&
        submitState !is NetworkState.Success
