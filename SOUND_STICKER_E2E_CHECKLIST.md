# Checklist de validación de Sound Sticker — móvil

Esta lista es para pruebas reales del editor existente. No implica compatibilidad nativa con WhatsApp hasta completar la sección de WhatsApp.

## Editor y exportación unificada
- [ ] Foto + audio importado: previsualiza y exporta un solo clip audiovisual.
- [ ] Foto + audio grabado desde la app.
- [ ] Foto + efecto de sonido.
- [ ] Emoji + audio.
- [ ] Vídeo + conservar audio original.
- [ ] Vídeo + reemplazar audio por archivo.
- [ ] Vídeo + reemplazar por audio grabado.
- [ ] Vídeo + audio silenciado.
- [ ] Recortes de visual/audio respetados.
- [ ] La duración final y el sincronizado se corresponden con la previsualización.
- [ ] Cancelar selección/edición no daña el proyecto anterior.
- [ ] Guardar en galería de la app, cerrar/reabrir y verificar recuperación.
- [ ] Guardar clip exportado en Galería del Android.
- [ ] Compartir clip exportado; confirmar que se comparte el archivo audiovisual y no solo audio.
- [ ] Probar archivos largos, formatos incompatibles y permisos denegados; mostrar error claro y no bloquear la app.

## WhatsApp — prueba de compatibilidad, no asumida
- [ ] En versión de WhatsApp que exponga la función, usar el flujo disponible para crear/importar desde el clip exportado.
- [ ] Confirmar que el resultado aparece como sticker de audio, no como vídeo/audio convencional.
- [ ] Enviar a un chat de prueba.
- [ ] Confirmar recepción y reproducción del audio con el sticker.
- [ ] Documentar versión de WhatsApp, Android y resultado observado (sin capturar información privada del chat).

## No aprobar todavía
No declarar Sound Sticker nativo, paquete nativo con audio, ni envío directo nativo basado solo en que el MP4 tenga audio incrustado o que el Intent de compartir abra WhatsApp.
