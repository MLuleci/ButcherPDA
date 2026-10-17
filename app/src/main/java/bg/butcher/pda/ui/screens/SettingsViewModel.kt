package bg.butcher.pda.ui.screens

import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import bg.butcher.pda.MainApplication
import bg.butcher.pda.data.PdaSettings
import bg.butcher.pda.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingState(
    val value: String = "",
    val isValid: Boolean = false,
)

data class SettingsUiState(
    val isDirty: Boolean = false,
    val baseUrl: SettingState = SettingState(),
    val apiKey: SettingState = SettingState(),
)

class SettingsViewModel(private val settingsRepository: SettingsRepository): ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Initialize UI state from settings only once
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _uiState.update {
                it.copy(
                    baseUrl = SettingState(settings.baseUrl, URLUtil.isValidUrl(settings.baseUrl)),
                    apiKey = SettingState(settings.apiKey, settings.apiKey.isNotBlank())
                )
            }
        }
    }

    fun setBaseUrl(url: String) {
        _uiState.update {
            it.copy(
                isDirty = true,
                baseUrl = SettingState(url, URLUtil.isValidUrl(url))
            )
        }
    }

    fun setApiKey(key: String) {
        _uiState.update {
            it.copy(
                isDirty = true,
                apiKey = SettingState(key, key.isNotBlank())
            )
        }
    }

    fun isValid(): Boolean {
        return _uiState.value.baseUrl.isValid && _uiState.value.apiKey.isValid
    }

    fun save() {
        if (isValid()) {
            _uiState.update { it.copy(isDirty = false) }
            viewModelScope.launch {
                settingsRepository.update(
                    PdaSettings(
                        _uiState.value.baseUrl.value,
                        _uiState.value.apiKey.value
                    )
                )
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