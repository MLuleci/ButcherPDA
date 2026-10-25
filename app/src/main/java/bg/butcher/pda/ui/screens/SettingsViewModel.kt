package bg.butcher.pda.ui.screens

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import bg.butcher.pda.MainApplication
import bg.butcher.pda.R
import bg.butcher.pda.data.ApkProblem
import bg.butcher.pda.data.AvailableUpdate
import bg.butcher.pda.data.DEFAULT_LABEL_HEIGHT_MM
import bg.butcher.pda.data.PaperSize
import bg.butcher.pda.data.PaperType
import bg.butcher.pda.data.PdaSettings
import bg.butcher.pda.data.SettingsRepository
import bg.butcher.pda.data.UpdateRepository
import bg.butcher.pda.data.normaliseApiKey
import bg.butcher.pda.data.normaliseBaseUrl
import bg.butcher.pda.data.normaliseLabelHeight
import bg.butcher.pda.ui.state.JobSlot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    val paperSize: PaperSize = PaperSize.MM_80,
    val labelHeight: SettingState = labelHeightState(DEFAULT_LABEL_HEIGHT_MM.toString(), PaperType.LABELS),
    val paperType: PaperType = PaperType.LABELS,
    val update: UpdateState = UpdateState.Idle,
) {
    val canSave: Boolean
        get() = isLoaded && isDirty && baseUrl.isValid && apiKey.isValid && labelHeight.isValid
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    // Null until the size is known
    data class Downloading(val update: AvailableUpdate, val progress: Float?) : UpdateState
    // Downloaded and checked; the installer can be opened again if it was cancelled
    data class Ready(val update: AvailableUpdate, val apk: File) : UpdateState
    data class Failed(val error: UpdateError) : UpdateState
}

enum class UpdateError { CHECK_FAILED, DOWNLOAD_FAILED, NOT_THIS_APP, WRONG_SIGNATURE }

sealed interface SettingsUiEffect {
    /** Told once, as a toast. */
    data class ShowToast(@StringRes val messageResId: Int) : SettingsUiEffect
    data class Install(val apk: File) : SettingsUiEffect
}

private fun baseUrlState(url: String) = SettingState(url, normaliseBaseUrl(url) != null)

private fun apiKeyState(key: String) = SettingState(key, normaliseApiKey(key) != null)

private fun labelHeightState(mm: String, paperType: PaperType) =
    SettingState(mm, normaliseLabelHeight(mm, paperType) != null)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val checkApk: (File, Int) -> ApkProblem?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SettingsUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

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
                        paperSize = settings.paperSize,
                        labelHeight = labelHeightState(settings.labelHeightMm.toString(), settings.paperType),
                        paperType = settings.paperType,
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

    fun setPaperSize(size: PaperSize) {
        _uiState.update { it.copy(isDirty = true, paperSize = size) }
    }

    fun setPaperType(type: PaperType) {
        // A roll needs a taller label, so the height is checked again
        _uiState.update {
            it.copy(isDirty = true, paperType = type, labelHeight = labelHeightState(it.labelHeight.value, type))
        }
    }

    fun setLabelHeight(mm: String) {
        _uiState.update { it.copy(isDirty = true, labelHeight = labelHeightState(mm, it.paperType)) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val baseUrl = normaliseBaseUrl(state.baseUrl.value) ?: return
        val apiKey = normaliseApiKey(state.apiKey.value) ?: return
        val labelHeight = normaliseLabelHeight(state.labelHeight.value, state.paperType) ?: return
        _uiState.update { it.copy(isDirty = false) }
        viewModelScope.launch {
            try {
                settingsRepository.update(PdaSettings(baseUrl.toString(), apiKey, state.paperSize, labelHeight, state.paperType))
                _uiState.update {
                    if (it.isDirty) it else it.copy(baseUrl = baseUrlState(baseUrl.toString()), apiKey = apiKeyState(apiKey))
                }
                _effects.send(SettingsUiEffect.ShowToast(R.string.settings_saved))
            } catch (e: IOException) {
                Log.e(TAG, "save", e)
                _uiState.update { it.copy(isDirty = true) }
                _effects.send(SettingsUiEffect.ShowToast(R.string.settings_save_failed))
            }
        }
    }

    // One check or download at a time
    private val updateJob = JobSlot(viewModelScope)

    fun checkForUpdate() {
        if (updateJob.isActive) return
        _uiState.update { it.copy(update = UpdateState.Checking) }
        updateJob.replace {
            val next = try {
                updateRepository.checkForUpdate()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: IOException) {
                Log.w(TAG, "checkForUpdate", e)
                UpdateState.Failed(UpdateError.CHECK_FAILED)
            }
            _uiState.update { it.copy(update = next) }
        }
    }

    /** Downloads the available update, checks it, and opens the installer. */
    fun installUpdate() {
        when (val state = _uiState.value.update) {
            is UpdateState.Ready -> _effects.trySend(SettingsUiEffect.Install(state.apk))
            is UpdateState.Available -> download(state.update)
            else -> {}
        }
    }

    private fun download(update: AvailableUpdate) {
        if (updateJob.isActive) return
        _uiState.update { it.copy(update = UpdateState.Downloading(update, null)) }
        updateJob.replace {
            val apk = try {
                updateRepository.download(update) { progress ->
                    _uiState.update { it.copy(update = UpdateState.Downloading(update, progress)) }
                }
            } catch (e: IOException) {
                Log.w(TAG, "download", e)
                _uiState.update { it.copy(update = UpdateState.Failed(UpdateError.DOWNLOAD_FAILED)) }
                return@replace
            }
            // The package manager can throw on unusual firmware; that's treated as an unusable file
            val problem = try {
                withContext(Dispatchers.IO) { checkApk(apk, update.versionCode) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                Log.w(TAG, "checkApk", e)
                ApkProblem.UNREADABLE
            }
            if (problem != null) {
                Log.w(TAG, "Refused update ${update.versionName}: $problem")
                apk.delete()
                val error = if (problem == ApkProblem.WRONG_SIGNATURE) UpdateError.WRONG_SIGNATURE else UpdateError.NOT_THIS_APP
                _uiState.update { it.copy(update = UpdateState.Failed(error)) }
                return@replace
            }
            _uiState.update { it.copy(update = UpdateState.Ready(update, apk)) }
            _effects.send(SettingsUiEffect.Install(apk))
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MainApplication).container
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    updateRepository = container.updateRepository,
                    checkApk = container.updateInstaller::check,
                )
            }
        }
    }
}