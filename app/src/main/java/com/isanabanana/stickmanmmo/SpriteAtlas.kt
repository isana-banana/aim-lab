package com.isanabanana.stickmanmmo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
                    "Invalid sprite sheet: $name"
                }
                require(bitmap.width >= 96 && bitmap.height >= 128) {
                    "Sprite sheet $name is ${bitmap.width}x${bitmap.height}; expected at least 96x128."
                }
                return bitmap
            }
            return SpriteAtlas(read("player"), read("mob-fast"), read("mob-heavy"), read("mob-guard"))
        }

        /** Safe visible fallback so a missing/corrupt image cannot terminate startup. */
        fun fallback(): SpriteAtlas {
            fun make(color: Int): Bitmap {
                val bitmap = Bitmap.createBitmap(96, 128, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                for (row in 0..3) for (col in 0..2) {
                    val cx = col * 32f + 16f
                    val cy = row * 32f + 16f
                    canvas.drawCircle(cx, cy - 7f, 4f, paint)
                    canvas.drawLine(cx, cy - 3f, cx, cy + 9f, paint)
                    canvas.drawLine(cx, cy + 1f, cx - 7f, cy + 5f, paint)
                    canvas.drawLine(cx, cy + 1f, cx + 7f, cy + 5f, paint)
                    canvas.drawLine(cx, cy + 9f, cx - 6f, cy + 15f, paint)
                    canvas.drawLine(cx, cy + 9f, cx + 6f, cy + 15f, paint)
                }
                return bitmap
            }
            return SpriteAtlas(
                make(Color.BLACK),
                make(Color.rgb(100, 100, 100)),
                make(Color.rgb(65, 65, 65)),
                make(Color.rgb(30, 30, 30))
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
        if (bitmap.isRecycled || bitmap.width < 96 || bitmap.height < 128) return
        val row = when (direction) { 0 -> 0; -1 -> 1; 1 -> 2; else -> 3 }
        val column = frame.coerceIn(0, 2)
        val src = Rect(column * 32, row * 32, column * 32 + 32, row * 32 + 32)
        val width = 42f * scale
        val height = 48f * scale
        val dst = RectF(x - width / 2f, feetY - height, x + width / 2f, feetY)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, src, dst, paint)
    }
}
