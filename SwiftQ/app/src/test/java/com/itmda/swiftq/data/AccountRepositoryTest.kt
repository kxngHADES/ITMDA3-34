package com.itmda.swiftq.data

import com.itmda.swiftq.FakeStore
import com.itmda.swiftq.data.local.TicketEntity
import com.itmda.swiftq.data.remote.ApiFactory
import com.itmda.swiftq.data.remote.ApiJson
import com.itmda.swiftq.data.remote.PatientAuthApi
import com.itmda.swiftq.data.remote.PatientSession
import com.itmda.swiftq.data.remote.PatientSessionStore
import com.itmda.swiftq.data.repository.AccountRepository
import com.itmda.swiftq.data.repository.AccountResult
import com.itmda.swiftq.data.repository.RegistrationInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InMemorySessionStore(initial: PatientSession? = null) : PatientSessionStore {
    private val state = MutableStateFlow(initial)
    override val session: StateFlow<PatientSession?> = state
    var saves = 0
    override fun save(session: PatientSession) { saves++; state.value = session }
    override fun clear() { state.value = null }
}

const val PROFILE_JSON = """{"id":"uid_1","email":"thandi@example.com","display_name":"Thandi","language":"zu","notification_channel":"sms","phone_last4":"4567","priority":{"category":"pregnant","verified":false,"verified_at":null,"valid_until":null,"active":false},"created_at":"2026-10-17T08:12:44Z","updated_at":"2026-10-17T08:12:44Z"}"""
const val AUTH_JSON = """{"access_token":"tok_patient","token_type":"Bearer","expires_in":604800,"patient":$PROFILE_JSON}"""

fun envelope(code: String, message: String, retryAfter: Int? = null) =
    """{"error":{"code":"$code","message":"$message","retryable":false,"retry_after_seconds":${retryAfter ?: "null"},"request_id":"req_1"}}"""

class AccountRepositoryTest {
    private lateinit var server: MockWebServer
    private val store = FakeStore()
    private val session = InMemorySessionStore()
    private val events = mutableListOf<String>()
    private lateinit var repo: AccountRepository
    private val now = 1_000_000L

