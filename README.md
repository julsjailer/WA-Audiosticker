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

The application ID is `io.github.julsjailer.audiostickers`. It installs next to older `com.example` builds; their saved gallery is not migrated.

## Experimento: stickers con sonido en WhatsApp Web

La app puede abrir WhatsApp Web con el código de [WA-audio-stickers](https://github.com/Shirtep/WA-audio-stickers) (`app/src/main/assets/waas`, **solo uso no comercial**). Es no oficial: WhatsApp puede restringir la cuenta y puede dejar de funcionar sin aviso.

## Cómo funcionan los stickers

- **Paquetes para WhatsApp:** stickers estáticos 512×512 (máximo 100 KB, de 3 a 30 por paquete, hasta 10 paquetes). **No llevan sonido**: el formato de paquetes no lo admite. Reexportar con el mismo nombre actualiza el paquete.
- **Stickers con sonido:** la app genera un video con audio. El sticker lo crea WhatsApp (Stickers → Crear sticker), solo en cuentas que ya tengan la función. WhatsApp une el sticker y el audio al enviar; los paquetes y el compartir de Android no pueden hacerlo (ver el experimento).
- **Galería de la app:** de un video solo guarda un cuadro fijo y el audio.
