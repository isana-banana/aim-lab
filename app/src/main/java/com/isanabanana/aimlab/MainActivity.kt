package com.isanabanana.aimlab

import android.graphics.BitmapFactory
import android.graphics.Color
import com.isanabanana.aim.Game
import com.isanabanana.aim.GameObject
import com.isanabanana.aim.GameScene
import com.isanabanana.aim.SpriteRenderer
import com.isanabanana.aim.TextRenderer

class MainActivity : Game() {
    override val gameWidth = 360f
    override val gameHeight = 640f
    override val gameBackground = Color.rgb(35, 55, 42)

    override fun createScene(): GameScene = LabStartScene(this)
}

private class LabStartScene(
    private val activity: MainActivity
) : GameScene() {

    override fun onStart() {
        // Load the background from app/src/main/res/drawable/orchard_bg.jpg.
        // If the image has not been uploaded yet, keep the scene runnable.
        val imageId = activity.resources.getIdentifier(
            "orchard_bg",
            "drawable",
            activity.packageName
        )

        if (imageId != 0) {
            val bitmap = BitmapFactory.decodeResource(activity.resources, imageId)
            if (bitmap != null) {
                add(GameObject("OrchardBackground").apply {
                    transform.x = 180f
                    transform.y = 320f
                    renderLayer = -100

                    // Preserve the image's original aspect ratio so the full
                    // top and bottom remain visible without vertical cropping.
                    val displayWidth = 640f * bitmap.width / bitmap.height
                    addComponent(SpriteRenderer(bitmap, displayWidth, 640f))
                })
            }
        }

        // Temporary label: we will replace this with the first game object
        // when the background is confirmed working.
        add(GameObject("LabTitle").apply {
            transform.x = 180f
            transform.y = 48f
            addComponent(TextRenderer("AIM LAB", 22f, Color.WHITE))
        })
    }
}
