package com.sketchcode.app

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sketchcode.app.network.SketchCodeClient
import com.sketchcode.app.network.CodeUpdate
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
    val sendingAnnotation: Boolean = false,
    val annotationSent: Boolean = false,
    val error: String? = null
)

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var client: SketchCodeClient? = null

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
                _state.value = _state.value.copy(currentCode = code)
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

    fun disconnect() {
        client?.disconnect()
        client = null
        _state.value = AppState()
    }

    fun sendAnnotation(bitmap: Bitmap, voiceText: String) {
        if (_state.value.sendingAnnotation) return
        val currentCode = _state.value.currentCode ?: return

        _state.value = _state.value.copy(sendingAnnotation = true)

        viewModelScope.launch {
            try {
                // Convert bitmap to base64 JPEG (much smaller than PNG for screenshots)
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                client?.sendAnnotation(
                    sketchImageBase64 = base64,
                    voiceTranscription = voiceText,
                    codeSnapshotTimestamp = currentCode.timestamp
                )

                _state.value = _state.value.copy(
                    sendingAnnotation = false,
                    annotationSent = true
                )

                // Reset after 2 seconds
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
