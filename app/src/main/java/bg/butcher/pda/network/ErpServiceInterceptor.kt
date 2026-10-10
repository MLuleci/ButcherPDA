package bg.butcher.pda.network

import bg.butcher.pda.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException

class ErpServiceInterceptor(private val settingsRepository: SettingsRepository): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val settings = runBlocking { settingsRepository.settings.first() }
        val baseUrl = settings.baseUrl.toHttpUrlOrNull()
            ?: throw IOException("Invalid or missing API base URL")

        val original = chain.request()
        val url = original.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .encodedPath(baseUrl.encodedPath.trimEnd('/') + original.url.encodedPath)
            .build()

        val request = original.newBuilder()
            .url(url)
            .header("Authorization", "token ${settings.apiKey}")
            .build()

        return chain.proceed(request)
    }
}