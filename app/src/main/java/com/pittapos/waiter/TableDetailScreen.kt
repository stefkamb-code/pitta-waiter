package com.pittapos.waiter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableDetailScreen(prefs: AppPrefs, table: Int, onAddMore: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var orders by remember { mutableStateOf<List<TableOrderDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingSettle by remember { mutableStateOf<Pair<TableOrderDto, TableOrderLineDto>?>(null) }
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

    pendingSettle?.let { (order, line) ->
        AlertDialog(
            onDismissRequest = { pendingSettle = null },
            title = { Text("Εξόφληση προϊόντος") },
            text = { Text("Πληρώθηκε το «${line.name}» (${String.format(Locale.getDefault(), "€%.2f", line.revenue)});") },
            confirmButton = {
                TextButton(onClick = {
                    pendingSettle = null
                    scope.launch {
                        try {
                            val response = ApiClient.create(prefs.serverUrl).settleLine(
                                table, SettleLineRequest(prefs.pin, order.orderNumber, line.lineIndex),
                            )
                            if (response.isSuccessful) load()
                            else snackbarHost.showSnackbar(
                                if (response.code() == 401) "Λάθος PIN — άλλαξέ το στις ρυθμίσεις" else "Απέτυχε η εξόφληση",
                            )
                        } catch (e: Exception) {
                            snackbarHost.showSnackbar("Αποτυχία σύνδεσης")
                        }
                    }
                }) { Text("Ναι, πληρώθηκε") }
            },
            dismissButton = { TextButton(onClick = { pendingSettle = null }) { Text("Άκυρο") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Τραπέζι $table") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Πίσω") }
                },
                actions = {
                    IconButton(onClick = { scope.launch { loading = true; load() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Ανανέωση")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddMore, icon = { Icon(Icons.Default.Add, null) }, text = { Text("ΠΡΟΣΘΗΚΗ") })
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Text(error.orEmpty(), modifier = Modifier.align(Alignment.Center).padding(24.dp))
                orders.isEmpty() -> Text(
                    "Δεν έχει παραγγελθεί ακόμα τίποτα — πάτα ΠΡΟΣΘΗΚΗ",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Οφειλόμενο", fontWeight = FontWeight.SemiBold)
                            Text(
                                String.format(Locale.getDefault(), "€%.2f", outstanding),
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        orders.forEach { order ->
                            item {
                                Text(
                                    "Γύρος ${order.orderNumber} · ${order.timeLabel}",
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                                )
                            }
                            items(order.lines) { line -> OrderLineRow(line) { pendingSettle = order to line } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderLineRow(line: TableOrderLineDto, onSettle: () -> Unit) {
    val faded = if (line.isSettled) Color.Gray else Color.Unspecified
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${line.quantity} × ${line.name}",
                fontWeight = FontWeight.SemiBold,
                color = faded,
                textDecoration = if (line.isSettled) TextDecoration.LineThrough else null,
            )
            if (line.details.isNotEmpty()) Text(line.details, color = faded)
        }
        Text(String.format(Locale.getDefault(), "€%.2f", line.revenue), color = faded)
        Spacer(Modifier.width(8.dp))
        if (line.isSettled) {
            Text("πληρώθηκε", color = Color.Gray)
        } else {
            IconButton(onClick = onSettle) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Εξόφληση")
            }
        }
    }
}
