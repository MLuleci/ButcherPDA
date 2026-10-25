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

/**
 * The batch field's text and where it came from. Scans skip the typing debounce, and each has its
 * own [scanId], so scanning the same label again (e.g. after a network error) looks it up again.
 */
@Serializable
data class BatchInput(
    val text: String,
    val fromScan: Boolean = false,
    val scanId: Long = 0,
    // A scan that isn't a batch label; shown as an error without a lookup
    val unrecognised: Boolean = false,
)

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
