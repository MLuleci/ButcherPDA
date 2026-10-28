package bg.butcher.pda.hardware

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

// A tick every millimetre; longer every 5 mm, and numbered every 10 mm across or 5 mm down
private const val TICK_MM = 2
private const val TICK_5MM = 4
private const val TICK_10MM = 6

/**
 * A ruler printout for finding where the paper sits under the print head: millimetres across the whole picture
 * from its left edge, and down both sides from its top, with the picture's outline and its centre marked. A
 * temporary calibration aid, not part of the labels.
 */
fun renderCalibration(width: Int, rows: LabelRows): Bitmap {
    val height = rows.bitmap
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    // No anti-aliasing: the printer only prints black or white
    val line = Paint().apply { color = Color.BLACK; strokeWidth = 1f }
    val text = Paint().apply {
        color = Color.BLACK
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
    }
    val mm = DOTS_PER_MM.toFloat()

    // The picture's outline, so its edges show wherever they land
    canvas.drawRect(0f, 0f, width - 1f, height - 1f, line.apply { style = Paint.Style.STROKE })
    line.style = Paint.Style.FILL

    // Across: ticks down from the top edge, numbered from the left
    var x = 0
    while (x * DOTS_PER_MM < width) {
        val px = x * mm
        val length = when {
            x % 10 == 0 -> TICK_10MM
            x % 5 == 0 -> TICK_5MM
            else -> TICK_MM
        } * mm
        canvas.drawLine(px, 0f, px, length, line)
        if (x % 10 == 0 && x > 0) {
            val label = x.toString()
            canvas.drawText(label, px - text.measureText(label) / 2, TICK_10MM * mm + 16f, text)
        }
        x++
    }

    // Down: ticks in from both side edges, numbered from the top, so either side shows if the other is cut off
    var y = 0
    while (y * DOTS_PER_MM < height) {
        val py = y * mm
        val length = when {
            y % 5 == 0 -> TICK_10MM
            else -> TICK_MM
        } * mm
        canvas.drawLine(0f, py, length, py, line)
        canvas.drawLine(width - 1f - length, py, width - 1f, py, line)
        if (y % 5 == 0 && y > 0) {
            val label = y.toString()
            canvas.drawText(label, length + 4f, py + 6f, text)
            canvas.drawText(label, width - 1f - length - 4f - text.measureText(label), py + 6f, text)
        }
        y++
    }

    // The centre, to see whether the picture is centred on the head
    val centre = width / 2f
    canvas.drawLine(centre, height * 0.35f, centre, height * 0.65f, line)
    val size = "$width px · ${width / DOTS_PER_MM} mm × ${height / DOTS_PER_MM} mm"
    canvas.drawText(size, centre - text.measureText(size) / 2, height * 0.35f - 8f, text)
    return bitmap
}
