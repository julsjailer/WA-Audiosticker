package com.example

import com.example.sounds.SoundLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundLibraryTest {

  @Test
  fun `stays on the library site`() {
    assertTrue(SoundLibrary.isLibraryPage(SoundLibrary.URL))
    assertFalse(SoundLibrary.isLibraryPage("http://www.myinstants.com/es/"))
    assertFalse(SoundLibrary.isLibraryPage("https://www.myinstants.com.evil.example/"))
    assertFalse(SoundLibrary.isLibraryPage("https://user@www.myinstants.com/"))
    assertFalse(SoundLibrary.isLibraryPage("intent://scan/#Intent;end"))
  }

  @Test
  fun `recognises only sound downloads`() {
    assertEquals(
      "vine-boom.mp3",
      SoundLibrary.soundName("https://www.myinstants.com/media/sounds/vine-boom.mp3"),
    )
    assertEquals(
      "risa épica.mp3",
      SoundLibrary.soundName("https://www.myinstants.com/media/sounds/risa%20%C3%A9pica.mp3"),
    )
    assertNull(SoundLibrary.soundName("https://www.myinstants.com/es/instant/vine-boom/"))
    assertNull(SoundLibrary.soundName("https://www.myinstants.com/media/sounds/a%2F..%2Fb.mp3"))
    assertNull(SoundLibrary.soundName("https://other.example/media/sounds/vine-boom.mp3"))
  }
}
