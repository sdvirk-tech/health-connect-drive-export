package ru.sdvirk.healthsync.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.sdvirk.healthsync.R
import ru.sdvirk.healthsync.drive.DriveUploader
import ru.sdvirk.healthsync.export.ExportFileNames
import ru.sdvirk.healthsync.export.FolderExport
import ru.sdvirk.healthsync.export.JsonExporter
import ru.sdvirk.healthsync.export.watchSampleCount
import ru.sdvirk.healthsync.export.withWatchSamples
import ru.sdvirk.healthsync.health.DataProbe
import ru.sdvirk.healthsync.health.HcSettings
import ru.sdvirk.healthsync.health.HealthConnectReader
import ru.sdvirk.healthsync.link.BluetoothPerms
import ru.sdvirk.healthsync.link.WatchLinkService
import ru.sdvirk.healthsync.watch.WatchSampleStore
import ru.sdvirk.healthsync.wear.PhoneIngest
import ru.sdvirk.healthsync.wear.WatchDiagStore
import ru.sdvirk.healthsync.worker.DailyExportWorker
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var reader: HealthConnectReader

    private val requestPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        Toast.makeText(
            this,
            if (granted.containsAll(reader.permissions)) {
                getString(R.string.permissions_ok)
            } else {
                getString(R.string.permissions_partial)
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        getSharedPreferences(DailyExportWorker.PREFS, MODE_PRIVATE)
            .edit()
            .putString(DailyExportWorker.KEY_EXPORT_TREE, uri.toString())
            .apply()
        Toast.makeText(this, getString(R.string.folder_saved), Toast.LENGTH_SHORT).show()
    }

    private val requestLinkPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { WatchLinkService.start(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reader = HealthConnectReader(this)
        val prefs = getSharedPreferences(DailyExportWorker.PREFS, MODE_PRIVATE)
        WatchLinkService.start(this)
        requestLinkPerms.launch(
            buildList {
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                addAll(BluetoothPerms.needed().toList())
            }.toTypedArray()
        )

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val scope = rememberCoroutineScope()
                    var status by remember { mutableStateOf(initialStatus()) }
                    var resumeTick by remember { mutableStateOf(0) }
                    var uploadUrl by remember {
                        mutableStateOf(prefs.getString(DailyExportWorker.KEY_UPLOAD_URL, "") ?: "")
                    }
                    var secret by remember {
                        mutableStateOf(prefs.getString(DailyExportWorker.KEY_SECRET, "") ?: "")
                    }
                    DisposableEffect(Unit) {
                        val obs = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
                        }
                        lifecycle.addObserver(obs)
                        onDispose { lifecycle.removeObserver(obs) }
                    }
                    LaunchedEffect(resumeTick) {
                        status = withContext(Dispatchers.IO) {
                            DataProbe.run(this@MainActivity, reader).asText()
                        }
                    }
                    LaunchedEffect(Unit) {
                        var seen = WatchDiagStore.lastMs(this@MainActivity)
                        while (true) {
                            delay(1500)
                            val ms = WatchDiagStore.lastMs(this@MainActivity)
                            if (ms > 0L && ms != seen) {
                                seen = ms
                                status = WatchDiagStore.last(this@MainActivity)
                            }
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Health Sync → Drive", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            buildString {
                                append(PhoneIngest.bluetoothLine(this@MainActivity))
                                val hr = WatchSampleStore.at(this@MainActivity.filesDir).lastHeartRate()
                                if (hr != null) {
                                    append(" · пульс с часов ${hr.value.toInt()} bpm")
                                } else {
                                    append(" · пульса с часов ещё нет")
                                }
                                append(". Пульс часов НЕ в Health Connect — только в Health Sync.")
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(stringResource(R.string.intro))
                        Text(
                            stringResource(R.string.tip_samsung),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            stringResource(R.string.tip_watch),
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = uploadUrl,
                            onValueChange = {
                                uploadUrl = it
                                prefs.edit().putString(DailyExportWorker.KEY_UPLOAD_URL, it.trim()).apply()
                            },
                            label = { Text(stringResource(R.string.upload_url_label)) },
                            supportingText = { Text(stringResource(R.string.upload_url_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = secret,
                            onValueChange = {
                                secret = it
                                prefs.edit().putString(DailyExportWorker.KEY_SECRET, it).apply()
                            },
                            label = { Text(stringResource(R.string.secret_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = { requestPermissions.launch(reader.permissions) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.request_permissions)) }

                        Button(
                            onClick = {
                                val opened = HcSettings.openHealthConnect(this@MainActivity)
                                if (!opened) {
                                    status = getString(R.string.hc_no_app)
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.hc_no_app),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.open_health_connect)) }

                        Button(
                            onClick = {
                                if (!HcSettings.openSamsungHealth(this@MainActivity)) {
                                    Toast.makeText(this@MainActivity, "Samsung Health не найден", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.open_samsung_health)) }

                        Button(
                            onClick = { pickFolder.launch(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.choose_folder)) }

                        Button(
                            onClick = {
                                scope.launch {
                                    status = getString(R.string.status_probe)
                                    status = withContext(Dispatchers.IO) {
                                        DataProbe.run(this@MainActivity, reader).asText()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.why_empty)) }

                        Button(
                            onClick = {
                                val log = WatchDiagStore.last(this@MainActivity)
                                if (log.isBlank()) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.watch_log_empty),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Health Sync watch log", log))
                                    status = log
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.watch_log_copied),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.watch_log_copy)) }

                        Button(
                            onClick = {
                                scope.launch {
                                    status = getString(R.string.status_reading)
                                    val sdk = reader.availability()
                                    if (sdk != HealthConnectClient.SDK_AVAILABLE) {
                                        status = healthConnectUnavailableMessage(sdk)
                                        return@launch
                                    }
                                    val end = Instant.now()
                                    val start = end.minus(30, ChronoUnit.DAYS)
                                    status = withContext(Dispatchers.IO) {
                                        val snap = reader.readSince(start, end).withWatchSamples(this@MainActivity)
                                        val fileName = ExportFileNames.zipName()
                                        val out = File(cacheDir, fileName)
                                        val extras = WatchDiagStore.last(this@MainActivity)
                                            .takeIf { it.isNotBlank() }
                                            ?.let { mapOf("watch_diag.txt" to it.toByteArray(Charsets.UTF_8)) }
                                            ?: emptyMap()
                                        JsonExporter.writeZip(snap, out, extras)
                                        var text = snap.summaryLines().joinToString(" · ")
                                        val tree = prefs.getString(DailyExportWorker.KEY_EXPORT_TREE, "") ?: ""
                                        if (tree.isNotBlank()) {
                                            runCatching {
                                                FolderExport.copyZip(this@MainActivity, out, Uri.parse(tree))
                                                text += "\n${getString(R.string.status_folder_copied)}"
                                            }.onFailure {
                                                text += "\n${it.message ?: ""}"
                                            }
                                        }
                                        val url = prefs.getString(DailyExportWorker.KEY_UPLOAD_URL, "") ?: ""
                                        if (url.isBlank()) {
                                            text += "\n${getString(R.string.status_local_zip, out.absolutePath)}"
                                        } else {
                                            val token = prefs.getString(DailyExportWorker.KEY_SECRET, "") ?: ""
                                            val result = DriveUploader(url, token).upload(out, fileName)
                                            result.fold(
                                                onSuccess = { text += "\n${getString(R.string.status_uploaded, it)}" },
                                                onFailure = { text += "\n${getString(R.string.status_upload_error, it.message ?: "")}" }
                                            )
                                        }
                                        text
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.export_now)) }

                        Button(
                            onClick = {
                                DailyExportWorker.schedule(this@MainActivity)
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.daily_scheduled),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.daily_sync)) }

                        Text(status)
                    }
                }
            }
        }
    }

    private fun initialStatus(): String {
        val sdk = if (::reader.isInitialized) reader.availability() else HealthConnectClient.SDK_UNAVAILABLE
        val hc = if (sdk == HealthConnectClient.SDK_AVAILABLE) {
            getString(R.string.status_ready)
        } else {
            healthConnectUnavailableMessage(sdk)
        }
        val watch = watchSampleCount(this)
        return if (watch > 0) {
            "$hc\n${getString(R.string.status_watch_samples, watch)}"
        } else {
            "$hc\n${getString(R.string.status_watch_needed)}"
        }
    }

    private fun healthConnectUnavailableMessage(sdk: Int): String = when (sdk) {
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            getString(R.string.hc_update_required)
        else -> getString(R.string.hc_unavailable)
    }
}
