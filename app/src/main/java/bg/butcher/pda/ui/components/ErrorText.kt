package bg.butcher.pda.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import bg.butcher.pda.R
import bg.butcher.pda.network.ErpError

@Composable
fun errorText(error: ErpError?): String = when (error) {
    is ErpError.Network -> stringResource(R.string.network_error)
    is ErpError.NotConfigured -> stringResource(R.string.settings_missing_error)
    is ErpError.Unauthorized -> stringResource(R.string.unauthorized_error)
    is ErpError.Forbidden -> stringResource(R.string.forbidden_error)
    is ErpError.UnexpectedResponse -> stringResource(R.string.unexpected_response_error)
    is ErpError.Http -> error.message ?: stringResource(R.string.error_message)
    null -> stringResource(R.string.error_message)
}
