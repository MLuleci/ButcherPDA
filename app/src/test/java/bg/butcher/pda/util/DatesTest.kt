package bg.butcher.pda.util

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DatesTest {
    @Test
    fun shortDate_isDayMonthYearWithLeadingZeros() {
        assertEquals("30.10.2026", shortDate(LocalDate(2026, 10, 30)))
        assertEquals("01.02.2027", shortDate(LocalDate(2027, 2, 1)))
    }
}
