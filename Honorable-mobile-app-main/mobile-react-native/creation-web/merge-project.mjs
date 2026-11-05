import { validateDrawingElements } from './scene.mjs';

/** Refuse missing references before renderer restore can silently detach them. */
export function validateProjectGraph(elements) {
  const active = validateDrawingElements(elements), ids = new Set(active.map(element => element.id)), byId = new Map(active.map(element => [element.id, element]));
  const reference = id => { if (id != null && !ids.has(id)) throw Error('This project refers to artwork that is missing. Choose a complete project or repair a copy of the file.'); };
  for (const element of active) {
    if (element.groupIds != null && (!Array.isArray(element.groupIds) || element.groupIds.some(id => typeof id !== 'string' || !id || id.length > 128))) throw Error('Project groups are invalid.');
    reference(element.containerId); reference(element.frameId);
    if (element.containerId != null && (element.type !== 'text' || !['rectangle', 'ellipse', 'diamond', 'arrow'].includes(byId.get(element.containerId).type))) throw Error('Project text containers are invalid.');
    if (element.frameId != null && byId.get(element.frameId).type !== 'frame') throw Error('Project frames are invalid.');
    const parents = new Set([element.id]); let parent = element.frameId;
    while (parent != null) { if (parents.has(parent)) throw Error('Project frames contain a cycle.'); parents.add(parent); const frame = byId.get(parent); if (!frame || frame.type !== 'frame') throw Error('Project frames are invalid.'); parent = frame.frameId; }
    if (element.boundElements != null) {
      if (!Array.isArray(element.boundElements) || element.boundElements.some(item => !item || typeof item.id !== 'string' || !item.id || !['text', 'arrow'].includes(item.type))) throw Error('Project object bindings are invalid.');
      element.boundElements.forEach(item => { reference(item.id); if (byId.get(item.id).type !== item.type) throw Error('Project object binding types are invalid.'); });
    }
    for (const value of [element.startBinding, element.endBinding]) if (value != null) {
      if (typeof value !== 'object' || !value.elementId) throw Error('Project arrow bindings are invalid.');
      reference(value.elementId);
      if (element.type !== 'arrow' || !['rectangle', 'ellipse', 'diamond', 'text', 'image', 'frame'].includes(byId.get(value.elementId).type)) throw Error('Project arrow targets are invalid.');
    }
  }
  return active;
}

/** Clone a complete local project graph. Assets remain immutable content IDs; caller verifies/merges files. */
export function mergeProjectElements(current, source, offsetX, offsetY, makeId) {
  const existing = validateDrawingElements(current), incoming = validateProjectGraph(source);
  if (!incoming.length) throw Error('This project has no artwork to insert.');
  if (existing.length + incoming.length > 500) throw Error('Inserting this project would exceed 500 objects.');
  if (!Number.isFinite(offsetX) || !Number.isFinite(offsetY) || typeof makeId !== 'function') throw Error('Project placement is unavailable.');
  const used = new Set([...current, ...source].map(element => element.id));
  const groups = new Set();
  for (const element of [...current, ...incoming]) {
    if (element.groupIds != null && (!Array.isArray(element.groupIds) || element.groupIds.some(id => typeof id !== 'string' || !id || id.length > 128))) throw Error('Project groups are invalid.');
    for (const id of element.groupIds || []) { used.add(id); if (incoming.includes(element)) groups.add(id); }
  }
  const fresh = () => {
    const id = makeId();
    if (typeof id !== 'string' || !id || id.length > 128 || used.has(id)) throw Error('Could not allocate independent project identifiers. Try again.');
    used.add(id); return id;
  };
  const ids = new Map(incoming.map(element => [element.id, fresh()]));
  const groupIds = new Map(Array.from(groups, id => [id, fresh()]));
  const reference = id => {
    if (id == null) return id;
    if (!ids.has(id)) throw Error('This project refers to artwork that is missing. Choose a complete project or repair a copy of the file.');
    return ids.get(id);
  };
  const binding = value => {
    if (value == null) return value;
    if (typeof value !== 'object' || !value.elementId) throw Error('Project arrow bindings are invalid.');
    return { ...value, elementId: reference(value.elementId) };
  };
  const copies = incoming.map(element => {
    const copy = JSON.parse(JSON.stringify(element));
    copy.id = ids.get(element.id); copy.x += offsetX; copy.y += offsetY;
    copy.index = null; copy.version = 1; copy.versionNonce = 0; copy.updated = Date.now(); copy.isDeleted = false;
    if (copy.groupIds) copy.groupIds = copy.groupIds.map(id => groupIds.get(id));
    if (copy.containerId != null) copy.containerId = reference(copy.containerId);
    if (copy.frameId != null) copy.frameId = reference(copy.frameId);
    if (copy.boundElements != null) {
      if (!Array.isArray(copy.boundElements) || copy.boundElements.some(item => !item || typeof item.id !== 'string' || !item.id || !['text', 'arrow'].includes(item.type))) throw Error('Project object bindings are invalid.');
      copy.boundElements = copy.boundElements.map(item => ({ ...item, id: reference(item.id) }));
    }
    if (copy.startBinding != null) copy.startBinding = binding(copy.startBinding);
    if (copy.endBinding != null) copy.endBinding = binding(copy.endBinding);
    return copy;
  });
  const elements = [...current, ...copies];
  validateDrawingElements(elements);
  return { elements, insertedIds: copies.map(element => element.id) };
}
