package com.isanabanana.stickmanmmo

import com.isanabanana.aim.AnimatedSprite
import com.isanabanana.aim.RealtimeRoom
import com.isanabanana.aim.Scene
import org.json.JSONObject

class ArenaScene : Scene() {
    private var room: RealtimeRoom? = null
    private lateinit var arena: ArenaController
    private lateinit var hero: AnimatedSprite
    private lateinit var fastMob: AnimatedSprite
    private lateinit var heavyMob: AnimatedSprite
    private lateinit var guardMob: AnimatedSprite
    private var networkStatus = "ROOM: OFFLINE"

    override fun onCreate() {
        // Music is now handled by AIM's beginner audio API.
        // The resource is optional until nature.mp3 is committed to res/raw.
        musicVolume(0.55f)
        runCatching { music("nature", loop = true) }

        arena = ArenaController(
            sendState = { x, y, facing, hp ->
                val payload = JSONObject()
                    .put("x", x).put("y", y)
                    .put("facing", facing).put("hp", hp)
                room?.sendEvent("state", payload)
            }
        )
        add(com.isanabanana.aim.GameObject("ArenaController").apply {
            addComponent(arena)
        })

        // All character sheets are rendered by AIM's SpriteSheetRenderer.
        // If a single image is invalid, use the valid player sheet as a safe fallback.
        fun sheet(name: String, x: Float, y: Float, w: Float, h: Float): AnimatedSprite =
            runCatching {
                spriteSheet(name, 32, 32, columns = 3, rows = 4, x = x, y = y, width = w, height = h)
            }.getOrElse {
                spriteSheet("player", 32, 32, columns = 3, rows = 4, x = x, y = y, width = w, height = h)
            }

        hero = sheet("player", 465f, 325f, 58f, 66f)
        fastMob = sheet("mob_fast", 690f, 270f, 50f, 58f)
        heavyMob = sheet("mob_heavy", 770f, 370f, 50f, 58f)
        guardMob = sheet("mob_guard", 610f, 420f, 60f, 70f)

        // AIM beginner analog joystick and touch buttons replace custom touch parsing.
        joystick(x = 130f, y = 440f, radius = 66f) { x, y ->
            arena.setMovement(x, y)
        }
        button("DODGE", x = 735f, y = 445f, width = 104f, height = 94f) {
            arena.requestDodge()
        }
        button("ATTACK", x = 860f, y = 445f, width = 118f, height = 108f) {
            arena.tapAttack()
        }

        // Multiplayer remains optional; offline play still works without the room server.
        room = runCatching {
            joinRoom(
                url = "wss://aim-room-server.bravewarrior011.workers.dev/game?room=training",
                roomId = "training",
                onConnected = { networkStatus = "ROOM: CONNECTED" },
                onMessage = { raw ->
                    runCatching {
                        val message = JSONObject(raw)
                        if (message.optString("event") == "state") {
                            val data = message.optJSONObject("data") ?: return@runCatching
                            arena.updateRemote(
                                data.optDouble("x", 480.0).toFloat(),
                                data.optDouble("y", 300.0).toFloat(),
                                data.optInt("facing", 1),
                                data.optInt("hp", 100)
                            )
                        }
                    }
                },
                onClosed = { _, _ -> networkStatus = "ROOM: RECONNECTING" },
                onError = { networkStatus = "ROOM: OFFLINE" },
                autoReconnect = true
            )
        }.onFailure {
            networkStatus = "ROOM: OFFLINE"
        }.getOrNull()
    }

    override fun onUpdate(delta: Float) {
        if (!::arena.isInitialized) return
        arena.networkLabel = networkStatus
        sync(hero, arena.playerX, arena.playerY, arena.playerFacing, arena.playerFrame, true)
        sync(fastMob, arena.fastView.x, arena.fastView.y, arena.fastView.direction, arena.fastView.frame, arena.fastView.alive)
        sync(heavyMob, arena.heavyView.x, arena.heavyView.y, arena.heavyView.direction, arena.heavyView.frame, arena.heavyView.alive)
        sync(guardMob, arena.guardView.x, arena.guardView.y, arena.guardView.direction, arena.guardView.frame, arena.guardView.alive)
    }

    private fun sync(
        sprite: AnimatedSprite,
        x: Float,
        y: Float,
        facing: Int,
        frame: Int,
        visible: Boolean
    ) {
        val row = when (facing) { 0 -> 0; -1 -> 1; 1 -> 2; else -> 3 }
        sprite.position(x, y).direction(row).frame(frame).alpha(if (visible) 255 else 0)
    }

    override fun onDestroy() {
        runCatching { room?.leave() }
        room = null
        stopMusic()
        super.onDestroy()
    }
}
