import { encodeDraft } from './scene.mjs';
import { captureComponent } from './composition.mjs';
import { filesForElements } from './image-assets.mjs';

/** A detached render-only copy. null selects the whole drawing; an object selects artwork. */
export function imageArtwork(elements, files = {}, selectedIds = null, measure) {
  encodeDraft(elements, {}, files);
  let artwork = elements;
  if (selectedIds !== null) {
    if (typeof selectedIds !== 'object' || Array.isArray(selectedIds) || !elements.some(element => !element.isDeleted && Object.prototype.hasOwnProperty.call(selectedIds, element.id) && selectedIds[element.id])) throw Error('Select artwork on the canvas first.');
    artwork = captureComponent(elements, selectedIds, measure);
  }
  const visible = artwork.filter(element => !element.isDeleted && element.opacity !== 0);
  if (!visible.length) throw Error(selectedIds === null ? 'Add a visible object first.' : 'The selected artwork has no visible objects. Show it in Objects first.');
  return { elements: visible, files: filesForElements(visible, files) };
}
