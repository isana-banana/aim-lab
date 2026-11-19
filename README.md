# AIM Lab

A collection of small Android 2D game projects used to learn, exercise, and validate **AIM Engine**.

## Current project: Stickman Arena

The first experiment is a landscape 2D action game:
- Monochrome paper arena with a stickman hero, sword and shield.
- Left virtual joystick and right-side Dodge / Attack controls.
- Three enemy types with different health and movement profiles.
- Enemy pursuit, hit feedback, health, score, defeat and respawn loop.
- Optional WebSocket room presence and remote-player state relay.

## Engine baseline

- Engine repository: https://github.com/isana-banana/aim
- Target branch: `aim-v0.5`
- Target commit: `0410ade9404c950d93802c7dd3f7e055280086fd`
- The currently vendored `aim-core/` directory is an older snapshot. It must be synchronized and regression-tested against the target commit before this project is considered a verified AIM v0.5 game. See [AIM_CORE_VERSION.md](AIM_CORE_VERSION.md).

The AIM repository is the engine source of truth. Do not develop engine changes in this repository; game-specific experiments belong here.

## Build on Android

1. Open **Actions** in this repository.
2. Run the Android build workflow.
3. Download the APK artifact from a successful run.
4. Install the APK on an Android phone.

GitHub Actions is used so development and APK builds can be managed from a phone without Android Studio or a PC.

## Multiplayer experiment

- WebSocket URL: `wss://aim-room-server.bravewarrior011.workers.dev/game?room=training`
- Room ID: `training`

The room server is currently a relay, not an authoritative combat server. Account identity, secure progression, matchmaking, persistence, anti-cheat, and reconnect reconciliation are not implemented.

## Working rules

- Keep the engine version explicit and reproducible.
- Keep each game experiment isolated from the AIM engine repository.
- Make small changes, build, and test before adding the next feature.
- Do not call a game AIM v0.5-verified until its engine snapshot or dependency matches the pinned commit and the APK has been tested.
