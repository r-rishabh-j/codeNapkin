package com.sketchcode.app.service

import android.content.Context
import android.util.Log
import com.sketchcode.app.whisper.AudioCapture
import com.sketchcode.app.whisper.MelSpectrogram
import com.sketchcode.app.whisper.ModelManager
import com.sketchcode.app.whisper.WhisperInference
import com.sketchcode.app.whisper.WhisperTokenizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

/**
 * Voice recording and transcription service using Qualcomm Whisper-Large-V3-Turbo
 * running on-device via ONNX Runtime + QNN NPU execution provider.
 *
 * Pipeline: Mic → 16kHz PCM → Mel Spectrogram (C++ JNI) → Encoder (NPU) → Decoder (NPU) → Text
 *
 * The VoiceState interface is identical to the previous SpeechRecognizer implementation,
 * so the UI layer (SketchScreen, MainScreen) requires no changes.
 */
class VoiceRecorderService(private val context: Context) {
    companion object {
        private const val TAG = "VoiceRecorder"
    }

    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val modelManager = ModelManager(context)
    private val audioCapture = AudioCapture()
    private var melSpectrogram: MelSpectrogram? = null
    private var tokenizer: WhisperTokenizer? = null
    private var inference: WhisperInference? = null
    private var isInitialized = false

    init {
        Log.i(TAG, "VoiceRecorderService init — checking models...")
        val modelsReady = modelManager.areModelsReady()
        if (!modelsReady) {
            val msg = modelManager.getStatusMessage()
            _state.value = _state.value.copy(
                supported = false,
                error = msg
            )
            Log.w(TAG, "Whisper models not available — voice disabled: $msg")
        } else {
            _state.value = _state.value.copy(
                interimText = "Loading Whisper..."
            )
            // Initialize pipeline on background thread
            scope.launch {
                try {
                    Log.i(TAG, "Initializing MelSpectrogram...")
                    melSpectrogram = MelSpectrogram()
                    Log.i(TAG, "Initializing WhisperTokenizer...")
                    tokenizer = WhisperTokenizer(context)
                    Log.i(TAG, "Initializing WhisperInference...")
                    inference = WhisperInference(modelManager, tokenizer!!)
                    inference!!.initialize()
                    isInitialized = true
                    _state.value = _state.value.copy(interimText = "")
                    Log.i(TAG, "Whisper pipeline initialized successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize Whisper: ${e.message}", e)
                    _state.value = _state.value.copy(
                        supported = false,
                        error = "Whisper init failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggle() {
        if (_state.value.isRecording) {
            stop()
        } else {
            start()
        }
    }

    fun start() {
        if (!_state.value.supported) {
            Log.w(TAG, "start() called but not supported: ${_state.value.error}")
            _state.value = _state.value.copy(
                error = _state.value.error ?: "Voice not supported",
                interimText = "Voice not available"
            )
            return
        }
        if (!isInitialized) {
            Log.w(TAG, "start() called but still loading")
            _state.value = _state.value.copy(
                interimText = "Whisper still loading...",
                error = null
            )
            return
        }

        try {
            audioCapture.start()
            _state.value = _state.value.copy(
                transcription = "",
                interimText = "Listening...",
                isRecording = true,
                error = null
            )
            Log.i(TAG, "Recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            _state.value = _state.value.copy(
                error = "Mic error: ${e.message}"
            )
        }
    }

    fun stop() {
        if (!_state.value.isRecording) return

        _state.value = _state.value.copy(
            isRecording = false,
            interimText = "Processing..."
        )

        // Move ENTIRE pipeline (including audioCapture.stop()) off the UI thread.
        // audioCapture.stop() calls recordingThread.join(2000) which blocks,
        // and the subsequent mel + inference are heavy compute.
        scope.launch {
            try {
                // Step 0: Stop recording and get audio (blocks until recording thread finishes)
                Log.i(TAG, "Stopping audio capture...")
                val audio: FloatArray
                try {
                    audio = audioCapture.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "audioCapture.stop() crashed: ${e.message}", e)
                    _state.value = _state.value.copy(
                        interimText = "",
                        error = "Audio stop failed: ${e.message}"
                    )
                    return@launch
                }
                Log.i(TAG, "Recording stopped, ${audio.size} samples (${audio.size / 16000f}s)")

                if (audio.isEmpty()) {
                    _state.value = _state.value.copy(
                        interimText = "",
                        error = "No audio captured"
                    )
                    return@launch
                }

                val startTime = System.currentTimeMillis()

                // Step 1: Compute mel spectrogram (C++ JNI)
                Log.i(TAG, "Computing mel spectrogram for ${audio.size} samples...")
                val mel: FloatArray
                try {
                    mel = melSpectrogram!!.compute(audio)
                } catch (e: Exception) {
                    Log.e(TAG, "Mel spectrogram crashed: ${e.message}", e)
                    _state.value = _state.value.copy(
                        interimText = "",
                        error = "Mel spectrogram failed: ${e.message}"
                    )
                    return@launch
                }
                val melTime = System.currentTimeMillis() - startTime
                Log.i(TAG, "Mel spectrogram: ${melTime}ms, output size=${mel.size}")

                // Step 2: Run encoder + decoder (ONNX Runtime QNN)
                Log.i(TAG, "Running Whisper inference...")
                val text: String
                try {
                    text = inference!!.transcribe(mel)
                } catch (e: Exception) {
                    Log.e(TAG, "Whisper inference crashed: ${e.message}", e)
                    _state.value = _state.value.copy(
                        interimText = "",
                        error = "Inference failed: ${e.message}"
                    )
                    return@launch
                }
                val totalTime = System.currentTimeMillis() - startTime
                Log.i(TAG, "Total transcription: ${totalTime}ms → \"$text\"")

                // Update state with result
                val current = _state.value.transcription
                val combined = if (current.isEmpty()) text else "$current $text"
                _state.value = _state.value.copy(
                    transcription = combined.trim(),
                    interimText = ""
                )
            } catch (e: Exception) {
                Log.e(TAG, "Transcription pipeline failed: ${e.message}", e)
                _state.value = _state.value.copy(
                    interimText = "",
                    error = "Transcription failed: ${e.message}"
                )
            }
        }
    }

    fun clearTranscription() {
        _state.value = _state.value.copy(transcription = "", interimText = "")
    }

    fun destroy() {
        audioCapture.release()
        inference?.release()
        scope.cancel()
        Log.i(TAG, "VoiceRecorderService destroyed")
    }
}
