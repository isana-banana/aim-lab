package com.isanabanana.aim

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Canvas
import kotlin.math.sqrt

/**
 * Simple screen API. Override onCreate() to build the screen and onUpdate()
 * for per-frame game logic. The lower-level GameScene API remains available.
 */
open class Scene : GameScene() {
    private var context: Context? = null
    private val assets = AssetManager()

    internal fun attachContext(value: Context) { context = value }

    private var savedData: GameData? = null
    private val realtimeConnections = mutableSetOf<RealtimeConnection>()

    /**
     * Open a secure WebSocket connection for multiplayer messages.
     * Use a wss:// endpoint. Messages are text, commonly JSON.
     */
    protected fun realtime(
        url: String,
        accessToken: String? = null,
        onConnected: (() -> Unit)? = null,
        onMessage: ((String) -> Unit)? = null,
        onClosed: ((Int, String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        autoReconnect: Boolean = false
    ): RealtimeConnection {
        val connection = Realtime.connect(
            url,
            RealtimeListener(onConnected, onMessage, onClosed, onError),
            accessToken,
            autoReconnect = autoReconnect
        )
        realtimeConnections += connection
        return connection
    }

    /**
     * Connect to a room using AIM's simple JSON room protocol.
     * The server receives a join message automatically after the WebSocket opens.
     */
    protected fun joinRoom(
        url: String,
        roomId: String,
        accessToken: String? = null,
        onConnected: (() -> Unit)? = null,
        onMessage: ((String) -> Unit)? = null,
        onClosed: ((Int, String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        autoReconnect: Boolean = true
    ): RealtimeRoom {
        require(roomId.isNotBlank()) { "Room ID cannot be blank." }
        lateinit var connection: RealtimeConnection
        connection = realtime(
            url = url,
            accessToken = accessToken,
            onConnected = {
                val joinMessage = org.json.JSONObject()
                    .put("type", "join")
                    .put("roomId", roomId)
                if (!connection.send(joinMessage.toString())) {
                    onError?.invoke("Could not send room join message.")
                } else {
                    onConnected?.invoke()
                }
            },
            onMessage = onMessage,
            onClosed = onClosed,
            onError = onError,
            autoReconnect = autoReconnect
        )
        return RealtimeRoom(connection, roomId)
    }

    /**
     * Connect to an HTTPS JSON API. Requests run in the background and callbacks
     * return on the Android main thread.
     */
    protected fun onlineData(
        baseUrl: String,
        apiKey: String? = null,
        accessToken: String? = null
    ): OnlineData {
        checkNotNull(context) { "Scene must be launched by an AIM Game." }
        return OnlineData(baseUrl, apiKey, accessToken)
    }

    /** Save a small player value locally. Data is kept between app launches. */
    protected fun saveInt(key: String, value: Int) = gameData().putInt(key, value)
    protected fun saveLong(key: String, value: Long) = gameData().putLong(key, value)
    protected fun saveFloat(key: String, value: Float) = gameData().putFloat(key, value)
    protected fun saveBoolean(key: String, value: Boolean) = gameData().putBoolean(key, value)
    protected fun saveString(key: String, value: String) = gameData().putString(key, value)

    protected fun loadInt(key: String, defaultValue: Int = 0): Int = gameData().getInt(key, defaultValue)
    protected fun loadLong(key: String, defaultValue: Long = 0L): Long = gameData().getLong(key, defaultValue)
    protected fun loadFloat(key: String, defaultValue: Float = 0f): Float = gameData().getFloat(key, defaultValue)
    protected fun loadBoolean(key: String, defaultValue: Boolean = false): Boolean = gameData().getBoolean(key, defaultValue)
    protected fun loadString(key: String, defaultValue: String = ""): String = gameData().getString(key, defaultValue)

    protected fun removeSavedValue(key: String) = gameData().remove(key)
    protected fun clearSavedData() = gameData().clear()

    private fun gameData(): GameData {
        val ctx = checkNotNull(context) { "Scene must be launched by an AIM Game." }
        return savedData ?: GameData(ctx).also { savedData = it }
    }

    protected open fun onCreate() = Unit
    protected open fun onUpdate(delta: Float) = Unit

    final override fun onStart() { onCreate() }

    final override fun update(deltaSeconds: Float) {
        super.update(deltaSeconds)
        onUpdate(deltaSeconds)
    }

    /** Keep scene-owned audio in sync when the scene is covered or the app is backgrounded. */
    override fun onPause() {
        audio?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        audio?.resume()
    }

    /** Add a drawable resource by name, for example sprite("player"). */
    protected fun sprite(resourceName: String): Sprite {
        val ctx = checkNotNull(context) { "Scene must be launched by an AIM Game." }
        val id = ctx.resources.getIdentifier(resourceName, "drawable", ctx.packageName)
        require(id != 0) { "Drawable '$resourceName' was not found in res/drawable." }
        val bitmap = assets.loadBitmap(ctx, id)
        val obj = GameObject(resourceName)
        val renderer = SpriteRenderer(bitmap)
        obj.addComponent(renderer)
        add(obj)
        return Sprite(obj, renderer)
    }

    /**
     * Load a directional sprite sheet from res/drawable.
     * Example: spriteSheet("hero", 32, 32, columns = 3, rows = 4, x = 180f, y = 260f)
     */
    protected fun spriteSheet(
        resourceName: String,
        frameWidth: Int,
        frameHeight: Int,
        columns: Int,
        rows: Int,
        x: Float = 0f,
        y: Float = 0f,
        width: Float = frameWidth.toFloat(),
        height: Float = frameHeight.toFloat()
    ): AnimatedSprite {
        require(frameWidth > 0 && frameHeight > 0 && columns > 0 && rows > 0)
        val ctx = checkNotNull(context) { "Scene must be launched by an AIM Game." }
        val id = ctx.resources.getIdentifier(resourceName, "drawable", ctx.packageName)
        require(id != 0) { "Sprite sheet '$resourceName' was not found in res/drawable." }
        val bitmap = assets.loadBitmap(ctx, id)
        val obj = GameObject("SpriteSheet:$resourceName")
        obj.transform.x = x
        obj.transform.y = y
        val renderer = SpriteSheetRenderer(
            bitmap, columns, rows, frameWidth.toFloat(), frameHeight.toFloat(), width, height
        )
        obj.addComponent(renderer)
        add(obj)
        return AnimatedSprite(obj, renderer)
    }

    /** Add a virtual analog joystick. onMove receives normalized horizontal/vertical values. */
    protected fun joystick(
        x: Float,
        y: Float,
        radius: Float = 58f,
        onMove: (horizontal: Float, vertical: Float) -> Unit
    ): VirtualJoystick {
        val obj = GameObject("Joystick", screenSpace = true)
        obj.transform.x = x
        obj.transform.y = y
        val control = VirtualJoystick(radius, onMove)
        obj.addComponent(control)
        add(obj)
        return control
    }

    /** Add a text label. Its position is the text baseline origin. */
    protected fun text(
        value: String,
        x: Float = 0f,
        y: Float = 0f,
        size: Float = 18f,
        color: Int = Color.WHITE,
        alignment: Paint.Align = Paint.Align.CENTER
    ): Label {
        val obj = GameObject("Text")
        val renderer = TextRenderer(value, size, color, alignment)
        obj.transform.x = x
        obj.transform.y = y
        obj.addComponent(renderer)
        add(obj)
        return Label(obj, renderer)
    }

    /** Create a tappable button centered at x/y. */
    protected fun button(
        value: String,
        x: Float,
        y: Float,
        width: Float = 180f,
        height: Float = 52f,
        color: Int = Color.rgb(67, 126, 220),
        alpha: Float = 1f,
        onClick: () -> Unit
    ): Button {
        require(alpha in 0f..1f)
        val obj = GameObject("Button:$value")
        obj.transform.x = x
        obj.transform.y = y
        val control = UIButton(width, height, value, backgroundColor = color, onClick = onClick, opacity = alpha)
        obj.addComponent(control)
        add(obj)
        return Button(obj, control)
    }

    /** Create a round touch button centered at x/y. */
    protected fun circleButton(
        value: String,
        x: Float,
        y: Float,
        size: Float = 88f,
        onClick: () -> Unit
    ): Button {
        val obj = GameObject("CircleButton:$value", screenSpace = true)
        obj.transform.x = x
        obj.transform.y = y
        val control = UIButton(size, size, value, textSize = 14f, backgroundColor = color,
            cornerRadius = size / 2f, onClick = onClick, circular = true, opacity = alpha)
        obj.addComponent(control)
        add(obj)
        return Button(obj, control)
    }

    /** Create a button that repeats its action while the player holds a finger on it. */
    protected fun touchButton(
        value: String,
        x: Float,
        y: Float,
        width: Float = 92f,
        height: Float = 56f,
        onHold: (Float) -> Unit
    ): TouchButton {
        val obj = GameObject("TouchButton:$value")
        obj.transform.x = x
        obj.transform.y = y
        val control = TouchButtonComponent(width, height, value, onHold)
        obj.addComponent(control)
        add(obj)
        return TouchButton(obj, control)
    }

    /** Create a simple filled circle, useful for bullets, particles, and markers. */
    protected fun circle(
        radius: Float,
        x: Float = 0f,
        y: Float = 0f,
        color: Int = Color.WHITE
    ): Circle {
        require(radius > 0f)
        val obj = GameObject("Circle")
        obj.transform.x = x
        obj.transform.y = y
        val renderer = CircleRenderer(radius, color)
        obj.addComponent(renderer)
        add(obj)
        return Circle(obj, renderer)
    }

    /** Create a sprite collider using the sprite's current size. */
    protected fun Sprite.collider(
        width: Float = gameObject.getComponent(SpriteRenderer::class.java)?.width ?: 32f,
        height: Float = gameObject.getComponent(SpriteRenderer::class.java)?.height ?: 32f,
        onEnter: (() -> Unit)? = null
    ): Sprite = apply {
        require(width >= 0f && height >= 0f)
        val existing = gameObject.getComponent(Collider2D::class.java)
        val collider = existing ?: Collider2D(
            shape = Collider2D.Shape.BOX,
            width = width,
            height = height,
            isTrigger = true
        ).also(gameObject::addComponent)
        collider.width = width
        collider.height = height
        if (onEnter != null) collider.onCollisionEnter = { onEnter() }
    }

    /** Check overlap with another sprite after both have a collider. */
    protected fun Sprite.overlaps(other: Sprite): Boolean {
        val own = gameObject.getComponent(Collider2D::class.java) ?: return false
        val target = other.gameObject.getComponent(Collider2D::class.java) ?: return false
        return own.overlaps(target)
    }

    /** Animate position, scale, and rotation with a single beginner-friendly call. */
    protected fun Sprite.animateTo(
        x: Float? = null,
        y: Float? = null,
        scale: Float? = null,
        rotation: Float? = null,
        duration: Float = 0.3f,
        onFinished: (() -> Unit)? = null
    ): Sprite = apply {
        var animator = gameObject.getComponent(Animator::class.java)
        if (animator == null) {
            animator = Animator()
            gameObject.addComponent(animator)
        }
        animator.animateTo(
            x = x,
            y = y,
            scaleX = scale,
            scaleY = scale,
            rotation = rotation,
            durationSeconds = duration,
            onFinished = onFinished
        )
    }

    /** Stop any active transform animation. */
    protected fun Sprite.stopAnimation(): Sprite = apply {
        gameObject.getComponent(Animator::class.java)?.cancel()
    }

    /** Create a rounded background panel. */
    protected fun panel(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int = 0xFF232D3E.toInt(),
        cornerRadius: Float = 12f
    ): Panel {
        val obj = GameObject("Panel")
        obj.transform.x = x
        obj.transform.y = y
        val control = UIPanel(width, height, color, cornerRadius)
        obj.addComponent(control)
        add(obj)
        return Panel(obj, control)
    }

    /** Create a touch-friendly checkbox with an optional label. */
    protected fun checkbox(
        label: String,
        x: Float,
        y: Float,
        checked: Boolean = false,
        onChange: ((Boolean) -> Unit)? = null
    ): CheckBox {
        val obj = GameObject("CheckBox:$label")
        obj.transform.x = x
        obj.transform.y = y
        val control = UICheckbox(checked = checked, labelText = label, onCheckedChanged = onChange)
        obj.addComponent(control)
        add(obj)
        return CheckBox(obj, control)
    }

    /** Create a horizontal slider. Values are clamped to minValue..maxValue. */
    protected fun slider(
        x: Float,
        y: Float,
        value: Float = 0f,
        minValue: Float = 0f,
        maxValue: Float = 1f,
        width: Float = 160f,
        onChange: ((Float) -> Unit)? = null
    ): Slider {
        val obj = GameObject("Slider")
        obj.transform.x = x
        obj.transform.y = y
        val control = UISlider(width, minValue, maxValue, value, onValueChanged = onChange)
        obj.addComponent(control)
        add(obj)
        return Slider(obj, control)
    }

    /** Create a non-interactive progress bar with a value from 0 to 1. */
    protected fun progressBar(
        x: Float,
        y: Float,
        width: Float = 160f,
        height: Float = 10f,
        progress: Float = 0f
    ): ProgressBar {
        val obj = GameObject("ProgressBar")
        obj.transform.x = x
        obj.transform.y = y
        val control = UIProgressBar(width, height, progress)
        obj.addComponent(control)
        add(obj)
        return ProgressBar(obj, control)
    }

    /** Load a short sound effect from res/raw. Loading is asynchronous; early play() calls are safely ignored. */
    protected fun soundEffect(resourceName: String): SoundEffect {
        val ctx = checkNotNull(context) { "Scene must be launched by an AIM Game." }
        val id = ctx.resources.getIdentifier(resourceName, "raw", ctx.packageName)
        require(id != 0) { "Audio resource '$resourceName' was not found in res/raw." }
        val manager = audioManager()
        return SoundEffect(manager, manager.loadSound(ctx, id))
    }

    /** Play or replace looping background music from res/raw. */
    protected fun music(resourceName: String, loop: Boolean = true): Boolean {
        val ctx = checkNotNull(context) { "Scene must be launched by an AIM Game." }
        val id = ctx.resources.getIdentifier(resourceName, "raw", ctx.packageName)
        require(id != 0) { "Audio resource '$resourceName' was not found in res/raw." }
        val manager = audioManager()
        return manager.playMusic(ctx, id, loop)
    }

    protected fun stopMusic() { audio?.stopMusic() }

    /** Set the default volume used by scene sound effects, from 0 (mute) to 1 (full). */
    protected fun sfxVolume(value: Float) { audioManager().setSfxVolume(value) }

    /** Set background music volume, from 0 (mute) to 1 (full). */
    protected fun musicVolume(value: Float) { audioManager().setMusicVolume(value) }

    protected fun pauseMusic() { audio?.pauseMusic() }
    protected fun resumeMusic() { audio?.resumeMusic() }

    private var audio: AudioManager? = null

    private fun audioManager(): AudioManager =
        audio ?: AudioManager().also { audio = it }

    /** Create a single-line text input. Tap it to open the Android keyboard. */
    protected fun inputField(
        value: String = "",
        x: Float = 0f,
        y: Float = 0f,
        width: Float = 220f,
        height: Float = 48f,
        placeholder: String = "",
        maxLength: Int = 64,
        onChange: ((String) -> Unit)? = null,
        onSubmit: ((String) -> Unit)? = null
    ): TextInput {
        val obj = GameObject("TextInput")
        obj.transform.x = x
        obj.transform.y = y
        val field = UITextField(
            width = width,
            height = height,
            text = value,
            placeholder = placeholder,
            maxLength = maxLength,
            onValueChanged = onChange,
            onSubmit = onSubmit
        )
        obj.addComponent(field)
        add(obj)
        return TextInput(obj, field)
    }

    protected fun remove(sprite: Sprite) { remove(sprite.gameObject) }
    protected fun remove(circle: Circle) { remove(circle.gameObject) }
    protected fun remove(control: TouchButton) { remove(control.gameObject) }
    protected fun remove(label: Label) { remove(label.gameObject) }
    protected fun remove(button: Button) { remove(button.gameObject) }
    protected fun remove(input: TextInput) { remove(input.gameObject) }
    protected fun remove(panel: Panel) { remove(panel.gameObject) }
    protected fun remove(checkBox: CheckBox) { remove(checkBox.gameObject) }
    protected fun remove(slider: Slider) { remove(slider.gameObject) }
    protected fun remove(progressBar: ProgressBar) { remove(progressBar.gameObject) }

    override fun onDestroy() {
        realtimeConnections.forEach { it.close() }
        realtimeConnections.clear()
        assets.release()
        audio?.release()
        audio = null
        context = null
        super.onDestroy()
    }
}

/** Fluent wrapper around an engine GameObject and SpriteRenderer. */
class Sprite internal constructor(
    internal val gameObject: GameObject,
    private val renderer: SpriteRenderer
) {
    private var movement: MoveToBehavior? = null

    fun position(x: Float, y: Float) = apply {
        gameObject.transform.x = x; gameObject.transform.y = y
    }
    fun size(width: Float, height: Float) = apply {
        require(width.isFinite() && height.isFinite() && width > 0f && height > 0f)
        renderer.width = width
        renderer.height = height
        gameObject.getComponent(Collider2D::class.java)?.let {
            it.width = width
            it.height = height
        }
        gameObject.components.filterIsInstance<SpriteTapBehavior>().forEach {
            it.width = width
            it.height = height
        }
        gameObject.components.filterIsInstance<SpriteDragBehavior>().forEach {
            it.width = width
            it.height = height
        }
    }
    fun rotation(degrees: Float) = apply { gameObject.transform.rotation = degrees }
    fun scale(value: Float) = apply {
        require(value >= 0f); gameObject.transform.scaleX = value; gameObject.transform.scaleY = value
    }
    fun layer(value: Int) = apply { gameObject.renderLayer = value }
    fun alpha(value: Int) = apply { renderer.alpha = value.coerceIn(0, 255) }
    /** Run [callback] when this sprite is tapped and released by the same finger. */
    fun onTap(callback: () -> Unit) = apply {
        val existing = gameObject.components.filterIsInstance<SpriteTapBehavior>().firstOrNull()
        if (existing != null) existing.callback = callback
        else gameObject.addComponent(SpriteTapBehavior(renderer.width, renderer.height, callback))
    }
    fun moveBy(x: Float, y: Float) = apply {
        movement?.let(gameObject::removeComponent)
        movement = null
        gameObject.transform.x += x; gameObject.transform.y += y
    }

    /** Drag this sprite with one finger while preserving the grab offset. */
    fun draggable(
        enabled: Boolean = true,
        onMove: ((x: Float, y: Float) -> Unit)? = null,
        onDrop: (() -> Unit)? = null
    ) = apply {
        val existing = gameObject.components.filterIsInstance<SpriteDragBehavior>().firstOrNull()
        if (existing != null) {
            existing.enabled = enabled
            existing.onMove = onMove
            existing.onDrop = onDrop
        } else {
            gameObject.addComponent(
                SpriteDragBehavior(renderer.width, renderer.height, enabled, onMove, onDrop)
            )
        }
    }

    /** Move toward a point at the given world-units-per-second speed. */
    fun moveTo(x: Float, y: Float, speed: Float) = apply {
        require(speed > 0f) { "Movement speed must be greater than zero." }
        movement?.let(gameObject::removeComponent)
        MoveToBehavior(x, y, speed).also {
            movement = it
            gameObject.addComponent(it)
        }
    }

    fun stop() = apply {
        movement?.let(gameObject::removeComponent)
        movement = null
    }
}

private class MoveToBehavior(
    private val targetX: Float,
    private val targetY: Float,
    private val speed: Float
) : Component() {
    override fun update(deltaSeconds: Float) {
        if (deltaSeconds <= 0f) return
        val transform = gameObject.transform
        val dx = targetX - transform.x
        val dy = targetY - transform.y
        val distance = sqrt(dx * dx + dy * dy)
        val step = speed * deltaSeconds
        if (distance <= step || distance <= 0.001f) {
            transform.x = targetX
            transform.y = targetY
            gameObject.removeComponent(this)
        } else {
            transform.x += dx / distance * step
            transform.y += dy / distance * step
        }
    }
}

/** Fluent wrapper for a text label. */
class Label internal constructor(
    internal val gameObject: GameObject,
    private val renderer: TextRenderer
) {
    fun position(x: Float, y: Float) = apply {
        gameObject.transform.x = x; gameObject.transform.y = y
    }
    fun size(value: Float) = apply { renderer.textSize = value }
    fun color(value: Int) = apply { renderer.color = value }
    fun value(value: String) = apply { renderer.text = value }
}

/** Fluent wrapper for a button. */
class Button internal constructor(
    internal val gameObject: GameObject,
    private val control: UIButton
) {
    fun position(x: Float, y: Float) = apply {
        gameObject.transform.x = x; gameObject.transform.y = y
    }
    fun enabled(value: Boolean) = apply { control.enabled = value }
    fun label(value: String) = apply { control.text = value }
    fun onPress(callback: () -> Unit) = apply { control.onPress = callback }
    fun onRelease(callback: (releasedInside: Boolean) -> Unit) = apply { control.onRelease = callback }
}


/** Fluent wrapper for a background panel. */
class Panel internal constructor(
    internal val gameObject: GameObject,
    private val control: UIPanel
) {
    fun position(x: Float, y: Float) = apply {
        gameObject.transform.x = x; gameObject.transform.y = y
    }
    fun size(width: Float, height: Float) = apply {
        require(width > 0f && height > 0f)
        control.width = width; control.height = height
    }
    fun color(value: Int) = apply { control.color = value }
}

/** Fluent wrapper for a checkbox. */
class CheckBox internal constructor(
    internal val gameObject: GameObject,
    private val control: UICheckbox
) {
    fun position(x: Float, y: Float) = apply {
        gameObject.transform.x = x; gameObject.transform.y = y
    }
    fun checked(value: Boolean) = apply { control.checked = value }
    fun label(value: String) = apply { control.labelText = value }
    fun onChange(callback: (Boolean) -> Unit) = apply { control.onCheckedChanged = callback }
}

/** Fluent wrapper for a horizontal slider. */
class Slider internal constructor(
    internal val gameObject: GameObject,
    private val control: UISlider
) {
    fun position(x: Float, y: Float) = apply {
        gameObject.transform.x = x; gameObject.transform.y = y
    }
    fun size(width: Float) = apply {
        require(width > 0f); control.width = width
    }
    fun value(value: Float) = apply {
        require(value.isFinite()); control.value = value.coerceIn(control.minValue, control.maxValue)
    }
    fun onChange(callback: (Float) -> Unit) = apply { control.onValueChanged = callback }
}

/** Fluent wrapper for a progress bar. */
class ProgressBar internal constructor(
    internal val gameObject: GameObject,
    private val control: UIProgressBar
) {
    fun position(x: Float, y: Float) = apply {
        gameObject.transform.x = x; gameObject.transform.y = y
    }
    fun size(width: Float, height: Float = control.height) = apply {
        require(width > 0f && height > 0f)
        control.width = width; control.height = height
    }
    fun progress(value: Float) = apply {
        require(value.isFinite()); control.progress = value.coerceIn(0f, 1f)
    }
}

/** Fluent wrapper for a single-line text input connected to the Android keyboard. */
class TextInput internal constructor(
    internal val gameObject: GameObject,
    private val inputField: UITextField
) {
    fun position(x: Float, y: Float) = apply {
        gameObject.transform.x = x
        gameObject.transform.y = y
    }
    fun size(width: Float, height: Float) = apply {
        require(width > 0f && height > 0f)
        inputField.width = width
        inputField.height = height
    }
    fun value(value: String) = apply {
        require(value.codePointCount(0, value.length) <= inputField.maxLength) {
            "Text exceeds the configured maximum length."
        }
        inputField.text = value
    }
    fun clear() = apply { inputField.text = "" }
    val isFocused: Boolean get() = inputField.isFocused
    fun placeholder(value: String) = apply { inputField.placeholder = value }
    fun maxLength(value: Int) = apply {
        require(value >= 0)
        inputField.maxLength = value
        if (inputField.text.codePointCount(0, inputField.text.length) > value) {
            val end = inputField.text.offsetByCodePoints(0, value)
            inputField.text = inputField.text.substring(0, end)
        }
    }
    fun keyboardType(value: KeyboardType) = apply { inputField.inputType = value.inputType }
    fun onChange(callback: (String) -> Unit) = apply { inputField.onValueChanged = callback }
    fun onSubmit(callback: (String) -> Unit) = apply { inputField.onSubmit = callback }
    fun onFocusChange(callback: (Boolean) -> Unit) = apply { inputField.onFocusChanged = callback }

    enum class KeyboardType(val inputType: Int) {
        TEXT(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES),
        NUMBER(android.text.InputType.TYPE_CLASS_NUMBER),
        DECIMAL(android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED),
        PHONE(android.text.InputType.TYPE_CLASS_PHONE),
        EMAIL(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
    }
}


/** A touch button that calls its action every frame while held. */
class TouchButton internal constructor(
    internal val gameObject: GameObject,
    private val control: TouchButtonComponent
) {
    fun position(x: Float, y: Float) = apply {
        gameObject.transform.x = x
        gameObject.transform.y = y
    }
    fun size(width: Float, height: Float) = apply {
        require(width > 0f && height > 0f)
        control.width = width
        control.height = height
    }
    fun label(value: String) = apply { control.text = value }
    fun enabled(value: Boolean) = apply { control.enabled = value }
}

internal class TouchButtonComponent(
    var width: Float,
    var height: Float,
    var text: String,
    private val onHold: (Float) -> Unit
) : Component() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private var pointerId: Int? = null
    var enabled: Boolean = true

    override fun update(deltaSeconds: Float) {
        if (enabled && pointerId != null && deltaSeconds > 0f) onHold(deltaSeconds)
    }

    override fun draw(canvas: Canvas) {
        paint.color = if (!enabled) 0xFF596273.toInt()
            else if (pointerId != null) 0xFF2D5BAA.toInt() else 0xFF437EDC.toInt()
        canvas.drawRoundRect(-width / 2f, -height / 2f, width / 2f, height / 2f, 11f, 11f, paint)
        label.color = Color.WHITE
        label.textSize = (height * .32f).coerceAtLeast(12f)
        val baseline = -((label.descent() + label.ascent()) / 2f)
        canvas.drawText(text, 0f, baseline, label)
    }

    override fun onTouch(event: GameTouchEvent): Boolean {
        if (!enabled) { pointerId = null; return false }
        val dx = event.x - gameObject.transform.x
        val dy = event.y - gameObject.transform.y
        val inside = kotlin.math.abs(dx) <= width / 2f && kotlin.math.abs(dy) <= height / 2f
        return when (event.action) {
            GameTouchEvent.Action.DOWN -> if (inside && pointerId == null) {
                pointerId = event.pointerId; true
            } else false
            GameTouchEvent.Action.MOVE -> pointerId == event.pointerId
            GameTouchEvent.Action.UP, GameTouchEvent.Action.CANCEL -> if (pointerId == event.pointerId) {
                pointerId = null; true
            } else false
        }
    }
}

/** Fluent circle primitive. */
class Circle internal constructor(
    internal val gameObject: GameObject,
    private val renderer: CircleRenderer
) {
    fun position(x: Float, y: Float) = apply {
        gameObject.transform.x = x
        gameObject.transform.y = y
    }
    fun radius(value: Float) = apply { require(value > 0f); renderer.radius = value }
    fun color(value: Int) = apply { renderer.color = value }
}

internal class CircleRenderer(var radius: Float, var color: Int) : Component() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        paint.color = color
        canvas.drawCircle(0f, 0f, radius, paint)
    }
}


/** Pointer-safe drag behavior for beginner Sprite.draggable(). */
internal class SpriteDragBehavior(
    var width: Float,
    var height: Float,
    enabled: Boolean = true,
    var onMove: ((x: Float, y: Float) -> Unit)? = null,
    var onDrop: (() -> Unit)? = null
) : Component() {
    private var capturedPointer: Int? = null
    var enabled: Boolean = enabled
        set(value) {
            field = value
            if (!value) capturedPointer = null
        }
    private var offsetX = 0f
    private var offsetY = 0f

    override fun onTouch(event: GameTouchEvent): Boolean {
        if (!enabled) return false
        val transform = gameObject.transform
        val inside = if (transform.scaleX == 0f || transform.scaleY == 0f) false else {
            val dx = event.x - transform.x
            val dy = event.y - transform.y
            val radians = Math.toRadians(transform.rotation.toDouble())
            val cosine = kotlin.math.cos(radians).toFloat()
            val sine = kotlin.math.sin(radians).toFloat()
            val localX = (dx * cosine + dy * sine) / transform.scaleX
            val localY = (-dx * sine + dy * cosine) / transform.scaleY
            kotlin.math.abs(localX) <= width / 2f && kotlin.math.abs(localY) <= height / 2f
        }
        return when (event.action) {
            GameTouchEvent.Action.DOWN -> if (capturedPointer == null && inside) {
                capturedPointer = event.pointerId
                offsetX = transform.x - event.x
                offsetY = transform.y - event.y
                true
            } else false
            GameTouchEvent.Action.MOVE -> if (capturedPointer == event.pointerId) {
                transform.x = event.x + offsetX
                transform.y = event.y + offsetY
                onMove?.invoke(transform.x, transform.y)
                true
            } else false
            GameTouchEvent.Action.UP -> if (capturedPointer == event.pointerId) {
                capturedPointer = null
                onDrop?.invoke()
                true
            } else false
            GameTouchEvent.Action.CANCEL -> if (capturedPointer == event.pointerId) {
                capturedPointer = null
                true
            } else false
        }
    }

    override fun onDisable() {
        capturedPointer = null
    }
}

/** Pointer-safe tap behavior for beginner Sprite.onTap(). */
internal class SpriteTapBehavior(
    var width: Float,
    var height: Float,
    var callback: () -> Unit
) : Component() {
    private var capturedPointer: Int? = null
    private var pressedInside = false

    override fun onTouch(event: GameTouchEvent): Boolean {
        val transform = gameObject.transform
        val inside = if (transform.scaleX == 0f || transform.scaleY == 0f) false else {
            val dx = event.x - transform.x
            val dy = event.y - transform.y
            val radians = Math.toRadians(transform.rotation.toDouble())
            val cosine = kotlin.math.cos(radians).toFloat()
            val sine = kotlin.math.sin(radians).toFloat()
            val localX = (dx * cosine + dy * sine) / transform.scaleX
            val localY = (-dx * sine + dy * cosine) / transform.scaleY
            kotlin.math.abs(localX) <= width / 2f && kotlin.math.abs(localY) <= height / 2f
        }
        return when (event.action) {
            GameTouchEvent.Action.DOWN -> if (capturedPointer == null && inside) {
                capturedPointer = event.pointerId
                pressedInside = true
                true
            } else false
            GameTouchEvent.Action.MOVE -> if (capturedPointer == event.pointerId) {
                pressedInside = inside
                true
            } else false
            GameTouchEvent.Action.UP -> if (capturedPointer == event.pointerId) {
                val shouldTap = pressedInside && inside
                capturedPointer = null
                pressedInside = false
                if (shouldTap) callback()
                true
            } else false
            GameTouchEvent.Action.CANCEL -> if (capturedPointer == event.pointerId) {
                capturedPointer = null
                pressedInside = false
                true
            } else false
        }
    }

    override fun onDisable() {
        capturedPointer = null
        pressedInside = false
    }
}


/** Beginner-friendly handle for a short sound effect loaded from res/raw. */
class SoundEffect internal constructor(
    private val manager: AudioManager,
    private val sampleId: Int
) {
    private var effectVolume: Float = 1f

    /** True after Android has finished loading the sound sample. */
    val isReady: Boolean get() = manager.isSoundLoaded(sampleId)

    /** Play once; returns false while loading or when audio is unavailable. */
    fun play(): Boolean =
        manager.playSound(sampleId, volume = effectVolume * manager.sfxVolume) != 0

    /** Set this effect's own volume without changing other effects. */
    fun volume(value: Float) = apply {
        require(value.isFinite()) { "Sound effect volume must be finite." }
        effectVolume = value.coerceIn(0f, 1f)
    }
}