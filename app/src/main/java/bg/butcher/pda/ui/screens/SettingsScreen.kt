package bg.butcher.pda.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import bg.butcher.pda.BuildConfig
import bg.butcher.pda.R
import bg.butcher.pda.data.AvailableUpdate
import bg.butcher.pda.data.PaperSize
import bg.butcher.pda.data.PaperType
import bg.butcher.pda.data.labelHeightRange
import bg.butcher.pda.ui.components.SubmitButton
import bg.butcher.pda.ui.theme.PreviewTheme
import bg.butcher.pda.ui.theme.ThemePreviews

@Composable
fun ValidatedTextField(
    label: String,
    error: String,
    state: SettingState,
    onChanged: (String) -> Unit,
    enabled: Boolean,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    suffix: String? = null,
) {
    val showError = enabled && !state.isValid
    OutlinedTextField(
        modifier = modifier,
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
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

@StringRes
private fun UpdateError.message(): Int = when (this) {
    UpdateError.CHECK_FAILED -> R.string.update_check_failed
    UpdateError.DOWNLOAD_FAILED -> R.string.update_download_failed
    UpdateError.NOT_THIS_APP -> R.string.update_not_this_app
    UpdateError.WRONG_SIGNATURE -> R.string.update_wrong_signature
}

/** The installed version, a button to check GitHub for a newer one, and what the check found. */
@Composable
private fun UpdateSection(
    state: UpdateState,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge)
        when (state) {
            UpdateState.Idle -> {}
            UpdateState.Checking -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            UpdateState.UpToDate -> Text(stringResource(R.string.update_up_to_date))
            is UpdateState.Available -> Text(stringResource(R.string.update_available, state.update.versionName))
            is UpdateState.Downloading -> {
                Text(stringResource(R.string.update_downloading, state.update.versionName))
                if (state.progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
            is UpdateState.Ready -> Text(stringResource(R.string.update_available, state.update.versionName))
            is UpdateState.Failed -> Text(stringResource(state.error.message()), color = MaterialTheme.colorScheme.error)
        }
        val canInstall = state is UpdateState.Available || state is UpdateState.Ready
        OutlinedButton(
            onClick = if (canInstall) onInstall else onCheck,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(if (canInstall) R.string.update_install else R.string.update_check))
        }
    }
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onPaperSizeChange: (PaperSize) -> Unit,
    onPaperTypeChange: (PaperType) -> Unit,
    onLabelHeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            ValidatedTextField(
                label = stringResource(R.string.base_url_label),
                error = stringResource(R.string.invalid_base_url),
                state = uiState.baseUrl,
                onChanged = onBaseUrlChange,
                enabled = uiState.isLoaded,
                placeholder = "https://erp.example.com",
                keyboardType = KeyboardType.Uri,
                modifier = Modifier.fillMaxWidth(),
            )
            ValidatedTextField(
                label = stringResource(R.string.api_key_label),
                error = stringResource(R.string.invalid_api_key),
                state = uiState.apiKey,
                onChanged = onApiKeyChange,
                enabled = uiState.isLoaded,
                placeholder = "api_key:api_secret",
                keyboardType = KeyboardType.Password,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
            )
            OptionSelect(
                label = R.string.paper_size_label,
                options = PaperSize.entries,
                selected = uiState.paperSize,
                optionLabel = { it.label() },
                onSelect = onPaperSizeChange,
                enabled = uiState.isLoaded,
            )
            OptionSelect(
                label = R.string.paper_type_label,
                options = PaperType.entries,
                selected = uiState.paperType,
                optionLabel = { it.label() },
                onSelect = onPaperTypeChange,
                enabled = uiState.isLoaded,
            )
            val heightRange = labelHeightRange(uiState.paperType)
            ValidatedTextField(
                label = stringResource(R.string.label_height_label),
                error = stringResource(R.string.invalid_label_height, heightRange.first, heightRange.last),
                state = uiState.labelHeight,
                onChanged = onLabelHeightChange,
                enabled = uiState.isLoaded,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
                suffix = stringResource(R.string.millimetres),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            UpdateSection(
                state = uiState.update,
                onCheck = onCheckForUpdate,
                onInstall = onInstallUpdate,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SubmitButton(
            label = stringResource(R.string.save_label),
            onClick = onSave,
            enabled = uiState.canSave,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 10.dp)
        )
    }
}

@Composable
private fun SettingsScreenPreview(uiState: SettingsUiState) {
    PreviewTheme {
        SettingsScreen(
            uiState = uiState,
            onBaseUrlChange = {},
            onApiKeyChange = {},
            onPaperSizeChange = {},
            onPaperTypeChange = {},
            onLabelHeightChange = {},
            onSave = {},
            onCheckForUpdate = {},
            onInstallUpdate = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@ThemePreviews
@Composable
private fun SettingsScreen_Preview() {
    SettingsScreenPreview(
        SettingsUiState(
            isLoaded = true,
            isDirty = true,
            baseUrl = SettingState("https://erp.example.bg", isValid = true),
            apiKey = SettingState("key:secret", isValid = true),
            paperType = PaperType.CONTINUOUS,
            labelHeight = SettingState("40", isValid = true),
        )
    )
}

@ThemePreviews
@Composable
private fun SettingsScreen_Invalid_Preview() {
    SettingsScreenPreview(
        SettingsUiState(
            isLoaded = true,
            isDirty = true,
            baseUrl = SettingState("http://erp.example.bg", isValid = false),
            apiKey = SettingState("key", isValid = false),
            paperType = PaperType.CONTINUOUS,
            labelHeight = SettingState("30", isValid = false),
        )
    )
}

@ThemePreviews
@Composable
private fun SettingsScreen_UpdateAvailable_Preview() {
    SettingsScreenPreview(
        SettingsUiState(
            isLoaded = true,
            baseUrl = SettingState("https://erp.example.bg", isValid = true),
            apiKey = SettingState("key:secret", isValid = true),
            labelHeight = SettingState("40", isValid = true),
            update = UpdateState.Downloading(AvailableUpdate(58, "1b641ad", "", 4_000_000), progress = 0.4f),
        )
    )
}
