package com.sketchcode.app.network

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*

data class CodeUpdate(
    val filename: String,
    val code: String,
    val language: String,
    val cursorLine: Int,
    val lineCount: Int,
    val timestamp: Long
)

data class OpenFileInfo(
    val filename: String,
    val fullPath: String
)

data class OpenFilesUpdate(
    val files: List<OpenFileInfo>,
    val activeFile: String
)

class SketchCodeClient(
    private val host: String,
    private val port: Int,
    private val token: String,
    private val onConnected: () -> Unit,
    private val onDisconnected: (String) -> Unit,
    private val onCodeUpdate: (CodeUpdate) -> Unit,
    private val onOpenFiles: (OpenFilesUpdate) -> Unit,
    private val onError: (String) -> Unit
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(java.time.Duration.ofSeconds(30))
        .build()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun connect() {
        val request = Request.Builder()
            .url("ws://$host:$port")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Send auth message
                val authMsg = """{"type":"auth","token":"$token"}"""
                webSocket.send(authMsg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                mainHandler.post { onDisconnected(reason) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mainHandler.post { onError(t.message ?: "Connection failed") }
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    fun sendFileSelect(filename: String, fullPath: String) {
        val msg = mapOf(
            "type" to "file_select",
            "payload" to mapOf(
                "filename" to filename,
                "fullPath" to fullPath
            )
        )
        webSocket?.send(gson.toJson(msg))
    }

    fun sendAnnotation(sketchImageBase64: String, voiceTranscription: String, codeSnapshotTimestamp: Long, filename: String) {
        val msg = mapOf(
            "type" to "annotation",
            "payload" to mapOf(
                "sketchImageBase64" to sketchImageBase64,
                "voiceTranscription" to voiceTranscription,
                "codeSnapshotTimestamp" to codeSnapshotTimestamp,
                "filename" to filename,
                "timestamp" to System.currentTimeMillis()
            )
        )
        webSocket?.send(gson.toJson(msg))
    }

    private fun handleMessage(text: String) {
        try {
            val json = JsonParser.parseString(text).asJsonObject
            val type = json.get("type")?.asString ?: return

            when (type) {
                "status" -> {
                    val status = json.getAsJsonObject("payload")?.get("status")?.asString
                    if (status == "connected") {
                        mainHandler.post { onConnected() }
                    }
                }
                "code_update" -> {
                    val payload = json.getAsJsonObject("payload")
                    val update = CodeUpdate(
                        filename = payload.get("filename")?.asString ?: "",
                        code = payload.get("code")?.asString ?: "",
                        language = payload.get("language")?.asString ?: "",
                        cursorLine = payload.get("cursorLine")?.asInt ?: 0,
                        lineCount = payload.get("lineCount")?.asInt ?: 0,
                        timestamp = payload.get("timestamp")?.asLong ?: 0
                    )
                    mainHandler.post { onCodeUpdate(update) }
                }
                "open_files" -> {
                    val payload = json.getAsJsonObject("payload")
                    val filesArray = payload.getAsJsonArray("files")
                    val files = filesArray.map { el ->
                        val obj = el.asJsonObject
                        OpenFileInfo(
                            filename = obj.get("filename")?.asString ?: "",
                            fullPath = obj.get("fullPath")?.asString ?: ""
                        )
                    }
                    val activeFile = payload.get("activeFile")?.asString ?: ""
                    mainHandler.post { onOpenFiles(OpenFilesUpdate(files, activeFile)) }
                }
            }
        } catch (e: Exception) {
            mainHandler.post { onError("Parse error: ${e.message}") }
        }
    }
}
