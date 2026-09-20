package com.parental.control.sync

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.parental.control.core.model.DistractionConstants
import com.parental.control.core.utils.PermissionHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Recolector y analizador de métricas telemétricas de uso de pantalla, ranking de aplicaciones
 * y hábitos digitales del menor para persistencia en Turso Cloud.
 */
object AppUsageTracker {

    private const val TAG = "AppUsageTracker"

    data class AppUsageData(
        val packageName: String,
        val appName: String,
        val date: String,
        val usageMinutes: Int,
        val lastTimeUsed: Long,
        val category: String
    )

    data class DailyMetricsData(
        val date: String,
        val totalScreenTimeMinutes: Int,
        val unlocksCount: Int,
        val tamperBlockedCount: Int,
        val morningMinutes: Int,
        val afternoonMinutes: Int,
        val eveningMinutes: Int,
        val nightMinutes: Int
    )

    // Acumulador en tiempo real en memoria (respaldo ante latencia de UsageStatsManager)
    private val appForegroundStartMillis = ConcurrentHashMap<String, Long>()
    private val liveAccumulatedMillis = ConcurrentHashMap<String, Long>()
    private val localUnlocksCounter = AtomicInteger(0)
    private var lastObservedDate: String = ""

    /**
     * Registra un cambio de foco de aplicación en primer plano desde el Centinela / Accessibility.
     */
    fun recordForegroundTransition(newPackage: String?) {
        val now = System.currentTimeMillis()
        val today = getTodayDateString()
        if (today != lastObservedDate) {
            lastObservedDate = today
            liveAccumulatedMillis.clear()
            appForegroundStartMillis.clear()
            localUnlocksCounter.set(0)
        }

        // Finalizar sesión previa de otras apps
        for ((pkg, startMs) in appForegroundStartMillis) {
            if (pkg != newPackage && startMs > 0L) {
                val duration = (now - startMs).coerceAtLeast(0L)
                liveAccumulatedMillis.merge(pkg, duration) { oldVal, newVal -> oldVal + newVal }
                appForegroundStartMillis.remove(pkg)
            }
        }

        if (!newPackage.isNullOrBlank() && newPackage != "none") {
            appForegroundStartMillis[newPackage] = now
        }
    }

    /**
     * Registra una activación o desbloqueo de la pantalla.
     */
    fun recordUnlock() {
        localUnlocksCounter.incrementAndGet()
    }

