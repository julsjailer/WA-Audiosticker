// AudioStickers — creador de stickers animados.
// Copyright (c) 2026 Jul's Jailer. Escrito a partir de la especificación pública del formato
// WebP (RFC 9649). No usa código de terceros.
(() => {
  'use strict';

  const SIDE = 512;
  const MAX_BYTES = 500000; // WhatsApp admite hasta 500 KB por sticker animado.
  const MAX_SECONDS = 10;
  const CAPTURE_FPS = 12;
  // De mejor a peor: se usa el primer nivel que quepa en el peso permitido.
  const LEVELS = [
    [12, 0.75], [12, 0.55], [12, 0.4], [10, 0.4], [10, 0.28],
    [8, 0.28], [8, 0.18], [8, 0.1], [6, 0.08], [5, 0.03],
  ];

  // ---------------------------------------------------------------- lectura del video
  function waitEvent(video, name) {
    return new Promise((resolve, reject) => {
      const finish = (error) => {
        clearTimeout(timer);
        video.removeEventListener(name, onOk);
        video.removeEventListener('error', onError);
        if (error) reject(error);
        else resolve();
      };
      const onOk = () => finish();
      const onError = () => finish(new Error('No se pudo leer el video.'));
      const timer = setTimeout(() => finish(new Error('El video tardó demasiado en responder.')), 15000);
      video.addEventListener(name, onOk);
      video.addEventListener('error', onError);
    });
  }

  /** Abre una copia del video, aparte de la vista previa, para recorrerlo cuadro por cuadro. */
  async function openVideo(file) {
    const url = URL.createObjectURL(file);
    const video = document.createElement('video');
    video.muted = true;
    video.playsInline = true;
    video.preload = 'auto';
    const close = () => {
      video.removeAttribute('src');
      video.load();
      URL.revokeObjectURL(url);
    };
    try {
      const loaded = waitEvent(video, 'loadeddata');
      video.src = url;
      await loaded;
      // Las grabaciones sin duración en la cabecera la revelan al saltar al final.
      if (!Number.isFinite(video.duration)) {
        const jumped = waitEvent(video, 'seeked');
        video.currentTime = 1e7;
        await jumped;
      }
      // Algunos recortes no empiezan en el segundo cero.
      const first = video.buffered.length ? video.buffered.start(0) : 0;
      const length = video.duration - (first < video.duration ? first : 0);
      if (!Number.isFinite(length) || length <= 0) throw new Error('No se pudo saber cuánto dura el video.');
      const origin = video.duration - length;
      return {
        video,
        length,
        close,
        async seek(second) {
          const target = origin + Math.max(0, Math.min(second, length - 0.02));
          if (Math.abs(video.currentTime - target) < 0.001 && !video.seeking && video.readyState >= 2) return;
          const done = waitEvent(video, 'seeked');
          video.currentTime = target;
          await done;
        },
      };
    } catch (error) {
      close();
      throw error;
    }
  }

  // ---------------------------------------------------------------- formato WebP
  const ascii = (text) => Uint8Array.from(text, (c) => c.charCodeAt(0));
  const littleEndian = (value, size) => Uint8Array.from({ length: size }, (_, i) => (value >>> (8 * i)) & 0xff);

  function join(parts) {
    const out = new Uint8Array(parts.reduce((sum, p) => sum + p.length, 0));
    let at = 0;
    for (const p of parts) {
      out.set(p, at);
      at += p.length;
    }
    return out;
  }

  /** Bloque RIFF: nombre, tamaño, datos y un byte de relleno si el tamaño es impar. */
  const block = (name, data) => join([ascii(name), littleEndian(data.length, 4), data, new Uint8Array(data.length % 2)]);

  /** Convierte la imagen WebP de un cuadro en los bloques que van dentro de una animación. */
  async function encodeFrame(canvas, quality) {
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/webp', quality));
    if (!blob || blob.type !== 'image/webp') throw new Error('Este teléfono no puede crear imágenes WebP.');
    const file = new Uint8Array(await blob.arrayBuffer());
    const view = new DataView(file.buffer);
    const kept = [];
    let transparent = false;
    for (let at = 12; at + 8 <= file.length;) {
      const name = String.fromCharCode(...file.subarray(at, at + 4));
      const size = view.getUint32(at + 4, true);
      if (name === 'ALPH' || name === 'VP8 ' || name === 'VP8L') {
        kept.push(block(name, file.subarray(at + 8, at + 8 + size)));
        // En VP8L el bit 28 posterior a la firma indica que usa transparencia.
        if (name === 'ALPH' || (name === 'VP8L' && file[at + 12] & 0x10)) transparent = true;
      }
      at += 8 + size + (size % 2);
    }
    if (!kept.length) throw new Error('El cuadro WebP llegó vacío.');
    return { data: join(kept), transparent };
  }

  /** frames: [{ data, transparent, ms }] */
  function buildAnimation(frames) {
    const full = [littleEndian(SIDE - 1, 3), littleEndian(SIDE - 1, 3)];
    const flags = 0x02 | (frames.some((f) => f.transparent) ? 0x10 : 0);
    const body = join([
      ascii('WEBP'),
      block('VP8X', join([Uint8Array.of(flags, 0, 0, 0), ...full])),
      block('ANIM', new Uint8Array(6)), // fondo transparente, repetición sin fin
      ...frames.map((f) => block('ANMF', join([
        new Uint8Array(6), // posición 0,0
        ...full,
        littleEndian(f.ms, 3),
        Uint8Array.of(0x02), // cada cuadro reemplaza al anterior, sin mezclarse
        f.data,
      ]))),
    ]);
    return join([ascii('RIFF'), littleEndian(body.length, 4), body]);
  }

  const sameBytes = (a, b) => a.length === b.length && a.every((value, i) => value === b[i]);

  // ---------------------------------------------------------------- sticker animado
  /**
   * draw(segundo) debe devolver un canvas de 512×512 con ese instante ya dibujado.
   * Devuelve un Blob WebP animado dentro de los límites de WhatsApp (sin sonido).
   */
  async function createSticker({ duration, draw, onProgress = () => {} }) {
    const seconds = Math.min(MAX_SECONDS, Math.max(0.4, duration || 0));
    const count = Math.max(2, Math.round(seconds * CAPTURE_FPS));
    const shots = [];
    try {
      for (let i = 0; i < count; i++) {
        shots.push(await createImageBitmap(await draw((i * seconds) / count)));
        onProgress('Leyendo el video', (0.6 * (i + 1)) / count);
      }
      const canvas = document.createElement('canvas');
      canvas.width = SIDE;
      canvas.height = SIDE;
      const context = canvas.getContext('2d');
      const encodeShot = (shot, quality) => {
        context.clearRect(0, 0, SIDE, SIDE);
        context.drawImage(shot, 0, 0);
        return encodeFrame(canvas, quality);
      };

      for (let level = 0; level < LEVELS.length; level++) {
        const [fps, quality] = LEVELS[level];
        const total = Math.max(2, Math.round(seconds * fps));
        const shotFor = (k) => shots[Math.min(count - 1, Math.floor((k * count) / total))];
        const msAt = (k) => Math.round((k * seconds * 1000) / total);

        // Se prueban unos pocos cuadros para saltar los niveles que claramente no caben.
        if (level < LEVELS.length - 1) {
          const samples = Math.min(6, total);
          let bytes = 0;
          for (let s = 0; s < samples; s++) {
            bytes += (await encodeShot(shotFor(Math.floor((s * total) / samples)), quality)).data.length;
          }
          const distinct = new Set(Array.from({ length: total }, (_, k) => shotFor(k))).size;
          if ((bytes / samples) * distinct > MAX_BYTES * 1.3) continue;
        }

        const frames = [];
        for (let k = 0; k < total; k++) {
          const frame = await encodeShot(shotFor(k), quality);
          const ms = msAt(k + 1) - msAt(k);
          const last = frames[frames.length - 1];
          if (last && sameBytes(last.data, frame.data)) last.ms += ms;
          else frames.push({ ...frame, ms });
          onProgress('Creando la animación', 0.6 + (0.4 * (k + 1)) / total);
        }
        // WhatsApp exige al menos 2 cuadros: una imagen que no cambia se parte en dos.
        if (frames.length === 1) {
          const half = Math.floor(frames[0].ms / 2);
          frames.push({ ...frames[0], ms: half });
          frames[0].ms -= half;
        }
        const bytes = buildAnimation(frames);
        if (bytes.length <= MAX_BYTES) return new Blob([bytes], { type: 'image/webp' });
      }
      throw new Error('No cabe en 500 KB: usa un tramo más corto.');
    } finally {
      shots.forEach((shot) => shot.close());
    }
  }

  window.Animacion = { openVideo, createSticker };
})();
