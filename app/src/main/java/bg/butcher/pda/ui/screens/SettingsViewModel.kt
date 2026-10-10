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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingState(
    val value: String = "",
    val isValid: Boolean = false,
)

data class SettingsUiState(
    val baseUrl: SettingState = SettingState(),
    val apiKey: SettingState = SettingState(),
)

class SettingsViewModel(private val settingsRepository: SettingsRepository): ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Update settings each time UI state changes
        _uiState.onEach {
            val newSettings = PdaSettings(it.baseUrl.value, it.apiKey.value)
            settingsRepository.update(newSettings)
        }.launchIn(viewModelScope)

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
        _uiState.update { it.copy(baseUrl = SettingState(url, URLUtil.isValidUrl(url))) }
    }

    fun setApiKey(key: String) {
        _uiState.update { it.copy(apiKey = SettingState(key, key.isNotBlank())) }
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