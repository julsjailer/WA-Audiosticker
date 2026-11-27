// Adaptación al teléfono: WhatsApp Web se muestra con ancho de escritorio y el panel de
// stickers con el ancho del dispositivo.
(() => {
  if (window.__waasMovil) return;
  const DESKTOP_WIDTH = 900;
  let meta = document.querySelector('meta[name="viewport"]');
  if (!meta) {
    meta = document.createElement("meta");
    meta.setAttribute("name", "viewport");
    document.head.appendChild(meta);
  }
  const overlay = () => {
    const host = document.getElementById("waas-root");
    return host && host.shadowRoot && host.shadowRoot.querySelector(".overlay");
  };
  const isOpen = () => { const o = overlay(); return !!o && o.style.display !== "none"; };
  const apply = () => {
    const want = isOpen()
      ? "width=device-width, initial-scale=1"
      : "width=" + DESKTOP_WIDTH + ", initial-scale=" + Math.min(1, screen.width / DESKTOP_WIDTH).toFixed(3);
    if (meta.getAttribute("content") !== want) meta.setAttribute("content", want);
  };
  const watch = () => {
    const o = overlay();
    if (!o) return setTimeout(watch, 500);
    new MutationObserver(apply).observe(o, { attributes: true, attributeFilter: ["style"] });
    apply();
  };
  // Pide que el almacenamiento (donde vive la sesión) no se borre para liberar espacio.
  if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});

  // ¿Hay una sesión iniciada y los chats ya cargaron?
  const loggedIn = () => {
    try {
      const wa = window.WAAS.wa;
      if (!wa.hasLoader() || !wa.ns("WAWebChatCollection").ChatCollection) return false;
      return !!document.querySelector("#pane-side") || !!wa.ns("WAWebUserPrefsMeUser").getMaybeMePnUser();
    } catch (e) { return false; }
  };

  window.__waasMovil = {
    delivered: false,
    // Recibe el video creado en el editor de la app y lo abre cuando WhatsApp está listo.
    receive(base64, mime, shape) {
      if (this.waiting) return true;
      const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
      const file = new File([bytes], "sticker." + (mime === "video/webm" ? "webm" : "mp4"), { type: mime });
      this.waiting = setInterval(async () => {
        if (this.sending || !window.WAAS || !window.WAAS.receiveVideo || !loggedIn()) return;
        this.sending = true;
        try {
          const mask = shape === "rounded" || shape === "circle" ? shape : null;
          if (await window.WAAS.receiveVideo(file, mask)) {
            this.delivered = true;
            clearInterval(this.waiting);
          }
        } finally { this.sending = false; }
      }, 2000);
      return true;
    },
    toggle() {
      if (!window.WAAS || !window.WAAS.togglePanel) return false;
      window.WAAS.togglePanel();
      return true;
    },
    // Botón "atrás": cierra el panel si está abierto.
    back() {
      if (!isOpen()) return false;
      window.WAAS.togglePanel();
      return true;
    },
  };
  apply();
  watch();
})();
