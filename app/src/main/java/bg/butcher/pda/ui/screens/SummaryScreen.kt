package bg.butcher.pda.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.components.SubmitButton
import bg.butcher.pda.ui.theme.AppTheme
import bg.butcher.pda.util.formatDate
import bg.butcher.pda.util.formatDecimal
import kotlinx.datetime.LocalDate
import java.text.DateFormat

@Composable
private fun SummaryLine(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SummaryScreen(
    entry: ManufactureEntry,
    printState: NetworkState<Unit, Throwable>,
    onPrint: () -> Unit,
    onSameBomAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(text = entry.itemName, style = MaterialTheme.typography.titleLarge)
            SummaryLine(stringResource(R.string.batch_label), entry.batchNumber)
            SummaryLine(
                stringResource(R.string.quantity_label),
                stringResource(R.string.quantity_with_unit, formatDecimal(entry.quantity), entry.uom),
            )
            SummaryLine(stringResource(R.string.production_date_label), formatDate(entry.productionDate, DateFormat.LONG))
            SummaryLine(stringResource(R.string.expiry_date_label), formatDate(entry.expiryDate, DateFormat.LONG))
            Text(stringResource(if (entry.isFinished) R.string.finished_summary else R.string.not_finished_label))
            SummaryLine(stringResource(R.string.stock_entry_label), entry.stockEntryId)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            OutlinedButton(
                onClick = onPrint,
                enabled = printState !is NetworkState.Loading,
                modifier = Modifier.fillMaxWidth(.9f).heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.print_label))
            }
            OutlinedButton(
                onClick = onSameBomAgain,
                modifier = Modifier.fillMaxWidth(.9f).heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.same_bom_again_label))
            }
            SubmitButton(
                label = stringResource(R.string.done_label),
                onClick = onDone,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SummaryScreen_Preview() {
    AppTheme {
        SummaryScreen(
            entry = ManufactureEntry(
                stockEntryId = "MAT-STE-2026-00012",
                batchNumber = "BEEF_MEATBALLS0004",
                itemCode = "BEEF_MEATBALLS",
                itemName = "Meatballs (Beef)",
                quantity = 100.0,
                uom = "Nos",
                wholeNumber = true,
                productionDate = LocalDate(2026, 10, 24),
                expiryDate = LocalDate(2026, 10, 31),
                isFinished = true,
            ),
            printState = NetworkState.Idle,
            onPrint = {},
            onSameBomAgain = {},
            onDone = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
