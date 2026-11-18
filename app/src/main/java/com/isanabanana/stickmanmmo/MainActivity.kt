package com.isanabanana.stickmanmmo

import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.isanabanana.aim.Game
import com.isanabanana.aim.GameScene

class MainActivity : Game() {
    override val gameWidth = 960f
    override val gameHeight = 540f
    override val gameBackground = Color.rgb(250, 249, 245)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android lifecycle must be initialized before touching the Activity window.
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun createScene(): GameScene = ArenaScene()
}
