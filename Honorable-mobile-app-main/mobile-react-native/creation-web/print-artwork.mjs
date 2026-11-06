import { encodeDraft } from './scene.mjs';
import { imageArtwork } from './image-export.mjs';

export const PRINT_CHUNK_CHARS = 32768;
export const MAX_PRINT_BYTES = 8 * 1024 * 1024;
const prefix = 'data:image/png;base64,';

/** Capture the selected artwork at the user action. Later viewport/selection changes do not retarget it; document edits invalidate it. */
export function printArtworkSnapshot(editor, selectionOnly = false, measure) {
  if (!editor) throw Error('Wait for the drawing to open.');
  const elements = editor.getSceneElements(), files = editor.getFiles();
  const raw = encodeDraft(elements, {}, files);
  const artwork = imageArtwork(elements, files, selectionOnly ? (editor.getAppState().selectedElementIds || {}) : null, measure);
  return {
    ...artwork,
    current(candidate) {
      if (candidate !== editor) return false;
      try { return encodeDraft(editor.getSceneElements(), {}, editor.getFiles()) === raw; }
      catch { return false; }
    },
  };
}

/** All bridge messages are bounded; failure releases only this exact request. */
export function sendPrintArtwork(bridge, id, dataURL) {
  if (!Number.isInteger(id) || id < 1 || id > 1_000_000_000) throw Error('Print request is unavailable. Try again.');
  if (typeof dataURL !== 'string' || dataURL.length > prefix.length + Math.ceil(MAX_PRINT_BYTES / 3) * 4 || !dataURL.startsWith(prefix)) throw Error('Artwork is too large to print. Use a smaller drawing.');
  const payload = dataURL.slice(prefix.length);
  if (!payload.length || payload.length % 4 || !/^[A-Za-z0-9+/]*={0,2}$/.test(payload)) throw Error('Printed artwork encoding is invalid.');
  for (const method of ['beginPrintImage', 'appendPrintImageChunk', 'finishPrintImage', 'releasePrintImage']) if (typeof bridge?.[method] !== 'function') throw Error('Open Noble on Android to print or save a PDF.');
  let began = false;
  try {
    began = bridge.beginPrintImage(id, dataURL.length) === true;
    if (!began) throw Error('Another operation is running or the drawing is not active. Try again.');
    for (let offset = 0; offset < dataURL.length; offset += PRINT_CHUNK_CHARS) if (bridge.appendPrintImageChunk(id, dataURL.slice(offset, offset + PRINT_CHUNK_CHARS)) !== true) throw Error('Artwork transfer stopped. Try printing again.');
    bridge.finishPrintImage(id);
  } catch (error) {
    if (began) { try { bridge.releasePrintImage(id); } catch { /* Keep the original failure. */ } }
    throw error;
  }
}
