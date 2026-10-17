package bg.butcher.pda.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import bg.butcher.pda.R
import bg.butcher.pda.ui.theme.AppTheme

@Composable
fun QuantityInput(
    uom: String,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    initialValue: Float? = null,
) {
    val state = rememberTextFieldState(initialValue?.toString() ?: "")
    val text = state.text.toString()
    val value = text.toFloatOrNull()

    LaunchedEffect(value) {
        if (value != null && value > 0f) {
            onValueChanged(value)
        }
    }
    OutlinedTextField(
        label = { Text(stringResource(R.string.quantity_label)) },
        state = state,
        lineLimits = TextFieldLineLimits.SingleLine,
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
        inputTransformation = InputTransformation.then {
            val text = toString()
            if (text.isNotEmpty()) {
                val value = text.toFloatOrNull()
                if (value == null || value < 0f) {
                    revertAllChanges()
                }
            }
        },
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun QuantityInputPreview() {
    AppTheme {
        QuantityInput( "Kgs", {})
    }
}