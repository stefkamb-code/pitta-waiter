package com.pittapos.waiter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.util.Locale

private typealias LineKey = Pair<Int, Int>

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableDetailScreen(prefs: AppPrefs, table: Int, onAddMore: (Int) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var orders by remember { mutableStateOf<List<TableOrderDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<LineKey>() }
    var pendingCloseTable by remember { mutableStateOf(false) }
    // Ποιες γραμμές περιμένουν να δηλωθεί ΠΩΣ πληρώθηκαν. Ο σερβιτόρος πατάει πρώτα «πληρωμένο» και το
    // μετρητά/κάρτα ρωτιέται μετά — τα δύο κουμπιά μπροστά γέμιζαν την οθόνη σε κάθε άτομο.
    var pendingSettle by remember { mutableStateOf<List<LineKey>?>(null) }
    var closing by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    suspend fun load() {
        try {
            orders = ApiClient.create(prefs.serverUrl).getTableOrders(table, prefs.pin)
            error = null
        } catch (e: Exception) {
            error = "Δεν φορτώθηκαν οι παραγγελίες — έλεγξε τη σύνδεση"
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    val outstanding = orders.sumOf { o -> o.lines.filter { !it.isSettled }.sumOf { it.revenue } }

    // Όλες οι γραμμές του τραπεζιού μαζεμένες ανά ΑΤΟΜΟ. Το null (ΑΧΡΕΩΤΑ) πάει τελευταίο: είναι ό,τι
    // δεν χρεώθηκε σε κανέναν — παραγγελία από το ταμείο πριν μπουν τα άτομα, ή κοινό πιάτο.
    val personGroups = orders
        .flatMap { o -> o.lines.map { o to it } }
        .groupBy { it.second.person }
        .toList()
        .sortedBy { it.first ?: Int.MAX_VALUE }
    val selectedTotal = orders.sumOf { o ->
        o.lines.filter { (o.orderNumber to it.lineIndex) in selected }.sumOf { it.revenue }
    }

    fun toggleSelect(order: TableOrderDto, line: TableOrderLineDto) {
        val key = order.orderNumber to line.lineIndex
        if (key in selected) selected.remove(key) else selected.add(key)
    }

    /** Σημειώνει πληρωμένες τις γραμμές — με ΤΟΝ ΤΡΟΠΟ που διάλεξε ο σερβιτόρος, όπως στο ταμείο. */
    fun settleLines(keys: List<LineKey>, method: String) {
        pendingSettle = null
        if (keys.isEmpty() || settling) return
        scope.launch {
            settling = true
            try {
                val api = ApiClient.create(prefs.serverUrl)
                var pinError = false
                var otherError = false
                for ((orderNumber, lineIndex) in keys) {
                    val response = api.settleLine(table, SettleLineRequest(prefs.pin, orderNumber, lineIndex, method))
                    if (!response.isSuccessful) {
                        if (response.code() == 401) pinError = true else otherError = true
                        break
                    }
                }
                selected.clear()
                load()
                if (pinError) snackbarHost.showSnackbar("Λάθος PIN — άλλαξέ το στις ρυθμίσεις")
                else if (otherError) snackbarHost.showSnackbar("Κάποια είδη δεν εξοφλήθηκαν")
            } catch (e: Exception) {
                snackbarHost.showSnackbar("Αποτυχία σύνδεσης")
            } finally {
                settling = false
            }
        }
    }

    /** Κλείνει το τραπέζι· ό,τι έμεινε απλήρωτο καταγράφεται ως πληρωμένο με αυτόν τον τρόπο. */
    fun closeTableWith(method: String) {
        pendingCloseTable = false
        scope.launch {
            closing = true
            try {
                val response = ApiClient.create(prefs.serverUrl).closeTable(table, CloseTableRequest(prefs.pin, method))
                if (response.isSuccessful) onBack()
                else snackbarHost.showSnackbar(
                    if (response.code() == 401) "Λάθος PIN — άλλαξέ το στις ρυθμίσεις" else "Απέτυχε η πληρωμή",
                )
            } catch (e: Exception) {
                snackbarHost.showSnackbar("Αποτυχία σύνδεσης")
            } finally {
                closing = false
            }
        }
    }

    // «Πληρώθηκε» → ΤΩΡΑ ρωτάμε πώς, σε παραθυράκι στη ΜΕΣΗ της οθόνης: εκεί κοιτάει ο σερβιτόρος μόλις
    // πατήσει, ενώ στην κάτω μπάρα έπρεπε να κατεβάσει το μάτι του στη γωνία.
    pendingSettle?.let { keys ->
        PaymentMethodDialog(
            title = "ΠΩΣ ΠΛΗΡΩΘΗΚΕ;",
            amount = selectedTotal,
            enabled = !settling,
            onCash = { settleLines(keys, PaymentMethod.CASH) },
            onCard = { settleLines(keys, PaymentMethod.CARD) },
            onCancel = { pendingSettle = null },
        )
    }

    if (pendingCloseTable) {
        PaymentMethodDialog(
            title = "ΠΩΣ ΠΛΗΡΩΘΗΚΕ ΤΟ ΥΠΟΛΟΙΠΟ;",
            amount = outstanding,
            enabled = !closing,
            onCash = { closeTableWith(PaymentMethod.CASH) },
            onCard = { closeTableWith(PaymentMethod.CARD) },
            onCancel = { pendingCloseTable = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Τραπέζι $table", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Πίσω") }
                },
                actions = {
                    IconButton(onClick = { scope.launch { loading = true; load() } }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Ανανέωση")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (selected.isEmpty()) {
                // Σε τραπέζι που πληρώνει χωριστά, η προσθήκη είναι ΠΡΟΣΘΗΚΗ ΑΤΟΜΟΥ: ήρθε κι άλλος στην
                // παρέα και θέλει δική του απόδειξη. Ανεβάζουμε το πλήθος ατόμων κατά ένα και η οθόνη
                // παραγγελίας ξεκινά μόνη της από αυτόν (τον πρώτο που δεν έχει παραγγείλει).
                val splitting = personGroups.any { it.first != null }
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!splitting) {
                            onAddMore(-1)
                            return@ExtendedFloatingActionButton
                        }
                        scope.launch {
                            val known = personGroups.mapNotNull { it.first }
                            val next = (known.maxOrNull() ?: -1) + 2
                            try {
                                ApiClient.create(prefs.serverUrl).setPersons(table, SetPersonsRequest(prefs.pin, next))
                            } catch (e: Exception) {
                                // Χωρίς δίκτυο δεν μπλοκάρουμε — μπαίνει στο μενού κανονικά.
                            }
                            onAddMore(-1)
                        }
                    },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = {
                        Text(if (splitting) "ΠΡΟΣΘΗΚΗ ΑΤΟΜΟΥ" else "ΠΡΟΣΘΗΚΗ", fontWeight = FontWeight.Bold)
                    },
                )
            }
        },
        bottomBar = {
            when {
                selected.isNotEmpty() -> BottomBarSurface {
                    // Ενικός στο ένα προϊόν: «ΠΛΗΡΩΜΕΝΑ» με ένα επιλεγμένο διαβάζεται σαν να πληρώνονται
                    // πολλά, και ο σερβιτόρος δεύτερη φορά κοιτάει τι ακριβώς θα χρεωθεί.
                    val one = selected.size == 1
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp, 14.dp)) {
                        BarHeader("${selected.size} " + if (one) "ΕΠΙΛΕΓΜΕΝΟ" else "ΕΠΙΛΕΓΜΕΝΑ", selectedTotal)
                        Button(
                            enabled = !settling,
                            onClick = { pendingSettle = selected.toList() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (one) "✓ ΠΛΗΡΩΜΕΝΟ" else "✓ ΠΛΗΡΩΜΕΝΑ", fontWeight = FontWeight.Bold) }
                    }
                }
                orders.isNotEmpty() -> BottomBarSurface {
                    OutlinedButton(
                        enabled = !closing,
                        onClick = { pendingCloseTable = true },
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
                    ) { Text("✕ ΠΛΗΡΩΜΗ ΟΛΟΥ ΤΟΥ ΤΡΑΠΕΖΙΟΥ", fontWeight = FontWeight.Bold) }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> ErrorMessage(error.orEmpty(), Modifier.align(Alignment.Center))
                orders.isEmpty() -> EmptyTableMessage(Modifier.align(Alignment.Center))
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    OutstandingHeader(outstanding)
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Χωρισμένο σε ΑΤΟΜΑ όταν το τραπέζι πληρώνει χωριστά — μία ομάδα = μία απόδειξη
                        // στην ταμειακή. Αλλιώς μένει όπως ήταν, ανά γύρο παραγγελίας.
                        if (personGroups.any { it.first != null }) {
                            items(personGroups) { (person, entries) ->
                                PersonCard(
                                    person = person,
                                    entries = entries,
                                    isSelected = { o, line -> (o.orderNumber to line.lineIndex) in selected },
                                    onToggle = { o, line -> toggleSelect(o, line) },
                                    // «Το ΑΤΟΜΟ Β θέλει και μια κόκα κόλα»: γράφεται στη ΔΙΚΗ του
                                    // απόδειξη. Τα ΑΧΡΕΩΤΑ δεν είναι άτομο, δεν έχουν κουμπί.
                                    onAddMore = person?.let { p -> { onAddMore(p) } },
                                )
                            }
                        } else {
                            items(orders) { order ->
                                RoundCard(
                                    order = order,
                                    isSelected = { line -> (order.orderNumber to line.lineIndex) in selected },
                                    onToggle = { line -> toggleSelect(order, line) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorMessage(message: String, modifier: Modifier = Modifier) {
    Text(message, modifier = modifier.padding(24.dp), textAlign = TextAlign.Center)
}

@Composable
private fun EmptyTableMessage(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Receipt,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Δεν έχει παραγγελθεί ακόμα τίποτα",
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "πάτα ΠΡΟΣΘΗΚΗ για να ξεκινήσεις",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OutstandingHeader(outstanding: Double) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp, 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "ΟΦΕΙΛΟΜΕΝΟ",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                String.format(Locale.getDefault(), "€%.2f", outstanding),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Ένα άτομο με ό,τι πήρε — **μία κάρτα = μία απόδειξη** στην ταμειακή του μαγαζιού. */
@Composable
private fun PersonCard(
    person: Int?,
    entries: List<Pair<TableOrderDto, TableOrderLineDto>>,
    isSelected: (TableOrderDto, TableOrderLineDto) -> Boolean,
    onToggle: (TableOrderDto, TableOrderLineDto) -> Unit,
    onAddMore: (() -> Unit)? = null,
) {
    val outstanding = entries.filter { !it.second.isSettled }.sumOf { it.second.revenue }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (person == null) "ΑΧΡΕΩΤΑ" else "ΑΤΟΜΟ ${personLabel(person)}",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (outstanding > 0) String.format(Locale.getDefault(), "%.2f€", outstanding) else "✓ πληρωμένο",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (outstanding > 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entries.forEachIndexed { index, (order, line) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OrderLineRow(line, selected = isSelected(order, line)) { onToggle(order, line) }
            }
            if (onAddMore != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = onAddMore, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ΠΡΟΣΘΗΚΗ ΣΕ ΑΥΤΟΝ", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun BottomBarSurface(content: @Composable () -> Unit) {
    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface, content = content)
}

/** Τίτλος αριστερά, ποσό δεξιά — η κοινή κεφαλίδα κάθε κατάστασης της κάτω μπάρας. */
@Composable
private fun BarHeader(title: String, amount: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        Text(
            String.format(Locale.getDefault(), "€%.2f", amount),
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/**
 * ΜΕΤΡΗΤΑ / ΚΑΡΤΑ — τα ίδια δύο κουμπιά που έχει το ταμείο σε κάθε είσπραξη τραπεζιού.
 *
 * Ο τρόπος πληρωμής δηλώνεται ΤΗ ΣΤΙΓΜΗ που πληρώνεται ο καθένας, γιατί μέσα στην ίδια παρέα άλλος
 * δίνει μετρητά κι άλλος κάρτα. Πριν, το κινητό δεν ρωτούσε καθόλου και το ταμείο κατέγραφε τα πάντα
 * ως μετρητά — ο διαχωρισμός της αναφοράς ημέρας έβγαινε λάθος.
 */
@Composable
private fun PaymentMethodDialog(
    title: String,
    amount: Double,
    enabled: Boolean,
    onCash: () -> Unit,
    onCard: () -> Unit,
    onCancel: () -> Unit,
) {
    // Σκέτο Dialog και όχι AlertDialog: το AlertDialog έβαζε τα κουμπιά μέσα στο «κείμενό» του και
    // έβγαιναν άχρωμα/δυσδιάκριτα — εδώ φτιάχνουμε την κάρτα μόνοι μας, με τα ίδια χρώματα του ταμείου.
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp, 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    String.format(Locale.getDefault(), "€%.2f", amount),
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("ΜΕΤΡΗΤΑ" to onCash, "ΚΑΡΤΑ" to onCard).forEach { (label, onClick) ->
                        Button(
                            onClick = onClick,
                            enabled = enabled,
                            modifier = Modifier.weight(1f).height(54.dp),
                        ) { Text(label, fontWeight = FontWeight.ExtraBold, maxLines = 1) }
                    }
                }
                TextButton(onClick = onCancel) { Text("ΑΚΥΡΟ", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun RoundCard(
    order: TableOrderDto,
    isSelected: (TableOrderLineDto) -> Boolean,
    onToggle: (TableOrderLineDto) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Text(
                order.timeLabel,
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (order.note.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 8.dp),
                ) {
                    Text(
                        "💬 " + order.note,
                        modifier = Modifier.padding(10.dp, 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            order.lines.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OrderLineRow(line, selected = isSelected(line)) { onToggle(line) }
            }
        }
    }
}

@Composable
private fun OrderLineRow(line: TableOrderLineDto, selected: Boolean, onToggle: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified
    val textColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        line.isSettled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (bg != Color.Unspecified) it.background(bg) else it }
            .let { if (!line.isSettled) it.clickable(onClick = onToggle) else it }
            .padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${line.quantity} × ${line.name}",
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textDecoration = if (line.isSettled) TextDecoration.LineThrough else null,
            )
            if (line.details.isNotEmpty()) {
                Text(
                    line.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) textColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (line.isSettled) {
                Text(
                    "✓ πληρώθηκε",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            String.format(Locale.getDefault(), "€%.2f", line.revenue),
            fontWeight = FontWeight.Bold,
            color = textColor,
            textDecoration = if (line.isSettled) TextDecoration.LineThrough else null,
        )
        Spacer(Modifier.width(8.dp))
        val icon: ImageVector = if (line.isSettled) Icons.Default.CheckCircle else if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle
        Icon(
            icon,
            contentDescription = if (line.isSettled) "Πληρωμένο" else if (selected) "Επιλεγμένο" else "Επιλογή",
            tint = if (line.isSettled) MaterialTheme.colorScheme.tertiary else if (selected) textColor else MaterialTheme.colorScheme.outline,
        )
    }
}
