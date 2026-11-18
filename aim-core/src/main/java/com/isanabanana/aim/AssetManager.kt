package com.isanabanana.aim

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized bitmap and sprite-sheet cache for AIM games.
 *
 * Resource IDs are used as stable cache keys. The manager owns decoded bitmaps
 * and recycles them only when clear() or release() is called.
 */
class AssetManager {

    private val bitmapCache = ConcurrentHashMap<Int, Bitmap>()
    private val spriteCache = ConcurrentHashMap<SpriteKey, List<Bitmap>>()

    private data class SpriteKey(
        val resourceId: Int,
        val frameWidth: Int,
        val frameHeight: Int,
        val margin: Int,
        val spacing: Int
    )

    /** Loads a drawable resource once and returns the cached bitmap thereafter. */
    @Synchronized
    fun loadBitmap(context: Context, resourceId: Int): Bitmap {
        require(resourceId != 0) { "A valid drawable resource ID is required." }
        bitmapCache[resourceId]?.let { if (!it.isRecycled) return it }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
            ?: throw IllegalArgumentException("Unable to decode bitmap resource: $resourceId")
        bitmapCache[resourceId] = bitmap
        return bitmap
    }

    /**
     * Splits a sprite-sheet drawable into row-major frames.
     * Frames are returned as cached bitmaps and must not be recycled by callers.
     */
    @Synchronized
    fun loadSpriteSheet(
        context: Context,
        resourceId: Int,
        frameWidth: Int,
        frameHeight: Int,
        margin: Int = 0,
        spacing: Int = 0
    ): List<Bitmap> {
        require(resourceId != 0) { "A valid drawable resource ID is required." }
        require(frameWidth > 0 && frameHeight > 0) { "Frame dimensions must be positive." }
        require(margin >= 0 && spacing >= 0) { "Margin and spacing cannot be negative." }

        val key = SpriteKey(resourceId, frameWidth, frameHeight, margin, spacing)
        spriteCache[key]?.let { frames ->
            if (frames.none { it.isRecycled }) return frames
        }

        val sheet = loadBitmap(context, resourceId)
        val availableWidth = sheet.width.toLong() - margin.toLong() * 2L
        val availableHeight = sheet.height.toLong() - margin.toLong() * 2L
        require(availableWidth >= frameWidth && availableHeight >= frameHeight) {
            "Sprite sheet is smaller than one requested frame."
        }

        val columnCount = (availableWidth + spacing) / (frameWidth.toLong() + spacing)
        val rowCount = (availableHeight + spacing) / (frameHeight.toLong() + spacing)
        require(columnCount > 0L && rowCount > 0L) { "No complete frames found in sprite sheet." }
        require(columnCount <= Int.MAX_VALUE.toLong() / rowCount) {
            "Sprite sheet contains too many frames."
        }
        val frameCount = columnCount * rowCount
        val columns = columnCount.toInt()
        val rows = rowCount.toInt()

        val frames = ArrayList<Bitmap>(frameCount.toInt())
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val left = margin.toLong() + column.toLong() * (frameWidth.toLong() + spacing)
                val top = margin.toLong() + row.toLong() * (frameHeight.toLong() + spacing)
                val rect = Rect(
                    left.toInt(), top.toInt(),
                    (left + frameWidth).toInt(), (top + frameHeight).toInt()
                )
                frames += Bitmap.createBitmap(sheet, rect.left, rect.top, rect.width(), rect.height())
            }
        }
        spriteCache[key] = frames
        return frames
    }

    /** Number of decoded source bitmaps currently cached. */
    val cachedBitmapCount: Int get() = bitmapCache.size

    /** Number of cached sprite-sheet frame sets. */
    val cachedSpriteSheetCount: Int get() = spriteCache.size

    /** Recycles all manager-owned bitmaps and empties both caches. */
    @Synchronized
    fun clear() {
        spriteCache.values.flatten().forEach { if (!it.isRecycled) it.recycle() }
        spriteCache.clear()
        bitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
        bitmapCache.clear()
    }

    /** Alias for clear(), useful when shutting down a game or scene owner. */
    fun release() = clear()
}
