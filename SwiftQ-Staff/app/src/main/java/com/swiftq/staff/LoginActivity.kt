package com.swiftq.staff

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.swiftq.staff.data.remote.StaffLoginHandler
import com.swiftq.staff.ui.applySystemBarInsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var loginProgress: ProgressBar

    private lateinit var staffLoginHandler: StaffLoginHandler

    private val loginScope = CoroutineScope(
        Dispatchers.Main + Job()
    )

    private var isProcessingLogin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        // Keep the form clear of the system bars and the keyboard.
        findViewById<View>(R.id.loginRoot).applySystemBarInsets(
            top = true,
            bottom = true,
            includeIme = true
        )

        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        loginProgress = findViewById(R.id.loginProgress)

        staffLoginHandler = StaffLoginHandler(applicationContext)

        setLoading(false)

        loginButton.setOnClickListener {
            if (!isProcessingLogin) {
                validateAndLogin()
            }
        }
    }

    private fun validateAndLogin() {
        val email = emailInput.text
            ?.toString()
            ?.trim()
            .orEmpty()

        val password = passwordInput.text
            ?.toString()
            .orEmpty()

        emailLayout.error = null
        passwordLayout.error = null

        when {
            email.isBlank() -> {
                emailLayout.error =
                    getString(R.string.login_required_email)
                emailInput.requestFocus()
                return
            }

            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailLayout.error =
                    getString(R.string.login_invalid_email)
                emailInput.requestFocus()
                return
            }

            password.isBlank() -> {
                passwordLayout.error =
                    getString(R.string.login_required_password)
                passwordInput.requestFocus()
                return
            }
        }

        signIn(email, password)
    }

    private fun signIn(email: String, password: String) {
        setLoading(true)

        loginScope.launch {
            try {
                val result = staffLoginHandler.login(
                    email = email,
                    password = password
                )

                result.fold(
                    onSuccess = { response ->
                        Toast.makeText(
                            this@LoginActivity,
                            "Welcome, ${response.user.fullName}",
                            Toast.LENGTH_SHORT
                        ).show()

                        openQueueDashboard()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@LoginActivity,
                            error.message
                                ?: getString(R.string.login_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            } catch (exception: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.login_failed),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun openQueueDashboard() {
        val intent = Intent(
            this,
            QueueDashboardActivity::class.java
        )

        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        isProcessingLogin = isLoading

        loginButton.isEnabled = !isLoading
        emailInput.isEnabled = !isLoading
        passwordInput.isEnabled = !isLoading

        loginProgress.visibility =
            if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        loginScope.cancel()
        super.onDestroy()
    }
}