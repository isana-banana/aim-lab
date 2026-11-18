package com.isanabanana.aim

import android.app.Activity
import android.os.Bundle
import android.graphics.Color

/**
 * Beginner-friendly Android entry point for an AIM game.
 *
 * A game can start with one class:
 * class MyGame : Game() {
 *     override fun createScene() = MainScene()
 * }
 *
 * Register MyGame as the launcher Activity in the app manifest.
 */
abstract class Game : Activity() {
    /** Override only when the default virtual resolution or background is unsuitable. */
    open val gameWidth: Float = 360f
    open val gameHeight: Float = 640f
    open val gameBackground: Int = Color.rgb(24, 31, 45)

    lateinit var gameView: GameView
        private set

    /** Return the first screen of the game. */
    abstract fun createScene(): GameScene

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val firstScene = createScene()
        if (firstScene is Scene) firstScene.attachContext(this)
        gameView = GameView(this, firstScene, gameWidth, gameHeight, gameBackground)
        setContentView(gameView)
    }

    /** Replace the current screen with another screen. */
    fun setScene(scene: GameScene, fadeSeconds: Float = 0f) {
        if (scene is Scene) scene.attachContext(this)
        gameView.sceneManager.transitionTo(scene, fadeSeconds, gameBackground)
    }

    /** Show a temporary screen such as pause/settings without destroying the current one. */
    fun openScene(scene: GameScene) {
        if (scene is Scene) scene.attachContext(this)
        gameView.sceneManager.push(scene)
    }

    fun closeScene(): Boolean = gameView.sceneManager.pop()

    @Deprecated("Use the AndroidX back dispatcher in production hosts.")
    override fun onBackPressed() {
        if (::gameView.isInitialized && gameView.sceneManager.handleBack()) return
        super.onBackPressed()
    }

    override fun onPause() {
        if (::gameView.isInitialized) gameView.pauseGame()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::gameView.isInitialized) gameView.resumeGame()
    }

    override fun onDestroy() {
        if (::gameView.isInitialized) gameView.dispose()
        super.onDestroy()
    }
}
