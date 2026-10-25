package bg.butcher.pda.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bg.butcher.pda.R
import bg.butcher.pda.data.PaperSize
import bg.butcher.pda.data.PaperType
import bg.butcher.pda.data.labelHeightRange
import bg.butcher.pda.ui.components.SubmitButton

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

@StringRes
private fun PaperType.label(): Int = when (this) {
    PaperType.LABELS -> R.string.paper_type_labels
    PaperType.CONTINUOUS -> R.string.paper_type_continuous
}

/** A read-only field that opens a list of [options]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionSelect(
    @StringRes label: Int,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> Int,
    onSelect: (T) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.padding(10.dp),
    ) {
        OutlinedTextField(
            value = stringResource(optionLabel(selected)),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(optionLabel(option))) },
                    onClick = {
                        onSelect(option)
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
        OptionSelect(
            label = R.string.paper_size_label,
            options = PaperSize.entries,
            selected = uiState.paperSize,
            optionLabel = { it.label() },
            onSelect = viewModel::setPaperSize,
            enabled = uiState.isLoaded,
        )
        OptionSelect(
            label = R.string.paper_type_label,
            options = PaperType.entries,
            selected = uiState.paperType,
            optionLabel = { it.label() },
            onSelect = viewModel::setPaperType,
            enabled = uiState.isLoaded,
        )
        val heightRange = labelHeightRange(uiState.paperType)
        ValidatedTextField(
            label = stringResource(R.string.label_height_label),
            error = stringResource(R.string.invalid_label_height, heightRange.first, heightRange.last),
            state = uiState.labelHeight,
            onChanged = { viewModel.setLabelHeight(it) },
            enabled = uiState.isLoaded,
            keyboardType = KeyboardType.Number,
            suffix = stringResource(R.string.millimetres),
        )
        SubmitButton(
            label = stringResource(R.string.save_label),
            onClick = { viewModel.save() },
            enabled = uiState.canSave,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 10.dp)
        )
    }
}
