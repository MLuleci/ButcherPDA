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
 * The roll's width. Where it lands under the head comes from `printArea`, as the roll sits against the bay's right
 * edge. On 58 mm the QR code is capped, so the batch number beside it stays readable.
 */
enum class PaperSize(val widthMm: Int, val maxQrPx: Int) {
    MM_80(80, Int.MAX_VALUE),
    MM_58(58, 180),
}

/**
 * Die-cut labels, where the printer feeds to the gap after each label, or a plain roll. Feeding to the gap on a
 * plain roll runs out a long length of blank paper, since there's no gap to find.
 */
enum class PaperType { LABELS, CONTINUOUS }

const val DEFAULT_LABEL_HEIGHT_MM = 40

/**
 * Below 30 mm the text no longer fits beside the QR code. A roll needs 5 mm more, for the blank end it's torn off
 * in, which leaves the same room. The upper limit is only a guard against typos.
 */
fun labelHeightRange(paperType: PaperType): IntRange = when (paperType) {
    PaperType.LABELS -> 30..150
    PaperType.CONTINUOUS -> 35..150
}

data class PdaSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val paperSize: PaperSize = PaperSize.MM_80,
    val labelHeightMm: Int = DEFAULT_LABEL_HEIGHT_MM,
    val paperType: PaperType = PaperType.LABELS,
)

/** A whole number of millimetres within [labelHeightRange]. */
fun normaliseLabelHeight(input: String, paperType: PaperType): Int? =
    input.trim().toIntOrNull()?.takeIf { it in labelHeightRange(paperType) }

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
            val paperType = PaperType.entries.firstOrNull { it.name == p[Keys.PAPER_TYPE] } ?: PaperType.LABELS
            PdaSettings(
                baseUrl = p[Keys.BASE_URL].orEmpty(),
                apiKey = p[Keys.API_KEY].orEmpty(),
                paperSize = PaperSize.entries.firstOrNull { it.name == p[Keys.PAPER_SIZE] } ?: PaperSize.MM_80,
                labelHeightMm = p[Keys.LABEL_HEIGHT]?.takeIf { it in labelHeightRange(paperType) }
                    ?: DEFAULT_LABEL_HEIGHT_MM,
                paperType = paperType,
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