package com.pittapos.waiter

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
    // Ποιο τραπέζι μόλις άνοιξε ο σερβιτόρος και περιμένει την απάντηση «πόσα άτομα;».
    var askPersonsFor by remember { mutableStateOf<Int?>(null) }
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

    // Δεμένο στο lifecycle της οθόνης: όταν η εφαρμογή πάει background (κλείδωμα κινητού, αλλαγή
    // εφαρμογής) το polling σταματά αντί να χτυπάει το ταμείο κάθε 4" επ' αόριστον, και ξαναρχίζει
    // μόνο του μόλις η οθόνη ξαναγίνει ορατή.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(serverUrl, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                load()
                delay(4000)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            prefs = prefs,
            onDismiss = { showSettings = false },
            onSaved = { serverUrl = prefs.serverUrl },
        )
    }

    askPersonsFor?.let { table ->
        PersonsDialog(
            table = table,
            onDismiss = { askPersonsFor = null },
            onPick = { count ->
                askPersonsFor = null
                scope.launch {
                    // Η αποτυχία ΔΕΝ σταματά τη δουλειά: ο σερβιτόρος μπαίνει στο τραπέζι έτσι κι
                    // αλλιώς και τα άτομα μπαίνουν από το ταμείο. Το να μπλοκάρει η παραγγελία επειδή
                    // δεν καταγράφηκε ο διαχωρισμός θα ήταν πολύ χειρότερο από το να λείπει.
                    try {
                        ApiClient.create(serverUrl).setPersons(table, SetPersonsRequest(prefs.pin, count))
                    } catch (e: Exception) {
                        snackbarHost.showSnackbar("Τα άτομα δεν καταχωρήθηκαν — βάλ' τα από το ταμείο")
                    }
                    onOpenTable(table)
                }
            },
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
                    IconButton(onClick = { scope.launch { loading = true; load() } }, enabled = !loading) {
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
                    // Κλειστό τραπέζι = νέα παρέα: ρωτάμε πόσα άτομα ΠΡΙΝ ανοίξει, γιατί ο σερβιτόρος
                    // το ξέρει εκείνη ακριβώς τη στιγμή. Ανοιχτό τραπέζι μπαίνει κατευθείαν — τα άτομα
                    // έχουν ήδη δηλωθεί και δεν ξαναρωτιούνται σε κάθε γύρο.
                    items(tables) { t ->
                        TableCard(t, onClick = {
                            if (t.isOpen) onOpenTable(t.number) else askPersonsFor = t.number
                        })
                    }
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
                    if (table.persons > 1) "${table.roundCount} παραγγελίες · ${table.persons} άτομα"
                    else "${table.roundCount} παραγγελίες",
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

/**
 * «Πόσα άτομα;» — η ερώτηση τη στιγμή που ανοίγει το τραπέζι.
 *
 * Το μαγαζί κόβει τις αποδείξεις σε ξεχωριστή ταμειακή μηχανή και ο κανόνας είναι **ένα άτομο = μία
 * απόδειξη**. Ο σερβιτόρος το ξέρει αμέσως μόλις καθίσει η παρέα («όλοι μαζί» / «ο καθένας το δικό
 * του»), οπότε ρωτιέται εδώ και όχι στο τέλος — στο τέλος θα χρειαζόταν να ξαναθυμηθεί ποιος πήρε τι.
 *
 * Μεγάλα κουμπιά με νούμερα: ο σερβιτόρος το πατάει όρθιος, με το ένα χέρι, μέσα σε δευτερόλεπτο.
 */
@Composable
private fun PersonsDialog(table: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    // Τα κουμπιά πιάνουν μέχρι το 11 — από εκεί και πάνω είναι εκδήλωση, όχι τραπέζι, και δεν αξίζει να
    // γεμίζει η οθόνη με νούμερα που δεν πατιούνται ποτέ. Το «＋» ανοίγει πληκτρολόγιο για τον αριθμό.
    var typing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val typedCount = typed.toIntOrNull()
    val typedOk = typedCount != null && typedCount in 1..MAX_PERSONS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Τραπέζι $table — πόσα άτομα;", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    "Κάθε άτομο = μία απόδειξη. Αν πληρώσουν όλοι μαζί, πάτα «Μαζί».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                if (typing) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Αριθμός ατόμων") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "μέχρι $MAX_PERSONS άτομα",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    for (row in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (col in 1..4) {
                                val n = row * 4 + col
                                val isPlus = n > 11
                                FilledTonalButton(
                                    onClick = { if (isPlus) typing = true else onPick(n) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 14.dp),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Text(if (isPlus) "＋" else "$n", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            if (typing) {
                TextButton(onClick = { typedCount?.let(onPick) }, enabled = typedOk) { Text("ΟΚ") }
            } else {
                TextButton(onClick = { onPick(1) }) { Text("Μαζί") }
            }
        },
        dismissButton = {
            // Μέσα στο πληκτρολόγιο το «Άκυρο» γυρνά στα κουμπιά, δεν κλείνει όλο τον διάλογο — αλλιώς
            // ένα λάθος πάτημα στο «＋» θα σε πετούσε έξω και θα ξανάρχιζες.
            TextButton(onClick = { if (typing) typing = false else onDismiss() }) {
                Text(if (typing) "Πίσω" else "Άκυρο")
            }
        },
        shape = MaterialTheme.shapes.large,
    )
}

/** Ίδιο όριο με το ταμείο (TablePersonsService.MaxPersons) — πάνω από αυτό είναι λάθος πάτημα. */
private const val MAX_PERSONS = 24

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
                Spacer(Modifier.height(16.dp))
                // Ίδιος αριθμός με την αρχική οθόνη — εδώ βρίσκεται χωρίς να χρειαστεί επανεκκίνηση,
                // όταν ψάχνουμε ποιο κινητό έμεινε σε παλιό APK.
                Text(
                    "Έκδοση εφαρμογής " + rememberAppVersionName(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
