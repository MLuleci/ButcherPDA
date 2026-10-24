package bg.butcher.pda.network

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.DataWrapper
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.model.ManufactureEntryRequest
import bg.butcher.pda.model.MessageWrapper
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap

interface ErpService {
    @GET("/api/resource/BOM")
    suspend fun searchBom(@QueryMap options: Map<String, String>): DataWrapper<List<BomSearchResultItem>>

    @GET("/api/resource/Batch/{id}")
    suspend fun getBatch(@Path("id") batchNumber: String): DataWrapper<BatchDetails>

    @FormUrlEncoded
    @POST("/api/method/my_app.my_app.api.get_bom_details")
    suspend fun getBomDetails(
        @Field("bom") bom: String,
        @Field("qty") quantity: Double = 1.0,
        @Field("explode") explode: Boolean = false,
    ): MessageWrapper<BomDetails>

    @POST("/api/method/my_app.my_app.api.create_manufacture_entry")
    suspend fun createManufactureEntry(@Body request: ManufactureEntryRequest): MessageWrapper<ManufactureEntry>
}

fun createErpService(baseUrl: String, client: OkHttpClient): ErpService = Retrofit.Builder()
    .addConverterFactory(erpJson.asConverterFactory("application/json".toMediaType()))
    .baseUrl(baseUrl)
    .client(client)
    .build()
    .create(ErpService::class.java)
