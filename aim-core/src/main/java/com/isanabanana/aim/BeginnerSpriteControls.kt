package com.isanabanana.aim

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.sqrt

/** Renders one frame from a sprite sheet. Columns are animation frames; rows are directions. */
class SpriteSheetRenderer(
    val bitmap: Bitmap,
    val columns: Int,
    val rows: Int,
    var frameWidth: Float,
    var frameHeight: Float,
    var displayWidth: Float,
    var displayHeight: Float
) : Component() {
    var frame: Int = 0
        set(value) { field = value.coerceIn(0, columns - 1) }
    var direction: Int = 0
        set(value) { field = value.coerceIn(0, rows - 1) }
    var alpha: Int = 255
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val destination = RectF()

    init {
        require(columns > 0 && rows > 0 && frameWidth > 0f && frameHeight > 0f)
        require(bitmap.width >= columns * frameWidth && bitmap.height >= rows * frameHeight) {
            "Sprite sheet dimensions do not match the frame grid."
        }
        require(displayWidth > 0f && displayHeight > 0f)
    }

    override fun draw(canvas: Canvas) {
        if (bitmap.isRecycled || alpha <= 0) return
        val left = frame * frameWidth
        val top = direction * frameHeight
        source.set(left.toInt(), top.toInt(), (left + frameWidth).toInt(), (top + frameHeight).toInt())
        destination.set(-displayWidth / 2f, -displayHeight / 2f, displayWidth / 2f, displayHeight / 2f)
        paint.alpha = alpha.coerceIn(0, 255)
        canvas.drawBitmap(bitmap, source, destination, paint)
    }
}

/** Beginner wrapper for a 4-direction (or any row-based) animated sprite. */
class AnimatedSprite internal constructor(
    internal val gameObject: GameObject,
    private val renderer: SpriteSheetRenderer
) {
    fun position(x: Float, y: Float) = apply { gameObject.transform.x = x; gameObject.transform.y = y }
    fun size(width: Float, height: Float) = apply {
        require(width > 0f && height > 0f)
        renderer.displayWidth = width
        renderer.displayHeight = height
    }
    /** Set the row: 0=down/front, 1=left, 2=right, 3=up/back by default. */
    fun direction(row: Int) = apply { renderer.direction = row }
    fun frame(column: Int) = apply { renderer.frame = column }
    fun alpha(value: Int) = apply { renderer.alpha = value.coerceIn(0, 255) }
    fun layer(value: Int) = apply { gameObject.renderLayer = value }
    fun moveTo(x: Float, y: Float, speed: Float) = apply {
        require(speed > 0f)
        gameObject.transform.x = x
        gameObject.transform.y = y
    }
}

/** On-screen analog stick. Callback values are normalized to -1..1. */
class VirtualJoystick(
    private val radius: Float,
    private val onMove: (x: Float, y: Float) -> Unit,
    private val baseColor: Int = 0x66505050,
    private val knobColor: Int = 0xAA222222.toInt()
) : Component() {
    private var pointerId: Int? = null
    private var knobX = 0f
    private var knobY = 0f
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = knobColor }

    init { require(radius > 0f) }

    override fun draw(canvas: Canvas) {
        canvas.drawCircle(0f, 0f, radius, basePaint)
        canvas.drawCircle(knobX, knobY, radius * 0.38f, knobPaint)
    }

    override fun onTouch(event: GameTouchEvent): Boolean {
        when (event.action) {
            GameTouchEvent.Action.DOWN -> {
                if (pointerId != null) return false
                val dx = event.x - gameObject.transform.x
                val dy = event.y - gameObject.transform.y
                if (dx * dx + dy * dy > radius * radius) return false
                pointerId = event.pointerId
                updateStick(event.x, event.y)
                return true
            }
            GameTouchEvent.Action.MOVE -> {
                if (pointerId != event.pointerId) return false
                updateStick(event.x, event.y)
                return true
            }
            GameTouchEvent.Action.UP, GameTouchEvent.Action.CANCEL -> {
                if (pointerId != event.pointerId) return false
                pointerId = null
                knobX = 0f
                knobY = 0f
                onMove(0f, 0f)
                return true
            }
        }
    }

    private fun updateStick(x: Float, y: Float) {
        var dx = x - gameObject.transform.x
        var dy = y - gameObject.transform.y
        val length = sqrt(dx * dx + dy * dy)
        if (length > radius) {
            val factor = radius / length
            dx *= factor
            dy *= factor
        }
        knobX = dx
        knobY = dy
        onMove((dx / radius).coerceIn(-1f, 1f), (dy / radius).coerceIn(-1f, 1f))
    }
}
