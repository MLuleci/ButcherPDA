package bg.butcher.pda.ui.components

import androidx.compose.foundation.text.KeyboardOptions
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import java.text.DateFormat
import java.util.Date
import kotlin.time.Clock

/** A calendar day, so it doesn't drift around daylight-saving changes. */
fun dateFromToday(days: Int): LocalDate =
    Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(days, DateTimeUnit.DAY)

fun validateDays(text: String): Int? {
    if (text.isBlank()) return R.string.value_required
    val value = text.trim().toIntOrNull() ?: return R.string.whole_number_required
    if (value <= 0) return R.string.must_be_positive
    return null
}

private fun formatDate(date: LocalDate): String {
    val millis = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    return DateFormat.getDateInstance(DateFormat.FULL).format(Date(millis))
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

@Preview(showBackground = true)
@Composable
fun ExpiryInputPreview() {
    AppTheme {
        ExpiryInput(
            value = "5",
            onValueChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ExpiryInput_Overridden_Preview() {
    AppTheme {
        ExpiryInput(
            value = "3",
            onValueChange = {},
            defaultDays = 7,
        )
    }
}
