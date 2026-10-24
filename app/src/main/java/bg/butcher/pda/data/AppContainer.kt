package bg.butcher.pda.data

import android.content.Context
import androidx.annotation.RawRes
import bg.butcher.pda.BuildConfig
import bg.butcher.pda.R
import bg.butcher.pda.network.ErpService
import bg.butcher.pda.network.ErpServiceInterceptor
import bg.butcher.pda.network.createErpService
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.decodeCertificatePem
import java.security.cert.X509Certificate
import kotlin.time.Duration.Companion.seconds

interface AppContainer {
    val erpRepository: ErpRepository
    val settingsRepository: SettingsRepository
}

// Android 6 doesn't ship the Let's Encrypt roots (ISRG Root X1 arrived in 7.1.1),
// so trust them explicitly on top of the system store.
private val LETS_ENCRYPT_ROOTS = listOf(
    R.raw.isrg_root_x1,
    R.raw.isrg_root_x2,
    R.raw.isrg_root_yr,
    R.raw.isrg_root_ye,
)

private fun Context.readCertificate(@RawRes id: Int): X509Certificate =
    resources.openRawResource(id).bufferedReader().use { it.readText() }.decodeCertificatePem()

class DefaultAppContainer(context: Context): AppContainer {
    override val settingsRepository = SettingsRepository(context.settingsDataStore)

    private val certificates by lazy {
        HandshakeCertificates.Builder()
            .addPlatformTrustedCertificates()
            .apply { LETS_ENCRYPT_ROOTS.forEach { addTrustedCertificate(context.readCertificate(it)) } }
            .build()
    }

    private val okHttp by lazy {
        OkHttpClient.Builder()
            .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
            .addInterceptor(ErpServiceInterceptor(settingsRepository.settings))
            .build()
    }

    private val service: ErpService by lazy {
        createErpService("http://localhost/", okHttp) // Base URL replaced by interceptor
    }

    override val erpRepository: ErpRepository by lazy {
        if (BuildConfig.USE_FAKE_ERP) FakeErpRepository(latency = 1.seconds) else NetworkErpRepository(service)
    }
}
