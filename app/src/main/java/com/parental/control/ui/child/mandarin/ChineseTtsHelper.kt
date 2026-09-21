package com.parental.control.ui.child.mandarin

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

class ChineseTtsHelper(context: Context) : TextToSpeech.OnInitListener {

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.CHINESE)
            }
            tts.setSpeechRate(0.85f)
            tts.setPitch(1.0f)
            isInitialized = true
            Log.i("ChineseTTS", "Motor TTS Chino Mandarín inicializado con éxito.")
        } else {
            Log.w("ChineseTTS", "Error inicializando TTS status=$status")
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        speakOnce(text, onDone)
    }

    fun speakOnce(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank()) {
            onDone?.invoke()
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        if (onDone != null) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        mainHandler.post { onDone() }
                    }
                }
                override fun onError(id: String?) {
                    if (id == utteranceId) {
                        mainHandler.post { onDone() }
                    }
                }
            })
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Protocolo oficial YCT 1: Reproduce el audio 2 veces con una pausa de 1.8 segundos intermedia.
     */
    fun speakTwice(text: String, onDone: (() -> Unit)? = null) {
        speakOnce(text) {
            mainHandler.postDelayed({
                speakOnce(text) {
                    onDone?.invoke()
                }
            }, 1800L)
        }
    }

    fun stop() {
        if (isInitialized) {
            tts.stop()
        }
    }

    fun shutdown() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
