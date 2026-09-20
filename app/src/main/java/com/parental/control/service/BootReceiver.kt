package com.parental.control.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

import com.parental.control.core.data.ParentalRepository

/**
 * Receptor de arranque para verificar y sincronizar la protección parental inmediatamente tras reiniciar el teléfono.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Evento de sistema recibido tras arranque: $action")

        try {
            val repo = ParentalRepository.getInstance(context.applicationContext)
            when (action) {
                "com.parental.control.ACTION_CLEAR_PAUSE", "com.parental.control.ACTION_LOCK_NOW" -> {
                    Log.i(TAG, "Comando administrativo recibido: $action -> Revocando pausas y forzando bloqueo inmediato")
                    repo.clearTemporaryUnlock()
                    repo.toggleAppBlock(com.parental.control.core.model.DistractionConstants.PKG_TIKTOK, true)
                }
                "com.parental.control.ACTION_TEMPORARY_UNLOCK" -> {
                    val duration = intent.getIntExtra("duration", 5)
                    Log.i(TAG, "Comando de prueba recibido: $action -> Desbloqueo temporal por $duration minutos")
                    repo.setTemporaryUnlock(duration)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando repositorio en arranque o broadcast", e)
        }
    }

    companion object {
        private const val TAG = "AegisBootReceiver"
    }
}
