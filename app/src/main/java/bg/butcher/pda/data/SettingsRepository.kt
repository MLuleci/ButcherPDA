package bg.butcher.pda.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * The label roll; the width is the printer's maximum for that paper. On 58 mm the QR code is capped, so the
 * batch number beside it stays readable.
 */
enum class PaperSize(val widthPx: Int, val maxQrPx: Int) {
    MM_80(570, Int.MAX_VALUE),
    MM_58(384, 180),
}

/**
 * Die-cut labels, where the printer feeds to the gap after each label, or a plain roll. Feeding to the gap on a
 * plain roll runs out a long length of blank paper, since there's no gap to find.
 */
enum class PaperType { LABELS, CONTINUOUS }

const val DEFAULT_LABEL_HEIGHT_MM = 40

// Below 30 mm the text no longer fits beside the QR code; the upper limit is only a guard against typos
val LABEL_HEIGHT_RANGE_MM = 30..150

data class PdaSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val paperSize: PaperSize = PaperSize.MM_80,
    val labelHeightMm: Int = DEFAULT_LABEL_HEIGHT_MM,
    val paperType: PaperType = PaperType.LABELS,
)

/** A whole number of millimetres within [LABEL_HEIGHT_RANGE_MM]. */
fun normaliseLabelHeight(input: String): Int? = input.trim().toIntOrNull()?.takeIf { it in LABEL_HEIGHT_RANGE_MM }

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
        val PAPER_SIZE = stringPreferencesKey("paper_size")
        val LABEL_HEIGHT = intPreferencesKey("label_height_mm")
        val PAPER_TYPE = stringPreferencesKey("paper_type")
    }

    val settings: Flow<PdaSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            PdaSettings(
                baseUrl = p[Keys.BASE_URL].orEmpty(),
                apiKey = p[Keys.API_KEY].orEmpty(),
                paperSize = PaperSize.entries.firstOrNull { it.name == p[Keys.PAPER_SIZE] } ?: PaperSize.MM_80,
                labelHeightMm = p[Keys.LABEL_HEIGHT]?.takeIf { it in LABEL_HEIGHT_RANGE_MM } ?: DEFAULT_LABEL_HEIGHT_MM,
                paperType = PaperType.entries.firstOrNull { it.name == p[Keys.PAPER_TYPE] } ?: PaperType.LABELS,
            )
        }

    suspend fun update(settings: PdaSettings) {
        dataStore.edit { p ->
            p[Keys.BASE_URL] = settings.baseUrl
            p[Keys.API_KEY] = settings.apiKey
            p[Keys.PAPER_SIZE] = settings.paperSize.name
            p[Keys.LABEL_HEIGHT] = settings.labelHeightMm
            p[Keys.PAPER_TYPE] = settings.paperType.name
        }
    }
}