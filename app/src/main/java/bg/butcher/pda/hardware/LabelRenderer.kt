package bg.butcher.pda.hardware

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import bg.butcher.pda.util.shortDate
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.ceil

// The printer's resolution: 384 dots across 48 mm of a 58 mm roll
const val DOTS_PER_MM = 8

private const val PADDING = 8
private const val GAP = 10

// About 6 mm: printing closer to the gap makes the printer feed an extra blank label
private const val BOTTOM_MARGIN = 6 * DOTS_PER_MM

// On a plain roll: blank paper after the label, to take it past the tear bar. The printer can't feed by itself
// there, since it has no gap to feed to
const val TEAR_OFF_ROWS = 12 * DOTS_PER_MM

// The SDK sends the picture in bands of 24 rows, and its buffer is too short for a partial last band on
// 570 px paper, which silently cuts the end of the job off (e.g. at 80 x 40 mm)
private const val BAND_ROWS = 24

private const val MAX_TEXT_SIZE = 28f
// About 2.5 mm tall; the dates are never printed smaller
private const val MIN_TEXT_SIZE = 20f
// About 18 mm, which a phone or the PDA reads at arm's length
const val MIN_QR_SIZE = 18 * DOTS_PER_MM

/** One-letter captions in the app's language, to tell the two dates apart. */
data class LabelCaptions(val produced: String, val expires: String)

// No anti-aliasing: the printer only prints black or white, so grey edges would come out ragged
private fun paint(size: Float, bold: Boolean = false) = TextPaint().apply {
    color = Color.BLACK
    textSize = size
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
}

private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

/** No character set hint: the URL is ASCII, and a UTF-8 hint would make the scan engine add an ECI prefix. */
private fun qrBitmap(content: String, size: Int): Bitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

private class TextBlock(val lines: List<StaticLayout>) {
    val height: Int get() = lines.sumOf { it.height }
}

/**
 * The product name (up to two lines), then one line per field. Only the name, batch number and Stock Entry ID
 * may be cut short; the caller checks the rest fit whole.
 */
private fun textBlock(label: LabelData, wholeLines: List<String>, width: Int, size: Float): TextBlock {
    fun line(text: String, bold: Boolean = false, maxLines: Int = 1) = layout(text, paint(size, bold), width, maxLines)
    return TextBlock(
        listOfNotNull(
            line(label.itemName, bold = true, maxLines = 2),
            line(label.batchNumber),
            *wholeLines.map { line(it) }.toTypedArray(),
            label.stockEntryId?.let { line(it) },
        )
    )
}

private fun widest(lines: List<String>, size: Float): Int =
    lines.maxOfOrNull { ceil(paint(size).measureText(it)).toInt() } ?: 0

/** Whole bands only, rounding down so the bottom margin only grows. */
fun labelBitmapHeight(labelHeightMm: Int): Int = (labelHeightMm * DOTS_PER_MM - BOTTOM_MARGIN) / BAND_ROWS * BAND_ROWS

/** [rows] rounded up to whole bands. */
fun feedHeight(rows: Int): Int = (rows + BAND_ROWS - 1) / BAND_ROWS * BAND_ROWS

/**
 * The QR code's side: what's left beside the dates at their smallest, up to the label's height and [maxSize].
 * [datesWidth] is the widest of the lines that must print whole.
 */
fun qrSize(width: Int, labelHeightMm: Int, datesWidth: Int, maxSize: Int = Int.MAX_VALUE): Int {
    val contentHeight = labelBitmapHeight(labelHeightMm) - 2 * PADDING
    return minOf(contentHeight, width - 2 * PADDING - GAP - datesWidth, maxSize).coerceAtLeast(0)
}

/**
 * The whole label as one bitmap, so it prints as one job with one feed, and Cyrillic needs no printer font.
 * The QR code is as large as the label allows on the left. The quantity and both dates always print whole beside
 * it, at the largest size that fits; the product name, batch number and Stock Entry ID may be cut short.
 * [feedRows] of blank paper follow the label.
 */
fun renderLabel(
    label: LabelData,
    captions: LabelCaptions,
    qrContent: String?,
    width: Int,
    labelHeightMm: Int,
    maxQrSize: Int = Int.MAX_VALUE,
    feedRows: Int = 0,
): Bitmap {
    val height = labelBitmapHeight(labelHeightMm)
    val contentHeight = height - 2 * PADDING
    val wholeLines = listOfNotNull(
        label.quantity,
        label.productionDate?.let { "${captions.produced}: ${shortDate(it)}" },
        label.expiryDate?.let { "${captions.expires}: ${shortDate(it)}" },
    )

    // Room for the dates at their smallest comes first; LabelRendererTest checks the QR code still clears
    // MIN_QR_SIZE on both paper sizes
    val qrSize = if (qrContent == null) 0 else {
        qrSize(width, labelHeightMm, widest(wholeLines, MIN_TEXT_SIZE), maxQrSize)
    }
    val textLeft = PADDING + if (qrSize > 0) qrSize + GAP else 0
    val textWidth = width - textLeft - PADDING

    var size = MAX_TEXT_SIZE
    var text = textBlock(label, wholeLines, textWidth, size)
    while ((text.height > contentHeight || widest(wholeLines, size) > textWidth) && size > MIN_TEXT_SIZE) {
        size -= 1f
        text = textBlock(label, wholeLines, textWidth, size)
    }

    val bitmap = Bitmap.createBitmap(width, height + feedHeight(feedRows), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    qrContent?.takeIf { qrSize > 0 }?.let {
        val top = PADDING + (contentHeight - qrSize) / 2
        canvas.drawBitmap(qrBitmap(it, qrSize), PADDING.toFloat(), top.toFloat(), null)
    }
    var top = PADDING + ((contentHeight - text.height) / 2).coerceAtLeast(0)
    text.lines.forEach { line ->
        canvas.save()
        canvas.translate(textLeft.toFloat(), top.toFloat())
        line.draw(canvas)
        canvas.restore()
        top += line.height
    }
    return bitmap
}
