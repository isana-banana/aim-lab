package com.isanabanana.stickmanmmo

import android.graphics.Color
import com.isanabanana.aim.Game
import com.isanabanana.aim.GameScene

class MainActivity : Game() {
    override val gameWidth = 960f
    override val gameHeight = 540f
    override val gameBackground = Color.rgb(250, 249, 245)
    override fun createScene(): GameScene = ArenaScene(applicationContext)
}
