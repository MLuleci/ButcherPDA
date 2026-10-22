package bg.butcher.pda.ui.components

import androidx.compose.foundation.layout.Column
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import java.text.DateFormat
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

fun dateFromToday(n: Int): LocalDate {
    return Clock.System.now()
        .plus(n.days)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

fun validateDays(text: String): Int? {
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
fun ExpiryInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = validateDays(value)
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.expiry_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                Text(
                    text = "days",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            isError = error != null,
            supportingText = {
                if (error != null) {
                    Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                } else {
                    value.toIntOrNull()?.let {
                        val dateFormat = DateFormat.getDateInstance(DateFormat.FULL)
                        val date = dateFromToday(it)
                            .atStartOfDayIn(TimeZone.currentSystemDefault())
                            .toEpochMilliseconds()
                        Text(dateFormat.format(Date(date)))
                    }
                }
            },
            modifier = modifier
        )
    }
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