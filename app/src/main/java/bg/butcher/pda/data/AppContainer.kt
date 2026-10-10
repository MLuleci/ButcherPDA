package bg.butcher.pda.data

import android.content.Context
import bg.butcher.pda.network.ErpService
import bg.butcher.pda.network.ErpServiceInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import kotlin.time.Duration.Companion.seconds

interface AppContainer {
    val erpRepository: ErpRepository
    val settingsRepository: SettingsRepository
}

class DefaultAppContainer(context: Context): AppContainer {
    override val settingsRepository = SettingsRepository(context.settingsDataStore)

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(ErpServiceInterceptor(settingsRepository))
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
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