    private val input = RegistrationInput(
        email = " Thandi@Example.com ",
        password = " correct horse battery ",
        displayName = " Thandi ",
        language = "zu",
        channel = "sms",
        phone = "082 123 4567",
        priority = "pregnant",
    )

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val base = server.url("/").toString()
        repo = AccountRepository(
            authApi = PatientAuthApi.create(base, ApiFactory.httpClient(null)),
            api = ApiFactory.create(base, ApiFactory.httpClient(null)),
            sessionStore = session,
            tickets = store.ticketDao,
            beforeIdentityChange = { events += "before:${session.read()?.accessToken}" },
            afterIdentityChange = { events += "after:${session.read()?.accessToken}" },
            clock = { now },
        )
    }

    @After fun tearDown() { server.shutdown() }

    private fun body(): JsonObject {
        val req = server.takeRequest()
        return ApiJson.decodeFromString(JsonObject.serializer(), req.body.readUtf8())
    }

    @Test fun registerSendsOneRequestAndSavesTheSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(AUTH_JSON))
        val result = repo.register(input)
        assertTrue(result is AccountResult.Success)
        val req = server.takeRequest()
        assertEquals("/v1/auth/patients/register", req.path)
        assertNull("register must not carry a bearer token", req.getHeader("Authorization"))
        val sent = ApiJson.decodeFromString(JsonObject.serializer(), req.body.readUtf8())
        assertEquals("thandi@example.com", sent["email"]!!.jsonPrimitive.content)
        assertEquals("password goes out exactly as typed", " correct horse battery ", sent["password"]!!.jsonPrimitive.content)
        assertEquals("Thandi", sent["display_name"]!!.jsonPrimitive.content)
        assertEquals("sms", sent["notification_channel"]!!.jsonPrimitive.content)
        assertEquals("+27821234567", sent["phone"]!!.jsonPrimitive.content)
        assertEquals("pregnant", sent["priority_request"]!!.jsonPrimitive.content)
        assertEquals("zu", sent["language"]!!.jsonPrimitive.content)

        val saved = session.read()!!
        assertEquals("tok_patient", saved.accessToken)
        assertEquals(now + 604800 * 1000L, saved.expiresAtMs)
        assertEquals("4567", saved.profile.phoneLast4)
        // Push token is moved: removed under the anonymous user, then sent again under the patient.
        assertEquals(listOf("before:null", "after:tok_patient"), events)
    }

    @Test fun pushChannelSendsNoPhoneAndNoPriorityWhenNoneIsPicked() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(AUTH_JSON))
        repo.register(input.copy(channel = "push", priority = "none"))
        val sent = body()
        assertFalse("phone" in sent)
        assertFalse("priority_request" in sent)
        assertEquals("push", sent["notification_channel"]!!.jsonPrimitive.content)
    }

    @Test fun emailInUseIsReported() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody(envelope("EMAIL_IN_USE", "An account with this email already exists.")))
        val result = repo.register(input)
        assertEquals(AccountResult.EmailInUse("An account with this email already exists."), result)
        assertNull(session.read())
        assertTrue(events.isEmpty())
    }

    @Test fun validationMessageIsShownAsItIs() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(422).setBody(envelope("VALIDATION_FAILED", "Add a phone number for SMS alerts.")))
        assertEquals(AccountResult.Invalid("Add a phone number for SMS alerts."), repo.register(input))
    }

    @Test fun rateLimitCarriesRetryAfter() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody(envelope("RATE_LIMITED", "Too many attempts.", retryAfter = 42)))
        assertEquals(AccountResult.RateLimited(42, "Too many attempts."), repo.login("thandi@example.com", "whatever1"))
    }

    @Test fun wrongPasswordAndStaffEmailAreDistinct() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody(envelope("UNAUTHENTICATED", "Incorrect email or password.")))
        server.enqueue(MockResponse().setResponseCode(403).setBody(envelope("FORBIDDEN", "This is a staff account.")))
        assertEquals(AccountResult.WrongCredentials("Incorrect email or password."), repo.login("thandi@example.com", "wrongpass"))
        assertEquals(AccountResult.StaffAccount("This is a staff account."), repo.login("nurse@clinic.org", "secret123"))
        assertNull(session.read())
    }

    @Test fun loginPostsEmailAndPasswordAndSavesTheSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(AUTH_JSON))
        assertTrue(repo.login("Thandi@example.com ", "correct horse battery") is AccountResult.Success)
        val req = server.takeRequest()
        assertEquals("/v1/auth/patients/login", req.path)
        assertEquals("""{"email":"thandi@example.com","password":"correct horse battery"}""", req.body.readUtf8())
        assertEquals("tok_patient", session.read()?.accessToken)
    }

    @Test fun noAnswerIsOffline() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(AccountResult.Offline, repo.login("thandi@example.com", "correct horse"))
    }

    @Test fun anOpenAnonymousTicketBlocksSignInWithoutCallingTheServer() = runBlocking {
        store.ticketDao.insert(
            TicketEntity(
                localRef = "l1", serverId = "t1", displayNumber = "A014", queueId = "q1", servicePointId = "sp1", clinicId = "c1",
                status = "waiting", notificationChannel = "push", language = "en", clientCreatedAt = "2026-10-17T08:00:00Z", updatedAt = "2026-10-17T08:00:00Z",
            ),
        )
        assertEquals(AccountResult.OpenTicket, repo.login("thandi@example.com", "correct horse"))
        assertEquals(AccountResult.OpenTicket, repo.register(input))
        assertEquals(0, server.requestCount)
    }

    @Test fun signOutRemovesThePushTokenBeforeForgettingTheSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(AUTH_JSON))
        repo.login("thandi@example.com", "correct horse battery")
        events.clear()
        repo.signOut()
        assertNull(session.read())
        assertEquals(listOf("before:tok_patient", "after:null"), events)
    }
}
