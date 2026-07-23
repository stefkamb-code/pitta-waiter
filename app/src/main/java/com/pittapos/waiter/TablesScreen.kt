package com.pittapos.waiter

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    val openCount = tables.count { it.isOpen }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ΤΡΑΠΕΖΙΑ", fontWeight = FontWeight.ExtraBold)
                        if (!loading && error == null) {
                            Text(
                                if (openCount == 0) "όλα ελεύθερα" else "$openCount ανοιχτά",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { loading = true; load() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Ανανέωση")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ρυθμίσεις")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> EmptyState(
                    icon = Icons.Default.WifiOff,
                    message = error.orEmpty(),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
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
private fun EmptyState(icon: ImageVector, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TableCard(table: TableDto, onClick: () -> Unit) {
    val elevation by animateDpAsState(if (table.isOpen) 2.dp else 0.dp, label = "tableElevation")
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(116.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (table.isOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = if (table.isOpen) BorderStroke2(MaterialTheme.colorScheme.primary) else BorderStroke2(MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (table.isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Τραπέζι ${table.number}",
                    fontWeight = FontWeight.ExtraBold,
                    color = if (table.isOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (table.isOpen) {
                Text(
                    String.format(Locale.getDefault(), "€%.2f", table.total),
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "${table.roundCount} παραγγελίες",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            } else {
                Text(
                    "ελεύθερο",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Μικρό βοηθητικό ώστε το BorderStroke να μη χρειάζεται ξεχωριστό import πρόθεμα παντού. */
private fun BorderStroke2(color: androidx.compose.ui.graphics.Color) =
    androidx.compose.foundation.BorderStroke(1.dp, color)

@OptIn(ExperimentalMaterial3Api::class)
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
                    shape = MaterialTheme.shapes.small,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN ταμείου") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
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
        shape = MaterialTheme.shapes.large,
    )
}
