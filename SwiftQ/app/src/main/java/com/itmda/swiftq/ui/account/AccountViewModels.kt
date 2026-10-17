package com.itmda.swiftq.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda.swiftq.data.repository.AccountRepository
import com.itmda.swiftq.data.repository.AccountResult
import com.itmda.swiftq.data.repository.AccountValidation
import com.itmda.swiftq.data.repository.NotificationChoice
import com.itmda.swiftq.data.repository.PriorityChoice
import com.itmda.swiftq.data.repository.RegistrationInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A problem the server or the phone reported, shown as a notice above the button. */
sealed interface FormProblem {
    /** The API's own words, shown as they are. */
    data class Server(val message: String) : FormProblem
    data object Offline : FormProblem
    data object OpenTicket : FormProblem
    /** RATE_LIMITED: the button stays disabled until [secondsLeft] reaches 0. */
    data class RateLimited(val secondsLeft: Int) : FormProblem
}

/** Figma 09a to 09f: one field (or one choice) per screen, then a check-your-details screen. */
enum class RegisterStep { Email, Password, Name, Alerts, Priority, Review }

data class RegisterUi(
    val step: RegisterStep = RegisterStep.Email,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val channel: String = NotificationChoice.PUSH,
    val phone: String = "",
    val priority: String = PriorityChoice.NONE,
    /** Field errors show only after Next was tapped on that step, not while typing the first time. */
    val showErrors: Boolean = false,
    /** Set when "Change" was tapped on the review screen, so Next goes straight back there. */
    val editingFromReview: Boolean = false,
    /** EMAIL_IN_USE message (Figma 09g). */
    val emailInUse: String? = null,
    val problem: FormProblem? = null,
    val submitting: Boolean = false,
    val done: Boolean = false,
) {
    val emailError get() = AccountValidation.emailError(email)
    val passwordError get() = AccountValidation.passwordError(password)
    val nameError get() = AccountValidation.nameError(name)
    val phoneError get() = AccountValidation.phoneError(channel, phone)
    val stepNumber get() = step.ordinal + 1
    val rateLimited get() = problem is FormProblem.RateLimited

    fun stepValid(s: RegisterStep = step): Boolean = when (s) {
        RegisterStep.Email -> emailError == null
        RegisterStep.Password -> passwordError == null
        RegisterStep.Name -> nameError == null
        RegisterStep.Alerts -> phoneError == null
        RegisterStep.Priority, RegisterStep.Review -> true
    }

    companion object { val STEP_COUNT = RegisterStep.entries.size - 1 } // the review screen is not a numbered step
}

