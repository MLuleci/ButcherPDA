package bg.butcher.pda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.ui.theme.PreviewTheme
import bg.butcher.pda.ui.theme.ThemePreviews
import java.io.IOException

@Composable
fun ErrorMessage(
    modifier: Modifier = Modifier,
    error: ErpError? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        Text(
            text = errorText(error),
            color = MaterialTheme.colorScheme.error
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(R.string.retry_label).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun ErrorMessage_Retry_Preview() {
    PreviewTheme {
        ErrorMessage(error = ErpError.Network(IOException()), onRetry = {})
    }
}

@ThemePreviews
@Composable
private fun ErrorMessage_ServerMessage_Preview() {
    PreviewTheme {
        ErrorMessage(error = ErpError.Http(417, "Batch BEEF.0001 has expired"))
    }
}
