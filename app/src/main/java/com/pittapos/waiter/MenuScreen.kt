package com.pittapos.waiter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

/** Ίδια λογική κατηγορίας με το ταμείο (βλ. MenuSeed.HasBreadChoice στο PittaPos.Core) — μόνο τα ΤΥΛΙΧΤΑ
 * και οι ΜΕΡΙΔΕΣ/ΜΕΡΙΔΕΣ ΠΑΠΠΟΥ έχουν καθόλου επιλογή ψωμιού, ώστε το κινητό να δείχνει το ίδιο με το ταμείο. */
private fun hasBreadChoice(category: String) =
    category == "ΤΥΛΙΧΤΑ" || category == "ΜΕΡΙΔΕΣ" || category == "ΜΕΡΙΔΕΣ ΠΑΠΠΟΥ"

/** Μόνο τα ΤΥΛΙΧΤΑ χωνεύουν το ψωμί μέσα στο όνομα («ΑΡ. Κοτόπουλο») — οι ΜΕΡΙΔΕΣ το δείχνουν σε
 * ξεχωριστή γραμμή ολόγραφο (βλ. CustomizerSheet), δεν βγάζει νόημα «ΑΡ. Μερίδα κοτόπουλο». */
private fun fuseBreadIntoName(category: String) = category == "ΤΥΛΙΧΤΑ"

private fun breadAbbreviation(bread: String) = when (bread) {
    "Ελληνική" -> "ΕΛ."
    "Αραβική" -> "ΑΡ."
    "Ψωμί" -> "Ψ."
    else -> if (bread.isNotEmpty()) bread.take(1).uppercase() + "." else ""
}

private fun composeCustomizedName(productName: String, bread: String): String {
    val prefix = "Πίττα "
    val rest = if (productName.startsWith(prefix, ignoreCase = true)) productName.substring(prefix.length) else productName
    return breadAbbreviation(bread) + " " + rest
}

/** Ίδια λογική με το ταμείο (βλ. MenuSeed.SupportsDoublePita στο PittaPos.Core) — μόνο ΤΥΛΙΧΤΑ και
 * ΚΛΑΣΙΚΑ ΜΙΝΙ έχουν την επιλογή «διπλή πίτα». Δέχεται και το παλιό «ΚΛΑΣΙΚΑ ΜΙΚΡΑ» για καταστήματα
 * που δεν έχουν ξαναγράψει το menu.json τους μετά τη μετονομασία. Ελέγχει ΚΑΙ ότι ο server έστειλε
 * χρέωση γι' αυτή την κατηγορία (doublePitaPrices) — αλλιώς δεν έχει ρυθμιστεί ακόμα από τη Διαχείριση
 * Καταλόγου του ταμείου, οπότε η επιλογή δεν έχει νόημα να φανεί.</summary> */
private fun supportsDoublePita(category: String, options: CustomizerOptionsDto) =
    (category == "ΤΥΛΙΧΤΑ" || category == "ΚΛΑΣΙΚΑ ΜΙΝΙ" || category == "ΚΛΑΣΙΚΑ ΜΙΚΡΑ") &&
        options.doublePitaPrices.orEmpty().containsKey(category)

/** Ίδια λογική με MenuSeed.ComposeDoublePitaName στο ταμείο — ΤΥΛΙΧΤΑ βάζει ψωμί+«ΔΙΠΛΗ ΠΙΤΑ» μπροστά
 * από το όνομα (χωρίς το «Πίττα »), ΚΛΑΣΙΚΑ ΜΙΝΙ/ΜΙΚΡΑ αντικαθιστά το «Μίνι»/«Μικρό» ώστε να μη
 * διπλασιάζεται η λέξη. */
