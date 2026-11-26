# WA-AudioSticker

Android Kotlin app for creating, trimming and sharing audio stickers for WhatsApp.

## CI

GitHub Actions automatically:
- Runs unit tests on pushes, pull requests and manual runs.
- Builds a debug APK and stores it as a workflow artifact (14-day retention).
- Builds a release APK and publishes it to **GitHub Releases** when a version tag beginning with `v` is pushed.

## Versioning

Use semantic version tags:
- `v1.0.0` — first stable release
- `v1.0.1` — patch/fix
- `v1.1.0` — backward-compatible feature
- `v2.0.0` — breaking change

Create a release by pushing a tag, for example:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The Android `versionName` is the tag; `versionCode` is the GitHub Actions run number.

## APK signing

The release workflow can sign using a stable Android keystore if these repository Actions secrets are configured:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded keystore file
- `ANDROID_STORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: key alias
- `ANDROID_KEY_PASSWORD`: key password

If those secrets are not configured, CI uses the standard temporary runner debug signing key as a fallback, so the APK can still be built and published for testing. **That fallback does not guarantee that future APKs can update an earlier installation.** For reliable in-place updates, configure a private, persistent signing key before distributing production builds. Never commit the keystore or passwords.

## Build configuration

- Kotlin + Jetpack Compose
- Android Gradle Plugin and Gradle versions from `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`
- Minimum Android API: 24
- CI Java: 17
- Release builds use version values supplied by GitHub Actions; local defaults remain available.

## Current repository notes

The application ID currently remains `com.example` to avoid changing app identity during this packaging setup. Before public distribution, it should be reviewed and changed to a unique application ID, which would create a separate app identity on Android.
