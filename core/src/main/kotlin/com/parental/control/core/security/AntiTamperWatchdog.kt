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

        // 1. Si está en el instalador de paquetes intentando desinstalar
        if (pkg.contains("packageinstaller") && (cls.contains("uninstall") || cls.contains("delete"))) {
            return true
        }

        // 2. Si está en Ajustes intentando acceder a la pantalla de la app o administradores
        if (DistractionConstants.CRITICAL_SYSTEM_SETTINGS_PACKAGES.any { pkg.contains(it) }) {
            // Verificar si intenta entrar a pantallas críticas
            for (target in DistractionConstants.SETTINGS_TAMPER_TARGETS) {
                if (cls.contains(target)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Valida si una aplicación o pantalla debe ser totalmente bloqueada en caso de modo estricto.
     */
    fun shouldBlockSettingsCompletely(packageName: CharSequence?, isStrict: Boolean): Boolean {
        if (!isStrict || packageName == null) return false
        val pkg = packageName.toString().lowercase()
        return pkg == "com.android.settings"
    }
}
