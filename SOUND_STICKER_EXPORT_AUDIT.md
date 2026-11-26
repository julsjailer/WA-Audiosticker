# Revisión técnica de exportación audiovisual (2026-11-26)

## Inspección de código — editor HTML existente
Archivo revisado: `app/src/main/assets/audiostickers.html`, rama `feature/sound-sticker-editor-foundation`.

### Flujo actual confirmado por código
- El vídeo importado se crea como elemento HTML `<video>`, pero se establece `muted = true` para su reproducción visual en el canvas.
- Hay botón/flujo `extractVideoAudioBtn` que intenta decodificar la pista de audio del archivo de vídeo mediante `loadAudioBlob`; si funciona, el audio extraído queda en `state.audioBuffer` y el usuario cambia al modo de audio de archivo.
- La pista de audio seleccionada/extraída/grabada/efecto se recorta usando `state.trimStart` y `state.trimEnd`.
- Exportación unificada crea canvas 512×512 y `captureStream(30)`, mezcla la pista de vídeo del canvas con una pista `MediaStreamDestination` de Web Audio y graba mediante `MediaRecorder`.
- La exportación selecciona MIME de vídeo con candidatos MP4 primero y WebM después, según `MediaRecorder.isTypeSupported`.
- El vídeo visual se reinicia y reproduce al comenzar la exportación; el canvas se actualiza con `requestAnimationFrame` durante la duración del audio recortado. El vídeo fuente está silenciado, por lo que la pista de audio exportada proviene de `state.audioBuffer`.
- Guardar y compartir invocan la misma función de generación de blob audiovisual; el botón para compartir audio WAV es una ruta separada.

## Riesgos/pendientes que no se deben ocultar
1. **No es passthrough automático del audio del vídeo.** La persona debe usar el control de extracción del audio del vídeo para cargarlo como pista seleccionada; si no, debe cargar/grabar/usar efecto. En exportación la pista fuente es el buffer Web Audio, no el audio original directo.
2. **Compatibilidad de contenedor/códec no garantizada.** El MIME MP4 es preferido solo si el navegador/WebView informa soporte; en otros casos puede exportar WebM. Debe verificarse el formato real del blob/archivo en Android.
3. **Exportación depende de WebView/MediaRecorder.** No hay aún pruebas automatizadas de exportación real en dispositivo, ni pruebas de que el compartir directo abra el flujo correcto de creación nativa de Sound Stickers.
4. **Duración y sincronización.** La exportación limita su duración al tramo de audio recortado y dibuja el vídeo mientras ese audio se graba. Se necesita probar vídeos más cortos/largos que el audio y revisar el comportamiento de loop y el último frame.
5. **Compatibilidad WhatsApp nativa no demostrada.** El MP4/WebM audiovisual único y un Intent para WhatsApp no prueban que el receptor reciba un sticker de audio nativo.

## Propuesta de pruebas iniciales (sin cambios invasivos)
- Vídeo con audio original: extraer pista, exportar, verificar audio+vídeo sincronizados y duración.
- Vídeo con audio reemplazado: cargar otro audio, exportar y comprobar que no se escucha el audio original.
- Vídeo sin audio: probar extracción y verificar mensaje/recuperación; usar pista de reemplazo.
- Foto y emoji con pista audio: exportar y verificar que el canvas estático se mantiene y el audio suena.
- Revisar archivo real en dispositivo: extensión, MIME, tamaño, reproducción, y qué aplicación recibe el compartir.
- Repetir en Android WebView del dispositivo destino; los soportes declarados por `MediaRecorder.isTypeSupported` pueden variar por Android/WebView.

## Estado
Esta auditoría es revisión de código, no prueba de ejecución en teléfono. No se modificó la interfaz ni se hizo una afirmación de compatibilidad nativa.
