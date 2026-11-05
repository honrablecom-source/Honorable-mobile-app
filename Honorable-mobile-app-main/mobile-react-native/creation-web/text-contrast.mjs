// W3C G18 sRGB relative luminance: https://www.w3.org/WAI/WCAG22/Techniques/general/G18
function color(value) {
  if (value === 'transparent') return { rgb: [0, 0, 0], alpha: 0 };
  if (typeof value !== 'string' || !/^#(?:[\da-f]{3}|[\da-f]{4}|[\da-f]{6}|[\da-f]{8})$/i.test(value.trim())) throw Error('Use a hexadecimal color.');
  let hex = value.trim().slice(1).toLowerCase();
  if (hex.length <= 4) hex = Array.from(hex, digit => digit + digit).join('');
  return { rgb: [0, 2, 4].map(index => parseInt(hex.slice(index, index + 2), 16)), alpha: hex.length === 8 ? parseInt(hex.slice(6), 16) / 255 : 1 };
}
function luminance(rgb) {
  return rgb.map(channel => { const value = channel / 255; return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4; })
    .reduce((total, value, index) => total + value * [0.2126, 0.7152, 0.0722][index], 0);
}
/** Source-over color/opacity estimate on a chosen solid opaque sRGB background; not an image sampler. */
export function textColorRatio(foreground, background, opacity = 100) {
  const front = color(foreground), back = color(background);
  if (back.alpha !== 1) throw Error('Choose an opaque background color, such as #ffffff.');
  if (!Number.isFinite(opacity) || opacity < 0 || opacity > 100) throw Error('Text opacity must be 0–100.');
  const alpha = front.alpha * opacity / 100;
  const painted = front.rgb.map((channel, index) => channel * alpha + back.rgb[index] * (1 - alpha));
  const first = luminance(painted), second = luminance(back.rgb);
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
}
export function formatContrastRatio(ratio) {
  return ratio < 4.5 && Number(ratio.toFixed(2)) >= 4.5 ? '<4.50' : ratio.toFixed(2);
}
export function reviewTextColors(elements, background) {
  // Validate the chosen background even when the composition has no text.
  textColorRatio('#000000', background);
  if (!Array.isArray(elements) || elements.length > 500 || elements.some(element => !element || typeof element !== 'object')) throw Error('Unsupported object list.');
  return elements.filter(element => !element.isDeleted).map((element, index) => ({ element, number: index + 1 }))
    .filter(({ element }) => element.type === 'text' && element.opacity !== 0 && !element.customData?.nobleHidden)
    .map(({ element, number }) => {
      const foreground = element.strokeColor ?? '#111111', opacity = element.opacity ?? 100;
      const row = { id: element.id, number, label: Array.from(String(element.originalText ?? element.text ?? '')).slice(0, 40).join(''), foreground, opacity };
      try { const ratio = textColorRatio(foreground, background, opacity); return { ...row, ratio, meetsReference: ratio >= 4.5 }; }
      catch { return { ...row, ratio: null, error: 'This text color could not be compared.' }; }
    });
}
