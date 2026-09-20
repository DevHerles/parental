package com.parental.control.core.turso

/**
 * Representa una sentencia SQL y sus argumentos tipados para la API /v2/pipeline de Turso (LibSQL).
 */
data class TursoStatement(
    val sql: String,
    val args: List<Any?> = emptyList()
)

/**
 * Resultado devuelto por una sentencia ejecutada en Turso.
 */
data class TursoResult(
    val rows: List<Map<String, Any?>> = emptyList(),
    val affectedRowCount: Int = 0,
    val error: String? = null
) {
    val isSuccess: Boolean get() = error == null
}

/**
 * Estado y presencia del dispositivo en Turso.
 */
data class TursoDevice(
    val deviceId: String,
    val familyId: String = "family_default",
    val role: String = "CHILD",
    val deviceName: String = "",
    val model: String = "",
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val currentForegroundApp: String? = null,
    val isOnline: Boolean = true,
    val lastSeenAt: Long = System.currentTimeMillis()
)

/**
 * Configuración global sincronizada del control parental.
 */
data class TursoSettings(
    val deviceId: String,
    val isInstantLockActive: Boolean = false,
    val isTemporarilyUnlocked: Boolean = false,
    val temporaryUnlockUntil: Long = 0L,
    val isAntiUninstallActive: Boolean = true,
    val isWebFilterActive: Boolean = true,
    val dailyTimeLimitMinutes: Int = 120,
    val version: Long = 1L,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Comando táctico despachado por el padre para ejecución en el dispositivo de la hija.
 */
data class TursoCommand(
    val commandId: String,
    val targetDeviceId: String,
    val sourceDeviceId: String? = "parent_cli",
    val commandType: String,
    val payload: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val executedAt: Long? = null,
    val status: String = "PENDING"
) {
    companion object {
        const val TYPE_LOCK_NOW = "LOCK_NOW"
        const val TYPE_UNLOCK_TEMPORARY = "UNLOCK_TEMPORARY"
        const val TYPE_CLEAR_LOCK = "CLEAR_LOCK"
        const val TYPE_UPDATE_APP = "UPDATE_APP"
        const val TYPE_PING = "PING"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_EXECUTED = "EXECUTED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}

/**
 * Restricción específica de una aplicación en la nube.
 */
data class TursoAppRestriction(
    val deviceId: String,
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val dailyLimitMinutes: Int = 0,
    val isAllowedInBedtime: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
