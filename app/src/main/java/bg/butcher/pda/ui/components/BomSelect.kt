package bg.butcher.pda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun BomResultRow(
    item: BomSearchResultItem,
    selected: Boolean,
    striped: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        selected -> colors.primary
        striped -> colors.surfaceDim
        else -> colors.surfaceBright
    }
    CompositionLocalProvider(LocalContentColor provides if (selected) colors.onPrimary else colors.onSurface) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .background(background),
        ) {
            Column(modifier = Modifier.padding(10.dp).weight(1f)) {
                Text(text = item.itemName, style = MaterialTheme.typography.bodyLarge)
                Text(text = "${item.name} (${item.uom})", style = MaterialTheme.typography.labelSmall)
            }
            if (item.isDefaultBom) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = stringResource(R.string.default_bom_label),
                    modifier = Modifier.padding(end = 10.dp)
                )
            }
        }
    }
}

@Composable
fun BomSearch(
    onQueryChanged: (String) -> Unit,
    onSelect: (BomSearchResultItem?) -> Unit,
    searchState: NetworkState<List<BomSearchResultItem>, ErpError>,
    modifier: Modifier = Modifier,
) {
    val state = rememberTextFieldState()
    val query = state.text.toString()
    var selectedItem: BomSearchResultItem? by remember { mutableStateOf(null) }
    val focusManager = LocalFocusManager.current
    val minHeight = if (searchState is NetworkState.Idle) 0.dp else 100.dp

    LaunchedEffect(query) {
        selectedItem = null
        onQueryChanged(query)
    }
    Column(modifier = modifier) {
        SearchField(state, requestFocus = true)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.heightIn(minHeight, 500.dp).fillMaxWidth()
        ) {
            when (searchState) {
                is NetworkState.Error -> ErrorMessage(error = searchState.error, onRetry = { onQueryChanged(query) })
                is NetworkState.Loading -> CircularProgressIndicator()
                is NetworkState.Success -> {
                    if (searchState.data.isEmpty()) {
                        Text(stringResource(R.string.no_results))
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            LazyColumn {
                                itemsIndexed(searchState.data, key = { _, item -> item.name }) { index, item ->
                                    BomResultRow(
                                        item = item,
                                        selected = item.name == selectedItem?.name,
                                        striped = index % 2 == 1,
                                        onClick = {
                                            selectedItem = item
                                            focusManager.clearFocus()
                                        },
                                    )
                                }
                            }
                        }
                        ConfirmCancelButtons(
                            confirmLabel = stringResource(R.string.select_label),
                            cancelLabel = stringResource(R.string.cancel_label),
                            onConfirm = { onSelect(selectedItem) },
                            onCancel = { onSelect(null) },
                            confirmEnabled = selectedItem != null,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                        )
                    }
                }
                NetworkState.Idle -> {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BomSelect(
    onQueryChanged: (String) -> Unit,
    onItemSelected: (BomSearchResultItem) -> Unit,
    selectedItem: BomSearchResultItem?,
    searchState: NetworkState<List<BomSearchResultItem>, ErpError>,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }

    OutlinedCard(
        onClick = { showBottomSheet = true },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.bom_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (selectedItem == null) {
                Text(stringResource(R.string.choose_bom_label), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(selectedItem.itemName, style = MaterialTheme.typography.bodyLarge)
                Text("${selectedItem.name} (${selectedItem.uom})", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (showBottomSheet) {
        ModalBottomSheet(
            dragHandle = null,
            sheetGesturesEnabled = false,
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            BomSearch(
                onQueryChanged = onQueryChanged,
                onSelect = {
                    if (it != null) onItemSelected(it)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                        }
                    }
                },
                searchState = searchState,
            )
        }
    }
}

private val previewResults = listOf(
    BomSearchResultItem("BOM.001", "SKU.001", "Meatballs with a stupid long name that may overflow", "Nos", 1),
    BomSearchResultItem("BOM.002", "SKU.002", "Mince Meat", "Kgs", 1),
    BomSearchResultItem("BOM.003", "SKU.003", "Kebabs", "Kgs", 0),
)

@Preview(showBackground = true)
@Composable
fun BomSelect_None_Preview() {
    AppTheme {
        BomSelect(onQueryChanged = {}, onItemSelected = {}, selectedItem = null, searchState = NetworkState.Idle)
    }
}

@Preview(showBackground = true)
@Composable
fun BomSelect_Some_Preview() {
    AppTheme {
        BomSelect(
            onQueryChanged = {},
            onItemSelected = {},
            selectedItem = previewResults[1],
            searchState = NetworkState.Idle,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BomResultRow_Selected_Preview() {
    AppTheme {
        Column {
            BomResultRow(previewResults[0], selected = true, striped = false, onClick = {})
            BomResultRow(previewResults[1], selected = false, striped = true, onClick = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BomSearch_Idle_Preview() {
    AppTheme {
        BomSearch(onQueryChanged = {}, onSelect = {}, searchState = NetworkState.Idle)
    }
}

@Preview(showBackground = true)
@Composable
fun BomSearch_Pending_Preview() {
    AppTheme {
        BomSearch(onQueryChanged = {}, onSelect = {}, searchState = NetworkState.Loading)
    }
}

@Preview(showBackground = true)
@Composable
fun BomSearch_Error_Preview() {
    AppTheme {
        BomSearch(
            onQueryChanged = {},
            onSelect = {},
            searchState = NetworkState.Error(ErpError.Http(417, "Hello World"))
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BomSearch_NoResults_Preview() {
    AppTheme {
        BomSearch(onQueryChanged = {}, onSelect = {}, searchState = NetworkState.Success(emptyList()))
    }
}

@Preview(showBackground = true)
@Composable
fun BomSearch_ResultList_Preview() {
    AppTheme {
        BomSearch(onQueryChanged = {}, onSelect = {}, searchState = NetworkState.Success(previewResults))
    }
}
