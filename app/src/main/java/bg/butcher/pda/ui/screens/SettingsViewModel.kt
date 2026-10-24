package bg.butcher.pda.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import bg.butcher.pda.MainApplication
import bg.butcher.pda.data.PdaSettings
import bg.butcher.pda.data.SettingsRepository
import bg.butcher.pda.data.normaliseApiKey
import bg.butcher.pda.data.normaliseBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

private const val TAG = "SettingsViewModel"

data class SettingState(
    val value: String = "",
    val isValid: Boolean = false,
)

data class SettingsUiState(
    val isLoaded: Boolean = false,
    val isDirty: Boolean = false,
    val baseUrl: SettingState = SettingState(),
    val apiKey: SettingState = SettingState(),
) {
    val canSave: Boolean
        get() = isLoaded && isDirty && baseUrl.isValid && apiKey.isValid
}

private fun baseUrlState(url: String) = SettingState(url, normaliseBaseUrl(url) != null)

private fun apiKeyState(key: String) = SettingState(key, normaliseApiKey(key) != null)

class SettingsViewModel(private val settingsRepository: SettingsRepository): ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _uiState.update {
                // Keep anything typed while the settings were loading
                if (it.isDirty) {
                    it.copy(isLoaded = true)
                } else {
                    it.copy(
                        isLoaded = true,
                        baseUrl = baseUrlState(settings.baseUrl),
                        apiKey = apiKeyState(settings.apiKey),
                    )
                }
            }
        }
    }

    fun setBaseUrl(url: String) {
        _uiState.update { it.copy(isDirty = true, baseUrl = baseUrlState(url)) }
    }

    fun setApiKey(key: String) {
        _uiState.update { it.copy(isDirty = true, apiKey = apiKeyState(key)) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val baseUrl = normaliseBaseUrl(state.baseUrl.value) ?: return
        val apiKey = normaliseApiKey(state.apiKey.value) ?: return
        _uiState.update { it.copy(isDirty = false) }
        viewModelScope.launch {
            try {
                settingsRepository.update(PdaSettings(baseUrl.toString(), apiKey))
                _uiState.update {
                    if (it.isDirty) it else it.copy(baseUrl = baseUrlState(baseUrl.toString()), apiKey = apiKeyState(apiKey))
                }
            } catch (e: IOException) {
                Log.e(TAG, "save", e)
                _uiState.update { it.copy(isDirty = true) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as MainApplication)
                SettingsViewModel(settingsRepository = application.container.settingsRepository)
            }
        }
    }
}