package com.isanabanana.aimlab

import android.graphics.Color
import com.isanabanana.aim.Game
import com.isanabanana.aim.GameObject
import com.isanabanana.aim.GameScene
import com.isanabanana.aim.TextRenderer

class MainActivity : Game() {
    override val gameWidth = 360f
    override val gameHeight = 640f
    override val gameBackground = Color.rgb(18, 24, 36)

    override fun createScene(): GameScene = LabStartScene()
}

private class LabStartScene : GameScene() {
    override fun onStart() {
        add(GameObject("LabTitle").apply {
            transform.x = 180f
            transform.y = 260f
            addComponent(TextRenderer("AIM LAB", 32f, Color.WHITE))
        })
        add(GameObject("LabSubtitle").apply {
            transform.x = 180f
            transform.y = 305f
            addComponent(TextRenderer("TEMPAT LATIHAN GAME 2D", 13f, Color.rgb(170, 190, 220)))
        })
        add(GameObject("LabHint").apply {
            transform.x = 180f
            transform.y = 365f
            addComponent(TextRenderer("Mulai dari tutorial pertamamu", 12f, Color.LTGRAY))
        })
    }
}
