package bg.butcher.pda.ui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

class JobSlot(private val scope: CoroutineScope) {
    private var job: Job? = null

    val isActive: Boolean
        get() = job?.isActive ?: false

    fun replace(debounce: Duration = Duration.ZERO, block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(debounce)
            block()
        }
    }

    fun cancel() {
        job?.cancel()
    }
}