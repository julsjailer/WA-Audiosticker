package com.example

/** La página local se carga como texto, así que los scripts que usa se insertan antes. */
object LocalPage {
  const val CONVERTER_TAG = "<script src=\"waas/convert.js\"></script>"

  fun compose(html: String, converter: String): String {
    require(CONVERTER_TAG in html) { "La página no declara el codificador de stickers." }
    return html.replace(CONVERTER_TAG, "<script>\n$converter\n</script>")
  }
}
