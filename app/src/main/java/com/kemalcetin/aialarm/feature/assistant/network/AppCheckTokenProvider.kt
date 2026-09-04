package com.kemalcetin.aialarm.feature.assistant.network

import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Supplies a Firebase App Check token to attach to AI-proxy requests as the
 * `x-firebase-app-check` header.
 *
 * A null result means App Check is unavailable/not configured (e.g. local dev
 * without google-services.json, or Play Integrity failing on an emulator). In
 * that case the request is sent without the header; the backend rejects it when
 * enforcement is on and the app gracefully falls back to the offline alarm
 * workflow. We NEVER fabricate a token and never put a secret in the APK.
 */
fun interface AppCheckTokenProvider {
    suspend fun getToken(): String?
}

/**
 * Real provider backed by Firebase App Check (Play Integrity provider).
 *
 * Safe to construct and call even when Firebase is not configured: it returns
 * null instead of throwing, so the app never crashes and AI simply degrades.
 * The [appCheckProvider] lambda is injected so tests can substitute a stub.
 */
class FirebaseAppCheckTokenProvider(
    private val appCheckProvider: () -> FirebaseAppCheck? = {
        runCatching { FirebaseAppCheck.getInstance() }.getOrNull()
    }
) : AppCheckTokenProvider {

    override suspend fun getToken(): String? = withContext(Dispatchers.IO) {
        try {
            val appCheck = appCheckProvider() ?: return@withContext null
            Tasks.await(appCheck.getAppCheckToken(/* forceRefresh = */ false)).token
        } catch (e: Exception) {
            // Any Firebase failure (not configured, no provider, Play Integrity
            // unavailable) means "no token" — the caller degrades gracefully.
            null
        }
    }
}
