# AIM single-file version

AIM Lab uses the official generated single-file distribution from:

- Repository: https://github.com/isana-banana/aim
- Branch: `aim-v0.5`
- Pinned source commit: `0410ade9404c950d93802c7dd3f7e055280086fd`
- Generator: `tools/build_single_file.py`
- Generated output: `dist/AIM.kt`
- App destination during CI: `app/src/main/java/com/isanabanana/aim/AIM.kt`

The build workflow checks out this exact AIM commit, runs the official generator, copies the generated source into the app, and compiles it. This avoids relying on the older vendored `aim-core` module.

Do not edit the generated AIM.kt. Game-specific code belongs in the app source files.
