package com.isanabanana.stickmanmmo

import android.content.Context
import com.isanabanana.aim.GameObject
import com.isanabanana.aim.Scene
import org.json.JSONObject

class ArenaScene(private val appContext: Context) : Scene() {
    private var room: com.isanabanana.aim.RealtimeRoom? = null
    private lateinit var arena: ArenaController
    private var networkStatus = "ROOM: OFFLINE"

    override fun onCreate() {
        // Asset failures must not prevent the local training arena from starting.
        val atlas = runCatching { SpriteAtlas.load(appContext) }
            .getOrElse { SpriteAtlas.fallback() }

        arena = ArenaController(
            sprites = atlas,
            sendState = { x, y, facing, hp ->
                val payload = JSONObject()
                    .put("x", x).put("y", y)
                    .put("facing", facing).put("hp", hp)
                room?.sendEvent("state", payload)
            }
        )
        add(GameObject("ArenaController").apply { addComponent(arena) })

        // Multiplayer is optional for this first local-play test. A bad endpoint
        // or connection setup must never crash the game at launch.
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
        if (::arena.isInitialized) arena.networkLabel = networkStatus
    }

    override fun onDestroy() {
        runCatching { room?.leave() }
        room = null
        super.onDestroy()
    }
}
