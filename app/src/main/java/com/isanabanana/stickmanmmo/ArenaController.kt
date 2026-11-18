package com.isanabanana.stickmanmmo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.isanabanana.aim.Component
import com.isanabanana.aim.GameTouchEvent
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class ArenaController(
    private val sprites: SpriteAtlas,
    private val sendState: (Float, Float, Int, Int) -> Unit
) : Component() {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(35, 35, 35)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(35, 35, 35)
        textSize = 17f
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }

    private var px = 465f
    private var py = 325f
    private var facing = 1
    private var hp = 100
    private var joystickPointer = -1
    private var joystickX = 0f
    private var joystickY = 0f
    private var attackHeld = false
    private var dodgeHeld = false
    private var attackCooldown = 0f
    private var dodgeCooldown = 0f
    private var invulnerable = 0f
    private var sendTimer = 0f
    private var hitFlash = 0f
    private var score = 0
    private var animationClock = 0f
    private var animationFrame = 0
    private var remoteX: Float? = null
    private var remoteY = 0f
    private var remoteFacing = 1

    // Mob 1: fast chaser, low HP and low damage.
    // Mob 2: slow chaser, high HP and heavier hits.
    // Mob 3: stationary guard, highest HP; it only turns to face the player.
    private val mobs = mutableListOf(
        Mob(690f, 270f, Kind.FAST, 45, 128f, 6),
        Mob(770f, 370f, Kind.HEAVY, 125, 43f, 13),
        Mob(610f, 420f, Kind.GUARD, 260, 0f, 22)
    )
    var networkLabel = "ROOM: CONNECTING"

    fun updateRemote(x: Float, y: Float, direction: Int, remoteHp: Int) {
        remoteX = x.coerceIn(80f, 880f)
        remoteY = y.coerceIn(145f, 475f)
        remoteFacing = direction
    }

    override fun update(deltaSeconds: Float) {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        attackCooldown = max(0f, attackCooldown - dt)
        dodgeCooldown = max(0f, dodgeCooldown - dt)
        invulnerable = max(0f, invulnerable - dt)
        hitFlash = max(0f, hitFlash - dt)

        px = (px + joystickX * 205f * dt).coerceIn(55f, 905f)
        py = (py + joystickY * 170f * dt).coerceIn(165f, 465f)
        if (abs(joystickX) > abs(joystickY) && abs(joystickX) > 0.08f) facing = if (joystickX > 0f) 1 else -1
        else if (abs(joystickY) > 0.08f) facing = if (joystickY > 0f) 0 else 2

        if (dodgeHeld && dodgeCooldown <= 0f) {
            px = (px + if (facing == -1) -70f else if (facing == 1) 70f else 0f).coerceIn(55f, 905f)
            py = (py + if (facing == 0) 55f else if (facing == 2) -55f else 0f).coerceIn(165f, 465f)
            dodgeCooldown = 0.65f
            invulnerable = 0.48f
            dodgeHeld = false
        }

        if (attackHeld && attackCooldown <= 0f) {
            attackCooldown = 0.36f
            val target = mobs.filter { it.hp > 0 && abs(it.x - px) < 88f && abs(it.y - py) < 78f }
                .minByOrNull { abs(it.x - px) + abs(it.y - py) }
            if (target != null) {
                target.hp -= if (target.kind == Kind.GUARD) 32 else 38
                target.flash = 0.18f
                if (target.hp <= 0) score += when (target.kind) {
                    Kind.FAST -> 10
                    Kind.HEAVY -> 20
                    Kind.GUARD -> 40
                }
            }
        }

        mobs.forEach { mob ->
            mob.flash = max(0f, mob.flash - dt)
            if (mob.hp <= 0) {
                mob.respawn -= dt
                if (mob.respawn <= 0f) {
                    mob.hp = mob.maxHp
                    mob.x = when (mob.kind) {
                        Kind.FAST -> 690f
                        Kind.HEAVY -> 770f
                        Kind.GUARD -> 610f
                    }
                    mob.y = when (mob.kind) {
                        Kind.FAST -> 270f
                        Kind.HEAVY -> 370f
                        Kind.GUARD -> 420f
                    }
                }
                return@forEach
            }

            val dx = px - mob.x
            val dy = py - mob.y
            mob.direction = directionTo(dx, dy)
            if (mob.kind != Kind.GUARD) {
                val distance = hypot(dx, dy).coerceAtLeast(1f)
                if (distance > 50f) {
                    mob.x += dx / distance * mob.speed * dt
                    mob.y += dy / distance * mob.speed * dt
                }
            }
            val distanceToPlayer = hypot(px - mob.x, py - mob.y)
            if (distanceToPlayer < if (mob.kind == Kind.GUARD) 62f else 48f && invulnerable <= 0f) {
                mob.attackTimer -= dt
                if (mob.attackTimer <= 0f) {
                    hp = (hp - mob.damage).coerceAtLeast(0)
                    mob.attackTimer = if (mob.kind == Kind.FAST) 1.0f else if (mob.kind == Kind.HEAVY) 1.35f else 1.6f
                    hitFlash = 0.16f
                }
            }
        }

        if (hp <= 0) {
            hp = 100
            px = 465f
            py = 325f
            invulnerable = 1.5f
        }

        if (abs(joystickX) + abs(joystickY) > 0.12f) {
            animationClock += dt
            if (animationClock >= 0.13f) {
                animationClock = 0f
                animationFrame = (animationFrame + 1) % 3
            }
        } else {
            animationClock = 0f
            animationFrame = 1
        }

        sendTimer += dt
        if (sendTimer >= 0.10f) {
            sendTimer = 0f
            sendState(px, py, facing, hp)
        }
    }

    private fun directionTo(dx: Float, dy: Float): Int =
        if (abs(dx) > abs(dy)) if (dx >= 0f) 1 else -1 else if (dy >= 0f) 0 else 2

    override fun onTouch(event: GameTouchEvent): Boolean {
        when (event.action) {
            GameTouchEvent.Action.DOWN -> {
                if (event.x < 280f && joystickPointer == -1) {
                    joystickPointer = event.pointerId
                    updateStick(event.x, event.y)
                    return true
                }
                if (event.x > 800f && event.y > 360f) {
                    attackHeld = true
                    return true
                }
                if (event.x > 660f && event.y > 360f) {
                    dodgeHeld = true
                    return true
                }
            }
            GameTouchEvent.Action.MOVE -> if (event.pointerId == joystickPointer) {
                updateStick(event.x, event.y)
                return true
            }
            GameTouchEvent.Action.UP, GameTouchEvent.Action.CANCEL -> {
                if (event.pointerId == joystickPointer) {
                    joystickPointer = -1
                    joystickX = 0f
                    joystickY = 0f
                    return true
                }
                if (event.x > 660f) {
                    attackHeld = false
                    return true
                }
            }
        }
        return false
    }

    private fun updateStick(x: Float, y: Float) {
        val dx = x - 130f
        val dy = y - 410f
        val length = hypot(dx, dy).coerceAtLeast(1f)
        joystickX = (dx / max(90f, length)).coerceIn(-1f, 1f)
        joystickY = (dy / max(90f, length)).coerceIn(-1f, 1f)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(250, 249, 245))
        ink.color = Color.rgb(225, 222, 214)
        ink.strokeWidth = 1.2f
        for (x in 0..960 step 48) canvas.drawLine(x.toFloat(), 115f, x.toFloat(), 520f, ink)
        for (y in 115..520 step 48) canvas.drawLine(24f, y.toFloat(), 936f, y.toFloat(), ink)
        ink.color = Color.rgb(65, 65, 65)
        ink.strokeWidth = 3f
        canvas.drawRoundRect(24f, 112f, 936f, 520f, 18f, 18f, ink)

        text.color = Color.rgb(40, 40, 40)
        text.textSize = 22f
        canvas.drawText("PAPER ARENA  /  TRAINING ROOM", 34f, 42f, text)
        text.textSize = 15f
        canvas.drawText(networkLabel, 35f, 72f, text)
        canvas.drawText("SCORE  $score", 820f, 42f, text)

        fill.color = Color.rgb(220, 220, 220)
        canvas.drawRoundRect(34f, 84f, 234f, 98f, 7f, 7f, fill)
        fill.color = if (hp > 30) Color.rgb(50, 50, 50) else Color.rgb(150, 55, 55)
        canvas.drawRoundRect(34f, 84f, 34f + 2f * hp, 98f, 7f, 7f, fill)
        text.textSize = 13f
        text.color = Color.DKGRAY
        canvas.drawText("HP $hp / 100", 245f, 96f, text)

        mobs.forEach { mob ->
            if (mob.hp > 0) {
                drawShadow(canvas, mob.x, mob.y)
                val bitmap = when (mob.kind) {
                    Kind.FAST -> sprites.fastMob
                    Kind.HEAVY -> sprites.heavyMob
                    Kind.GUARD -> sprites.guardMob
                }
                val frame = ((System.currentTimeMillis() / 170L) % 3L).toInt()
                sprites.draw(canvas, bitmap, mob.x, mob.y, mob.direction, frame, if (mob.kind == Kind.GUARD) 1.42f else 1.18f)
                fill.color = Color.LTGRAY
                canvas.drawRect(mob.x - 25f, mob.y - 62f, mob.x + 25f, mob.y - 57f, fill)
                fill.color = if (mob.flash > 0f) Color.RED else Color.DKGRAY
                canvas.drawRect(mob.x - 25f, mob.y - 62f, mob.x - 25f + 50f * mob.hp / mob.maxHp, mob.y - 57f, fill)
            }
        }

        remoteX?.let { x ->
            drawShadow(canvas, x, remoteY)
            sprites.draw(canvas, sprites.player, x, remoteY, remoteFacing, 1, 1.22f)
        }
        drawShadow(canvas, px, py)
        sprites.draw(canvas, sprites.player, px, py, facing, animationFrame, 1.38f)

        fill.color = Color.argb(55, 30, 30, 30)
        canvas.drawCircle(130f, 410f, 66f, fill)
        ink.color = Color.rgb(75, 75, 75)
        ink.strokeWidth = 3f
        canvas.drawCircle(130f, 410f, 66f, ink)
        fill.color = Color.argb(100, 70, 70, 70)
        canvas.drawCircle(130f + joystickX * 35f, 410f + joystickY * 35f, 25f, fill)
        drawControl(canvas, 735f, 438f, 47f, "DODGE")
        drawControl(canvas, 860f, 438f, 57f, "ATTACK")
        text.color = Color.GRAY
        text.textSize = 12f
        canvas.drawText("MOVE", 107f, 500f, text)
        canvas.drawText("MC / FAST / HEAVY / GUARD", 365f, 505f, text)
    }

    private fun drawShadow(canvas: Canvas, x: Float, y: Float) {
        fill.color = Color.argb(35, 0, 0, 0)
        canvas.drawOval(x - 18f, y - 5f, x + 18f, y + 3f, fill)
    }

    private fun drawControl(canvas: Canvas, x: Float, y: Float, radius: Float, label: String) {
        fill.color = Color.argb(35, 20, 20, 20)
        canvas.drawCircle(x, y, radius, fill)
        ink.color = Color.rgb(55, 55, 55)
        ink.strokeWidth = 3f
        canvas.drawCircle(x, y, radius, ink)
        text.color = Color.rgb(40, 40, 40)
        text.textSize = 13f
        canvas.drawText(label, x, y + radius + 19f, text)
    }

    private enum class Kind { FAST, HEAVY, GUARD }
    private data class Mob(
        var x: Float,
        var y: Float,
        val kind: Kind,
        var hp: Int,
        val speed: Float,
        val damage: Int,
        val maxHp: Int = hp,
        var flash: Float = 0f,
        var respawn: Float = 3f,
        var attackTimer: Float = 0f,
        var direction: Int = -1
    )
}
