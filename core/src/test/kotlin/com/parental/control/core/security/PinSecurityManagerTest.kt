package com.parental.control.core.security

import org.junit.Assert.*
import org.junit.Test

class PinSecurityManagerTest {

    @Test
    fun `generateSalt returns non-empty base64 string`() {
        val salt = PinSecurityManager.generateSalt()
        assertNotNull(salt)
        assertTrue(salt.isNotEmpty())
    }

    @Test
    fun `hashPin generates repeatable hash for same salt and pin`() {
        val salt = PinSecurityManager.generateSalt()
        val pin = "1234"
        val hash1 = PinSecurityManager.hashPin(pin, salt)
        val hash2 = PinSecurityManager.hashPin(pin, salt)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `hashPin generates different hash for different pins`() {
        val salt = PinSecurityManager.generateSalt()
        val hash1 = PinSecurityManager.hashPin("1234", salt)
        val hash2 = PinSecurityManager.hashPin("5678", salt)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `verifyPin validates correct pin and rejects incorrect pin`() {
        val pin = "9876"
        val salt = PinSecurityManager.generateSalt()
        val hash = PinSecurityManager.hashPin(pin, salt)

        // Correct PIN
        assertTrue(PinSecurityManager.verifyPin("9876", hash, salt))

        // Incorrect PINs
        assertFalse(PinSecurityManager.verifyPin("1234", hash, salt))
        assertFalse(PinSecurityManager.verifyPin("", hash, salt))
        assertFalse(PinSecurityManager.verifyPin("987", hash, salt))
        assertFalse(PinSecurityManager.verifyPin("98760", hash, salt))
    }

    @Test
    fun `generateRecoveryCode produces 6-character alphanumeric code`() {
        val code = PinSecurityManager.generateRecoveryCode()
        assertEquals(6, code.length)
        assertTrue(code.all { it.isLetterOrDigit() })
    }
}
