package bg.butcher.pda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import bg.butcher.pda.R
import bg.butcher.pda.model.BomDetailsItem
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.screens.BatchLookupError
import bg.butcher.pda.ui.screens.BatchMatch
import bg.butcher.pda.util.formatDecimal
import bg.butcher.pda.util.parseDecimal
import bg.butcher.pda.util.validateQuantity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds

@Composable
private fun lookupErrorText(error: BatchLookupError): String = when (error) {
    BatchLookupError.InvalidInput, BatchLookupError.NotFound -> stringResource(R.string.invalid_batch_no)
    BatchLookupError.AlreadyAdded -> stringResource(R.string.batch_already_added)
    BatchLookupError.NotABatchLabel -> stringResource(R.string.not_a_batch_label)
    is BatchLookupError.NotInRecipe -> stringResource(R.string.batch_not_in_recipe, error.batch.name, error.batch.itemName)
    is BatchLookupError.Failed -> errorText(error.error)
}

@Composable
fun BatchDialog(
    state: BatchDialogState,
    ingredients: List<BomDetailsItem>,
    onBatchNumberChange: (String) -> Unit,
    lookupBatch: suspend (String) -> NetworkState<BatchMatch, BatchLookupError>,
    onSubmit: (itemCode: String, batchNumber: String, quantity: Double) -> Unit,
    onDismiss: () -> Unit,
    // Once per scan: whether the scanned code was accepted. Not called when the server couldn't be reached.
    onScanResult: (accepted: Boolean) -> Unit = {},
) {
    val editing = (state as? BatchDialogState.Editing)?.batch
    val input = (state as? BatchDialogState.Adding)?.input

    // Not keyed on the batch number, so a typed quantity survives a new scan
    var quantityText by rememberSaveable { mutableStateOf(editing?.quantity.orEmpty()) }
    var quantityTyped by rememberSaveable { mutableStateOf(editing != null) }
    // Reset with the batch number, so an earlier match can't be added for a new one
    var lookup by remember(input) { mutableStateOf<NetworkState<BatchMatch, BatchLookupError>>(NetworkState.Idle) }

    LaunchedEffect(input) {
        if (input == null) return@LaunchedEffect
        if (input.unrecognised) {
            lookup = NetworkState.Error(BatchLookupError.NotABatchLabel)
            onScanResult(false)
            return@LaunchedEffect
        }
        val number = input.text.trim()
        if (number.isEmpty()) return@LaunchedEffect
        if (!input.fromScan) delay(300.milliseconds)
        lookup = NetworkState.Loading
        val result = lookupBatch(number)
        lookup = result
        if (input.fromScan) {
            when {
                result is NetworkState.Success -> onScanResult(true)
                result is NetworkState.Error && result.error !is BatchLookupError.Failed -> onScanResult(false)
            }
        }
        if (result is NetworkState.Success && !quantityTyped) {
            quantityText = result.data.prefill?.let { formatDecimal(it) }.orEmpty()
        }
    }

    val match = (lookup as? NetworkState.Success)?.data
    val ingredient = if (editing != null) {
        ingredients.firstOrNull { it.itemCode == editing.itemCode }
    } else {
        match?.ingredient
    }
    val wholeNumber = ingredient?.wholeNumber == true
    val quantity = parseDecimal(quantityText)?.takeIf { validateQuantity(quantityText, wholeNumber) == null }
    // The quantity to save, or null while Add / Save should be disabled
    val submittable = quantity?.takeIf { if (editing != null) it != parseDecimal(editing.quantity) else match != null }
    val canSubmit = submittable != null

    fun submit(): Boolean {
        val amount = submittable ?: return false
        when {
            editing != null -> onSubmit(editing.itemCode, editing.batchNumber, amount)
            match != null -> onSubmit(match.ingredient.itemCode, match.batch.name, amount)
        }
        return true
    }

    val batchFocus = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = false)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(if (editing != null) R.string.edit_batch_label else R.string.add_batch_label),
                    style = MaterialTheme.typography.titleLarge,
                )
                val error = (lookup as? NetworkState.Error)?.error
                OutlinedTextField(
                    value = editing?.batchNumber ?: input?.text.orEmpty(),
                    onValueChange = onBatchNumberChange,
                    label = { Text(stringResource(R.string.batch_label)) },
                    singleLine = true,
                    readOnly = editing != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    isError = error != null,
                    // Always one line, so the dialog doesn't jump in height
                    supportingText = {
                        Text(
                            when {
                                error != null -> lookupErrorText(error)
                                ingredient != null -> stringResource(R.string.batch_ingredient, ingredient.itemName)
                                else -> ""
                            }
                        )
                    },
                    trailingIcon = {
                        if (lookup is NetworkState.Loading) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        } else if (match != null) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(batchFocus)
                )
                // Read inside the dialog, so they act on its window rather than the screen behind it
                val windowInfo = LocalWindowInfo.current
                val focusManager = LocalFocusManager.current
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    if (input == null || input.fromScan) return@LaunchedEffect
                    // Opened from +; the keyboard only opens once the dialog's window has focus
                    snapshotFlow { windowInfo.isWindowFocused }.first { it }
                    batchFocus.requestFocus()
                    keyboard?.show()
                }
                LaunchedEffect(input) {
                    if (input?.fromScan == true) {
                        focusManager.clearFocus(force = true)
                        keyboard?.hide()
                    }
                }
                QuantityInput(
                    value = quantityText,
                    onValueChange = {
                        quantityText = it
                        quantityTyped = true
                    },
                    uom = ingredient?.uom ?: editing?.uom.orEmpty(),
                    wholeNumber = wholeNumber,
                    modifier = Modifier.fillMaxWidth(),
                    onDone = ::submit,
                )
                ConfirmCancelButtons(
                    confirmLabel = stringResource(if (editing != null) R.string.save_label else R.string.add_label),
                    cancelLabel = stringResource(R.string.cancel_label),
                    onConfirm = { submit() },
                    onCancel = onDismiss,
                    confirmEnabled = canSubmit,
                )
            }
        }
    }
}
