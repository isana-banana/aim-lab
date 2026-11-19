# AIM single-file version

AIM Lab uses the generated single-file distribution with adaptive EXPAND viewport from:

- Repository: https://github.com/isana-banana/aim
- Branch: `feature/expand-viewport`
- Pinned source commit: `10796d641ae22d187c6d2e653b0b9db6a3e13885`
- Generator: `tools/build_single_file.py`
- Generated output: `dist/AIM.kt`
- App destination during CI: `app/src/main/java/com/isanabanana/aim/AIM.kt`

The build workflow checks out this exact AIM commit, runs the official generator, copies the generated source into the app, and compiles it. This avoids relying on the older vendored `aim-core` module.

Do not edit the generated AIM.kt. Game-specific code belongs in the app source files.
