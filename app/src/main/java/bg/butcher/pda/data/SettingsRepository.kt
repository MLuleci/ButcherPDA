package bg.butcher.pda.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class PdaSettings(
    val baseUrl: String = "http://localhost/",
    val apiKey: String = "",
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
    }

    val settings: Flow<PdaSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            PdaSettings(
                baseUrl = p[Keys.BASE_URL].orEmpty(),
                apiKey = p[Keys.API_KEY].orEmpty(),
            )
        }

    suspend fun update(settings: PdaSettings) {
        dataStore.edit { p ->
            p[Keys.BASE_URL] = settings.baseUrl
            p[Keys.API_KEY] = settings.apiKey
        }
    }
}