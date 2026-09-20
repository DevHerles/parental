package com.parental.control.sync

import android.content.Context
import android.util.Log
import com.parental.control.core.turso.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Gestor y consola remota del padre respaldada por Turso Cloud (LibSQL).
 * Proporciona telemetría en tiempo real, despacho de comandos tácticos con ACK (<2s),
 * administración de restricciones de apps y diagnósticos de latencia (Ping).
 */
class TursoParentManager private constructor(private val context: Context) {

    private val tursoClient = TursoClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var pollJob: Job? = null
    @Volatile
    private var isPolling = false

    private val _activeChildDevice = MutableStateFlow<TursoDevice?>(null)
    val activeChildDevice: StateFlow<TursoDevice?> = _activeChildDevice.asStateFlow()

    private val _childDevicesList = MutableStateFlow<List<TursoDevice>>(emptyList())
    val childDevicesList: StateFlow<List<TursoDevice>> = _childDevicesList.asStateFlow()

    private val _childSettings = MutableStateFlow<TursoSettings?>(null)
    val childSettings: StateFlow<TursoSettings?> = _childSettings.asStateFlow()

    private val _childAppRestrictions = MutableStateFlow<List<TursoAppRestriction>>(emptyList())
    val childAppRestrictions: StateFlow<List<TursoAppRestriction>> = _childAppRestrictions.asStateFlow()

    private val _recentTamperLogs = MutableStateFlow<List<TursoTamperLog>>(emptyList())
    val recentTamperLogs: StateFlow<List<TursoTamperLog>> = _recentTamperLogs.asStateFlow()

