package bg.butcher.pda.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.ExpiryStatus
import bg.butcher.pda.model.expiryStatus
import bg.butcher.pda.ui.theme.PreviewTheme
import bg.butcher.pda.ui.theme.ThemePreviews
import bg.butcher.pda.util.formatDecimal
import bg.butcher.pda.util.shortDate
import kotlinx.datetime.LocalDate

private val WARN = setOf(ExpiryStatus.EXPIRED, ExpiryStatus.TODAY, ExpiryStatus.SOON)

@Composable
fun BatchResultRow(
    batch: BatchDetails,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noDate = stringResource(R.string.no_date)
    val quantity = batch.batchQuantity?.let {
        stringResource(R.string.quantity_with_unit, formatDecimal(it), batch.stockUom.orEmpty())
    } ?: noDate
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(batch.itemName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(batch.name, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = stringResource(
                    R.string.batch_row_details,
                    quantity,
                    batch.manufacturingDate?.let(::shortDate) ?: noDate,
                    batch.expiryDate?.let(::shortDate) ?: noDate,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (expiryStatus(batch.expiryDate, today) in WARN) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = stringResource(R.string.expiry_warning),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun BatchResultRow_Preview() {
    PreviewTheme {
        BatchResultRow(
            batch = BatchDetails(
                name = "PORK_SAUSAGE0012",
                item = "PORK_SAUSAGE",
                itemName = "Sausage (Pork)",
                stockUom = "Kg",
                manufacturingDate = LocalDate(2026, 10, 23),
                expiryDate = LocalDate(2026, 10, 26),
                batchQuantity = 20.5,
                defaultBom = "BOM-PORK_SAUSAGE-001",
            ),
            today = LocalDate(2026, 10, 25),
            onClick = {},
        )
    }
}
