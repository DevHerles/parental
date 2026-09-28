package com.parental.control.ui.child

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.parental.control.core.data.ParentalRepository
import com.parental.control.core.model.DistractionConstants
import com.parental.control.ui.theme.AegisParentalTheme
import kotlinx.coroutines.launch

/**
 * Pantalla de bloqueo Activity a pantalla completa.
 * Proporciona un bloqueo 100% infalible sobre cualquier app sin depender exclusivamente
 * de los permisos de superposición WindowManager.
 */
class LockScreenActivity : ComponentActivity() {

    private lateinit var repository: ParentalRepository
    private val blockedAppNameState = androidx.compose.runtime.mutableStateOf("Aplicación")
    private val currentBlockedPackageState = androidx.compose.runtime.mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ParentalRepository.getInstance(applicationContext)

        if (intent.getBooleanExtra("EXTRA_RESET_COOLDOWN", false)) {
            repository.resetChineseExamCooldown()
        }

        updateBlockedPackageFromIntent(intent)

        // Si la niña pulsa atrás, forzar ir al Launcher/Home
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                sendToHome()
            }
        })

        // Observar en caliente si el padre autoriza o desbloquea desde Turso Cloud
        lifecycleScope.launch {
            repository.settings.collect { settings ->
                val blockedPkg = currentBlockedPackageState.value
                if (settings.isTemporarilyUnlocked && !settings.isInstantLockActive) {
                    android.util.Log.i("LockScreen", "Autorización del padre detectada -> Cerrando pantalla de bloqueo automáticamente")
                    finish()
                } else if (blockedPkg.isNotEmpty() && !repository.isPackageBlocked(blockedPkg)) {
                    android.util.Log.i("LockScreen", "Paquete desbloqueado -> Cerrando pantalla de bloqueo automáticamente")
                    finish()
                }
            }
        }

        // Forzar sincronización inmediata con Turso Cloud al desplegar el bloqueo
        com.parental.control.sync.TursoSyncManager.getInstance(applicationContext).forceSync()

        setContent {
            AegisParentalTheme {
                LockOverlayContent(
                    blockedAppName = blockedAppNameState.value,
                    onDismissToHome = {
                        sendToHome()
                    },
                    onParentUnlock = { pin, durationMinutes ->
                        if (repository.verifyPin(pin)) {
                            repository.setTemporaryUnlock(durationMinutes)
                            finish()
                            true
                        } else {
                            false
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        updateBlockedPackageFromIntent(newIntent)
    }

    private fun updateBlockedPackageFromIntent(targetIntent: Intent) {
        val packageName = targetIntent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "Aplicación"
        val customReason = targetIntent.getStringExtra(EXTRA_BLOCK_REASON)
        val friendlyName = customReason ?: DistractionConstants.getFriendlyAppName(packageName)
        currentBlockedPackageState.value = packageName
        blockedAppNameState.value = friendlyName

        val blockedPkg = targetIntent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
        if (!blockedPkg.isNullOrEmpty()) {
            try {
                val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                am?.killBackgroundProcesses(blockedPkg)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode) {
            sendToHome()
        }
    }

    override fun onResume() {
        super.onResume()
        isLockScreenVisible = true
    }

    override fun onPause() {
        super.onPause()
        isLockScreenVisible = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isLockScreenVisible = false
    }

    private fun sendToHome() {
        isLockScreenVisible = false
        val blockedPkg = currentBlockedPackageState.value.ifEmpty { intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) }
        if (!blockedPkg.isNullOrEmpty()) {
            try {
                val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                am.killBackgroundProcesses(blockedPkg)
            } catch (e: Exception) {
                // Ignore
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        @Volatile
        var isLockScreenVisible: Boolean = false
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_BLOCK_REASON = "extra_block_reason"
    }
}
