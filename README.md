# Stickman MMO

A small-room 2D multiplayer stickman action prototype built as a separate project using AIM v0.5.

## Initial vertical slice
- Landscape Android game
- Monochrome paper-like arena
- Left virtual joystick; right attack and dodge controls
- Stickman hero with equipment
- Three enemy types
- Health, hit feedback, and respawn
- Optional WebSocket room presence and player movement

## Realtime endpoint
`wss://aim-room-server.bravewarrior011.workers.dev/game?room=training`

Room ID: `training`

This is an early prototype. The relay server is not authoritative for combat and does not provide production MMO security or persistence yet.
