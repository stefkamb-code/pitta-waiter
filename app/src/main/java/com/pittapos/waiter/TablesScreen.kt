package com.pittapos.waiter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TablesScreen(prefs: AppPrefs, onOpenTable: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(prefs.serverUrl) }
    var tables by remember { mutableStateOf<List<TableDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    suspend fun load() {
        try {
            tables = ApiClient.create(serverUrl).getTables()
            error = null
        } catch (e: Exception) {
            error = "Δεν συνδέθηκε με το ταμείο — έλεγξε ότι είσαι στο ίδιο WiFi (⚙ ρυθμίσεις)"
        }
        loading = false
    }

    LaunchedEffect(serverUrl) {
        while (true) {
            load()
            delay(4000)
        }
    }

    if (showSettings) {
        SettingsDialog(
            prefs = prefs,
            onDismiss = { showSettings = false },
            onSaved = { serverUrl = prefs.serverUrl },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("ΤΡΑΠΕΖΙΑ") },
                actions = {
                    IconButton(onClick = { scope.launch { loading = true; load() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Ανανέωση")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ρυθμίσεις")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Text(
                    error.orEmpty(),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(tables) { t -> TableCard(t, onClick = { onOpenTable(t.number) }) }
                }
            }
        }
    }
}

@Composable
private fun TableCard(table: TableDto, onClick: () -> Unit) {
    val bg = if (table.isOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Τραπέζι ${table.number}", fontWeight = FontWeight.ExtraBold)
        if (table.isOpen) {
            Spacer(Modifier.height(6.dp))
            Text(String.format(Locale.getDefault(), "€%.2f", table.total), fontWeight = FontWeight.Bold)
            Text("${table.roundCount} παραγγελίες")
        } else {
            Spacer(Modifier.height(6.dp))
            Text("ελεύθερο")
        }
    }
}

@Composable
private fun SettingsDialog(prefs: AppPrefs, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var url by remember { mutableStateOf(prefs.serverUrl) }
    var pin by remember { mutableStateOf(prefs.pin) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ρυθμίσεις") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Διεύθυνση ταμείου (IP:θύρα)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN ταμείου") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.serverUrl = url.trim()
                prefs.pin = pin.trim()
                onSaved()
                onDismiss()
            }) { Text("Αποθήκευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } },
    )
}
