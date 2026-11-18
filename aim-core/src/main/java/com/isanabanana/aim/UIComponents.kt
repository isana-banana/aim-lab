package com.isanabanana.aim

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputType
import kotlin.math.cos
import kotlin.math.sin

/** Draws a rounded UI panel centered on its GameObject origin. */
class UIPanel(
    var width: Float,
    var height: Float,
    var color: Int = 0xFF232D3E.toInt(),
    var cornerRadius: Float = 12f,
    var borderColor: Int = Color.TRANSPARENT,
    var borderWidth: Float = 0f
) : Component() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    init { require(width > 0f && height > 0f); require(cornerRadius >= 0f && borderWidth >= 0f) }
    override fun draw(canvas: Canvas) {
        fill.color = color
        val rect = RectF(-width / 2f, -height / 2f, width / 2f, height / 2f)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fill)
        if (borderWidth > 0f && borderColor != Color.TRANSPARENT) {
            border.color = borderColor; border.strokeWidth = borderWidth
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, border)
        }
    }
}

/** Draws a UI text label with configurable alignment. */
class UILabel(
    var text: String,
    var textSize: Float = 18f,
    var color: Int = Color.WHITE,
    var alignment: Paint.Align = Paint.Align.CENTER
) : Component() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = alignment; this.textSize = this@UILabel.textSize; this.color = this@UILabel.color
    }
    override fun draw(canvas: Canvas) {
        paint.textAlign = alignment; paint.textSize = textSize.coerceAtLeast(1f); paint.color = color
        canvas.drawText(text, 0f, 0f, paint)
    }
}

/** Clickable rectangular UI button; its pointer stays captured until release or cancellation. */
class UIButton(
    var width: Float,
    var height: Float,
    var text: String,
    var textSize: Float = 16f,
    var backgroundColor: Int = 0xFF437EDC.toInt(),
    var pressedColor: Int = 0xFF2D5BAA.toInt(),
    var textColor: Int = Color.WHITE,
    var cornerRadius: Float = 10f,
    var enabled: Boolean = true,
    var onClick: (() -> Unit)? = null,
    /** Called as soon as a pointer presses the button. */
    var onPress: (() -> Unit)? = null,
    /** Called when the captured pointer ends; true means it ended inside the button. */
    var onRelease: ((releasedInside: Boolean) -> Unit)? = null,
    /** Draw and hit-test this button as a circle instead of a rounded rectangle. */
    var circular: Boolean = false,
    /** Multiplies the alpha of the button background and label (0 = invisible, 1 = opaque). */
    var opacity: Float = 1f
) : Component() {
    private val background = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private var pressedPointerId: Int? = null
    private var pressed = false

    init { require(width > 0f && height > 0f); require(cornerRadius >= 0f); require(opacity in 0f..1f) }

    override fun draw(canvas: Canvas) {
        background.color = if (!enabled) 0xFF5F6570.toInt() else if (pressed) pressedColor else backgroundColor
        background.alpha = (Color.alpha(background.color) * opacity).toInt().coerceIn(0, 255)
        if (circular) canvas.drawCircle(0f, 0f, minOf(width, height) / 2f, background)
        else canvas.drawRoundRect(-width / 2f, -height / 2f, width / 2f, height / 2f,
            cornerRadius, cornerRadius, background)
        label.color = textColor
        label.alpha = (Color.alpha(textColor) * opacity).toInt().coerceIn(0, 255)
        label.textSize = textSize.coerceAtLeast(1f)
        val baseline = -((label.descent() + label.ascent()) / 2f)
        canvas.drawText(text, 0f, baseline, label)
    }

    override fun onTouch(event: GameTouchEvent): Boolean {
        if (!enabled) {
            if (pressedPointerId != null) onRelease?.invoke(false)
            pressed = false
            pressedPointerId = null
            return false
        }
        val inside = containsWorldPoint(event.x, event.y)
        return when (event.action) {
            GameTouchEvent.Action.DOWN -> {
                if (pressedPointerId != null || !inside) false
                else {
                    pressedPointerId = event.pointerId
                    pressed = true
                    onPress?.invoke()
                    true
                }
            }
            GameTouchEvent.Action.MOVE -> {
                if (pressedPointerId == event.pointerId) { pressed = inside; true } else false
            }
            GameTouchEvent.Action.UP -> {
                if (pressedPointerId != event.pointerId) false
                else {
                    val shouldClick = pressed && inside
                    pressed = false
                    pressedPointerId = null
                    onRelease?.invoke(shouldClick)
                    if (shouldClick) onClick?.invoke()
                    true
                }
            }
            GameTouchEvent.Action.CANCEL -> {
                if (pressedPointerId != event.pointerId) false
                else {
                    pressed = false
                    pressedPointerId = null
                    onRelease?.invoke(false)
                    true
                }
            }
        }
    }

    private fun containsWorldPoint(worldX: Float, worldY: Float): Boolean {
        val transform = gameObject.transform
        if (transform.scaleX == 0f || transform.scaleY == 0f) return false
        val dx = worldX - transform.x; val dy = worldY - transform.y
        val radians = Math.toRadians(transform.rotation.toDouble())
        val c = cos(radians).toFloat(); val s = sin(radians).toFloat()
        val localX = (dx * c + dy * s) / transform.scaleX
        val localY = (-dx * s + dy * c) / transform.scaleY
        if (circular) {
            val radius = minOf(width, height) / 2f
            return localX * localX + localY * localY <= radius * radius
        }
        return localX >= -width / 2f && localX <= width / 2f &&
            localY >= -height / 2f && localY <= height / 2f
    }
}


