package com.parental.control.core.security

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Receptor de Administrador de Dispositivo para Aegis Control Parental.
 * Bloquea la desinstalación directa desde el launcher y el menú de ajustes de Android.
 */
class ParentalDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin habilitado con éxito para Aegis.")
        Toast.makeText(context, "Protección Anti-Desinstalación Activa", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Intento de desactivar Device Admin detectado.")
        try {
            // Expulsar de inmediato a HOME para desestimar el diálogo de desactivación
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)

            // Superponer pantalla de bloqueo pidiendo autorización parental
            val lockIntent = Intent(context, com.parental.control.ui.child.LockScreenActivity::class.java).apply {
                putExtra(com.parental.control.ui.child.LockScreenActivity.EXTRA_BLOCKED_PACKAGE, context.packageName)
                putExtra(com.parental.control.ui.child.LockScreenActivity.EXTRA_BLOCK_REASON, "Desactivación de Administrador de Dispositivo Protegida")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(lockIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error repeliendo desactivación en onDisableRequested", e)
        }
        return "¡ATENCIÓN! La desactivación del Administrador de Aegis requiere autorización de los padres. Cualquier intento de evasión queda bloqueado y registrado."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.e(TAG, "Device Admin fue desactivado.")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(TAG, "Intento de contraseña o PIN fallido en el dispositivo.")
    }

    companion object {
        private const val TAG = "ParentalDeviceAdmin"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, ParentalDeviceAdminReceiver::class.java)
        }
    }
}
