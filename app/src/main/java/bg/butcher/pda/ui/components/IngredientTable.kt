package bg.butcher.pda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import bg.butcher.pda.model.BomDetailsItem

data class TableParameters(
    val cellPadding: Dp,
    val borderThickness: Dp,
    val borderColor: Color
)

data class BatchInfo(
    val batchNumber: String = "",
    val itemCode: String = "",
    val quantity: Float = 0f,
    val uom: String = "",
)

@Composable
fun IngredientTableHeader(
    params: TableParameters,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceDim)
    ) {
        Text(
            text = stringResource(R.string.batch_label),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(params.cellPadding)
        )
        VerticalDivider(thickness = params.borderThickness, color = params.borderColor)
        Text(
            text = stringResource(R.string.amount_label),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(params.cellPadding)
        )
    }
}

@Composable
fun IngredientTableSection(
    name: String,
    totalQuantity: Float,
    requriedQuantity: Float,
    uom: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceBright)
    ) {
        Text(text = name, modifier = Modifier.weight(1f))
        Text(text = "%.1f / %.1f %s".format(totalQuantity, requriedQuantity, uom))
    }
}

@Composable
fun IngredientTableRow(
    batch: BatchInfo,
    params: TableParameters,
    modifier: Modifier = Modifier
) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier.fillMaxWidth(),
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Edit - LHS
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary)
                            .wrapContentSize(Alignment.CenterStart)
                            .padding(horizontal = 5.dp),
                        tint = Color.White
                    )
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Delete - RHS
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.error)
                            .wrapContentSize(Alignment.CenterEnd)
                            .padding(horizontal = 5.dp),
                        tint = Color.White
                    )
                }
                else -> {}
            }
        },
        onDismiss = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> {}
                SwipeToDismissBoxValue.EndToStart -> {}
                else -> {}
            }
        }
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Text(
                text = batch.batchNumber,
                modifier = Modifier
                    .weight(1f)
                    .padding(params.cellPadding)
            )
            VerticalDivider(thickness = params.borderThickness, color = params.borderColor)
            Text(
                text = "%.1f %s".format(batch.quantity, batch.uom),
                modifier = Modifier
                    .weight(1f)
                    .padding(params.cellPadding)
            )
        }
    }
}

@Composable
fun IngredientTable(
    items: List<BomDetailsItem>,
    batches: Map<String, Map<String, Float>>,
    modifier: Modifier = Modifier,
) {
    val params = TableParameters(
        borderThickness = 1.dp,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        cellPadding = 5.dp,
    )
    Column(modifier = modifier.border(params.borderThickness, params.borderColor)) {
        IngredientTableHeader(params)
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items.forEach { item ->
                val entries = batches[item.itemCode] ?: emptyMap()
                val totalAmount = entries.values.sum()
                stickyHeader(item.itemCode) {
                    IngredientTableSection(item.itemName, totalAmount, item.quantity, item.uom)
                }

                if (entries.isEmpty()) {
                    item {
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

                items(entries.toList(), key = { it.first }) {
                    val (batchNumber, quantity) = it
                    HorizontalDivider(thickness = params.borderThickness, color = params.borderColor)
                    IngredientTableRow(
                        batch = BatchInfo(
                            batchNumber,
                            itemCode = item.itemCode,
                            quantity,
                            uom = item.uom,
                        ),
                        params = params,
                    )
                }
            }
        }
    }
}
