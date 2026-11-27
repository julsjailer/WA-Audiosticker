# WA-AudioSticker

Android Kotlin app for creating, trimming and sharing audio stickers for WhatsApp.

**Creado por Jul's Jailer** · © 2026. Ver [LICENSE](LICENSE).

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

Para que cada versión se instale encima de la anterior hace falta **un solo secreto**, una única vez:

1. Abre **[crear secreto](https://github.com/julsjailer/WA-Audiosticker/settings/secrets/actions/new)**.
2. **Name:** `FIRMA`
3. **Secret:** la línea completa del archivo privado `FIRMA.txt` (formato `contraseña:almacén_en_base64`; el alias de la clave es `audiostickers`).
4. **Add secret**. La siguiente compilación ya queda firmada con tu clave.

Sin ese secreto se usa una clave temporal distinta en cada compilación y hay que desinstalar antes de instalar. Guarda `FIRMA.txt` en un lugar privado y nunca lo subas al repositorio.

## Build configuration

- Kotlin + Jetpack Compose
- Android Gradle Plugin and Gradle versions from `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`
- Minimum Android API: 24
- CI Java: 17
- Release builds use version values supplied by GitHub Actions; local defaults remain available.

## Current repository notes

The application ID is `io.github.julsjailer.audiostickers`. It installs next to older `com.example` builds; their saved gallery is not migrated.

## Cómo funcionan los stickers

- **Paquetes para WhatsApp:** 512×512, de 3 a 30 por paquete y hasta 10 paquetes. Fijos (máximo 100 KB) o **animados** (máximo 500 KB y 10 s; un video guardado en la galería queda animado, con un creador propio en `assets/animacion.js`). Un paquete no puede mezclar ambos tipos y **ninguno lleva sonido**: el formato de paquetes no lo admite. Reexportar con el mismo nombre actualiza el paquete.
- **Encuadre:** zoom, posición horizontal y vertical, y «Llenar cuadro» o «Ver completo».
- **Audios:** micrófono, archivo propio, efectos o la biblioteca de [MyInstants](https://www.myinstants.com/es/index/us/). La app solo muestra el sitio y entrega al editor el audio que la persona descarga. Sus [condiciones](https://www.myinstants.com/en/terms_of_use.html) limitan el uso a personal y no comercial.
- **Enviar como sticker con sonido (experimental):** pasa el sticker del editor a WhatsApp Web dentro de la app; solo queda enviarlo y elegir los chats.
- **Estudio (experimental):** WhatsApp Web dentro de la app para crear y enviar stickers con sonido. Es no oficial: WhatsApp puede restringir la cuenta y puede dejar de funcionar sin aviso. La sesión queda guardada en la app (se pierde al desinstalarla, al borrar sus datos o si WhatsApp desvincula el dispositivo).

## Autoría y código de terceros

El código propio de la app es de **Jul's Jailer** (ver [LICENSE](LICENSE)).

`app/src/main/assets/waas` se basa en [WA-audio-stickers](https://github.com/Shirtep/WA-audio-stickers) de Shirtep, con cambios propios (ver `ORIGEN.md` en esa carpeta). Esa parte sigue siendo de su autor, bajo licencia PolyForm Noncommercial 1.0.0: **mientras la app la incluya, solo se puede distribuir sin fines comerciales**.
