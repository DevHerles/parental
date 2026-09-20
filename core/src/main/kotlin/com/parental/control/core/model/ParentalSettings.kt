package com.parental.control.core.model

/**
 * Configuración global del sistema de control parental.
 */
data class ParentalSettings(
    val pinHash: String = "",
    val pinSalt: String = "",
    val recoveryCode: String = "",
    val isAntiUninstallActive: Boolean = true,
    val isWebFilterActive: Boolean = true,
    val isInstantLockActive: Boolean = false,
    val pairedChildId: String = "child-device-01",
    val childName: String = "Hija",
    val temporaryUnlockUntil: Long = 0L, // Timestamp millis hasta cuando está desbloqueado temporalmente
    val deviceRole: String = "UNSET" // "PARENT", "CHILD", "UNSET"
) {
    val isConfigured: Boolean
        get() = pinHash.isNotEmpty()

    val isTemporarilyUnlocked: Boolean
        get() = System.currentTimeMillis() < temporaryUnlockUntil

    val isParentMode: Boolean
        get() = deviceRole == "PARENT"

    val isChildMode: Boolean
        get() = deviceRole == "CHILD"
}

