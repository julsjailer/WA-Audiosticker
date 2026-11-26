# WA-AudioSticker

Android Kotlin app for creating, trimming and sharing audio stickers for WhatsApp.

## Descargar el APK

Cada cambio enviado a `main` o `experimento/whatsapp-web` publica el APK en **[Releases](https://github.com/julsjailer/WA-Audiosticker/releases)**: abre la versión más reciente y descarga el archivo `.apk` de **Assets**.

## CI

GitHub Actions automáticamente:
- Ejecuta las pruebas en pushes, pull requests y ejecuciones manuales.
- Compila el APK de depuración y el de publicación (artefactos, 14 días).
- Publica el APK de publicación en GitHub Releases desde `main`, `experimento/whatsapp-web` y las etiquetas `v*`.

## Versiones

Las compilaciones de rama se llaman `0.2.0-beta.N` (N es el número de ejecución, que también es el `versionCode`). Para una versión estable, crea una etiqueta:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Firma del APK

Para que cada versión se instale encima de la anterior, configura estos secretos en **Settings → Secrets and variables → Actions**:

- `ANDROID_KEYSTORE_BASE64`: archivo del almacén de claves en base64
- `ANDROID_STORE_PASSWORD`: contraseña del almacén
- `ANDROID_KEY_ALIAS`: alias de la clave
- `ANDROID_KEY_PASSWORD`: contraseña de la clave

Sin ellos se usa una clave temporal distinta en cada compilación y hay que desinstalar antes de instalar. Guarda una copia de la clave y las contraseñas en un lugar privado y nunca las subas al repositorio.

## Build configuration

- Kotlin + Jetpack Compose
- Android Gradle Plugin and Gradle versions from `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`
- Minimum Android API: 24
- CI Java: 17
- Release builds use version values supplied by GitHub Actions; local defaults remain available.

## Current repository notes

The application ID is `io.github.julsjailer.audiostickers`. It installs next to older `com.example` builds; their saved gallery is not migrated.

## Cómo funcionan los stickers

- **Paquetes para WhatsApp:** 512×512, de 3 a 30 por paquete y hasta 10 paquetes. Fijos (máximo 100 KB) o **animados** (máximo 500 KB y 10 s; un video guardado en la galería queda animado). Un paquete no puede mezclar ambos tipos y **ninguno lleva sonido**: el formato de paquetes no lo admite. Reexportar con el mismo nombre actualiza el paquete.
- **Encuadre:** zoom, posición horizontal y vertical, y «Llenar cuadro» o «Ver completo».
- **Estudio (stickers con sonido, experimental):** abre WhatsApp Web dentro de la app, crea el sticker con su audio y lo envía. Es no oficial: WhatsApp puede restringir la cuenta y puede dejar de funcionar sin aviso.

## Código de terceros

`app/src/main/assets/waas` se basa en [WA-audio-stickers](https://github.com/Shirtep/WA-audio-stickers) de Shirtep, con cambios propios (ver `ORIGEN.md` en esa carpeta). Licencia PolyForm Noncommercial 1.0.0: **solo uso no comercial**.
