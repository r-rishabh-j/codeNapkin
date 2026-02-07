package com.sketchcode.app.whisper

/**
 * Kotlin JNI wrapper for the C++ mel spectrogram computation.
 * Computes a 128x3000 log-mel spectrogram from 16kHz PCM audio,
 * matching Whisper-Large-V3-Turbo's expected input format.
 */
class MelSpectrogram {
    companion object {
        init {
            System.loadLibrary("whisper_mel")
        }
    }

    /**
     * Compute mel spectrogram from raw PCM audio samples.
     * @param audio Float array of 16kHz mono PCM samples (will be padded/truncated to 30s)
     * @return Float array of shape [128 * 3000] (flattened row-major mel spectrogram)
     */
    fun compute(audio: FloatArray): FloatArray {
        return nativeComputeMelSpectrogram(audio)
    }

    private external fun nativeComputeMelSpectrogram(audio: FloatArray): FloatArray
}