    private val _lastPingRtt = MutableStateFlow<Long?>(null)
    val lastPingRtt: StateFlow<Long?> = _lastPingRtt.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun startPolling() {
        if (isPolling) return
        isPolling = true
        Log.i(TAG, "Iniciando sondeo en tiempo real de TursoParentManager...")

        pollJob = scope.launch {
            while (isActive && isPolling) {
                try {
                    refreshAllData()
                } catch (e: Exception) {
                    Log.w(TAG, "Error refrescando datos en TursoParentManager: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        isPolling = false
        pollJob?.cancel()
        pollJob = null
        Log.i(TAG, "Sondeo de TursoParentManager detenido.")
    }

    fun selectDevice(device: TursoDevice) {
        _activeChildDevice.value = device
        scope.launch {
            refreshDeviceSpecificData(device.deviceId)
        }
    }

    /**
     * Refresca todo el conjunto de datos desde Turso Cloud.
     */
    suspend fun refreshAllData() = withContext(Dispatchers.IO) {
        // 1. Obtener lista de dispositivos ordenados por presencia
        val devRows = tursoClient.query("SELECT * FROM devices ORDER BY last_seen_at DESC;")
        val now = System.currentTimeMillis()
        val devices = devRows.map { row ->
            val lastSeen = (row["last_seen_at"] as? Number)?.toLong() ?: 0L
            val isOnline = (now - lastSeen) <= 20_000L
            TursoDevice(
                deviceId = row["device_id"]?.toString() ?: "",
                familyId = row["family_id"]?.toString() ?: "family_default",
                role = row["role"]?.toString() ?: "CHILD",
                deviceName = row["device_name"]?.toString() ?: "",
                model = row["model"]?.toString() ?: "",
                batteryPercent = (row["battery_percent"] as? Number)?.toInt() ?: 100,
                isCharging = ((row["is_charging"] as? Number)?.toInt() ?: 0) == 1,
                currentForegroundApp = row["current_foreground_app"]?.toString(),
                isOnline = isOnline,
                lastSeenAt = lastSeen
            )
        }
        _childDevicesList.value = devices

        // Seleccionar dispositivo activo si no hay ninguno seleccionado
        val currentActive = _activeChildDevice.value
        val targetDevice = if (currentActive != null) {
            devices.find { it.deviceId == currentActive.deviceId } ?: devices.firstOrNull()
        } else {
            devices.firstOrNull()
        }

        _activeChildDevice.value = targetDevice

        if (targetDevice != null) {
            refreshDeviceSpecificData(targetDevice.deviceId)
        }
    }

    private suspend fun refreshDeviceSpecificData(deviceId: String) = withContext(Dispatchers.IO) {
        // 2. Obtener ajustes del dispositivo
        val settingsRows = tursoClient.query("SELECT * FROM parental_settings WHERE device_id = ?;", listOf(deviceId))
        if (settingsRows.isNotEmpty()) {
            val sRow = settingsRows.first()
            val instantLock = ((sRow["is_instant_lock_active"] as? Number)?.toInt() ?: 0) == 1
            val tempUnlocked = ((sRow["is_temporarily_unlocked"] as? Number)?.toInt() ?: 0) == 1
            val unlockUntil = (sRow["temporary_unlock_until"] as? Number)?.toLong() ?: 0L
            val antiUninstall = ((sRow["is_anti_uninstall_active"] as? Number)?.toInt() ?: 1) == 1
            val webFilter = ((sRow["is_web_filter_active"] as? Number)?.toInt() ?: 1) == 1
            val limitMins = (sRow["daily_time_limit_minutes"] as? Number)?.toInt() ?: 120
            val version = (sRow["version"] as? Number)?.toLong() ?: 1L
            val updatedAt = (sRow["updated_at"] as? Number)?.toLong() ?: System.currentTimeMillis()

            _childSettings.value = TursoSettings(
                deviceId = deviceId,
                isInstantLockActive = instantLock,
                isTemporarilyUnlocked = tempUnlocked,
                temporaryUnlockUntil = unlockUntil,
                isAntiUninstallActive = antiUninstall,
                isWebFilterActive = webFilter,
                dailyTimeLimitMinutes = limitMins,
                version = version,
                updatedAt = updatedAt
            )
        }

        // 3. Obtener restricciones de aplicaciones
        val appRows = tursoClient.query(
            "SELECT * FROM app_restrictions WHERE device_id = ? ORDER BY app_name ASC;",
            listOf(deviceId)
        )
        val apps = appRows.map { row ->
            TursoAppRestriction(
                deviceId = deviceId,
                packageName = row["package_name"]?.toString() ?: "",
                appName = row["app_name"]?.toString() ?: "",
                isBlocked = ((row["is_blocked"] as? Number)?.toInt() ?: 0) == 1,
                dailyLimitMinutes = (row["daily_limit_minutes"] as? Number)?.toInt() ?: 0,
                isAllowedInBedtime = ((row["is_allowed_in_bedtime"] as? Number)?.toInt() ?: 0) == 1,
                updatedAt = (row["updated_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
        _childAppRestrictions.value = apps

        // 4. Obtener últimos logs de tampering
        val logRows = tursoClient.query(
            "SELECT * FROM tamper_logs WHERE device_id = ? ORDER BY timestamp DESC LIMIT 5;",
            listOf(deviceId)
        )
        val logs = logRows.map { row ->
            TursoTamperLog(
                id = (row["id"] as? Number)?.toLong() ?: 0L,
                deviceId = deviceId,
                eventType = row["event_type"]?.toString() ?: "TAMPER_ATTEMPT",
                detail = row["detail"]?.toString() ?: "",
                timestamp = (row["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
        _recentTamperLogs.value = logs
    }

    /**
     * Envía comando de bloqueo total instantáneo a la tablet de la hija.
     */
    suspend fun lockDevice(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        _isOperating.value = true
        _statusMessage.value = "Enviando bloqueo instantáneo..."
        val cmdId = "cmd_mob_${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()

        try {
            tursoClient.query(
                "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_mobile', 'LOCK_NOW', '{}', ?, 'PENDING');",
                listOf(cmdId, deviceId, now)
            )
            tursoClient.query(
                "UPDATE parental_settings SET is_instant_lock_active = 1, updated_at = ? WHERE device_id = ?;",
                listOf(now, deviceId)
            )

            val ack = awaitCommandAck(cmdId)
            if (ack) {
                _statusMessage.value = "🔒 ¡Tablet bloqueada con éxito!"
            } else {
                _statusMessage.value = "⚠️ Orden registrada en Turso Cloud"
            }
            refreshDeviceSpecificData(deviceId)
            ack
        } catch (e: Exception) {
            Log.e(TAG, "Error bloqueando dispositivo: ${e.message}", e)
            _statusMessage.value = "Error: ${e.message}"
            false
        } finally {
            _isOperating.value = false
        }
    }

    /**
     * Concede un tiempo de desbloqueo temporal (en minutos) a la hija.
     */
    suspend fun unlockDevice(deviceId: String, minutes: Int): Boolean = withContext(Dispatchers.IO) {
        _isOperating.value = true
        _statusMessage.value = "Autorizando $minutes min de uso..."
        val cmdId = "cmd_mob_${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()
        val until = now + (minutes * 60 * 1000L)
        val payload = JSONObject().apply { put("minutes", minutes) }.toString()

        try {
            tursoClient.query(
                "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_mobile', 'UNLOCK_TEMPORARY', ?, ?, 'PENDING');",
                listOf(cmdId, deviceId, payload, now)
            )
            tursoClient.query(
                "UPDATE parental_settings SET is_instant_lock_active = 0, is_temporarily_unlocked = 1, temporary_unlock_until = ?, updated_at = ? WHERE device_id = ?;",
                listOf(until, now, deviceId)
            )

            val ack = awaitCommandAck(cmdId)
            if (ack) {
                _statusMessage.value = "✅ ¡Desbloqueo de $minutes min concedido!"
            } else {
                _statusMessage.value = "⚠️ Autorización registrada en Turso Cloud"
            }
            refreshDeviceSpecificData(deviceId)
            ack
        } catch (e: Exception) {
            Log.e(TAG, "Error desbloqueando dispositivo: ${e.message}", e)
            _statusMessage.value = "Error: ${e.message}"
            false
        } finally {
            _isOperating.value = false
        }
    }

    /**
     * Cancela pausas y reanuda el bloqueo / modo protegido estricto.
     */
    suspend fun resumeProtection(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        _isOperating.value = true
        _statusMessage.value = "Reanudando protección estricta..."
        val cmdId = "cmd_mob_${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()

        try {
            tursoClient.query(
                "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_mobile', 'CLEAR_LOCK', '{}', ?, 'PENDING');",
                listOf(cmdId, deviceId, now)
            )
            tursoClient.query(
                "UPDATE parental_settings SET is_instant_lock_active = 0, is_temporarily_unlocked = 0, temporary_unlock_until = 0, updated_at = ? WHERE device_id = ?;",
                listOf(now, deviceId)
            )

            val ack = awaitCommandAck(cmdId)
            if (ack) {
                _statusMessage.value = "🛡️ Protección estricta reanudada"
            } else {
                _statusMessage.value = "⚠️ Reanudación registrada en Turso Cloud"
            }
            refreshDeviceSpecificData(deviceId)
            ack
        } catch (e: Exception) {
            Log.e(TAG, "Error reanudando protección: ${e.message}", e)
            _statusMessage.value = "Error: ${e.message}"
            false
        } finally {
            _isOperating.value = false
        }
    }

    /**
     * Bloquea o permite una aplicación determinada en el dispositivo de la hija.
     */
    suspend fun setAppBlocked(deviceId: String, packageName: String, appName: String, blocked: Boolean): Boolean = withContext(Dispatchers.IO) {
        _isOperating.value = true
        val cmdId = "cmd_mob_${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("package", packageName)
            put("blocked", blocked)
        }.toString()

        try {
            tursoClient.query(
                "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_mobile', 'UPDATE_APP', ?, ?, 'PENDING');",
                listOf(cmdId, deviceId, payload, now)
            )

            val upsertSql = """
                INSERT INTO app_restrictions (device_id, package_name, app_name, is_blocked, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(device_id, package_name) DO UPDATE SET is_blocked = excluded.is_blocked, updated_at = excluded.updated_at;
            """.trimIndent()

            tursoClient.query(upsertSql, listOf(deviceId, packageName, appName, if (blocked) 1 else 0, now))

            val actionStr = if (blocked) "bloqueada" else "permitida"
            _statusMessage.value = "App $appName $actionStr"
            refreshDeviceSpecificData(deviceId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando regla de app $packageName: ${e.message}", e)
            _statusMessage.value = "Error: ${e.message}"
            false
        } finally {
            _isOperating.value = false
        }
    }

    /**
     * Ejecuta una prueba de ping y latencia hacia la tablet a través de Turso Cloud.
     */
    suspend fun pingDevice(deviceId: String): Long? = withContext(Dispatchers.IO) {
        _isPinging.value = true
        _statusMessage.value = "📡 Enviando Ping a la tablet..."
        val cmdId = "ping_mob_${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()
        val startT = System.currentTimeMillis()

        try {
            tursoClient.query(
                "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_mobile', 'PING', '{}', ?, 'PENDING');",
                listOf(cmdId, deviceId, now)
            )

            var ack = false
            for (i in 0 until 25) {
                delay(300L)
                val rows = tursoClient.query("SELECT status FROM commands WHERE command_id = ?;", listOf(cmdId))
                if (rows.isNotEmpty() && rows.first()["status"]?.toString() == "EXECUTED") {
                    ack = true
                    break
                }
            }

            val rtt = System.currentTimeMillis() - startT
            if (ack) {
                _lastPingRtt.value = rtt
                _statusMessage.value = "🏓 PONG! Respuesta en ${rtt}ms"
                rtt
            } else {
                _lastPingRtt.value = null
                _statusMessage.value = "❌ Timeout: Sin respuesta de la tablet"
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando ping: ${e.message}", e)
            _statusMessage.value = "Error en Ping: ${e.message}"
            null
        } finally {
            _isPinging.value = false
        }
    }

    private suspend fun awaitCommandAck(cmdId: String, maxTries: Int = 12, delayMs: Long = 350L): Boolean {
        for (i in 0 until maxTries) {
            delay(delayMs)
            val rows = tursoClient.query("SELECT status FROM commands WHERE command_id = ?;", listOf(cmdId))
            if (rows.isNotEmpty() && rows.first()["status"]?.toString() == "EXECUTED") {
                return true
            }
        }
        return false
    }

    companion object {
        private const val TAG = "TursoParentManager"
        private const val POLL_INTERVAL_MS = 1500L

        @Volatile
        private var INSTANCE: TursoParentManager? = null

        fun getInstance(context: Context): TursoParentManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TursoParentManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
