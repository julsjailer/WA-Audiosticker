package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPageTest {

  @Test
  fun `inserts the converter into the bundled page`() {
    val html = File("src/main/assets/audiostickers.html").readText()
    val converter = File("src/main/assets/waas/convert.js").readText()
    assertFalse("rompería el bloque script", converter.contains("</script", ignoreCase = true))

    val page = LocalPage.compose(html, converter)

    assertFalse(page.contains(LocalPage.CONVERTER_TAG))
    assertTrue(page.contains("encodePackAnimation"))
    assertTrue(page.contains(converter))
  }
}
