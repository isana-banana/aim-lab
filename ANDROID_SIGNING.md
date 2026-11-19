# Android release signing

AIM Lab's release APK is signed only when all four repository Actions secrets are configured:

- `AIMLAB_KEYSTORE_BASE64`
- `AIMLAB_STORE_PASSWORD`
- `AIMLAB_KEY_PASSWORD`
- `AIMLAB_KEY_ALIAS` (value: `aimlab`)

The PKCS12 keystore is decoded only inside the GitHub Actions runner and is not committed to this repository. Keep a secure offline backup of the original keystore and passwords. Losing the keystore prevents installing future updates over builds signed with it.

When the secrets are absent, the workflow builds a debug APK as a fallback. When present, it builds a signed release APK and uploads it as `aim-lab-installable-apk`.

The signing certificate provides a stable app update identity; it does not bypass Android or Play Protect security checks.
