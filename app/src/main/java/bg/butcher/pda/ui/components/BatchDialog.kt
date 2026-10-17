package bg.butcher.pda.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import bg.butcher.pda.R
import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetailsItem
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.network.NetworkStatus
import bg.butcher.pda.ui.screens.AssemblyViewModel
import bg.butcher.pda.ui.theme.AppTheme

@Composable
fun BatchInput(
    initialValue: String,
    onValueChanged: (String, String) -> Unit,
    fetchState: NetworkState<BatchDetails>,
    lookupBatch: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val state = rememberTextFieldState(initialValue)
    val text = state.text.toString()
    val isError = fetchState.status == NetworkStatus.ERROR

    LaunchedEffect(text) { lookupBatch(text) }
    LaunchedEffect(fetchState) {
        fetchState.result?.let { onValueChanged(it.item, it.name) }
    }
    OutlinedTextField(
        state = state,
        isError = isError,
        enabled = enabled,
        supportingText = {
            when (fetchState.status) {
                NetworkStatus.ERROR -> {
                    Text(
                        text = stringResource(R.string.invalid_batch_no),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                NetworkStatus.SUCCESS -> {
                    Text(
                        text = fetchState.result?.itemName ?: "",
                        color = Color.Green
                    )
                }
                else -> {}
            }
        },
        trailingIcon = {
            if (fetchState.status == NetworkStatus.PENDING) {
                CircularProgressIndicator()
            }
        },
        modifier = modifier
    )
}

@Composable
fun BatchDialog(
    onDismiss: () -> Unit,
    initialBatch: BatchInfo? = null,
    isEditing: Boolean = false,
    viewModel: AssemblyViewModel = viewModel(factory = AssemblyViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    var batch by rememberSaveable(initialBatch) { mutableStateOf(initialBatch ?: BatchInfo()) }
    val isDirty = batch.batchNumber != initialBatch?.batchNumber || batch.quantity != initialBatch.quantity
    val isValid = batch.itemCode.isNotEmpty() && batch.batchNumber.isNotEmpty() && batch.quantity > 0f

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column {
                BatchInput(
                    initialValue = batch.batchNumber,
                    onValueChanged = { itemCode, batchNumber ->
                        batch = batch.copy(itemCode = itemCode, batchNumber = batchNumber)
                    },
                    fetchState = uiState.batchFetchState,
                    lookupBatch = { viewModel.fetchBatchDetails(it) },
                    enabled = !isEditing,
                )
                QuantityInput(
                    initialValue = batch.quantity,
                    uom = batch.uom,
                    onValueChanged = { batch = batch.copy(quantity = it) }
                )
            }
            Row {
                OutlinedButton(onClick = { onDismiss() }) {
                    Text(stringResource(R.string.cancel_label))
                }
                Button(
                    onClick = {
                        viewModel.upsertBatch(batch.itemCode, batch.batchNumber, batch.quantity)
                        onDismiss()
                    },
                    enabled = isDirty && isValid
                ) {
                    Text(stringResource(R.string.submit_label))
                }
            }
        }
    }
}