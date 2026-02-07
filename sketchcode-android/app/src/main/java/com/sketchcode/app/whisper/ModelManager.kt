package com.sketchcode.app.whisper

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages Whisper model files on device storage.
 *
 * Qualcomm AI Hub exports precompiled QNN ONNX with EPContext referencing "./model.bin".
 * Encoder and decoder must be in separate subdirectories, each containing:
 *   model.onnx (EPContext wrapper, ~1-3KB)
 *   model.bin  (compiled NPU graph, hundreds of MB)
 *
 * Layout:
 *   {externalFilesDir}/models/encoder/model.onnx + model.bin
 *   {externalFilesDir}/models/decoder/model.onnx + model.bin
 */
class ModelManager(context: Context) {
    companion object {
        private const val TAG = "ModelManager"
        private const val MODELS_DIR = "models"
    }

    private val modelsDir: File = File(context.getExternalFilesDir(null), MODELS_DIR)
    private val encoderDir: File = File(modelsDir, "encoder")
    private val decoderDir: File = File(modelsDir, "decoder")

    val encoderOnnxPath: String get() = File(encoderDir, "model.onnx").absolutePath
    val decoderOnnxPath: String get() = File(decoderDir, "model.onnx").absolutePath

    init {
        // Create directories so they're owned by the app (not shell)
        // adb push can then write files into app-owned directories
        encoderDir.mkdirs()
        decoderDir.mkdirs()
    }

    /**
     * Check if all model files are present on device.
     */
    fun areModelsReady(): Boolean {
        val encOnnx = File(encoderDir, "model.onnx")
        val encBin = File(encoderDir, "model.bin")
        val decOnnx = File(decoderDir, "model.onnx")
        val decBin = File(decoderDir, "model.bin")

        val files = listOf(encOnnx, encBin, decOnnx, decBin)
        val allPresent = files.all { it.exists() && it.length() > 0 }

        if (!allPresent) {
            Log.w(TAG, "Models not ready. Checking files:")
            files.forEach { f ->
                Log.w(TAG, "  ${f.absolutePath}: exists=${f.exists()}, size=${if (f.exists()) f.length() else 0}")
            }
            // Also log what IS in the models dir
            if (modelsDir.exists()) {
                Log.i(TAG, "Contents of ${modelsDir.absolutePath}:")
                logDirectoryTree(modelsDir, "  ")
            } else {
                Log.w(TAG, "Models directory doesn't exist: ${modelsDir.absolutePath}")
            }
        } else {
            Log.i(TAG, "All model files found:")
            Log.i(TAG, "  Encoder: ${encOnnx.absolutePath} (${encOnnx.length()} bytes), bin=${encBin.length()} bytes")
            Log.i(TAG, "  Decoder: ${decOnnx.absolutePath} (${decOnnx.length()} bytes), bin=${decBin.length()} bytes")
        }
        return allPresent
    }

    private fun logDirectoryTree(dir: File, indent: String) {
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                Log.i(TAG, "$indent${f.name}/")
                logDirectoryTree(f, "$indent  ")
            } else {
                Log.i(TAG, "$indent${f.name} (${f.length()} bytes)")
            }
        }
    }

    fun getStatusMessage(): String {
        return if (areModelsReady()) {
            "Whisper models loaded"
        } else {
            "Models not found. Push via adb:\n" +
            "  adb shell mkdir -p /sdcard/Android/data/com.sketchcode.app/files/models/encoder\n" +
            "  adb shell mkdir -p /sdcard/Android/data/com.sketchcode.app/files/models/decoder\n" +
            "  adb push encoder.onnx .../models/encoder/model.onnx\n" +
            "  adb push encoder.bin .../models/encoder/model.bin\n" +
            "  adb push decoder.onnx .../models/decoder/model.onnx\n" +
            "  adb push decoder.bin .../models/decoder/model.bin"
        }
    }
}
