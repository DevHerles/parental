package com.parental.control.sync

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.parental.control.core.data.ParentalRepository
import com.parental.control.core.model.DistractionConstants
import com.parental.control.core.turso.TursoClient
import com.parental.control.core.turso.TursoCommand
import com.parental.control.core.turso.TursoStatement
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID

/**
 * Gestor de sincronización en tiempo real con Turso Cloud (LibSQL).
 * Mantiene un ciclo de comunicación de alta frecuencia para reflejar al instante
 * cualquier autorización, bloqueo, pausa o cambio de horario efectuado por el padre.
 */
class TursoSyncManager private constructor(private val context: Context) {

    private val repository: ParentalRepository = ParentalRepository.getInstance(context)
    private val tursoClient: TursoClient = TursoClient()
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var syncJob: Job? = null
    @Volatile
    private var isRunning = false

    val deviceId: String by lazy {
        getOrCreateDeviceId()
    }

    private var lastKnownSettingsVersion: Long = -1L
    private var lastReportedTamperCount: Int = 0
    private var lastUsageSyncTimestamp: Long = 0L
    private val USAGE_SYNC_INTERVAL_MS: Long = 25000L

    private fun getOrCreateDeviceId(): String {
        val prefs = context.getSharedPreferences("aegis_device_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("turso_device_id", null)
        if (id.isNullOrBlank()) {
            val modelSlug = Build.MODEL.replace("\\s+".toRegex(), "_").lowercase()
            id = "child_${modelSlug}_${UUID.randomUUID().toString().take(6)}"
            prefs.edit().putString("turso_device_id", id).apply()
        }
        return id
    }

    fun start() {
        if (isRunning) return
        if (repository.settings.value.isParentMode) {
            Log.i(TAG, "Dispositivo en MODO PADRES: TursoSyncManager de hija omitido.")
            return
        }
        isRunning = true
        Log.i(TAG, "Iniciando TursoSyncManager para dispositivo: $deviceId")


        syncJob = syncScope.launch {
            // Inicializar registro del dispositivo en Turso en el primer inicio
            registerDeviceInitial()

            while (isActive && isRunning) {
                val isInteractive = isScreenOn()
                val sleepInterval = if (isInteractive) ACTIVE_INTERVAL_MS else IDLE_INTERVAL_MS

                try {
                    syncCycle()
                } catch (e: Exception) {
                    Log.w(TAG, "Excepción en ciclo de sincronización Turso: ${e.message}")
                }

                delay(sleepInterval)
            }
        }
    }

    fun stop() {
        isRunning = false
        syncJob?.cancel()
        syncJob = null
        Log.i(TAG, "TursoSyncManager detenido.")
    }

    /**
     * Fuerza una sincronización inmediata (0ms de espera), útil cuando la niña
     * abre una app o está en la pantalla de bloqueo esperando autorización.
     */
    fun forceSync() {
        syncScope.launch {
            try {
                syncCycle()
            } catch (e: Exception) {
                Log.w(TAG, "Error en forceSync: ${e.message}")
            }
        }
    }

    private fun syncCycle() {
        val now = System.currentTimeMillis()
        val batteryInfo = getBatteryStatus()
        val currentApp = repository.telemetry.value.currentForegroundApp

        // 1. Enviar telemetría viva (heartbeat) y verificar presencia
        val updateDeviceStmt = TursoStatement(
            """
            INSERT INTO devices (device_id, family_id, role, device_name, model, battery_percent, is_charging, current_foreground_app, is_online, last_seen_at)
            VALUES (?, 'family_default', 'CHILD', ?, ?, ?, ?, ?, 1, ?)
            ON CONFLICT(device_id) DO UPDATE SET
                battery_percent = excluded.battery_percent,
                is_charging = excluded.is_charging,
                current_foreground_app = excluded.current_foreground_app,
                is_online = 1,
                last_seen_at = excluded.last_seen_at;
            """.trimIndent(),
            listOf(
                deviceId,
                "${Build.MANUFACTURER} ${Build.MODEL}",
                Build.MODEL,
                batteryInfo.first,
                if (batteryInfo.second) 1 else 0,
                currentApp ?: "none",
                now
            )
        )

        // 2. Consultar comandos pendientes dirigidos a este dispositivo
        val fetchCommandsStmt = TursoStatement(
            "SELECT command_id, command_type, payload, created_at FROM commands WHERE target_device_id = ? AND status = 'PENDING' ORDER BY created_at ASC;",
            listOf(deviceId)
        )

        // 3. Consultar configuración en Turso
        val fetchSettingsStmt = TursoStatement(
            "SELECT is_instant_lock_active, is_temporarily_unlocked, temporary_unlock_until, version, updated_at FROM parental_settings WHERE device_id = ?;",
            listOf(deviceId)
        )

        // Ejecutar en pipeline unificado (1 solo viaje de red)
        val results = tursoClient.pipeline(listOf(updateDeviceStmt, fetchCommandsStmt, fetchSettingsStmt))

        // Procesar Comandos
        val commandsResult = results.getOrNull(1)
        if (commandsResult != null && commandsResult.isSuccess) {
            for (row in commandsResult.rows) {
                processRemoteCommand(row)
            }
        }

        // Procesar Ajustes
        val settingsResult = results.getOrNull(2)
        if (settingsResult != null && settingsResult.isSuccess && settingsResult.rows.isNotEmpty()) {
            processRemoteSettings(settingsResult.rows[0])
        }

        // 4. Reportar nuevos intentos de manipulación si hubieron
        reportTamperLogsIfNeeded()

        // 5. Reportar métricas de uso diario y ranking a Turso Cloud
        reportDailyUsageStatsIfNeeded()
    }

    private fun processRemoteCommand(row: Map<String, Any?>) {
        val commandId = row["command_id"]?.toString() ?: return
        val type = row["command_type"]?.toString() ?: return
        val payloadStr = row["payload"]?.toString() ?: "{}"
        val now = System.currentTimeMillis()

        Log.i(TAG, "⚡ COMANDO REMOTO RECIBIDO: $type (id=$commandId)")

        try {
            when (type) {
                TursoCommand.TYPE_LOCK_NOW -> {
                    Log.w(TAG, "Ejecutando orden de BLOQUEO TOTAL INMEDIATO desde la nube")
                    repository.setInstantLock(true)
                }

                TursoCommand.TYPE_UNLOCK_TEMPORARY -> {
                    val minutes = try {
                        val json = JSONObject(payloadStr)
                        json.optInt("minutes", 30)
                    } catch (e: Exception) {
                        payloadStr.toIntOrNull() ?: 30
                    }
                    Log.i(TAG, "Ejecutando orden de DESBLOQUEO TEMPORAL por $minutes minutos")
                    repository.setInstantLock(false)
                    repository.setTemporaryUnlock(minutes)
                }

                TursoCommand.TYPE_CLEAR_LOCK -> {
                    Log.i(TAG, "Ejecutando orden de REANUDAR BLOQUEO / LIMPIAR PAUSA")
                    repository.clearTemporaryUnlock()
                    repository.setInstantLock(false)
                }

                TursoCommand.TYPE_UPDATE_APP -> {
                    try {
                        val json = JSONObject(payloadStr)
                        val pkg = json.optString("package")
                        val blocked = json.optBoolean("blocked", true)
                        if (pkg.isNotBlank()) {
                            Log.i(TAG, "Actualizando restricción remota de app: $pkg -> bloqueado=$blocked")
                            repository.toggleAppBlock(pkg, blocked)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseando payload de UPDATE_APP", e)
                    }
                }

                TursoCommand.TYPE_PING -> {
                    Log.i(TAG, "Ping remoto respondido con éxito.")
                }
            }

            // Marcar comando como ejecutado en Turso
            tursoClient.execute(
                "UPDATE commands SET status = 'EXECUTED', executed_at = ? WHERE command_id = ?;",
                listOf(now, commandId)
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando comando remoto $type", e)
        }
    }

    private fun processRemoteSettings(row: Map<String, Any?>) {
        val version = (row["version"] as? Number)?.toLong() ?: 0L
        val isInstantLock = (row["is_instant_lock_active"] as? Number)?.toInt() == 1
        val unlockUntil = (row["temporary_unlock_until"] as? Number)?.toLong() ?: 0L

        if (version != lastKnownSettingsVersion) {
            lastKnownSettingsVersion = version
            Log.i(TAG, "Actualizando settings locales desde Turso (v=$version): lock=$isInstantLock, unlockUntil=$unlockUntil")

            if (isInstantLock != repository.settings.value.isInstantLockActive) {
                repository.setInstantLock(isInstantLock)
            }

            val now = System.currentTimeMillis()
            if (unlockUntil > now) {
                val remainingMinutes = ((unlockUntil - now) / (60 * 1000L)).toInt().coerceAtLeast(1)
                repository.setTemporaryUnlock(remainingMinutes)
            } else if (unlockUntil == 0L && repository.settings.value.isTemporarilyUnlocked) {
                repository.clearTemporaryUnlock()
            }
        }
    }

    private fun registerDeviceInitial() {
        try {
            val now = System.currentTimeMillis()
            val batteryInfo = getBatteryStatus()
            val statements = listOf(
                TursoStatement(
                    """
                    INSERT INTO devices (device_id, family_id, role, device_name, model, battery_percent, is_charging, is_online, last_seen_at)
                    VALUES (?, 'family_default', 'CHILD', ?, ?, ?, ?, 1, ?)
                    ON CONFLICT(device_id) DO UPDATE SET is_online = 1, last_seen_at = excluded.last_seen_at;
                    """.trimIndent(),
                    listOf(
                        deviceId,
                        "${Build.MANUFACTURER} ${Build.MODEL}",
                        Build.MODEL,
                        batteryInfo.first,
                        if (batteryInfo.second) 1 else 0,
                        now
                    )
                ),
                TursoStatement(
                    """
                    INSERT INTO parental_settings (device_id, is_instant_lock_active, is_temporarily_unlocked, temporary_unlock_until, is_anti_uninstall_active, is_web_filter_active, daily_time_limit_minutes, version, updated_at)
                    VALUES (?, 0, 0, 0, 1, 1, 120, 1, ?)
                    ON CONFLICT(device_id) DO NOTHING;
                    """.trimIndent(),
                    listOf(deviceId, now)
                )
            )
            tursoClient.pipeline(statements)
            Log.i(TAG, "Dispositivo registrado en Turso Cloud: $deviceId")
        } catch (e: Exception) {
            Log.w(TAG, "Error en registro inicial de dispositivo en Turso: ${e.message}")
        }
    }

    private fun reportTamperLogsIfNeeded() {
        val currentTamperCount = repository.telemetry.value.tamperingAttemptsCount
        if (currentTamperCount > lastReportedTamperCount) {
            val newAttempts = currentTamperCount - lastReportedTamperCount
            lastReportedTamperCount = currentTamperCount
            val detail = repository.telemetry.value.lastTamperDetail ?: "Intento de desinstalación/Ajustes interceptado"
            val now = System.currentTimeMillis()

            try {
                tursoClient.execute(
                    "INSERT INTO tamper_logs (log_id, device_id, event_type, detail, timestamp) VALUES (?, ?, 'TAMPER_ATTEMPT', ?, ?);",
                    listOf(UUID.randomUUID().toString(), deviceId, detail, now)
                )
                Log.w(TAG, "Reportado intento de manipulación a Turso Cloud ($newAttempts nuevos)")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun reportDailyUsageStatsIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastUsageSyncTimestamp < USAGE_SYNC_INTERVAL_MS) {
            return
        }
        lastUsageSyncTimestamp = now

        try {
            val currentTamperCount = repository.telemetry.value.tamperingAttemptsCount
            val (usageList, dailyMetrics) = AppUsageTracker.collectDailyUsage(context, currentTamperCount)

            val statements = mutableListOf<TursoStatement>()

            // 1. Sentencia para device_daily_metrics
            statements.add(
                TursoStatement(
                    """
                    INSERT INTO device_daily_metrics (
                        device_id, date, total_screen_time_minutes, unlocks_count,
                        tamper_blocked_count, morning_minutes, afternoon_minutes,
                        evening_minutes, night_minutes, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(device_id, date) DO UPDATE SET
                        total_screen_time_minutes = excluded.total_screen_time_minutes,
                        unlocks_count = excluded.unlocks_count,
                        tamper_blocked_count = excluded.tamper_blocked_count,
                        morning_minutes = excluded.morning_minutes,
                        afternoon_minutes = excluded.afternoon_minutes,
                        evening_minutes = excluded.evening_minutes,
                        night_minutes = excluded.night_minutes,
                        updated_at = excluded.updated_at;
                    """.trimIndent(),
                    listOf(
                        deviceId,
                        dailyMetrics.date,
                        dailyMetrics.totalScreenTimeMinutes,
                        dailyMetrics.unlocksCount,
                        dailyMetrics.tamperBlockedCount,
                        dailyMetrics.morningMinutes,
                        dailyMetrics.afternoonMinutes,
                        dailyMetrics.eveningMinutes,
                        dailyMetrics.nightMinutes,
                        now
                    )
                )
            )

            // 2. Sentencias para app_usage_stats (Top 25)
            for (usage in usageList.take(25)) {
                statements.add(
                    TursoStatement(
                        """
                        INSERT INTO app_usage_stats (
                            device_id, package_name, app_name, date,
                            usage_minutes, last_time_used, category, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(device_id, package_name, date) DO UPDATE SET
                            app_name = excluded.app_name,
                            usage_minutes = excluded.usage_minutes,
                            last_time_used = excluded.last_time_used,
                            category = excluded.category,
                            updated_at = excluded.updated_at;
                        """.trimIndent(),
                        listOf(
                            deviceId,
                            usage.packageName,
                            usage.appName,
                            usage.date,
                            usage.usageMinutes,
                            usage.lastTimeUsed,
                            usage.category,
                            now
                        )
                    )
                )
            }

            if (statements.isNotEmpty()) {
                tursoClient.pipeline(statements)
                Log.d(TAG, "Telemetría de uso diario sincronizada con Turso Cloud (${usageList.size} apps, ${dailyMetrics.totalScreenTimeMinutes}m total)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error sincronizando estadísticas de uso con Turso: ${e.message}")
        }
    }

    private fun getBatteryStatus(): Pair<Int, Boolean> {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
            Pair(pct, isCharging)
        } catch (e: Exception) {
            Pair(100, false)
        }
    }

    private fun isScreenOn(): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isInteractive ?: true
        } catch (e: Exception) {
            true
        }
    }

    companion object {
        private const val TAG = "TursoSync"
        private const val ACTIVE_INTERVAL_MS = 1500L
        private const val IDLE_INTERVAL_MS = 10000L

        @Volatile
        private var INSTANCE: TursoSyncManager? = null

        fun getInstance(context: Context): TursoSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TursoSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
