package com.parental.control.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.parental.control.core.data.ParentalRepository
import com.parental.control.core.model.DistractionConstants
import com.parental.control.core.security.AntiTamperWatchdog
import com.parental.control.ui.child.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio de Accesibilidad Centinela.
 *
 * Estrategia anti-flotante/PiP/split y pre-interceptación:
 * - Detección activa con Heartbeat cada 250ms (no depende pasivamente de eventos).
 * - Observador reactivo de StateFlow en ParentalRepository para reaccionar inmediatamente (<50ms).
 * - Pre-interceptación de clics en el Launcher (TYPE_VIEW_CLICKED) para que la app no logre abrir.
 * - Descarte de ventanas flotantes/PiP y retorno a Home.
 */
class ParentalAccessibilityService : AccessibilityService() {

    private lateinit var repository: ParentalRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Anti-rebote de cierres del mismo paquete
    private var lastBlockedPackage: String? = null
    private var lastBlockedTimestamp: Long = 0L

    // Seguimiento de primer plano para detectar apps que pasan a flotante/PiP
    private var lastFocusedPackage: String? = null
    private var pendingFloatingPackage: String? = null
    private var pendingFloatingTimestamp: Long = 0L

    private var repelling = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchdogRunning = false
    private var watchdogTick = 0

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            inspectWindows("watchdog")
            if (watchdogRunning) {
                mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = ParentalRepository.getInstance(applicationContext)
        Log.i(TAG, "Centinela iniciado correctamente.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 25
        }
        serviceInfo = info

        // Observador reactivo de settings (reacciona en caliente si se cancela el desbloqueo temporal o se activa bloqueo)
        serviceScope.launch {
            repository.settings.collect { settings ->
                if (!settings.isTemporarilyUnlocked || settings.isInstantLockActive) {
                    Log.i(TAG, "Cambio reactivo en settings (bloqueo activo o fin de pausa) -> forzar inspección inmediata")
                    lastBlockedPackage = null
                    inspectWindows("settingsChanged")
                }
            }
        }

        // Observador reactivo de restricciones individuales de apps
        serviceScope.launch {
            repository.restrictions.collect {
                Log.i(TAG, "Cambio reactivo en restricciones -> forzar inspección inmediata")
                lastBlockedPackage = null
                inspectWindows("restrictionsChanged")
            }
        }

        startWatchdog()
        Log.i(TAG, "Centinela conectado (Heartbeat ${WATCHDOG_INTERVAL_MS}ms + Reactivo StateFlow + TYPE_VIEW_CLICKED).")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopWatchdog()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWatchdog()
        serviceScope.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        if (packageName == applicationContext.packageName) return

        // 1. Pre-interceptación de clics (Launcher, Ajustes, Diálogos de desinstalación)
        // Se evalúa ANTES de cualquier descarte de launcher o apps del sistema
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            handleViewClicked(event)
            return
        }

        // 2. Detección Anti-Tampering Prioritaria (PackageInstaller, Ajustes, Administradores)
        if (repository.settings.value.isAntiUninstallActive) {
            if (AntiTamperWatchdog.isTamperAttempt(packageName, className)) {
                Log.w(TAG, "Intento de manipulación bloqueado en onAccessibilityEvent: pkg=$packageName, cls=$className")
                repelTamperAttempt(packageName, className, "Ajustes / Desinstalación Protegidos")
                return
            }
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                if (inspectSettingsOrInstallerNodes(rootInActiveWindow, packageName)) {
                    return
                }
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            trackForegroundTransition(packageName)
        }

        // 3. Paquetes esenciales del sistema (IME, Launchers en modo pasivo, Teléfono)
        if (com.parental.control.core.utils.PermissionHelper.isSystemEssentialPackage(applicationContext, packageName)) return

