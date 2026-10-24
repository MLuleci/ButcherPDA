package bg.butcher.pda.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomDetailsItem
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.components.Centered
import bg.butcher.pda.ui.components.ErrorMessage
import bg.butcher.pda.ui.components.IngredientLine
import bg.butcher.pda.ui.components.IngredientTable
import bg.butcher.pda.ui.components.SubmitButton
import bg.butcher.pda.ui.theme.AppTheme
import bg.butcher.pda.util.formatDecimal

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
        "${formatDecimal(bom.quantity)} ${bom.uom}",
        state.expiryDays?.let { stringResource(R.string.expires_in_days, it) },
        stringResource(if (state.isFinished) R.string.finished_summary else R.string.not_finished_label),
        if (state.explode) stringResource(R.string.explode_label) else null,
    )
    Column {
        Text(text = bom.itemName, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${stringResource(R.string.bom_label)}: ${bom.name}",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(text = settings.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FormScreen(
    state: FormUiState,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
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
                        )
                    }
                    IconButton(
                        onClick = { showDialog = true },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth(.9f)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = stringResource(R.string.add_batch_label))
                    }
                    SubmitButton(
                        label = stringResource(R.string.submit_label),
                        onClick = onSubmit,
                        enabled = state.canSubmit,
                        isLoading = state.submitState is NetworkState.Loading,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
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

@Preview(showBackground = true)
@Composable
fun FormScreen_Preview() {
    val bom = BomDetails(
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
    AppTheme {
        FormScreen(
            state = FormUiState(
                fetchState = NetworkState.Success(bom),
                expiryDaysText = "7",
                items = mapOf(
                    "BEEF" to mapOf("BEEF.0001" to 2.5, "BEEF.0002" to 1.4),
                    "SPICE" to mapOf("SPICE.0001" to 0.5),
                    "ONION" to mapOf("ONION.0001" to 14.0),
                ),
            ),
            onSubmit = {},
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
