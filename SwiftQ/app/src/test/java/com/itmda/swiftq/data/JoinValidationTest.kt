package com.itmda.swiftq.data

import com.itmda.swiftq.data.repository.JoinValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoinValidationTest {
    @Test fun acceptsAllThreeWrittenForms() {
        assertEquals("+27821234567", JoinValidation.normalisePhone("0821234567"))
        assertEquals("+27821234567", JoinValidation.normalisePhone("082 123 4567"))
        assertEquals("+27821234567", JoinValidation.normalisePhone("+27821234567"))
        assertEquals("+27821234567", JoinValidation.normalisePhone("+27 82 123 4567"))
        assertEquals("+27821234567", JoinValidation.normalisePhone(" 082-123-4567 "))
    }

    @Test fun rejectsInvalidNumbers() {
        listOf("", "12345", "082123456", "08212345678", "+44 7911 123456", "+2782123456", "abcdefghij", "0821234567x", "+270821234567")
            .forEach { assertNull(it, JoinValidation.normalisePhone(it)) }
    }

    @Test fun nameRules() {
        assertNull(JoinValidation.nameError("Thabo"))
        assertEquals(JoinValidation.NameError.TooLong, JoinValidation.nameError("a".repeat(41)))
        assertEquals(JoinValidation.NameError.BadCharacters, JoinValidation.nameError("<b>"))
    }
}
