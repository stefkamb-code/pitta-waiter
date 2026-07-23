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
import kotlinx.coroutines.launch
import java.util.Locale

private typealias LineKey = Pair<Int, Int>

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableDetailScreen(prefs: AppPrefs, table: Int, onAddMore: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var orders by remember { mutableStateOf<List<TableOrderDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<LineKey>() }
    var pendingCloseTable by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    suspend fun load() {
        try {
            orders = ApiClient.create(prefs.serverUrl).getTableOrders(table)
            error = null
        } catch (e: Exception) {
            error = "Δεν φορτώθηκαν οι παραγγελίες — έλεγξε τη σύνδεση"
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    val outstanding = orders.sumOf { o -> o.lines.filter { !it.isSettled }.sumOf { it.revenue } }
    val selectedTotal = orders.sumOf { o ->
        o.lines.filter { (o.orderNumber to it.lineIndex) in selected }.sumOf { it.revenue }
    }

    fun toggleSelect(order: TableOrderDto, line: TableOrderLineDto) {
        val key = order.orderNumber to line.lineIndex
        if (key in selected) selected.remove(key) else selected.add(key)
    }

    fun settleSelected() {
        val keys = selected.toList()
        if (keys.isEmpty() || settling) return
        scope.launch {
            settling = true
            try {
                val api = ApiClient.create(prefs.serverUrl)
                var pinError = false
                var otherError = false
                for ((orderNumber, lineIndex) in keys) {
                    val response = api.settleLine(table, SettleLineRequest(prefs.pin, orderNumber, lineIndex))
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

    if (pendingCloseTable) {
        AlertDialog(
            onDismissRequest = { pendingCloseTable = false },
            title = { Text("Πληρωμή τραπεζιού") },
            text = { Text("Να κλείσει όλο το τραπέζι $table; Θα ελευθερωθεί για νέους πελάτες.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingCloseTable = false
                    scope.launch {
                        closing = true
                        try {
                            val response = ApiClient.create(prefs.serverUrl).closeTable(table, CloseTableRequest(prefs.pin))
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
                }) { Text("Ναι, πληρώθηκε") }
            },
            dismissButton = { TextButton(onClick = { pendingCloseTable = false }) { Text("Άκυρο") } },
            shape = MaterialTheme.shapes.large,
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
                    IconButton(onClick = { scope.launch { loading = true; load() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Ανανέωση")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (selected.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddMore,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("ΠΡΟΣΘΗΚΗ", fontWeight = FontWeight.Bold) },
                )
            }
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp, 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                "${selected.size} ΕΠΙΛΕΓΜΕΝΑ",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                String.format(Locale.getDefault(), "€%.2f", selectedTotal),
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        Button(
                            enabled = !settling,
                            onClick = { settleSelected() },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        ) { Text("✓ ΠΛΗΡΩΜΕΝΑ", fontWeight = FontWeight.Bold) }
                        TextButton(
                            onClick = { selected.clear() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("ΑΠΟΕΠΙΛΟΓΗ", fontWeight = FontWeight.Bold) }
                    }
                }
            } else if (orders.isNotEmpty()) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
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