private fun composeDoublePitaName(productName: String, category: String, bread: String): String {
    if (category == "ΤΥΛΙΧΤΑ") {
        val prefix = "Πίττα "
        val rest = if (productName.startsWith(prefix, ignoreCase = true)) productName.substring(prefix.length) else productName
        return breadAbbreviation(bread) + " ΔΙΠΛΗ ΠΙΤΑ " + rest
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
    if (removed.size >= allIngredients.size) return listOf("σκέτο")
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
    var customizingCategory by remember { mutableStateOf("") }
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

    fun changeSimpleQuantity(product: MenuProductDto, category: String, delta: Int) {
        val key = "p:${product.id}"
        val idx = cartLines.indexOfFirst { it.key == key }
        if (idx < 0) {
            if (delta > 0) {
                // Ίδια λογική με το ταμείο (βλ. ProductsViewModel.TapProduct) — ένα γρήγορο tap σε
                // customizable προϊόν παίρνει το προεπιλεγμένο ψωμί, ώστε το όνομα να δείχνει ήδη ό,τι θα
                // έβγαινε αν είχε ανοίξει κανείς τον customizer, όχι το γυμνό όνομα προϊόντος.
                val defaultBread = customizerOptions?.breads?.firstOrNull() ?: ""
                val name = if (product.customizable && hasBreadChoice(category) && fuseBreadIntoName(category))
                    composeCustomizedName(product.name, defaultBread) else product.name
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
        customizingCategory = categories.firstOrNull { cat -> cat.products.any { it.id == line.productId } }?.name ?: ""
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
            CustomizerSheet(
                product = product,
                category = customizingCategory,
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
                            onOpenCustomizer = { customizingProduct = product; customizingCategory = category.name },
                            onInc = { changeSimpleQuantity(product, category.name, 1) },
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

/** Κλικ πάνω στο προϊόν προσθέτει (1, 2, 3...) — το «+» δεξιά ανοίγει πάντα την προσαρμογή/έξτρα. */
@Composable
private fun ProductCard(
    product: MenuProductDto,
    quantity: Int,
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
        border = BorderStroke(1.dp, if (inCart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onInc),
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
    category: String,
    options: CustomizerOptionsDto,
    initial: DraftLine? = null,
    onDismiss: () -> Unit,
    onAdd: (DraftLine) -> Unit,
) {
    val noteFocusManager = LocalFocusManager.current
    val noteKeyboardController = LocalSoftwareKeyboardController.current
    val breadChoice = hasBreadChoice(category)
    val fuseBread = fuseBreadIntoName(category)
    val showDoublePita = supportsDoublePita(category, options)
    val doublePitaPrice = options.doublePitaPrices.orEmpty()[category] ?: 0.0
    var quantity by remember { mutableStateOf(initial?.quantity ?: 1) }
    var selectedBread by remember { mutableStateOf(initial?.bread ?: options.breads.firstOrNull() ?: "") }
    val removed = remember { mutableStateListOf<String>().apply { initial?.removedIngredients?.let(::addAll) } }
    val extraQty = remember { mutableStateMapOf<String, Int>().apply { initial?.extras?.let(::putAll) } }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var isDoublePita by remember { mutableStateOf(initial?.doublePita ?: false) }
    // Ίδια λογική με CustomizerViewModel.IsSketo στο ταμείο — «σκέτο» σημαίνει όλα τα υλικά αφαιρεμένα.
    val isSketo = options.ingredients.isNotEmpty() && removed.size == options.ingredients.size
    // Το bottom sheet κλείνει με animation — ένα γρήγορο διπλό tap στο ΠΡΟΣΘΗΚΗ προλαβαίνει να πατηθεί
    // δύο φορές πριν προλάβει να κλείσει, προσθέτοντας το ίδιο είδος διπλό στο καλάθι.
    var submitted by remember { mutableStateOf(false) }

    val extrasCost = options.extras.sumOf { (extraQty[it.name] ?: 0) * it.price }
    val unitPrice = product.price + extrasCost + (if (showDoublePita && isDoublePita) doublePitaPrice else 0.0)
    val lineTotal = unitPrice * quantity

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
            doublePita -> composeDoublePitaName(product.name, category, selectedBread)
            breadChoice && fuseBread -> composeCustomizedName(product.name, selectedBread)
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
            addAll(describeRemovedIngredients(removed, options.ingredients))
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
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
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

            if (breadChoice && options.breads.isNotEmpty()) {
                SectionLabel("Ψωμί")
                options.breads.forEach { bread ->
                    SelectableRow(selected = selectedBread == bread, onClick = { selectedBread = bread }) {
                        RadioButton(selected = selectedBread == bread, onClick = { selectedBread = bread })
                        Text(bread)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Διπλή πίτα — μόνο ΤΥΛΙΧΤΑ/ΚΛΑΣΙΚΑ ΜΙΝΙ, ίδια λογική με CustomizerViewModel.ShowDoublePitaOption
            // στο ταμείο. Δεν χρειάζεται επιλογή ψωμιού (ΚΛΑΣΙΚΑ ΜΙΝΙ δεν έχει καν), γι' αυτό δική της ενότητα.
            if (showDoublePita) {
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

            if (options.ingredients.isNotEmpty()) {
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
                            removed.addAll(options.ingredients)
                        }
                    }) { Text(if (isSketo) "✓ ΣΚΕΤΟ" else "ΣΚΕΤΟ") }
                }
                options.ingredients.forEach { ing ->
                    val included = ing !in removed
                    SelectableRow(
                        selected = included,
                        onClick = { if (included) removed.add(ing) else removed.remove(ing) },
                    ) {
                        Checkbox(checked = included, onCheckedChange = {
                            if (it) removed.remove(ing) else removed.add(ing)
                        })
                        Text(ing)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (options.extras.isNotEmpty()) {
                SectionLabel("Έξτρα")
                options.extras.forEach { extra ->
                    val qty = extraQty[extra.name] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(extra.name, fontWeight = FontWeight.Medium)
                            Text(
                                if (extra.price > 0) String.format(Locale.getDefault(), "+€%.2f", extra.price) else "δωρεάν",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        QuantityStepper(
                            qty,
                            onInc = { extraQty[extra.name] = (qty + 1).coerceAtMost(10) },
                            onDec = { if (qty > 0) extraQty[extra.name] = qty - 1 },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

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
private fun SelectableRow(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
