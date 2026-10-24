package bg.butcher.pda.network

import bg.butcher.pda.data.PdaSettings
import bg.butcher.pda.data.normaliseApiKey
import bg.butcher.pda.data.normaliseBaseUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.Locale

/** Thrown before any request is sent. An [IOException], so OkHttp reports it instead of crashing. */
class InvalidSettingsException(message: String) : IOException(message)

// Frappe picks the language before it checks the API key, so the user's own setting is ignored
private fun appLanguage(): String = Locale.getDefault().language.takeIf { it == "bg" } ?: "en"

class ErpServiceInterceptor(private val settings: Flow<PdaSettings>): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val settings = runBlocking { settings.first() }
        val baseUrl = normaliseBaseUrl(settings.baseUrl)
            ?: throw InvalidSettingsException("Invalid or missing API base URL")
        val apiKey = normaliseApiKey(settings.apiKey)
            ?: throw InvalidSettingsException("Invalid or missing API key")

        val original = chain.request()
        val url = original.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .encodedPath(baseUrl.encodedPath.trimEnd('/') + original.url.encodedPath)
            .build()

        val request = original.newBuilder()
            .url(url)
            .header("Authorization", "token $apiKey")
            .header("Accept-Language", appLanguage())
            .build()

        return chain.proceed(request)
    }
}
