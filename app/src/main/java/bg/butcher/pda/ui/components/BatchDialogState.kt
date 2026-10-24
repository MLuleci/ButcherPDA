package bg.butcher.pda.ui.components

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BatchInfo(
    val batchNumber: String = "",
    val itemCode: String = "",
    val quantity: String = "",
    val uom: String = "",
)

// Scans skip the typing debounce
@Serializable
data class BatchInput(val text: String, val fromScan: Boolean = false)

@Serializable
sealed interface BatchDialogState {
    @Serializable
    data class Adding(val input: BatchInput) : BatchDialogState

    // Quantity only: no lookup, and scans are ignored
    @Serializable
    data class Editing(val batch: BatchInfo) : BatchDialogState
}

val BatchDialogStateSaver = Saver<BatchDialogState?, String>(
    save = { state -> state?.let { Json.encodeToString<BatchDialogState>(it) } },
    restore = { runCatching { Json.decodeFromString<BatchDialogState>(it) }.getOrNull() },
)
