package bg.butcher.pda.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.components.ErrorMessage
import bg.butcher.pda.ui.components.QuantityInput
import bg.butcher.pda.ui.components.RowWithLabel
import bg.butcher.pda.ui.components.SearchField
import bg.butcher.pda.ui.components.SubmitButton
import bg.butcher.pda.ui.theme.AppTheme
import kotlinx.coroutines.launch

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
    val minHeight = if (searchState is NetworkState.Idle) { 0.dp } else { 100.dp }

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
                                itemsIndexed(searchState.data, key = { _, i -> i.name }) { index, item ->
                                    val focusRequester = remember { FocusRequester() }
                                    val isSelected = item.name == selectedItem?.name
                                    val color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else if (index % 2 == 0) {
                                        MaterialTheme.colorScheme.surfaceBright
                                    } else {
                                        MaterialTheme.colorScheme.surfaceDim
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester)
                                            .focusable()
                                            .clickable(
                                                role = Role.Button,
                                                onClick = {
                                                    selectedItem = item
                                                    focusRequester.requestFocus()
                                                }
                                            )
                                            .background(color),
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp).weight(1f)) {
                                            Text(
                                                text = item.itemName,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = "${item.name} (${item.uom})",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        if (item.isDefaultBom) {
                                            val tint = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                tint = tint,
                                                contentDescription = "Default",
                                                modifier = Modifier.padding(end = 10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 5.dp)
                                .height(IntrinsicSize.Min)
                                .fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onSelect(null) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.cancel_label))
                            }
                            Button(
                                onClick = { onSelect(selectedItem) },
                                enabled = selectedItem != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.select_label))
                            }
                        }
                    }
                }
                else -> {}
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

    TextButton(
        onClick = { showBottomSheet = true },
        modifier = modifier,
    ) {
        Text(selectedItem?.let { "${it.itemName} (${it.name})" } ?: stringResource(R.string.none_label))
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
                    if (it != null) { onItemSelected(it) }
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

@Composable
fun StartScreen(
    state: FormUiState,
    onQuantityChanged: (String) -> Unit,
    onExplodeChanged: (Boolean) -> Unit,
    onQueryChanged: (String) -> Unit,
    onItemSelected: (BomSearchResultItem) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.padding(20.dp)
    ) {
        RowWithLabel(
            label = R.string.bom_label,
            content = {
                BomSelect(
                    onQueryChanged = onQueryChanged,
                    onItemSelected = onItemSelected,
                    selectedItem = state.selectedItem,
                    searchState = state.searchState,
                )
            }
        )
        QuantityInput(
            value = state.quantityText,
            onValueChange = onQuantityChanged,
            uom = state.selectedItem?.uom.orEmpty()
        )
        RowWithLabel(
            label = R.string.explode_label,
            content = {
                Checkbox(
                    checked = state.explode,
                    onCheckedChange = onExplodeChanged
                )
            }
        )
        SubmitButton(
            label = stringResource(R.string.continue_label),
            onClick = onContinue,
            enabled = state.canContinue,
            isLoading = state.fetchState is NetworkState.Loading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StartScreen_Loading() {
    AppTheme {
        StartScreen(
            state = FormUiState(
                fetchState = NetworkState.Loading
            ),
            onQueryChanged = {},
            onQuantityChanged = {},
            onExplodeChanged = {},
            onItemSelected = {},
            onContinue = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BomSelect_None_Preview() {
    AppTheme {
        RowWithLabel(
            label = R.string.bom_label,
            content = {
                BomSelect(
                    onQueryChanged = {},
                    onItemSelected = {},
                    selectedItem = null,
                    searchState = NetworkState.Idle
                )
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BomSelect_Some_Preview() {
    AppTheme {
        RowWithLabel(
            label = R.string.bom_label,
            content = {
                BomSelect(
                    onQueryChanged = {},
                    onItemSelected = {},
                    selectedItem = BomSearchResultItem(
                        name = "BOM.001",
                        item = "SKU.001",
                        itemName = "Item Name",
                        uom = "Kgs",
                        isDefault = 1,
                    ),
                    searchState = NetworkState.Idle
                )
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BomSearch_Idle_Preview() {
    AppTheme {
        BomSearch(
            onQueryChanged = {},
            onSelect = {},
            searchState = NetworkState.Idle
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BomSearch_Pending_Preview() {
    AppTheme {
        BomSearch(
            onQueryChanged = {},
            onSelect = {},
            searchState = NetworkState.Loading
        )
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
        BomSearch(
            onQueryChanged = {},
            onSelect = {},
            searchState = NetworkState.Success(emptyList()),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BomSearch_ResultList_Preview() {
    AppTheme {
        BomSearch(
            onQueryChanged = {},
            onSelect = {},
            searchState = NetworkState.Success(listOf(
                BomSearchResultItem("BOM.001", "SKU.001", "Meatballs with a stupid long name that may overflow", "Nos", 1),
                BomSearchResultItem("BOM.002", "SKU.002", "Mince Meat", "Kgs", 1),
                BomSearchResultItem("BOM.003", "SKU.003", "Kebabs", "Kgs", 1),
                BomSearchResultItem("BOM.004", "SKU.003", "Kebabs", "Kgs", 1),
                BomSearchResultItem("BOM.005", "SKU.003", "Kebabs", "Kgs", 1),
                BomSearchResultItem("BOM.006", "SKU.003", "Kebabs", "Kgs", 1),
                BomSearchResultItem("BOM.007", "SKU.003", "Kebabs", "Kgs", 1),
                BomSearchResultItem("BOM.008", "SKU.003", "Kebabs", "Kgs", 1),
            )),
        )
    }
}