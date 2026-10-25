package bg.butcher.pda.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.qs.wiget.PrintUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

sealed interface ScanEvent {
    data class Code(val text: String) : ScanEvent

    /** The trigger was pressed, but the scanner read nothing. */
    data object NoRead : ScanEvent
}

interface BarcodeScanner {
    val events: Flow<ScanEvent>

    /**
     * Whether the hardware trigger fires the scanner at all; only scanning screens turn it on.
     * Calls are counted, since two screens can both be started during a transition.
     */
    fun setActive(active: Boolean)
}

// Sent by the vendor SDK for each scan, with the text in "data"
private const val SCAN_ACTION = "com.qs.scancode"
private const val SCAN_EXTRA = "data"

// Sent by the firmware when the trigger is pressed
private const val TRIGGER_ACTION = "ismart.intent.scandown"

// The SDK powers the scan engine down 3 s after the trigger and drops its "nothing read" reply,
// so a press with no code by then is a failed read
private val NO_READ_TIMEOUT = 3500.milliseconds

class BroadcastBarcodeScanner(private val context: Context) : BarcodeScanner {
    private var activeCount = 0

    override val events: Flow<ScanEvent> = callbackFlow {
        var noRead: Job? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                noRead?.cancel()
                when (intent.action) {
                    TRIGGER_ACTION -> noRead = launch {
                        delay(NO_READ_TIMEOUT)
                        trySend(ScanEvent.NoRead)
                    }
                    SCAN_ACTION -> intent.getStringExtra(SCAN_EXTRA)?.takeIf { it.isNotBlank() }
                        ?.let { trySend(ScanEvent.Code(it)) }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(SCAN_ACTION)
            addAction(TRIGGER_ACTION)
        }
        // Exported: both broadcasts are implicit and come from outside the app
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    override fun setActive(active: Boolean) {
        activeCount = (activeCount + if (active) 1 else -1).coerceAtLeast(0)
        PrintUtils.isCanScan = activeCount > 0
    }
}

/** For the emulator and tests: [scan] and [noRead] stand in for the hardware trigger. */
class FakeBarcodeScanner : BarcodeScanner {
    private val _events = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 1)
    override val events = _events.asSharedFlow()

    private var activeCount = 0
    val isActive: Boolean get() = activeCount > 0

    override fun setActive(active: Boolean) {
        activeCount = (activeCount + if (active) 1 else -1).coerceAtLeast(0)
    }

    fun scan(code: String) {
        if (isActive) _events.tryEmit(ScanEvent.Code(code))
    }

    fun noRead() {
        if (isActive) _events.tryEmit(ScanEvent.NoRead)
    }
}
