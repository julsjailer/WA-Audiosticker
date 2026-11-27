package com.example

/** La página local se carga como texto, así que los scripts que usa se insertan antes. */
object LocalPage {
  const val ANIMATION_TAG = "<script src=\"animacion.js\"></script>"

  fun compose(html: String, animation: String): String {
    require(ANIMATION_TAG in html) { "La página no declara el creador de animaciones." }
    return html.replace(ANIMATION_TAG, "<script>\n$animation\n</script>")
  }
}
