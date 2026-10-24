package bg.butcher.pda.network

import bg.butcher.pda.data.PdaSettings
import kotlinx.coroutines.flow.flowOf
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.util.Locale

class ErpServiceInterceptorTest {
    private val server = MockWebServer()
    private val certificate = HeldCertificate.Builder()
        .addSubjectAlternativeName("localhost")
        .build()
    private val clientCertificates = HandshakeCertificates.Builder()
        .addTrustedCertificate(certificate.certificate)
        .build()
    private val defaultLocale = Locale.getDefault()

    @Before
    fun setUp() {
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory())
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
        Locale.setDefault(defaultLocale)
    }

    private fun call(settings: PdaSettings, path: String = "/api/resource/BOM?fields=x") {
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .addInterceptor(ErpServiceInterceptor(flowOf(settings)))
            .build()
        val request = Request.Builder().url("http://localhost$path").build()
        client.newCall(request).execute().close()
    }

    private fun baseUrl(path: String = "/") = "https://localhost:${server.port}$path"

    @Test
    fun rewritesUrlAndAddsHeaders() {
        Locale.setDefault(Locale.forLanguageTag("bg-BG"))
        server.enqueue(MockResponse())

        call(PdaSettings(baseUrl = baseUrl(), apiKey = " key:secret\n"))

        val request = server.takeRequest()
        assertEquals("/api/resource/BOM", request.url.encodedPath)
        assertEquals("x", request.url.queryParameter("fields"))
        assertEquals("token key:secret", request.headers["Authorization"])
        assertEquals("bg", request.headers["Accept-Language"])
    }

    @Test
    fun keepsBaseUrlPathPrefix() {
        server.enqueue(MockResponse())

        call(PdaSettings(baseUrl = baseUrl("/erp/"), apiKey = "key:secret"))

        assertEquals("/erp/api/resource/BOM", server.takeRequest().url.encodedPath)
    }

    @Test
    fun usesEnglishForOtherLanguages() {
        Locale.setDefault(Locale.forLanguageTag("de-DE"))
        server.enqueue(MockResponse())

        call(PdaSettings(baseUrl = baseUrl(), apiKey = "key:secret"))

        assertEquals("en", server.takeRequest().headers["Accept-Language"])
    }

    @Test
    fun failsEarlyWithoutValidSettings() {
        val valid = PdaSettings(baseUrl = baseUrl(), apiKey = "key:secret")

        assertThrows(InvalidSettingsException::class.java) { call(valid.copy(apiKey = "")) }
        assertThrows(InvalidSettingsException::class.java) { call(valid.copy(apiKey = "no-secret")) }
        assertThrows(InvalidSettingsException::class.java) { call(valid.copy(baseUrl = "")) }
        assertThrows(InvalidSettingsException::class.java) {
            call(valid.copy(baseUrl = "http://localhost:${server.port}/"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun invalidSettingsBecomeNotConfigured() {
        val error = ErpError.from(InvalidSettingsException("Invalid or missing API key"))

        assertEquals(ErpError.NotConfigured::class, error::class)
    }
}
