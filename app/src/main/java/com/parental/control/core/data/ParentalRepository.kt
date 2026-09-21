package com.parental.control.core.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.parental.control.core.model.*
import com.parental.control.core.security.PinSecurityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * Repositorio central de datos y reglas de control parental.
 * Maneja almacenamiento local seguro, estado en tiempo real y sincronización.
 */
class ParentalRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "aegis_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback en caso de dispositivos sin keystore moderno
            context.getSharedPreferences("aegis_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ParentalSettings> = _settings.asStateFlow()

    private val _restrictions = MutableStateFlow<Map<String, AppRestriction>>(emptyMap())
    val restrictions: StateFlow<Map<String, AppRestriction>> = _restrictions.asStateFlow()

    private val _schedules = MutableStateFlow<List<CurfewSchedule>>(emptyList())
    val schedules: StateFlow<List<CurfewSchedule>> = _schedules.asStateFlow()

    private val _telemetry = MutableStateFlow(DeviceTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    init {
        loadRestrictions()
        loadSchedules()
    }

    private fun loadSettings(): ParentalSettings {
        val pinHash = prefs.getString(KEY_PIN_HASH, "") ?: ""
        val pinSalt = prefs.getString(KEY_PIN_SALT, "") ?: ""
        val recoveryCode = prefs.getString(KEY_RECOVERY_CODE, "") ?: ""
        val antiUninstall = prefs.getBoolean(KEY_ANTI_UNINSTALL, true)
        val webFilter = prefs.getBoolean(KEY_WEB_FILTER, true)
        // Resetear bloqueo instantáneo al iniciar para evitar bloqueos accidentales
        prefs.edit().putBoolean(KEY_INSTANT_LOCK, false).apply()
        val instantLock = false
        val unlockUntil = prefs.getLong(KEY_UNLOCK_UNTIL, 0L)
        val pairedId = prefs.getString(KEY_PAIRED_ID, "child-device-01") ?: "child-device-01"
        val deviceRole = prefs.getString(KEY_DEVICE_ROLE, "UNSET") ?: "UNSET"

        return ParentalSettings(
            pinHash = pinHash,
            pinSalt = pinSalt,
            recoveryCode = recoveryCode,
            isAntiUninstallActive = antiUninstall,
            isWebFilterActive = webFilter,
            isInstantLockActive = instantLock,
            temporaryUnlockUntil = unlockUntil,
            pairedChildId = pairedId,
            deviceRole = deviceRole
        )
    }

    fun setDeviceRole(role: String) {
        prefs.edit().putString(KEY_DEVICE_ROLE, role).apply()
        _settings.value = _settings.value.copy(deviceRole = role)
        Log.i(TAG, "Rol del dispositivo actualizado a: $role")
    }


    fun savePin(pin: String): String {
        val salt = PinSecurityManager.generateSalt()
        val hash = PinSecurityManager.hashPin(pin, salt)
        val recovery = PinSecurityManager.generateRecoveryCode()

        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_RECOVERY_CODE, recovery)
            .apply()

        val updated = _settings.value.copy(
            pinHash = hash,
            pinSalt = salt,
            recoveryCode = recovery
        )
        _settings.value = updated
        return recovery
    }

    fun verifyPin(pin: String): Boolean {
        val current = _settings.value
        return PinSecurityManager.verifyPin(pin, current.pinHash, current.pinSalt)
    }

    fun setInstantLock(locked: Boolean) {
        prefs.edit().putBoolean(KEY_INSTANT_LOCK, locked).apply()
        _settings.value = _settings.value.copy(isInstantLockActive = locked)
    }

    fun setTemporaryUnlock(durationMinutes: Int) {
        val until = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        prefs.edit().putLong(KEY_UNLOCK_UNTIL, until).apply()
        _settings.value = _settings.value.copy(temporaryUnlockUntil = until)
        Log.i(TAG, "Pausa temporal CONCEDIDA por $durationMinutes minutos (hasta: $until)")
    }

    fun clearTemporaryUnlock() {
        prefs.edit().putLong(KEY_UNLOCK_UNTIL, 0L).apply()
        _settings.value = _settings.value.copy(temporaryUnlockUntil = 0L)
        Log.i(TAG, "Pausa temporal REVOCADA/LIMPIADA -> Bloqueo total restablecido")
    }

    fun getChineseExamCooldownRemainingMs(): Long {
        val lastTime = prefs.getLong(KEY_CHINESE_EXAM_COOLDOWN, 0L)
        val now = System.currentTimeMillis()
        val elapsed = now - lastTime
        return if (elapsed < CHINESE_EXAM_COOLDOWN_MS) {
            CHINESE_EXAM_COOLDOWN_MS - elapsed
        } else {
            0L
        }
    }

    fun canAttemptChineseExam(): Boolean {
        val remaining = getChineseExamCooldownRemainingMs()
        val isAlreadyUnlocked = _settings.value.isTemporarilyUnlocked
        return remaining <= 0L && !isAlreadyUnlocked
    }

    fun claimChineseExamReward(result: com.parental.control.core.model.YctExamResult): Int {
        val earned = result.earnedMinutes
        if (earned <= 0) return 0
        if (!canAttemptChineseExam()) return 0

        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_CHINESE_EXAM_COOLDOWN, now).apply()

        val minutesToGrant = earned.coerceIn(1, 5)
        setTemporaryUnlock(minutesToGrant)

        val detail = "Examen Oficial YCT 1 aprobado: ${result.totalCorrect}/${result.totalQuestions} (${result.totalScore}/200 pts) -> +${minutesToGrant}m concedidos"
        recordTamperAttempt("🎓 [RETO CHINO] $detail")

        return minutesToGrant
    }

    fun claimVocabularyQuizReward(result: com.parental.control.core.model.VocabQuizResult): Int {
        val earned = result.earnedMinutes
        if (earned <= 0) return 0
        if (!canAttemptChineseExam()) return 0

        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_CHINESE_EXAM_COOLDOWN, now).apply()

        val minutesToGrant = earned.coerceIn(1, 5)
        setTemporaryUnlock(minutesToGrant)

        val detail = "Quiz de Vocabulario YCT 1 aprobado: ${result.correctCount}/${result.totalQuestions} aciertos (${result.stars} ⭐) -> +${minutesToGrant}m recreativos"
        recordTamperAttempt("🎓 [RETO CHINO] $detail")

        return minutesToGrant
    }

    fun setAntiUninstallActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_ANTI_UNINSTALL, active).apply()
        _settings.value = _settings.value.copy(isAntiUninstallActive = active)
    }

    fun setWebFilterActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_WEB_FILTER, active).apply()
        _settings.value = _settings.value.copy(isWebFilterActive = active)
    }

    private fun loadRestrictions() {
        // Inicializa con las apps distractoras más críticas bloqueadas
        val map = mutableMapOf<String, AppRestriction>()
        for (pkg in DistractionConstants.DEFAULT_BLOCKED_PACKAGES) {
            val isBlocked = prefs.getBoolean("blocked_$pkg", true)
            map[pkg] = AppRestriction(
                packageName = pkg,
                appName = DistractionConstants.getFriendlyAppName(pkg),
                isBlocked = isBlocked,
                category = if (pkg.contains("roblox")) AppCategory.GAMES else AppCategory.SOCIAL_VIDEO
            )
        }
        _restrictions.value = map
    }

    fun toggleAppBlock(packageName: String, blocked: Boolean) {
        if (blocked) {
            clearTemporaryUnlock()
        }
        prefs.edit().putBoolean("blocked_$packageName", blocked).apply()
        val current = _restrictions.value.toMutableMap()
        val existing = current[packageName]
        if (existing != null) {
            current[packageName] = existing.copy(isBlocked = blocked)
        } else {
            current[packageName] = AppRestriction(
                packageName = packageName,
                appName = DistractionConstants.getFriendlyAppName(packageName),
                isBlocked = blocked
            )
        }
        _restrictions.value = current
    }

    private fun loadSchedules() {
        // Horarios por defecto desactivados para evitar bloqueos involuntarios
        _schedules.value = listOf(
            CurfewSchedule(
                id = "study_time",
                name = "Horario de Estudio",
                daysOfWeek = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
                startHour = 15,
                startMinute = 0,
                endHour = 18,
                endMinute = 0,
                isEnabled = false
            ),
            CurfewSchedule(
                id = "bed_time",
                name = "Hora de Dormir",
                daysOfWeek = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY),
                startHour = 22,
                startMinute = 0,
                endHour = 7,
                endMinute = 0,
                isEnabled = false
            )
        )
    }

    fun updateTelemetry(update: (DeviceTelemetry) -> DeviceTelemetry) {
        _telemetry.value = update(_telemetry.value)
    }

    fun recordTamperAttempt(detail: String) {
        updateTelemetry {
            it.copy(
                tamperingAttemptsCount = it.tamperingAttemptsCount + 1,
                lastTamperDetail = detail,
                lastHeartbeatTimestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Verifica si una aplicación determinada está restringida en este instante.
     */
    fun isPackageBlocked(packageName: String): Boolean {
        // Paquetes esenciales del sistema (teclados, launchers, llamadas, etc.) NUNCA se bloquean
        if (com.parental.control.core.utils.PermissionHelper.isSystemEssentialPackage(context, packageName)) {
            return false
        }

        val currentSettings = _settings.value

        // 1. Si el padre activó el bloqueo instantáneo, TIENE PRIORIDAD MÁXIMA (anula cualquier pausa)
        if (currentSettings.isInstantLockActive) {
            return true
        }

        // 2. Si hay un desbloqueo temporal activo concedido por el padre, no se bloquea
        if (currentSettings.isTemporarilyUnlocked) {
            return false
        }

        // 1. Coincidencia difusa para TikTok
        if (DistractionConstants.isTikTokPackage(packageName)) {
            val restriction = _restrictions.value[DistractionConstants.PKG_TIKTOK]
            return restriction?.isBlocked ?: true
        }

        // 2. Coincidencia difusa para YouTube
        if (DistractionConstants.isYouTubePackage(packageName)) {
            val restriction = _restrictions.value[DistractionConstants.PKG_YOUTUBE]
            return restriction?.isBlocked ?: true
        }

        // 3. Coincidencia difusa para Facebook
        if (DistractionConstants.isFacebookPackage(packageName)) {
            val restriction = _restrictions.value[DistractionConstants.PKG_FACEBOOK]
            return restriction?.isBlocked ?: true
        }

        // 4. Coincidencia difusa para Instagram
        if (DistractionConstants.isInstagramPackage(packageName)) {
            val restriction = _restrictions.value[DistractionConstants.PKG_INSTAGRAM]
            return restriction?.isBlocked ?: true
        }

        // 5. Verificación directa en el mapa o lista predeterminada
        val restriction = _restrictions.value[packageName]
        val isExplicitlyBlocked = restriction?.isBlocked ?: DistractionConstants.DEFAULT_BLOCKED_PACKAGES.contains(packageName)

        // 6. Revisar si algún horario de toque de queda está activo
        val isCurfewNow = _schedules.value.any { it.isCurfewActive() }

        return if (isCurfewNow) {
            isExplicitlyBlocked
        } else {
            isExplicitlyBlocked
        }
    }

    companion object {
        private const val TAG = "AegisRepository"
        private const val KEY_PIN_HASH = "key_pin_hash"
        private const val KEY_PIN_SALT = "key_pin_salt"
        private const val KEY_RECOVERY_CODE = "key_recovery_code"
        private const val KEY_ANTI_UNINSTALL = "key_anti_uninstall"
        private const val KEY_WEB_FILTER = "key_web_filter"
        private const val KEY_INSTANT_LOCK = "key_instant_lock"
        private const val KEY_UNLOCK_UNTIL = "key_unlock_until"
        private const val KEY_PAIRED_ID = "key_paired_id"
        private const val KEY_DEVICE_ROLE = "key_device_role"
        private const val KEY_CHINESE_EXAM_COOLDOWN = "key_chinese_exam_cooldown"
        private const val CHINESE_EXAM_COOLDOWN_MS = 60 * 60 * 1000L

        @Volatile
        private var INSTANCE: ParentalRepository? = null

        fun getInstance(context: Context): ParentalRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ParentalRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
