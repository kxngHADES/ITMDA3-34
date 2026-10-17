package com.itmda.swiftq.data

import com.itmda.swiftq.data.repository.AccountValidation
import com.itmda.swiftq.data.repository.AccountValidation.EmailError
import com.itmda.swiftq.data.repository.AccountValidation.NameError
import com.itmda.swiftq.data.repository.AccountValidation.PasswordError
import com.itmda.swiftq.data.repository.AccountValidation.PhoneError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountValidationTest {
    @Test fun emailMustBeAValidAddress() {
        assertEquals(EmailError.Empty, AccountValidation.emailError("  "))
        assertEquals(EmailError.Invalid, AccountValidation.emailError("thandi@"))
        assertEquals(EmailError.Invalid, AccountValidation.emailError("thandi example.com"))
        assertNull(AccountValidation.emailError(" Thandi@Example.com "))
    }

    @Test fun passwordIs8To128CharactersCountedExactlyAsTyped() {
        assertEquals(PasswordError.TooShort, AccountValidation.passwordError("1234567"))
        assertNull(AccountValidation.passwordError("12345678"))
        // Spaces count: the password is never trimmed.
        assertNull(AccountValidation.passwordError("   abcde"))
        assertEquals(PasswordError.TooShort, AccountValidation.passwordError("abc def"))
        assertNull(AccountValidation.passwordError("x".repeat(128)))
        assertEquals(PasswordError.TooLong, AccountValidation.passwordError("x".repeat(129)))
    }

    @Test fun nameIs1To40CharactersWithoutAngleBrackets() {
        assertEquals(NameError.Empty, AccountValidation.nameError(" "))
        assertNull(AccountValidation.nameError("Thandi"))
        assertNull(AccountValidation.nameError("x".repeat(40)))
        assertEquals(NameError.TooLong, AccountValidation.nameError("x".repeat(41)))
        assertEquals(NameError.BadCharacters, AccountValidation.nameError("<b>Thandi"))
    }

    @Test fun alertsArePushSmsOrEmailAndUrgentIsNeverOffered() {
        assertEquals(listOf("push", "sms", "email"), AccountValidation.REGISTRATION_CHANNELS)
        assertFalse("whatsapp" in AccountValidation.REGISTRATION_CHANNELS)
        assertFalse("urgent" in AccountValidation.REGISTRATION_PRIORITIES)
        assertTrue(listOf("none", "pregnant", "disability", "elderly").all { it in AccountValidation.REGISTRATION_PRIORITIES })
    }

    @Test fun onlySmsNeedsAPhoneNumber() {
        assertNull(AccountValidation.phoneError("push", ""))
        assertNull(AccountValidation.phoneError("email", ""))
        assertEquals(PhoneError.Empty, AccountValidation.phoneError("sms", " "))
        assertEquals(PhoneError.Invalid, AccountValidation.phoneError("sms", "12345"))
        assertNull(AccountValidation.phoneError("sms", "082 123 4567"))
        assertNull(AccountValidation.phoneError("sms", "27821234567"))
    }
}
