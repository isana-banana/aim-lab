package com.isanabanana.aim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.Choreographer
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.View

/** Canvas-backed runtime view driven by Choreographer on Android's UI thread. */
class GameView(
    context: Context,
    scene: GameScene,
    worldWidth: Float = 360f,
    worldHeight: Float = 640f,
    private val backgroundColor: Int = Color.rgb(24, 31, 45)
) : View(context), Choreographer.FrameCallback {

    val viewport = VirtualViewport(worldWidth, worldHeight)
    val sceneManager = SceneManager(scene)
    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var lastFrameNanos = 0L
    private val simulationClock = FixedStepClock()
    private val metricsTracker = FrameMetricsTracker()
    private val acceptedPointers = mutableSetOf<Int>()

    /** Rolling timing and simulation metrics for recent rendered frames. */
    val frameMetrics: FrameMetrics
        get() = metricsTracker.snapshot()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        sceneManager.currentScene.camera.setViewport(worldWidth, worldHeight)
        sceneManager.setTextInputFocusListener { focused ->
            if (focused) showSoftKeyboard() else hideSoftKeyboard()
        }
    }

    override fun onCheckIsTextEditor(): Boolean =
        sceneManager.currentScene.focusedTextInput() != null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val field = sceneManager.currentScene.focusedTextInput() ?: return null
        outAttrs.inputType = field.inputType
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                sceneManager.currentScene.commitText(text?.toString().orEmpty())
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) repeat(beforeLength) {
                    sceneManager.currentScene.deleteTextBackward()
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action != KeyEvent.ACTION_DOWN) return true
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DEL -> sceneManager.currentScene.deleteTextBackward()
                    KeyEvent.KEYCODE_ENTER -> sceneManager.currentScene.submitText()
                    else -> return false
                }
                return true
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                sceneManager.currentScene.submitText()
                hideSoftKeyboard()
                return true
            }
        }
    }

    /** Requests Android's native soft keyboard for the currently focused UITextField. */
    fun showSoftKeyboard() {
        if (sceneManager.currentScene.focusedTextInput() == null) return
        requestFocus()
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.restartInput(this)
        post { manager?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }
    }

    /** Hides the Android soft keyboard without changing the current scene. */
    fun hideSoftKeyboard() {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLoop()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        acceptedPointers.clear()
        sceneManager.cancelTouches()
        super.onDetachedFromWindow()
    }

    fun startLoop() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        simulationClock.reset()
        choreographer.postFrameCallback(this)
    }

    fun stopLoop() {
        if (!running) return
        running = false
        simulationClock.reset()
        choreographer.removeFrameCallback(this)
        acceptedPointers.clear()
        sceneManager.cancelTouches()
    }

    /** Call from Activity.onPause to pause scene hooks and stop frame delivery. */
    fun pauseGame() {
        stopLoop()
        sceneManager.pause()
    }

    /** Call from Activity.onResume to resume the active scene and frame delivery. */
    fun resumeGame() {
        sceneManager.resume()
        startLoop()
    }

    /** Stops frame delivery and releases scene components. Call when the host is destroyed. */
    fun dispose() {
        stopLoop()
        sceneManager.dispose()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val frameDelta = if (lastFrameNanos == 0L) 0f
        else (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = frameTimeNanos
        val updateStartedNanos = System.nanoTime()
        val steps = simulationClock.advance(frameDelta) { fixedDelta -> sceneManager.update(fixedDelta) }
        metricsTracker.recordFrame(frameDelta, System.nanoTime() - updateStartedNanos, steps)
        invalidate()
        choreographer.postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundColor)
        viewport.update(width, height)
        sceneManager.currentScene.camera.setViewport(viewport.worldWidth, viewport.worldHeight)
        val save = canvas.save()
        viewport.applyTo(canvas)
        val drawStartedNanos = System.nanoTime()
        sceneManager.draw(canvas)
        canvas.restoreToCount(save)
        metricsTracker.recordDraw(System.nanoTime() - drawStartedNanos)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            acceptedPointers.clear()
            sceneManager.cancelTouches()
            return true
        }
        viewport.update(width, height)
        val index = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(index)
                val y = event.getY(index)
                if (!viewport.containsScreenPoint(x, y)) return true
                val pointerId = event.getPointerId(index)
                acceptedPointers += pointerId
                dispatchPointer(GameTouchEvent.Action.DOWN, event, index)
            }
            MotionEvent.ACTION_MOVE -> {
                val positions = (0 until event.pointerCount)
                    .filter { event.getPointerId(it) in acceptedPointers }
                    .map { pointerIndex ->
                        GamePointerPosition(
                            event.getPointerId(pointerIndex),
                            viewport.toWorldX(event.getX(pointerIndex)),
                            viewport.toWorldY(event.getY(pointerIndex))
                        )
                    }
                for (pointerIndex in 0 until event.pointerCount) {
                    if (event.getPointerId(pointerIndex) in acceptedPointers) {
                        dispatchPointer(GameTouchEvent.Action.MOVE, event, pointerIndex, positions)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val pointerId = event.getPointerId(index)
                if (pointerId in acceptedPointers) dispatchPointer(GameTouchEvent.Action.UP, event, index)
                acceptedPointers.remove(pointerId)
            }
            else -> return true
        }
        return true
    }

    private fun dispatchPointer(
        action: GameTouchEvent.Action,
        event: MotionEvent,
        index: Int,
        pointerPositions: List<GamePointerPosition> = emptyList()
    ) {
        val screenX = event.getX(index)
        val screenY = event.getY(index)
        if (action == GameTouchEvent.Action.DOWN && !viewport.containsScreenPoint(screenX, screenY)) return
        sceneManager.onTouch(
            GameTouchEvent(
                action = action,
                x = viewport.toWorldX(screenX),
                y = viewport.toWorldY(screenY),
                pointerId = event.getPointerId(index),
                pointerCount = event.pointerCount,
                pointerPositions = pointerPositions
            )
        )
    }
}
