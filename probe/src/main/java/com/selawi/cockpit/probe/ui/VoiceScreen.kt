package com.selawi.cockpit.probe.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

@Composable
fun VoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var micGranted by remember { mutableStateOf(hasMic(context)) }
    var micResult by remember { mutableStateOf("Not tested") }
    var level by remember { mutableFloatStateOf(0f) }
    var speech by remember { mutableStateOf("Not tested") }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsStatus by remember { mutableStateOf("Not started") }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    val googleVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        speech = if (result.resultCode == Activity.RESULT_OK && text != null) "Voice screen heard: \"$text\"" else "Voice screen returned nothing (code ${result.resultCode})"
    }
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(Unit) {
        onDispose {
            recognizer?.destroy()
            tts?.shutdown()
        }
    }

    Page("Voice") {
        Text("Microphone", color = Accent, fontSize = 18.sp)
        InfoLine("Permission", if (micGranted) "granted" else "not granted", if (micGranted) Good else Bad)
        ButtonRow {
            if (!micGranted) BigButton("Allow microphone") { permission.launch(Manifest.permission.RECORD_AUDIO) }
            listOf(
                "Mic" to MediaRecorder.AudioSource.MIC,
                "Voice recognition" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
                "Voice call" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            ).forEach { (name, source) ->
                BigButton("Test: $name") {
                    if (!micGranted) {
                        permission.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        micResult = "Recording 3 s from $name… speak now"
                        scope.launch {
                            micResult = withContext(Dispatchers.IO) { recordLevel(source) { level = it } }
                            level = 0f
                        }
                    }
                }
            }
        }
        Note(micResult)
        Box(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(8.dp)).background(Background)) {
            Box(Modifier.fillMaxWidth(level.coerceIn(0f, 1f)).fillMaxHeight().background(Good))
        }

        Text("Speech to text", color = Accent, fontSize = 18.sp)
        ButtonRow {
            listOf("English" to "en-US", "Arabic" to "ar-JO").forEach { (name, tag) ->
                BigButton("Listen: $name") {
                    if (recognizer == null) {
                        speech = "No speech recognition service on this car"
                    } else if (!micGranted) {
                        permission.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        speech = "Listening ($name)…"
                        recognizer.setRecognitionListener(listener { speech = it })
                        recognizer.startListening(recognizeIntent(tag))
                    }
                }
            }
            BigButton("Google voice screen") {
                runCatching { googleVoice.launch(recognizeIntent("en-US")) }
                    .onFailure { speech = "No voice input screen: ${it.message}" }
            }
        }
        Note(speech)

        Text("Text to speech", color = Accent, fontSize = 18.sp)
        ButtonRow {
            listOf(
                "English" to (Locale.US to "Hello. The cockpit voice test is working."),
                "Arabic" to (Locale("ar") to "مرحبا. اختبار الصوت يعمل."),
            ).forEach { (name, pair) ->
                BigButton("Speak: $name") {
                    val (locale, line) = pair
                    val speak = { engine: TextToSpeech ->
                        val support = engine.setLanguage(locale)
                        val code = engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, "probe")
                        ttsStatus = "Engine ${engine.defaultEngine}, $name support ${languageName(support)}, speak ${if (code == TextToSpeech.SUCCESS) "ok" else "failed"}"
                    }
                    val existing = tts
                    if (existing != null) {
                        speak(existing)
                    } else {
                        ttsStatus = "Starting text to speech…"
                        var created: TextToSpeech? = null
                        created = TextToSpeech(context) { status ->
                            if (status == TextToSpeech.SUCCESS) {
                                tts = created
                                created?.let(speak)
                            } else {
                                ttsStatus = "Text to speech failed to start (status $status)"
                            }
                        }
                    }
                }
            }
        }
        Note(ttsStatus)
    }
}

private fun hasMic(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun recognizeIntent(languageTag: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

private fun listener(report: (String) -> Unit) = object : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) = report("Ready, speak now…")
    override fun onBeginningOfSpeech() = report("Hearing you…")
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() = report("Processing…")
    override fun onError(error: Int) = report("Recognition error: ${errorName(error)}")
    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        report(if (text != null) "Heard: \"$text\"" else "Heard nothing")
    }
    override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            ?.takeIf { it.isNotBlank() }?.let { report("… $it") }
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

private fun errorName(code: Int) = when (code) {
    SpeechRecognizer.ERROR_NETWORK -> "network"
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
    SpeechRecognizer.ERROR_AUDIO -> "audio"
    SpeechRecognizer.ERROR_SERVER -> "server"
    SpeechRecognizer.ERROR_CLIENT -> "client"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech heard"
    SpeechRecognizer.ERROR_NO_MATCH -> "no match"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "missing permission"
    else -> "code $code"
}

private fun languageName(result: Int) = when (result) {
    TextToSpeech.LANG_MISSING_DATA -> "missing voice data"
    TextToSpeech.LANG_NOT_SUPPORTED -> "not supported"
    else -> "ok"
}

/** Records three seconds and reports how loud the input was. */
@SuppressLint("MissingPermission")
private fun recordLevel(source: Int, onLevel: (Float) -> Unit): String {
    val rate = 16_000
    val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (minBuffer <= 0) return "Microphone format not supported (code $minBuffer)"
    val record = runCatching {
        AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 4)
    }.getOrElse { return "Could not open microphone: ${it.message}" }
    if (record.state != AudioRecord.STATE_INITIALIZED) {
        record.release()
        return "Microphone could not be opened (maybe in use by the car)"
    }
    val buffer = ShortArray(rate / 10)
    var peak = 0
    var sumSquares = 0.0
    var samples = 0L
    var silentChunks = 0
    var chunks = 0
    try {
        record.startRecording()
        val end = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < end) {
            val n = record.read(buffer, 0, buffer.size)
            if (n <= 0) return "Microphone read failed (code $n)"
            var chunkPeak = 0
            for (i in 0 until n) {
                val v = kotlin.math.abs(buffer[i].toInt())
                if (v > chunkPeak) chunkPeak = v
                sumSquares += v.toDouble() * v
            }
            samples += n
            chunks++
            if (chunkPeak == 0) silentChunks++
            peak = maxOf(peak, chunkPeak)
            onLevel(chunkPeak / 32768f)
        }
    } finally {
        runCatching { record.stop() }
        record.release()
    }
    if (peak == 0) return "Only silence recorded: the microphone may be blocked or used by the car"
    val rms = sqrt(sumSquares / samples)
    return "Works: peak %.1f dBFS, average %.1f dBFS, %d of %d chunks silent".format(
        20 * log10(peak / 32768.0), 20 * log10(rms / 32768.0), silentChunks, chunks,
    )
}
