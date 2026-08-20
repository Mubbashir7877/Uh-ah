package com.example.uh_ah.ui.components

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Speaks each question once and reports completion to its Yes button.
 *
 * Android does not expose standardized gender metadata for TTS voices. We first
 * prefer an installed, offline US-English voice whose engine-provided name says
 * "male", then fall back to any offline US-English voice on the device.
 */
@Composable
fun rememberQuestionNarrationComplete(question: String): Boolean {
    val appContext = LocalContext.current.applicationContext
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var engineReady by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    val utteranceId = remember(question) { "survey-question-${question.hashCode()}" }
    val activeUtteranceId = remember { AtomicReference<String?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(appContext) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.configureAmericanVoice()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit

                    override fun onDone(id: String?) {
                        if (id == activeUtteranceId.get()) mainHandler.post { completed = true }
                    }

                    @Deprecated("Deprecated by Android, but required on older engines")
                    override fun onError(id: String?) {
                        if (id == activeUtteranceId.get()) mainHandler.post { completed = true }
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == activeUtteranceId.get()) mainHandler.post { completed = true }
                    }
                })
                engine = tts
                engineReady = true
            } else {
                // Do not permanently trap the user if the device has no TTS engine.
                completed = true
            }
        }

        onDispose {
            engineReady = false
            engine = null
            tts?.stop()
            tts?.shutdown()
        }
    }

    LaunchedEffect(question, engineReady) {
        if (!engineReady) return@LaunchedEffect
        completed = false
        activeUtteranceId.set(utteranceId)
        val result = engine?.speak(
            question,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId
        )
        if (result == TextToSpeech.ERROR) completed = true
    }

    return completed
}

private fun TextToSpeech.configureAmericanVoice() {
    val usVoices = voices.orEmpty()
        .filter { it.locale.language == Locale.US.language && it.locale.country == Locale.US.country }
    val preferred = usVoices.firstOrNull {
        !it.isNetworkConnectionRequired && it.name.contains("male", ignoreCase = true)
    } ?: usVoices.firstOrNull { !it.isNetworkConnectionRequired }
        ?: usVoices.firstOrNull()

    if (preferred != null) voice = preferred else language = Locale.US
    setSpeechRate(0.92f)
    setPitch(0.9f)
}
