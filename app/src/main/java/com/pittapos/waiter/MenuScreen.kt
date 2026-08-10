package com.pittapos.waiter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

// ── Κανόνες κατηγορίας ────────────────────────────────────────────────────────────────────────────
// Πηγή είναι ΤΟ ΤΑΜΕΙΟ: κάθε κατηγορία κουβαλάει τους κανόνες της στο /api/menu, οπότε ό,τι αλλάζει ο
// ταμίας από τη Διαχείριση Καταλόγου (μετονομασία κατηγορίας, ψωμί, διπλή πίτα, υλικά, τιμές) περνάει
// αυτόματα στο κινητό χωρίς νέο APK. Οι legacy* από κάτω μένουν ΜΟΝΟ ως εφεδρεία για ταμείο παλιότερο
// από αυτή την έκδοση — μη γράψεις νέο όνομα κατηγορίας εκεί, θα ξανασπάσει στην επόμενη μετονομασία.

private fun MenuCategoryDto.breadChoice() = hasBread ?: legacyHasBreadChoice(name)

private fun MenuCategoryDto.fusesBreadIntoName() = fuseBreadIntoName ?: legacyFuseBreadIntoName(name)

/** Η κατηγορία δείχνει «διπλή πίτα» μόνο αν το ταμείο τη δίνει ΚΑΙ έχει οριστεί χρέωση — αλλιώς δεν
 * έχει ρυθμιστεί ακόμα από τη Διαχείριση Καταλόγου και δεν έχει νόημα να φανεί. */
private fun MenuCategoryDto.doublePita(options: CustomizerOptionsDto): Boolean {
    val supported = supportsDoublePita ?: legacySupportsDoublePita(name)
    return supported && doublePitaPriceOr(options) > 0.0
}

private fun MenuCategoryDto.doublePitaPriceOr(options: CustomizerOptionsDto) =
    doublePitaPrice ?: options.doublePitaPrices.orEmpty()[name] ?: 0.0

/** Εφεδρεία για ταμείο πριν το /api/menu στείλει κανόνες κατηγορίας — τα ονόματα είναι του MenuSeed. */
private fun legacyHasBreadChoice(category: String) =
    category == "ΤΥΛΙΧΤΑ" || category == "ΠΙΤΤΕΣ" || category == "ΠΙΤΤΕΣ ΠΑΠΠΟΥ" ||
        category == "ΜΕΡΙΔΕΣ" || category == "ΜΕΡΙΔΕΣ ΠΑΠΠΟΥ"

/** Τα ΤΥΛΙΧΤΑ/ΠΙΤΤΕΣ χωνεύουν το ψωμί μέσα στο όνομα («ΑΡ. Κοτόπουλο») — οι ΜΕΡΙΔΕΣ το δείχνουν σε
 * ξεχωριστή γραμμή ολόγραφο, δεν βγάζει νόημα «ΑΡ. Μερίδα κοτόπουλο». */
private fun legacyFuseBreadIntoName(category: String) =
    category == "ΤΥΛΙΧΤΑ" || category == "ΠΙΤΤΕΣ" || category == "ΠΙΤΤΕΣ ΠΑΠΠΟΥ"

private fun legacySupportsDoublePita(category: String) =
    category == "ΤΥΛΙΧΤΑ" || category == "ΠΙΤΤΕΣ" || category == "ΚΛΑΣΙΚΑ ΜΙΝΙ" || category == "ΚΛΑΣΙΚΑ ΜΙΚΡΑ"

/** Η συντομογραφία έρχεται από το ταμείο· το when από κάτω είναι μόνο εφεδρεία για ταμείο που δεν τη
 * στέλνει ακόμα, με το ίδιο τελευταίο σκαλί (αρχικό γράμμα + τελεία) που έχει και το MenuSeed. */
private fun breadAbbreviation(bread: String, options: CustomizerOptionsDto): String {
    options.breadAbbreviations.orEmpty()[bread]?.let { return it }
    return when (bread) {
        "Ελληνική" -> "ΕΛ."
        "Αραβική" -> "ΑΡ."
        "Ψωμί" -> "Ψ."
        else -> if (bread.isNotEmpty()) bread.take(1).uppercase() + "." else ""
    }
}

private fun composeCustomizedName(productName: String, bread: String, options: CustomizerOptionsDto): String {
    val prefix = "Πίττα "
    val rest = if (productName.startsWith(prefix, ignoreCase = true)) productName.substring(prefix.length) else productName
    return breadAbbreviation(bread, options) + " " + rest
}

