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

        // Instalador de paquetes intentando desinstalar
        assertTrue(
            AntiTamperWatchdog.isTamperAttempt(
                "com.android.packageinstaller",
                "com.android.packageinstaller.UninstallerActivity"
            )
        )
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
