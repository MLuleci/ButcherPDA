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
                    val size = qrSize(printArea(paper.widthMm).width, labelRows(mm, type), datesWidth, paper.maxQrPx)
                    assertTrue("$paper $type at $mm mm gives a $size px QR code", size >= MIN_QR_SIZE)
                }
            }
        }
    }

    @Test
    fun qrCode_fillsTheHeightWhenThereIsRoom() {
        // 80 x 40 mm: limited by the height, not by the dates
        val rows = labelRows(40, PaperType.LABELS)
        val size = qrSize(printArea(PaperSize.MM_80.widthMm).width, rows, datesWidth)

        assertEquals(rows.label - 16, size)
    }

    @Test
    fun rows_areWholeBandsThatFitTheSdkBuffer() {
        PaperType.entries.forEach { type ->
            for (mm in labelHeightRange(type)) {
                val rows = labelRows(mm, type)
                assertEquals("$type at $mm mm", 0, rows.label % 24)
                assertEquals("$type at $mm mm", 0, rows.bitmap % 24)
                // What PrintUtils.draw2PxPoint writes, against what it allocates; every picture is the head's width
                val needed = 3 + rows.bitmap / 24 * (6 + 3 * HEAD_DOTS)
                assertTrue("$type at $mm mm", needed <= HEAD_DOTS * rows.bitmap / 8 + 1200)
            }
        }
    }

    @Test
    fun dieCutLabels_stopShortOfTheGap() {
        for (mm in labelHeightRange(PaperType.LABELS)) {
            val rows = labelRows(mm, PaperType.LABELS)
            assertEquals(rows.label, rows.bitmap)
            // The vendor's 5-7 mm, after the picture's 2 mm shift down in label mode
            assertTrue("$mm mm leaves too little margin", rows.bitmap <= mm * DOTS_PER_MM - 8 * DOTS_PER_MM)
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
        val rows = labelRows(DEFAULT_LABEL_HEIGHT_MM, PaperType.LABELS)
        val size = qrSize(printArea(paper.widthMm).width, rows, datesWidth, paper.maxQrPx)

        assertEquals(paper.maxQrPx, size)
        assertTrue("$size px", size >= MIN_QR_SIZE)
    }

    @Test
    fun printArea_80mmIsTheWholeHead() {
        assertEquals(PrintArea(0, HEAD_DOTS), printArea(80))
    }

    @Test
    fun printArea_58mmStartsAtTheRollsLeftEdge() {
        // Measured with the test ruler: the roll, pushed right, starts at about 17 mm on the 71 mm picture
        val area = printArea(58)

        assertEquals(HEAD_DOTS, area.right)
        val paperEdgeMm = area.left / DOTS_PER_MM.toDouble() - 1
        assertTrue("$paperEdgeMm mm", paperEdgeMm in 17.0..18.0)
        // 53 mm of label to print on, against 46 mm when the picture was 384 dots, centred
        assertTrue("${area.width} dots", area.width >= 52 * DOTS_PER_MM)
    }
}
