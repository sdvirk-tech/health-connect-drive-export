package ru.sdvirk.healthsync.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import androidx.health.connect.client.PermissionController
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.sdvirk.healthsync.watch.WatchSample
import java.util.concurrent.atomic.AtomicInteger

class WearMainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private var measureJob: Job? = null
    private var measureCallback: MeasureCallback? = null

    private val requestSensorPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatus() }

    private val requestHcPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        status.text = "HC выдано: ${granted.size}/${WatchHealthConnect.permissions.size}"
        lifecycleScope.launch {
            delay(600)
            refreshStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear)
        status = findViewById(R.id.status)

        findViewById<Button>(R.id.permissions).setOnClickListener {
            requestSensorPermissions.launch(neededSensorPermissions())
        }
        findViewById<Button>(R.id.hc).setOnClickListener {
            lifecycleScope.launch {
                if (WatchHealthConnect.client(this@WearMainActivity) == null) {
                    status.text = WatchHealthConnect.hcUnavailable(WatchHealthConnect.availability(this@WearMainActivity))
                    return@launch
                }
                status.text = "Читаю Health Connect на часах…"
                try {
                    val granted = WatchHealthConnect.client(this@WearMainActivity)
                        ?.permissionController?.getGrantedPermissions().orEmpty()
                    if (!granted.containsAll(WatchHealthConnect.permissions)) {
                        requestHcPermissions.launch(WatchHealthConnect.permissions)
                        return@launch
                    }
                    val (samples, note) = withContext(Dispatchers.IO) {
                        WatchHealthConnect.pull(this@WearMainActivity)
                    }
                    WatchHealth.store(this@WearMainActivity).append(samples)
                    status.text = note
                    if (samples.isNotEmpty()) WatchPhoneSync.enqueue(this@WearMainActivity)
                } catch (e: Exception) {
                    status.text = e.message ?: e.javaClass.simpleName
                }
            }
        }
        findViewById<Button>(R.id.passive).setOnClickListener {
            lifecycleScope.launch {
                try {
                    if (!hasBodySensors()) {
                        status.text = "Сначала выдай разрешение на датчики"
                        return@launch
                    }
                    if (WatchHealth.isPassiveEnabled(this@WearMainActivity)) {
                        WatchHealth.unregisterPassive(this@WearMainActivity)
                        status.text = "Фон выключен"
                    } else {
                        val types = WatchHealth.registerPassive(this@WearMainActivity)
                        status.text = "Фон включён (${types.size} типов HS)"
                    }
                } catch (e: Exception) {
                    status.text = e.message ?: e.javaClass.simpleName
                }
                delay(800)
                refreshStatus()
            }
        }
        findViewById<Button>(R.id.measure).setOnClickListener { startMeasure() }
        findViewById<Button>(R.id.diagnose).setOnClickListener {
            lifecycleScope.launch { status.text = diagnose() }
        }
        findViewById<Button>(R.id.sync).setOnClickListener {
            lifecycleScope.launch {
                status.text = "Отправляю на телефон…"
                try {
                    val n = withContext(Dispatchers.IO) {
                        WatchPhoneSync.syncNow(this@WearMainActivity)
                    }
                    status.text = if (n == 0) "Нечего слать — сначала замер / HC / фон" else "Отправлено проб: $n"
                } catch (e: Exception) {
                    status.text = e.message ?: e.javaClass.simpleName
                }
            }
        }
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        stopMeasure()
        super.onDestroy()
    }

    private fun refreshStatus() {
        val counts = WatchHealth.store(this).countsByType()
        fun n(type: String) = counts[type] ?: 0
        val sensors = if (hasBodySensors()) "датчики OK" else "нет датчиков"
        val passive = if (WatchHealth.isPassiveEnabled(this)) "фон вкл" else "фон выкл"
        val lastHr = WatchHealth.store(this).lastHeartRate()?.value?.toInt()?.let { "$it bpm" } ?: "нет"
        status.text = buildString {
            append("Пульс: $lastHr · $passive\n")
            append("HS/HC проб: HR ${n(WatchSample.HEART_RATE)}")
            append(" HRV ${n(WatchSample.HRV)}")
            append(" SpO2 ${n(WatchSample.SPO2)}")
            append(" сон ${n(WatchSample.SLEEP)}")
            append(" BP ${n(WatchSample.BLOOD_PRESSURE)}")
            append(" ECG ${n(WatchSample.ECG)}\n")
            append(sensors)
        }
    }

    private suspend fun diagnose(): String {
        val counts = WatchHealth.store(this).countsByType()
        val hs = WatchHealth.capabilitiesReport(this)
        val hcSdk = WatchHealthConnect.availability(this)
        val hcClient = WatchHealthConnect.client(this)
        val hcGranted = hcClient?.permissionController?.getGrantedPermissions()?.size ?: 0
        return buildString {
            appendLine(hs)
            val hcLine = if (hcSdk == SDK_AVAILABLE) {
                "Health Connect: есть, разрешений $hcGranted/${WatchHealthConnect.permissions.size}"
            } else {
                "Health Connect: " + WatchHealthConnect.hcUnavailable(hcSdk)
            }
            appendLine(hcLine)
            appendLine("Локально: $counts")
            appendLine("Пульс: датчик часов (Health Services). Надень часы, «Замерить пульс» или фон.")
            appendLine("HRV/сон/SpO2/давление: Samsung Health должен ПИСАТЬ в Health Connect, затем «HC: сон/SpO2/BP/HRV».")
            appendLine("ЭКГ: Samsung Health Monitor, в Health Connect обычно нет. Health Services ЭКГ не отдаёт.")
            appendLine("Потом «На телефон» — пробы уходят в Health Sync на телефоне и в zip.")
        }
    }

    private fun startMeasure() {
        if (!hasBodySensors()) {
            status.text = "Сначала выдай разрешение на датчики"
            requestSensorPermissions.launch(neededSensorPermissions())
            return
        }
        stopMeasure()
        status.text = "Измеряю пульс…"
        val received = AtomicInteger(0)
        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability,
            ) {
                if (availability is DataTypeAvailability &&
                    availability != DataTypeAvailability.AVAILABLE
                ) {
                    runOnUiThread {
                        status.text = "Датчик: $availability. Надень часы плотнее."
                    }
                }
            }

            override fun onDataReceived(data: DataPointContainer) {
                val samples = WatchHealth.toSamples(data)
                if (samples.isEmpty()) return
                WatchHealth.store(this@WearMainActivity).append(samples)
                val hr = samples.lastOrNull { it.type == WatchSample.HEART_RATE }
                val n = received.incrementAndGet()
                runOnUiThread {
                    status.text = if (hr != null) {
                        "Пульс: ${hr.value.toInt()} bpm ($n)"
                    } else {
                        "Есть данные ($n)"
                    }
                }
                if (n >= 5) {
                    runOnUiThread { stopMeasure() }
                    WatchPhoneSync.enqueue(this@WearMainActivity)
                }
            }
        }
        measureCallback = callback
        val client = HealthServices.getClient(this).measureClient
        client.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        measureJob = lifecycleScope.launch {
            delay(25_000)
            stopMeasure()
            WatchPhoneSync.enqueue(this@WearMainActivity)
            refreshStatus()
        }
    }

    private fun stopMeasure() {
        measureJob?.cancel()
        measureJob = null
        val callback = measureCallback ?: return
        measureCallback = null
        lifecycleScope.launch {
            runCatching {
                HealthServices.getClient(this@WearMainActivity).measureClient
                    .unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
                    .await()
            }
        }
    }

    private fun neededSensorPermissions(): Array<String> = buildList {
        add(Manifest.permission.BODY_SENSORS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.BODY_SENSORS_BACKGROUND)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
    }.toTypedArray()

    private fun hasBodySensors(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED
}
