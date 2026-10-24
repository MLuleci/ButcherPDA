package bg.butcher.pda.data

import android.content.Context
import androidx.annotation.RawRes
import bg.butcher.pda.R
import bg.butcher.pda.network.ErpService
import bg.butcher.pda.network.ErpServiceInterceptor
import bg.butcher.pda.network.erpJson
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.decodeCertificatePem
import retrofit2.Retrofit
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

    private val certificates = HandshakeCertificates.Builder()
        .addPlatformTrustedCertificates()
        .apply { LETS_ENCRYPT_ROOTS.forEach { addTrustedCertificate(context.readCertificate(it)) } }
        .build()

    private val okHttp = OkHttpClient.Builder()
        .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
        .addInterceptor(ErpServiceInterceptor(settingsRepository))
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .addConverterFactory(erpJson.asConverterFactory("application/json".toMediaType()))
        .baseUrl("http://localhost/") // Replaced by interceptor
        .client(okHttp)
        .build()

    private val service: ErpService by lazy {
        retrofit.create(ErpService::class.java)
    }

    override val erpRepository: ErpRepository by lazy {
        FakeErpRepository(1.seconds)
    }
}
