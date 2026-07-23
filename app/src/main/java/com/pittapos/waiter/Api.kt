package com.pittapos.waiter

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

data class TableDto(
    val number: Int,
    val isOpen: Boolean,
    val total: Double,
    val roundCount: Int,
    val lastOrderTime: String?,
)

data class MenuProductDto(
    val id: String,
    val name: String,
    val nameEn: String?,
    val price: Double,
    val customizable: Boolean,
)

data class MenuCategoryDto(
    val id: String,
    val name: String,
    val products: List<MenuProductDto>,
)

data class ExtraOptionDto(
    val name: String,
    val price: Double,
)

data class CustomizerOptionsDto(
    val breads: List<String>,
    val ingredients: List<String>,
    val extras: List<ExtraOptionDto>,
)

data class OrderLineRequest(
    val productId: String,
    val quantity: Int,
    val bread: String? = null,
    val removedIngredients: List<String>? = null,
    val extras: Map<String, Int>? = null,
    val note: String? = null,
)

data class SubmitOrderRequest(
    val table: Int,
    val pin: String,
    val lines: List<OrderLineRequest>,
)

data class SubmitOrderResponse(
    val orderNumber: Int? = null,
    val error: String? = null,
)

data class TableOrderLineDto(
    val lineIndex: Int,
    val name: String,
    val quantity: Int,
    val revenue: Double,
    val details: String,
    val isSettled: Boolean,
)

data class TableOrderDto(
    val orderNumber: Int,
    val timeLabel: String,
    val total: Double,
    val lines: List<TableOrderLineDto>,
)

data class SettleLineRequest(
    val pin: String,
    val orderNumber: Int,
    val lineIndex: Int,
)

interface PittaApi {
    @GET("api/tables")
    suspend fun getTables(): List<TableDto>

    @GET("api/tables/{table}/orders")
    suspend fun getTableOrders(@Path("table") table: Int): List<TableOrderDto>

    @GET("api/menu")
    suspend fun getMenu(): List<MenuCategoryDto>

    @GET("api/customizer-options")
    suspend fun getCustomizerOptions(): CustomizerOptionsDto

    @POST("api/orders")
    suspend fun submitOrder(@Body request: SubmitOrderRequest): Response<SubmitOrderResponse>

    @POST("api/tables/{table}/settle")
    suspend fun settleLine(@Path("table") table: Int, @Body request: SettleLineRequest): Response<Unit>
}

/** Το POS τρέχει μέσα στο ίδιο WiFi του μαγαζιού — χωρίς HTTPS, δεν χρειάζεται πιστοποιητικό. */
object ApiClient {
    fun create(baseUrl: String): PittaApi {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PittaApi::class.java)
    }
}
