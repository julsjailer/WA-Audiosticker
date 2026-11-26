package com.example.editor

/**
 * Draft representation of a single sound sticker project.
 *
 * This model keeps the visual and audio choices inside one project. It is editor
 * metadata only; it does not claim to produce WhatsApp's proprietary native
 * sound-sticker message format.
 */
enum class VisualSourceType { IMAGE, VIDEO, EMOJI }

enum class AudioMode {
  SILENT,
  KEEP_VIDEO_AUDIO,
  REPLACEMENT_FILE,
  RECORDED_AUDIO,
  SOUND_EFFECT,
}

data class StickerProjectDraft(
  val title: String,
  val visualSourceType: VisualSourceType,
  val visualSourceUri: String? = null,
  val visualDurationSeconds: Double? = null,
  val visualTrimStartSeconds: Double = 0.0,
  val visualTrimEndSeconds: Double? = null,
  val audioMode: AudioMode = AudioMode.SILENT,
  val audioSourceUri: String? = null,
  val audioDurationSeconds: Double? = null,
  val audioTrimStartSeconds: Double = 0.0,
  val audioTrimEndSeconds: Double? = null,
) {
  val includesAudio: Boolean
    get() = audioMode != AudioMode.SILENT

  val keepsOriginalVideoAudio: Boolean
    get() = visualSourceType == VisualSourceType.VIDEO &&
      audioMode == AudioMode.KEEP_VIDEO_AUDIO

  fun validate(): List<String> = buildList {
    if (title.isBlank()) add("El proyecto necesita un nombre.")
    if (visualSourceType != VisualSourceType.EMOJI && visualSourceUri.isNullOrBlank()) {
      add("Selecciona una imagen o vídeo.")
    }
    if (visualDurationSeconds != null && (!visualDurationSeconds.isFinite() || visualDurationSeconds <= 0.0)) {
      add("La duración visual debe ser mayor que cero.")
    }
    if (!visualTrimStartSeconds.isFinite() || visualTrimStartSeconds < 0.0) {
      add("El inicio del recorte visual no es válido.")
    }
    if (visualTrimEndSeconds != null) {
      if (!visualTrimEndSeconds.isFinite() || visualTrimEndSeconds <= visualTrimStartSeconds) {
        add("El final del recorte visual debe ser posterior al inicio.")
      }
      if (visualDurationSeconds != null && visualTrimEndSeconds > visualDurationSeconds) {
        add("El recorte visual excede la duración del archivo.")
      }
    }
    if (visualDurationSeconds != null && visualTrimStartSeconds >= visualDurationSeconds) {
      add("El inicio del recorte visual excede la duración del archivo.")
    }
    if (audioMode == AudioMode.KEEP_VIDEO_AUDIO && visualSourceType != VisualSourceType.VIDEO) {
      add("Solo un vídeo puede conservar su audio original.")
    }
    if (audioMode in setOf(AudioMode.REPLACEMENT_FILE, AudioMode.RECORDED_AUDIO, AudioMode.SOUND_EFFECT) &&
      audioSourceUri.isNullOrBlank()
    ) {
      add("Selecciona o graba el audio que se usará.")
    }
    if (audioDurationSeconds != null && (!audioDurationSeconds.isFinite() || audioDurationSeconds <= 0.0)) {
      add("La duración del audio debe ser mayor que cero.")
    }
    if (!audioTrimStartSeconds.isFinite() || audioTrimStartSeconds < 0.0) {
      add("El inicio del recorte de audio no es válido.")
    }
    if (audioTrimEndSeconds != null) {
      if (!audioTrimEndSeconds.isFinite() || audioTrimEndSeconds <= audioTrimStartSeconds) {
        add("El final del recorte de audio debe ser posterior al inicio.")
      }
      if (audioDurationSeconds != null && audioTrimEndSeconds > audioDurationSeconds) {
        add("El recorte de audio excede la duración del archivo.")
      }
    }
    if (audioDurationSeconds != null && audioTrimStartSeconds >= audioDurationSeconds) {
      add("El inicio del recorte de audio excede la duración del archivo.")
    }
  }
}