/** Ίδια λογική με MenuSeed.ComposeDoublePitaName στο ταμείο — οι κατηγορίες που χωνεύουν το ψωμί στο
 * όνομα βάζουν ψωμί+«ΔΙΠΛΗ ΠΙΤΑ» μπροστά (χωρίς το «Πίττα »), οι υπόλοιπες αντικαθιστούν το
 * «Μίνι»/«Μικρό» ώστε να μη διπλασιάζεται η λέξη. Κρίνεται από τον κανόνα της κατηγορίας, όχι από το
 * όνομά της — στο ταμείο είναι το ίδιο σύνολο κατηγοριών με το FuseBreadIntoName.
 * Το τελικό όνομα το ξαναφτιάχνει ούτως ή άλλως το ταμείο κατά την καταχώρηση· εδώ είναι για το καλάθι. */
private fun composeDoublePitaName(
    productName: String,
    category: MenuCategoryDto,
    bread: String,
    options: CustomizerOptionsDto,
): String {
    if (category.fusesBreadIntoName()) {
        val prefix = "Πίττα "
        val rest = if (productName.startsWith(prefix, ignoreCase = true)) productName.substring(prefix.length) else productName
        return breadAbbreviation(bread, options) + " ΔΙΠΛΗ ΠΙΤΑ " + rest
    }
    val miniPrefixes = listOf("Μίνι ", "Μικρό ")
    val rest = miniPrefixes.firstNotNullOfOrNull { prefix ->
        if (productName.startsWith(prefix, ignoreCase = true)) productName.substring(prefix.length) else null
    } ?: productName
    return "ΜΙΝΙ ΔΙΠΛΗ ΠΙΤΑ " + rest
}

/** Ίδιο κατώφλι με το ταμείο (βλ. MenuSeed.DescribeRemovedIngredients) — 3+ αφαιρέσεις γίνονται
 * «μόνο με:» + ένα υλικό ανά γραμμή αντί για μακριά λίστα «χωρίς Χ · χωρίς Υ». */
private fun describeRemovedIngredients(removed: List<String>, allIngredients: List<String>): List<String> {
    if (removed.isEmpty()) return emptyList()
    // Το isNotEmpty() είναι ο ίδιος φύλακας με το ταμείο: προϊόν χωρίς βασικά υλικά δεν γίνεται «σκέτο»
    // από ξεμαρκαρίσματα που κουβαλήθηκαν από αλλού — αλλιώς οι δύο πλευρές τύπωναν διαφορετικά.
    if (allIngredients.isNotEmpty() && removed.size >= allIngredients.size) return listOf("σκέτο")
    if (removed.size >= 3) {
        val remaining = allIngredients.filter { it !in removed }.map { it.replaceFirstChar(Char::lowercaseChar) }
        return listOf("μόνο με:") + remaining
    }
    return removed.map { "χωρίς " + it.replaceFirstChar(Char::lowercaseChar) }
}

