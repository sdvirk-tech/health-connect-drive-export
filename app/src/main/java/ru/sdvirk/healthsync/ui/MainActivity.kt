package ru.sdvirk.healthsync.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.sdvirk.healthsync.R
import ru.sdvirk.healthsync.drive.DriveUploader
import ru.sdvirk.healthsync.export.ExportFileNames
import ru.sdvirk.healthsync.export.JsonExporter
import ru.sdvirk.healthsync.health.HealthConnectReader
import ru.sdvirk.healthsync.worker.DailyExportWorker
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reader = HealthConnectReader(this)
        val prefs = getSharedPreferences(DailyExportWorker.PREFS, MODE_PRIVATE)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val scope = rememberCoroutineScope()
                    var status by remember { mutableStateOf(initialStatus()) }
                    var uploadUrl by remember {
                        mutableStateOf(prefs.getString(DailyExportWorker.KEY_UPLOAD_URL, "") ?: "")
                    }
                    var secret by remember {
                        mutableStateOf(prefs.getString(DailyExportWorker.KEY_SECRET, "") ?: "")
                    }

                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Health Sync → Drive", style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.intro))
                        Text(
                            stringResource(R.string.tip_samsung),
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
                                scope.launch {
                                    status = getString(R.string.status_reading)
                                    val sdk = reader.availability()
                                    if (sdk != HealthConnectClient.SDK_AVAILABLE) {
                                        status = healthConnectUnavailableMessage(sdk)
                                        return@launch
                                    }
                                    val end = Instant.now()
                                    val start = end.minus(7, ChronoUnit.DAYS)
                                    val snap = reader.readSince(start, end)
                                    val fileName = ExportFileNames.zipName()
                                    val out = File(cacheDir, fileName)
                                    JsonExporter.writeZip(snap, out)
                                    status = snap.summaryLines().joinToString(" · ")
                                    val url = prefs.getString(DailyExportWorker.KEY_UPLOAD_URL, "") ?: ""
                                    if (url.isBlank()) {
                                        status += "\n${getString(R.string.status_local_zip, out.absolutePath)}"
                                    } else {
                                        val token = prefs.getString(DailyExportWorker.KEY_SECRET, "") ?: ""
                                        val result = withContext(Dispatchers.IO) {
                                            DriveUploader(url, token).upload(out, fileName)
                                        }
                                        result.fold(
                                            onSuccess = { status += "\n${getString(R.string.status_uploaded, it)}" },
                                            onFailure = { status += "\n${getString(R.string.status_upload_error, it.message ?: "")}" }
                                        )
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
        return if (sdk == HealthConnectClient.SDK_AVAILABLE) {
            getString(R.string.status_ready)
        } else {
            healthConnectUnavailableMessage(sdk)
        }
    }

    private fun healthConnectUnavailableMessage(sdk: Int): String = when (sdk) {
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            getString(R.string.hc_update_required)
        else -> getString(R.string.hc_unavailable)
    }
}
