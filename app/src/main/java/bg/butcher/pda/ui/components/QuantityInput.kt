package bg.butcher.pda.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import bg.butcher.pda.R
import bg.butcher.pda.ui.theme.AppTheme

fun validateQuantity(text: String): Int? {
    if (text.isBlank()) {
        return R.string.value_required
    }

    val value = text.toFloatOrNull()
        ?: return R.string.invalid_number

    if (!value.isFinite() || value <= 0f) {
        return R.string.must_be_positive
    }

    return null
}
@Composable
fun QuantityInput(
    value: String,
    onValueChange: (String) -> Unit,
    uom: String,
    modifier: Modifier = Modifier,
) {
    val error = validateQuantity(value)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.quantity_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        trailingIcon = {
            if (uom.isNotEmpty()) {
                Text(uom, style = MaterialTheme.typography.labelSmall)
            }
        },
        isError = error != null,
        supportingText = {
            error?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error)
            }
        },
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun QuantityInput_Valid_Preview() {
    AppTheme {
        QuantityInput(
            value = "12.3",
            onValueChange = {},
            uom ="Kgs"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun QuantityInput_Invalid_Preview() {
    AppTheme {
        QuantityInput(
            value = "",
            onValueChange = {},
            uom ="Kgs"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun QuantityInput_NoUOM_Preview() {
    AppTheme {
        QuantityInput(
            value = "12.3",
            onValueChange = {},
            uom = ""
        )
    }
}