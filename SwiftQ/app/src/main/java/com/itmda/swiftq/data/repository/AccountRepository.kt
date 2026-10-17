package com.itmda.swiftq.data.repository

import com.itmda.swiftq.data.local.TicketDao
import com.itmda.swiftq.data.remote.ApiJson
import com.itmda.swiftq.data.remote.ApiResult
import com.itmda.swiftq.data.remote.PatientAuthApi
import com.itmda.swiftq.data.remote.PatientAuthDto
import com.itmda.swiftq.data.remote.PatientLoginRequest
import com.itmda.swiftq.data.remote.PatientProfileDto
import com.itmda.swiftq.data.remote.PatientRegisterRequest
import com.itmda.swiftq.data.remote.PatientSession
import com.itmda.swiftq.data.remote.PatientSessionStore
import com.itmda.swiftq.data.remote.SwiftQApi
import com.itmda.swiftq.data.remote.safeApiCall
import com.itmda.swiftq.data.remote.toSession
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** What the registration steps collected. [password] is kept exactly as typed. */
data class RegistrationInput(
    val email: String,
    val password: String,
    val displayName: String,
    val language: String,
    val channel: String,
    val phone: String,
    val priority: String,
)

sealed interface AccountResult {
    data class Success(val profile: PatientProfileDto) : AccountResult
    /** EMAIL_IN_USE: offer sign in instead. */
    data class EmailInUse(val message: String) : AccountResult
    /** 401 on login: "Incorrect email or password." */
    data class WrongCredentials(val message: String) : AccountResult
    /** 403 on login: a staff email, which has no patient profile. */
    data class StaffAccount(val message: String) : AccountResult
    /** 422: a rule failed on the server. The message is shown as it is. */
    data class Invalid(val message: String) : AccountResult
    data class RateLimited(val retryAfterSeconds: Int, val message: String) : AccountResult
    /** A ticket taken anonymously is still open; signing in would hide it (it belongs to the anonymous user). */
    data object OpenTicket : AccountResult
    data object Offline : AccountResult
    data class Failed(val message: String?) : AccountResult
}

/**
 * Register, sign in and sign out for patient accounts. Anonymous use is untouched: without a session the app keeps
 * using the anonymous Firebase token. [beforeIdentityChange] / [afterIdentityChange] move the push token between users.
 */
class AccountRepository(
    private val authApi: PatientAuthApi,
    private val api: SwiftQApi,
    private val sessionStore: PatientSessionStore,
    private val tickets: TicketDao,
    private val beforeIdentityChange: suspend () -> Unit = {},
    private val afterIdentityChange: suspend () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val session: StateFlow<PatientSession?> = sessionStore.session

    /** The signed-in patient's saved details for the join form, or null when anonymous. */
    fun accountDefaults(): AccountDefaults? = sessionStore.read()?.profile?.let(AccountDefaults::from)

    suspend fun register(input: RegistrationInput): AccountResult {
        if (tickets.findActive() != null) return AccountResult.OpenTicket
        val needsPhone = NotificationChoice.needsPhone(input.channel)
        val body = PatientRegisterRequest(
            email = JoinValidation.normaliseEmail(input.email) ?: input.email.trim(),
            password = input.password,
            displayName = input.displayName.trim(),
            language = input.language,
            notificationChannel = input.channel,
            phone = if (needsPhone) JoinValidation.normalisePhone(input.phone) ?: input.phone.trim() else null,
            priorityRequest = input.priority.takeIf { it != PriorityChoice.NONE },
        )
        val json = ApiJson.encodeToString(PatientRegisterRequest.serializer(), body)
        return signInWith { authApi.register(json.toRequestBody(JSON)) }
    }

    suspend fun login(email: String, password: String): AccountResult {
        if (tickets.findActive() != null) return AccountResult.OpenTicket
        val body = PatientLoginRequest(JoinValidation.normaliseEmail(email) ?: email.trim(), password)
        val json = ApiJson.encodeToString(PatientLoginRequest.serializer(), body)
        return signInWith { authApi.login(json.toRequestBody(JSON)) }
    }

    /** Stops alerts on this device first (that call still needs the patient token), then forgets the session. */
    suspend fun signOut() {
        beforeIdentityChange()
        sessionStore.clear()
        afterIdentityChange()
    }

    /** Re-reads the profile (staff may have confirmed a priority). Keeps the cached copy when offline. */
    suspend fun refreshProfile(): PatientProfileDto? {
        val current = sessionStore.read() ?: return null
        val result = safeApiCall({ ApiJson.decodeFromString(PatientProfileDto.serializer(), it) }) { api.getProfile() }
        if (result is ApiResult.Success) sessionStore.read()?.let { sessionStore.save(it.copy(profile = result.value)) }
        return sessionStore.read()?.profile ?: current.profile
    }

    private suspend fun signInWith(call: suspend () -> retrofit2.Response<okhttp3.ResponseBody>): AccountResult {
        val result = safeApiCall({ ApiJson.decodeFromString(PatientAuthDto.serializer(), it) }, call)
        return when (result) {
            is ApiResult.Success -> {
                beforeIdentityChange()
                sessionStore.save(result.value.toSession(clock()))
                afterIdentityChange()
                AccountResult.Success(result.value.patient)
            }
            is ApiResult.Failure -> failure(result)
            is ApiResult.NetworkError -> AccountResult.Offline
        }
    }

    private fun failure(f: ApiResult.Failure): AccountResult {
        val message = f.error?.message
        return when {
            f.error?.code == "EMAIL_IN_USE" -> AccountResult.EmailInUse(message.orEmpty())
            f.httpStatus == 429 || f.error?.code == "RATE_LIMITED" ->
                AccountResult.RateLimited(f.retryAfterSeconds ?: DEFAULT_RETRY_S, message.orEmpty())
            f.httpStatus == 401 -> AccountResult.WrongCredentials(message.orEmpty())
            f.httpStatus == 403 -> AccountResult.StaffAccount(message.orEmpty())
            f.httpStatus == 422 || f.httpStatus == 400 -> AccountResult.Invalid(message.orEmpty())
            else -> AccountResult.Failed(message)
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        const val DEFAULT_RETRY_S = 60
    }
}
