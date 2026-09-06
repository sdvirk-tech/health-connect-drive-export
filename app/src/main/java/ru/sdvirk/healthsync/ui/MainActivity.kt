package ru.sdvirk.healthsync.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.sdvirk.healthsync.drive.DriveUploader
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
            if (granted.containsAll(reader.permissions)) "Разрешения OK" else "Часть разрешений отклонена",
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
                    var status by remember { mutableStateOf("Готов") }
                    var uploadUrl by remember {
                        mutableStateOf(prefs.getString(DailyExportWorker.KEY_UPLOAD_URL, "") ?: "")
                    }

                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Health Sync → Drive", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Samsung Health → Health Connect → zip в твою Drive-папку. " +
                                "Без логина в Samsung в приложении."
                        )
                        OutlinedTextField(
                            value = uploadUrl,
                            onValueChange = {
                                uploadUrl = it
                                prefs.edit().putString(DailyExportWorker.KEY_UPLOAD_URL, it).apply()
                            },
                            label = { Text("Upload URL (Apps Script / backend)") },
                            modifier = Modifier
                        )
                        Button(onClick = {
                            requestPermissions.launch(reader.permissions)
                        }) { Text("Запросить разрешения Health Connect") }

                        Button(onClick = {
                            scope.launch {
                                status = "Читаю…"
                                val end = Instant.now()
                                val start = end.minus(7, ChronoUnit.DAYS)
                                val snap = reader.readSince(start, end)
                                val out = File(cacheDir, "health_connect_export.zip")
                                JsonExporter.writeZip(snap, out)
                                status = snap.summaryLines().joinToString(" · ")
                                val url = prefs.getString(DailyExportWorker.KEY_UPLOAD_URL, "") ?: ""
                                if (url.isBlank()) {
                                    status += "\nZip локально: ${out.absolutePath} (URL не задан)"
                                } else {
                                    DriveUploader(url).upload(out).fold(
                                        onSuccess = { status += "\nЗагружено: $it" },
                                        onFailure = { status += "\nОшибка загрузки: ${it.message}" }
                                    )
                                }
                            }
                        }) { Text("Выгрузить сейчас") }

                        Button(onClick = {
                            DailyExportWorker.schedule(this@MainActivity)
                            Toast.makeText(this@MainActivity, "Расписание раз в сутки", Toast.LENGTH_SHORT).show()
                        }) { Text("Включить автораз в сутки") }

                        Text(status)
                    }
                }
            }
        }
    }
}
