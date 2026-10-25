package bg.butcher.pda.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bg.butcher.pda.ui.theme.PreviewTheme
import bg.butcher.pda.ui.theme.ThemePreviews

/** The page's main action, as tall as the outlined buttons beside it. */
@Composable
fun SubmitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        enabled = !isLoading && enabled,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(.9f).heightIn(min = 56.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(label)
        }
    }
}

@ThemePreviews
@Composable
private fun SubmitButton_Preview() {
    PreviewTheme {
        SubmitButton(label = "Submit", onClick = {})
    }
}

@ThemePreviews
@Composable
private fun SubmitButton_Loading_Preview() {
    PreviewTheme {
        SubmitButton(label = "Submit", onClick = {}, isLoading = true)
    }
}
