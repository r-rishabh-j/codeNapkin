package com.sketchcode.app.whisper

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Decodes Whisper token IDs back to text using the BPE vocabulary.
 * Loads vocab.json from assets and implements the bytes_to_unicode reverse mapping
 * used by GPT-2 / Whisper tokenizers.
 */
class WhisperTokenizer(context: Context) {
    companion object {
        private const val TAG = "WhisperTokenizer"

        // Whisper special token IDs
        const val EOT = 50257          // <|endoftext|>
        const val SOT = 50258          // <|startoftranscript|>
        const val EN = 50259           // <|en|>
        const val TRANSCRIBE = 50360   // <|transcribe|>
        const val TRANSLATE = 50359    // <|translate|>
        const val NO_TIMESTAMPS = 50364 // <|notimestamps|>
        const val FIRST_TIMESTAMP = 50365

        // First real text token
        const val FIRST_SPECIAL = 50257
    }

    // Maps token ID → BPE string
    private val idToToken: Map<Int, String>

    // Reverse of bytes_to_unicode(): maps unicode char → byte value
    private val byteDecoder: Map<Char, Int>

    init {
        // Load vocab.json: { "token_string": token_id, ... }
        val vocabJson = context.assets.open("vocab.json").bufferedReader().readText()
        val type = object : TypeToken<Map<String, Int>>() {}.type
        val tokenToId: Map<String, Int> = Gson().fromJson(vocabJson, type)
        idToToken = tokenToId.entries.associate { (k, v) -> v to k }
        Log.i(TAG, "Loaded ${idToToken.size} tokens from vocab.json")

        // Build bytes_to_unicode reverse mapping
        byteDecoder = buildByteDecoder()
    }

    /**
     * Decode a list of token IDs into a text string.
     * Filters out special tokens and applies BPE byte decoding.
     */
    fun decode(tokenIds: List<Int>): String {
        // Filter out special tokens
        val textTokens = tokenIds.filter { it < FIRST_SPECIAL }

        // Map token IDs to BPE strings and concatenate
        val bpeString = textTokens.mapNotNull { idToToken[it] }.joinToString("")

        // Decode BPE bytes to UTF-8 string
        val bytes = ByteArray(bpeString.length)
        var validLen = 0
        for (ch in bpeString) {
            val b = byteDecoder[ch]
            if (b != null) {
                bytes[validLen++] = b.toByte()
            }
        }

        return String(bytes, 0, validLen, Charsets.UTF_8).trim()
    }

    /**
     * Build the reverse of OpenAI's bytes_to_unicode() mapping.
     * This is the standard GPT-2 byte-level BPE encoding table.
     */
    private fun buildByteDecoder(): Map<Char, Int> {
        // bytes_to_unicode() maps byte values 0-255 to unicode code points.
        // Printable ASCII chars map to themselves. Other bytes map to
        // code points starting at 256 to avoid control characters.
        val bs = mutableListOf<Int>()
        val cs = mutableListOf<Int>()

        // Ranges that map to themselves: '!' to '~', non-break space to Latin-1 supplement
        for (b in '!'.code..'~'.code) { bs.add(b); cs.add(b) }
        for (b in '\u00A1'.code..'\u00AC'.code) { bs.add(b); cs.add(b) }
        for (b in '\u00AE'.code..'\u00FF'.code) { bs.add(b); cs.add(b) }

        // Remaining bytes map to 256+
        var n = 0
        for (b in 0..255) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }

        // Reverse: unicode char → byte value
        val decoder = mutableMapOf<Char, Int>()
        for (i in bs.indices) {
            decoder[cs[i].toChar()] = bs[i]
        }
        return decoder
    }
}
