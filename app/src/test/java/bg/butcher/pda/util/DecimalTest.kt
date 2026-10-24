package bg.butcher.pda.util

import bg.butcher.pda.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecimalTest {
    @Test
    fun parseDecimal_acceptsDotAndComma() {
        assertEquals(2.5, parseDecimal("2.5")!!, 0.0)
        assertEquals(2.5, parseDecimal("2,5")!!, 0.0)
        assertEquals(25.0, parseDecimal(" 25 ")!!, 0.0)
        assertEquals(-1.0, parseDecimal("-1")!!, 0.0)
    }

    @Test
    fun parseDecimal_rejectsInvalid() {
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal("1.2.3"))
        assertNull(parseDecimal("NaN"))
        assertNull(parseDecimal("Infinity"))
    }

    @Test
    fun formatDecimal_usesDotWithoutTrailingZeros() {
        assertEquals("1.25", formatDecimal(1.25))
        assertEquals("25", formatDecimal(25.0))
        assertEquals("0.04", formatDecimal(0.04))
        assertEquals("100", formatDecimal(100.0))
        assertEquals("0.3", formatDecimal(0.1 + 0.2))
    }

    @Test
    fun formatDecimal_neverUsesExponent() {
        assertEquals("1234567", formatDecimal(1234567.0))
        assertEquals("0.001", formatDecimal(0.001))
        assertEquals("0", formatDecimal(1.0E-4))
    }

    @Test
    fun formatDecimal_zero() {
        assertEquals("0", formatDecimal(0.0))
        assertEquals("0", formatDecimal(-0.0001))
    }

    @Test
    fun formatDecimal_maxDecimals() {
        assertEquals("1.3", formatDecimal(1.25, maxDecimals = 1))
        assertEquals("2", formatDecimal(1.5, maxDecimals = 0))
    }

    @Test
    fun roundForErp_roundsToThreeDecimals() {
        assertEquals(0.3, (0.1 + 0.2).roundForErp(), 0.0)
        assertEquals(1.001, 1.0005.roundForErp(), 0.0)
        assertEquals(1.234, 1.2344.roundForErp(), 0.0)
        assertEquals(25.0, 25.0.roundForErp(), 0.0)
    }

    @Test
    fun validateQuantity_withoutWholeNumber() {
        assertNull(validateQuantity("25", wholeNumber = false))
        assertNull(validateQuantity("25.0", wholeNumber = false))
        assertNull(validateQuantity("2.5", wholeNumber = false))
        assertNull(validateQuantity("2,5", wholeNumber = false))
        assertEquals(R.string.must_be_positive, validateQuantity("0", wholeNumber = false))
        assertEquals(R.string.must_be_positive, validateQuantity("-1", wholeNumber = false))
        assertEquals(R.string.value_required, validateQuantity("", wholeNumber = false))
        assertEquals(R.string.value_required, validateQuantity("  ", wholeNumber = false))
        assertEquals(R.string.invalid_number, validateQuantity("abc", wholeNumber = false))
    }

    @Test
    fun validateQuantity_withWholeNumber() {
        assertNull(validateQuantity("25", wholeNumber = true))
        assertNull(validateQuantity("25.0", wholeNumber = true))
        assertEquals(R.string.whole_number_required, validateQuantity("2.5", wholeNumber = true))
        assertEquals(R.string.whole_number_required, validateQuantity("2,5", wholeNumber = true))
        assertEquals(R.string.must_be_positive, validateQuantity("0", wholeNumber = true))
        assertEquals(R.string.must_be_positive, validateQuantity("-1", wholeNumber = true))
        assertEquals(R.string.value_required, validateQuantity("", wholeNumber = true))
    }
}
