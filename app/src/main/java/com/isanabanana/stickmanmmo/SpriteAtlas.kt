package com.isanabanana.stickmanmmo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.util.Base64

/** 3-column animation x 4-direction sprite atlas, each frame is 32x32 pixels. */
class SpriteAtlas private constructor(
    val player: Bitmap,
    val fastMob: Bitmap,
    val heavyMob: Bitmap,
    val guardMob: Bitmap
) {
    companion object {
        fun load(context: Context): SpriteAtlas {
            fun read(name: String): Bitmap {
                val encoded = context.assets.open("sprites/$name.png.b64").bufferedReader().use { it.readText() }
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
                    "Invalid sprite sheet: $name"
                }
            }
            return SpriteAtlas(
                read("player"), read("mob-fast"), read("mob-heavy"), read("mob-guard")
            )
        }
    }

    fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        x: Float,
        feetY: Float,
        direction: Int,
        frame: Int,
        scale: Float
    ) {
        // Rows: down/front, left, right, up/back. Columns: animation frames.
        val row = when (direction) { 0 -> 0; -1 -> 1; 1 -> 2; else -> 3 }
        val column = frame.coerceIn(0, 2)
        val src = Rect(column * 32, row * 32, column * 32 + 32, row * 32 + 32)
        val width = 42f * scale
        val height = 48f * scale
        val dst = RectF(x - width / 2f, feetY - height, x + width / 2f, feetY)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, src, dst, paint)
    }
}
