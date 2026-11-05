import { captureComponent } from './composition.mjs';
import { importProject, exportProject, MAX_PORTABLE_BYTES } from './projects.mjs';
export const PROJECT_CHUNK_CHARS = 32768;
export function projectFilename(name) {
  if (typeof name !== 'string' || !name.trim() || name.length > 40 || /[\x00-\x1f\x7f-\x9f]/.test(name)) throw Error('Use a project name of 1–40 characters.');
  const safe = name.trim().replace(/[\\/:*?"<>|]/g, '_').replace(/^[ .]+|[ .]+$/g, '') || 'Noble project';
  return `${safe}.noble.json`;
}
export function checkedProjectFile(raw) {
  if (typeof raw !== 'string' || !raw || new TextEncoder().encode(raw).length > MAX_PORTABLE_BYTES) throw Error('Project file is empty or too large.');
  raw = raw.replace(/^\uFEFF/, '');
  return { raw, project: importProject(raw) };
}
export function decodeProjectFile(buffer) {
  if (!buffer?.byteLength || buffer.byteLength > MAX_PORTABLE_BYTES) throw Error('Project file is empty or too large.');
  return checkedProjectFile(new TextDecoder('utf-8', { fatal: true }).decode(buffer));
}
function chunk(text, offset) {
  let end = Math.min(text.length, offset + PROJECT_CHUNK_CHARS);
  if (end < text.length && /[\uD800-\uDBFF]/.test(text[end - 1]) && /[\uDC00-\uDFFF]/.test(text[end])) end--;
  return text.slice(offset, end);
}
function sendDocumentFile(bridge, requestId, name, raw) {
  projectFilename(name);
  let started = false;
  try {
    if (bridge.beginProjectFileSave(requestId, name, raw.length) !== true) throw Error('The project file tool is busy or unavailable. Try again.');
    started = true;
    for (let offset = 0; offset < raw.length;) {
      const value = chunk(raw, offset);
      if (bridge.appendProjectFileChunk(requestId, value) !== true) throw Error('Project file transfer stopped. Save again.');
      offset += value.length;
    }
    bridge.finishProjectFileSave(requestId);
  } catch (error) { if (started) bridge.releaseProjectFile(requestId); throw error; }
}
export function sendProjectFile(bridge, requestId, name, raw) {
  checkedProjectFile(raw); sendDocumentFile(bridge, requestId, name, raw);
}
/** Recovery archives carry opaque stored text; they are deliberately not editable project imports. */
export function checkedRecoveryFile(raw) {
  if (typeof raw !== 'string' || !raw || raw.length > MAX_PORTABLE_BYTES || new TextEncoder().encode(raw).length > MAX_PORTABLE_BYTES) throw Error('Recovery copy is too large or unavailable. The stored draft is unchanged.');
  const value = JSON.parse(raw);
  if (value?.format !== 'noble-drawing-recovery' || value.version !== 1 || typeof value.draft !== 'string' || !value.draft.length || Object.keys(value).sort().join(',') !== 'draft,format,version') throw Error('Unsupported drawing recovery archive.');
  return { raw, draft: value.draft };
}
export function recoveryFile(rawDraft) {
  if (typeof rawDraft !== 'string' || !rawDraft.length || rawDraft.length > MAX_PORTABLE_BYTES) throw Error('The stored draft is unavailable or too large to copy. Nothing was changed.');
  const template = { format: 'noble-drawing-recovery', version: 1, draft: '' };
  let size = new TextEncoder().encode(JSON.stringify(template)).length;
  // Count escaped JSON UTF-8 bytes before allocating a potentially oversized envelope.
  for (let i = 0; i < rawDraft.length; i++) {
    const code = rawDraft.charCodeAt(i);
    if (code < 32) size += (code === 8 || code === 9 || code === 10 || code === 12 || code === 13) ? 2 : 6;
    else if (code === 34 || code === 92) size += 2;
    else if (code >= 0xd800 && code <= 0xdbff && i + 1 < rawDraft.length && rawDraft.charCodeAt(i + 1) >= 0xdc00 && rawDraft.charCodeAt(i + 1) <= 0xdfff) { size += 4; i++; }
    else if (code >= 0xd800 && code <= 0xdfff) size += 6;
    else size += code < 128 ? 1 : code < 2048 ? 2 : 3;
    if (size > MAX_PORTABLE_BYTES) throw Error('Recovery copy exceeds the supported file limit. Update Noble before clearing the stored draft.');
  }
  return checkedRecoveryFile(JSON.stringify({ ...template, draft: rawDraft })).raw;
}
export function sendRecoveryFile(bridge, requestId, raw) {
  checkedRecoveryFile(raw); sendDocumentFile(bridge, requestId, 'Preserved drawing recovery', raw);
}
export function receiveProjectFile(bridge, requestId, length) {
  try {
    if (!Number.isInteger(length) || length < 1 || length > MAX_PORTABLE_BYTES) throw Error('Project file size is unsupported.');
    const parts = []; let offset = 0;
    while (offset < length) {
      const value = bridge.readProjectFileChunk(requestId, offset);
      if (typeof value !== 'string' || !value.length || value.length > PROJECT_CHUNK_CHARS || value.length > length - offset) throw Error('Project file transfer is incomplete. Choose it again.');
      parts.push(value); offset += value.length;
    }
    return checkedProjectFile(parts.join(''));
  } finally { bridge.releaseProjectFile(requestId); }
}

/** Portable editable selection; keeps hidden state, unlike raster/image exports. */
export function projectFileArtwork(name, elements, state = {}, files = {}, selectedIds = null, measure) {
  if (selectedIds === null) return exportProject(name, elements, state, files);
  if (typeof selectedIds !== 'object' || Array.isArray(selectedIds) || !elements.some(element => !element.isDeleted && Object.prototype.hasOwnProperty.call(selectedIds, element.id) && selectedIds[element.id])) throw Error('Select artwork on the canvas before saving a selection file.');
  return exportProject(name, captureComponent(elements, selectedIds, measure), state, files);
}
