import { canvasPreview } from '../src/components/canvasGeometry';

test.each([[1080, 1080, '1:1', 'Square'], [1920, 1080, '16:9', 'Landscape'], [1080, 1920, '9:16', 'Portrait'], [800, 600, '4:3', 'Landscape'], [997, 991, '997:991', 'Landscape']] as const)('preview preserves exact reduced ratio for %s by %s', (width, height, ratio, orientation) => {
  const result = canvasPreview(width, height)!;
  expect(result.ratio).toBe(ratio); expect(result.orientation).toBe(orientation);
  expect(result.width / result.height).toBeCloseTo(width / height, 10);
});

test('all dimension extremes fit without distorting or hiding a thin canvas', () => {
  for (const width of [64, 65, 1080, 4095, 4096]) for (const height of [64, 65, 1080, 4095, 4096]) {
    const result = canvasPreview(width, height)!;
    expect(result.width).toBeGreaterThan(2); expect(result.height).toBeGreaterThan(2);
    expect(result.width).toBeLessThanOrEqual(232); expect(result.height).toBeLessThanOrEqual(144);
    expect(result.width / result.height).toBeCloseTo(width / height, 10);
  }
  // A visual preview can explain an oversized choice without authorizing native creation.
  expect(canvasPreview(4096, 4096)?.ratio).toBe('1:1');
});

test.each([[0, 64], [64, 63], [4097, 64], [64, NaN], [Infinity, 64], [100.5, 200], [-64, 64]])('invalid dimensions have no misleading preview: %s by %s', (width, height) => {
  expect(canvasPreview(width, height)).toBeNull();
});
