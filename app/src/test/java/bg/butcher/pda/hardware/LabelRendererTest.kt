package bg.butcher.pda.hardware

import bg.butcher.pda.data.DEFAULT_LABEL_HEIGHT_MM
import bg.butcher.pda.data.LABEL_HEIGHT_RANGE_MM
import bg.butcher.pda.data.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelRendererTest {
    // Wider than "E: 28.10.2026" and "1000 Nos" at the smallest text size, from Roboto's character widths
    private val datesWidth = 160

    @Test
    fun qrCode_staysLargeOnBothPaperSizes() {
        PaperSize.entries.forEach { paper ->
            val size = qrSize(paper.widthPx, DEFAULT_LABEL_HEIGHT_MM, datesWidth, paper.maxQrPx)
            assertTrue("$paper gives a $size px QR code", size >= MIN_QR_SIZE)
        }
    }

    @Test
    fun qrCode_fillsTheHeightWhenThereIsRoom() {
        // 80 x 40 mm: limited by the height, not by the dates
        val size = qrSize(PaperSize.MM_80.widthPx, 40, datesWidth)

        assertEquals(labelBitmapHeight(40) - 16, size)
    }

    @Test
    fun bitmapHeight_isWholeBandsThatFitTheSdkBuffer() {
        for (mm in LABEL_HEIGHT_RANGE_MM) {
            val height = labelBitmapHeight(mm)
            assertEquals("$mm mm", 0, height % 24)
            PaperSize.entries.forEach { paper ->
                // What PrintUtils.draw2PxPoint writes, against what it allocates
                val needed = 3 + height / 24 * (6 + 3 * paper.widthPx)
                assertTrue("$paper at $mm mm", needed <= paper.widthPx * height / 8 + 1200)
            }
            assertTrue("$mm mm leaves too little margin", height <= mm * DOTS_PER_MM - 6 * DOTS_PER_MM)
        }
    }

    @Test
    fun qrCode_isCappedOn58mmSoTheTextHasRoom() {
        val paper = PaperSize.MM_58
        val size = qrSize(paper.widthPx, DEFAULT_LABEL_HEIGHT_MM, datesWidth, paper.maxQrPx)

        assertEquals(paper.maxQrPx, size)
        assertTrue("$size px", size >= MIN_QR_SIZE)
    }
}

