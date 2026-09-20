package com.parental.control.core.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.parental.control.core.security.ParentalDeviceAdminReceiver
import com.parental.control.service.ParentalAccessibilityService

object PermissionHelper {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            for (service in enabledServices) {
                if (service.resolveInfo?.serviceInfo?.packageName == context.packageName) {
                    return true
                }
            }

            // Fallback con Secure Settings
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServicesSetting.contains(context.packageName)
        } catch (e: Exception) {
            return false
        }
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ParentalDeviceAdminReceiver.getComponentName(context))
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isUsageStatsGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    /**
     * Identifica componentes críticos del sistema operativo que NUNCA deben ser bloqueados
     * (Teclados/IME para poder escribir el PIN, Launchers para no romper la pantalla de inicio,
     * llamadas telefónicas de emergencia y la propia aplicación).
     */
    fun isSystemEssentialPackage(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return true
        if (packageName == context.packageName) return true
        if (packageName == "android" || packageName == "com.android.systemui") return true

        val p = packageName.lowercase()

        // 1. Teclados (Input Method Editors - IME)
        if (p.contains("inputmethod") || p.contains("keyboard") || p.contains("gboard") ||
            p.contains("swiftkey") || p.contains("latin") || p.contains("ime") ||
            p.contains("facemoji") || p.contains("sogou") || p.contains("touchtype")
        ) {
            return true
        }
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            val imes = imm?.enabledInputMethodList ?: emptyList()
            if (imes.any { it.packageName.equals(packageName, ignoreCase = true) }) {
                return true
            }
        } catch (e: Exception) {
            // Ignorar excepción
        }

        // 2. Launchers (Pantalla de inicio / Launcher del sistema)
        if (p.contains("launcher") || p == "com.miui.home" || p.contains("home") ||
            p == "com.android.launcher3" || p == "com.google.android.apps.nexuslauncher"
        ) {
            return true
        }
        try {
            val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
            val resolveInfos = context.packageManager.queryIntentActivities(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfos.any { it.activityInfo.packageName.equals(packageName, ignoreCase = true) }) {
                return true
            }
        } catch (e: Exception) {
            // Ignorar excepción
        }

        // 3. Teléfono / Llamadas de emergencia
        if (p.contains("dialer") || p.contains("telecom") || p.contains("incallui") || p == "com.android.phone" || p.contains("emergency")) {
            return true
        }

        return false
    }

    /**
     * Comprueba si el dispositivo es Xiaomi / Redmi / POCO (HyperOS / MIUI).
     */
    fun isXiaomiDevice(): Boolean {
        val man = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return man.contains("xiaomi") || man.contains("redmi") || man.contains("poco") ||
                brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")
    }

    /**
     * Verifica si en MIUI/HyperOS está concedido el permiso especial de mostrar ventanas emergentes en segundo plano (AppOp 10021).
     */
    fun isMiuiBackgroundPopupGranted(context: Context): Boolean {
        if (!isXiaomiDevice()) return true
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val method = appOps.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val mode = method.invoke(appOps, 10021, android.os.Process.myUid(), context.packageName) as Int
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Abre directamente la pantalla de permisos de MIUI/HyperOS para conceder 'Mostrar ventanas emergentes en segundo plano'.
     */
    fun openMiuiPermissionSettings(context: Context) {
        try {
            val intent = android.content.Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                // Ignorar
            }
        }
    }
}
