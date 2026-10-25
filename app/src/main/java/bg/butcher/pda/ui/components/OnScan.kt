package bg.butcher.pda.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import bg.butcher.pda.MainApplication

/**
 * Receives hardware scans while this route is on screen and [enabled]. Routes that don't call it
 * never see scans, and the trigger does nothing there.
 */
@Composable
fun OnScan(enabled: Boolean = true, onScan: (String) -> Unit) {
    val scanner = (LocalContext.current.applicationContext as MainApplication).container.barcodeScanner
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScan by rememberUpdatedState(onScan)
    LaunchedEffect(scanner, lifecycleOwner, enabled) {
        if (!enabled) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            scanner.setActive(true)
            try {
                scanner.scans.collect { currentOnScan(it) }
            } finally {
                scanner.setActive(false)
            }
        }
    }
}
