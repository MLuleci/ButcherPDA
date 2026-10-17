package bg.butcher.pda.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bg.butcher.pda.R

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
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val canSave = viewModel.isValid() && uiState.isDirty
    Column (modifier = modifier) {
        ValidatedTextField(
            label = "API URL",
            state = uiState.baseUrl,
            onChanged = { viewModel.setBaseUrl(it) }
        )
        ValidatedTextField(
            label = "API Key",
            state = uiState.apiKey,
            onChanged = { viewModel.setApiKey(it) }
        )
        Button(
            onClick = { viewModel.save() },
            enabled = canSave
        ) {
            Text(stringResource(R.string.save_label))
        }
    }
}