package com.itmda.swiftq.data.remote

import android.content.Context
import androidx.core.content.edit
import com.itmda.swiftq.security.PayloadCipher
import com.itmda.swiftq.security.decryptText
import com.itmda.swiftq.security.encryptText
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/** Supplies the bearer token for API calls. Null means "no token available right now". */
interface AuthTokenProvider {
    suspend fun idToken(forceRefresh: Boolean = false): String?
}

/**
 * Anonymous Firebase sign-in over the Identity Toolkit REST API, so patients never see a login screen.
 * Tokens are kept encrypted. With no web API key configured it returns null and the request goes out
 * unauthenticated (the server answers 401 and the outbox entry simply stays pending).
 */
class FirebaseAnonymousAuth(
    context: Context,
    private val cipher: PayloadCipher,
    private val httpClient: OkHttpClient,
    private val apiKey: String,
    private val authBaseUrl: String,
    private val tokenBaseUrl: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : AuthTokenProvider {
    private val prefs = context.getSharedPreferences("swiftq_auth", Context.MODE_PRIVATE)
    private val lock = Mutex()

    @Serializable
    private data class SignUpResponse(
        val idToken: String,
        val refreshToken: String,
        val expiresIn: String = "3600",
    )

    @Serializable
    private data class RefreshResponse(
        @SerialName("id_token") val idToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: String = "3600",
    )

    override suspend fun idToken(forceRefresh: Boolean): String? = lock.withLock {
        if (apiKey.isBlank()) return null
        val cached = readToken()
        if (!forceRefresh && cached != null && clock() < cached.expiresAtMs - SKEW_MS) return cached.idToken
        val refreshed = cached?.refreshToken?.let { refresh(it) } ?: signUpAnonymously()
        refreshed?.also(::saveToken)?.idToken
    }

    private data class StoredToken(val idToken: String, val refreshToken: String, val expiresAtMs: Long)

    private fun readToken(): StoredToken? {
        val blob = prefs.getString(KEY_TOKEN, null) ?: return null
        return runCatching {
            val parts = cipher.decryptText(blob).split('\n')
            StoredToken(parts[0], parts[1], parts[2].toLong())
        }.getOrNull()
    }

    private fun saveToken(t: StoredToken) {
        prefs.edit { putString(KEY_TOKEN, cipher.encryptText("${t.idToken}\n${t.refreshToken}\n${t.expiresAtMs}")) }
    }

    private fun signUpAnonymously(): StoredToken? = post(
        url = "$authBaseUrl/v1/accounts:signUp?key=$apiKey",
        json = """{"returnSecureToken":true}""",
    ) { text ->
        val r = ApiJson.decodeFromString(SignUpResponse.serializer(), text)
        StoredToken(r.idToken, r.refreshToken, clock() + r.expiresIn.toLong() * 1000)
    }

    private fun refresh(refreshToken: String): StoredToken? = post(
        url = "$tokenBaseUrl/v1/token?key=$apiKey",
        json = """{"grant_type":"refresh_token","refresh_token":"$refreshToken"}""",
    ) { text ->
        val r = ApiJson.decodeFromString(RefreshResponse.serializer(), text)
        StoredToken(r.idToken, r.refreshToken, clock() + r.expiresIn.toLong() * 1000)
    }

    private fun <T> post(url: String, json: String, parse: (String) -> T): T? = try {
        val request = Request.Builder().url(url)
            .post(json.toRequestBody("application/json".toMediaType())).build()
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) parse(response.body.string()) else null
        }
    } catch (e: IOException) {
        null
    } catch (e: kotlinx.serialization.SerializationException) {
        null
    }

    private companion object {
        const val KEY_TOKEN = "firebase_token_enc"
        const val SKEW_MS = 60_000L
    }
}

/** Adds `Authorization: Bearer <idToken>`. Only the header is touched: the body and Idempotency-Key are never rebuilt here. */
class AuthInterceptor(private val tokens: AuthTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokens.idToken() }
        val request = if (token == null) chain.request() else chain.request().newBuilder()
            .header("Authorization", "Bearer $token").build()
        return chain.proceed(request)
    }
}

/**
 * On a 401 refresh the token once and replay the same request. OkHttp reuses the original request object,
 * so the Idempotency-Key header and the body bytes are identical. A second 401 is returned as-is
 * and the outbox entry stays pending.
 */
class TokenRefreshAuthenticator(private val tokens: AuthTokenProvider) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val fresh = runBlocking { tokens.idToken(forceRefresh = true) } ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
    }
}
