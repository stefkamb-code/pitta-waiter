package com.pittapos.waiter

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

data class TableDto(
    val number: Int,
    val isOpen: Boolean,
    val total: Double,
    val roundCount: Int,
    val lastOrderTime: String?,
    // Πόσα άτομα δηλώθηκαν στο άνοιγμα — δηλαδή ΠΟΣΕΣ ΑΠΟΔΕΙΞΕΙΣ θα κοπούν στην ταμειακή του μαγαζιού.
    // 0 = δεν ρωτήθηκε ακόμα, οπότε ρωτάμε. Ταμείο παλιότερης έκδοσης δεν στέλνει το πεδίο και το Gson
    // το αφήνει 0 — τότε η εφαρμογή απλώς ρωτάει και η απάντηση αγνοείται, καμία ζημιά.
    val persons: Int = 0,
)

/**
 * Ποια βάρδια τρέχει στο ταμείο. Το κινητό ΔΕΝ την υπολογίζει από την ώρα του — ο διακόπτης στο ταμείο
 * είναι χειροκίνητος και η παραγγελία σφραγίζεται με ό,τι βρει εκεί όταν φτάσει. Ταμείο παλιότερης
 * έκδοσης δεν έχει καθόλου αυτό το endpoint (404) και τότε η βάρδια απλώς δεν δείχνεται πουθενά.
 */
data class ShiftDto(val isEveningShift: Boolean = false)

/** Όπως τα κουμπιά της κεφαλίδας του ταμείου — ίδιες λέξεις, να μη λέει αλλιώς η κάθε οθόνη. */
fun shiftLabel(isEveningShift: Boolean): String = if (isEveningShift) "🌙 ΒΡΑΔΙΝΗ" else "☀ ΠΡΩΙΝΗ"

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
    // Σε ποιο άτομο του τραπεζιού χρεώνεται (0-based: 0 = «Α»). Το μαγαζί κόβει μία απόδειξη ανά άτομο,
    // οπότε ο σερβιτόρος γράφει την παραγγελία ανά άτομο. null = σε κανέναν (πληρώνουν μαζί).
    val person: Int? = null,
)

data class SubmitOrderRequest(
    val table: Int,
    val pin: String,
    val lines: List<OrderLineRequest>,
    val note: String? = null,
    // Δελτίο κουζίνας: σε τραπέζι με άτομα στέλνουμε false για όλους εκτός από τον τελευταίο, ώστε ο
    // ψήστης να πάρει ΕΝΑ χαρτί με όλο το τραπέζι αντί για ένα ανά άτομο. Οι παραγγελίες καταχωρούνται
    // κανονικά μία-μία — μία απόδειξη ανά άτομο.
    val printNow: Boolean = true,
    // Ίδιος σε κάθε ξαναπάτημα της ίδιας παραγγελίας: αν η πρώτη πέρασε αλλά η απάντηση χάθηκε, το ταμείο
    // (1.0.104+) απαντά «ΟΚ» χωρίς να τη γράψει και να την τυπώσει δεύτερη φορά.
    val submissionId: String? = null,
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
    // Σε ποιο άτομο χρεώνεται (0-based). null = σε κανέναν, ή ταμείο παλιότερης έκδοσης — τότε η
    // καρτέλα του τραπεζιού δείχνει τους γύρους όπως πάντα, χωρίς ομαδοποίηση.
    val person: Int? = null,
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
    // «cash» ή «card» — ό,τι διάλεξε ο σερβιτόρος, ίδια κουμπιά με το ταμείο. Χωρίς αυτό το ταμείο
    // κατέγραφε ΚΑΘΕ είσπραξη του κινητού ως μετρητά και ο διαχωρισμός της αναφοράς ημέρας έβγαινε λάθος.
    val method: String = PaymentMethod.CASH,
)

data class CloseTableRequest(val pin: String, val method: String = PaymentMethod.CASH)

/** Πώς πληρώθηκε — οι τιμές που περιμένει το ταμείο (WaiterApiModels.SettleLineRequest.Method). */
object PaymentMethod {
    const val CASH = "cash"
    const val CARD = "card"
}

/** «Πόσα άτομα;» στο άνοιγμα του τραπεζιού — ένα άτομο = μία απόδειξη στην ταμειακή. */
data class SetPersonsRequest(val pin: String, val count: Int)

interface PittaApi {
    // Ο κωδικός ταξιδεύει ΚΑΙ στα GET, όχι μόνο στις εγγραφές: αλλιώς ένα κινητό με παλιό ή λάθος
    // κωδικό άνοιγε κανονικά, έβλεπε τραπέζια και κατάλογο, και κοβόταν μόνο στην ΑΠΟΣΤΟΛΗ — έμοιαζε
    // σαν να δουλεύει ακόμα ο παλιός κωδικός.
    @GET("api/tables")
    suspend fun getTables(@Query("pin") pin: String): List<TableDto>

    @GET("api/tables/{table}/orders")
    suspend fun getTableOrders(@Path("table") table: Int, @Query("pin") pin: String): List<TableOrderDto>

    @GET("api/menu")
    suspend fun getMenu(@Query("pin") pin: String): List<MenuCategoryDto>

    @GET("api/shift")
    suspend fun getShift(@Query("pin") pin: String): ShiftDto

    @GET("api/customizer-options")
    suspend fun getCustomizerOptions(@Query("pin") pin: String): CustomizerOptionsDto

    @POST("api/orders")
    suspend fun submitOrder(@Body request: SubmitOrderRequest): Response<SubmitOrderResponse>

    @POST("api/tables/{table}/settle")
    suspend fun settleLine(@Path("table") table: Int, @Body request: SettleLineRequest): Response<Unit>

    @POST("api/tables/{table}/close")
    suspend fun closeTable(@Path("table") table: Int, @Body request: CloseTableRequest): Response<Unit>

    @POST("api/tables/{table}/persons")
    suspend fun setPersons(@Path("table") table: Int, @Body request: SetPersonsRequest): Response<Unit>
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
