package com.example.gallery

import android.Manifest

/** Reglas de la galería propia: qué archivos mostrar y qué permisos pedir. */
object MediaGallery {

  enum class Kind {
    IMAGES,
    VIDEOS,
  }

  enum class Access {
    NONE,
    /** Android 14 o superior: la persona permitió solo algunas fotos o videos. */
    PARTIAL,
    FULL,
  }

  /** Qué mostrar según lo que pide la página; null si no es una foto ni un video. */
  fun kindFor(acceptTypes: Array<String>?): Kind? {
    val accepted = acceptTypes.orEmpty().joinToString(",").lowercase()
    return when {
      "audio" in accepted -> null
      "video/" in accepted -> Kind.VIDEOS
      "image/" in accepted -> Kind.IMAGES
      else -> null
    }
  }

  private fun fullPermission(sdk: Int, kind: Kind): String =
    when {
      sdk < 33 -> Manifest.permission.READ_EXTERNAL_STORAGE
      kind == Kind.IMAGES -> Manifest.permission.READ_MEDIA_IMAGES
      else -> Manifest.permission.READ_MEDIA_VIDEO
    }

  fun permissions(sdk: Int, kind: Kind): Array<String> =
    if (sdk >= 34) {
      arrayOf(fullPermission(sdk, kind), Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    } else {
      arrayOf(fullPermission(sdk, kind))
    }

  fun access(sdk: Int, kind: Kind, isGranted: (String) -> Boolean): Access =
    when {
      isGranted(fullPermission(sdk, kind)) -> Access.FULL
      sdk >= 34 && isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.PARTIAL
      else -> Access.NONE
    }

  /** 65000 ms -> "1:05". */
  fun durationLabel(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) + 500) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
  }
}
