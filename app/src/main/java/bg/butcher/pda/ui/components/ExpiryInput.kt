package bg.butcher.pda.ui.components

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
import bg.butcher.pda.util.dateFromToday
import bg.butcher.pda.util.formatDate

fun validateDays(text: String): Int? {
    if (text.isBlank()) return R.string.value_required
    val value = text.trim().toIntOrNull() ?: return R.string.whole_number_required
    if (value <= 0) return R.string.must_be_positive
    return null
}

@Composable
fun ExpiryInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    // The product's shelf life, shown when the operator has typed over it
    defaultDays: Int? = null,
) {
    val error = validateDays(value)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.expiry_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        suffix = { Text(stringResource(R.string.days)) },
        isError = error != null,
        supportingText = {
            val date = value.trim().toIntOrNull()?.takeIf { error == null }?.let { formatDate(dateFromToday(it)) }
            val hint = defaultDays?.let { stringResource(R.string.expiry_default_hint, it) }
            val text = listOfNotNull(error?.let { stringResource(it) } ?: date, hint).joinToString("\n")
            if (text.isNotEmpty()) Text(text)
        },
        modifier = modifier
    )
}

@ThemePreviews
@Composable
private fun ExpiryInputPreview() {
    PreviewTheme {
        ExpiryInput(
            value = "5",
            onValueChange = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ExpiryInput_Overridden_Preview() {
    PreviewTheme {
        ExpiryInput(
            value = "3",
            onValueChange = {},
            defaultDays = 7,
        )
    }
}
