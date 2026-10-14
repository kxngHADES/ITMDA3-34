package com.itmda.swiftq.data.repository

/** Same rules as the API's Pydantic JoinQueueRequest, so a request the server would reject never leaves the phone. */
object JoinValidation {
    const val NAME_MAX = 40
    private val SA_PHONE = Regex("^\\+27[0-9]{9}$")

    /**
     * Accepts 0821234567, 082 123 4567 and +27821234567 (also 27821234567 and dashes/brackets)
     * and returns +27821234567, or null when it is not a South African number.
     */
    fun normalisePhone(raw: String): String? {
        var s = raw.trim().replace(Regex("[\\s\\-().]"), "")
        s = when {
            s.startsWith("+") -> s
            s.startsWith("27") && s.length == 11 -> "+$s"
            s.startsWith("0") -> "+27" + s.drop(1)
            else -> return null
        }
        return s.takeIf { SA_PHONE.matches(it) }
    }

    /** Null means valid. The server forbids `<` and `>` in free text. */
    fun nameError(name: String): NameError? = when {
        name.trim().length > NAME_MAX -> NameError.TooLong
        name.contains('<') || name.contains('>') -> NameError.BadCharacters
        else -> null
    }

    enum class NameError { TooLong, BadCharacters }

    private val EMAIL = Regex("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]{2,}$")

    /** Trimmed, lower-cased address, or null when it does not look like one. */
    fun normaliseEmail(raw: String): String? =
        raw.trim().lowercase().takeIf { it.length <= 254 && EMAIL.matches(it) }
}

object NotificationChoice {
    const val PUSH = "push"
    const val SMS = "sms"
    const val WHATSAPP = "whatsapp"
    const val NONE = "none"

    /** Sent to the API with the address in `email`; the server encrypts it and sends the alerts. */
    const val EMAIL = "email"

    /** The server requires a phone number for SMS and WhatsApp. */
    fun needsPhone(channel: String) = channel == SMS || channel == WHATSAPP

    fun needsEmail(channel: String) = channel == EMAIL

}

object PriorityChoice {
    const val NONE = "none"
    val ALL = listOf("pregnant", "disability", "elderly", "urgent")
}
