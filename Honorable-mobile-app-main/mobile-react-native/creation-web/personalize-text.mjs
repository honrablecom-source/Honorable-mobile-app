import { restoreElements } from '@excalidraw/excalidraw';

/** Use the pinned renderer's wrapping/metrics, keeping all originalText and bounded source boxes. */
export async function fitPersonalizedText(elements, changed, requireCurrent = () => {}) {
  const source = JSON.parse(JSON.stringify(elements));
  let working = JSON.parse(JSON.stringify(elements));
  const limits = new Map(source.map(element => [element.id, element]));
  const refresh = () => restoreElements(working, null, { repairBindings: true, refreshDimensions: true });
  for (const id of changed) {
    await new Promise(resolve => setTimeout(resolve, 0)); requireCurrent();
    const initial = limits.get(id), container = limits.get(initial.containerId);
    let width = initial.width, height = initial.height;
    if (container && ['rectangle', 'ellipse', 'diamond'].includes(container.type)) {
      const factor = container.type === 'ellipse' ? Math.SQRT1_2 : container.type === 'diamond' ? .5 : 1;
      width = container.width * factor - 10; height = container.height * factor - 10;
    }
    if (!(width > 0 && height > 0)) throw Error('Make the field’s text box larger before personalizing.');
    const reset = size => {
      working = working.map(element => element.id === id ? { ...initial, fontSize: size, ...(!container ? { autoResize: false, width } : {}) } : element);
      working = refresh();
      const text = working.find(element => element.id === id);
      return text && text.width <= width + .01 && text.height <= height + .01;
    };
    if (reset(initial.fontSize)) continue;
    if (!reset(8)) throw Error('Some values will not fit at 8 pixels. Shorten them or enlarge the design’s text boxes.');
    let low = 8, high = initial.fontSize;
    for (let pass = 0; pass < 9 && high - low > .25; pass++) {
      const middle = Math.floor((low + high) * 2) / 4;
      if (reset(middle)) low = middle; else high = middle;
    }
    if (!reset(low)) throw Error('The text could not fit safely. Enlarge its box and try again.');
  }
  return working;
}
