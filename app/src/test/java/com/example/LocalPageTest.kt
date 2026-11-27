package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPageTest {

  @Test
  fun `inserts the animation script into the bundled page`() {
    val html = File("src/main/assets/audiostickers.html").readText()
    val animation = File("src/main/assets/animacion.js").readText()
    assertFalse("rompería el bloque script", animation.contains("</script", ignoreCase = true))

    val page = LocalPage.compose(html, animation)

    assertFalse(page.contains(LocalPage.ANIMATION_TAG))
    assertTrue(page.contains("createSticker"))
    assertTrue(page.contains(animation))
  }
}
