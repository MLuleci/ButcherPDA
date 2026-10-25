package bg.butcher.pda.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.ExpiryStatus
import bg.butcher.pda.model.StockEntryRow
import bg.butcher.pda.model.expiryStatus
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.components.Centered
import bg.butcher.pda.ui.components.ErrorMessage
import bg.butcher.pda.ui.components.LabelledValue
import bg.butcher.pda.ui.theme.AppTheme
import bg.butcher.pda.util.formatDecimal
import bg.butcher.pda.util.shortDate
import bg.butcher.pda.util.today
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import java.io.IOException

@Composable
private fun ExpiryBanner(expiry: LocalDate?, today: LocalDate) {
    val colors = MaterialTheme.colorScheme
    val status = expiryStatus(expiry, today)
    val (text, background, content) = when (status) {
        ExpiryStatus.EXPIRED ->
            Triple(stringResource(R.string.expired_on, shortDate(expiry!!)), colors.errorContainer, colors.onErrorContainer)
        ExpiryStatus.TODAY -> Triple(stringResource(R.string.expires_today), colors.errorContainer, colors.onErrorContainer)
        ExpiryStatus.SOON -> {
            val days = today.daysUntil(expiry!!)
            val text = if (days == 1) {
                stringResource(R.string.expires_tomorrow)
            } else {
                pluralStringResource(R.plurals.expires_in_days_banner, days, days)
            }
            Triple(text, colors.tertiaryContainer, colors.onTertiaryContainer)
        }
        ExpiryStatus.UNKNOWN ->
            Triple(stringResource(R.string.no_expiry_date), colors.surfaceVariant, colors.onSurfaceVariant)
        ExpiryStatus.OK -> return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(text, color = content, style = MaterialTheme.typography.titleMedium)
        if (expiry != null && status != ExpiryStatus.EXPIRED) {
            Text(shortDate(expiry), color = content)
        }
    }
}

@Composable
private fun IngredientList(
    ingredients: NetworkState<Ingredients, ErpError>,
    onRetry: () -> Unit,
) {
    Text(stringResource(R.string.ingredients_used_label), style = MaterialTheme.typography.titleMedium)
    when (ingredients) {
        is NetworkState.Idle, is NetworkState.Loading -> CircularProgressIndicator()
        is NetworkState.Error -> ErrorMessage(error = ingredients.error, onRetry = onRetry)
        is NetworkState.Success -> when (val result = ingredients.data) {
            Ingredients.NotMadeHere -> Text(stringResource(R.string.not_made_with_app))
            is Ingredients.Loaded -> if (result.rows.isEmpty()) {
                Text(stringResource(R.string.no_ingredients))
            } else {
                Column {
                    result.rows.forEach { row ->
                        IngredientRow(row)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientRow(row: StockEntryRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text(row.batchNumber ?: row.itemName, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.quantity_with_unit, formatDecimal(row.quantity), row.uom))
    }
}

@Composable
fun DetailsScreen(
    state: DetailsUiState,
    onRetry: () -> Unit,
    onRetryIngredients: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = today(),
) {
    when (val result = state.batch) {
        is NetworkState.Idle, is NetworkState.Loading -> Centered(modifier) { CircularProgressIndicator() }
        is NetworkState.Error -> Centered(modifier) { ErrorMessage(error = result.error, onRetry = onRetry) }
        is NetworkState.Success -> {
            val batch = result.data
            val noDate = stringResource(R.string.no_date)
            Column(modifier = modifier) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Column {
                        Text(batch.itemName, style = MaterialTheme.typography.titleLarge)
                        Text(batch.name, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (!batch.isMadeHere) {
                        Text(stringResource(R.string.not_produced_here), color = MaterialTheme.colorScheme.error)
                    }
                    ExpiryBanner(batch.expiryDate, today)
                    LabelledValue(
                        stringResource(R.string.quantity_label),
                        batch.batchQuantity?.let {
                            stringResource(R.string.quantity_with_unit, formatDecimal(it), batch.stockUom.orEmpty())
                        } ?: noDate,
                    )
                    LabelledValue(
                        stringResource(R.string.production_date_label),
                        batch.manufacturingDate?.let(::shortDate) ?: noDate,
                    )
                    batch.referenceName?.let { LabelledValue(stringResource(R.string.stock_entry_label), it) }
                    IngredientList(state.ingredients, onRetryIngredients)
                }
                // Printing is wired up with the label printer (Step 6.5)
                if (batch.isMadeHere) {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth(.9f)
                            .heightIn(min = 56.dp)
                            .padding(bottom = 10.dp)
                    ) {
                        Text(stringResource(R.string.print_label))
                    }
                }
            }
        }
    }
}

private val previewBatch = BatchDetails(
    name = "PORK_SAUSAGE0012",
    item = "PORK_SAUSAGE",
    itemName = "Sausage (Pork)",
    stockUom = "Kg",
    manufacturingDate = LocalDate(2026, 10, 23),
    expiryDate = LocalDate(2026, 10, 26),
    batchQuantity = 20.0,
    referenceName = "MAT-STE-2026-00042",
    defaultBom = "BOM-PORK_SAUSAGE-001",
)

@Composable
private fun DetailsScreenPreview(state: DetailsUiState) {
    AppTheme {
        DetailsScreen(
            state = state,
            onRetry = {},
            onRetryIngredients = {},
            modifier = Modifier.fillMaxSize(),
            today = LocalDate(2026, 10, 25),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DetailsScreen_Preview() {
    DetailsScreenPreview(
        DetailsUiState(
            batchNumber = previewBatch.name,
            batch = NetworkState.Success(previewBatch),
            ingredients = NetworkState.Success(
                Ingredients.Loaded(
                    listOf(
                        StockEntryRow("PORK", "Pork", 8.5, "Kg", "PORK.0001"),
                        StockEntryRow("SPICE", "Spice", 0.3, "Kg", "SPICE.0002"),
                        StockEntryRow("CASING", "Casing", 20.0, "Nos", "CASING.0001"),
                    )
                )
            ),
        )
    )
}

@Preview(showBackground = true)
@Composable
fun DetailsScreen_NotProducedHere_Preview() {
    DetailsScreenPreview(
        DetailsUiState(
            batchNumber = "BEEF.0001",
            batch = NetworkState.Success(
                BatchDetails(
                    name = "BEEF.0001", item = "BEEF", itemName = "Beef", stockUom = "Kg",
                    manufacturingDate = LocalDate(2026, 10, 1), expiryDate = LocalDate(2026, 10, 15),
                    batchQuantity = 486.5, referenceName = "MAT-STE-2026-00001",
                )
            ),
            ingredients = NetworkState.Success(Ingredients.NotMadeHere),
        )
    )
}

@Preview(showBackground = true)
@Composable
fun DetailsScreen_IngredientsError_Preview() {
    DetailsScreenPreview(
        DetailsUiState(
            batchNumber = previewBatch.name,
            batch = NetworkState.Success(previewBatch.copy(expiryDate = null)),
            ingredients = NetworkState.Error(ErpError.Network(IOException())),
        )
    )
}
