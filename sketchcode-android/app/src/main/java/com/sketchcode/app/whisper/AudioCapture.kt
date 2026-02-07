package com.sketchcode.app.whisper

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records 16kHz mono PCM audio using Android's AudioRecord API.
 * Accumulates samples in a buffer until stop() is called, then returns
 * the complete recording as a FloatArray.
 */
class AudioCapture {
    companion object {
        private const val TAG = "AudioCapture"
        private const val SAMPLE_RATE = 16000
        private const val MAX_DURATION_SECONDS = 30
        private const val MAX_SAMPLES = SAMPLE_RATE * MAX_DURATION_SECONDS // 480000
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private val buffer = ArrayList<Float>(MAX_SAMPLES)

    /**
     * Start recording audio. Non-blocking — recording happens on a background thread.
     */
    fun start() {
        if (isRecording.get()) return

        buffer.clear()

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(SAMPLE_RATE) // at least 1 second buffer

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            bufferSize * 4 // float = 4 bytes
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            return
        }

        isRecording.set(true)
        audioRecord?.startRecording()

        recordingThread = Thread({
            val chunk = FloatArray(1024)
            while (isRecording.get() && buffer.size < MAX_SAMPLES) {
                val read = audioRecord?.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING) ?: 0
                if (read > 0) {
                    synchronized(buffer) {
                        val remaining = MAX_SAMPLES - buffer.size
                        val toAdd = minOf(read, remaining)
                        for (i in 0 until toAdd) {
                            buffer.add(chunk[i])
                        }
                    }
                }
            }
            Log.i(TAG, "Recording thread finished, ${buffer.size} samples captured")
        }, "AudioCapture")
        recordingThread?.start()

        Log.i(TAG, "Recording started (16kHz, mono, float)")
    }

    /**
     * Stop recording and return all captured audio as a FloatArray.
     * Safe to call from any thread.
     */
    fun stop(): FloatArray {
        Log.i(TAG, "stop() called, isRecording=${isRecording.get()}")
        isRecording.set(false)

        // Stop AudioRecord FIRST — this unblocks the recording thread if it's in READ_BLOCKING
        try {
            val state = audioRecord?.recordingState
            Log.i(TAG, "AudioRecord state before stop: $state")
            if (state == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
                Log.i(TAG, "AudioRecord stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord.stop() failed: ${e.message}", e)
        }

        // Now wait for recording thread to finish (should exit quickly since AudioRecord is stopped)
        try {
            Log.i(TAG, "Joining recording thread...")
            recordingThread?.join(3000)
            Log.i(TAG, "Recording thread joined")
        } catch (e: InterruptedException) {
            Log.w(TAG, "Recording thread join interrupted: ${e.message}")
        }
        recordingThread = null

        // Release AudioRecord
        try {
            audioRecord?.release()
            Log.i(TAG, "AudioRecord released")
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord.release() failed: ${e.message}", e)
        }
        audioRecord = null

        // Copy buffer to FloatArray
        val result: FloatArray
        synchronized(buffer) {
            result = FloatArray(buffer.size)
            for (i in buffer.indices) {
                result[i] = buffer[i]
            }
            buffer.clear()
        }

        Log.i(TAG, "Recording stopped, returning ${result.size} samples (${result.size / SAMPLE_RATE.toFloat()}s)")
        return result
    }

    /**
     * Release all resources.
     */
    fun release() {
        isRecording.set(false)
        try {
            recordingThread?.join(1000)
        } catch (_: InterruptedException) {}
        recordingThread = null
        audioRecord?.release()
        audioRecord = null
        buffer.clear()
    }
}
