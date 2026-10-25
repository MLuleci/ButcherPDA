package bg.butcher.pda.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.qs.wiget.PrintUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow

interface BarcodeScanner {
    val scans: Flow<String>

    /**
     * Whether the hardware trigger fires the scanner at all; only scanning screens turn it on.
     * Calls are counted, since two screens can both be started during a transition.
     */
    fun setActive(active: Boolean)
}

// Sent by the vendor SDK for each scan, with the text in "data"
private const val SCAN_ACTION = "com.qs.scancode"
private const val SCAN_EXTRA = "data"

class BroadcastBarcodeScanner(private val context: Context) : BarcodeScanner {
    private var activeCount = 0

    override val scans: Flow<String> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.getStringExtra(SCAN_EXTRA)?.takeIf { it.isNotBlank() }?.let { trySend(it) }
            }
        }
        // Exported: the SDK's broadcast is implicit and global
        ContextCompat.registerReceiver(context, receiver, IntentFilter(SCAN_ACTION), ContextCompat.RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    override fun setActive(active: Boolean) {
        activeCount = (activeCount + if (active) 1 else -1).coerceAtLeast(0)
        PrintUtils.isCanScan = activeCount > 0
    }
}

/** For the emulator and tests: [scan] stands in for the hardware trigger. */
class FakeBarcodeScanner : BarcodeScanner {
    private val _scans = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val scans = _scans.asSharedFlow()

    private var activeCount = 0
    val isActive: Boolean get() = activeCount > 0

    override fun setActive(active: Boolean) {
        activeCount = (activeCount + if (active) 1 else -1).coerceAtLeast(0)
    }

    fun scan(code: String) {
        if (isActive) _scans.tryEmit(code)
    }
}
