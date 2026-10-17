package com.itmda.swiftq.data.repository

/** The API's register rules, checked on the phone first so each step can say what is wrong before the request goes out. */
object AccountValidation {
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 128

    /** Alerts come by app notification, SMS (to the patient's phone) or email. Sign-up itself never uses an SMS code. */
    val REGISTRATION_CHANNELS = listOf(NotificationChoice.PUSH, NotificationChoice.SMS, NotificationChoice.EMAIL)

    /** "urgent" is refused by the API: it is decided at the clinic on the day. */
    val REGISTRATION_PRIORITIES = listOf(PriorityChoice.NONE, "pregnant", "disability", "elderly")

    enum class EmailError { Empty, Invalid }
    enum class PasswordError { TooShort, TooLong }
    enum class NameError { Empty, TooLong, BadCharacters }
    enum class PhoneError { Empty, Invalid }

    fun emailError(raw: String): EmailError? = when {
        raw.isBlank() -> EmailError.Empty
        JoinValidation.normaliseEmail(raw) == null -> EmailError.Invalid
        else -> null
    }

    /** Counted exactly as typed: spaces are part of the password and are never trimmed. */
    fun passwordError(password: String): PasswordError? = when {
        password.length < PASSWORD_MIN -> PasswordError.TooShort
        password.length > PASSWORD_MAX -> PasswordError.TooLong
        else -> null
    }

    fun nameError(raw: String): NameError? {
        if (raw.isBlank()) return NameError.Empty
        return when (JoinValidation.nameError(raw)) {
            JoinValidation.NameError.TooLong -> NameError.TooLong
            JoinValidation.NameError.BadCharacters -> NameError.BadCharacters
            null -> null
        }
    }

    /** Only SMS alerts need a phone number. */
    fun phoneError(channel: String, raw: String): PhoneError? = when {
        !NotificationChoice.needsPhone(channel) -> null
        raw.isBlank() -> PhoneError.Empty
        JoinValidation.normalisePhone(raw) == null -> PhoneError.Invalid
        else -> null
    }
}
