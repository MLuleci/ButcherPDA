package bg.butcher.pda.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bg.butcher.pda.R
import bg.butcher.pda.data.LABEL_HEIGHT_RANGE_MM
import bg.butcher.pda.data.PaperSize

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
    suffix: String? = null,
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
        suffix = suffix?.let { { Text(it) } },
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

@StringRes
private fun PaperSize.label(): Int = when (this) {
    PaperSize.MM_80 -> R.string.paper_80mm
    PaperSize.MM_58 -> R.string.paper_58mm
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaperSizeSelect(selected: PaperSize, onSelect: (PaperSize) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.padding(10.dp),
    ) {
        OutlinedTextField(
            value = stringResource(selected.label()),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.paper_size_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PaperSize.entries.forEach { size ->
                DropdownMenuItem(
                    text = { Text(stringResource(size.label())) },
                    onClick = {
                        onSelect(size)
                        expanded = false
                    },
                )
            }
        }
    }
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
        PaperSizeSelect(
            selected = uiState.paperSize,
            onSelect = viewModel::setPaperSize,
            enabled = uiState.isLoaded,
        )
        ValidatedTextField(
            label = stringResource(R.string.label_height_label),
            error = stringResource(R.string.invalid_label_height, LABEL_HEIGHT_RANGE_MM.first, LABEL_HEIGHT_RANGE_MM.last),
            state = uiState.labelHeight,
            onChanged = { viewModel.setLabelHeight(it) },
            enabled = uiState.isLoaded,
            keyboardType = KeyboardType.Number,
            suffix = stringResource(R.string.millimetres),
        )
        Button(
            onClick = { viewModel.save() },
            enabled = uiState.canSave
        ) {
            Text(stringResource(R.string.save_label))
        }
    }
}
