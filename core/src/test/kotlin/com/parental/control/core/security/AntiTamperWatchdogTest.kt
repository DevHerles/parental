package com.parental.control.core.security

import org.junit.Assert.*
import org.junit.Test

class AntiTamperWatchdogTest {

    @Test
    fun `isTamperAttempt detects settings critical screens`() {
        // Pantalla de Desinstalación de apps
        assertTrue(
            AntiTamperWatchdog.isTamperAttempt(
                "com.android.settings",
                "com.android.settings.applications.UninstallAppProgress"
            )
        )

        // Instalador de paquetes intentando desinstalar (Android estándar o Google)
        assertTrue(
            AntiTamperWatchdog.isTamperAttempt(
                "com.android.packageinstaller",
                "com.android.packageinstaller.UninstallerActivity"
            )
        )
        assertTrue(
            AntiTamperWatchdog.isTamperAttempt(
                "com.google.android.packageinstaller",
                "com.google.android.packageinstaller.CleanActivity"
            )
        )

        // Android 14 / 15 Jetpack Compose Settings Platform Architecture (SPA)
        assertTrue(
            AntiTamperWatchdog.isTamperAttempt(
                "com.android.settings",
                "com.android.settings.spa.SpaActivity"
            )
        )

        // Lenovo ZUI SafeCenter
        assertTrue(
            AntiTamperWatchdog.isTamperAttempt(
                "com.zui.safecenter",
                "com.zui.safecenter.appinfo.AppDetailActivity"
            )
        )

        // Pantalla de Administradores de Dispositivo o Accesibilidad
        assertTrue(
            AntiTamperWatchdog.isTamperAttempt(
                "com.android.settings",
                "com.android.settings.Settings\$DeviceAdminSettingsActivity"
            )
        )
        assertTrue(
            AntiTamperWatchdog.isTamperAttempt(
                "com.android.settings",
                "com.android.settings.Settings\$AccessibilitySettingsActivity"
            )
        )
    }

    @Test
    fun `isTamperText detects dangerous keywords`() {
        assertTrue(AntiTamperWatchdog.isTamperText("¿Deseas desinstalar Aegis Control Parental?"))
        assertTrue(AntiTamperWatchdog.isTamperText("Desinstalar"))
        assertTrue(AntiTamperWatchdog.isTamperText("Uninstall"))
        assertTrue(AntiTamperWatchdog.isTamperText("Forzar detención"))
        assertTrue(AntiTamperWatchdog.isTamperText("Force stop"))
        assertTrue(AntiTamperWatchdog.isTamperText("Borrar almacenamiento"))
        assertTrue(AntiTamperWatchdog.isTamperText("Clear storage"))
        assertTrue(AntiTamperWatchdog.isTamperText("Desactivar esta aplicación de administración"))
        assertTrue(AntiTamperWatchdog.isTamperText("Aegis Control Parental", inSettingsOrInstaller = true))
        assertTrue(AntiTamperWatchdog.isTamperText("com.parental.control", inSettingsOrInstaller = true))
        // En el launcher el clic al icono para abrir la app no debe ser bloqueado:
        assertFalse(AntiTamperWatchdog.isTamperText("Aegis Control Parental", inSettingsOrInstaller = false))
    }

    @Test
    fun `isTamperText ignores benign texts`() {
        assertFalse(AntiTamperWatchdog.isTamperText("Redes móviles"))
        assertFalse(AntiTamperWatchdog.isTamperText("Pantalla y brillo"))
        assertFalse(AntiTamperWatchdog.isTamperText("Sonido y vibración"))
        assertFalse(AntiTamperWatchdog.isTamperText("Batería"))
        assertFalse(AntiTamperWatchdog.isTamperText("Calculadora"))
    }

    @Test
    fun `isTamperAttempt does not flag benign apps or launcher`() {
        // Launcher normal
        assertFalse(
            AntiTamperWatchdog.isTamperAttempt(
                "com.google.android.apps.nexuslauncher",
                "com.google.android.apps.nexuslauncher.NexusLauncherActivity"
            )
        )

        // Calculadora o herramientas permitidas
        assertFalse(
            AntiTamperWatchdog.isTamperAttempt(
                "com.google.android.calculator",
                "com.android.calculator2.Calculator"
            )
        )
    }
}
