package com.isanabanana.aim

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Draws a bitmap centered on its GameObject origin.
 * The GameObject Transform controls position, rotation, and scale.
 */
class SpriteRenderer(
    var bitmap: Bitmap,
    var width: Float = bitmap.width.toFloat(),
    var height: Float = bitmap.height.toFloat(),
    var alpha: Int = 255
) : Component() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()

    init {
        require(width > 0f && height > 0f) { "Sprite size must be positive." }
    }

    override fun draw(canvas: Canvas) {
        if (bitmap.isRecycled || alpha <= 0) return
        paint.alpha = alpha.coerceIn(0, 255)
        destination.set(-width / 2f, -height / 2f, width / 2f, height / 2f)
        canvas.drawBitmap(bitmap, null, destination, paint)
    }
}
