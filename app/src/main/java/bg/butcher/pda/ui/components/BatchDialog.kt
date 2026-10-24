package bg.butcher.pda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import bg.butcher.pda.R
import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.screens.FetchBatchError
import bg.butcher.pda.util.validateQuantity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BatchDialog(
    fetchBatchDetails: suspend (String) -> NetworkState<BatchDetails, FetchBatchError>,
    onSubmit: (BatchInfo) -> Unit,
    onDismiss: () -> Unit,
    initialValue: BatchInfo? = null,
    isEditing: Boolean = false,
) {
    var batch by rememberSaveable(initialValue) { mutableStateOf(initialValue ?: BatchInfo()) }
    var batchNumberText by rememberSaveable(initialValue) { mutableStateOf(initialValue?.batchNumber ?: "") }
    var status by rememberSaveable { mutableStateOf<NetworkState<BatchDetails, FetchBatchError>>(NetworkState.Idle) }

    val isError = status is NetworkState.Error
    val isLoading = status is NetworkState.Loading
    val isDirty = batch != initialValue

    val isBatchValid = status is NetworkState.Success
    val isQuantityValid = validateQuantity(batch.quantity, wholeNumber = false) == null

    val canSubmit = isBatchValid && isQuantityValid && isDirty

    val scope = rememberCoroutineScope()

    LaunchedEffect(batchNumberText) {
        val batchNumber = batchNumberText.trim()

        // Invalidate any previous result immediately.
        status = NetworkState.Idle

        if (batchNumber.isEmpty()) {
            return@LaunchedEffect
        }

        // Avoid requesting every intermediate edit.
        delay(300.milliseconds)

        status = NetworkState.Loading

        val result = fetchBatchDetails(batchNumber)

        // Protect against a lookup implementation that returns after
        // cancellation instead of propagating CancellationException.
        currentCoroutineContext().ensureActive()

        status = result
        (status as? NetworkState.Success)?.data?.let {
            batch = batch.copy(
                batchNumber = it.name,
                itemCode = it.item,
                uom = it.stockUom.orEmpty(),
            )
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Column {
                OutlinedTextField(
                    value = batchNumberText,
                    onValueChange = { batchNumberText = it },
                    label = { Text(stringResource(R.string.batch_label)) },
                    singleLine = true,
                    enabled = !isEditing,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    isError = isError,
                    supportingText = {
                        (status as? NetworkState.Error)?.let {
                            Text(
                                text = stringResource(
                                    when (it.error) {
                                        FetchBatchError.NOT_FOUND -> R.string.invalid_batch_no
                                        FetchBatchError.FORBIDDEN -> R.string.forbidden_error
                                        FetchBatchError.NETWORK_ERROR -> R.string.network_error
                                        else -> R.string.error_message
                                    }
                                )
                            )
                        }
                    },
                    trailingIcon = {
                        if (isLoading) {
                            CircularProgressIndicator()
                        } else if (isBatchValid) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Valid")
                        }
                    }
                )
                QuantityInput(
                    value = batch.quantity,
                    onValueChange = { batch = batch.copy(quantity = it) },
                    uom = (status as? NetworkState.Success)?.data?.stockUom.orEmpty(),
                    wholeNumber = false,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 5.dp)
                        .height(IntrinsicSize.Min)
                        .fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel_label))
                    }
                    Button(
                        onClick = { onSubmit(batch) },
                        enabled = canSubmit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.select_label))
                    }
                }
            }
        }
    }
}