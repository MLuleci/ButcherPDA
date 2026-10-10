package bg.butcher.pda.network

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

    fun launchIfIdle(block: suspend CoroutineScope.() -> Unit) {
        if (isActive) return
        job = scope.launch(block = block)
    }

    suspend fun join() {
        job?.join()
    }

    fun cancel() {
        job?.cancel()
    }
}