package com.sketchcode.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.sketchcode.app.network.CodeUpdate
import com.sketchcode.app.network.OpenFileInfo
import com.sketchcode.app.service.VoiceState
import com.sketchcode.app.ui.components.DrawingTool
import com.sketchcode.app.ui.components.SketchCanvasView

@Composable
fun SketchScreen(
    codeUpdate: CodeUpdate?,
    openFiles: List<OpenFileInfo>,
    activeFile: String,
    codeCache: Map<String, CodeUpdate>,
    voiceState: VoiceState,
    isSending: Boolean,
    annotationSent: Boolean,
    onSendAll: (List<Pair<Bitmap, String>>, String) -> Unit,
    onFileSelect: (OpenFileInfo) -> Unit,
    onVoiceToggle: () -> Unit,
    onClearTranscription: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTool by remember { mutableStateOf(DrawingTool.PEN) }
    var penColor by remember { mutableIntStateOf(AndroidColor.RED) }
    // Track whether current file has drawings (for enabling Send button reactively)
    var hasDrawings by remember { mutableStateOf(false) }

    // Native view references
    var sketchViewRef by remember { mutableStateOf<SketchCanvasView?>(null) }
    var containerRef by remember { mutableStateOf<FrameLayout?>(null) }
    var innerFrameRef by remember { mutableStateOf<FrameLayout?>(null) }

    // Push tool/color changes to the native view
    LaunchedEffect(currentTool, penColor) {
        sketchViewRef?.currentTool = currentTool
        sketchViewRef?.penColor = penColor
    }

    // Switch canvas strokes when the active file changes
    LaunchedEffect(activeFile) {
        if (activeFile.isNotEmpty()) {
            sketchViewRef?.switchToFile(activeFile)
        }
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

        // ── File tabs ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D30))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (openFiles.isEmpty()) {
                // Show the current file as a single tab when open_files hasn't arrived yet
                codeUpdate?.let { code ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            code.filename,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFFFFFF),
                            maxLines = 1
                        )
                    }
                }
            } else {
                openFiles.forEach { file ->
                    val isActive = file.filename == activeFile
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (isActive) Color(0xFF1E1E1E) else Color.Transparent
                            )
                            .clickable { onFileSelect(file) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            file.filename,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isActive) Color(0xFFFFFFFF) else Color(0xFF858585),
                            maxLines = 1
                        )
                    }
                }
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

                    // ScrollView contains BOTH the code text and the sketch canvas
                    // inside a FrameLayout, so annotations scroll with the code.
                    val codeText = TextView(context).apply {
                        setTextColor(AndroidColor.parseColor("#D4D4D4"))
                        setBackgroundColor(AndroidColor.parseColor("#1E1E1E"))
                        textSize = 12f
                        typeface = Typeface.MONOSPACE
                        setPadding(20, 20, 20, 600)
                        setLineSpacing(0f, 1.3f)
                        tag = "codeText"
                    }

                    // Inner FrameLayout: code + canvas stacked, both same height
                    val sketchView = SketchCanvasView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }

                    val innerFrame = FrameLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        tag = "innerFrame"
                        addView(codeText)
                        addView(sketchView)
                    }

                    val scrollView = ScrollView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        isVerticalScrollBarEnabled = true
                        tag = "scrollView"
                        addView(innerFrame)
                    }
                    addView(scrollView)

                    sketchViewRef = sketchView
                    containerRef = this
                    innerFrameRef = innerFrame

                    // Wire up stroke change callback to update Compose state
                    sketchView.onStrokesChanged = {
                        hasDrawings = sketchView.hasAnyAnnotations()
                    }
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

        // ── Voice transcription / status bar ──
        if (voiceState.isRecording || voiceState.transcription.isNotEmpty()
            || voiceState.interimText.isNotEmpty() || voiceState.error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        when {
                            voiceState.error != null -> Color(0xFF4A1A1A)
                            voiceState.isRecording -> Color(0xFF3B1A1A)
                            else -> Color(0xFF2D2D30)
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (voiceState.error != null) "⚠️" else "🎙",
                    fontSize = 16.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        voiceState.error != null -> voiceState.error
                        voiceState.isRecording -> voiceState.interimText.ifEmpty { "Listening..." }
                        voiceState.interimText.isNotEmpty() -> voiceState.interimText
                        else -> voiceState.transcription
                    },
                    color = if (voiceState.error != null) Color(0xFFEF4444) else Color(0xFFD4D4D4),
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

                // Clear transcription
                if (voiceState.transcription.isNotEmpty()) {
                    ToolChip("✕", false) {
                        onClearTranscription()
                    }
                }

                // SEND button — captures all annotated files
                Button(
                    onClick = {
                        val sketch = sketchViewRef
                        val frame = innerFrameRef
                        val codeTextView = frame?.findViewWithTag<TextView>("codeText")
                        if (sketch != null && frame != null && codeTextView != null) {
                            val annotatedFiles = sketch.getAnnotatedFiles()
                            val captures = mutableListOf<Pair<Bitmap, String>>()

                            if (annotatedFiles.isNotEmpty()) {
                                // Save the original code text so we can restore it
                                val originalText = codeTextView.text.toString()
                                val originalFile = activeFile

                                for (filename in annotatedFiles) {
                                    // Switch canvas to this file's strokes
                                    sketch.switchToFile(filename)

                                    // Set the code text to this file's content
                                    val cached = codeCache[filename]
                                    if (cached != null) {
                                        val lines = cached.code.split("\n")
                                        val padWidth = lines.size.toString().length
                                        val numbered = lines.mapIndexed { i, line ->
                                            "${(i + 1).toString().padStart(padWidth)}  $line"
                                        }.joinToString("\n")
                                        codeTextView.text = numbered
                                    }

                                    // Force layout so the view dimensions are correct
                                    frame.measure(
                                        android.view.View.MeasureSpec.makeMeasureSpec(frame.width, android.view.View.MeasureSpec.EXACTLY),
                                        android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                                    )
                                    frame.layout(frame.left, frame.top, frame.right, frame.top + frame.measuredHeight)

                                    val bitmap = captureFullContent(frame, sketch)
                                    if (bitmap != null) {
                                        captures.add(Pair(bitmap, filename))
                                    }

                                    // Clear this file's strokes after capture
                                    sketch.clearCanvas()
                                }

                                // Restore the original active file
                                sketch.switchToFile(originalFile)
                                codeTextView.text = originalText
                            } else if (voiceState.transcription.isNotEmpty()) {
                                // Voice-only: capture current file as-is
                                val bitmap = captureFullContent(frame, sketch)
                                if (bitmap != null) {
                                    captures.add(Pair(bitmap, activeFile))
                                }
                            }

                            if (captures.isNotEmpty()) {
                                onSendAll(captures, voiceState.transcription)
                            }
                        }
                    },
                    enabled = !isSending && codeUpdate != null && (hasDrawings || voiceState.transcription.isNotEmpty()),
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

/**
 * Capture code + annotations, cropped to the annotated region with context padding.
 * If no annotations exist, falls back to the visible viewport.
 * The result is capped at MAX_DIM pixels on any side to stay within API limits.
 */
private fun captureFullContent(innerFrame: FrameLayout, sketchView: SketchCanvasView?): Bitmap? {
    return try {
        val w = innerFrame.width
        val h = innerFrame.height
        if (w <= 0 || h <= 0) return null

        // Render the full content to a bitmap
        val fullBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fullBitmap)
        innerFrame.draw(canvas)

        // Crop vertically to annotation region (keep full width for line numbers)
        val bounds = sketchView?.getAnnotationBounds()
        val cropped = if (bounds != null) {
            val pad = 150
            val cropTop = max(0, (bounds.top - pad).roundToInt())
            val cropBottom = min(h, (bounds.bottom + pad).roundToInt())
            val cropH = cropBottom - cropTop
            if (cropH > 0) {
                Bitmap.createBitmap(fullBitmap, 0, cropTop, w, cropH)
            } else {
                fullBitmap
            }
        } else {
            fullBitmap
        }

        // Scale down if any dimension exceeds limit (Claude API max is 8000, target well below)
        val maxDim = 4000
        val scale = if (cropped.width > maxDim || cropped.height > maxDim) {
            maxDim.toFloat() / max(cropped.width, cropped.height)
        } else {
            1f
        }
        if (scale < 1f) {
            val scaledW = (cropped.width * scale).roundToInt()
            val scaledH = (cropped.height * scale).roundToInt()
            Bitmap.createScaledBitmap(cropped, scaledW, scaledH, true)
        } else {
            cropped
        }
    } catch (e: Exception) {
        null
    }
}
