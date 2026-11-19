package com.isanabanana.aimlab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.DisplayMetrics
import com.caverock.androidsvg.SVG
import com.isanabanana.aim.Game
import com.isanabanana.aim.GameObject
import com.isanabanana.aim.GameScene
import com.isanabanana.aim.GameTouchEvent
import com.isanabanana.aim.SpriteRenderer
import com.isanabanana.aim.TextRenderer
import kotlin.math.max

class MainActivity : Game() {
    override val gameWidth = 360f
    override val gameHeight = 640f
    override val gameBackground = Color.rgb(35, 55, 42)

    override fun createScene(): GameScene = LabStartScene(this)
}

private class LabStartScene(
    private val activity: MainActivity
) : GameScene() {

    private data class FruitButton(
        val label: String,
        val x: Float,
        val y: Float,
        val gameObject: GameObject
    )

    private val fruitButtons = mutableListOf<FruitButton>()
    private var pressedFruit: Int? = null
    private lateinit var statusRenderer: TextRenderer

    override fun onStart() {
        val metrics: DisplayMetrics = activity.resources.displayMetrics
        val screenScale = max(metrics.widthPixels / 360f, metrics.heightPixels / 640f)
        val visibleWorldWidth = metrics.widthPixels / screenScale
        val visibleWorldHeight = metrics.heightPixels / screenScale

        // COVER: fill the actual EXPAND viewport, preserve image proportions,
        // and crop equally from the left/right or top/bottom around its center.
        val imageId = activity.resources.getIdentifier(
            "orchard_bg", "drawable", activity.packageName
        )
        if (imageId != 0) {
            val bitmap = BitmapFactory.decodeResource(activity.resources, imageId)
            if (bitmap != null) {
                val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val backgroundHeight = max(visibleWorldHeight, visibleWorldWidth / imageRatio)
                val backgroundWidth = backgroundHeight * imageRatio
                add(GameObject("OrchardBackground").apply {
                    transform.x = visibleWorldWidth / 2f
                    transform.y = visibleWorldHeight / 2f
                    renderLayer = -100
                    addComponent(SpriteRenderer(bitmap, backgroundWidth, backgroundHeight))
                })
            }
        }

        statusRenderer = TextRenderer("PILIH BUAH", 14f, Color.WHITE)
        add(GameObject("FruitSelectionStatus").apply {
            transform.x = visibleWorldWidth / 2f
            transform.y = 210f
            renderLayer = 120
            screenSpace = true
            addComponent(statusRenderer)
        })

        val fruitFiles = listOf(
            "apple.svg" to "APEL",
            "orange.svg" to "JERUK",
            "strawberry.svg" to "STROBERI",
            "grapes.svg" to "ANGGUR",
            "watermelon.svg" to "SEMANGKA",
            "pear.svg" to "PIR"
        )
        val positions = listOf(
            34f to 52f, 34f to 104f, 34f to 156f,
            (visibleWorldWidth - 34f) to 52f,
            (visibleWorldWidth - 34f) to 104f,
            (visibleWorldWidth - 34f) to 156f
        )

        fruitFiles.forEachIndexed { index, (file, label) ->
            val bitmap = loadSvgSprite("fruits/$file") ?: return@forEachIndexed
            val (x, y) = positions[index]
            val button = GameObject("FruitButton_$label").apply {
                transform.x = x
                transform.y = y
                renderLayer = 110
                screenSpace = true
                addComponent(SpriteRenderer(bitmap, 46f, 46f))
            }
            add(button)
            fruitButtons += FruitButton(label, x, y, button)
        }
    }

    private fun loadSvgSprite(path: String): Bitmap? = try {
        val svg = SVG.getFromAsset(activity.assets, path)
        val picture = svg.renderToPicture(128, 128)
        Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawPicture(picture)
        }
    } catch (_: Exception) {
        null
    }

    override fun onTouch(event: GameTouchEvent): Boolean {
        if (super.onTouch(event)) return true

        when (event.action) {
            GameTouchEvent.Action.DOWN -> {
                pressedFruit = fruitButtons.indexOfFirst { button ->
                    kotlin.math.abs(event.x - button.x) <= 26f &&
                        kotlin.math.abs(event.y - button.y) <= 26f
                }.takeIf { it >= 0 }
                return pressedFruit != null
            }
            GameTouchEvent.Action.UP -> {
                val index = pressedFruit
                pressedFruit = null
                if (index != null) {
                    val button = fruitButtons.getOrNull(index) ?: return true
                    val inside = kotlin.math.abs(event.x - button.x) <= 28f &&
                        kotlin.math.abs(event.y - button.y) <= 28f
                    if (inside) statusRenderer.text = "DIPILIH: ${button.label}"
                    return true
                }
            }
            GameTouchEvent.Action.CANCEL -> {
                pressedFruit = null
                return true
            }
            GameTouchEvent.Action.MOVE -> if (pressedFruit != null) return true
        }
        return false
    }
}
