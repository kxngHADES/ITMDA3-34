package com.itmda.swiftq.ui

import com.itmda.swiftq.FakeStore
import com.itmda.swiftq.data.AUTH_JSON
import com.itmda.swiftq.data.InMemorySessionStore
import com.itmda.swiftq.data.envelope
import com.itmda.swiftq.data.remote.ApiFactory
import com.itmda.swiftq.data.remote.PatientAuthApi
import com.itmda.swiftq.data.repository.AccountRepository
import com.itmda.swiftq.ui.account.FormProblem
import com.itmda.swiftq.ui.account.RegisterStep
import com.itmda.swiftq.ui.account.RegisterViewModel
import com.itmda.swiftq.ui.account.SignInViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var accounts: AccountRepository
    private val session = InMemorySessionStore()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().also { it.start() }
        val base = server.url("/").toString()
        accounts = AccountRepository(
            PatientAuthApi.create(base, ApiFactory.httpClient(null)), ApiFactory.create(base, ApiFactory.httpClient(null)),
            session, FakeStore().ticketDao,
        )
    }

    @After fun tearDown() { server.shutdown(); Dispatchers.resetMain() }

    private fun filledIn() = RegisterViewModel(accounts) { "en" }.apply {
        onEmail("thandi@example.com"); next()
        onPassword("correct horse"); next()
        onName("Thandi"); next()
        next() // alerts: push
        next() // priority: none
    }

    @Test fun walksOneStepAtATimeAndBlocksOnAnInvalidValue() {
        val vm = RegisterViewModel(accounts) { "en" }
        vm.onEmail("not-an-email"); vm.next()
        assertEquals(RegisterStep.Email, vm.ui.value.step)
        assertTrue(vm.ui.value.showErrors)
        vm.onEmail("thandi@example.com"); vm.next()
        assertEquals(RegisterStep.Password, vm.ui.value.step)
        assertFalse("errors reset on a new step", vm.ui.value.showErrors)
        vm.onPassword("short"); vm.next()
        assertEquals(RegisterStep.Password, vm.ui.value.step)
        vm.onPassword("long enough"); vm.next()
        assertEquals(RegisterStep.Name, vm.ui.value.step)
        assertEquals(3, vm.ui.value.stepNumber)
    }

    @Test fun smsNeedsANumberAndWhatsAppCannotBePicked() {
        val vm = RegisterViewModel(accounts) { "en" }
        vm.onEmail("thandi@example.com"); vm.next(); vm.onPassword("correct horse"); vm.next(); vm.onName("Thandi"); vm.next()
        vm.onChannel("whatsapp")
        assertEquals("push", vm.ui.value.channel)
        vm.onChannel("none")
        assertEquals("push", vm.ui.value.channel)
        vm.onChannel("sms"); vm.next()
        assertEquals(RegisterStep.Alerts, vm.ui.value.step)
        vm.onPhone("082 123 4567"); vm.next()
        assertEquals(RegisterStep.Priority, vm.ui.value.step)
        vm.onPriority("urgent")
        assertEquals("none", vm.ui.value.priority)
    }

    @Test fun backWalksTheStepsAndLeavesFromTheFirst() {
        val vm = filledIn()
        assertEquals(RegisterStep.Review, vm.ui.value.step)
        repeat(5) { assertTrue(vm.back()) }
        assertEquals(RegisterStep.Email, vm.ui.value.step)
        assertFalse(vm.back())
    }

    @Test fun changeFromReviewComesStraightBack() {
        val vm = filledIn()
        vm.edit(RegisterStep.Name)
        vm.onName("Thandi M"); vm.next()
        assertEquals(RegisterStep.Review, vm.ui.value.step)
        assertEquals("Thandi M", vm.ui.value.name)
    }

    @Test fun createAccountSucceeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(AUTH_JSON))
        val vm = filledIn()
        vm.submit()
        withTimeout(5_000) { vm.ui.first { it.done } }
        assertEquals("tok_patient", session.read()?.accessToken)
    }

    @Test fun emailInUseGoesBackToTheEmailStep() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody(envelope("EMAIL_IN_USE", "An account with this email already exists.")))
        val vm = filledIn()
        vm.submit()
        val ui = withTimeout(5_000) { vm.ui.first { it.emailInUse != null } }
        assertEquals(RegisterStep.Email, ui.step)
        assertEquals("An account with this email already exists.", ui.emailInUse)
        vm.onEmail("other@example.com")
        assertNull(vm.ui.value.emailInUse)
        vm.next()
        assertEquals("a corrected email returns to the review", RegisterStep.Review, vm.ui.value.step)
    }

    @Test fun rateLimitDisablesTheButton() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody(envelope("RATE_LIMITED", "Too many attempts.", retryAfter = 30)))
        val vm = filledIn()
        vm.submit()
        val ui = withTimeout(5_000) { vm.ui.first { it.problem is FormProblem.RateLimited } }
        assertEquals(30, (ui.problem as FormProblem.RateLimited).secondsLeft)
        vm.submit() // ignored while limited
        assertEquals(1, server.requestCount)
    }

    @Test fun signInShowsTheServersWrongPasswordMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody(envelope("UNAUTHENTICATED", "Incorrect email or password.")))
        val vm = SignInViewModel(accounts)
        vm.submit()
        assertTrue("empty form is not sent", vm.ui.value.showErrors)
        assertEquals(0, server.requestCount)
        vm.onEmail("thandi@example.com"); vm.onPassword("wrong-password")
        vm.submit()
        val ui = withTimeout(5_000) { vm.ui.first { it.wrongCredentials != null } }
        assertEquals("Incorrect email or password.", ui.wrongCredentials)
        assertNull(session.read())
    }
}
