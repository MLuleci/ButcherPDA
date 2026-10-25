package bg.butcher.pda.hardware

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanParserTest {
    private fun batch(number: String) = ScannedCode.Batch(number)

    @Test
    fun plainNumber_isABatch() {
        assertEquals(batch("BEEF.0001"), parseScan("BEEF.0001"))
        assertEquals(batch("BEEF.0001"), parseScan("  BEEF.0001\r\n"))
    }

    @Test
    fun batchUrl_givesTheBatchNumber() {
        assertEquals(batch("BEEF.0001"), parseScan("https://erp.example.bg/app/batch/BEEF.0001"))
        assertEquals(batch("BEEF.0001"), parseScan("HTTPS://erp.example.bg/app/batch/BEEF.0001/"))
        assertEquals(batch("BEEF.0001"), parseScan("https://erp.example.bg/app/batch/BEEF.0001?ref=label"))
        assertEquals(batch("BATCH/001"), parseScan("https://erp.example.bg/app/batch/BATCH%2F001"))
    }

    @Test
    fun eciDesignator_isRemoved() {
        assertEquals(batch("BEEF.0001"), parseScan("\\000026https://example.com/app/batch/BEEF.0001"))
        assertEquals(batch("BEEF.0001"), parseScan("\\000026BEEF.0001"))
        // Only a backslash and exactly six digits at the start
        assertEquals(batch("000026BEEF.0001"), parseScan("000026BEEF.0001"))
    }

    @Test
    fun otherUrls_areUnrecognised() {
        listOf(
            "https://erp.example.bg/app/item/BEEF",
            "https://erp.example.bg/app/batch",
            "https://supplier.example.com/",
            "https://",
        ).forEach { assertEquals(ScannedCode.Unrecognised(it), parseScan(it)) }
    }

    @Test
    fun emptyScan_isUnrecognised() {
        assertEquals(ScannedCode.Unrecognised("  "), parseScan("  "))
    }
}
