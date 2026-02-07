package com.sketchcode.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceState(
    val isRecording: Boolean = false,
    val transcription: String = "",
    val interimText: String = "",
    val error: String? = null,
    val supported: Boolean = true
)

class VoiceRecorderService(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    init {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        _state.value = _state.value.copy(supported = available)
    }

    fun toggle() {
        if (_state.value.isRecording) {
            stop()
        } else {
            start()
        }
    }

    fun start() {
        if (!_state.value.supported) return

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = _state.value.copy(isRecording = true, error = null)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(isRecording = false)
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    else -> "Recognition error ($error)"
                }
                _state.value = _state.value.copy(isRecording = false, error = msg)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                val current = _state.value.transcription
                val combined = if (current.isEmpty()) text else "$current $text"
                _state.value = _state.value.copy(
                    transcription = combined.trim(),
                    interimText = "",
                    isRecording = false
                )
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val interim = matches?.firstOrNull() ?: ""
                _state.value = _state.value.copy(interimText = interim)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        _state.value = _state.value.copy(transcription = "", interimText = "")
        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        _state.value = _state.value.copy(isRecording = false)
    }

    fun clearTranscription() {
        _state.value = _state.value.copy(transcription = "", interimText = "")
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
