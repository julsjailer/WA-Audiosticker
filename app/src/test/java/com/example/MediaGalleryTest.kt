package com.example

import android.Manifest
import com.example.gallery.MediaGallery
import com.example.gallery.MediaGallery.Access
import com.example.gallery.MediaGallery.Kind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaGalleryTest {

  @Test
  fun `chooses the gallery only for photos and videos`() {
    assertEquals(Kind.VIDEOS, MediaGallery.kindFor(arrayOf("video/mp4", "video/webm", "video/*")))
    assertEquals(Kind.IMAGES, MediaGallery.kindFor(arrayOf("image/png", "image/*")))
    assertNull(MediaGallery.kindFor(arrayOf("audio/*", ".mp3", ".webm")))
    assertNull(MediaGallery.kindFor(arrayOf("")))
    assertNull(MediaGallery.kindFor(null))
  }

  @Test
  fun `asks for the permissions of each Android version`() {
    assertArrayEquals(
      arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
      MediaGallery.permissions(30, Kind.VIDEOS),
    )
    assertArrayEquals(
      arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
      MediaGallery.permissions(33, Kind.IMAGES),
    )
    assertArrayEquals(
      arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
      ),
      MediaGallery.permissions(35, Kind.VIDEOS),
    )
  }

  @Test
  fun `recognises partial access`() {
    val onlySelected = { p: String -> p == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
    assertEquals(Access.PARTIAL, MediaGallery.access(34, Kind.VIDEOS, onlySelected))
    assertEquals(Access.NONE, MediaGallery.access(33, Kind.VIDEOS, onlySelected))
    assertEquals(Access.FULL, MediaGallery.access(34, Kind.IMAGES) { true })
  }

  @Test
  fun `formats durations`() {
    assertEquals("1:05", MediaGallery.durationLabel(65_000))
    assertEquals("0:00", MediaGallery.durationLabel(-1))
  }
}
