package bg.butcher.pda.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import bg.butcher.pda.R
import bg.butcher.pda.ui.theme.PreviewTheme
import bg.butcher.pda.ui.theme.ThemePreviews
import bg.butcher.pda.util.validateQuantity

@Composable
fun QuantityInput(
    value: String,
    onValueChange: (String) -> Unit,
    uom: String,
    wholeNumber: Boolean,
    modifier: Modifier = Modifier,
    // Shown when the value itself is valid, e.g. a rule that needs the server's data
    extraError: String? = null,
    // Next moves focus on to the following field
    imeAction: ImeAction = ImeAction.Done,
    // Enter/Done; returns false to fall back to putting the keyboard away
    onDone: () -> Boolean = { false },
) {
    val error = validateQuantity(value, wholeNumber)?.let { stringResource(it) } ?: extraError
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.quantity_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            // Hides the decimal key on most keyboards; the validator is the real check
            keyboardType = if (wholeNumber) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { if (!onDone()) defaultKeyboardAction(ImeAction.Done) }),
        suffix = {
            if (uom.isNotEmpty()) {
                Text(uom)
            }
        },
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        modifier = modifier
    )
}

@ThemePreviews
@Composable
private fun QuantityInput_Valid_Preview() {
    PreviewTheme {
        QuantityInput(
            value = "12.3",
            onValueChange = {},
            uom = "Kgs",
            wholeNumber = false,
        )
    }
}

@ThemePreviews
@Composable
private fun QuantityInput_WholeNumber_Preview() {
    PreviewTheme {
        QuantityInput(
            value = "2.5",
            onValueChange = {},
            uom = "Nos",
            wholeNumber = true,
        )
    }
}

@ThemePreviews
@Composable
private fun QuantityInput_Invalid_Preview() {
    PreviewTheme {
        QuantityInput(
            value = "",
            onValueChange = {},
            uom = "Kgs",
            wholeNumber = false,
        )
    }
}

@ThemePreviews
@Composable
private fun QuantityInput_NoUOM_Preview() {
    PreviewTheme {
        QuantityInput(
            value = "12.3",
            onValueChange = {},
            uom = "",
            wholeNumber = false,
        )
    }
}