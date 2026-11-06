export const MAX_SVG_BYTES = 2 * 1024 * 1024;
export const SVG_CHUNK_CHARS = 32768;

/** Native receives only the fresh renderer output, never the editable scene or source locations. */
export function sendSvgArtwork(bridge, id, value) {
  if (!Number.isInteger(id) || id < 1 || id > 1_000_000_000) throw Error('SVG request is unavailable. Try again.');
  if (typeof value !== 'string' || !value.trim() || value.length > MAX_SVG_BYTES) throw Error('Prepare SVG artwork up to 2 MiB first.');
  const bytes = new TextEncoder().encode(value);
  if (bytes.length > MAX_SVG_BYTES || new TextDecoder().decode(bytes) !== value) throw Error('SVG artwork is too large or has invalid text.');
  if (!/^\s*<svg(?:\s|>)/.test(value) || !value.trimEnd().endsWith('</svg>')) throw Error('Prepare SVG artwork first.');
  for (const name of ['beginSvgShare', 'appendSvgShareChunk', 'finishSvgShare', 'releaseSvgShare']) if (typeof bridge?.[name] !== 'function') throw Error('Open Noble on Android to share the SVG file. You can still copy the SVG text here.');
  let began = false;
  try {
    began = bridge.beginSvgShare(id, value.length) === true;
    if (!began) throw Error('Another operation is running or the drawing is not active. Try again.');
    let position = 0;
    while (position < value.length) {
      let end = Math.min(value.length, position + SVG_CHUNK_CHARS);
      if (end < value.length && /[\uD800-\uDBFF]/.test(value[end - 1]) && /[\uDC00-\uDFFF]/.test(value[end])) end--;
      if (bridge.appendSvgShareChunk(id, value.slice(position, end)) !== true) throw Error('SVG transfer stopped. Share again when ready.');
      position = end;
    }
    bridge.finishSvgShare(id);
  } catch (error) {
    if (began) { try { bridge.releaseSvgShare(id); } catch { /* Keep the actionable transfer error. */ } }
    throw error;
  }
}
