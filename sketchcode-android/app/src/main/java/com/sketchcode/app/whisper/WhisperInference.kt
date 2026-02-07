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
 * Runs Whisper-Large-V3-Turbo inference using ONNX Runtime with QNN Execution Provider.
 *
 * The Qualcomm AI Hub model converts linear layers to Conv2D (NCHW layout).
 * The self-attention KV cache uses a SLIDING WINDOW of fixed size 199:
 *   - Each step, the model concatenates new key/value, then drops index 0 (oldest)
 *   - The attention mask unmasks from the RIGHT side (position 199 first, then 198, etc.)
 *   - Mask value 0.0 = attend, -100.0 (or -inf) = mask
 *
 * Decoder input/output shapes (all fp16 on QNN):
 *   input_ids:        int32 [1, 1]
 *   attention_mask:    fp16 [1, 1, 1, 200]     — unmask right-to-left
 *   position_ids:      int32 [1]
 *   k_cache_self_*_in: fp16 [20, 1, 64, 199]   — sliding window
 *   v_cache_self_*_in: fp16 [20, 1, 199, 64]
 *   k_cache_cross_*:   fp16 [20, 1, 64, 1500]  — from encoder, constant
 *   v_cache_cross_*:   fp16 [20, 1, 1500, 64]
 *   logits:            fp16 [1, 51866, 1, 1]    — NCHW, argmax on dim 1
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
        private const val N_LAYERS = 4
        private const val N_HEADS = 20
        private const val HEAD_DIM = 64
        private const val ENCODER_SEQ = 1500
        private const val CACHE_LEN = 199        // MEAN_DECODE_LEN - 1
        private const val ATTN_MASK_SIZE = 200    // MEAN_DECODE_LEN
        // Mask value for "don't attend": Qualcomm uses -100.0, but -inf also works
        // In fp16: -100.0 = 0xD640, -inf = 0xFC00
        private const val MASK_NEG_FP16: Short = 0xD640.toShort()  // fp16 for -100.0
        private const val MASK_ZERO_FP16: Short = 0x0000           // fp16 for 0.0
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

        // Collect cross-attention KV caches (constant across all decoder steps)
        val crossCaches = mutableMapOf<String, OnnxTensor>()
        for (name in encoder.outputNames) {
            crossCaches[name] = encoderResults.get(name).get() as OnnxTensor
        }
        Log.i(TAG, "Encoder outputs: ${crossCaches.keys}")

        // === Step 2: Autoregressive decoder with sliding window KV cache ===
        val startDec = System.currentTimeMillis()

        // Self-attention cache shapes
        val kCacheSelfShape = longArrayOf(N_HEADS.toLong(), 1, HEAD_DIM.toLong(), CACHE_LEN.toLong())
        val vCacheSelfShape = longArrayOf(N_HEADS.toLong(), 1, CACHE_LEN.toLong(), HEAD_DIM.toLong())
        val kCacheSelfSize = N_HEADS * HEAD_DIM * CACHE_LEN
        val vCacheSelfSize = N_HEADS * CACHE_LEN * HEAD_DIM

        // Initialize caches to zeros
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

            val inputs = mutableMapOf<String, OnnxTensor>()

            // input_ids: int32 [1, 1]
            inputs["input_ids"] = OnnxTensor.createTensor(
                env, IntBuffer.wrap(intArrayOf(currentToken)), longArrayOf(1, 1)
            )

            // position_ids: int32 [1]
            inputs["position_ids"] = OnnxTensor.createTensor(
                env, IntBuffer.wrap(intArrayOf(position)), longArrayOf(1)
            )

            // attention_mask: fp16 [1, 1, 1, 200]
            // Sliding window: unmask from the RIGHT side.
            // Step 0: unmask position 199 only
            // Step 1: unmask positions 198-199
            // Step n: unmask positions (199-n)..199
            inputs["attention_mask"] = createAttentionMask(env, step)

            // Self-attention KV caches
            for (layer in 0 until N_LAYERS) {
                inputs["k_cache_self_${layer}_in"] = selfKCaches[layer]
                inputs["v_cache_self_${layer}_in"] = selfVCaches[layer]
            }

            // Cross-attention KV caches (from encoder, constant)
            for (layer in 0 until N_LAYERS) {
                inputs["k_cache_cross_$layer"] = crossCaches["k_cache_cross_$layer"]!!
                inputs["v_cache_cross_$layer"] = crossCaches["v_cache_cross_$layer"]!!
            }

            if (step == 0) {
                val missing = decoder.inputNames.filter { it !in inputs }
                if (missing.isNotEmpty()) {
                    Log.e(TAG, "Missing decoder inputs: $missing")
                } else {
                    Log.i(TAG, "All ${inputs.size} decoder inputs provided")
                }
            }

            try {
                val decoderResults = decoder.run(inputs)

                // Extract logits: fp16 [1, 51866, 1, 1] (NCHW from Conv2D)
                // Vocab is on dimension 1 (channel dim). Flattened buffer = 51866 values.
                val logitsTensor = decoderResults.get("logits").get() as OnnxTensor
                val logits: FloatArray

                val logitsFloat = logitsTensor.floatBuffer
                if (logitsFloat != null) {
                    logits = FloatArray(VOCAB_SIZE)
                    logitsFloat.rewind()
                    logitsFloat.get(logits)
                } else {
                    val fp16Buf = logitsTensor.shortBuffer
                    logits = fp16ShortBufferToFloatArray(fp16Buf, VOCAB_SIZE)
                }

                // Greedy argmax over vocab dimension
                val nextToken = argmax(logits)

                // Log top-k for debugging on first few steps
                if (step < 6) {
                    val topK = logits.indices.sortedByDescending { logits[it] }.take(5)
                    val topStr = topK.joinToString { "[$it]=${String.format("%.2f", logits[it])}" }
                    Log.i(TAG, "Step $step (pos=$position): input=$currentToken → next=$nextToken " +
                            "(${if (nextToken < WhisperTokenizer.FIRST_SPECIAL) tokenizer.decode(listOf(nextToken)) else "<special:$nextToken>"}) " +
                            "top5: $topStr")
                }

                // Clean up per-step tensors
                inputs["input_ids"]?.close()
                inputs["position_ids"]?.close()
                inputs["attention_mask"]?.close()

                // Extract updated self-attention KV caches (sliding window: oldest dropped)
                val newKCaches = Array(N_LAYERS) { layer ->
                    decoderResults.get("k_cache_self_${layer}_out").get() as OnnxTensor
                }
                val newVCaches = Array(N_LAYERS) { layer ->
                    decoderResults.get("v_cache_self_${layer}_out").get() as OnnxTensor
                }

                // Close old caches
                selfKCaches.forEach { it.close() }
                selfVCaches.forEach { it.close() }
                selfKCaches = newKCaches
                selfVCaches = newVCaches

                position++

                // During prompt phase (steps 0..2), just advance — don't collect output
                if (step < promptTokens.size - 1) continue

                // From step (promptTokens.size - 1) onward, collect generated tokens
                if (nextToken == WhisperTokenizer.EOT) {
                    Log.i(TAG, "EOT at step $step (position $position)")
                    break
                } else if (nextToken >= WhisperTokenizer.FIRST_TIMESTAMP) {
                    // Timestamp token — add to sequence but don't collect as text
                    allTokens.add(nextToken)
                } else {
                    generatedTokens.add(nextToken)
                    allTokens.add(nextToken)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Decoder step $step failed: ${e.message}", e)
                inputs["input_ids"]?.close()
                inputs["position_ids"]?.close()
                inputs["attention_mask"]?.close()
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
     * Create attention mask: fp16 [1, 1, 1, 200]
     *
     * The Qualcomm model uses a sliding window cache. The mask unmasks from the RIGHT:
     *   Step 0: all masked except position 199 → [mask, mask, ..., mask, 0.0]
     *   Step 1: positions 198-199 unmasked     → [mask, mask, ..., 0.0, 0.0]
     *   Step n: positions (199-n)..199 unmasked
     *
     * Mask value: -100.0 (fp16 = 0xD640), matching Qualcomm's reference implementation.
     * Attend value: 0.0 (fp16 = 0x0000).
     */
    private fun createAttentionMask(env: OrtEnvironment, step: Int): OnnxTensor {
        val buf = ByteBuffer.allocateDirect(ATTN_MASK_SIZE * 2).order(ByteOrder.nativeOrder())
        val shorts = buf.asShortBuffer()

        // Start fully masked, then unmask (n+1) positions from the right
        val numUnmasked = step + 1  // step 0 → 1 unmasked, step 1 → 2, etc.
        val firstUnmasked = ATTN_MASK_SIZE - numUnmasked  // unmask from this index to end

        for (i in 0 until ATTN_MASK_SIZE) {
            shorts.put(if (i >= firstUnmasked) MASK_ZERO_FP16 else MASK_NEG_FP16)
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
        val shorts = buf.asShortBuffer()
        shorts.rewind()
        return OnnxTensor.createTensor(env, shorts, shape, OnnxJavaType.FLOAT16)
    }

    /**
     * Fallback: manually convert fp16 ShortBuffer to float32 array.
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
