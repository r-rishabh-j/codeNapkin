package com.sketchcode.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sketchcode.app.network.CodeUpdate
import com.sketchcode.app.service.VoiceState
import com.sketchcode.app.ui.components.DrawingTool
import com.sketchcode.app.ui.components.SketchCanvasView

@Composable
fun SketchScreen(
    codeUpdate: CodeUpdate?,
    voiceState: VoiceState,
    isSending: Boolean,
    annotationSent: Boolean,
    onSend: (Bitmap, String) -> Unit,
    onVoiceToggle: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTool by remember { mutableStateOf(DrawingTool.PEN) }
    var penColor by remember { mutableIntStateOf(AndroidColor.RED) }

    // Native view references
    var sketchViewRef by remember { mutableStateOf<SketchCanvasView?>(null) }
    var containerRef by remember { mutableStateOf<FrameLayout?>(null) }

    // Push tool/color changes to the native view
    LaunchedEffect(currentTool, penColor) {
        sketchViewRef?.currentTool = currentTool
        sketchViewRef?.penColor = penColor
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        // ── Status bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252526))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
            )
            Spacer(Modifier.width(8.dp))
            Text("Connected", color = Color(0xFFCCCCCC), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            codeUpdate?.let {
                Text(
                    it.filename,
                    color = Color(0xFF858585),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onDisconnect,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("Disconnect", fontSize = 11.sp, color = Color(0xFFEF4444))
            }
        }

        // ── Code + Sketch overlay (native Android Views) ──
        AndroidView(
            factory = { context ->
                FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Layer 1: ScrollView with code TextView
                    val codeText = TextView(context).apply {
                        setTextColor(AndroidColor.parseColor("#D4D4D4"))
                        setBackgroundColor(AndroidColor.parseColor("#1E1E1E"))
                        textSize = 12f
                        typeface = Typeface.MONOSPACE
                        setPadding(20, 20, 20, 600)
                        setLineSpacing(0f, 1.3f)
                        tag = "codeText"
                    }
                    val scrollView = ScrollView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        isVerticalScrollBarEnabled = true
                        tag = "scrollView"
                        addView(codeText)
                    }
                    addView(scrollView)

                    // Layer 2: SketchCanvasView on top (transparent, handles touch)
                    val sketchView = SketchCanvasView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                    addView(sketchView)

                    sketchViewRef = sketchView
                    containerRef = this
                }
            },
            update = { frameLayout ->
                // Update code text content
                val codeTextView = frameLayout.findViewWithTag<TextView>("codeText")
                if (codeUpdate != null && codeTextView != null) {
                    val lines = codeUpdate.code.split("\n")
                    val padWidth = lines.size.toString().length
                    val numbered = lines.mapIndexed { i, line ->
                        "${(i + 1).toString().padStart(padWidth)}  $line"
                    }.joinToString("\n")

                    if (codeTextView.text.toString() != numbered) {
                        codeTextView.text = numbered
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        // ── Voice transcription bar ──
        if (voiceState.isRecording || voiceState.transcription.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (voiceState.isRecording) Color(0xFF3B1A1A) else Color(0xFF2D2D30)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎙", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (voiceState.isRecording) {
                        voiceState.interimText.ifEmpty { "Listening..." }
                    } else {
                        voiceState.transcription
                    },
                    color = Color(0xFFD4D4D4),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Bottom Toolbar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252526))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: drawing tools
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pen
                ToolChip("✏️", currentTool == DrawingTool.PEN) {
                    currentTool = DrawingTool.PEN
                }
                // Eraser
                ToolChip("⌫", currentTool == DrawingTool.ERASER) {
                    currentTool = DrawingTool.ERASER
                }
                // Color dots
                listOf(
                    AndroidColor.RED,
                    AndroidColor.YELLOW,
                    AndroidColor.parseColor("#00FF00"),
                    AndroidColor.CYAN,
                ).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(color.toLong() or 0xFF000000L))
                            .then(
                                if (penColor == color) Modifier.border(
                                    2.dp,
                                    Color.White,
                                    CircleShape
                                ) else Modifier
                            )
                            .clickable {
                                penColor = color
                                currentTool = DrawingTool.PEN
                            }
                    )
                }
                // Clear
                ToolChip("🗑", false) {
                    sketchViewRef?.clearCanvas()
                }
            }

            // Right side: voice + send
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice toggle
                ToolChip(
                    text = if (voiceState.isRecording) "⏹" else "🎤",
                    isActive = voiceState.isRecording,
                    activeColor = Color(0xFFB91C1C)
                ) { onVoiceToggle() }

                // SEND button
                Button(
                    onClick = {
                        containerRef?.let { container ->
                            val bitmap = captureContainerBitmap(container)
                            if (bitmap != null) {
                                onSend(bitmap, voiceState.transcription)
                                sketchViewRef?.clearCanvas()
                            }
                        }
                    },
                    enabled = !isSending && codeUpdate != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            annotationSent -> Color(0xFF22C55E)
                            else -> Color(0xFF0E639C)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        when {
                            isSending -> "..."
                            annotationSent -> "Sent!"
                            else -> "Send"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolChip(
    text: String,
    isActive: Boolean,
    activeColor: Color = Color(0xFF0E639C),
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) activeColor else Color(0xFF3C3C3C))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 15.sp)
    }
}

private fun captureContainerBitmap(container: FrameLayout): Bitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(container.width, container.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        container.draw(canvas)
        bitmap
    } catch (e: Exception) {
        null
    }
}