class RegisterViewModel(
    private val accounts: AccountRepository,
    private val language: () -> String,
) : ViewModel() {
    private val state = MutableStateFlow(RegisterUi())
    val ui: StateFlow<RegisterUi> = state.asStateFlow()
    private var countdown: Job? = null

    fun onEmail(v: String) = state.update { it.copy(email = v, emailInUse = null) }
    fun onPassword(v: String) = state.update { it.copy(password = v) }
    fun onName(v: String) = state.update { it.copy(name = v) }
    fun onChannel(v: String) { if (v in AccountValidation.REGISTRATION_CHANNELS) state.update { it.copy(channel = v) } }
    fun onPhone(v: String) = state.update { it.copy(phone = v) }
    fun onPriority(v: String) { if (v in AccountValidation.REGISTRATION_PRIORITIES) state.update { it.copy(priority = v) } }

    fun next() = state.update { s ->
        when {
            s.emailInUse != null && s.step == RegisterStep.Email -> s.copy(showErrors = true)
            !s.stepValid() -> s.copy(showErrors = true)
            s.editingFromReview -> s.copy(step = RegisterStep.Review, showErrors = false, editingFromReview = false)
            else -> s.copy(step = RegisterStep.entries[s.step.ordinal + 1], showErrors = false)
        }
    }

    /** False when already on the first step, so the caller leaves the flow. */
    fun back(): Boolean {
        val s = state.value
        if (s.submitting) return true
        if (s.editingFromReview) { state.update { it.copy(step = RegisterStep.Review, editingFromReview = false, showErrors = false) }; return true }
        if (s.step == RegisterStep.Email) return false
        state.update { it.copy(step = RegisterStep.entries[it.step.ordinal - 1], showErrors = false) }
        return true
    }

    fun edit(step: RegisterStep) = state.update { it.copy(step = step, editingFromReview = true, showErrors = false) }

    fun submit() {
        val s = state.value
        if (s.submitting || s.rateLimited) return
        // Every step is checked again: a value can only be wrong here if a step was skipped.
        RegisterStep.entries.firstOrNull { !s.stepValid(it) }?.let { bad ->
            state.update { it.copy(step = bad, showErrors = true, editingFromReview = true) }
            return
        }
        state.update { it.copy(submitting = true, problem = null) }
        viewModelScope.launch {
            val result = accounts.register(
                RegistrationInput(s.email, s.password, s.name, language(), s.channel, s.phone, s.priority),
            )
            state.update { it.copy(submitting = false) }
            when (result) {
                is AccountResult.Success -> state.update { it.copy(done = true) }
                is AccountResult.EmailInUse -> state.update {
                    it.copy(step = RegisterStep.Email, emailInUse = result.message, showErrors = true, editingFromReview = true)
                }
                is AccountResult.RateLimited -> startCountdown(result.retryAfterSeconds)
                is AccountResult.Invalid -> state.update { it.copy(problem = FormProblem.Server(result.message)) }
                is AccountResult.WrongCredentials -> state.update { it.copy(problem = FormProblem.Server(result.message)) }
                is AccountResult.StaffAccount -> state.update { it.copy(problem = FormProblem.Server(result.message)) }
                is AccountResult.Failed -> state.update { it.copy(problem = result.message?.let(FormProblem::Server) ?: FormProblem.Offline) }
                AccountResult.OpenTicket -> state.update { it.copy(problem = FormProblem.OpenTicket) }
                AccountResult.Offline -> state.update { it.copy(problem = FormProblem.Offline) }
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch { runCountdown(seconds) { p -> state.update { it.copy(problem = p) } } }
    }
}

data class SignInUi(
    val email: String = "",
    val password: String = "",
    val showErrors: Boolean = false,
    /** 401 "Incorrect email or password." under the password field (Figma 22b). */
    val wrongCredentials: String? = null,
    val problem: FormProblem? = null,
    val submitting: Boolean = false,
    val done: Boolean = false,
) {
    val emailError get() = AccountValidation.emailError(email)
    val passwordMissing get() = password.isEmpty()
    val rateLimited get() = problem is FormProblem.RateLimited
}

/** Figma 22 · Sign in. */
class SignInViewModel(private val accounts: AccountRepository) : ViewModel() {
    private val state = MutableStateFlow(SignInUi())
    val ui: StateFlow<SignInUi> = state.asStateFlow()
    private var countdown: Job? = null

    fun prefill(email: String) { if (state.value.email.isEmpty()) state.update { it.copy(email = email) } }
    fun onEmail(v: String) = state.update { it.copy(email = v, wrongCredentials = null) }
    fun onPassword(v: String) = state.update { it.copy(password = v, wrongCredentials = null) }

    fun submit() {
        val s = state.value
        if (s.submitting || s.rateLimited) return
        if (s.emailError != null || s.passwordMissing) { state.update { it.copy(showErrors = true) }; return }
        state.update { it.copy(submitting = true, problem = null, wrongCredentials = null) }
        viewModelScope.launch {
            val result = accounts.login(s.email, s.password)
            state.update { it.copy(submitting = false) }
            when (result) {
                is AccountResult.Success -> state.update { it.copy(done = true) }
                is AccountResult.WrongCredentials -> state.update { it.copy(wrongCredentials = result.message) }
                is AccountResult.RateLimited -> {
                    countdown?.cancel()
                    countdown = viewModelScope.launch { runCountdown(result.retryAfterSeconds) { p -> state.update { it.copy(problem = p) } } }
                }
                is AccountResult.StaffAccount -> state.update { it.copy(problem = FormProblem.Server(result.message)) }
                is AccountResult.Invalid -> state.update { it.copy(problem = FormProblem.Server(result.message)) }
                is AccountResult.EmailInUse -> state.update { it.copy(problem = FormProblem.Server(result.message)) }
                is AccountResult.Failed -> state.update { it.copy(problem = result.message?.let(FormProblem::Server) ?: FormProblem.Offline) }
                AccountResult.OpenTicket -> state.update { it.copy(problem = FormProblem.OpenTicket) }
                AccountResult.Offline -> state.update { it.copy(problem = FormProblem.Offline) }
            }
        }
    }
}

/** Counts [seconds] down once a second, then clears the problem so the button works again. */
private suspend fun runCountdown(seconds: Int, set: (FormProblem?) -> Unit) {
    for (left in seconds.coerceAtLeast(1) downTo 1) {
        set(FormProblem.RateLimited(left))
        delay(1_000)
    }
    set(null)
}