/** Editable single-line text input. GameView supplies Android's soft-keyboard connection. */
class UITextField(
    var width: Float,
    var height: Float,
    var text: String = "",
    var placeholder: String = "",
    var textSize: Float = 16f,
    var textColor: Int = Color.WHITE,
    var hintColor: Int = 0xFF919DAF.toInt(),
    var backgroundColor: Int = 0xFF19212F.toInt(),
    var borderColor: Int = 0xFF55657D.toInt(),
    var focusedBorderColor: Int = 0xFF5AA0FF.toInt(),
    var cornerRadius: Float = 9f,
    var maxLength: Int = 64,
    var inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
    var onValueChanged: ((String) -> Unit)? = null,
    var onSubmit: ((String) -> Unit)? = null,
    var onFocusChanged: ((Boolean) -> Unit)? = null
) : Component() {
    // Defer Android graphics object creation until drawing so text-input logic
    // can be exercised by plain JVM unit tests without Android framework stubs.
    private val fill by lazy(LazyThreadSafetyMode.NONE) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val border by lazy(LazyThreadSafetyMode.NONE) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    }
    private val label by lazy(LazyThreadSafetyMode.NONE) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }
    private var pressedPointerId: Int? = null
    var isFocused: Boolean = false
        private set

    init {
        require(width > 0f && height > 0f)
        require(cornerRadius >= 0f && maxLength >= 0)
    }

    override fun draw(canvas: Canvas) {
        fill.color = backgroundColor
        canvas.drawRoundRect(-width / 2f, -height / 2f, width / 2f, height / 2f,
            cornerRadius, cornerRadius, fill)
        border.color = if (isFocused) focusedBorderColor else borderColor
        border.strokeWidth = if (isFocused) 2f else 1f
        canvas.drawRoundRect(-width / 2f, -height / 2f, width / 2f, height / 2f,
            cornerRadius, cornerRadius, border)

        label.textSize = textSize.coerceAtLeast(1f)
        val baseline = -((label.descent() + label.ascent()) / 2f)
        val left = -width / 2f + 10f
        val right = width / 2f - 10f
        canvas.save()
        canvas.clipRect(left, -height / 2f + 2f, right, height / 2f - 2f)
        val shown = if (text.isEmpty()) placeholder else text
        label.color = if (text.isEmpty()) hintColor else textColor
        val available = (right - left - if (isFocused && text.isNotEmpty()) 3f else 0f).coerceAtLeast(0f)
        var start = 0
        while (start < shown.length && label.measureText(shown, start, shown.length) > available) start++
        canvas.drawText(shown, start, shown.length, left, baseline, label)
        if (isFocused) {
            val visible = shown.substring(start)
            val cursorX = (left + label.measureText(visible)).coerceAtMost(right - 1f)
            fill.color = textColor
            canvas.drawRect(cursorX, -height * 0.22f, cursorX + 1.5f, height * 0.22f, fill)
        }
        canvas.restore()
    }

    override fun onTouch(event: GameTouchEvent): Boolean {
        return when (event.action) {
            GameTouchEvent.Action.DOWN -> {
                if (!containsPoint(event.x, event.y)) false
                else {
                    pressedPointerId = event.pointerId
                    setFocused(true)
                    true
                }
            }
            GameTouchEvent.Action.MOVE -> pressedPointerId == event.pointerId
            GameTouchEvent.Action.UP -> {
                if (pressedPointerId != event.pointerId) false
                else { pressedPointerId = null; true }
            }
            GameTouchEvent.Action.CANCEL -> {
                if (pressedPointerId != event.pointerId) false
                else { pressedPointerId = null; true }
            }
        }
    }

    internal fun setFocused(value: Boolean) {
        if (isFocused == value) return
        isFocused = value
        if (!value) pressedPointerId = null
        onFocusChanged?.invoke(value)
    }

    internal fun insertText(value: String) {
        if (!isFocused || value.isEmpty()) return
        val filtered = value.replace("\n", " ").replace("\r", " ")
        val remainingCodePoints = (maxLength - text.codePointCount(0, text.length)).coerceAtLeast(0)
        val appendCodePoints = minOf(remainingCodePoints, filtered.codePointCount(0, filtered.length))
        val appendEnd = filtered.offsetByCodePoints(0, appendCodePoints)
        val next = text + filtered.substring(0, appendEnd)
        if (next != text) {
            text = next
            onValueChanged?.invoke(text)
        }
    }

    internal fun deleteBackward() {
        if (!isFocused || text.isEmpty()) return
        val end = text.offsetByCodePoints(text.length, -1)
        text = text.substring(0, end)
        onValueChanged?.invoke(text)
    }

    internal fun submit() {
        if (isFocused) onSubmit?.invoke(text)
    }

    private fun containsPoint(worldX: Float, worldY: Float): Boolean {
        val transform = gameObject.transform
        if (transform.scaleX == 0f || transform.scaleY == 0f) return false
        val dx = worldX - transform.x
        val dy = worldY - transform.y
        val radians = Math.toRadians(transform.rotation.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val localX = (dx * c + dy * s) / transform.scaleX
        val localY = (-dx * s + dy * c) / transform.scaleY
        return localX >= -width / 2f && localX <= width / 2f &&
            localY >= -height / 2f && localY <= height / 2f
    }
}

