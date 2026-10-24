package bg.butcher.pda.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bg.butcher.pda.R

@Composable
fun ValidatedTextField(
    label: String,
    error: String,
    state: SettingState,
    onChanged: (String) -> Unit,
    enabled: Boolean,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val showError = enabled && !state.isValid
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        value = state.value,
        onValueChange = onChanged,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        isError = showError,
        supportingText = {
            if (showError) {
                Text(error)
            }
        },
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = ImeAction.Done
        )
    )
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        ValidatedTextField(
            label = stringResource(R.string.base_url_label),
            error = stringResource(R.string.invalid_base_url),
            state = uiState.baseUrl,
            onChanged = { viewModel.setBaseUrl(it) },
            enabled = uiState.isLoaded,
            placeholder = "https://erp.example.com",
            keyboardType = KeyboardType.Uri,
        )
        ValidatedTextField(
            label = stringResource(R.string.api_key_label),
            error = stringResource(R.string.invalid_api_key),
            state = uiState.apiKey,
            onChanged = { viewModel.setApiKey(it) },
            enabled = uiState.isLoaded,
            placeholder = "api_key:api_secret",
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            onClick = { viewModel.save() },
            enabled = uiState.canSave
        ) {
            Text(stringResource(R.string.save_label))
        }
    }
}
