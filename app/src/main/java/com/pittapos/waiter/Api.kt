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
    // Τα βασικά υλικά ΤΟΥ ΣΥΓΚΕΚΡΙΜΕΝΟΥ προϊόντος. Το ταμείο μετράει το «σκέτο» και γράφει το «μόνο με:»
    // της απόδειξης με αυτά, οπότε ο customizer πρέπει να δείχνει αυτά — όχι τον κοινό κατάλογο του
    // CustomizerOptionsDto.ingredients (πριν έδειχνε εκείνον παντού: ξεμαρκάριζες υλικά που το προϊόν
    // δεν είχε καν και η κουζίνα έπαιρνε ΣΚΕΤΟ σε παραγγελία που δεν το ζήτησε).
    // Nullable για ταμείο που δεν στέλνει ακόμα το πεδίο — τότε πέφτουμε στον κοινό κατάλογο, όπως πριν.
    // Άδεια λίστα σημαίνει «προϊόν χωρίς βασικά υλικά» και ΔΕΝ είναι το ίδιο με null (βλ. σχόλιο στο
    // doublePitaPrices: το Gson αγνοεί τα Kotlin defaults, το πεδίο που λείπει έρχεται όντως null).
    val ingredients: List<String>? = null,
    // Τα ονόματα των έξτρα ΤΟΥ προϊόντος, με τη σειρά που τα έχει το ταμείο. Οι τιμές τους μένουν στο
    // CustomizerOptionsDto.extras (κοινός κατάλογος) — εδώ ταξιδεύει μόνο ποια και με ποια σειρά.
    // Nullable: ταμείο που δεν το στέλνει ακόμα → δείχνουμε τον κοινό κατάλογο, όπως πριν.
    val extras: List<String>? = null,
)

data class MenuCategoryDto(
    val id: String,
    val name: String,
    val products: List<MenuProductDto>,
    // Οι κανόνες της κατηγορίας, όπως τους έχει ΤΟ ΤΑΜΕΙΟ (Διαχείριση Καταλόγου). Παλιότερα ήταν
    // γραμμένα εδώ μέσα τα ονόματα («ΤΥΛΙΧΤΑ»...), οπότε μια μετονομασία κατηγορίας στο ταμείο έκοβε
    // αθόρυβα ψωμί και διπλή πίτα από το κινητό μέχρι να βγει νέο APK. Τώρα ρωτάμε το ταμείο.
    // Nullable: ταμείο που δεν τα στέλνει ακόμα → πέφτουμε στους τοπικούς κανόνες (βλ. MenuScreen).
    val hasBread: Boolean? = null,
    val fuseBreadIntoName: Boolean? = null,
    val supportsDoublePita: Boolean? = null,
    val doublePitaPrice: Double? = null,
)

data class ExtraOptionDto(
    val name: String,
    val price: Double,
)

data class CustomizerOptionsDto(
    val breads: List<String>,
    val ingredients: List<String>,
    val extras: List<ExtraOptionDto>,
    // Nullable, όχι emptyMap() default — το Gson γεμίζει τα πεδία με reflection παρακάμπτοντας τον
    // constructor, οπότε ένα παλιότερο POS που δεν στέλνει ακόμα αυτό το κλειδί στο JSON δίνει null εδώ
    // ΠΑΡΑ το Kotlin default (δοκιμασμένο: crash στο MenuScreen χωρίς αυτό). Πάντα .orEmpty() στη χρήση.
    val doublePitaPrices: Map<String, Double>? = null,
    // Η συντομογραφία κάθε ψωμιού («Αραβική» → «ΑΡ.») όπως τη γράφει το ταμείο. Την υπολόγιζε και το
    // κινητό μόνο του· αν άλλαζαν τα ψωμιά, το καλάθι του σερβιτόρου έγραφε άλλα από την απόδειξη.
    val breadAbbreviations: Map<String, String>? = null,
)

data class OrderLineRequest(
    val productId: String,
    val quantity: Int,
    val bread: String? = null,
    val removedIngredients: List<String>? = null,
    val extras: Map<String, Int>? = null,
    val note: String? = null,
    val doublePita: Boolean = false,
)

data class SubmitOrderRequest(
    val table: Int,
    val pin: String,
    val lines: List<OrderLineRequest>,
    val note: String? = null,
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
    val note: String = "",
)

data class SettleLineRequest(
    val pin: String,
    val orderNumber: Int,
    val lineIndex: Int,
)

data class CloseTableRequest(val pin: String)

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

    @POST("api/tables/{table}/close")
    suspend fun closeTable(@Path("table") table: Int, @Body request: CloseTableRequest): Response<Unit>
}

/**
 * Το POS τρέχει μέσα στο ίδιο WiFi του μαγαζιού — χωρίς HTTPS, δεν χρειάζεται πιστοποιητικό.
 *
 * Κρατάμε ένα μόνο PittaApi (άρα ένα OkHttpClient/thread pool) ανά διεύθυνση server: το ΤΡΑΠΕΖΙΑ
 * ρωτάει κάθε 4" όσο η οθόνη είναι ανοιχτή· αν φτιάχναμε καινούριο OkHttpClient σε κάθε κλήση θα
 * γεννιόντουσαν εκατοντάδες νήματα/connection pools ανά ώρα βάρδιας, με κίνδυνο η εφαρμογή να
 * γίνεται σταδιακά πιο αργή.
 */
object ApiClient {
    @Volatile private var cachedUrl: String? = null
    @Volatile private var cachedApi: PittaApi? = null

    fun create(baseUrl: String): PittaApi {
        cachedApi?.let { if (cachedUrl == baseUrl) return it }
        synchronized(this) {
            cachedApi?.let { if (cachedUrl == baseUrl) return it }
            val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            val api = Retrofit.Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PittaApi::class.java)
            cachedUrl = baseUrl
            cachedApi = api
            return api
        }
    }
}
