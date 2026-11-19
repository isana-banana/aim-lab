# AIM Lab

A small practice project for learning to make Android 2D games with **AIM v0.5**.

## How we will use this repository

You tell me what game or feature you want to make. I will guide you with short, beginner-friendly steps. Each lesson changes only the files needed for that goal, then GitHub Actions builds an APK so you can test it on your Android phone.

## Project rules

- The default branch is `main`; it is the learning workspace.
- AIM v0.5 is used as the **single generated Kotlin file** `AIM.kt`.
- The generated file comes from the official AIM repository at the pinned v0.5 commit listed in `AIM_CORE_VERSION.md`.
- Do not edit the generated `AIM.kt` by hand. Edit the game code in `app/src/main/java/com/isanabanana/aimlab/MainActivity.kt`.
- No Android Studio or PC is required for this workflow.

## First screen

The starter app only shows a simple AIM Lab welcome screen. It is intentionally empty so the first tutorial can start from the basics.

## One-time GitHub setup

The source repository `isana-banana/aim` is private. To let GitHub Actions fetch the official AIM single-file source, add a fine-grained personal access token as a repository secret:

1. Create a fine-grained token in GitHub with access only to repository `isana-banana/aim` and **Contents: Read-only** permission.
2. In this repository, open Settings → Secrets and variables → Actions → New repository secret.
3. Name the secret `AIM_REPO_TOKEN` and paste the token value.

Keep the token private. It is only used by the build workflow to read AIM v0.5.

## Build and download

1. Open the [Actions page](https://github.com/isana-banana/aim-lab/actions).
2. Open the latest successful **Build AIM Lab** run on `main`.
3. Download the `aim-lab-debug-apk` artifact and install the APK on your phone.
4. The same run also uploads `aim-lab-aim-v0.5-single-file`, containing the generated AIM.kt used for that build.

## Learning workflow

1. Tell me the game you want to make or the next feature.
2. I explain the goal briefly and make the smallest code change.
3. GitHub Actions builds the project.
4. After the build succeeds, install and test it before we continue.


Starter reset: clean AIM v0.5 single-file learning workspace.
