package bg.butcher.pda.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.components.BomSelect
import bg.butcher.pda.ui.components.ExpiryInput
import bg.butcher.pda.ui.components.QuantityInput
import bg.butcher.pda.ui.components.RowWithLabel
import bg.butcher.pda.ui.components.SubmitButton
import bg.butcher.pda.ui.theme.AppTheme
import bg.butcher.pda.util.formatDecimal

@Composable
private fun quantityTooSmallMessage(state: FormUiState): String? {
    val ingredient = state.unworkableIngredients.firstOrNull() ?: return null
    return stringResource(
        R.string.quantity_too_small,
        ingredient.itemName,
        formatDecimal(ingredient.quantity),
        ingredient.uom,
    )
}

@Composable
fun StartScreen(
    state: FormUiState,
    onQuantityChanged: (String) -> Unit,
    onExplodeChanged: (Boolean) -> Unit,
    onExpiryChanged: (String) -> Unit,
    onFinishedChanged: (Boolean) -> Unit,
    onQueryChanged: (String) -> Unit,
    onItemSelected: (BomSearchResultItem) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.selectedItem
    val quantityFocus = remember { FocusRequester() }
    Column(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            BomSelect(
                onQueryChanged = onQueryChanged,
                onItemSelected = onItemSelected,
                selectedItem = selected,
                searchState = state.searchState,
                onPicked = { quantityFocus.requestFocus() },
            )
            QuantityInput(
                value = state.quantityText,
                onValueChange = onQuantityChanged,
                uom = selected?.uom.orEmpty(),
                wholeNumber = selected?.isWholeNumber == true,
                extraError = quantityTooSmallMessage(state),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(quantityFocus),
                imeAction = ImeAction.Next,
            )
            ExpiryInput(
                value = state.expiryDaysText,
                onValueChange = onExpiryChanged,
                defaultDays = selected?.shelfLife?.takeIf { state.expiryTyped && it > 0 },
                modifier = Modifier.fillMaxWidth(),
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
            RowWithLabel(
                label = R.string.finished_label,
                content = {
                    Checkbox(
                        checked = state.isFinished,
                        onCheckedChange = onFinishedChanged
                    )
                }
            )
        }
        SubmitButton(
            label = stringResource(R.string.continue_label),
            onClick = onContinue,
            enabled = state.canContinue,
            isLoading = state.fetchState is NetworkState.Loading,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 10.dp)
        )
    }
}

private val previewBom = BomSearchResultItem(
    name = "BOM-BEEF_MEATBALLS-001",
    item = "BEEF_MEATBALLS",
    itemName = "Meatballs (Beef)",
    uom = "Nos",
    isDefault = 1,
    shelfLife = 7,
    wholeNumber = 1,
)

@Composable
private fun StartScreenPreview(state: FormUiState) {
    AppTheme {
        StartScreen(
            state = state,
            onQueryChanged = {},
            onQuantityChanged = {},
            onExplodeChanged = {},
            onExpiryChanged = {},
            onFinishedChanged = {},
            onItemSelected = {},
            onContinue = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StartScreen_Empty_Preview() {
    StartScreenPreview(FormUiState())
}

@Preview(showBackground = true)
@Composable
fun StartScreen_Selected_Preview() {
    StartScreenPreview(
        FormUiState(selectedItem = previewBom, quantityText = "100", expiryDaysText = "5", expiryTyped = true)
    )
}

@Preview(showBackground = true)
@Composable
fun StartScreen_Loading_Preview() {
    StartScreenPreview(
        FormUiState(
            selectedItem = previewBom,
            quantityText = "100",
            expiryDaysText = "7",
            fetchState = NetworkState.Loading,
        )
    )
}
