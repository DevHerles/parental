package com.parental.control

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.parental.control.core.data.ParentalRepository

class ParentalApp : Application() {

    lateinit var repository: ParentalRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Prevenir que cualquier excepción imprevista rompa el proceso en bucle
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
        }

        try {
            repository = ParentalRepository.getInstance(this)
            createNotificationChannels()
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando repositorio en ParentalApp", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "ParentalApp"
        const val CHANNEL_ID = "aegis_protection_channel"
        lateinit var instance: ParentalApp
            private set
    }
}
