package bg.butcher.pda.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.ui.components.BomSelect
import bg.butcher.pda.ui.components.CheckboxRow
import bg.butcher.pda.ui.components.ExpiryInput
import bg.butcher.pda.ui.components.QuantityInput
import bg.butcher.pda.ui.components.SubmitButton
import bg.butcher.pda.ui.state.NetworkState
import bg.butcher.pda.ui.theme.PreviewTheme
import bg.butcher.pda.ui.theme.ThemePreviews
import bg.butcher.pda.util.formatDecimal
import kotlinx.coroutines.flow.first

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
    val windowInfo = LocalWindowInfo.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Counts picks, so picking again focuses the quantity again
    var picks by remember { mutableIntStateOf(0) }
    LaunchedEffect(picks) {
        if (picks == 0) return@LaunchedEffect
        // The sheet is its own window; the keyboard only opens once the screen's window has focus back
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        quantityFocus.requestFocus()
        keyboard?.show()
    }
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
                onPicked = { picks++ },
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
            CheckboxRow(
                label = R.string.explode_label,
                checked = state.explode,
                onCheckedChange = onExplodeChanged,
            )
            CheckboxRow(
                label = R.string.finished_label,
                checked = state.isFinished,
                onCheckedChange = onFinishedChanged,
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
    PreviewTheme {
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

@ThemePreviews
@Composable
private fun StartScreen_Empty_Preview() {
    StartScreenPreview(FormUiState())
}

@ThemePreviews
@Composable
private fun StartScreen_Selected_Preview() {
    StartScreenPreview(
        FormUiState(selectedItem = previewBom, quantityText = "100", expiryDaysText = "5", expiryTyped = true)
    )
}

@ThemePreviews
@Composable
private fun StartScreen_Loading_Preview() {
    StartScreenPreview(
        FormUiState(
            selectedItem = previewBom,
            quantityText = "100",
            expiryDaysText = "7",
            fetchState = NetworkState.Loading,
        )
    )
}
