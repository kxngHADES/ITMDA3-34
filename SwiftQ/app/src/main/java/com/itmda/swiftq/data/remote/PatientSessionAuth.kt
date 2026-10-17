package com.itmda.swiftq.data.remote

import android.content.Context
import androidx.core.content.edit
import com.itmda.swiftq.security.PayloadCipher
import com.itmda.swiftq.security.decryptText
import com.itmda.swiftq.security.encryptText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** A signed-in patient: the SwiftQ access token (not a Firebase token), when it runs out, and the cached profile. */
@Serializable
data class PatientSession(
    val accessToken: String,
    val expiresAtMs: Long,
    val profile: PatientProfileDto,
)

/** Where the session lives. Reads are cheap and non-suspending because the interceptor asks on every call. */
interface PatientSessionStore {
    val session: StateFlow<PatientSession?>
    fun read(): PatientSession? = session.value
    fun save(session: PatientSession)
    fun clear()
}

/** Encrypted shared preferences, the same way [FirebaseAnonymousAuth] keeps its token. */
class PrefsPatientSessionStore(context: Context, private val cipher: PayloadCipher) : PatientSessionStore {
    private val prefs = context.getSharedPreferences("swiftq_patient_session", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    override val session: StateFlow<PatientSession?> = state.asStateFlow()

    private fun load(): PatientSession? {
        val blob = prefs.getString(KEY, null) ?: return null
        return runCatching { ApiJson.decodeFromString(PatientSession.serializer(), cipher.decryptText(blob)) }.getOrNull()
    }

    override fun save(session: PatientSession) {
        prefs.edit { putString(KEY, cipher.encryptText(ApiJson.encodeToString(PatientSession.serializer(), session))) }
        state.value = session
    }

    override fun clear() {
        prefs.edit { remove(KEY) }
        state.value = null
    }

    private companion object { const val KEY = "session_enc" }
}

fun PatientAuthDto.toSession(now: Long) = PatientSession(accessToken, now + expiresIn * 1000, patient)

/**
 * Returns the patient token while a session exists and otherwise falls back to the anonymous one, so a patient who
 * never signs up notices nothing. Refreshes a day before expiry; a 401/403 on refresh ends the session.
 */
class PatientSessionAuth(
    private val session: PatientSessionStore,
    private val authApi: PatientAuthApi,
    private val anonymous: AuthTokenProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) : AuthTokenProvider {
    private val lock = Mutex()

    override suspend fun idToken(forceRefresh: Boolean): String? {
        if (session.read() == null) return anonymous.idToken(forceRefresh)
        return lock.withLock {
            val s = session.read() ?: return@withLock null
            val fresh = !forceRefresh && clock() < s.expiresAtMs - REFRESH_EARLY_MS
            if (fresh) s.accessToken else refresh(s)
        }
    }

    private suspend fun refresh(s: PatientSession): String? {
        val result = safeApiCall({ ApiJson.decodeFromString(PatientAuthDto.serializer(), it) }) { authApi.refresh("Bearer ${s.accessToken}") }
        return when (result) {
            is ApiResult.Success -> result.value.toSession(clock()).also(session::save).accessToken
            // The session is over: the patient has to sign in again.
            is ApiResult.Failure -> if (result.httpStatus == 401 || result.httpStatus == 403) { session.clear(); null } else stillValid(s)
            // No answer: keep using the old token while it lasts, and try again on the next call.
            is ApiResult.NetworkError -> stillValid(s)
        }
    }

    private fun stillValid(s: PatientSession) = s.accessToken.takeIf { clock() < s.expiresAtMs }

    private companion object { const val REFRESH_EARLY_MS = 24 * 60 * 60 * 1000L }
}
