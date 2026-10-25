package bg.butcher.pda.hardware

import bg.butcher.pda.data.DEFAULT_LABEL_HEIGHT_MM
import bg.butcher.pda.data.PaperSize
import bg.butcher.pda.data.PaperType
import bg.butcher.pda.data.labelHeightRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelRendererTest {
    // Wider than "E: 28.10.2026" and "1000 Nos" at the smallest text size, from Roboto's character widths
    private val datesWidth = 160

    @Test
    fun qrCode_staysLargeOnBothPaperSizes() {
        PaperType.entries.forEach { type ->
            // The shortest label allowed, too
            listOf(DEFAULT_LABEL_HEIGHT_MM, labelHeightRange(type).first).forEach { mm ->
                PaperSize.entries.forEach { paper ->
                    val size = qrSize(paper.widthPx, labelRows(mm, type), datesWidth, paper.maxQrPx)
                    assertTrue("$paper $type at $mm mm gives a $size px QR code", size >= MIN_QR_SIZE)
                }
            }
        }
    }

    @Test
    fun qrCode_fillsTheHeightWhenThereIsRoom() {
        // 80 x 40 mm: limited by the height, not by the dates
        val rows = labelRows(40, PaperType.LABELS)
        val size = qrSize(PaperSize.MM_80.widthPx, rows, datesWidth)

        assertEquals(rows.label - 16, size)
    }

    @Test
    fun rows_areWholeBandsThatFitTheSdkBuffer() {
        PaperType.entries.forEach { type ->
            for (mm in labelHeightRange(type)) {
                val rows = labelRows(mm, type)
                assertEquals("$type at $mm mm", 0, rows.label % 24)
                assertEquals("$type at $mm mm", 0, rows.bitmap % 24)
                PaperSize.entries.forEach { paper ->
                    // What PrintUtils.draw2PxPoint writes, against what it allocates
                    val needed = 3 + rows.bitmap / 24 * (6 + 3 * paper.widthPx)
                    assertTrue("$paper $type at $mm mm", needed <= paper.widthPx * rows.bitmap / 8 + 1200)
                }
            }
        }
    }

    @Test
    fun dieCutLabels_stopShortOfTheGap() {
        for (mm in labelHeightRange(PaperType.LABELS)) {
            val rows = labelRows(mm, PaperType.LABELS)
            assertEquals(rows.label, rows.bitmap)
            assertTrue("$mm mm leaves too little margin", rows.bitmap <= mm * DOTS_PER_MM - 6 * DOTS_PER_MM)
        }
    }

    @Test
    fun roll_isTheLabelHeightWithABlankEnd() {
        for (mm in labelHeightRange(PaperType.CONTINUOUS)) {
            val rows = labelRows(mm, PaperType.CONTINUOUS)
            // To the nearest band, so within 1.5 mm
            assertTrue("$mm mm gives ${rows.bitmap} rows", kotlin.math.abs(rows.bitmap - mm * DOTS_PER_MM) <= 12)
            assertEquals(TEAR_OFF_ROWS, rows.bitmap - rows.label)
        }
    }

    @Test
    fun roll_leavesAsMuchRoomAsTheShortestDieCutLabel() {
        val roll = labelRows(labelHeightRange(PaperType.CONTINUOUS).first, PaperType.CONTINUOUS)
        val dieCut = labelRows(labelHeightRange(PaperType.LABELS).first, PaperType.LABELS)

        assertTrue("${roll.label} < ${dieCut.label}", roll.label >= dieCut.label)
    }

    @Test
    fun qrCode_isCappedOn58mmSoTheTextHasRoom() {
        val paper = PaperSize.MM_58
        val size = qrSize(paper.widthPx, labelRows(DEFAULT_LABEL_HEIGHT_MM, PaperType.LABELS), datesWidth, paper.maxQrPx)

        assertEquals(paper.maxQrPx, size)
        assertTrue("$size px", size >= MIN_QR_SIZE)
    }
}
