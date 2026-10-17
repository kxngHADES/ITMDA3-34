package com.itmda.swiftq.data

import com.itmda.swiftq.data.remote.ApiFactory
import com.itmda.swiftq.data.remote.ApiJson
import com.itmda.swiftq.data.remote.AuthTokenProvider
import com.itmda.swiftq.data.remote.PatientAuthApi
import com.itmda.swiftq.data.remote.PatientProfileDto
import com.itmda.swiftq.data.remote.PatientSession
import com.itmda.swiftq.data.remote.PatientSessionAuth
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PatientSessionAuthTest {
    private lateinit var server: MockWebServer
    private lateinit var authApi: PatientAuthApi
    private var now = 10_000_000_000L
    private val day = 24 * 60 * 60 * 1000L
    private val profile = ApiJson.decodeFromString(PatientProfileDto.serializer(), PROFILE_JSON)
    private val anonymous = object : AuthTokenProvider {
        var calls = 0
        override suspend fun idToken(forceRefresh: Boolean): String { calls++; return "anon" }
    }

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        authApi = PatientAuthApi.create(server.url("/").toString(), ApiFactory.httpClient(null))
    }

    @After fun tearDown() { server.shutdown() }

    private fun auth(session: PatientSession?) = InMemorySessionStore(session).let { it to PatientSessionAuth(it, authApi, anonymous) { now } }

    @Test fun withoutASessionTheAnonymousTokenIsUsed() = runBlocking {
        val (_, auth) = auth(null)
        assertEquals("anon", auth.idToken())
        assertEquals(1, anonymous.calls)
        assertEquals(0, server.requestCount)
    }

    @Test fun aFreshPatientTokenIsUsedAsItIs() = runBlocking {
        val (_, auth) = auth(PatientSession("tok_old", now + 3 * day, profile))
        assertEquals("tok_old", auth.idToken())
        assertEquals(0, server.requestCount)
        assertEquals(0, anonymous.calls)
    }

    @Test fun refreshesADayBeforeExpiry() = runBlocking {
        server.enqueue(MockResponse().setBody(AUTH_JSON))
        val (store, auth) = auth(PatientSession("tok_old", now + day - 1, profile))
        assertEquals("tok_patient", auth.idToken())
        val req = server.takeRequest()
        assertEquals("/v1/auth/patients/refresh", req.path)
        assertEquals("Bearer tok_old", req.getHeader("Authorization"))
        assertEquals(now + 604800 * 1000L, store.read()!!.expiresAtMs)
    }

    @Test fun aRefusedRefreshEndsTheSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody(envelope("UNAUTHENTICATED", "Session expired.")))
        val (store, auth) = auth(PatientSession("tok_old", now + 1_000, profile))
        assertNull(auth.idToken())
        assertNull(store.read())
    }

    @Test fun offlineKeepsTheOldTokenWhileItIsStillValid() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val (store, auth) = auth(PatientSession("tok_old", now + 1_000, profile))
        assertEquals("tok_old", auth.idToken())
        assertEquals("tok_old", store.read()?.accessToken)
    }
}
