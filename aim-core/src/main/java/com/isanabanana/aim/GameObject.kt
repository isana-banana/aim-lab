package com.isanabanana.aim

/** Entity in a scene. Behaviors are composed from Components. */
class GameObject(
    val name: String = "GameObject",
    val transform: Transform = Transform(),
    var renderLayer: Int = 0,
    var renderOrder: Int = 0,
    var screenSpace: Boolean = false
) {
    private val componentList = mutableListOf<Component>()
    private val pendingMutations = java.util.ArrayDeque<() -> Unit>()
    private val pendingAdditions = mutableSetOf<Component>()
    private val pendingRemovals = mutableSetOf<Component>()
    private var iterationDepth = 0
    private var flushingMutations = false
    private var started = false
    var isActive: Boolean = true
        private set
    private var destroyed = false

    /** Read-only live view of attached components. */
    val components: List<Component> = java.util.Collections.unmodifiableList(componentList)

    /** Returns the first attached component assignable to [type], or null. */
    fun <T : Component> getComponent(type: Class<T>): T? =
        componentList.firstOrNull(type::isInstance)?.let(type::cast)

    /** Checks whether a component of [type] is attached. */
    fun hasComponent(type: Class<out Component>): Boolean =
        componentList.any(type::isInstance)

    /**
     * Adds a component. Mutations requested from component callbacks are applied
     * after the current callback pass, keeping iteration stable.
     */
    fun addComponent(component: Component): GameObject {
        check(!destroyed) { "Cannot add a component to a destroyed GameObject." }
        check(component !in componentList && component !in pendingAdditions) {
            "Component is already attached to this GameObject."
        }
        check(component.attachedTo == null) {
            "A Component can only be attached to one GameObject."
        }
        if (iterationDepth > 0 || flushingMutations) {
            pendingAdditions += component
            pendingMutations.addLast { addComponentNow(component) }
        } else {
            addComponentNow(component)
            flushPendingMutations()
        }
        return this
    }

    /** Removes and permanently destroys this component instance. */
    fun removeComponent(component: Component): Boolean {
        if (destroyed || component in pendingRemovals) return false
        if (component !in componentList && component !in pendingAdditions) return false
        pendingRemovals += component
        if (iterationDepth > 0 || flushingMutations) {
            pendingMutations.addLast { removeComponentNow(component) }
        } else {
            removeComponentNow(component)
            flushPendingMutations()
        }
        return true
    }

    private fun addComponentNow(component: Component) {
        pendingAdditions.remove(component)
        withComponentIteration {
            component.attachTo(this)
            componentList += component
            if (started) {
                component.onStart()
                if (isActive) component.onEnable()
            }
        }
    }

    private fun removeComponentNow(component: Component) {
        pendingRemovals.remove(component)
        if (!componentList.remove(component)) return
        withComponentIteration {
            if (isActive) component.onDisable()
            component.onDestroy()
        }
    }

    private inline fun <T> withComponentIteration(action: () -> T): T {
        iterationDepth++
        try {
            return action()
        } finally {
            iterationDepth--
            if (iterationDepth == 0) flushPendingMutations()
        }
    }

    private fun flushPendingMutations() {
        if (iterationDepth != 0 || flushingMutations) return
        flushingMutations = true
        try {
            while (pendingMutations.isNotEmpty()) {
                pendingMutations.removeFirst().invoke()
            }
        } finally {
            flushingMutations = false
        }
    }

    internal fun start() {
        if (destroyed || (started && isActive)) return
        if (!isActive) isActive = true
        val componentsAtStart = componentList.toList()
        if (!started) {
            started = true
            withComponentIteration {
                var index = 0
                while (index < componentsAtStart.size) {
                    val component = componentsAtStart[index++]
                    if (component in componentList && component !in pendingRemovals) {
                        component.onStart()
                    }
                }
            }
        }
        withComponentIteration {
            var index = 0
            while (index < componentsAtStart.size) {
                val component = componentsAtStart[index++]
                if (component in componentList && component !in pendingRemovals) {
                    component.onEnable()
                }
            }
        }
    }

    internal fun deactivate() {
        if (!isActive || destroyed) return
        isActive = false
        withComponentIteration {
            var index = componentList.size - 1
            while (index >= 0) componentList[index--].onDisable()
        }
    }

    internal fun update(deltaSeconds: Float) {
        if (!isActive || destroyed) return
        withComponentIteration {
            val count = componentList.size
            var index = 0
            while (index < count && isActive && !destroyed) {
                componentList[index++].update(deltaSeconds)
            }
        }
    }

    internal fun draw(canvas: android.graphics.Canvas) {
        if (!isActive || destroyed) return
        val save = canvas.save()
        try {
            canvas.translate(transform.x, transform.y)
            canvas.rotate(transform.rotation)
            canvas.scale(transform.scaleX, transform.scaleY)
            withComponentIteration {
                val count = componentList.size
                var index = 0
                while (index < count) componentList[index++].draw(canvas)
            }
        } finally {
            canvas.restoreToCount(save)
        }
    }

    internal fun dispatchTouch(event: GameTouchEvent): Boolean {
        if (!isActive || destroyed) return false
        return withComponentIteration {
            var index = componentList.size - 1
            var handled = false
            while (index >= 0 && !handled && isActive && !destroyed) {
                handled = componentList[index--].onTouch(event)
            }
            handled
        }
    }

    internal fun destroy() {
        if (destroyed) return
        deactivate()
        destroyed = true
        withComponentIteration {
            var index = componentList.size - 1
            while (index >= 0) componentList[index--].onDestroy()
        }
    }
}
