package com.sketchcode.app.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

enum class DrawingTool { PEN, ERASER }

data class StrokePoint(val x: Float, val y: Float, val pressure: Float)

data class Stroke(
    val points: MutableList<StrokePoint> = mutableListOf(),
    val color: Int = Color.RED,
    val baseWidth: Float = 6f,
    val tool: DrawingTool = DrawingTool.PEN
)

/**
 * Native Android View for drawing. Handles touch/stylus directly — no Compose
 * gesture conflicts. Sits as a transparent overlay on top of the code display.
 */
class SketchCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val strokes = mutableListOf<Stroke>()
    private var currentStroke: Stroke? = null

    var currentTool: DrawingTool = DrawingTool.PEN
    var penColor: Int = Color.RED
    var penWidth: Float = 6f

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        // Transparent background so code shows through
        setBackgroundColor(Color.TRANSPARENT)
        // We need to handle touch ourselves
        isClickable = true
        isFocusable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only handle stylus input — let finger events pass through to ScrollView for scrolling
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val stroke = Stroke(
                    color = penColor,
                    baseWidth = if (currentTool == DrawingTool.ERASER) 30f else penWidth,
                    tool = currentTool
                )
                stroke.points.add(StrokePoint(event.x, event.y, event.pressure))
                currentStroke = stroke
                invalidate()
                // Request parent not to intercept (prevent scroll stealing touch)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentStroke?.let { stroke ->
                    // Capture historical points for smooth stylus input
                    for (i in 0 until event.historySize) {
                        stroke.points.add(
                            StrokePoint(
                                event.getHistoricalX(i),
                                event.getHistoricalY(i),
                                event.getHistoricalPressure(i)
                            )
                        )
                    }
                    stroke.points.add(StrokePoint(event.x, event.y, event.pressure))
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentStroke?.let { stroke ->
                    if (stroke.points.size >= 2) {
                        strokes.add(stroke)
                    }
                }
                currentStroke = null
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw completed strokes
        for (stroke in strokes) {
            drawStroke(canvas, stroke)
        }
        // Draw in-progress stroke
        currentStroke?.let { drawStroke(canvas, it) }
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke) {
        if (stroke.points.size < 2) return

        if (stroke.tool == DrawingTool.ERASER) {
            strokePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            strokePaint.strokeWidth = stroke.baseWidth
            strokePaint.color = Color.TRANSPARENT
        } else {
            strokePaint.xfermode = null
            strokePaint.color = stroke.color
        }

        val path = Path()
        val first = stroke.points[0]
        path.moveTo(first.x, first.y)

        for (i in 1 until stroke.points.size) {
            val pt = stroke.points[i]
            if (stroke.tool == DrawingTool.PEN) {
                strokePaint.strokeWidth = stroke.baseWidth + (pt.pressure * 8f)
            }
            path.lineTo(pt.x, pt.y)
        }
        canvas.drawPath(path, strokePaint)
    }

    /** Clear all drawings */
    fun clearCanvas() {
        strokes.clear()
        currentStroke = null
        invalidate()
    }

    /** Check if there are any drawings */
    fun hasDrawings(): Boolean = strokes.isNotEmpty()

    /**
     * Get the bounding rectangle of all strokes (annotation region).
     * Returns null if no strokes exist.
     */
    fun getAnnotationBounds(): RectF? {
        if (strokes.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (stroke in strokes) {
            for (pt in stroke.points) {
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
        }
        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * Capture the full bitmap of this view (just the strokes, transparent background).
     */
    fun captureDrawingBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        draw(c)
        return bitmap
    }
}