    /**
     * Recolecta las métricas consolidadas del día actual.
     */
    fun collectDailyUsage(context: Context, tamperCount: Int): Pair<List<AppUsageData>, DailyMetricsData> {
        val now = System.currentTimeMillis()
        val todayStr = getTodayDateString()

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        val pm = context.packageManager
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val hasUsagePerm = PermissionHelper.isUsageStatsGranted(context)

        val usageMap = mutableMapOf<String, AppUsageData>()
        var morningMins = 0
        var afternoonMins = 0
        var eveningMins = 0
        var nightMins = 0
        var unlocksCount = localUnlocksCounter.get().coerceAtLeast(1)

        if (hasUsagePerm && usm != null) {
            try {
                // 1. Consultar estadísticas de uso agregadas del día
                val statsList: List<UsageStats> = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now) ?: emptyList()
                for (stat in statsList) {
                    val pkg = stat.packageName
                    val durationMs = stat.totalTimeInForeground
                    if (durationMs < 60_000L) continue // Ignorar menos de 1 minuto

                    val minutes = (durationMs / 60_000L).toInt()
                    val lastUsed = stat.lastTimeUsed

                    val (appName, category) = resolveAppInfoAndCategory(pm, pkg)
                    usageMap[pkg] = AppUsageData(
                        packageName = pkg,
                        appName = appName,
                        date = todayStr,
                        usageMinutes = minutes,
                        lastTimeUsed = lastUsed,
                        category = category
                    )
                }

                // 2. Analizar eventos para conteo de desbloqueos y distribución horaria
                val events = usm.queryEvents(startOfDay, now)
                val event = UsageEvents.Event()
                var eventUnlocks = 0

                var lastEventTime = startOfDay
                var currentActivePkg: String? = null

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val evTime = event.timeStamp
                    val evType = event.eventType

                    if (evType == UsageEvents.Event.KEYGUARD_HIDDEN || evType == UsageEvents.Event.SCREEN_INTERACTIVE) {
                        eventUnlocks++
                    }

                    if (evType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        currentActivePkg = event.packageName
                        lastEventTime = evTime
                    } else if (evType == UsageEvents.Event.ACTIVITY_PAUSED || evType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                        if (currentActivePkg != null && evTime > lastEventTime) {
                            val durationMins = ((evTime - lastEventTime) / 60_000L).toInt()
                            if (durationMins > 0) {
                                val evCal = Calendar.getInstance().apply { timeInMillis = lastEventTime }
                                val hour = evCal.get(Calendar.HOUR_OF_DAY)
                                when (hour) {
                                    in 0..5 -> nightMins += durationMins
                                    in 6..11 -> morningMins += durationMins
                                    in 12..17 -> afternoonMins += durationMins
                                    else -> eveningMins += durationMins
                                }
                            }
                        }
                        currentActivePkg = null
                    }
                }

                if (eventUnlocks > 0) {
                    unlocksCount = eventUnlocks
                }

            } catch (e: Exception) {
                Log.w(TAG, "Error consultando UsageStatsManager: ${e.message}")
            }
        }

        // 3. Fallback / Enriquecimiento con acumulador en memoria
        for ((pkg, startMs) in appForegroundStartMillis) {
            val liveSessionMs = (now - startMs).coerceAtLeast(0L)
            val totalLiveMs = (liveAccumulatedMillis[pkg] ?: 0L) + liveSessionMs
            val liveMins = (totalLiveMs / 60_000L).toInt()

            if (liveMins > 0) {
                val existing = usageMap[pkg]
                if (existing == null || liveMins > existing.usageMinutes) {
                    val (appName, category) = resolveAppInfoAndCategory(pm, pkg)
                    usageMap[pkg] = AppUsageData(
                        packageName = pkg,
                        appName = appName,
                        date = todayStr,
                        usageMinutes = maxOf(liveMins, existing?.usageMinutes ?: 0),
                        lastTimeUsed = now,
                        category = category
                    )
                }
            }
        }

        // Calcular minutos totales y balancear curvas si estaban vacías
        val totalScreenMins = usageMap.values.sumOf { it.usageMinutes }
        val sumBucketMins = morningMins + afternoonMins + eveningMins + nightMins
        if (sumBucketMins == 0 && totalScreenMins > 0) {
            // Distribución ponderada natural en caso de no disponer de detalle granular de eventos
            afternoonMins = (totalScreenMins * 0.60).toInt()
            morningMins = (totalScreenMins * 0.25).toInt()
            eveningMins = (totalScreenMins - afternoonMins - morningMins).coerceAtLeast(0)
            nightMins = 0
        }

        val dailyMetrics = DailyMetricsData(
            date = todayStr,
            totalScreenTimeMinutes = totalScreenMins,
            unlocksCount = unlocksCount,
            tamperBlockedCount = tamperCount,
            morningMinutes = morningMins,
            afternoonMinutes = afternoonMins,
            eveningMinutes = eveningMins,
            nightMinutes = nightMins
        )

        val sortedList = usageMap.values.sortedByDescending { it.usageMinutes }
        return Pair(sortedList, dailyMetrics)
    }

    private fun resolveAppInfoAndCategory(pm: PackageManager, packageName: String): Pair<String, String> {
        var appName: String = DistractionConstants.getFriendlyAppName(packageName)
        var appInfo: ApplicationInfo? = null
        try {
            appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.isNotBlank() && label != packageName) {
                appName = label
            }
        } catch (e: Exception) {
            // Ignorar y usar nombre amigable
        }

        val category = categorizeApp(packageName, appName, appInfo)
        return Pair(appName, category)
    }

    fun categorizeApp(packageName: String, appName: String, appInfo: ApplicationInfo? = null): String {
        val p = packageName.lowercase()
        val n = appName.lowercase()

        // 1. Android ApplicationInfo Category Hint (si disponible)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo != null) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> return "GAMES"
                ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_SOCIAL -> return "SOCIAL_VIDEO"
                ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "UTILITIES"
            }
        }

        // 2. Juegos
        if (p.contains("game") || p.contains("puzzle") || p.contains("flow") ||
            p.contains("roblox") || p.contains("minecraft") || p.contains("brawlstars") ||
            p.contains("supercell") || p.contains("clash") || p.contains("king") ||
            p.contains("candy") || p.contains("wood") || n.contains("puzzle") ||
            n.contains("juego") || n.contains("flow") || n.contains("block")
        ) {
            return "GAMES"
        }

        // 3. Redes Sociales y Videos
        if (p.contains("musically") || p.contains("tiktok") || p.contains("trill") ||
            p.contains("youtube") || p.contains("katana") || p.contains("orca") ||
            p.contains("facebook") || p.contains("instagram") || p.contains("snapchat") ||
            p.contains("twitch") || p.contains("netflix") || p.contains("twitter") ||
            p.contains("discord") || p.contains("pinterest") || n.contains("tiktok") ||
            n.contains("youtube") || n.contains("pinterest") || n.contains("video")
        ) {
            return "SOCIAL_VIDEO"
        }

        // 4. Educación
        if (p.contains("duolingo") || p.contains("classroom") || p.contains("khan") ||
            p.contains("dictionary") || p.contains("read") || p.contains("math") ||
            p.contains("educa") || n.contains("aprende") || n.contains("educa") ||
            n.contains("lectura") || n.contains("ingles")
        ) {
            return "EDUCATIONAL"
        }

        // 5. Navegación Web
        if (p.contains("chrome") || p.contains("firefox") || p.contains("browser") ||
            p.contains("opera") || p.contains("brave") || p.contains("duckduckgo") ||
            DistractionConstants.BROWSER_PACKAGES.contains(packageName)
        ) {
            return "NAVIGATION"
        }

        // 6. Utilidades y Sistema
        if (p.contains("calculator") || p.contains("calculadora") || p.contains("clock") ||
            p.contains("reloj") || p.contains("weather") || p.contains("clima") ||
            p.contains("camera") || p.contains("camara") || p.contains("gallery") ||
            p.contains("galeria") || p.contains("settings") || p.contains("launcher") ||
            p.contains("zui") || p.contains("systemui") || p.contains("file") ||
            p.contains("archivos")
        ) {
            return "UTILITIES"
        }

        return "OTHER"
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