        if (LockScreenActivity.isLockScreenVisible && !repository.isPackageBlocked(packageName)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(packageName, className)
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // Se creó/movió/descartó una ventana (flotante, PiP, split): re-evaluar al instante
                inspectWindows("windowsChanged")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (repository.isPackageBlocked(packageName)) {
                    handleWindowStateChanged(packageName, className)
                } else if (repository.settings.value.isWebFilterActive && DistractionConstants.BROWSER_PACKAGES.contains(packageName)) {
                    inspectBrowserUrl(rootInActiveWindow)
                }
            }
        }
    }

    /**
     * Intercepta clics en los iconos del Launcher, menús contextuales, botones de desinstalación
     * ANTES de que la aplicación o diálogo termine su procesamiento.
     */
    private fun handleViewClicked(event: AccessibilityEvent) {
        try {
            val text = event.text?.joinToString(" ") ?: ""
            val desc = event.contentDescription?.toString() ?: ""
            val sourceText = event.source?.text?.toString() ?: ""
            val sourceDesc = event.source?.contentDescription?.toString() ?: ""
            val clicked = "$text $desc $sourceText $sourceDesc".lowercase()

            // Pre-interceptación Anti-Tampering: si el clic es sobre "Desinstalar", "Borrar datos", "Desactivar", etc.
            if (repository.settings.value.isAntiUninstallActive && AntiTamperWatchdog.isTamperText(clicked)) {
                Log.w(TAG, "Pre-interceptado clic peligroso en UI (desinstalación/ajustes): '$clicked'")
                repelTamperAttempt(event.packageName?.toString() ?: "com.android.settings", clicked, "Desinstalación y Modificación Protegidas")
                return
            }

            if (clicked.contains("tiktok") && isBlocked(DistractionConstants.PKG_TIKTOK)) {
                Log.i(TAG, "Pre-interceptado clic en icono TikTok en UI/Launcher!")
                performGlobalAction(GLOBAL_ACTION_HOME)
                closeAndRepel(DistractionConstants.PKG_TIKTOK, null)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * Rastrea la transición de primer plano. Si una app bloqueada pierde el foco a favor de
     * otra cosa (p. ej. el Launcher), pudo quedar minimizada a ventana flotante o PiP.
     */
    private fun trackForegroundTransition(newPackage: String) {
        val prev = lastFocusedPackage
        lastFocusedPackage = newPackage
        if (prev != null && prev != newPackage && repository.isPackageBlocked(prev)) {
            pendingFloatingPackage = prev
            pendingFloatingTimestamp = System.currentTimeMillis()
            Log.i(TAG, "App bloqueada abandonó el foco -> posible flotante/PiP: $prev")
            if (pendingFloatingPackage != lastBlockedPackage || System.currentTimeMillis() - lastBlockedTimestamp > REBOUND_COOLDOWN_MS) {
                mainHandler.postDelayed({ checkAndRepelFloating() }, 40L)
            }
        }
    }

    private fun handleWindowStateChanged(packageName: String, className: String) {
        repository.updateTelemetry { it.copy(currentForegroundApp = packageName) }

        // 1. Detección Anti-Tampering (Ajustes, Desinstalador, Administrador de Dispositivos)
        if (repository.settings.value.isAntiUninstallActive && AntiTamperWatchdog.isTamperAttempt(packageName, className)) {
            Log.w(TAG, "Intento de manipulación bloqueado: pkg=$packageName, cls=$className")
            repelTamperAttempt(packageName, className, "Ajustes del Sistema Protegidos")
            return
        }

        // 2. Detección de Aplicaciones Bloqueadas en la ventana enfocada
        if (repository.isPackageBlocked(packageName)) {
            if (acquireBlockCooldown(packageName)) {
                Log.i(TAG, "Cerrando app restringida enfocada: $packageName")
                closeAndRepel(packageName, null)
            }
            return
        }

        // 3. Ventanas flotantes / freeform / PiP / divididas
        checkAndRepelFloating()
    }

    /**
     * Resuelve el nombre de paquete de una ventana inspeccionando primero su título (en memoria local, 0ms).
     * NUNCA llama a w.root en ventanas inactivas/flotantes porque causa bloqueos de Binder IPC de 5 segundos.
     */
    private fun getPackageFromWindow(w: AccessibilityWindowInfo): String? {
        val title = w.title?.toString() ?: ""
        if (title.isNotEmpty()) {
            val lower = title.lowercase()
            if (lower.contains("musically") || lower.contains("tiktok") || lower.contains("trill") || lower.contains("aweme")) {
                return DistractionConstants.PKG_TIKTOK
            }
            if (lower.contains("youtube") && !lower.contains("kids")) {
                return DistractionConstants.PKG_YOUTUBE
            }
            if (lower.contains("facebook") || lower.contains("katana") || lower.contains("orca")) {
                return DistractionConstants.PKG_FACEBOOK
            }
            if (lower.contains("instagram")) {
                return DistractionConstants.PKG_INSTAGRAM
            }
            if (lower.contains("roblox")) {
                return DistractionConstants.PKG_ROBLOX
            }
            for (pkg in DistractionConstants.DEFAULT_BLOCKED_PACKAGES) {
                val name = DistractionConstants.getFriendlyAppName(pkg)
                if (lower.contains(pkg.lowercase()) || lower.contains(name.lowercase())) {
                    return pkg
                }
            }
        }

        // Solo si la ventana está activamente enfocada y el título no arrojó nada, consultar root con seguridad
        if (w.isFocused || w.isActive) {
            try {
                val rootPkg = w.root?.packageName?.toString()
                if (!rootPkg.isNullOrEmpty() && rootPkg != applicationContext.packageName) {
                    return rootPkg
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }

    /**
     * Inspección de ventanas (emble del watchdog y de TYPE_WINDOWS_CHANGED).
     * Detecta primero apps bloqueadas enfocadas y después ventanas flotantes/PiP
     * aunque su root sea NULL.
     */
    private fun inspectWindows(reason: String) {
        try {
            val win = windows

            // 0) Detección Anti-Tampering proactiva en ventana enfocada
            if (repository.settings.value.isAntiUninstallActive) {
                for (w in win) {
                    if (w.isFocused || w.isActive) {
                        val pkg = getPackageFromWindow(w) ?: ""
                        if (pkg.contains("packageinstaller")) {
                            Log.w(TAG, "[$reason] Watchdog detectó PackageInstaller enfocado -> Repulsión!")
                            repelTamperAttempt(pkg, "Watchdog PackageInstaller", "Desinstalación Protegida")
                            return
                        }
                    }
                }
            }

            // 1) App bloqueada enfocada o ventana flotante con paquete identificado
            for (w in win) {
                val pkg = getPackageFromWindow(w) ?: continue
                if (pkg == applicationContext.packageName) continue // Nunca bloquear nuestra propia app
                if (!repository.isPackageBlocked(pkg)) continue

                if (isPiP(w) || isFloatingWindow(w)) {
                    Log.w(TAG, "[$reason] Ventana flotante detectada de app bloqueada: $pkg")
                    startRepel(pkg, reason)
                    return
                } else if (w.isFocused || w.isActive) {
                    if (acquireBlockCooldown(pkg)) {
                        Log.i(TAG, "[$reason] App bloqueada enfocada: $pkg")
                        closeAndRepel(pkg, null)
                    }
                    return
                }
            }

            // 2) Ventanas flotantes / PiP residuales o con título genérico
            checkAndRepelFloating(win, reason)
        } catch (e: Exception) {
            // Ignorar excepción al consultar ventanas
        }
    }

    /**
     * Detecta ventanas flotantes/freeform/PiP y lanza su repulsión. No depende de `root`
     * porque la ventana flotante inactiva suele exponer root == null.
     */
    private fun checkAndRepelFloating(cached: List<AccessibilityWindowInfo>? = null, reason: String = "event") {
        val win = cached ?: safeWindows()
        var sawFloat = false

        for (w in win) {
            if (isPiP(w) || isFloatingWindow(w)) {
                sawFloat = true
                val pkg = getPackageFromWindow(w)
                if (pkg != null && pkg != applicationContext.packageName && isBlocked(pkg)) {
                    startRepel(pkg, reason)
                    return
                }
            }
        }

        // Flotante con root ilegible / inexistente: usar la app bloqueada que perdió el foco
        if (sawFloat && pendingFloatingPackage != null) {
            val pkg = pendingFloatingPackage!!
            val recent = System.currentTimeMillis() - pendingFloatingTimestamp < PENDING_WINDOW_MS
            if (recent && isBlocked(pkg)) {
                startRepel(pkg, reason)
            }
        }
    }

    private fun startRepel(packageName: String, reason: String) {
        if (repelling) return
        repelling = true
        Log.w(TAG, "REPULSANDO flotante/PiP/split de $packageName ($reason)")
        closeAndRepel(packageName, null)
        mainHandler.postDelayed({ repelling = false }, REPELLING_LOCK_MS)
    }

    private fun isBlocked(packageName: String): Boolean = try {
        repository.isPackageBlocked(packageName)
    } catch (e: Exception) {
        false
    }

    private fun safeWindows(): List<AccessibilityWindowInfo> = try {
        windows
    } catch (e: Exception) {
        emptyList()
    }

    private fun boundsOf(window: AccessibilityWindowInfo): Rect {
        val r = Rect()
        try {
            window.getBoundsInScreen(r)
        } catch (e: Exception) {
            // bounds vacíos
        }
        return r
    }

    private fun isPiP(window: AccessibilityWindowInfo): Boolean = try {
        window.isInPictureInPictureMode
    } catch (e: Exception) {
        false
    }

    /**
     * Ventana flotante/freeform (MIUI / HyperOS): cualquier ventana de aplicación
     * que no ocupe prácticamente la pantalla completa.
     * Descarta barras de caption decorativas de HyperOS (ancho < 100 o alto < 100).
     */
    private fun isFloatingWindow(window: AccessibilityWindowInfo): Boolean {
        if (isPiP(window)) return false
        return try {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return false
            val b = boundsOf(window)
            if (b.width() <= 0 || b.height() <= 0) return false
            if (b.width() < 100 || b.height() < 100) return false // Filtrar Embedded{Miui Caption...}
            val dm = resources.displayMetrics
            b.width() < dm.widthPixels * 0.96f || b.height() < dm.heightPixels * 0.94f
        } catch (e: Exception) {
            false
        }
    }

    private fun startWatchdog() {
        if (watchdogRunning) return
        watchdogRunning = true
        mainHandler.post(watchdogRunnable)
        Log.i(TAG, "Watchdog de ventanas iniciado ($WATCHDOG_INTERVAL_MS ms).")
    }

    private fun stopWatchdog() {
        watchdogRunning = false
        mainHandler.removeCallbacks(watchdogRunnable)
    }

    /**
     * Anti-rebote: evita re-disparos en ráfaga del mismo paquete y no vuelve a mostrar
     * la pantalla de bloqueo si ya está visible.
     */
    private fun acquireBlockCooldown(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && (now - lastBlockedTimestamp) < REBOUND_COOLDOWN_MS) {
            return false
        }
        lastBlockedPackage = packageName
        lastBlockedTimestamp = now
        return true
    }

    /**
     * Expulsión fulminante de intentos de manipulación, desinstalación o acceso a ajustes sensibles.
     */
    private fun repelTamperAttempt(packageName: String, detail: String, reason: String) {
        try {
            Log.w(TAG, "¡REPULSIÓN ANTI-TAMPERING! Intento detectado: pkg=$packageName ($detail)")
            repository.recordTamperAttempt("Intento de manipulación o desinstalación: $detail")

            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            // 1. Expulsar de inmediato con BACK y HOME para cerrar el diálogo o activity
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)

            // 2. Liquidar procesos de desinstalación de inmediato
            am?.killBackgroundProcesses(packageName)
            am?.killBackgroundProcesses("com.google.android.packageinstaller")
            am?.killBackgroundProcesses("com.android.packageinstaller")
            am?.killBackgroundProcesses("com.miui.packageinstaller")

            // 3. Superponer pantalla de bloqueo al instante (30ms)
            mainHandler.postDelayed({
                launchLockScreen(packageName, reason)
            }, 30L)

            // 4. Barrido de seguridad a 120ms
            mainHandler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }, 120L)
        } catch (e: Exception) {
            Log.e(TAG, "Error en repelTamperAttempt", e)
        }
    }

    /**
     * Inspecciona los nodos de UI en Ajustes y PackageInstaller para detectar textos de desinstalación o referencias a Aegis.
     */
    private fun inspectSettingsOrInstallerNodes(rootNode: AccessibilityNodeInfo?, packageName: String): Boolean {
        if (rootNode == null) return false
        val lowerPkg = packageName.lowercase()
        val isCritical = DistractionConstants.CRITICAL_SYSTEM_SETTINGS_PACKAGES.any { lowerPkg.contains(it) } ||
                lowerPkg.contains("settings") || lowerPkg.contains("safecenter") || lowerPkg.contains("packageinstaller")

        if (!isCritical) return false

        if (hasTamperNode(rootNode)) {
            Log.w(TAG, "Detectado contenido de tamper en UI de $packageName")
            repelTamperAttempt(packageName, "UI Text Tamper", "Ajustes y Desinstalación Protegidos")
            return true
        }
        return false
    }

    private fun hasTamperNode(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 8) return false
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (AntiTamperWatchdog.isTamperText(text) || AntiTamperWatchdog.isTamperText(desc)) {
            return true
        }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            if (hasTamperNode(child, depth + 1)) return true
        }
        return false
    }

    /**
     * Cierre de raíz de la app restringida:
     * - Si es Ventana Flotante / PiP: Gesto flick de 90ms a 0ms SIN invocar HOME antes (evita que la animación del Launcher devore el toque).
     * - Si es Pantalla Completa / Split: HOME + BACK inmediatos.
     * - Pantalla de bloqueo informativa inmediata.
     */
    private fun closeAndRepel(packageName: String, customReason: String?) {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val isFloatOrPip = hasBlockedFloatWindows(packageName)

            if (isFloatOrPip) {
                Log.i(TAG, "Expulsión RÁPIDA de ventana flotante para $packageName (0ms, BACK prioritario)")
                // 1. Matar procesos en segundo plano de inmediato a 0ms
                am?.killBackgroundProcesses(packageName)

                // 2. Destruir foco de inmediato con BACK (si está enfocada se cierra en <50ms)
                performGlobalAction(GLOBAL_ACTION_BACK)

                // 3. Disparar el descarte gestual inmediato a 0ms (flick de 80ms)
                dismissFloatWindows(packageName, customReason)

                // 4. Intento de respaldo a 150ms: si la ventana persiste, segundo barrido con foco + BACK
                mainHandler.postDelayed({
                    try {
                        if (hasBlockedFloatWindows(packageName)) {
                            Log.w(TAG, "Flotante aún detectada a 150ms -> segundo barrido")
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            am?.killBackgroundProcesses(packageName)
                            dismissFloatWindows(packageName, customReason)
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }, 150L)

                // 5. Intento de respaldo final a 300ms
                mainHandler.postDelayed({
                    try {
                        if (hasBlockedFloatWindows(packageName)) {
                            Log.w(TAG, "Flotante aún rebelde a 300ms -> forzando HOME + BACK")
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            am?.killBackgroundProcesses(packageName)
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }, 300L)
            } else {
                // Modo Pantalla Completa / Split-Screen
                Log.i(TAG, "Cierre de app a pantalla completa / split: $packageName")
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
                am?.killBackgroundProcesses(packageName)

                mainHandler.postDelayed({
                    launchLockScreen(packageName, customReason)
                }, 120L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en closeAndRepel", e)
        }
    }

    private fun launchLockScreen(packageName: String, customReason: String?) {
        try {
            if (LockScreenActivity.isLockScreenVisible) return
            val activityIntent = Intent(this, LockScreenActivity::class.java).apply {
                putExtra(LockScreenActivity.EXTRA_BLOCKED_PACKAGE, packageName)
                if (customReason != null) {
                    putExtra(LockScreenActivity.EXTRA_BLOCK_REASON, customReason)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(activityIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando LockScreenActivity", e)
        }
    }

    /**
     * Descarte certero de la ventana flotante/PiP activa de una app bloqueada.
     * Selecciona una ÚNICA ventana candidata para evitar colisiones en dispatchGesture.
     */
    private fun dismissFloatWindows(packageName: String, customReason: String? = null) {
        try {
            val win = windows
            // 1. Buscar la ventana flotante específica de la app o con paquete bloqueado
            val targetWindow = win.firstOrNull { w ->
                isFloatingWindow(w) && (getPackageFromWindow(w) == packageName || (getPackageFromWindow(w) != null && isBlocked(getPackageFromWindow(w)!!)))
            } ?: win.firstOrNull { w ->
                isFloatingWindow(w) && getPackageFromWindow(w) == null
            } ?: win.firstOrNull { w ->
                isPiP(w)
            }

            if (targetWindow != null) {
                dismissFloatWindow(targetWindow, packageName, customReason)
            } else {
                Log.w(TAG, "No se encontró ventana flotante candidata para descartar")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error descartando ventanas flotantes", e)
        }
    }

    private fun dismissFloatWindow(window: AccessibilityWindowInfo, targetPackage: String, customReason: String? = null) {
        try {
            val b = boundsOf(window)
            if (b.width() <= 0 || b.height() <= 0) return
            val dm = resources.displayMetrics

            // 1) Si la ventana es un mini-dock lateral/esquina (pill o thumbnail <350px de ancho)
            if (b.width() < 350) {
                Log.i(TAG, "Expulsando mini-dock lateral/esquina ($b): tap foco + BACK + flick")
                val tapX = b.centerX().toFloat().coerceIn(10f, (dm.widthPixels - 10).toFloat())
                val tapY = b.centerY().toFloat().coerceIn(10f, (dm.heightPixels - 10).toFloat())

                // Dar foco con tap rápido y disparar BACK de inmediato
                dispatchDirectTap(tapX, tapY, 30L) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    mainHandler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        launchLockScreen(targetPackage, customReason)
                    }, 50L)
                }
                return
            }

            // 2) Ventana flotante estándar en HyperOS / MIUI (freeform):
            Log.i(TAG, "Expulsando ventana flotante HyperOS ($b): BACK + flick ascendente total (80ms)")
            performGlobalAction(GLOBAL_ACTION_BACK)

            val startX = b.centerX().toFloat().coerceIn(10f, (dm.widthPixels - 1).toFloat())
            val startY = (b.bottom - 15f).coerceIn(10f, (dm.heightPixels - 1).toFloat())
            val endX = startX
            // Fling ascendente recorriendo toda la ventana hacia el borde superior
            val endY = (b.top - 50f).coerceAtLeast(40f)

            dispatchDirectStroke(startX, startY, endX, endY, 80L) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                mainHandler.postDelayed({
                    launchLockScreen(targetPackage, customReason)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }, 60L)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error al descartar ventana flotante", e)
        }
    }

    private fun dispatchDirectTap(
        x: Float,
        y: Float,
        durationMs: Long = 30L,
        onCompleteAction: (() -> Unit)? = null
    ) {
        try {
            val path = Path()
            path.moveTo(x, y)
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onCompleteAction?.invoke()
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onCompleteAction?.invoke()
                }
            }, mainHandler)
            if (!ok) {
                onCompleteAction?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en dispatchDirectTap", e)
            onCompleteAction?.invoke()
        }
    }

    private fun dispatchDirectStroke(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        onCompleteAction: (() -> Unit)? = null
    ) {
        try {
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()

            val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "Gesto de repulsión completado ($startX, $startY -> $endX, $endY)")
                    onCompleteAction?.invoke()
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Gesto de repulsión cancelado ($startX, $startY -> $endX, $endY)")
                    onCompleteAction?.invoke()
                }
            }, mainHandler)
            Log.i(TAG, "dispatchGesture resultado=$ok ($startX, $startY -> $endX, $endY, ${durationMs}ms)")
            if (!ok) {
                onCompleteAction?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en dispatchDirectStroke", e)
            onCompleteAction?.invoke()
        }
    }

    private fun hasBlockedFloatWindows(targetPackage: String? = null): Boolean {
        return try {
            for (w in windows) {
                if (isPiP(w) || isFloatingWindow(w)) {
                    val pkg = getPackageFromWindow(w)
                    if (pkg != null && isBlocked(pkg)) {
                        if (targetPackage == null || pkg == targetPackage) return true
                    }
                    if (pkg == null && pendingFloatingPackage != null && isBlocked(pendingFloatingPackage!!)) {
                        if (targetPackage == null || pendingFloatingPackage == targetPackage) return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun logWindows(win: List<AccessibilityWindowInfo>) {
        try {
            if (win.isEmpty()) {
                Log.d(TAG, "logWindows: 0 ventanas visibles")
                return
            }
            val sb = StringBuilder("logWindows(${win.size}): ")
            for (w in win) {
                val b = boundsOf(w)
                val pkg = getPackageFromWindow(w) ?: w.title?.toString() ?: "(sin-id)"
                val type = when (w.type) {
                    AccessibilityWindowInfo.TYPE_APPLICATION -> "APP"
                    AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "IME"
                    AccessibilityWindowInfo.TYPE_SYSTEM -> "SYS"
                    AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "A11Y"
                    else -> "T${w.type}"
                }
                sb.append("[")
                    .append(type).append(" ")
                    .append(pkg).append(" ")
                    .append(b.width()).append("x").append(b.height()).append(" ")
                    .append(if (isPiP(w)) "PIP" else if (isFloatingWindow(w)) "FLOAT" else "FULL")
                    .append("] ")
            }
            Log.d(TAG, sb.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error en logWindows", e)
        }
    }

    /**
     * Inspecciona los nodos de la interfaz del navegador en busca de la barra de URL.
     */
    private fun inspectBrowserUrl(rootNode: AccessibilityNodeInfo?) {
        if (rootNode == null) return

        try {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
            for (node in nodes) {
                val text = node.text?.toString()?.lowercase() ?: continue
                checkAndBlockUrl(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspeccionando nodo de URL", e)
        }
    }

    private fun checkAndBlockUrl(url: String) {
        for (domain in DistractionConstants.DEFAULT_BLOCKED_DOMAINS) {
            if (url.contains(domain)) {
                Log.w(TAG, "URL bloqueada detectada en navegador: $url")
                closeAndRepel("Navegador Web", "Sitio Web Bloqueado ($domain)")
                break
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Centinela interrumpido.")
    }

    companion object {
        private const val TAG = "AegisAccessibility"
        private const val WATCHDOG_INTERVAL_MS = 150L
        private const val REBOUND_COOLDOWN_MS = 300L
        private const val GESTURE_DURATION_MS = 90L
        private const val TAP_DURATION_MS = 40L
        private const val REPEL_ATTEMPTS = 4
        private const val KILL_STEP_MS = 100L
        private const val LOCK_DELAY_MS = 100L
        private const val REPELLING_LOCK_MS = 250L
        private const val PENDING_WINDOW_MS = 3000L
    }
}