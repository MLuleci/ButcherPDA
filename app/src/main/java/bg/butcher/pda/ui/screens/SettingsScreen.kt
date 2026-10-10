package bg.butcher.pda.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bg.butcher.pda.R
import bg.butcher.pda.ui.theme.AppTheme

@Composable
fun ValidatedTextField(
    label: String,
    state: SettingState,
    onChanged: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        value = state.value,
        onValueChange = onChanged,
        label = { Text(label) },
        isError = !state.isValid,
        supportingText = {
            if (!state.isValid) {
                Text(stringResource(R.string.invalid_label))
            }
        },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done
        )
    )
}

@Composable
fun SettingsScreenBody(
    state: SettingsUiState,
    onUrlChanged: (String) -> Unit,
    onKeyChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column (modifier = modifier) {
        ValidatedTextField(
            label = "API URL",
            state = state.baseUrl,
            onChanged = onUrlChanged
        )
        ValidatedTextField(
            label = "API Key",
            state = state.apiKey,
            onChanged = onKeyChanged
        )
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsScreenBody(
        state = uiState,
        onUrlChanged = { viewModel.setBaseUrl(it) },
        onKeyChanged = { viewModel.setApiKey(it) },
        modifier = modifier,
    )
}

@Preview
@Composable
fun SettingsScreenBodyPreview() {
    AppTheme {
        SettingsScreenBody(
            state = SettingsUiState(
                baseUrl = SettingState("http://localhost/", true),
                apiKey = SettingState("dummy_key",false),
            ),
            onUrlChanged = {},
            onKeyChanged = {}
        )
    }
}