/** A touch-friendly checkbox with an optional change callback. */
class UICheckbox(
    var size: Float = 28f,
    var checked: Boolean = false,
    var labelText: String = "",
    var textSize: Float = 16f,
    var checkedColor: Int = 0xFF4B91EB.toInt(),
    var uncheckedColor: Int = 0xFF4B5769.toInt(),
    var textColor: Int = Color.WHITE,
    var onCheckedChanged: ((Boolean) -> Unit)? = null
) : Component() {
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pointerId: Int? = null

    init { require(size > 0f) }

    override fun draw(canvas: Canvas) {
        val half = size / 2f
        boxPaint.color = if (checked) checkedColor else uncheckedColor
        canvas.drawRoundRect(-half, -half, half, half, 5f, 5f, boxPaint)
        if (checked) {
            markPaint.color = Color.WHITE
            canvas.drawLine(-size * .25f, 0f, -size * .05f, size * .2f, markPaint)
            canvas.drawLine(-size * .05f, size * .2f, size * .3f, -size * .25f, markPaint)
        }
        if (labelText.isNotEmpty()) {
            textPaint.color = textColor
            textPaint.textSize = textSize.coerceAtLeast(1f)
            val baseline = -((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(labelText, half + 9f, baseline, textPaint)
        }
    }

    override fun onTouch(event: GameTouchEvent): Boolean {
        val inside = containsPoint(event.x, event.y)
        return when (event.action) {
            GameTouchEvent.Action.DOWN -> if (!inside || pointerId != null) false
                else { pointerId = event.pointerId; true }
            GameTouchEvent.Action.MOVE -> pointerId == event.pointerId
            GameTouchEvent.Action.UP -> {
                if (pointerId != event.pointerId) false else {
                    pointerId = null
                    if (inside) {
                        checked = !checked
                        onCheckedChanged?.invoke(checked)
                    }
                    true
                }
            }
            GameTouchEvent.Action.CANCEL -> if (pointerId != event.pointerId) false
                else { pointerId = null; true }
        }
    }

    private fun containsPoint(x: Float, y: Float): Boolean {
        val t = gameObject.transform
        if (t.scaleX == 0f || t.scaleY == 0f) return false
        val dx = x - t.x; val dy = y - t.y
        val r = Math.toRadians(t.rotation.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        val lx = (dx * c + dy * s) / t.scaleX
        val ly = (-dx * s + dy * c) / t.scaleY
        val half = size / 2f
        textPaint.textSize = textSize.coerceAtLeast(1f)
        val labelWidth = textPaint.measureText(labelText)
        return lx >= -half && lx <= half + (if (labelText.isEmpty()) 0f else labelWidth + 18f) &&
            ly >= -half && ly <= half
    }
}

/** Horizontal value control. The value is clamped to the configured range. */
class UISlider(
    var width: Float = 160f,
    var minValue: Float = 0f,
    var maxValue: Float = 1f,
    value: Float = 0f,
    var trackColor: Int = 0xFF465265.toInt(),
    var fillColor: Int = 0xFF4B91EB.toInt(),
    var thumbColor: Int = Color.WHITE,
    var onValueChanged: ((Float) -> Unit)? = null
) : Component() {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pointerId: Int? = null
    var value: Float = value
        set(newValue) { field = newValue.coerceIn(minValue, maxValue) }

    init {
        require(width > 0f && minValue.isFinite() && maxValue.isFinite() && minValue < maxValue)
        require(value.isFinite())
        this.value = value
    }

    override fun draw(canvas: Canvas) {
        val left = -width / 2f
        val right = width / 2f
        val y = 0f
        val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        track.color = trackColor
        fill.color = fillColor
        thumb.color = thumbColor
        canvas.drawRoundRect(left, -3f, right, 3f, 3f, 3f, track)
        canvas.drawRoundRect(left, -3f, left + width * fraction, 3f, 3f, 3f, fill)
        canvas.drawCircle(left + width * fraction, y, 8f, thumb)
    }

    override fun onTouch(event: GameTouchEvent): Boolean {
        return when (event.action) {
            GameTouchEvent.Action.DOWN -> {
                if (!containsPoint(event.x, event.y) || pointerId != null) false
                else { pointerId = event.pointerId; updateFromPoint(event.x, event.y); true }
            }
            GameTouchEvent.Action.MOVE -> if (pointerId == event.pointerId) {
                updateFromPoint(event.x, event.y); true
            } else false
            GameTouchEvent.Action.UP -> if (pointerId == event.pointerId) {
                updateFromPoint(event.x, event.y); pointerId = null; true
            } else false
            GameTouchEvent.Action.CANCEL -> if (pointerId == event.pointerId) {
                pointerId = null; true
            } else false
        }
    }

    private fun updateFromPoint(worldX: Float, worldY: Float) {
        val t = gameObject.transform
        if (t.scaleX == 0f || t.scaleY == 0f) return
        val radians = Math.toRadians(t.rotation.toDouble())
        val dx = worldX - t.x
        val dy = worldY - t.y
        val localX = (dx * cos(radians).toFloat() + dy * sin(radians).toFloat()) / t.scaleX
        val fraction = ((localX + width / 2f) / width).coerceIn(0f, 1f)
        val next = minValue + (maxValue - minValue) * fraction
        if (next != value) { value = next; onValueChanged?.invoke(value) }
    }

    private fun containsPoint(x: Float, y: Float): Boolean {
        val t = gameObject.transform
        if (t.scaleX == 0f || t.scaleY == 0f) return false
        val dx = x - t.x; val dy = y - t.y
        val r = Math.toRadians(t.rotation.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        val lx = (dx * c + dy * s) / t.scaleX
        val ly = (-dx * s + dy * c) / t.scaleY
        return lx >= -width / 2f - 8f && lx <= width / 2f + 8f &&
            ly >= -16f && ly <= 16f
    }
}

/** Non-interactive horizontal progress indicator. */
class UIProgressBar(
    var width: Float = 160f,
    var height: Float = 10f,
    var progress: Float = 0f,
    var backgroundColor: Int = 0xFF414C5E.toInt(),
    var progressColor: Int = 0xFF4BBE82.toInt(),
    var cornerRadius: Float = 5f
) : Component() {
    private val background = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        require(width > 0f && height > 0f && cornerRadius >= 0f)
        require(progress.isFinite())
        progress = progress.coerceIn(0f, 1f)
    }

    override fun draw(canvas: Canvas) {
        val left = -width / 2f
        val top = -height / 2f
        background.color = backgroundColor
        fill.color = progressColor
        canvas.drawRoundRect(left, top, width / 2f, height / 2f,
            cornerRadius, cornerRadius, background)
        val right = left + width * progress.coerceIn(0f, 1f)
        if (right > left) canvas.drawRoundRect(left, top, right, height / 2f,
            cornerRadius, cornerRadius, fill)
    }
}