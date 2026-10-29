package bg.butcher.pda.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import bg.butcher.pda.R
import bg.butcher.pda.data.PaperType
import bg.butcher.pda.data.PdaSettings
import bg.butcher.pda.data.normaliseBaseUrl
import com.qs.wiget.PrintUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

// Sent by the SDK when the printer reports its state, with an int "message"
private const val STATUS_ACTION = "com.qs.getmassage"
private const val STATUS_EXTRA = "message"
private const val STATUS_NO_PAPER = 1

class PrinterNotReadyException : Exception("Printer not ready")

class QsPdaLabelPrinter(
    private val context: Context,
    private val settings: suspend () -> PdaSettings,
) : LabelPrinter {
    override suspend fun print(label: LabelData): Result<Unit> {
        if (PrintUtils.mComFd <= 0) return Result.failure(PrinterNotReadyException())

        val captions = LabelCaptions(
            produced = context.getString(R.string.label_caption_produced),
            expires = context.getString(R.string.label_caption_expires),
        )
        // The SDK only scales down bitmaps wider than the printer, so this is printed as drawn
        val current = settings()
        val url = normaliseBaseUrl(current.baseUrl)?.let { batchUrl(it, label.batchNumber) }
        // On a plain roll the bitmap is the whole label, instead of the printer feeding to the gap
        val continuous = current.paperType == PaperType.CONTINUOUS
        val bitmap = withContext(Dispatchers.Default) {
            val paper = current.paperSize
            val rows = labelRows(current.labelHeightMm, current.paperType)
            renderLabel(label, captions, url, printArea(paper.widthMm), rows, paper.maxQrPx)
        }
        return send(bitmap, continuous)
    }

    /**
     * The full width of the head, whatever the paper size, at the label height and paper type in Settings, so it
     * shows where a label's picture would fall.
     */
    override suspend fun printCalibration(): Result<Unit> {
        if (PrintUtils.mComFd <= 0) return Result.failure(PrinterNotReadyException())
        val current = settings()
        val bitmap = withContext(Dispatchers.Default) {
            renderCalibration(HEAD_DOTS, labelRows(current.labelHeightMm, current.paperType))
        }
        return send(bitmap, current.paperType == PaperType.CONTINUOUS)
    }

    private suspend fun send(bitmap: Bitmap, continuous: Boolean): Result<Unit> {
        // The print call returns at once; the only result is a status broadcast, which may not come
        val status = withTimeoutOrNull(4.seconds) {
            callbackFlow {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        trySend(intent.getIntExtra(STATUS_EXTRA, -1))
                    }
                }
                ContextCompat.registerReceiver(
                    context, receiver, IntentFilter(STATUS_ACTION), ContextCompat.RECEIVER_EXPORTED,
                )
                // Its handlers need the main looper
                withContext(Dispatchers.Main) {
                    // The SDK only learns the printer's state from what the printer happens to send, and never asks.
                    // After the lid is opened it's left at "no paper" until the printer reports something else, which
                    // closing the lid doesn't do, and the SDK silently drops jobs while it says so. Clearing it lets
                    // the job through; with no paper the printer reports that again, and the broadcast says so.
                    PrintUtils.isCanprint = true
                    PrintUtils.printBitmap(1, bitmap, !continuous)
                }
                awaitClose { context.unregisterReceiver(receiver) }
            }.first()
        }
        return if (status == STATUS_NO_PAPER) Result.failure(PrinterNotReadyException()) else Result.success(Unit)
    }
}
