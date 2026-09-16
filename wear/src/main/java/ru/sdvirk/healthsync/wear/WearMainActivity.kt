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

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear)
        status = findViewById(R.id.status)

        findViewById<Button>(R.id.permissions).setOnClickListener {
            requestPermissions.launch(neededPermissions())
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
                        status.text = "Фоновый пульс выключен"
                    } else {
                        val types = WatchHealth.registerPassive(this@WearMainActivity)
                        status.text = "Фон включён (${types.size} типов)"
                    }
                } catch (e: Exception) {
                    status.text = e.message ?: e.javaClass.simpleName
                }
                delay(800)
                refreshStatus()
            }
        }
        findViewById<Button>(R.id.measure).setOnClickListener { startMeasure() }
        findViewById<Button>(R.id.sync).setOnClickListener {
            lifecycleScope.launch {
                status.text = "Отправляю на телефон…"
                try {
                    val n = withContext(Dispatchers.IO) {
                        WatchPhoneSync.syncNow(this@WearMainActivity)
                    }
                    status.text = if (n == 0) "Нечего слать" else "Отправлено проб: $n"
                } catch (e: Exception) {
                    status.text = e.message ?: e.javaClass.simpleName
                }
                delay(1200)
                refreshStatus()
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
        val last = WatchHealth.store(this).lastHeartRate()
        val bpm = last?.let { "${it.value.toInt()} bpm" } ?: "нет"
        val n = WatchHealth.store(this).count()
        val sensors = if (hasBodySensors()) "датчики OK" else "нет датчиков"
        val passive = if (WatchHealth.isPassiveEnabled(this)) "фон вкл" else "фон выкл"
        status.text = "Пульс: $bpm\nПроб: $n · $passive\n$sensors"
    }

    private fun startMeasure() {
        if (!hasBodySensors()) {
            status.text = "Сначала выдай разрешение на датчики"
            requestPermissions.launch(neededPermissions())
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

    private fun neededPermissions(): Array<String> = buildList {
        add(Manifest.permission.BODY_SENSORS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.BODY_SENSORS_BACKGROUND)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
    }.toTypedArray()

    private fun hasBodySensors(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED
}
