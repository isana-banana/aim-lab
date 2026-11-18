# Stickman MMO

A landscape Android 2D action prototype using **AIM v0.5** as a separate dependency. The AIM repository is checked out by CI; this project does not modify AIM.

## Playable prototype
- Monochrome paper arena with a stickman hero, sword and shield.
- Left virtual joystick.
- Right-side Dodge and Attack controls.
- Three enemy silhouettes with different health/speed profiles.
- Enemy pursuit, hit feedback, health, score, defeat and respawn loop.
- Optional WebSocket room presence and remote-player state relay.

## Build and install on Android
1. Open **Actions** in this repository.
2. Select **Build Stickman MMO** and open the latest successful run.
3. Under **Artifacts**, download `stickman-mmo-debug-apk`.
4. Extract the ZIP and install `app-debug.apk` on the Android phone. Allow installation from that file source if Android asks.

The workflow fetches `isana-banana/aim` at branch `aim-v0.5`, copies only its `aim-core` module into the build workspace, then builds the APK with Gradle. No Android Studio or PC is required on the phone.

## Multiplayer room
- WebSocket URL: `wss://aim-room-server.bravewarrior011.workers.dev/game?room=training`
- Room ID: `training`

The game sends periodic `state` room events. The connection status is shown at the top of the arena. A successful APK build does not prove that the remote Worker speaks the same room protocol; test with two devices to verify actual synchronization.

## Prototype limitations
This is an early playable slice, not a production MMO. The room server is a relay, not an authoritative combat server; account identity, secure progression, matchmaking, persistence, anti-cheat, and reconnect reconciliation are not implemented. Background music is not bundled yet because the audio asset is not present in this repository.
