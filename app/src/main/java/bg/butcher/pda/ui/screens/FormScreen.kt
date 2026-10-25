package bg.butcher.pda.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomDetailsItem
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.components.BatchDialog
import bg.butcher.pda.ui.components.BatchDialogState
import bg.butcher.pda.ui.components.BatchInput
import bg.butcher.pda.ui.components.Centered
import bg.butcher.pda.ui.components.ErrorMessage
import bg.butcher.pda.ui.components.IngredientLine
import bg.butcher.pda.ui.components.IngredientTable
import bg.butcher.pda.ui.components.SubmitButton
import bg.butcher.pda.ui.components.errorText
import bg.butcher.pda.ui.theme.AppTheme
import bg.butcher.pda.util.formatDecimal
import java.io.IOException

fun FormUiState.ingredientLines(bom: BomDetails): List<IngredientLine> = bom.items.map {
    IngredientLine(
        itemCode = it.itemCode,
        name = it.itemName,
        uom = it.uom,
        required = it.quantity,
        chosen = chosenQuantity(it.itemCode),
        coverage = coverage(it),
        allowed = allowedRange(it),
        wholeNumberChoices = if (it.wholeNumber) wholeNumberChoices(it) else null,
    )
}

/** The run settings from Start, read-only: change them by going back. */
@Composable
private fun RunSummary(state: FormUiState, bom: BomDetails) {
    val settings = listOfNotNull(
        stringResource(R.string.quantity_with_unit, formatDecimal(bom.quantity), bom.uom),
        state.expiryDays?.let { stringResource(R.string.expires_in_days, it) },
        stringResource(if (state.isFinished) R.string.finished_summary else R.string.not_finished_label),
        if (state.explode) stringResource(R.string.explode_label) else null,
    )
    Column {
        Text(text = bom.itemName, style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.bom_line, bom.name),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(text = settings.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FormScreen(
    state: FormUiState,
    dialog: BatchDialogState?,
    onDialogChange: (BatchDialogState?) -> Unit,
    lookupBatch: suspend (String) -> NetworkState<BatchMatch, BatchLookupError>,
    onAddBatch: (itemCode: String, batchNumber: String, quantity: Double) -> BatchChangeError?,
    onUpdateBatch: (itemCode: String, batchNumber: String, quantity: Double) -> BatchChangeError?,
    onRemoveBatch: (itemCode: String, batchNumber: String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onScanAccepted: () -> Unit = {},
) {
    Box(modifier = modifier) {
        when (state.fetchState) {
            is NetworkState.Error -> Centered { ErrorMessage(error = state.fetchState.error, onRetry = onRetry) }
            // Idle only after process death, until the reload starts
            is NetworkState.Idle, is NetworkState.Loading -> Centered { CircularProgressIndicator() }
            is NetworkState.Success -> {
                val bom = state.fetchState.data
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().padding(20.dp)
                ) {
                    RunSummary(state, bom)
                    Box(modifier = Modifier.weight(1f)) {
                        IngredientTable(
                            ingredients = state.ingredientLines(bom),
                            batches = state.items,
                            onEdit = { onDialogChange(BatchDialogState.Editing(it)) },
                            onDelete = onRemoveBatch,
                        )
                    }
                    OutlinedButton(
                        onClick = { onDialogChange(BatchDialogState.Adding(BatchInput(""))) },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth(.9f)
                            .heightIn(min = 56.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null)
                        Text(
                            text = stringResource(R.string.add_batch_label),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    val submitError = (state.submitState as? NetworkState.Error)?.error
                    submitError?.let { error ->
                        Text(
                            text = if (error.isOutcomeUnknown) stringResource(R.string.submit_unknown) else errorText(error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    SubmitButton(
                        label = stringResource(
                            if (submitError?.isOutcomeUnknown == true) R.string.retry_label else R.string.submit_label
                        ),
                        onClick = onSubmit,
                        enabled = state.canSubmit,
                        isLoading = state.submitState is NetworkState.Loading,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                if (dialog != null) {
                    BatchDialog(
                        state = dialog,
                        ingredients = bom.items,
                        onBatchNumberChange = { onDialogChange(BatchDialogState.Adding(BatchInput(it))) },
                        lookupBatch = lookupBatch,
                        onSubmit = { itemCode, batchNumber, quantity ->
                            val save = if (dialog is BatchDialogState.Editing) onUpdateBatch else onAddBatch
                            if (save(itemCode, batchNumber, quantity) == null) onDialogChange(null)
                        },
                        onDismiss = { onDialogChange(null) },
                        onScanAccepted = onScanAccepted,
                    )
                }
            }
        }
    }
}

private fun previewItem(code: String, name: String, quantity: Double, wholeNumber: Boolean = false) = BomDetailsItem(
    quantity = quantity,
    itemCode = code,
    itemName = name,
    uom = if (wholeNumber) "Nos" else "Kg",
    stockUom = if (wholeNumber) "Nos" else "Kg",
    conversionFactor = 1.0,
    wholeNumber = wholeNumber,
)

private val previewBom = BomDetails(
    name = "BOM-BEEF_MEATBALLS-001",
    quantity = 100.0,
    itemCode = "BEEF_MEATBALLS",
    itemName = "Meatballs (Beef)",
    uom = "Nos",
    stockUom = "Nos",
    shelfLife = 7,
    items = listOf(
        previewItem("BEEF", "Beef", 4.0),
        previewItem("SPICE", "Spice", 0.8),
        previewItem("ONION", "Onion", 12.0, wholeNumber = true),
        previewItem("CASING", "Casing", 2.5, wholeNumber = true),
    ),
    wholeNumber = true,
)

@Composable
private fun FormScreenPreview(state: FormUiState) {
    AppTheme {
        FormScreen(
            state = state,
            dialog = null,
            onDialogChange = {},
            lookupBatch = { NetworkState.Idle },
            onAddBatch = { _, _, _ -> null },
            onUpdateBatch = { _, _, _ -> null },
            onRemoveBatch = { _, _ -> },
            onSubmit = {},
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FormScreen_Preview() {
    FormScreenPreview(
        FormUiState(
            fetchState = NetworkState.Success(previewBom),
            expiryDaysText = "7",
            items = mapOf(
                "BEEF" to mapOf("BEEF.0001" to 2.5, "BEEF.0002" to 1.4),
                "SPICE" to mapOf("SPICE.0001" to 0.5),
                "ONION" to mapOf("ONION.0001" to 14.0),
            ),
        )
    )
}

@Preview(showBackground = true)
@Composable
fun FormScreen_SubmitUnknown_Preview() {
    FormScreenPreview(
        FormUiState(
            fetchState = NetworkState.Success(previewBom),
            expiryDaysText = "7",
            items = mapOf(
                "BEEF" to mapOf("BEEF.0001" to 4.0),
                "SPICE" to mapOf("SPICE.0001" to 0.8),
                "ONION" to mapOf("ONION.0001" to 12.0),
                "CASING" to mapOf("CASING.0001" to 2.0),
            ),
            submitState = NetworkState.Error(ErpError.Http(500, null)),
        )
    )
}

@Preview(showBackground = true)
@Composable
fun FormScreen_FetchError_Preview() {
    FormScreenPreview(FormUiState(fetchState = NetworkState.Error(ErpError.Network(IOException()))))
}
