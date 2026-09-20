package com.parental.control.ui.child

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.parental.control.core.data.ParentalRepository
import com.parental.control.core.model.DistractionConstants
import com.parental.control.ui.theme.AegisParentalTheme

/**
 * Pantalla de bloqueo Activity a pantalla completa.
 * Proporciona un bloqueo 100% infalible sobre cualquier app sin depender exclusivamente
 * de los permisos de superposición WindowManager.
 */
class LockScreenActivity : ComponentActivity() {

    private lateinit var repository: ParentalRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ParentalRepository.getInstance(applicationContext)

        val packageName = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "Aplicación"
        val customReason = intent.getStringExtra(EXTRA_BLOCK_REASON)
        val friendlyName = customReason ?: DistractionConstants.getFriendlyAppName(packageName)

        // Matar cualquier proceso remanente en segundo plano de la app bloqueada
        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
        if (!blockedPkg.isNullOrEmpty()) {
            try {
                val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                am?.killBackgroundProcesses(blockedPkg)
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Si la niña pulsa atrás, forzar ir al Launcher/Home
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                sendToHome()
            }
        })

        setContent {
            AegisParentalTheme {
                LockOverlayContent(
                    blockedAppName = friendlyName,
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
        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
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
