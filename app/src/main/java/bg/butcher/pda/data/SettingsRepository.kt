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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class PdaSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
)

/** HTTPS only. A bare host name gets `https://` added. */
fun normaliseBaseUrl(input: String): HttpUrl? {
    val text = input.trim().let { if ("://" in it) it else "https://$it" }
    return text.toHttpUrlOrNull()?.takeIf { it.isHttps }
}

/** One field holding `api_key:api_secret`. */
fun normaliseApiKey(input: String): String? {
    val key = input.trim()
    val parts = key.split(':')
    return key.takeIf { parts.size == 2 && parts.all { it.isNotEmpty() } && key.none { it.isWhitespace() } }
}

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