# WA-AudioSticker — desarrollo de Sound Stickers

## Objetivo
Construir un editor Android que cree **un único proyecto de sticker visual con audio asociado**: foto, emoji o vídeo; audio conservado del vídeo, sustituido por un archivo, grabación o efecto, o silenciado. El editor y la exportación deben tratarlo como una sola creación, no como dos archivos independientes.

## Estado inicial auditado
- La app actual usa Kotlin/Jetpack Compose como contenedor Android y una interfaz/editor HTML en WebView.
- Ya existe selección de imagen/vídeo, captura/importación de audio, recorte, previsualización y acciones de guardado/compartir en el prototipo web. Estas funciones requieren validación y conexión de extremo a extremo antes de considerarse terminadas.
- Room guarda proyectos/stickers visuales y archivos de audio localmente.
- El ContentProvider actual implementa el formato de paquetes de stickers convencional de WhatsApp; no se ha confirmado que ese mecanismo admita Sound Stickers nativos.

## Primer incremento
- Introducir un modelo Kotlin de proyecto que represente visual, audio, origen, duración y recortes con validación.
- Mantener explícita la diferencia entre audio original de vídeo y audio de reemplazo.
- Añadir pruebas unitarias para las reglas del modelo.
- No afirmar que la salida resultante es un Sound Sticker nativo de WhatsApp hasta demostrarlo con una prueba real en una versión compatible de WhatsApp.

## Ruta de construcción
1. **Modelo y validación del proyecto** (en progreso).
2. Integrar la selección de fuentes de vídeo/foto/audio y la grabadora al modelo.
3. Editor visual: recorte, encuadre, duración y previsualización.
4. Editor de audio: conservar, reemplazar, grabar, efectos, recortar y sincronizar.
5. Renderizador unificado que produzca el contenido visual y la pista de audio sincronizada como un solo proyecto/exportación.
6. Guardado y recuperación de proyectos sin pérdida; Room migrations seguras.
7. Pruebas de integración del editor y de exportación.
8. Investigar opciones oficiales de integración con WhatsApp y realizar pruebas de compatibilidad. No depender de APIs privadas, claves de cifrado, ni protocolos de envío no documentados.
9. CI: pruebas y APK Debug; publicar APK Release en GitHub Releases con firma estable antes de distribuir actualizaciones.

## Limitación técnica importante
WhatsApp puede usar infraestructura y formato de mensaje propios para los Sound Stickers. La aplicación puede construir y editar el contenido audiovisual fusionado, pero la importación/envío como sticker de audio nativo depende de una vía compatible y verificable de WhatsApp. No se presentará un archivo de vídeo más un audio separado como equivalente al formato nativo.
