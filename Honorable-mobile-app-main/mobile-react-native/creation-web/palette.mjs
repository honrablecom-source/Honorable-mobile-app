import { validateDrawingElements } from './scene.mjs';
import { normalizedColor } from './composition.mjs';

export const MAX_PALETTE_COLORS = 32;
/** Assigned stroke/fill colors with object opacity; not a raster/pixel sampler. */
export function artworkPalette(elements) {
  validateDrawingElements(elements);
  const colors = new Map();
  for (const element of elements) {
    if (element.isDeleted || element.opacity === 0 || element.customData?.nobleHidden) continue;
    const used = new Set();
    for (const field of ['strokeColor', 'backgroundColor']) {
      if (!element[field]) continue;
      const normalized = normalizedColor(element[field]);
      if (normalized === 'transparent') continue;
      const alpha = Math.round((normalized.length === 9 ? parseInt(normalized.slice(7), 16) : 255) * (element.opacity ?? 100) / 100);
      if (!alpha) continue;
      const color = normalized.slice(0, 7) + (alpha === 255 ? '' : alpha.toString(16).padStart(2, '0'));
      used.add(color);
    }
    for (const color of used) colors.set(color, (colors.get(color) || 0) + 1);
  }
  if (!colors.size) throw Error('Add visible shapes or text with a stroke or fill color first.');
  if (colors.size > MAX_PALETTE_COLORS) throw Error('Use up to 32 visible artwork colors before creating a color key.');
  return [...colors].sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0).map(([color, count]) => ({ color, count }));
}
/** Ordinary editable swatches and labels; no media, account data or source mutation. */
export function palettePlan(elements, heading) {
  if (typeof heading !== 'string' || !heading.trim() || heading.length > 60 || /[\x00-\x1f\x7f]/.test(heading)) throw Error('Use a color-key title of 1–60 characters on one line.');
  const palette = artworkPalette(elements);
  const text = (value, x, y, fontSize = 16) => ({ type: 'text', text: value, x, y, fontSize, fontFamily: 2, strokeColor: '#111111' });
  const shapes = [text(heading.trim(), 0, 0, 28), text('Artwork colors · alpha included in 8-digit hex', 0, 42)];
  palette.forEach(({ color, count }, index) => {
    const x = index % 4 * 190, y = 88 + Math.floor(index / 4) * 150;
    shapes.push({ type: 'rectangle', x, y, width: 150, height: 72, strokeColor: '#111111', backgroundColor: color, fillStyle: 'solid', roughness: 0, strokeWidth: 1 });
    shapes.push(text(`${color}\n${count} ${count === 1 ? 'object' : 'objects'}`, x, y + 82));
  });
  return shapes;
}
