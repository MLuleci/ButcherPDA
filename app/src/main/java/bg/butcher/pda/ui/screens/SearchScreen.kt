package bg.butcher.pda.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BatchFilters
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.components.BatchFilterSheet
import bg.butcher.pda.ui.components.BatchResultRow
import bg.butcher.pda.ui.components.Centered
import bg.butcher.pda.ui.components.ErrorMessage
import bg.butcher.pda.ui.components.SearchField
import bg.butcher.pda.ui.components.errorText
import bg.butcher.pda.ui.components.orderLabel
import bg.butcher.pda.ui.theme.PreviewTheme
import bg.butcher.pda.ui.theme.ThemePreviews
import bg.butcher.pda.util.today
import kotlinx.datetime.LocalDate
import java.io.IOException

@Composable
private fun filterSummary(filters: BatchFilters): String = listOfNotNull(
    filters.producedWithinDays?.let { stringResource(R.string.summary_last_days, it) }
        ?: stringResource(R.string.summary_any_date),
    stringResource(filters.sort.orderLabel(filters.descending)),
    stringResource(R.string.summary_hiding_expired).takeIf { filters.hideExpired },
).joinToString(" · ")

@Composable
private fun lookupErrorText(error: SearchLookupError): String = when (error) {
    SearchLookupError.NotFound -> stringResource(R.string.batch_not_found)
    SearchLookupError.NotABatchLabel -> stringResource(R.string.not_a_batch_label)
    is SearchLookupError.Failed -> errorText(error.error)
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFiltersChange: (BatchFilters) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (BatchDetails) -> Unit,
    showFilters: Boolean,
    onShowFiltersChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = today(),
) {
    val query = rememberTextFieldState(initialText = state.query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.collect { currentOnQueryChange(it) }
    }

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            SearchField(
                state = query,
                placeholder = stringResource(R.string.search_placeholder),
                onSearch = onSubmit,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onShowFiltersChange(true) }) {
                BadgedBox(badge = { if (!state.filters.isDefault) Badge() }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(
                            if (state.filters.isDefault) R.string.filters_label else R.string.filters_changed
                        ),
                    )
                }
            }
        }
        state.lookupError?.let {
            Text(lookupErrorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = filterSummary(state.filters),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowFiltersChange(true) }
                .padding(vertical = 8.dp)
        )
        if (state.lookingUp) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
        }

        Box(modifier = Modifier.weight(1f)) {
            when (val results = state.results) {
                is NetworkState.Idle, is NetworkState.Loading -> Centered { CircularProgressIndicator() }
                is NetworkState.Error -> Centered { ErrorMessage(error = results.error, onRetry = onRetry) }
                is NetworkState.Success -> if (results.data.isEmpty()) {
                    Centered {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.no_results))
                            if (!state.filters.isDefault) Text(stringResource(R.string.try_fewer_filters))
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(results.data, key = { it.name }) { batch ->
                            BatchResultRow(batch = batch, today = today, onClick = { onOpen(batch) })
                            HorizontalDivider()
                        }
                        if (state.canLoadMore || state.loadMoreError != null) {
                            item {
                                LoadMore(
                                    loading = state.loadingMore,
                                    error = state.loadMoreError,
                                    onClick = onLoadMore,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        BatchFilterSheet(
            filters = state.filters,
            onClose = {
                onShowFiltersChange(false)
                onFiltersChange(it)
            },
        )
    }
}

@Composable
private fun LoadMore(loading: Boolean, error: ErpError?, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        if (loading) {
            CircularProgressIndicator()
        } else {
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.load_more_label))
            }
        }
    }
}

private val previewBatches = listOf(
    BatchDetails(
        name = "PORK_SAUSAGE0012", item = "PORK_SAUSAGE", itemName = "Sausage (Pork)", stockUom = "Kg",
        manufacturingDate = LocalDate(2026, 10, 23), expiryDate = LocalDate(2026, 10, 28), batchQuantity = 20.0,
        defaultBom = "BOM-PORK_SAUSAGE-001",
    ),
    BatchDetails(
        name = "PORK_SAUSAGE0011", item = "PORK_SAUSAGE", itemName = "Sausage (Pork)", stockUom = "Kg",
        manufacturingDate = LocalDate(2026, 10, 21), expiryDate = LocalDate(2026, 10, 26), batchQuantity = 18.0,
        defaultBom = "BOM-PORK_SAUSAGE-001",
    ),
)

@Composable
private fun SearchScreenPreview(state: SearchUiState) {
    PreviewTheme {
        SearchScreen(
            state = state,
            onQueryChange = {},
            onSubmit = {},
            onFiltersChange = {},
            onRetry = {},
            onLoadMore = {},
            onOpen = {},
            showFilters = false,
            onShowFiltersChange = {},
            modifier = Modifier.fillMaxSize(),
            today = LocalDate(2026, 10, 25),
        )
    }
}

@ThemePreviews
@Composable
private fun SearchScreen_Results_Preview() {
    SearchScreenPreview(SearchUiState(results = NetworkState.Success(previewBatches), canLoadMore = true))
}

@ThemePreviews
@Composable
private fun SearchScreen_NoResults_Preview() {
    SearchScreenPreview(
        SearchUiState(
            query = "beef",
            filters = BatchFilters(hideExpired = true),
            results = NetworkState.Success(emptyList()),
            lookupError = SearchLookupError.NotFound,
        )
    )
}

@ThemePreviews
@Composable
private fun SearchScreen_Error_Preview() {
    SearchScreenPreview(SearchUiState(results = NetworkState.Error(ErpError.Network(IOException()))))
}
