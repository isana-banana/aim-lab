# AIM Engine baseline

- Engine repository: https://github.com/isana-banana/aim
- Target branch: `aim-v0.5`
- Target commit: `0410ade9404c950d93802c7dd3f7e055280086fd`

## Snapshot status

The vendored `aim-core/` source in this repository came from the older AIM commit `005d8ff6e29a79b883a4000c4e86a26a82300365`. It is **not yet identical to the current aim-v0.5 branch**.

Do not treat this game as validated against the current AIM v0.5 API until the vendored module is synchronized with the target commit, the build succeeds, and the game is regression-tested.

The vendored module exists to let GitHub Actions build without cross-repository private checkout permissions. The original AIM repository is the source of truth and must not be modified by game-specific work.
