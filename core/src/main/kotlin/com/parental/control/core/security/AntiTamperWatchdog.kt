package com.parental.control.core.security

import com.parental.control.core.model.DistractionConstants

/**
 * Centinela que detecta intentos de manipulación del sistema operativo,
 * desinstalación forzada, cierre de servicios o alteración de permisos.
 */
object AntiTamperWatchdog {

    /**
     * Determina si la ventana o pantalla que el usuario intenta abrir
     * corresponde a una acción peligrosa (Desinstalación, Desactivación de Accesibilidad, Borrado de Datos).
     */
    fun isTamperAttempt(packageName: CharSequence?, className: CharSequence?): Boolean {
        if (packageName == null) return false
        val pkg = packageName.toString().lowercase()
        val cls = className?.toString()?.lowercase() ?: ""

        // 1. Si está en cualquier instalador/desinstalador de paquetes del sistema
        if (pkg.contains("packageinstaller")) {
            return true
        }

        // 2. Si la clase contiene indicios explícitos de desinstalación en cualquier paquete
        if (cls.contains("uninstall") || cls.contains("uninstaller") || cls.contains("deleteapp") || cls.contains("appuninstall")) {
            return true
        }

        // 3. Si está en Ajustes o Centros de Seguridad intentando acceder a pantallas críticas
        val isSettingsPackage = DistractionConstants.CRITICAL_SYSTEM_SETTINGS_PACKAGES.any { pkg.contains(it) } ||
                pkg.contains("settings") || pkg.contains("safecenter") || pkg.contains("securitycenter")

        if (isSettingsPackage) {
            for (target in DistractionConstants.SETTINGS_TAMPER_TARGETS) {
                if (cls.contains(target)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Evalúa si un texto o descripción visible en un nodo de UI o en un evento de clic
     * revela un intento inminente de desinstalar, detener, desactivar permisos o manipular la app.
     * @param inSettingsOrInstaller indica si el contexto actual es Ajustes o PackageInstaller.
     */
    fun isTamperText(content: CharSequence?, inSettingsOrInstaller: Boolean = false): Boolean {
        if (content.isNullOrBlank()) return false
        val text = content.toString().lowercase()

        // 1. Mención a la aplicación Aegis dentro de Ajustes o PackageInstaller (intento de entrar a detalles o permisos)
        if (inSettingsOrInstaller && (text.contains("aegis") || text.contains("com.parental.control"))) {
            return true
        }

        // 2. Intento de desinstalación explícita (en Launcher, diálogos o ajustes)
        if (text.contains("desinstalar") || text.contains("uninstall") || text.contains("eliminar app") || text.contains("delete app")) {
            return true
        }

        // 3. Intento de desactivar Device Admin o Accesibilidad
        if (text.contains("desactivar y desinstalar") || text.contains("deactivate and uninstall") ||
            text.contains("desactivar esta aplicación") || text.contains("deactivate this app") ||
            text.contains("administradores de dispositivos") || text.contains("device admin apps")) {
            return true
        }

        // 4. Intento de borrado de datos o forzado de detención
        if (text.contains("borrar almacenamiento") || text.contains("clear storage") ||
            text.contains("borrar datos") || text.contains("clear data") ||
            text.contains("forzar detención") || text.contains("force stop")) {
            return true
        }

        return false
    }

    /**
     * Valida si una aplicación o pantalla debe ser totalmente bloqueada en caso de modo estricto.
     */
    fun shouldBlockSettingsCompletely(packageName: CharSequence?, isStrict: Boolean): Boolean {
        if (!isStrict || packageName == null) return false
        val pkg = packageName.toString().lowercase()
        return pkg == "com.android.settings" || pkg.contains("settings")
    }
}
