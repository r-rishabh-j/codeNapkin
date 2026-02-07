package com.sketchcode.app.whisper

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import ai.onnxruntime.platform.Fp16Conversions
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer

/**
 * Runs Whisper-Large-V3-Turbo inference using ONNX Runtime with QNN Execution Provider
 * for Snapdragon NPU acceleration.
 *
 * The Qualcomm AI Hub models use FLOAT16 tensors and a KV-cache decoder architecture:
 *
 * Encoder inputs:
 *   input_features: fp16 [1, 128, 3000]
 * Encoder outputs:
 *   k_cache_cross_0..3, v_cache_cross_0..3: fp16 cross-attention KV caches
 *
 * Decoder inputs (per step, single-token):
 *   input_ids: int32 [1, 1]
 *   attention_mask: fp16 [1, 1, 1, 200]
 *   position_ids: int32 [1]
 *   k_cache_self_{0..3}_in: fp16 [20, 1, 64, 199]
 *   v_cache_self_{0..3}_in: fp16 [20, 1, 199, 64]
 *   k_cache_cross_{0..3}: fp16 [20, 1, 64, 1500]
 *   v_cache_cross_{0..3}: fp16 [20, 1, 1500, 64]
 * Decoder outputs:
 *   logits: fp16 [1, 51866, 1, 1]
 *   k_cache_self_{0..3}_out, v_cache_self_{0..3}_out
 */
