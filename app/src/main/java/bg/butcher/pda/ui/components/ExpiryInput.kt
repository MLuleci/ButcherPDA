package bg.butcher.pda.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
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

@Composable
fun ExpiryPicker(
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    initialValue: Int = 0,
) {
    val state = rememberTextFieldState(initialValue.toString())
    val text = state.text.toString()
    val value = text.toIntOrNull()
    LaunchedEffect(value) {
        if (value != null && value >= 0) {
            onValueChanged(value)
        }
    }
    Column {
        TextField(
            state = state,
            lineLimits = TextFieldLineLimits.SingleLine,
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
            inputTransformation = InputTransformation.then {
                val text = toString()
                if (text.isNotEmpty()) {
                    val value = text.toIntOrNull()
                    if (value == null || value < 0) {
                        revertAllChanges()
                    }
                }
            },
            modifier = modifier
        )
        if (value != null) {
            val format = DateFormat.getDateInstance(DateFormat.MEDIUM)
            val date = dateFromToday(value)
                .atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
            Text(
                text = format.format(Date(date)),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ExpiryPickerPreview() {
    AppTheme {
        ExpiryPicker(
            onValueChanged = {},
            initialValue = 5
        )
    }
}