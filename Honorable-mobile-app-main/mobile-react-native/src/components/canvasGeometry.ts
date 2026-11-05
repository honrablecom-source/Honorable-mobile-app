/** Informational geometry only; native creation keeps its independent pixel-budget validation. */
export function canvasPreview(width: number, height: number) {
  if (![width, height].every(value => Number.isInteger(value) && value >= 64 && value <= 4096)) return null;
  let a = width, b = height;
  while (b) { const remainder = a % b; a = b; b = remainder; }
  const scale = Math.min(232 / width, 144 / height);
  return {
    width: width * scale,
    height: height * scale,
    ratio: `${width / a}:${height / a}`,
    orientation: width === height ? 'Square' : width > height ? 'Landscape' : 'Portrait',
  };
}
