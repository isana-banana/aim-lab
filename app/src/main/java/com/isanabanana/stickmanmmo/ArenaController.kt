package com.isanabanana.stickmanmmo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.isanabanana.aim.Component
import com.isanabanana.aim.GameTouchEvent
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * First playable paper-arena slice. Drawing and touch handling live in one AIM component,
 * keeping the game logic independent from Android Activity code.
 */
class ArenaController(
    private val sendState: (Float, Float, Int, Int) -> Unit
) : Component() {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(35, 35, 35); strokeWidth = 4f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(35, 35, 35); textSize = 17f; typeface = android.graphics.Typeface.create("sans-serif",  android.graphics.Typeface.BOLD)
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
    private var dodgeTime = 0f
    private var invulnerable = 0f
    private var sendTimer = 0f
    private var hitFlash = 0f
    private var score = 0
    private var elapsed = 0f
    private var remoteX: Float? = null
    private var remoteY = 0f
    private var remoteFacing = 1
    private val mobs = mutableListOf(
        Mob(690f, 270f, 1, 70, 1.0f),
        Mob(770f, 370f, 2, 110, 0.72f),
        Mob(610f, 420f, 3, 55, 1.25f)
    )
    var networkLabel = "ROOM: CONNECTING"

    fun updateRemote(x: Float, y: Float, direction: Int, remoteHp: Int) {
        remoteX = x.coerceIn(80f, 880f)
        remoteY = y.coerceIn(145f, 475f)
        remoteFacing = if (direction < 0) -1 else 1
    }

    override fun update(deltaSeconds: Float) {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        elapsed += dt
        attackCooldown = max(0f, attackCooldown - dt)
        dodgeTime = max(0f, dodgeTime - dt)
        invulnerable = max(0f, invulnerable - dt)
        hitFlash = max(0f, hitFlash - dt)
        px = (px + joystickX * 205f * dt).coerceIn(55f, 905f)
        py = (py + joystickY * 170f * dt).coerceIn(165f, 465f)
        if (abs(joystickX) > 0.08f) facing = if (joystickX > 0f) 1 else -1
        if (dodgeHeld && dodgeTime <= 0f) {
            px = (px + facing * 62f).coerceIn(55f, 905f)
            dodgeTime = 0.55f
            invulnerable = 0.48f
            dodgeHeld = false
        }
        if (attackHeld && attackCooldown <= 0f) {
            attackCooldown = 0.38f
            val target = mobs.filter { it.hp > 0 && abs(it.x - (px + facing * 55f)) < 100f && abs(it.y - py) < 75f }
                .minByOrNull { abs(it.x - px) + abs(it.y - py) }
            if (target != null) {
                target.hp -= 35
                target.flash = 0.16f
                if (target.hp <= 0) score += 10
            }
        }
        mobs.forEach { mob ->
            mob.flash = max(0f, mob.flash - dt)
            if (mob.hp > 0) {
                val dx = px - mob.x
                val dy = py - mob.y
                val distance = hypot(dx, dy).coerceAtLeast(1f)
                if (distance > 54f) {
                    mob.x += dx / distance * mob.speed * dt
                    mob.y += dy / distance * mob.speed * dt
                } else if (invulnerable <= 0f) {
                    mob.attackTimer -= dt
                    if (mob.attackTimer <= 0f) {
                        hp = (hp - 8).coerceAtLeast(0)
                        mob.attackTimer = 0.75f
                        hitFlash = 0.12f
                    }
                }
            } else if (mob.respawn <= 0f) {
                mob.respawn = 3f
            } else {
                mob.respawn -= dt
                if (mob.respawn <= 0f) {
                    mob.hp = mob.maxHp
                    mob.x = 650f + (mobs.indexOf(mob) * 95f)
                    mob.y = 220f + (mobs.indexOf(mob) * 85f)
                }
            }
        }
        if (hp <= 0) {
            hp = 100
            px = 465f; py = 325f
            mobs.forEach { it.x += 35f; it.y -= 20f }
        }
        sendTimer += dt
        if (sendTimer >= 0.10f) {
            sendTimer = 0f
            sendState(px, py, facing, hp)
        }
    }

    override fun onTouch(event: GameTouchEvent): Boolean {
        when (event.action) {
            GameTouchEvent.Action.DOWN -> {
                if (event.x < 280f && joystickPointer == -1) {
                    joystickPointer = event.pointerId
                    updateStick(event.x, event.y)
                    return true
                }
                if (event.x > 790f && event.y > 360f) {
                    attackHeld = true
                    return true
                }
                if (event.x > 650f && event.y > 360f) {
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
                    joystickPointer = -1; joystickX = 0f; joystickY = 0f
                    return true
                }
                if (event.x > 650f) {
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
        // Paper and hand-ruled arena.
        fill.color = Color.rgb(250, 249, 245)
        canvas.drawColor(fill.color)
        ink.color = Color.rgb(225, 222, 214); ink.strokeWidth = 1.4f
        for (x in 0..960 step 48) canvas.drawLine(x.toFloat(), 115f, x.toFloat(), 520f, ink)
        for (y in 115..520 step 48) canvas.drawLine(24f, y.toFloat(), 936f, y.toFloat(), ink)
        ink.color = Color.rgb(65, 65, 65); ink.strokeWidth = 3f
        canvas.drawRoundRect(24f, 112f, 936f, 520f, 18f, 18f, ink)
        text.color = Color.rgb(40, 40, 40); text.textSize = 22f
        canvas.drawText("PAPER ARENA  /  TRAINING ROOM", 34f, 42f, text)
        text.textSize = 15f
        canvas.drawText(networkLabel, 35f, 72f, text)
        canvas.drawText("SCORE  $score", 820f, 42f, text)
        // Health.
        fill.color = Color.rgb(220, 220, 220)
        canvas.drawRoundRect(34f, 84f, 234f, 98f, 7f, 7f, fill)
        fill.color = if (hp > 30) Color.rgb(50, 50, 50) else Color.rgb(150, 55, 55)
        canvas.drawRoundRect(34f, 84f, 34f + 2f * hp, 98f, 7f, 7f, fill)
        text.textSize = 13f; text.color = Color.DKGRAY
        canvas.drawText("HP $hp / 100", 245f, 96f, text)
        // Mob names and figures.
        mobs.forEachIndexed { index, mob ->
            if (mob.hp > 0) {
                drawStickman(canvas, mob.x, mob.y, -1, if (mob.flash > 0f) Color.RED else Color.rgb(95, 95, 95), 0.88f, index + 1)
                fill.color = Color.LTGRAY
                canvas.drawRect(mob.x - 22f, mob.y - 58f, mob.x + 22f, mob.y - 53f, fill)
                fill.color = Color.DKGRAY
                canvas.drawRect(mob.x - 22f, mob.y - 58f, mob.x - 22f + 44f * mob.hp / mob.maxHp, mob.y - 53f, fill)
            }
        }
        remoteX?.let { drawStickman(canvas, it, remoteY, remoteFacing, Color.rgb(90, 110, 130), 1f, 0) }
        drawStickman(canvas, px, py, facing, if (hitFlash > 0f) Color.RED else Color.BLACK, 1.12f, 0)
        // Sword and shield hint.
        ink.color = Color.rgb(35, 35, 35); ink.strokeWidth = 5f
        canvas.drawLine(px + facing * 15f, py - 20f, px + facing * 54f, py - 44f, ink)
        canvas.drawCircle(px - facing * 17f, py - 10f, 13f, ink)
        // Virtual joystick.
        fill.color = Color.argb(55, 30, 30, 30)
        canvas.drawCircle(130f, 410f, 66f, fill)
        ink.color = Color.rgb(75, 75, 75); ink.strokeWidth = 3f
        canvas.drawCircle(130f, 410f, 66f, ink)
        fill.color = Color.argb(100, 70, 70, 70)
        canvas.drawCircle(130f + joystickX * 35f, 410f + joystickY * 35f, 25f, fill)
        // Action controls.
        drawControl(canvas, 735f, 438f, 47f, "DODGE")
        drawControl(canvas, 860f, 438f, 57f, "ATTACK")
        text.color = Color.GRAY; text.textSize = 12f
        canvas.drawText("MOVE", 107f, 500f, text)
        canvas.drawText("SWORD / SHIELD / LIGHT ARMOR", 335f, 505f, text)
    }

    private fun drawControl(canvas: Canvas, x: Float, y: Float, radius: Float, label: String) {
        fill.color = Color.argb(35, 20, 20, 20)
        canvas.drawCircle(x, y, radius, fill)
        ink.color = Color.rgb(55, 55, 55); ink.strokeWidth = 3f
        canvas.drawCircle(x, y, radius, ink)
        text.color = Color.rgb(40, 40, 40); text.textSize = 13f
        canvas.drawText(label, x, y + radius + 19f, text)
    }

    private fun drawStickman(canvas: Canvas, x: Float, y: Float, direction: Int, color: Int, scale: Float, type: Int) {
        val save = canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale * direction, scale)
        ink.color = color; ink.strokeWidth = if (type == 0) 5f else 4f
        canvas.drawCircle(0f, -48f, 12f, ink)
        canvas.drawLine(0f, -35f, 0f, 4f, ink)
        canvas.drawLine(0f, -25f, -19f, -5f, ink)
        canvas.drawLine(0f, -25f, 20f, -8f, ink)
        canvas.drawLine(0f, 4f, -15f, 31f, ink)
        canvas.drawLine(0f, 4f, 17f, 30f, ink)
        if (type == 1) {
            ink.strokeWidth = 7f
            canvas.drawLine(-12f, -35f, 13f, -35f, ink)
        } else if (type == 2) {
            canvas.drawCircle(-19f, -10f, 9f, ink)
        } else if (type == 3) {
            canvas.drawLine(-10f, -34f, 10f, -34f, ink)
            canvas.drawLine(-10f, -30f, 10f, -30f, ink)
        }
        canvas.restoreToCount(save)
    }

    private data class Mob(
        var x: Float, var y: Float, val type: Int, var hp: Int, val speed: Float,
        val maxHp: Int = hp, var flash: Float = 0f, var respawn: Float = 0f, var attackTimer: Float = 0f
    )
}