/** Μία γραμμή προς αποστολή· τα customization πεδία μένουν null για απλά προϊόντα χωρίς customizer. */
private data class DraftLine(
    val key: String,
    val productId: String,
    val name: String,
    val unitPrice: Double,
    val quantity: Int,
    val bread: String? = null,
    val removedIngredients: List<String>? = null,
    val extras: Map<String, Int>? = null,
    val note: String? = null,
    val details: String = "",
    val doublePita: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(prefs: AppPrefs, table: Int, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<MenuCategoryDto>>(emptyList()) }
    var customizerOptions by remember { mutableStateOf<CustomizerOptionsDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<MenuCategoryDto?>(null) }
    var customizingProduct by remember { mutableStateOf<MenuProductDto?>(null) }
    var customizingCategory by remember { mutableStateOf<MenuCategoryDto?>(null) }
    // Ποιο προϊόν έχει πατηθεί μία φορά. Δεν κάνει τίποτα από μόνο του — δίνει μόνο οπτική επιβεβαίωση
    // ότι το πάτημα έπιασε, αφού πλέον χρειάζεται δεύτερο πάτημα για να μπει το προϊόν.
    var selectedProductId by remember { mutableStateOf<String?>(null) }
    val cartLines = remember { mutableStateListOf<DraftLine>() }
    var lineCounter by remember { mutableStateOf(0) }
    val snackbarHost = remember { SnackbarHostState() }
    var orderNote by remember { mutableStateOf("") }
    var showOrderNote by remember { mutableStateOf(false) }
    var showCartReview by remember { mutableStateOf(false) }
    var editingLine by remember { mutableStateOf<DraftLine?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Ίδια λογική με το βελάκι πίσω: μέσα σε κατηγορία γυρνά στις κατηγορίες (κρατώντας το καλάθι),
    // μόνο από τις κατηγορίες βγαίνει στο τραπέζι — ώστε το κουμπί πίσω του κινητού να μη χάνει παραγγελία.
    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    LaunchedEffect(Unit) {
        try {
            val api = ApiClient.create(prefs.serverUrl)
            categories = api.getMenu()
            customizerOptions = api.getCustomizerOptions()
        } catch (e: Exception) {
            error = "Δεν φορτώθηκε το μενού — έλεγξε τη σύνδεση"
        }
        loading = false
    }

    val total = cartLines.sumOf { it.unitPrice * it.quantity }
    val itemCount = cartLines.sumOf { it.quantity }

    fun simpleQuantity(product: MenuProductDto): Int =
        cartLines.firstOrNull { it.key == "p:${product.id}" }?.quantity ?: 0

    fun changeSimpleQuantity(product: MenuProductDto, category: MenuCategoryDto, delta: Int) {
        val key = "p:${product.id}"
        val idx = cartLines.indexOfFirst { it.key == key }
        if (idx < 0) {
            if (delta > 0) {
                // Ίδια λογική με το ταμείο (βλ. ProductsViewModel.TapProduct) — ένα γρήγορο tap σε
                // customizable προϊόν παίρνει το προεπιλεγμένο ψωμί, ώστε το όνομα να δείχνει ήδη ό,τι θα
                // έβγαινε αν είχε ανοίξει κανείς τον customizer, όχι το γυμνό όνομα προϊόντος.
                val opts = customizerOptions
                val defaultBread = opts?.breads?.firstOrNull() ?: ""
                // Χωρίς επιλογές customizer δεν ξέρουμε ούτε ψωμί ούτε συντόμευση — μπαίνει το γυμνό
                // όνομα, όπως και πριν· το ταμείο ξαναφτιάχνει το τελικό όνομα στην καταχώρηση.
                val name = if (opts != null && product.customizable &&
                    category.breadChoice() && category.fusesBreadIntoName())
                    composeCustomizedName(product.name, defaultBread, opts) else product.name
                cartLines.add(DraftLine(key, product.id, name, product.price, 1))
            }
            return
        }
        val newQty = cartLines[idx].quantity + delta
        if (newQty <= 0) cartLines.removeAt(idx) else cartLines[idx] = cartLines[idx].copy(quantity = newQty)
    }

    /** Γενική αλλαγή ποσότητας ανά γραμμή του καλαθιού (απλή ή προσαρμοσμένη) — για την προβολή/διόρθωση καλαθιού. */
    fun changeLineQuantity(key: String, delta: Int) {
        val idx = cartLines.indexOfFirst { it.key == key }
        if (idx < 0) return
        val newQty = cartLines[idx].quantity + delta
        if (newQty <= 0) cartLines.removeAt(idx) else cartLines[idx] = cartLines[idx].copy(quantity = newQty)
    }

    fun removeLine(key: String) {
        cartLines.removeAll { it.key == key }
    }

    /** Ανοίγει τον customizer πάνω σε ήδη υπάρχουσα γραμμή του καλαθιού — το «✎» δίπλα στο στέπερ. */
    fun openEditor(line: DraftLine) {
        val product = categories.flatMap { it.products }.firstOrNull { it.id == line.productId } ?: return
        editingLine = line
        customizingProduct = product
        customizingCategory = categories.firstOrNull { cat -> cat.products.any { it.id == line.productId } }
    }

    if (showCartReview) {
        CartReviewSheet(
            lines = cartLines,
            isCustomizable = { productId -> categories.flatMap { it.products }.any { it.id == productId && it.customizable } },
            onDismiss = { showCartReview = false },
            onInc = { key -> changeLineQuantity(key, 1) },
            onDec = { key -> changeLineQuantity(key, -1) },
            onRemove = { key -> removeLine(key) },
            onEdit = { line -> showCartReview = false; openEditor(line) },
        )
    }

    customizingProduct?.let { product ->
        customizerOptions?.let { options ->
            // Η κατηγορία κουβαλάει τους κανόνες της (ψωμί/διπλή πίτα), οπότε χωρίς αυτήν ο customizer
            // δεν έχει τι να δείξει — δεν ανοίγει καν αντί να μαντέψει από το όνομα.
            customizingCategory?.let { category ->
            CustomizerSheet(
                product = product,
                category = category,
                options = options,
                initial = editingLine,
                onDismiss = { customizingProduct = null; editingLine = null },
                onAdd = { line ->
                    val editKey = editingLine?.key
                    if (editKey != null) {
                        val idx = cartLines.indexOfFirst { it.key == editKey }
                        if (idx >= 0) cartLines[idx] = line.copy(key = editKey) else cartLines.add(line.copy(key = editKey))
                    } else {
                        lineCounter++
                        cartLines.add(line.copy(key = "c:$lineCounter"))
                    }
                    customizingProduct = null
                    editingLine = null
                },
            )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(selectedCategory?.name ?: "Τραπέζι $table", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = { if (selectedCategory != null) selectedCategory = null else onDone() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Πίσω")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (itemCount > 0) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(20.dp, 14.dp)) {
                        if (showOrderNote) {
                            OutlinedTextField(
                                value = orderNote,
                                onValueChange = { orderNote = it },
                                label = { Text("Σημείωση παραγγελίας (π.χ. γενέθλια, χωρίς πιρούνια)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }),
                            )
                        } else {
                            TextButton(onClick = { showOrderNote = true }) {
                                Text(if (orderNote.isNotBlank()) "💬 " + orderNote else "💬 ΣΗΜΕΙΩΣΗ ΠΑΡΑΓΓΕΛΙΑΣ")
                            }
                        }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { showCartReview = true }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Text(
                                "$itemCount είδη  ›",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                String.format(Locale.getDefault(), "€%.2f", total),
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Button(
                            enabled = itemCount > 0 && !sending,
                            shape = MaterialTheme.shapes.medium,
                            onClick = {
                                scope.launch {
                                    sending = true
                                    val lines = cartLines.map { l ->
                                        OrderLineRequest(l.productId, l.quantity, l.bread, l.removedIngredients, l.extras, l.note, l.doublePita)
                                    }
                                    try {
                                        val response = ApiClient.create(prefs.serverUrl).submitOrder(
                                            SubmitOrderRequest(
                                                table = table, pin = prefs.pin, lines = lines,
                                                note = orderNote.trim().ifEmpty { null },
                                            ),
                                        )
                                        if (response.isSuccessful) {
                                            onDone()
                                        } else {
                                            snackbarHost.showSnackbar(
                                                if (response.code() == 401) "Λάθος PIN — άλλαξέ το στις ρυθμίσεις"
                                                else "Η παραγγελία απορρίφθηκε",
                                            )
                                        }
                                    } catch (e: Exception) {
                                        snackbarHost.showSnackbar("Αποτυχία αποστολής — έλεγξε τη σύνδεση")
                                    } finally {
                                        sending = false
                                    }
                                }
                            },
                        ) { Text(if (sending) "..." else "ΑΠΟΣΤΟΛΗ ΠΑΡΑΓΓΕΛΙΑΣ", fontWeight = FontWeight.Bold) }
                    }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Ένα τοπικό val — όχι επανειλημμένο selectedCategory!! — ώστε ο μηδενισμός του (πίσω στις
            // κατηγορίες) να μην προλάβει να προκαλέσει NullPointerException μέσα στο ίδιο composition pass.
            val category = selectedCategory
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Text(error.orEmpty(), modifier = Modifier.align(Alignment.Center).padding(24.dp))
                category == null -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(categories) { cat ->
                        CategoryCard(cat, cartCount = cartLines.count { line -> cat.products.any { it.id == line.productId } }) {
                            selectedCategory = cat
                        }
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(category.products) { product ->
                        ProductCard(
                            product = product,
                            quantity = simpleQuantity(product),
                            selected = selectedProductId == product.id,
                            onSelect = { selectedProductId = product.id },
                            onOpenCustomizer = { customizingProduct = product; customizingCategory = category },
                            onInc = { changeSimpleQuantity(product, category, 1) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
        Text(
            "$count",
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CategoryCard(category: MenuCategoryDto, cartCount: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp, 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(category.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "${category.products.size} είδη",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cartCount > 0) {
                CountBadge(cartCount)
                Spacer(Modifier.width(10.dp))
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ένα έξτρα σε μισό πλάτος — όνομα/τιμή αριστερά, στέπερ δεξιά στην ΙΔΙΑ γραμμή, δύο ανά σειρά.
 * Σε μισό πλάτος δεν χωράει το κανονικό στέπερ δίπλα στο όνομα, γι' αυτό εδώ είναι πιο μαζεμένο
 * (34dp κουμπιά αντί 48dp): το ζητούμενο ήταν να μη χρειάζεται ατέλειωτο σκρολ για ~20 έξτρα. */
@Composable
private fun RowScope.ExtraCell(extra: ExtraOptionDto, quantity: Int, onInc: () -> Unit, onDec: () -> Unit) {
    // Επιλεγμένο έξτρα βάφεται, ίδιο σημάδι με το τσεκαρισμένο υλικό (SelectableRow): με ~21 έξτρα σε δύο
    // στήλες, το σκέτο «1» μέσα στο στέπερ χανόταν και ο σερβιτόρος δεν έβλεπε με μια ματιά τι έβαλε.
    val picked = quantity > 0
    Row(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (picked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                extra.name,
                fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
                color = if (picked) MaterialTheme.colorScheme.primary
                else androidx.compose.ui.graphics.Color.Unspecified,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 2,
            )
            Text(
                if (extra.price > 0) String.format(Locale.getDefault(), "+€%.2f", extra.price) else "δωρεάν",
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CompactQuantityStepper(quantity, onInc = onInc, onDec = onDec)
    }
}

/** Μαζεμένη εκδοχή του στέπερ, μόνο για τα έξτρα σε δύο στήλες — ίδια συμπεριφορά, μικρότερα κουμπιά. */
@Composable
private fun CompactQuantityStepper(quantity: Int, onInc: () -> Unit, onDec: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        IconButton(onClick = onDec, enabled = quantity > 0, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Μείωση",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            "$quantity",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        IconButton(onClick = onInc, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Αύξηση",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun QuantityStepper(quantity: Int, onInc: () -> Unit, onDec: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onDec, enabled = quantity > 0) {
            Icon(Icons.Default.Remove, contentDescription = "Μείωση", tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(
            "$quantity",
            modifier = Modifier.padding(horizontal = 4.dp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        IconButton(onClick = onInc) {
            Icon(Icons.Default.Add, contentDescription = "Αύξηση", tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** Μικρό τετράγωνο κουμπί «✎» που ανοίγει την προσαρμογή/έξτρα — ίδιο εικονίδιο παντού (μενού + καλάθι). */
@Composable
private fun CustomizeBoxButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Έξτρα / προσαρμογή",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** ΔΙΠΛΟ κλικ πάνω στο προϊόν προσθέτει (1, 2, 3...) — το «✎» δεξιά ανοίγει πάντα την προσαρμογή/έξτρα. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductCard(
    product: MenuProductDto,
    quantity: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpenCustomizer: () -> Unit,
    onInc: () -> Unit,
) {
    val inCart = quantity > 0
    // Surface απλό (όχι Card με δικό του onClick) — το «tap για προσθήκη» και το κουμπί εξτρών
    // είναι αδέρφια, ποτέ το ένα μέσα στο άλλο, ώστε να μην υπάρχει καμία αμφισημία στο ποιο κλικ πιάνει το Android.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (inCart) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        // Πιο χοντρό περίγραμμα στο επιλεγμένο, ώστε να ξεχωρίζει από το «είναι στο καλάθι» (που
        // βάφει ολόκληρη την κάρτα) — δύο διαφορετικά πράγματα, δύο διαφορετικά σημάδια.
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected || inCart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                // Διπλό πάτημα βάζει το προϊόν σκέτο· ένα σκέτο άγγιγμα στη σειρά δεν κάνει τίποτα.
                // Τα έξτρα ανοίγουν ΜΟΝΟ από το ✎ δίπλα — η λίστα σκρολάρεται με το δάχτυλο πάνω στα
                // ίδια τα προϊόντα, οπότε ένα άστοχο tap δεν πρέπει ούτε να προσθέτει είδος ούτε να
                // πετάγεται μπροστά η φόρμα υλικών εν ώρα αιχμής.
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(onClick = onSelect, onDoubleClick = onInc),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        product.name,
                        fontWeight = FontWeight.SemiBold,
                        color = if (inCart) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        String.format(Locale.getDefault(), "€%.2f", product.price),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (inCart) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (inCart) {
                    CountBadge(quantity)
                } else if (!product.customizable) {
                    Icon(Icons.Default.Add, contentDescription = "Προσθήκη", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (product.customizable) {
                Spacer(Modifier.width(10.dp))
                CustomizeBoxButton(onClick = onOpenCustomizer)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizerSheet(
    product: MenuProductDto,
    category: MenuCategoryDto,
    options: CustomizerOptionsDto,
    initial: DraftLine? = null,
    onDismiss: () -> Unit,
    onAdd: (DraftLine) -> Unit,
) {
    val noteFocusManager = LocalFocusManager.current
    val noteKeyboardController = LocalSoftwareKeyboardController.current
    val breadChoice = category.breadChoice()
    val fuseBread = category.fusesBreadIntoName()
    val showDoublePita = category.doublePita(options)
    val doublePitaPrice = category.doublePitaPriceOr(options)
    var quantity by remember { mutableStateOf(initial?.quantity ?: 1) }
    var selectedBread by remember { mutableStateOf(initial?.bread ?: options.breads.firstOrNull() ?: "") }
    val removed = remember { mutableStateListOf<String>().apply { initial?.removedIngredients?.let(::addAll) } }
    val extraQty = remember { mutableStateMapOf<String, Int>().apply { initial?.extras?.let(::putAll) } }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var isDoublePita by remember { mutableStateOf(initial?.doublePita ?: false) }
    // Τα υλικά του προϊόντος — ίδια πηγή με το ταμείο (MenuStore.IngredientsFor). Ο κοινός κατάλογος
    // μένει μόνο ως εφεδρεία για ταμείο που δεν στέλνει ακόμα το πεδίο (βλ. MenuProductDto.ingredients).
    val ingredients = product.ingredients ?: options.ingredients
    // Τα έξτρα του προϊόντος, με τη σειρά του ταμείου. Οι τιμές έρχονται από τον κοινό κατάλογο, γι'
    // αυτό γίνεται αντιστοίχιση με το όνομα· ό,τι δεν βρεθεί αγνοείται (θα ήταν έξτρα χωρίς τιμή).
    val extras = remember(product.id, product.extras, options.extras) {
        product.extras?.mapNotNull { name -> options.extras.firstOrNull { it.name == name } }
            ?: options.extras
    }
    // Ίδια λογική με CustomizerViewModel.IsSketo στο ταμείο — «σκέτο» σημαίνει όλα τα υλικά αφαιρεμένα.
    val isSketo = ingredients.isNotEmpty() && removed.size == ingredients.size
    // Το bottom sheet κλείνει με animation — ένα γρήγορο διπλό tap στο ΠΡΟΣΘΗΚΗ προλαβαίνει να πατηθεί
    // δύο φορές πριν προλάβει να κλείσει, προσθέτοντας το ίδιο είδος διπλό στο καλάθι.
    var submitted by remember { mutableStateOf(false) }

    // derivedStateOf, όχι σκέτος υπολογισμός: το άθροισμα διαβάζει ΟΛΟΝ τον χάρτη των έξτρα, οπότε ένα
    // «+» σε ένα μόνο έξτρα ακύρωνε ολόκληρη τη φόρμα και ξαναχτίζονταν υλικά, ψωμί και τα ~21 έξτρα.
    // Έτσι η ανάγνωση μένει εκεί που όντως χρησιμοποιείται η τιμή (το κουμπί ΠΡΟΣΘΗΚΗ).
    val extrasCost by remember {
        derivedStateOf { extras.sumOf { (extraQty[it.name] ?: 0) * it.price } }
    }
    val unitPrice by remember {
        derivedStateOf { product.price + extrasCost + (if (showDoublePita && isDoublePita) doublePitaPrice else 0.0) }
    }
    val lineTotal by remember { derivedStateOf { unitPrice * quantity } }

    // Κοινή λογική "πρόσθεσε στο καλάθι" — καλείται είτε από το κουμπί πάνω δεξιά (γρήγορη αλλαγή, π.χ.
    // μόνο το ψωμί, χωρίς να χρειάζεται σκρολ μέχρι κάτω) είτε από το κανονικό κουμπί στο τέλος της φόρμας.
    fun submit() {
        if (submitted) return
        submitted = true
        // Ίδια λογική κατηγορίας με το ταμείο (βλ. CustomizerViewModel.Add στο PittaPos.App):
        // ΤΥΛΙΧΤΑ χωνεύουν το ψωμί στο όνομα, ΜΕΡΙΔΕΣ το δείχνουν σε ξεχωριστή γραμμή ολόγραφο,
        // άλλες κατηγορίες δεν αναφέρουν καθόλου ψωμί. Η διπλή πίτα προηγείται — αντικαθιστά εντελώς
        // το κανονικό όνομα, ίδια προτεραιότητα με το ταμείο.
        val noteTrimmed = note.trim()
        val doublePita = showDoublePita && isDoublePita
        val lineName = when {
            doublePita -> composeDoublePitaName(product.name, category, selectedBread, options)
            breadChoice && fuseBread -> composeCustomizedName(product.name, selectedBread, options)
            else -> product.name
        }
        val descLine1 = when {
            doublePita -> noteTrimmed
            !breadChoice -> noteTrimmed
            fuseBread -> noteTrimmed
            noteTrimmed.isNotEmpty() -> selectedBread + "\n" + noteTrimmed
            else -> selectedBread
        }
        val mods = buildList {
            addAll(describeRemovedIngredients(removed, ingredients))
            addAll(extraQty.filterValues { it > 0 }.map { (name, qty) -> "+ $name" + (if (qty > 1) " ×$qty" else "") })
        }
        val details = (listOf(descLine1) + mods).filter { it.isNotEmpty() }.joinToString("\n")
        onAdd(
            DraftLine(
                key = "",
                productId = product.id,
                name = lineName,
                unitPrice = unitPrice,
                quantity = quantity,
                bread = if (breadChoice) selectedBread else null,
                removedIngredients = removed.toList(),
                extras = extraQty.filterValues { it > 0 }.toMap(),
                note = noteTrimmed,
                details = details,
                doublePita = doublePita,
            ),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // LazyColumn, όχι Column με verticalScroll: με το scroll χτίζονταν ΟΛΕΣ οι σειρές πριν προλάβει
        // να ανοίξει το φύλλο — ψωμί, ~7 υλικά, ~21 έξτρα, σημείωση, ποσότητα, κουμπί. Σε φθηνό κινητό
        // (Xiaomi Redmi 4GB) αυτό φαινόταν σαν κόλλημα κάθε φορά που άνοιγες τα έξτρα. Έτσι χτίζονται
        // μόνο όσες σειρές φαίνονται, και οι υπόλοιπες καθώς κυλάς.
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text(
                        String.format(Locale.getDefault(), "από €%.2f", product.price),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Γρήγορη προσθήκη πάνω δεξιά — για μια απλή αλλαγή (π.χ. μόνο αραβική αντί για ελληνική
                // πίτα) δεν χρειάζεται σκρολ μέχρι το κουμπί στο τέλος της φόρμας, μετά από όλα τα υλικά/
                // έξτρα. Χωρίς τιμή πάνω — μόνο εικονίδιο, η τιμή φαίνεται ήδη στο κανονικό κουμπί κάτω.
                // Τικ αντί για «+» — το «+» μπέρδευε με τα στέπερ ποσότητας/εξτρών δίπλα του, σαν να
                // αύξανε ποσότητα αντί να καταχωρεί/κλείνει την προσαρμογή.
                FilledIconButton(
                    enabled = !submitted,
                    shape = MaterialTheme.shapes.medium,
                    onClick = { submit() },
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Καταχώρηση")
                }
            }
            Spacer(Modifier.height(20.dp))
            }

            if (breadChoice && options.breads.isNotEmpty()) {
                // Χωρίς τίτλο «Ψωμί»: οι ίδιες οι επιλογές (Ελληνική/Αραβική/Ψωμί) λένε ήδη τι είναι,
                // ενώ η ετικέτα έπιανε μια ολόκληρη σειρά ψηλά στη φόρμα χωρίς να πατιέται.
                items(options.breads) { bread ->
                    SelectableRow(selected = selectedBread == bread, onClick = { selectedBread = bread }) {
                        RadioButton(selected = selectedBread == bread, onClick = { selectedBread = bread })
                        Text(bread)
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            // Διπλή πίτα — μόνο ΤΥΛΙΧΤΑ/ΚΛΑΣΙΚΑ ΜΙΝΙ, ίδια λογική με CustomizerViewModel.ShowDoublePitaOption
            // στο ταμείο. Δεν χρειάζεται επιλογή ψωμιού (ΚΛΑΣΙΚΑ ΜΙΝΙ δεν έχει καν), γι' αυτό δική της ενότητα.
            if (showDoublePita) {
                item {
                SelectableRow(selected = isDoublePita, onClick = { isDoublePita = !isDoublePita }) {
                    Checkbox(checked = isDoublePita, onCheckedChange = { isDoublePita = it })
                    Text(
                        if (doublePitaPrice > 0)
                            String.format(Locale.getDefault(), "ΔΙΠΛΗ ΠΙΤΑ (+€%.2f)", doublePitaPrice)
                        else "ΔΙΠΛΗ ΠΙΤΑ",
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(16.dp))
                }
            }

            if (ingredients.isNotEmpty()) {
                item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Υλικά — ξεμαρκάρισε ό,τι δεν θέλεις")
                    // Γρήγορο κουμπί «ΣΚΕΤΟ» — ίδια λογική με CustomizerViewModel.ToggleSketo στο ταμείο,
                    // αφαιρεί/επαναφέρει όλα τα υλικά μαζί αντί να ξεμαρκάρεις ένα-ένα.
                    TextButton(onClick = {
                        if (isSketo) removed.clear() else {
                            removed.clear()
                            removed.addAll(ingredients)
                        }
                    }) { Text(if (isSketo) "✓ ΣΚΕΤΟ" else "ΣΚΕΤΟ") }
                }
                }
                // Δύο ανά σειρά, όπως και τα έξτρα — τα ονόματα υλικών είναι κοντά και σε μία στήλη
                // έμενε μισή οθόνη κενή δεξιά, σπρώχνοντας τα έξτρα και το ΠΡΟΣΘΗΚΗ πιο κάτω.
                items(ingredients.chunked(2)) { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        pair.forEach { ing ->
                            val included = ing !in removed
                            SelectableRow(
                                selected = included,
                                onClick = { if (included) removed.add(ing) else removed.remove(ing) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Checkbox(checked = included, onCheckedChange = {
                                    if (it) removed.remove(ing) else removed.add(ing)
                                })
                                Text(ing, fontSize = 14.sp, maxLines = 2)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            if (extras.isNotEmpty()) {
                item { SectionLabel("Έξτρα") }
                // Δύο ανά σειρά: τα έξτρα είναι κοντά στα 20 και σε μία στήλη ο σερβιτόρος σκρόλαρε
                // ατέλειωτα για να φτάσει στα τελευταία. Το στέπερ μπαίνει ΚΑΤΩ από το όνομα, όχι δίπλα:
                // στο μισό πλάτος δεν χωρούν και τα δύο, και το να μικρύνει το στέπερ θα έκανε τα κουμπιά
                // μικρότερα από το όριο αφής — λάθος πάτημα σε ώρα αιχμής κοστίζει πιο πολύ από το σκρολ.
                items(extras.chunked(2)) { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        pair.forEach { extra ->
                            val qty = extraQty[extra.name] ?: 0
                            ExtraCell(
                                extra = extra,
                                quantity = qty,
                                onInc = { extraQty[extra.name] = (qty + 1).coerceAtMost(10) },
                                onDec = { if (qty > 0) extraQty[extra.name] = qty - 1 },
                            )
                        }
                        // Μονός αριθμός έξτρα: το τελευταίο κρατά το μισό πλάτος αντί να απλωθεί σε όλη
                        // τη σειρά, ώστε η στήλη να μένει ευθυγραμμισμένη με τις από πάνω.
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Σημείωση (προαιρετικό)") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                    noteFocusManager.clearFocus()
                    noteKeyboardController?.hide()
                }),
            )
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Ποσότητα", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                QuantityStepper(quantity, onInc = { quantity++ }, onDec = { if (quantity > 1) quantity-- })
            }
            Spacer(Modifier.height(16.dp))

            Button(
                enabled = !submitted,
                shape = MaterialTheme.shapes.medium,
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    (if (initial != null) "ΕΝΗΜΕΡΩΣΗ · " else "ΠΡΟΣΘΗΚΗ · ") + String.format(Locale.getDefault(), "€%.2f", lineTotal),
                    fontWeight = FontWeight.Bold,
                )
            }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    // Προεπιλογή όλο το πλάτος· τα υλικά το περνούν σε δύο στήλες με weight(1f).
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Ανοίγει από κάτω προς τα πάνω — δείχνει όλο το καλάθι πριν την αποστολή, με δυνατότητα διόρθωσης. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartReviewSheet(
    lines: List<DraftLine>,
    isCustomizable: (String) -> Boolean,
    onDismiss: () -> Unit,
    onInc: (String) -> Unit,
    onDec: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (DraftLine) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // verticalScroll: χωρίς αυτό, σε μεγάλη παραγγελία όσες γραμμές δεν χωρούσαν στο ύψος της
        // οθόνης ήταν ΑΠΡΟΣΙΤΕΣ — το φύλλο δεν κυλούσε καθόλου και ο σερβιτόρος δεν μπορούσε ούτε να
        // τις δει ούτε να τις διορθώσει. (Το φύλλο των υλικών παρακάτω το είχε ήδη σωστά.)
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text("Το καλάθι σου", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            if (lines.isEmpty()) {
                Text(
                    "Άδειο καλάθι",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            lines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(line.name, fontWeight = FontWeight.SemiBold)
                        if (line.details.isNotEmpty()) {
                            Text(
                                line.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            String.format(Locale.getDefault(), "€%.2f ανά τεμάχιο", line.unitPrice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isCustomizable(line.productId)) {
                        CustomizeBoxButton(onClick = { onEdit(line) })
                        Spacer(Modifier.width(8.dp))
                    }
                    QuantityStepper(line.quantity, onInc = { onInc(line.key) }, onDec = { onDec(line.key) })
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onRemove(line.key) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Αφαίρεση", tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
