package app.synchealth.hc.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import app.synchealth.hc.hc.HealthConnectWriter
import app.synchealth.hc.model.DataCategory
import app.synchealth.hc.scale.ScaleScanner
import app.synchealth.hc.scale.Sex
import app.synchealth.hc.settings.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { MainScreen() }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    Column(
        Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("HealthConnect Sync", style = MaterialTheme.typography.headlineSmall)
        HealthConnectCard()
        ProfileCard()
        ScaleCard()
        PriorityCard()
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun HealthConnectCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available = remember { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }
    var granted by remember { mutableStateOf(false) }

    suspend fun refresh() {
        if (!available) return
        val perms = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        granted = perms.containsAll(HealthConnectWriter.BODY_PERMISSIONS)
    }

    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        scope.launch { refresh() }
    }
    LaunchedEffect(Unit) { refresh() }

    Section("Health Connect") {
        when {
            !available -> Text("Health Connect is not available on this phone.")
            granted -> Text("Access granted. Samsung Health reads this data once Samsung Health → Settings → Health Connect is on.")
            else -> {
                Text("Allow this app to write weight and body composition.")
                Button(onClick = { launcher.launch(HealthConnectWriter.BODY_PERMISSIONS) }) { Text("Grant access") }
            }
        }
    }
}

@Composable
private fun ProfileCard() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var height by remember { mutableStateOf(store.heightCm.takeIf { it > 0 }?.toString().orEmpty()) }
    var birthYear by remember { mutableStateOf(store.birthYear.takeIf { it > 0 }?.toString().orEmpty()) }
    var sex by remember { mutableStateOf(store.sex ?: Sex.MALE) }
    var saved by remember { mutableStateOf(false) }

    Section("Profile (for body composition)") {
        OutlinedTextField(
            value = height, onValueChange = { height = it; saved = false },
            label = { Text("Height (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = birthYear, onValueChange = { birthYear = it; saved = false },
            label = { Text("Birth year (e.g. 1990)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { s ->
                FilterChip(selected = sex == s, onClick = { sex = s; saved = false }, label = { Text(s.name.lowercase()) })
            }
        }
        val h = height.toFloatOrNull()
        val y = birthYear.toIntOrNull()
        Button(
            enabled = h != null && h in 100f..250f && y != null && y in 1900..2025,
            onClick = { store.saveProfile(h!!, y!!, sex); saved = true },
        ) { Text(if (saved) "Saved" else "Save") }
    }
}

@Composable
private fun ScaleCard() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var enabled by remember { mutableStateOf(store.scaleScanEnabled) }
    var status by remember { mutableStateOf(store.lastStatus) }
    var error by remember { mutableStateOf<String?>(null) }
    var bound by remember { mutableStateOf(store.boundScaleAddress) }

    fun setEnabled(on: Boolean) {
        if (on) {
            if (ScaleScanner.start(context)) {
                store.scaleScanEnabled = true; enabled = true; error = null
            } else {
                error = "Could not start scanning. Check Bluetooth is on and permission is granted."
            }
        } else {
            ScaleScanner.stop(context)
            store.scaleScanEnabled = false; enabled = false
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) setEnabled(true) else error = "Bluetooth permission denied."
    }
    LaunchedEffect(Unit) {
        while (true) {
            status = store.lastStatus
            bound = store.boundScaleAddress
            delay(2000)
        }
    }

    Section("Mi Body Composition Scale 2") {
        Text("Listens for the scale in the background. Step on the scale barefoot; the result is saved within seconds.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = enabled, onCheckedChange = { on ->
                if (on && !ScaleScanner.hasPermission(context)) {
                    permLauncher.launch(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                    )
                } else {
                    setEnabled(on)
                }
            })
            Text(if (enabled) "Listening" else "Off")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Last reading: ${status.ifEmpty { "none yet" }}", style = MaterialTheme.typography.bodySmall)
        Text(
            "Trusted scale: ${bound ?: "the first scale that measures you"}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (bound != null) {
            TextButton(onClick = { store.boundScaleAddress = null; bound = null }) { Text("Forget scale") }
        }
    }
}

@Composable
private fun PriorityCard() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }

    Section("Source priority") {
        Text("When two sources record the same thing within 10 minutes, only the higher one is kept.")
        DataCategory.entries.filter { it.defaultSources.size > 1 }.forEach { category ->
            var order by remember { mutableStateOf(store.priority(category)) }
            Text(category.label, style = MaterialTheme.typography.labelLarge)
            order.forEachIndexed { i, source ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${source.label}", Modifier.weight(1f))
                    TextButton(enabled = i > 0, onClick = {
                        order = order.toMutableList().apply { add(i - 1, removeAt(i)) }
                        store.setPriority(category, order)
                    }) { Text("▲") }
                    TextButton(enabled = i < order.lastIndex, onClick = {
                        order = order.toMutableList().apply { add(i + 1, removeAt(i)) }
                        store.setPriority(category, order)
                    }) { Text("▼") }
                }
            }
        }
        Text(
            "For data written by other apps (e.g. phone steps vs watch steps), set the order in " +
                "Settings → Health Connect → Data and access → pick a type → Data sources and priority.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
