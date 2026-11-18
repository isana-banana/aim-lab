package com.isanabanana.stickmanmmo

import com.isanabanana.aim.GameObject
import com.isanabanana.aim.Scene
import org.json.JSONObject

class ArenaScene : Scene() {
    private var room: com.isanabanana.aim.RealtimeRoom? = null
    private lateinit var arena: ArenaController
    private var networkStatus = "CONNECTING"

    override fun onCreate() {
        arena = ArenaController(
            sendState = { x, y, facing, hp ->
                val payload = JSONObject()
                    .put("x", x).put("y", y)
                    .put("facing", facing).put("hp", hp)
                room?.sendEvent("state", payload)
            }
        )
        add(GameObject("ArenaController").apply { addComponent(arena) })

        room = joinRoom(
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
    }

    override fun onUpdate(delta: Float) {
        arena.networkLabel = networkStatus
    }

    override fun onDestroy() {
        room?.leave()
        room = null
        super.onDestroy()
    }
}
