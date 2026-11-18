package com.isanabanana.aim

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Draws text relative to the GameObject origin. */
class TextRenderer(
    var text: String,
    var textSize: Float = 18f,
    var color: Int = Color.WHITE,
    var alignment: Paint.Align = Paint.Align.CENTER
) : Component() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textAlign = alignment
        this.color = this@TextRenderer.color
        this.textSize = this@TextRenderer.textSize
    }

    override fun draw(canvas: Canvas) {
        paint.color = color
        paint.textSize = textSize.coerceAtLeast(1f)
        paint.textAlign = alignment
        canvas.drawText(text, 0f, 0f, paint)
    }
}
