package bg.butcher.pda.hardware

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.qs.wiget.PrintUtils

private const val TAG = "QsPdaHardware"

// The SDK resets the printer 2000 ms after initPrintUtils, which would undo label mode
private const val LABEL_SETUP_DELAY_MS = 2_500L

object QsPdaHardware {
    var available = false
        private set

    /** Once per process, on the main thread: a second call leaks a serial port and a reader thread. */
    fun init(app: Application) {
        Log.i(TAG, "Build.MODEL = ${Build.MODEL}")
        try {
            // Scans are off until a scanning screen turns them on
            PrintUtils.isCanScan = false
            PrintUtils.initPrintUtils(app.applicationContext)
            Handler(Looper.getMainLooper()).postDelayed({
                PrintUtils.labelEnable(true)
                PrintUtils.setStarNum(30)
            }, LABEL_SETUP_DELAY_MS)
            available = true
        } catch (e: LinkageError) {
            // The emulator, or a device without the vendor library
            Log.w(TAG, "Scanner/printer SDK not available", e)
        }
    }
}
