package com.swiftq.staff.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.swiftq.staff.R
import com.swiftq.staff.data.remote.QueueApiRepository
import com.swiftq.staff.data.remote.SessionExpiredException
import com.swiftq.staff.data.remote.model.TicketResponse
import kotlinx.coroutines.launch
import java.util.UUID

// Bottom sheet that registers a patient who arrived without the app.
class WalkInSheet(
    private val activity: AppCompatActivity,
    private val queueId: String,
    private val servicePointName: String,
    private val repository: QueueApiRepository,
    private val onRegistered: (TicketResponse) -> Unit,
    private val onSessionExpired: (String?) -> Unit
) {

    private data class Option(val value: String, val label: String) {
        override fun toString() = label
    }

    // One key per form: retrying after a timeout must not add the
    // patient twice.
    private val idempotencyKey = UUID.randomUUID().toString()

    private val dialog = BottomSheetDialog(activity)
    private val view: View = LayoutInflater.from(activity)
        .inflate(R.layout.dialog_walk_in, null)

    private val nameLayout: TextInputLayout = view.findViewById(R.id.walkInNameLayout)
    private val nameInput: TextInputEditText = view.findViewById(R.id.walkInNameInput)
    private val phoneLayout: TextInputLayout = view.findViewById(R.id.walkInPhoneLayout)
    private val phoneInput: TextInputEditText = view.findViewById(R.id.walkInPhoneInput)
    private val emailLayout: TextInputLayout = view.findViewById(R.id.walkInEmailLayout)
    private val emailInput: TextInputEditText = view.findViewById(R.id.walkInEmailInput)
    private val languageInput: AutoCompleteTextView = view.findViewById(R.id.walkInLanguageInput)
    private val channelInput: AutoCompleteTextView = view.findViewById(R.id.walkInChannelInput)
    private val priorityInput: AutoCompleteTextView = view.findViewById(R.id.walkInPriorityInput)
    private val errorText: TextView = view.findViewById(R.id.walkInError)
    private val submitButton: MaterialButton = view.findViewById(R.id.walkInSubmitButton)
    private val cancelButton: MaterialButton = view.findViewById(R.id.walkInCancelButton)
    private val progress: ProgressBar = view.findViewById(R.id.walkInProgress)

    private val languages = listOf(
        Option("en", "English"),
        Option("zu", "isiZulu"),
        Option("xh", "isiXhosa"),
        Option("af", "Afrikaans"),
        Option("nso", "Sepedi"),
        Option("st", "Sesotho"),
        Option("tn", "Setswana"),
        Option("ts", "Xitsonga"),
        Option("ss", "siSwati"),
        Option("ve", "Tshivenda"),
        Option("nr", "isiNdebele")
    )

    private val channels = listOf(
        Option("none", activity.getString(R.string.walk_in_channel_none)),
        Option("sms", activity.getString(R.string.walk_in_channel_sms)),
        Option("whatsapp", activity.getString(R.string.walk_in_channel_whatsapp)),
        Option("email", activity.getString(R.string.walk_in_channel_email))
    )

    private val priorities = QueueApiRepository.PRIORITY_CATEGORIES.map {
        Option(it, TicketBadges.categoryLabel(activity, it))
    }

    private var selectedLanguage = languages.first()
    private var selectedChannel = channels.first()
    private var selectedPriority = priorities.first()
    private var isSubmitting = false

    fun show() {
        view.findViewById<TextView>(R.id.walkInSubtitle).text =
            activity.getString(R.string.walk_in_subtitle, servicePointName)

        bindDropdown(languageInput, languages, selectedLanguage) {
            selectedLanguage = it
        }

        bindDropdown(channelInput, channels, selectedChannel) {
            selectedChannel = it
            updateContactFields()
        }

        bindDropdown(priorityInput, priorities, selectedPriority) {
            selectedPriority = it
        }

        nameInput.doAfterTextChanged { nameLayout.error = null }
        phoneInput.doAfterTextChanged { phoneLayout.error = null }
        emailInput.doAfterTextChanged { emailLayout.error = null }

        submitButton.setOnClickListener { submit() }
        cancelButton.setOnClickListener { dialog.dismiss() }

        updateContactFields()

        dialog.setContentView(view)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    private fun bindDropdown(
        input: AutoCompleteTextView,
        options: List<Option>,
        initial: Option,
        onSelected: (Option) -> Unit
    ) {
        input.setAdapter(
            ArrayAdapter(activity, android.R.layout.simple_list_item_1, options)
        )
        input.setText(initial.label, false)
        input.setOnItemClickListener { _, _, position, _ ->
            onSelected(options[position])
        }
    }

    private fun updateContactFields() {
        emailLayout.visibility =
            if (selectedChannel.value == "email") View.VISIBLE else View.GONE

        phoneLayout.helperText = activity.getString(
            if (selectedChannel.value in setOf("sms", "whatsapp")) {
                R.string.walk_in_phone_required
            } else {
                R.string.walk_in_phone_helper
            }
        )
    }

    private fun submit() {
        if (isSubmitting) return

        errorText.visibility = View.GONE

        val name = nameInput.text?.toString()?.trim().orEmpty()
        val phone = normalisePhone(phoneInput.text?.toString().orEmpty())
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val channel = selectedChannel.value

        when {
            name.length > 40 -> {
                nameLayout.error = activity.getString(R.string.walk_in_name_too_long)
                return
            }

            phone != null && !phone.matches(PHONE_PATTERN) -> {
                phoneLayout.error = activity.getString(R.string.walk_in_phone_invalid)
                return
            }

            channel in setOf("sms", "whatsapp") && phone == null -> {
                phoneLayout.error = activity.getString(R.string.walk_in_phone_required)
                return
            }

            channel == "email" &&
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailLayout.error = activity.getString(R.string.login_invalid_email)
                return
            }
        }

        setSubmitting(true)

        activity.lifecycleScope.launch {
            val result = repository.registerWalkIn(
                queueId = queueId,
                idempotencyKey = idempotencyKey,
                displayName = name,
                phone = phone,
                email = email.takeIf { channel == "email" },
                language = selectedLanguage.value,
                notificationChannel = channel,
                priorityCategory = selectedPriority.value
            )

            setSubmitting(false)

            result.onSuccess { ticket ->
                dialog.dismiss()
                onRegistered(ticket)
            }.onFailure { error ->
                if (error is SessionExpiredException) {
                    dialog.dismiss()
                    onSessionExpired(error.message)
                    return@onFailure
                }

                errorText.text = error.message
                    ?: activity.getString(R.string.walk_in_failed)
                errorText.visibility = View.VISIBLE
            }
        }
    }

    private fun setSubmitting(submitting: Boolean) {
        isSubmitting = submitting
        submitButton.isEnabled = !submitting
        cancelButton.isEnabled = !submitting
        progress.visibility = if (submitting) View.VISIBLE else View.GONE
        dialog.setCancelable(!submitting)
    }

    companion object {
        private val PHONE_PATTERN = Regex("^\\+27[0-9]{9}$")

        // Accepts "082 123 4567", "0821234567" or "+27 82 123 4567".
        fun normalisePhone(raw: String): String? {
            val digits = raw.filter { it.isDigit() || it == '+' }
            if (digits.isEmpty()) return null

            return when {
                digits.startsWith("+") -> digits
                digits.startsWith("27") && digits.length == 11 -> "+$digits"
                digits.startsWith("0") && digits.length == 10 -> "+27${digits.drop(1)}"
                else -> digits
            }
        }
    }
}
