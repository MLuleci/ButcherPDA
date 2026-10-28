package bg.butcher.pda.hardware

/*
 * The PDA-408's printer, as measured with the test ruler:
 * - the head prints 570 dots (71 mm), centred in an 80 mm bay; the SDK sends the picture at that width and the
 *   printer centres it there;
 * - the gap sensor is on the right of the bay, so a roll has to be pushed against the right edge, or the printer
 *   reports no paper.
 * So a roll narrower than the bay covers the head only from its own left edge to the head's right end, and a
 * picture drawn there lands on the paper.
 */

/** The head's width in dots, which every picture is sent at. */
const val HEAD_DOTS = 570

private const val BAY_MM = 80

/** The head's left end, measured from the bay's left edge. */
private val HEAD_LEFT_MM = (BAY_MM - HEAD_DOTS.toDouble() / DOTS_PER_MM) / 2

// Kept blank inside the paper's edge on the left, where the label meets its backing
private const val EDGE_MARGIN_DOTS = 1 * DOTS_PER_MM

/** The columns of a [HEAD_DOTS]-wide picture that land on the paper, from [left] to [left] + [width]. */
data class PrintArea(val left: Int, val width: Int) {
    val right: Int get() = left + width
}

/**
 * Where a roll [paperWidthMm] wide, pushed against the bay's right edge, lies under the head. On a roll as wide
 * as the head or wider, that's the whole picture; on a narrower one, the part right of its left edge, less a
 * margin.
 */
fun printArea(paperWidthMm: Int): PrintArea {
    val paperLeftDots = ((BAY_MM - paperWidthMm - HEAD_LEFT_MM) * DOTS_PER_MM).toInt()
    if (paperLeftDots <= 0) return PrintArea(0, HEAD_DOTS)
    val left = (paperLeftDots + EDGE_MARGIN_DOTS).coerceAtMost(HEAD_DOTS)
    return PrintArea(left, HEAD_DOTS - left)
}
