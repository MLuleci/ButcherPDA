package bg.butcher.pda.network

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.DataWrapper
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.model.MessageWrapper
import kotlinx.datetime.LocalDate
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap
import retrofit2.http.Path
import java.util.Date

interface ErpService {
    @GET("/api/resource/BOM")
    suspend fun seachBom(@QueryMap(encoded = true) options: Map<String, String>): DataWrapper<List<BomSearchResultItem>>

    @GET("/api/resource/Batch/{id}")
    suspend fun getBatch(@Path("id") batchNumber: String, @QueryMap options: Map<String, String>): BatchDetails

    @FormUrlEncoded
    @POST("/api/method/my_app.my_app.api.get_bom_details")
    suspend fun getBomDetails(
        @Field("bom") bom: String,
        @Field("qty") quantity: Double = 1.0,
        @Field("explode") explode: Boolean = false,
    ): MessageWrapper<BomDetails>

    @FormUrlEncoded
    @POST("/api/method/my_app.my_app.api.create_manufacture_entry")
    suspend fun createManufactureEntry(
        @Field("bom") bom: String,
        @Field("qty") quantity: Double,
        @Field("items") items: Map<String, Map<String, Double>>,
        @Field("expiry_date") expiryDate: LocalDate,
        @Field("explode") explode: Boolean = false,
        @Field("is_finished") isFinished: Boolean = true,
    ): ManufactureEntry
}