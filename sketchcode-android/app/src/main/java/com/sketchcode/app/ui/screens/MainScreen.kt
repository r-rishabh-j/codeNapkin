package com.sketchcode.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sketchcode.app.AppScreen
import com.sketchcode.app.MainViewModel
import com.sketchcode.app.network.OpenFileInfo
import com.sketchcode.app.service.VoiceRecorderService

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Voice recorder (create once, tied to context)
    val voiceRecorder = remember { VoiceRecorderService(context) }
    val voiceState by voiceRecorder.state.collectAsStateWithLifecycle()

    // Request permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied */ }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose { voiceRecorder.destroy() }
    }

    when (state.screen) {
        AppScreen.SCANNER -> {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                ScannerScreen(
                    onQrScanned = { url -> viewModel.onQrScanned(url) },
                    onManualConnect = { host, port, token -> viewModel.onManualConnect(host, port, token) },
                    isConnecting = state.connecting,
                    error = state.error,
                    modifier = modifier
                )
            }
        }
        AppScreen.SKETCH -> {
            SketchScreen(
                codeUpdate = state.currentCode,
                openFiles = state.openFiles,
                activeFile = state.activeFile,
                codeCache = viewModel.codeCache,
                voiceState = voiceState,
                isSending = state.sendingAnnotation,
                annotationSent = state.annotationSent,
                onSendAll = { captures, voice ->
                    viewModel.sendAnnotations(captures, voice)
                    voiceRecorder.clearTranscription()
                },
                onFileSelect = { file -> viewModel.selectFile(file) },
                onVoiceToggle = { voiceRecorder.toggle() },
                onClearTranscription = { voiceRecorder.clearTranscription() },
                onDisconnect = { viewModel.disconnect() },
                modifier = modifier
            )
        }
    }
}
