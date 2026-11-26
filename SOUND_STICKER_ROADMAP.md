# WA-AudioSticker — desarrollo de Sound Stickers

## Objetivo
Construir un editor Android que cree **un único proyecto visual con audio asociado**: foto, emoji o vídeo; audio conservado del vídeo, sustituido por archivo, grabación o efecto, o silenciado. La edición y exportación deben tratarlo como una sola creación, no como dos archivos independientes.

## Estado auditado (rama de desarrollo)
- Android Kotlin/Jetpack Compose como contenedor y editor HTML en WebView.
- El editor existente ya contiene selección de imagen/vídeo, controles de encuadre, importación/grabación de audio, edición, previsualización, galería local y exportación audiovisual en un único archivo.
- La interfaz incluye opciones para guardar el clip fusionado y compartirlo; también mantiene una acción separada de compartir audio, que no debe confundirse con el resultado audiovisual.
- Room almacena stickers/proyectos locales y sus archivos.
- El ContentProvider existente implementa paquetes convencionales de stickers de WhatsApp. No se ha verificado que sirva Sound Stickers nativos.
- El HTML actualmente presenta un flujo de importación en WhatsApp Beta y afirma que WhatsApp convierte el archivo. Esa afirmación **todavía no está validada con una prueba real**; se considera hipótesis a comprobar, no garantía.

## Primer incremento técnico (completado en esta rama)
- Modelo Kotlin de proyecto: visual, audio, fuente, duración y recortes, con validación.
- Diferencia explícita entre audio original de vídeo y pista de reemplazo.
- Pruebas unitarias del modelo.
- CI de GitHub Actions: pruebas y compilación Debug en rama de desarrollo, verificada en Run #26.

## Siguientes etapas
1. **Prueba de extremo a extremo de la exportación existente:** comprobar fuentes imagen/emoji/vídeo, audio original, reemplazo, grabación, silencioso, sincronía y guardado/compartido.
2. Conectar de forma verificable el modelo Kotlin con el flujo del editor (no solo dejarlo como modelo aislado).
3. Validar recuperación de proyectos guardados y casos de fallo; revisar Room antes de cualquier cambio de esquema/migración.
4. Revisar las restricciones del archivo exportado (duración, dimensiones, códec, formato y tamaño) mediante inspección de código y pruebas.
5. **Compatibilidad nativa WhatsApp:** probar únicamente la vía de importación/creación documentada y disponible en la app. No declarar compatibilidad Sound Sticker nativa hasta que una prueba real lo confirme.
6. CI: mantener pruebas + APK Debug; publicar APK Release en GitHub Releases solo con configuración de firma estable y verificación de compatibilidad de instalación.

## Criterio de aceptación
La exportación audiovisual unificada es una función del editor, **no prueba de que el archivo sea un Sound Sticker nativo**. La aceptación final de esa parte requiere que WhatsApp lo importe como un sticker con audio integrado, que se envíe y que el destinatario lo reciba/reproduzca como sticker, comprobado en una versión compatible.

## Limitaciones
No se utilizarán APIs privadas, protocolos de envío no documentados, claves de cifrado ni mecanismos de bypass. Si no hay vía oficial/compatible, se mantendrá el editor y exportación como funciones independientes, y se informará con claridad que el envío nativo no está confirmado.
