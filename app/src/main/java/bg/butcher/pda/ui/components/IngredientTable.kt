package bg.butcher.pda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.ui.screens.Coverage
import bg.butcher.pda.ui.theme.PreviewTheme
import bg.butcher.pda.ui.theme.ThemePreviews
import bg.butcher.pda.util.formatDecimal

data class TableParameters(
    val cellPadding: Dp,
    val borderThickness: Dp,
    val borderColor: Color
)

data class BatchRow(
    val batchNumber: String,
    val quantity: Double,
    val uom: String,
)

@Composable
private fun coverageColor(coverage: Coverage): Color {
    val dark = isSystemInDarkTheme()
    return when (coverage) {
        Coverage.MISSING -> MaterialTheme.colorScheme.onSurfaceVariant
        Coverage.UNDER -> if (dark) Color(0xFFFFB951) else Color(0xFF8A5100)
        Coverage.OK -> if (dark) Color(0xFF7DDB8A) else Color(0xFF1B6D2F)
        Coverage.OVER -> MaterialTheme.colorScheme.error
    }
}

@Composable
private fun coverageHint(ingredient: IngredientLine): String? {
    val uom = ingredient.uom
    return when {
        ingredient.coverage == Coverage.OK -> null
        ingredient.wholeNumberChoices != null -> {
            val choices = ingredient.wholeNumberChoices
            if (choices.first == choices.last) {
                stringResource(R.string.coverage_use, choices.first.toString())
            } else {
                stringResource(R.string.coverage_use, "${choices.first}–${choices.last}")
            }
        }
        ingredient.coverage == Coverage.OVER ->
            stringResource(R.string.coverage_at_most, formatDecimal(ingredient.allowed.endInclusive), uom)
        else -> stringResource(R.string.coverage_at_least, formatDecimal(ingredient.allowed.start), uom)
    }
}

/** One ingredient as the table shows it. */
data class IngredientLine(
    val itemCode: String,
    val name: String,
    val uom: String,
    val required: Double,
    val chosen: Double,
    val coverage: Coverage,
    val allowed: ClosedFloatingPointRange<Double>,
    // Set for whole-number units, which show the whole amounts allowed instead of the limits
    val wholeNumberChoices: LongRange?,
)

@Composable
fun IngredientTableSection(
    ingredient: IngredientLine,
    modifier: Modifier = Modifier
) {
    val color = coverageColor(ingredient.coverage)
    val amount = stringResource(
        R.string.coverage_amount,
        formatDecimal(ingredient.chosen),
        formatDecimal(ingredient.required),
        ingredient.uom,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(5.dp)
    ) {
        Text(text = ingredient.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = amount, color = color)
                if (ingredient.coverage == Coverage.OK) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.coverage_ok),
                        tint = color,
                        modifier = Modifier.padding(start = 4.dp).size(18.dp)
                    )
                }
            }
            coverageHint(ingredient)?.let {
                Text(text = it, color = color, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** A batch with its quantity, and buttons to edit or remove it, large enough to press with gloves. */
@Composable
fun IngredientTableRow(
    batch: BatchRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    params: TableParameters,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = params.cellPadding)
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = params.cellPadding)) {
            Text(text = batch.batchNumber)
            Text(
                text = stringResource(R.string.quantity_with_unit, formatDecimal(batch.quantity), batch.uom),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = Icons.Default.Edit, contentDescription = stringResource(R.string.edit_label))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete_label),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun IngredientTable(
    ingredients: List<IngredientLine>,
    batches: Map<String, Map<String, Double>>,
    onEdit: (BatchInfo) -> Unit,
    onDelete: (itemCode: String, batchNumber: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val params = TableParameters(
        borderThickness = 1.dp,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        cellPadding = 5.dp,
    )
    Column(modifier = modifier.border(params.borderThickness, params.borderColor)) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            ingredients.forEach { ingredient ->
                val entries = batches[ingredient.itemCode].orEmpty()

                // Keys are prefixed so an item code and a batch number can't clash
                stickyHeader(key = "item|${ingredient.itemCode}") {
                    IngredientTableSection(ingredient)
                }

                if (entries.isEmpty()) {
                    item(key = "none|${ingredient.itemCode}") {
                        HorizontalDivider(thickness = params.borderThickness, color = params.borderColor)
                        Text(
                            text = stringResource(R.string.none_label),
                            textAlign = TextAlign.Center,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(params.cellPadding),
                        )
                    }
                }

                items(entries.toList(), key = { (batchNumber, _) -> "batch|${ingredient.itemCode}|$batchNumber" }) {
                    val (batchNumber, quantity) = it
                    HorizontalDivider(thickness = params.borderThickness, color = params.borderColor)
                    IngredientTableRow(
                        batch = BatchRow(batchNumber, quantity, ingredient.uom),
                        onEdit = {
                            onEdit(BatchInfo(batchNumber, ingredient.itemCode, formatDecimal(quantity), ingredient.uom))
                        },
                        onDelete = { onDelete(ingredient.itemCode, batchNumber) },
                        params = params,
                    )
                }
            }
        }
    }
}

private val previewIngredients = listOf(
    IngredientLine("BEEF", "Beef", "Kg", 4.0, 4.1, Coverage.OK, 3.8..4.2, null),
    IngredientLine("SPICE", "Spice", "Kg", 0.8, 0.5, Coverage.UNDER, 0.76..0.84, null),
    IngredientLine("ONION", "Onion", "Kg", 12.0, 14.0, Coverage.OVER, 11.4..12.6, null),
    // Whole numbers: "use 2–3" rather than the limits
    IngredientLine("CASING", "Casing", "Nos", 2.5, 0.0, Coverage.MISSING, 2.0..3.0, 2L..3L),
)

@ThemePreviews
@Composable
private fun IngredientTable_Preview() {
    PreviewTheme {
        IngredientTable(
            ingredients = previewIngredients,
            batches = mapOf(
                "BEEF" to mapOf("BEEF.0001" to 2.5, "BEEF.0002" to 1.6),
                "SPICE" to mapOf("SPICE.0001" to 0.5),
                "ONION" to mapOf("ONION.0001" to 14.0),
            ),
            onEdit = {},
            onDelete = { _, _ -> },
            modifier = Modifier.height(480.dp),
        )
    }
}
