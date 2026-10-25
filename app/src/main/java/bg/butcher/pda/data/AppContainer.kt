package bg.butcher.pda.data

import android.content.Context
import androidx.annotation.RawRes
import bg.butcher.pda.BuildConfig
import bg.butcher.pda.R
import bg.butcher.pda.hardware.BarcodeScanner
import bg.butcher.pda.hardware.BroadcastBarcodeScanner
import bg.butcher.pda.hardware.FakeBarcodeScanner
import bg.butcher.pda.hardware.FakeLabelPrinter
import bg.butcher.pda.hardware.LabelPrinter
import bg.butcher.pda.hardware.QsPdaHardware
import bg.butcher.pda.hardware.QsPdaLabelPrinter
import bg.butcher.pda.hardware.ScanFeedback
import bg.butcher.pda.network.ErpService
import bg.butcher.pda.network.ErpServiceInterceptor
import bg.butcher.pda.network.createErpService
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.decodeCertificatePem
import java.io.File
import java.security.cert.X509Certificate
import kotlin.time.Duration.Companion.seconds

interface AppContainer {
    val erpRepository: ErpRepository
    val settingsRepository: SettingsRepository
    val labelPrinter: LabelPrinter
    val barcodeScanner: BarcodeScanner
    val scanFeedback: ScanFeedback
    val updateRepository: UpdateRepository
    val updateInstaller: UpdateInstaller
}

// Roots Android 6 may not ship, trusted on top of the system store: Let's Encrypt (ISRG Root X1 arrived in 7.1.1),
// used by the ERP server and GitHub's release downloads, and USERTrust ECC, which api.github.com chains to.
private val EXTRA_ROOTS = listOf(
    R.raw.isrg_root_x1,
    R.raw.isrg_root_x2,
    R.raw.isrg_root_yr,
    R.raw.isrg_root_ye,
    R.raw.usertrust_ecc,
)

private fun Context.readCertificate(@RawRes id: Int): X509Certificate =
    resources.openRawResource(id).bufferedReader().use { it.readText() }.decodeCertificatePem()

class DefaultAppContainer(context: Context): AppContainer {
    override val settingsRepository = SettingsRepository(context.settingsDataStore)

    private val certificates by lazy {
        HandshakeCertificates.Builder()
            .addPlatformTrustedCertificates()
            .apply { EXTRA_ROOTS.forEach { addTrustedCertificate(context.readCertificate(it)) } }
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

    override val labelPrinter: LabelPrinter =
        if (QsPdaHardware.available) {
            QsPdaLabelPrinter(context.applicationContext) { settingsRepository.settings.first() }
        } else {
            FakeLabelPrinter()
        }

    override val barcodeScanner: BarcodeScanner =
        if (QsPdaHardware.available) BroadcastBarcodeScanner(context.applicationContext) else FakeBarcodeScanner()

    override val scanFeedback by lazy { ScanFeedback(context.applicationContext) }

    // Without the ERP interceptor, which would send these requests to the ERP server
    override val updateRepository by lazy {
        val client = OkHttpClient.Builder()
            .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
            .build()
        UpdateRepository(client, File(context.cacheDir, "updates"), BuildConfig.VERSION_CODE)
    }

    override val updateInstaller by lazy { UpdateInstaller(context.applicationContext) }
}
