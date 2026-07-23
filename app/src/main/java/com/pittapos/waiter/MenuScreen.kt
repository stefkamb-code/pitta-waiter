package com.pittapos.waiter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

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
    val cartLines = remember { mutableStateListOf<DraftLine>() }
    var lineCounter by remember { mutableStateOf(0) }
    val snackbarHost = remember { SnackbarHostState() }

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

    fun changeSimpleQuantity(product: MenuProductDto, delta: Int) {
        val key = "p:${product.id}"
        val idx = cartLines.indexOfFirst { it.key == key }
        if (idx < 0) {
            if (delta > 0) cartLines.add(DraftLine(key, product.id, product.name, product.price, 1))
            return
        }
        val newQty = cartLines[idx].quantity + delta
        if (newQty <= 0) cartLines.removeAt(idx) else cartLines[idx] = cartLines[idx].copy(quantity = newQty)
    }

    customizingProduct?.let { product ->
        customizerOptions?.let { options ->
            CustomizerSheet(
                product = product,
                options = options,
                onDismiss = { customizingProduct = null },
                onAdd = { line -> lineCounter++; cartLines.add(line.copy(key = "c:$lineCounter")); customizingProduct = null },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(selectedCategory?.name ?: "Τραπέζι $table") },
                navigationIcon = {
                    IconButton(onClick = { if (selectedCategory != null) selectedCategory = null else onDone() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Πίσω")
                    }
                },
            )
        },
        bottomBar = {
            if (itemCount > 0) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            String.format(Locale.getDefault(), "%d είδη — €%.2f", itemCount, total),
                            fontWeight = FontWeight.Bold,
                        )
                        Button(
                            enabled = !sending,
                            onClick = {
                                scope.launch {
                                    sending = true
                                    val lines = cartLines.map { l ->
                                        OrderLineRequest(l.productId, l.quantity, l.bread, l.removedIngredients, l.extras, l.note)
                                    }
                                    try {
                                        val response = ApiClient.create(prefs.serverUrl).submitOrder(
                                            SubmitOrderRequest(table = table, pin = prefs.pin, lines = lines),
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
                        ) { Text(if (sending) "..." else "ΑΠΟΣΤΟΛΗ ΠΑΡΑΓΓΕΛΙΑΣ") }
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Text(error.orEmpty(), modifier = Modifier.align(Alignment.Center).padding(24.dp))
                selectedCategory == null -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(categories) { cat ->
                        CategoryRow(cat, cartCount = cartLines.count { line -> cat.products.any { it.id == line.productId } }) {
                            selectedCategory = cat
                        }
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(selectedCategory!!.products) { product ->
                        ProductRow(
                            product = product,
                            quantity = simpleQuantity(product),
                            onOpenCustomizer = { customizingProduct = product },
                            onInc = { changeSimpleQuantity(product, 1) },
                            onDec = { changeSimpleQuantity(product, -1) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: MenuCategoryDto, cartCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickableRow(onClick)
            .padding(16.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(category.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("${category.products.size} είδη")
        }
        if (cartCount > 0) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                Text(
                    "$cartCount",
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
    }
    HorizontalDivider()
}

@Composable
private fun ProductRow(
    product: MenuProductDto,
    quantity: Int,
    onOpenCustomizer: () -> Unit,
    onInc: () -> Unit,
    onDec: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .let { if (product.customizable) it.clickableRow(onOpenCustomizer) else it }
            .padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(product.name, fontWeight = FontWeight.SemiBold)
            Text(String.format(Locale.getDefault(), "€%.2f", product.price))
        }
        if (product.customizable) {
            if (quantity > 0) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                    Text(
                        "στο καλάθι: $quantity",
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Προσαρμογή")
        } else {
            IconButton(onClick = onDec) {
                Text("−", fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp))
            }
            Text("$quantity", modifier = Modifier.padding(horizontal = 4.dp))
            IconButton(onClick = onInc) {
                Icon(Icons.Default.Add, contentDescription = "Αύξηση")
            }
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizerSheet(
    product: MenuProductDto,
    options: CustomizerOptionsDto,
    onDismiss: () -> Unit,
    onAdd: (DraftLine) -> Unit,
) {
    var quantity by remember { mutableStateOf(1) }
    var selectedBread by remember { mutableStateOf(options.breads.firstOrNull() ?: "") }
    val removed = remember { mutableStateListOf<String>() }
    val extraQty = remember { mutableStateMapOf<String, Int>() }
    var note by remember { mutableStateOf("") }

    val extrasCost = options.extras.sumOf { (extraQty[it.name] ?: 0) * it.price }
    val unitPrice = product.price + extrasCost
    val lineTotal = unitPrice * quantity

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(product.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(String.format(Locale.getDefault(), "από €%.2f", product.price))
            Spacer(Modifier.height(20.dp))

            if (options.breads.isNotEmpty()) {
                Text("Ψωμί", fontWeight = FontWeight.Bold)
                options.breads.forEach { bread ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickableRow { selectedBread = bread },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedBread == bread, onClick = { selectedBread = bread })
                        Text(bread)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (options.ingredients.isNotEmpty()) {
                Text("Υλικά — ξεμαρκάρισε ό,τι δεν θέλεις", fontWeight = FontWeight.Bold)
                options.ingredients.forEach { ing ->
                    val included = ing !in removed
                    Row(
                        modifier = Modifier.fillMaxWidth().clickableRow {
                            if (included) removed.add(ing) else removed.remove(ing)
                        },
                        verticalAlignment = Alignment.CenterVertically,
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
                Text("Έξτρα", fontWeight = FontWeight.Bold)
                options.extras.forEach { extra ->
                    val qty = extraQty[extra.name] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(extra.name)
                            Text(
                                if (extra.price > 0) String.format(Locale.getDefault(), "+€%.2f", extra.price) else "δωρεάν",
                            )
                        }
                        IconButton(onClick = { if (qty > 0) extraQty[extra.name] = qty - 1 }) { Text("−", fontWeight = FontWeight.Bold) }
                        Text("$qty", modifier = Modifier.padding(horizontal = 4.dp))
                        IconButton(onClick = { extraQty[extra.name] = (qty + 1).coerceAtMost(10) }) {
                            Icon(Icons.Default.Add, contentDescription = "Αύξηση ${extra.name}")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Σημείωση (προαιρετικό)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Ποσότητα", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (quantity > 1) quantity-- }) { Text("−", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                Text("$quantity", modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { quantity++ }) { Icon(Icons.Default.Add, contentDescription = "Αύξηση ποσότητας") }
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    val descLine1 = selectedBread + " πίττα" + (if (note.isNotBlank()) " · ${note.trim()}" else "")
                    val mods = buildList {
                        addAll(removed.map { "χωρίς " + it.replaceFirstChar(Char::lowercaseChar) })
                        addAll(extraQty.filterValues { it > 0 }.map { (name, qty) -> "+ $name" + (if (qty > 1) " ×$qty" else "") })
                    }
                    val details = (listOf(descLine1) + listOf(mods.joinToString(" · "))).filter { it.isNotEmpty() }.joinToString(" · ")
                    onAdd(
                        DraftLine(
                            key = "",
                            productId = product.id,
                            name = product.name,
                            unitPrice = unitPrice,
                            quantity = quantity,
                            bread = selectedBread,
                            removedIngredients = removed.toList(),
                            extras = extraQty.filterValues { it > 0 }.toMap(),
                            note = note.trim(),
                            details = details,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ΠΡΟΣΘΗΚΗ · " + String.format(Locale.getDefault(), "€%.2f", lineTotal)) }
        }
    }
}
