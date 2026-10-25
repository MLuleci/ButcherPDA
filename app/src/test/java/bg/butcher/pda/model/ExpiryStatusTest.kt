package bg.butcher.pda.model

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpiryStatusTest {
    private val today = LocalDate(2026, 10, 25)

    @Test
    fun eachBoundary() {
        assertEquals(ExpiryStatus.UNKNOWN, expiryStatus(null, today))
        assertEquals(ExpiryStatus.EXPIRED, expiryStatus(LocalDate(2026, 10, 24), today))
        assertEquals(ExpiryStatus.TODAY, expiryStatus(today, today))
        assertEquals(ExpiryStatus.SOON, expiryStatus(LocalDate(2026, 10, 26), today))
        assertEquals(ExpiryStatus.OK, expiryStatus(LocalDate(2026, 10, 27), today))
    }

    @Test
    fun soonDays_widensSoon() {
        assertEquals(ExpiryStatus.SOON, expiryStatus(LocalDate(2026, 10, 28), today, soonDays = 3))
        assertEquals(ExpiryStatus.OK, expiryStatus(LocalDate(2026, 10, 29), today, soonDays = 3))
    }

    @Test
    fun monthAndYearEnds() {
        assertEquals(ExpiryStatus.SOON, expiryStatus(LocalDate(2027, 1, 1), LocalDate(2026, 12, 31)))
        assertEquals(ExpiryStatus.EXPIRED, expiryStatus(LocalDate(2026, 10, 31), LocalDate(2026, 11, 1)))
    }
}
