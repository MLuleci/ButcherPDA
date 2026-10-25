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

    // Gap mode until the saved paper type is read; it can only be sent once the SDK's reset is done
    private var labelMode = true
    private var isSetUp = false

    /** On the main thread. On for die-cut labels, off for a plain roll, where looking for the gap runs paper out. */
    fun setLabelMode(enabled: Boolean) {
        labelMode = enabled
        if (isSetUp) PrintUtils.labelEnable(enabled)
    }

    /** Once per process, on the main thread: a second call leaks a serial port and a reader thread. */
    fun init(app: Application) {
        Log.i(TAG, "Build.MODEL = ${Build.MODEL}")
        try {
            // Scans are off until a scanning screen turns them on
            PrintUtils.isCanScan = false
            PrintUtils.initPrintUtils(app.applicationContext)
            // The SDK fails silently when the port is busy, e.g. still held by the previous process
            Log.i(TAG, "Serial port fd = ${PrintUtils.mComFd}")
            Handler(Looper.getMainLooper()).postDelayed({
                isSetUp = true
                PrintUtils.labelEnable(labelMode)
                PrintUtils.setStarNum(30)
            }, LABEL_SETUP_DELAY_MS)
            available = true
        } catch (e: LinkageError) {
            // The emulator, or a device without the vendor library
            Log.w(TAG, "Scanner/printer SDK not available", e)
        }
    }
}
