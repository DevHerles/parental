package com.parental.control.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Gestor de seguridad para el PIN maestro de los padres, hashing con salt y códigos de recuperación.
 */
object PinSecurityManager {

    private const val SALT_LENGTH = 16

    /**
     * Genera un Salt criptográfico aleatorio en base64.
     */
    fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(SALT_LENGTH)
        random.nextBytes(saltBytes)
        return Base64.getEncoder().encodeToString(saltBytes)
    }

    /**
     * Calcula el hash SHA-256 con salt del PIN ingresado.
     */
    fun hashPin(pin: String, saltBase64: String): String {
        val saltBytes = Base64.getDecoder().decode(saltBase64)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltBytes)
        val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    /**
     * Valida si el PIN introducido coincide con el hash almacenado.
     */
    fun verifyPin(enteredPin: String, storedHash: String, storedSalt: String): Boolean {
        if (enteredPin.isBlank() || storedHash.isBlank() || storedSalt.isBlank()) {
            return false
        }
        val computedHash = hashPin(enteredPin, storedSalt)
        return computedHash == storedHash
    }

    /**
     * Genera un código de recuperación único de 6 caracteres alfanuméricos.
     */
    fun generateRecoveryCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        val sb = java.lang.StringBuilder(6)
        for (i in 0 until 6) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }
}
