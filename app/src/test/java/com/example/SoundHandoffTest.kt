package com.example

import com.example.whatsappweb.SoundHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SoundHandoffTest {

  @Test
  fun `accepts only video types and drops the codecs`() {
    assertEquals("video/mp4", SoundHandoff.videoType("video/mp4;codecs=avc1.42E01E,mp4a.40.2"))
    assertEquals("video/webm", SoundHandoff.videoType("video/webm"))
    assertNull(SoundHandoff.videoType("audio/wav"))
    assertNull(SoundHandoff.videoType("video/mp4');alert(1);('"))
  }

  @Test
  fun `refuses data that could break out of the script`() {
    assertThrows(IllegalArgumentException::class.java) {
      SoundHandoff.deliveryScript("AAAA');alert(1);('", "video/mp4", "square")
    }
    assertEquals("square", SoundHandoff.shape("');alert(1);('"))
    assertEquals("circle", SoundHandoff.shape("circle"))
  }
}
