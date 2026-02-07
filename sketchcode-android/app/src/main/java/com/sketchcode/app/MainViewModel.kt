package com.sketchcode.app

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sketchcode.app.network.SketchCodeClient
import com.sketchcode.app.network.CodeUpdate
import com.sketchcode.app.network.OpenFileInfo
import com.sketchcode.app.network.OpenFilesUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import android.util.Base64

data class ConnectionInfo(
    val host: String,
    val port: Int,
    val token: String
)

enum class AppScreen {
    SCANNER,
    SKETCH
}

data class AppState(
    val screen: AppScreen = AppScreen.SCANNER,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val currentCode: CodeUpdate? = null,
    val openFiles: List<OpenFileInfo> = emptyList(),
    val activeFile: String = "",
    val sendingAnnotation: Boolean = false,
    val annotationSent: Boolean = false,
    val error: String? = null
)

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var client: SketchCodeClient? = null

    /** Code content cached per filename, so we can capture annotated files that aren't active */
    val codeCache = mutableMapOf<String, CodeUpdate>()

    fun onQrScanned(url: String) {
        val info = parseConnectionUrl(url) ?: run {
            _state.value = _state.value.copy(error = "Invalid QR code")
            return
        }
        connect(info)
    }

    fun onManualConnect(host: String, port: Int, token: String) {
        connect(ConnectionInfo(host, port, token))
    }

    private fun connect(info: ConnectionInfo) {
        _state.value = _state.value.copy(connecting = true, error = null)

        client = SketchCodeClient(
            host = info.host,
            port = info.port,
            token = info.token,
            onConnected = {
                _state.value = _state.value.copy(
                    connected = true,
                    connecting = false,
                    screen = AppScreen.SKETCH
                )
            },
            onDisconnected = { reason ->
                _state.value = _state.value.copy(
                    connected = false,
                    connecting = false,
                    error = "Disconnected: $reason"
                )
            },
            onCodeUpdate = { code ->
                codeCache[code.filename] = code
                _state.value = _state.value.copy(currentCode = code)
            },
            onOpenFiles = { update ->
                _state.value = _state.value.copy(
                    openFiles = update.files,
                    activeFile = update.activeFile
                )
            },
            onError = { error ->
                _state.value = _state.value.copy(
                    error = error,
                    connecting = false
                )
            }
        )
        client?.connect()
    }

    fun selectFile(file: OpenFileInfo) {
        client?.sendFileSelect(file.filename, file.fullPath)
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        _state.value = AppState()
    }

    /**
     * Send annotations for multiple files. Each entry is a (bitmap, filename) pair.
     * Voice text is attached to the first annotation only.
     */
    fun sendAnnotations(captures: List<Pair<Bitmap, String>>, voiceText: String) {
        if (_state.value.sendingAnnotation) return
        if (captures.isEmpty()) return

        _state.value = _state.value.copy(sendingAnnotation = true)

        viewModelScope.launch {
            try {
                for ((i, entry) in captures.withIndex()) {
                    val (bitmap, filename) = entry
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                    client?.sendAnnotation(
                        sketchImageBase64 = base64,
                        voiceTranscription = if (i == 0) voiceText else "",
                        codeSnapshotTimestamp = System.currentTimeMillis(),
                        filename = filename
                    )
                }

                _state.value = _state.value.copy(
                    sendingAnnotation = false,
                    annotationSent = true
                )

                kotlinx.coroutines.delay(2000)
                _state.value = _state.value.copy(annotationSent = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    sendingAnnotation = false,
                    error = "Failed to send: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun parseConnectionUrl(url: String): ConnectionInfo? {
        return try {
            val uri = java.net.URI(url)
            val token = uri.query?.split("&")
                ?.find { it.startsWith("token=") }
                ?.substringAfter("token=")
                ?: return null
            ConnectionInfo(
                host = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 9876,
                token = token
            )
        } catch (e: Exception) {
            null
        }
    }
}
