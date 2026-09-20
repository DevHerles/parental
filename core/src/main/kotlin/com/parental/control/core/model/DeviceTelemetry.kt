package com.parental.control.core.model

/**
 * Telemetría y estado del dispositivo de la niña.
 */
data class DeviceTelemetry(
    val deviceId: String = "",
    val batteryPercentage: Int = 100,
    val isCharging: Boolean = false,
    val currentForegroundApp: String = "Launcher",
    val lastHeartbeatTimestamp: Long = System.currentTimeMillis(),
    val tamperingAttemptsCount: Int = 0,
    val lastTamperDetail: String = "",
    val isOnline: Boolean = true
)
