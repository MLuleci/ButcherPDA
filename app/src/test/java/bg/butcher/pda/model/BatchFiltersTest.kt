package bg.butcher.pda.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchFiltersTest {
    @Test
    fun defaults() {
        val filters = BatchFilters()

        assertTrue(filters.isDefault)
        assertEquals(BatchSort.PRODUCED, filters.sort)
        assertTrue(filters.descending)
        assertEquals(30, filters.producedWithinDays)
        assertFalse(filters.hideExpired)
    }

    @Test
    fun producedWithinDays_acceptsWholeNonNegativeDays() {
        assertEquals(0, BatchFilters(producedWithinDaysText = "0").producedWithinDays)
        assertEquals(7, BatchFilters(producedWithinDaysText = " 7 ").producedWithinDays)
        assertNull(BatchFilters(producedWithinDaysText = "").producedWithinDays)
        assertNull(BatchFilters(producedWithinDaysText = "1.5").producedWithinDays)
        assertNull(BatchFilters(producedWithinDaysText = "-1").producedWithinDays)
    }

    @Test
    fun withSort_picksTheSortsUsualDirection() {
        val filters = BatchFilters().withSort(BatchSort.EXPIRES)

        assertEquals(BatchSort.EXPIRES, filters.sort)
        assertFalse(filters.descending)
        assertFalse(filters.isDefault)
        assertTrue(filters.withSort(BatchSort.PRODUCED).isDefault)
    }
}
