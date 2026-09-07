package ru.sdvirk.healthsync.drive

import android.accounts.Account
import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ru.sdvirk.healthsync.R
import ru.sdvirk.healthsync.worker.DailyExportWorker
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SignInCancelledException : Exception("Вход отменён")

/**
 * Google Sign-In (Credential Manager) + AuthorizationClient для scope Drive.
 * Повторный [silentAccessToken] без UI — для WorkManager, пока пользователь не отозвал доступ.
 */
class GoogleDriveAuth(
    private val activity: ComponentActivity,
    private val launchAuthorization: (IntentSenderRequest) -> Unit,
) {
    @Volatile
    private var pendingAuth: CancellableContinuation<AuthorizationResult>? = null

    fun onAuthorizationIntentResult(result: ActivityResult) {
        val cont = pendingAuth ?: return
        pendingAuth = null
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            if (cont.isActive) cont.resumeWithException(SignInCancelledException())
            return
        }
        try {
            val auth = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(result.data)
            if (cont.isActive) cont.resume(auth)
        } catch (e: Exception) {
            if (cont.isActive) cont.resumeWithException(mapGmsError(e))
        }
    }

    suspend fun signIn(): String {
        val webClientId = activity.getString(R.string.default_web_client_id).trim()
        var email = if (webClientId.isNotEmpty()) {
            runCatching { signInWithCredentialManager(webClientId) }.getOrElse { err ->
                if (err is GetCredentialCancellationException || err is SignInCancelledException) throw err
                null
            }
        } else {
            null
        }

        val auth = authorizeDrive(email)
        val token = auth.accessToken ?: error("Google не вернул access token")
        email = auth.toGoogleSignInAccount()?.email?.takeIf { it.isNotBlank() }
            ?: email
            ?: accountEmail(activity).takeIf { it.isNotBlank() }

        val account = email?.takeIf { it.isNotBlank() } ?: "Google"
        saveSession(activity, account)

        DriveUploader(
            accessToken = token,
            folderId = DriveConfig.requireConfiguredFolderId(activity),
        ).verifyFolder().getOrElse { err ->
            throw IllegalStateException(
                "Вход: $account. Папка Drive недоступна: ${err.message}. " +
                    "Войди аккаунтом, у которого есть доступ к папке.",
                err
            )
        }
        return account
    }

    suspend fun signOut() {
        runCatching {
            CredentialManager.create(activity)
                .clearCredentialState(ClearCredentialStateRequest())
        }
        saveSession(activity, email = "", authorized = false)
    }

    private suspend fun signInWithCredentialManager(webClientId: String): String {
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val response = CredentialManager.create(activity).getCredential(
            context = activity,
            request = request,
        )
        val cred = response.credential
        val googleId = if (cred is CustomCredential &&
            cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(cred.data)
        } else {
            error("Ожидался Google ID. Проверь Web client ID в oauth.xml.")
        }
        return googleId.id
    }

    private suspend fun authorizeDrive(email: String?): AuthorizationResult {
        val request = authorizationRequest(email)
        val first = try {
            Identity.getAuthorizationClient(activity).authorize(request).awaitGms()
        } catch (e: Exception) {
            throw mapGmsError(e)
        }
        val result = if (first.hasResolution()) {
            val pending = first.pendingIntent
                ?: error("Google запросил подтверждение, но нет PendingIntent")
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { cont ->
                    pendingAuth = cont
                    cont.invokeOnCancellation { pendingAuth = null }
                    launchAuthorization(IntentSenderRequest.Builder(pending).build())
                }
            }
        } else {
            first
        }
        if (result.accessToken.isNullOrBlank()) {
            error("Нет access token. Проверь OAuth client (Android + SHA-1) в Google Cloud Console.")
        }
        return result
    }

    companion object {
        const val KEY_GOOGLE_EMAIL = "google_email"
        const val KEY_GOOGLE_AUTHORIZED = "google_authorized"

        fun isAuthorized(context: Context): Boolean {
            val prefs = prefs(context)
            return prefs.getBoolean(KEY_GOOGLE_AUTHORIZED, false) &&
                !prefs.getString(KEY_GOOGLE_EMAIL, "").isNullOrBlank()
        }

        fun accountEmail(context: Context): String =
            prefs(context).getString(KEY_GOOGLE_EMAIL, "") ?: ""

        suspend fun silentAccessToken(context: Context): String? {
            if (!isAuthorized(context)) return null
            val result = try {
                Identity.getAuthorizationClient(context)
                    .authorize(authorizationRequest(accountEmail(context).ifBlank { null }))
                    .awaitGms()
            } catch (e: Exception) {
                throw mapGmsError(e)
            }
            if (result.hasResolution()) {
                error("Нужно снова нажать «Войти в Google» в приложении")
            }
            return result.accessToken ?: error("Нет access token")
        }

        fun saveSession(context: Context, email: String, authorized: Boolean = email.isNotBlank()) {
            prefs(context).edit()
                .putString(KEY_GOOGLE_EMAIL, email)
                .putBoolean(KEY_GOOGLE_AUTHORIZED, authorized)
                .apply()
        }

        internal fun authorizationRequest(email: String?): AuthorizationRequest {
            val builder = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DriveConfig.DRIVE_SCOPE)))
            if (!email.isNullOrBlank()) {
                builder.setAccount(Account(email, "com.google"))
            }
            return builder.build()
        }

        internal fun mapGmsError(e: Exception): Exception {
            val api = e as? ApiException
            return when (api?.statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR -> IllegalStateException(
                    "OAuth DEVELOPER_ERROR (10): package должен быть ru.sdvirk.healthsync, " +
                        "SHA-1 — из ./gradlew :app:signingReport, оба OAuth client в одном GCP-проекте.",
                    e
                )
                CommonStatusCodes.NETWORK_ERROR -> IllegalStateException("Нет сети для входа в Google", e)
                CommonStatusCodes.CANCELED, 12501 -> SignInCancelledException()
                else -> e
            }
        }

        private fun prefs(context: Context) =
            context.getSharedPreferences(DailyExportWorker.PREFS, Context.MODE_PRIVATE)
    }
}

internal suspend fun <T> Task<T>.awaitGms(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (!cont.isActive) return@addOnCompleteListener
        when {
            task.isCanceled -> cont.cancel()
            task.isSuccessful -> cont.resume(task.result)
            else -> cont.resumeWithException(task.exception ?: RuntimeException("GMS task failed"))
        }
    }
}
