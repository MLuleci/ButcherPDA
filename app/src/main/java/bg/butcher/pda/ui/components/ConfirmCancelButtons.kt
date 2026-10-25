package bg.butcher.pda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bg.butcher.pda.ui.theme.PreviewTheme
import bg.butcher.pda.ui.theme.ThemePreviews

@Composable
fun ConfirmCancelButtons(
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
            Text(cancelLabel)
        }
        Button(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
            Text(confirmLabel)
        }
    }
}

@ThemePreviews
@Composable
private fun ConfirmCancelButtons_Preview() {
    PreviewTheme {
        ConfirmCancelButtons(confirmLabel = "Select", cancelLabel = "Cancel", onConfirm = {}, onCancel = {})
    }
}

@ThemePreviews
@Composable
private fun ConfirmCancelButtons_Disabled_Preview() {
    PreviewTheme {
        ConfirmCancelButtons(
            confirmLabel = "Select",
            cancelLabel = "Cancel",
            onConfirm = {},
            onCancel = {},
            confirmEnabled = false,
        )
    }
}
