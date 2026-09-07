package ru.sdvirk.healthsync.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.launch
import ru.sdvirk.healthsync.R
import ru.sdvirk.healthsync.drive.GoogleDriveAuth
import ru.sdvirk.healthsync.drive.SignInCancelledException
import ru.sdvirk.healthsync.export.HealthExport
import ru.sdvirk.healthsync.health.HealthConnectReader
import ru.sdvirk.healthsync.worker.DailyExportWorker

class MainActivity : ComponentActivity() {

    private lateinit var reader: HealthConnectReader
    private lateinit var googleAuth: GoogleDriveAuth

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

    private val requestDriveAuth = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (::googleAuth.isInitialized) {
            googleAuth.onAuthorizationIntentResult(result)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reader = HealthConnectReader(this)
        googleAuth = GoogleDriveAuth(this) { sender ->
            requestDriveAuth.launch(sender)
        }
        val prefs = getSharedPreferences(DailyExportWorker.PREFS, MODE_PRIVATE)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val scope = rememberCoroutineScope()
                    var status by remember { mutableStateOf(initialStatus()) }
                    var accountEmail by remember {
                        mutableStateOf(GoogleDriveAuth.accountEmail(this@MainActivity))
                    }
                    var signingIn by remember { mutableStateOf(false) }
                    var uploadUrl by remember {
                        mutableStateOf(prefs.getString(DailyExportWorker.KEY_UPLOAD_URL, "") ?: "")
                    }
                    var secret by remember {
                        mutableStateOf(prefs.getString(DailyExportWorker.KEY_SECRET, "") ?: "")
                    }
                    var showAppsScript by remember {
                        mutableStateOf(uploadUrl.isNotBlank())
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

                        if (accountEmail.isBlank()) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        signingIn = true
                                        status = getString(R.string.status_google_signing_in)
                                        try {
                                            val email = googleAuth.signIn()
                                            accountEmail = email
                                            status = getString(R.string.status_google_signed_in, email)
                                        } catch (e: SignInCancelledException) {
                                            status = getString(R.string.status_google_cancelled)
                                        } catch (e: Exception) {
                                            status = getString(
                                                R.string.status_google_error,
                                                e.message ?: e.javaClass.simpleName
                                            )
                                        } finally {
                                            accountEmail = GoogleDriveAuth.accountEmail(this@MainActivity)
                                            signingIn = false
                                        }
                                    }
                                },
                                enabled = !signingIn,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.google_sign_in)) }
                        } else {
                            Text(
                                stringResource(R.string.google_account, accountEmail),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        googleAuth.signOut()
                                        accountEmail = ""
                                        status = getString(R.string.status_google_signed_out)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.google_sign_out)) }
                        }

                        Button(
                            onClick = { requestPermissions.launch(reader.permissions) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.request_permissions)) }

                        Button(
                            onClick = {
                                scope.launch {
                                    status = getString(R.string.status_reading)
                                    try {
                                        val outcome = HealthExport(this@MainActivity).run()
                                        status = outcome.summary
                                        val upload = outcome.upload
                                        status += when {
                                            upload == null ->
                                                "\n${getString(R.string.status_local_zip, outcome.zipFile.absolutePath)}"
                                            upload.isSuccess ->
                                                "\n${getString(R.string.status_uploaded, upload.getOrNull().orEmpty())}"
                                            else ->
                                                "\n${getString(R.string.status_upload_error, upload.exceptionOrNull()?.message ?: "")}"
                                        }
                                    } catch (e: Exception) {
                                        status = e.message ?: e.javaClass.simpleName
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.export_now)) }

                        Button(
                            onClick = {
                                val signedIn = GoogleDriveAuth.isAuthorized(this@MainActivity)
                                val url = prefs.getString(DailyExportWorker.KEY_UPLOAD_URL, "") ?: ""
                                if (!signedIn && url.isBlank()) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.daily_needs_google),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@Button
                                }
                                DailyExportWorker.schedule(this@MainActivity)
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.daily_scheduled),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.daily_sync)) }

                        TextButton(
                            onClick = { showAppsScript = !showAppsScript },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(
                                    if (showAppsScript) {
                                        R.string.apps_script_hide
                                    } else {
                                        R.string.apps_script_show
                                    }
                                )
                            )
                        }
                        if (showAppsScript) {
                            Text(
                                stringResource(R.string.apps_script_fallback_note),
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
                        }

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
        val email = GoogleDriveAuth.accountEmail(this)
        return if (email.isBlank()) {
            "$hc\n${getString(R.string.status_google_needed)}"
        } else {
            "$hc\n${getString(R.string.google_account, email)}"
        }
    }

    private fun healthConnectUnavailableMessage(sdk: Int): String = when (sdk) {
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            getString(R.string.hc_update_required)
        else -> getString(R.string.hc_unavailable)
    }
}
