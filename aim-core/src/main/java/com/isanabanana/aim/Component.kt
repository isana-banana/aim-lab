package com.isanabanana.aim

/** A reusable behavior or renderer attached to a GameObject. */
abstract class Component {
    lateinit var gameObject: GameObject
        internal set

    internal var attachedTo: GameObject? = null
        private set

    internal fun attachTo(owner: GameObject) {
        check(attachedTo == null) { "A Component can only be attached to one GameObject." }
        attachedTo = owner
        gameObject = owner
        onAttach()
    }

    open fun onAttach() = Unit
    open fun onStart() = Unit
    /** Called whenever a started GameObject becomes active in a scene. */
    open fun onEnable() = Unit
    /** Called when a GameObject is recycled or deactivated without destruction. */
    open fun onDisable() = Unit
    open fun update(deltaSeconds: Float) = Unit
    open fun draw(canvas: android.graphics.Canvas) = Unit
    open fun onTouch(event: GameTouchEvent): Boolean = false
    open fun onDestroy() = Unit
}
