package com.isanabanana.aim

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * Owns the active scene, scene stack, and fade-through-color replacements.
 *
 * [push] preserves the current scene beneath a new one. [pop] destroys the
 * top scene and resumes the previous scene. [transitionTo] replaces the top.
 */
class SceneManager(
    initialScene: GameScene,
    private val disposePreviousScene: Boolean = true
) {
    enum class TransitionPhase { IDLE, OUT, IN }

    private val sceneStack = mutableListOf(initialScene)
    private var hostPaused = false
    val currentScene: GameScene get() = sceneStack.last()
    val stackDepth: Int get() = sceneStack.size
    val canPop: Boolean get() = sceneStack.size > 1

    var phase: TransitionPhase = TransitionPhase.IDLE
        private set
    val isTransitioning: Boolean get() = phase != TransitionPhase.IDLE

    private var nextScene: GameScene? = null
    private var durationSeconds = 0f
    private var elapsedSeconds = 0f
    private var transitionColor = Color.BLACK
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipBounds = Rect()
    private var textInputFocusListener: ((Boolean) -> Unit)? = null

    init {
        currentScene.start()
    }

    internal fun setTextInputFocusListener(listener: ((Boolean) -> Unit)?) {
        textInputFocusListener = listener
        sceneStack.forEach { it.setTextInputFocusListener(listener) }
        nextScene?.setTextInputFocusListener(listener)
    }

    /**
     * Places a new scene above the current one without destroying the previous
     * scene. The covered scene is paused and receives onPause().
     */
    fun push(scene: GameScene): Boolean {
        if (isTransitioning || sceneStack.any { it === scene }) return false
        currentScene.cancelTouches()
        currentScene.pause()
        sceneStack += scene
        scene.setTextInputFocusListener(textInputFocusListener)
        scene.start()
        if (hostPaused) scene.pause()
        return true
    }

    /**
     * Removes the top scene and resumes the scene beneath it.
     * Returns false when only the root scene remains.
     */
    fun pop(): Boolean {
        if (isTransitioning || !canPop) return false
        currentScene.cancelTouches()
        val removed = sceneStack.removeAt(sceneStack.lastIndex)
        removed.destroy()
        if (!hostPaused) currentScene.resume()
        return true
    }

    /**
     * Pauses the active scene when the host Activity enters the background.
     * Covered scenes remain paused and are not resumed accidentally.
     */
    fun pause() {
        if (hostPaused) return
        hostPaused = true
        if (sceneStack.isNotEmpty()) currentScene.pause()
        cancelTouches()
    }

    /** Resumes only the top scene after the host Activity returns to foreground. */
    fun resume() {
        if (!hostPaused) return
        hostPaused = false
        if (sceneStack.isNotEmpty()) currentScene.resume()
    }

    /** Handles a conventional Android/system Back request. */
    fun handleBack(): Boolean = pop()

    /**
     * Replaces the active top scene using a fade-out, swap, and fade-in.
     * Returns false if a transition is running or the destination is already
     * present in the retained stack. A zero duration swaps immediately.
     */
    fun transitionTo(
        scene: GameScene,
        durationSeconds: Float = 0.35f,
        color: Int = Color.BLACK
    ): Boolean {
        require(durationSeconds >= 0f) { "Transition duration cannot be negative." }
        if (isTransitioning || sceneStack.any { it === scene }) return false
        if (durationSeconds == 0f) {
            replaceScene(scene)
            return true
        }
        nextScene = scene
        this.durationSeconds = durationSeconds
        elapsedSeconds = 0f
        transitionColor = color
        phase = TransitionPhase.OUT
        currentScene.cancelTouches()
        return true
    }

    fun update(deltaSeconds: Float) {
        if (deltaSeconds < 0f) return
        if (!isTransitioning) {
            currentScene.update(deltaSeconds)
            return
        }

        elapsedSeconds += deltaSeconds
        when (phase) {
            TransitionPhase.OUT -> {
                currentScene.update(deltaSeconds)
                if (elapsedSeconds >= durationSeconds / 2f) {
                    val target = nextScene ?: return
                    replaceScene(target)
                    elapsedSeconds = 0f
                    phase = TransitionPhase.IN
                }
            }
            TransitionPhase.IN -> {
                currentScene.update(deltaSeconds)
                if (elapsedSeconds >= durationSeconds / 2f) {
                    nextScene = null
                    elapsedSeconds = 0f
                    phase = TransitionPhase.IDLE
                }
            }
            TransitionPhase.IDLE -> Unit
        }
    }

    fun draw(canvas: Canvas) {
        currentScene.draw(canvas)
        if (!isTransitioning) return
        val halfDuration = durationSeconds / 2f
        val progress = (elapsedSeconds / halfDuration).coerceIn(0f, 1f)
        val alpha = if (phase == TransitionPhase.OUT) {
            (progress * 255f).toInt()
        } else {
            ((1f - progress) * 255f).toInt()
        }
        overlayPaint.color = transitionColor
        overlayPaint.alpha = alpha.coerceIn(0, 255)
        canvas.getClipBounds(clipBounds)
        canvas.drawRect(clipBounds, overlayPaint)
    }

    fun onTouch(event: GameTouchEvent): Boolean =
        if (isTransitioning) false else currentScene.onTouch(event)

    fun cancelTouches() {
        currentScene.cancelTouches()
    }

    /** Permanently releases every retained scene and any pending destination. */
    fun dispose() {
        sceneStack.asReversed().distinct().forEach(GameScene::destroy)
        sceneStack.clear()
        nextScene?.takeIf { candidate -> sceneStack.none { it === candidate } }?.destroy()
        nextScene = null
        phase = TransitionPhase.IDLE
    }

    private fun replaceScene(scene: GameScene) {
        val previous = currentScene
        sceneStack[sceneStack.lastIndex] = scene
        scene.setTextInputFocusListener(textInputFocusListener)
        scene.start()
        if (hostPaused) scene.pause()
        if (disposePreviousScene) previous.destroy()
        else previous.pause()
    }
}
