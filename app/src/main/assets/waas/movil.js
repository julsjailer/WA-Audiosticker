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
  window.__waasMovil = {
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