class WhisperInference(
    private val modelManager: ModelManager,
    private val tokenizer: WhisperTokenizer
) {
    companion object {
        private const val TAG = "WhisperInference"
        private const val N_MELS = 128
        private const val N_FRAMES = 3000
        private const val MAX_TOKENS = 200
        private const val VOCAB_SIZE = 51866
        private const val N_LAYERS = 4    // Whisper-Large-V3-Turbo has 4 decoder layers
        private const val N_HEADS = 20
        private const val HEAD_DIM = 64
        private const val ENCODER_SEQ = 1500
        private const val MAX_DEC_SEQ = 199
        private const val ATTN_MASK_SIZE = MAX_DEC_SEQ + 1  // 200
    }

    private var env: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    fun initialize() {
        env = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_VERBOSE)
        Log.i(TAG, "OrtEnvironment created")

        val providers = OrtEnvironment.getAvailableProviders()
        Log.i(TAG, "Available EPs: $providers")

        if (!providers.contains(OrtProvider.QNN)) {
            throw RuntimeException("QNN EP not available! Available: $providers")
        }

        val qnnOpts = mapOf(
            "backend_type" to "htp",
            "htp_performance_mode" to "burst"
        )

        val encoderPath = modelManager.encoderOnnxPath
        val decoderPath = modelManager.decoderOnnxPath

        Log.i(TAG, "Creating encoder session from $encoderPath")
        val encoderOpts = OrtSession.SessionOptions()
        encoderOpts.addQnn(qnnOpts)
        encoderOpts.addConfigEntry("ep.context_file_path", encoderPath)
        encoderSession = env!!.createSession(encoderPath, encoderOpts)
        Log.i(TAG, "Encoder session created!")
        logSessionInfo("Encoder", encoderSession!!)

        Log.i(TAG, "Creating decoder session from $decoderPath")
        val decoderOpts = OrtSession.SessionOptions()
        decoderOpts.addQnn(qnnOpts)
        decoderOpts.addConfigEntry("ep.context_file_path", decoderPath)
        decoderSession = env!!.createSession(decoderPath, decoderOpts)
        Log.i(TAG, "Decoder session created!")
        logSessionInfo("Decoder", decoderSession!!)
    }

    /**
     * Run full Whisper transcription pipeline.
     * @param mel Float array of shape [128 * 3000] (flattened mel spectrogram, float32)
     * @return Transcribed text
     */
    fun transcribe(mel: FloatArray): String {
        val env = this.env ?: throw IllegalStateException("Not initialized")
        val encoder = this.encoderSession ?: throw IllegalStateException("Encoder not loaded")
        val decoder = this.decoderSession ?: throw IllegalStateException("Decoder not loaded")

        // === Step 1: Encoder ===
        val startEnc = System.currentTimeMillis()

        // Convert mel float32 → float16 using ORT's Fp16Conversions utility
        val melFp16 = Fp16Conversions.convertFloatBufferToFp16Buffer(FloatBuffer.wrap(mel))
        val melTensor = OnnxTensor.createTensor(
            env, melFp16, longArrayOf(1, N_MELS.toLong(), N_FRAMES.toLong()),
            OnnxJavaType.FLOAT16
        )

        val encoderInputName = encoder.inputNames.first()
        Log.i(TAG, "Running encoder (input: $encoderInputName)...")
        val encoderResults = encoder.run(mapOf(encoderInputName to melTensor))
        val encTime = System.currentTimeMillis() - startEnc
        Log.i(TAG, "Encoder inference: ${encTime}ms")

        // Collect encoder outputs (cross-attention KV caches) — these stay constant
        val crossCaches = mutableMapOf<String, OnnxTensor>()
        for (name in encoder.outputNames) {
            crossCaches[name] = encoderResults.get(name).get() as OnnxTensor
        }
        Log.i(TAG, "Encoder outputs: ${crossCaches.keys}")

        // === Step 2: Autoregressive decoder with KV cache ===
        val startDec = System.currentTimeMillis()

        // Shape constants
        val kCacheSelfShape = longArrayOf(N_HEADS.toLong(), 1, HEAD_DIM.toLong(), MAX_DEC_SEQ.toLong())
        val vCacheSelfShape = longArrayOf(N_HEADS.toLong(), 1, MAX_DEC_SEQ.toLong(), HEAD_DIM.toLong())
        val kCacheSelfSize = N_HEADS * HEAD_DIM * MAX_DEC_SEQ
        val vCacheSelfSize = N_HEADS * MAX_DEC_SEQ * HEAD_DIM

        // Initialize self-attention KV caches to zeros (fp16)
        var selfKCaches = Array(N_LAYERS) { createZeroFp16Tensor(env, kCacheSelfShape, kCacheSelfSize) }
        var selfVCaches = Array(N_LAYERS) { createZeroFp16Tensor(env, vCacheSelfShape, vCacheSelfSize) }

        // Prompt: SOT, EN, TRANSCRIBE, NO_TIMESTAMPS
        val promptTokens = intArrayOf(
            WhisperTokenizer.SOT,
            WhisperTokenizer.EN,
            WhisperTokenizer.TRANSCRIBE,
            WhisperTokenizer.NO_TIMESTAMPS
        )

        val generatedTokens = mutableListOf<Int>()
        var position = 0
        val allTokens = promptTokens.toMutableList()

        for (step in 0 until MAX_TOKENS + promptTokens.size) {
            if (step >= allTokens.size) break
            val currentToken = allTokens[step]

            // Build decoder inputs
            val inputs = mutableMapOf<String, OnnxTensor>()

            // input_ids: int32 [1, 1]
            inputs["input_ids"] = OnnxTensor.createTensor(
                env, IntBuffer.wrap(intArrayOf(currentToken)), longArrayOf(1, 1)
            )

            // position_ids: int32 [1]
            inputs["position_ids"] = OnnxTensor.createTensor(
                env, IntBuffer.wrap(intArrayOf(position)), longArrayOf(1)
            )

            // attention_mask: fp16 [1, 1, 1, 200] — causal mask
            inputs["attention_mask"] = createAttentionMask(env, position)

            // Self-attention KV caches (updated each step)
            for (layer in 0 until N_LAYERS) {
                inputs["k_cache_self_${layer}_in"] = selfKCaches[layer]
                inputs["v_cache_self_${layer}_in"] = selfVCaches[layer]
            }

            // Cross-attention KV caches (from encoder, constant)
            for (layer in 0 until N_LAYERS) {
                inputs["k_cache_cross_$layer"] = crossCaches["k_cache_cross_$layer"]!!
                inputs["v_cache_cross_$layer"] = crossCaches["v_cache_cross_$layer"]!!
            }

            // Log missing inputs on first step
            if (step == 0) {
                val missing = decoder.inputNames.filter { it !in inputs }
                if (missing.isNotEmpty()) {
                    Log.e(TAG, "Missing decoder inputs: $missing")
                    Log.e(TAG, "Provided: ${inputs.keys}")
                } else {
                    Log.i(TAG, "All ${inputs.size} decoder inputs provided")
                }
            }

            try {
                val decoderResults = decoder.run(inputs)

                // Extract logits: fp16 [1, 51866, 1, 1]
                // Use getFloatBuffer() which auto-converts fp16 → float32
                val logitsTensor = decoderResults.get("logits").get() as OnnxTensor
                val logitsFloat = logitsTensor.floatBuffer
                if (logitsFloat == null) {
                    // Fallback: manually convert via ShortBuffer
                    Log.w(TAG, "getFloatBuffer() returned null, using manual conversion")
                    val fp16Buf = logitsTensor.shortBuffer
                    val logits = fp16ShortBufferToFloatArray(fp16Buf, VOCAB_SIZE)
                    processDecoderStep(logits, step, promptTokens, generatedTokens, allTokens, decoderResults, inputs, selfKCaches, selfVCaches).also {
                        selfKCaches = it.first
                        selfVCaches = it.second
                    }
                } else {
                    // Extract float32 logits — shape is [1, 51866, 1, 1], we need the first 51866 values
                    val logits = FloatArray(VOCAB_SIZE)
                    logitsFloat.rewind()
                    logitsFloat.get(logits)
                    processDecoderStep(logits, step, promptTokens, generatedTokens, allTokens, decoderResults, inputs, selfKCaches, selfVCaches).also {
                        selfKCaches = it.first
                        selfVCaches = it.second
                    }
                }

                position++

                // During prompt phase, don't collect output tokens
                if (step < promptTokens.size - 1) continue

                // Check last generated token for EOT
                if (generatedTokens.isNotEmpty() || step >= promptTokens.size - 1) {
                    val lastToken = if (step < promptTokens.size) -1 else allTokens.lastOrNull() ?: -1
                    // EOT check happens inside processDecoderStep via the allTokens list
                }

            } catch (e: Exception) {
                Log.e(TAG, "Decoder step $step failed: ${e.message}", e)
                inputs["input_ids"]?.close()
                inputs["position_ids"]?.close()
                inputs["attention_mask"]?.close()
                break
            }

            // Check if EOT was the last token added
            if (allTokens.size > promptTokens.size && allTokens.last() == WhisperTokenizer.EOT) {
                Log.i(TAG, "EOT at step $step (position $position)")
                break
            }
        }

        val decTime = System.currentTimeMillis() - startDec
        Log.i(TAG, "Decoder: ${decTime}ms for ${generatedTokens.size} tokens")

        // Cleanup
        selfKCaches.forEach { it.close() }
        selfVCaches.forEach { it.close() }
        melTensor.close()
        encoderResults.close()

        val text = tokenizer.decode(generatedTokens)
        Log.i(TAG, "Transcription: \"$text\"")
        return text
    }

    /**
     * Process one decoder step: extract next token, update KV caches.
     * Returns the new self KV caches.
     */
    private fun processDecoderStep(
        logits: FloatArray,
        step: Int,
        promptTokens: IntArray,
        generatedTokens: MutableList<Int>,
        allTokens: MutableList<Int>,
        decoderResults: OrtSession.Result,
        inputs: Map<String, OnnxTensor>,
        oldKCaches: Array<OnnxTensor>,
        oldVCaches: Array<OnnxTensor>
    ): Pair<Array<OnnxTensor>, Array<OnnxTensor>> {
        // Greedy argmax
        val nextToken = argmax(logits)

        // Clean up per-step input tensors
        inputs["input_ids"]?.close()
        inputs["position_ids"]?.close()
        inputs["attention_mask"]?.close()

        // Extract updated self-attention KV caches
        val newKCaches = Array(N_LAYERS) { layer ->
            decoderResults.get("k_cache_self_${layer}_out").get() as OnnxTensor
        }
        val newVCaches = Array(N_LAYERS) { layer ->
            decoderResults.get("v_cache_self_${layer}_out").get() as OnnxTensor
        }

        // Close old caches
        oldKCaches.forEach { it.close() }
        oldVCaches.forEach { it.close() }

        // During prompt feeding, only advance position (don't collect tokens)
        if (step >= promptTokens.size - 1) {
            if (nextToken == WhisperTokenizer.EOT) {
                allTokens.add(WhisperTokenizer.EOT)
                Log.i(TAG, "Step $step: EOT")
            } else if (nextToken >= WhisperTokenizer.FIRST_TIMESTAMP) {
                // Skip timestamp tokens but still add to sequence
                allTokens.add(nextToken)
            } else {
                generatedTokens.add(nextToken)
                allTokens.add(nextToken)
                if (generatedTokens.size <= 5) {
                    Log.i(TAG, "Step $step: token=$nextToken '${tokenizer.decode(listOf(nextToken))}'")
                }
            }
        }

        return Pair(newKCaches, newVCaches)
    }

    /**
     * Create attention mask: fp16 [1, 1, 1, 200]
     * Positions 0..currentPos → 0.0 (attend), positions > currentPos → -inf (mask)
     */
    private fun createAttentionMask(env: OrtEnvironment, currentPos: Int): OnnxTensor {
        val buf = ByteBuffer.allocateDirect(ATTN_MASK_SIZE * 2).order(ByteOrder.nativeOrder())
        val shorts = buf.asShortBuffer()
        val zeroFp16: Short = 0x0000
        val negInfFp16: Short = 0xFC00.toShort()

        for (i in 0 until ATTN_MASK_SIZE) {
            shorts.put(if (i <= currentPos) zeroFp16 else negInfFp16)
        }
        shorts.rewind()

        return OnnxTensor.createTensor(
            env, shorts, longArrayOf(1, 1, 1, ATTN_MASK_SIZE.toLong()),
            OnnxJavaType.FLOAT16
        )
    }

    /**
     * Create a zero-filled fp16 tensor.
     */
    private fun createZeroFp16Tensor(
        env: OrtEnvironment,
        shape: LongArray,
        numElements: Int
    ): OnnxTensor {
        val buf = ByteBuffer.allocateDirect(numElements * 2).order(ByteOrder.nativeOrder())
        // ByteBuffer.allocateDirect is already zeroed, but be explicit
        val shorts = buf.asShortBuffer()
        shorts.rewind()
        return OnnxTensor.createTensor(env, shorts, shape, OnnxJavaType.FLOAT16)
    }

    /**
     * Fallback: manually convert fp16 ShortBuffer to float32 array
     * using ORT's Fp16Conversions if getFloatBuffer() returns null.
     */
    private fun fp16ShortBufferToFloatArray(fp16: ShortBuffer, count: Int): FloatArray {
        val result = FloatArray(count)
        fp16.rewind()
        for (i in 0 until count) {
            result[i] = Fp16Conversions.fp16ToFloat(fp16.get())
        }
        return result
    }

    private fun argmax(arr: FloatArray): Int {
        var maxIdx = 0
        var maxVal = arr[0]
        for (i in 1 until arr.size) {
            if (arr[i] > maxVal) {
                maxVal = arr[i]
                maxIdx = i
            }
        }
        return maxIdx
    }

    private fun logSessionInfo(name: String, session: OrtSession) {
        Log.i(TAG, "=== $name Session ===")
        Log.i(TAG, "  Inputs:")
        for (inputName in session.inputNames) {
            val info = session.inputInfo[inputName]
            Log.i(TAG, "    $inputName: $info")
        }
        Log.i(TAG, "  Outputs:")
        for (outputName in session.outputNames) {
            val info = session.outputInfo[outputName]
            Log.i(TAG, "    $outputName: $info")
        }
    }

    fun release() {
        encoderSession?.close()
        decoderSession?.close()
        encoderSession = null
        decoderSession = null
        env?.close()
        env = null
        Log.i(TAG, "Resources released")
    }
}
