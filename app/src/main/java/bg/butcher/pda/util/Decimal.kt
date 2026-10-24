package bg.butcher.pda.util

import androidx.annotation.StringRes
import bg.butcher.pda.R
import java.math.BigDecimal
import java.math.RoundingMode

fun parseDecimal(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

fun formatDecimal(value: Double, maxDecimals: Int = 3): String {
    val rounded = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP)
    // Android's stripTrailingZeros() leaves zero as "0.000"
    if (rounded.signum() == 0) return "0"
    return rounded.stripTrailingZeros().toPlainString()
}

fun Double.roundForErp(): Double =
    BigDecimal.valueOf(this).setScale(3, RoundingMode.HALF_UP).toDouble()

@StringRes
fun validateQuantity(text: String, wholeNumber: Boolean): Int? {
    if (text.isBlank()) return R.string.value_required
    val value = parseDecimal(text) ?: return R.string.invalid_number
    if (value <= 0.0) return R.string.must_be_positive
    if (wholeNumber && value % 1.0 != 0.0) return R.string.whole_number_required
    return null
}
