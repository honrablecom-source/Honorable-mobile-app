import { parsePath, absolutize, normalize } from 'path-data-parser';
import { pointsOnBezierCurves } from 'points-on-curve';
import clipping from 'polygon-clipping';
import { connectedFill, signedArea } from './shape-boolean.mjs';
import { validateDrawingElements } from './scene.mjs';

import { SVG_LIMITS } from './svg-import-limits.mjs';
export { SVG_LIMITS } from './svg-import-limits.mjs';
const arity = { M: 2, L: 2, H: 1, V: 1, C: 6, S: 4, Q: 4, T: 2, A: 7, Z: 0 };
const coordinate = value => Number.isFinite(value) && Math.abs(value) <= 1e6;
const same = (a, b) => Math.abs(a[0] - b[0]) < 1e-9 && Math.abs(a[1] - b[1]) < 1e-9;
const visible = color => color !== 'transparent' && !(color.length === 5 && color.endsWith('0')) && !(color.length === 9 && color.endsWith('00'));
const fail = () => { throw Error('This vector path is too complex or intersects itself. Simplify it before importing.'); };

// The pinned upstream parser accepts an implicit initial move and can loop on numbers
// after Z. Validate its complete token grammar before handing it any untrusted data.
export function strictSvgPath(d) {
  if (typeof d !== 'string' || !d.trim() || d.length > 16384) throw Error('Use a nonempty vector path up to 16 KB.');
  const tokens = [], pattern = /[a-zA-Z]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/g;
  let end = 0;
  for (const match of d.matchAll(pattern)) {
    if (!/^[\s,]*$/.test(d.slice(end, match.index))) throw Error('The SVG path contains unsupported syntax.');
    tokens.push(match[0]); end = match.index + match[0].length;
    if (tokens.length > 4096) throw Error('The SVG path has too many commands.');
  }
  if (!/^[\s,]*$/.test(d.slice(end)) || !/^[Mm]$/.test(tokens[0])) throw Error('Every SVG path must start with a move command.');
  let index = 0, mode, count = 0;
  while (index < tokens.length) {
    if (/^[a-zA-Z]$/.test(tokens[index])) mode = tokens[index++];
    if (!mode || !Object.prototype.hasOwnProperty.call(arity, mode.toUpperCase())) throw Error('The SVG path contains an unsupported command.');
    const key = mode.toUpperCase(), length = arity[key];
    if (++count > SVG_LIMITS.commands) throw Error('The SVG path has too many commands.');
    if (!length) { mode = null; continue; }
    const values = tokens.slice(index, index + length).map(Number);
    if (values.length !== length || values.some(value => !coordinate(value))) throw Error('The SVG path has incomplete or oversized coordinates.');
    if (key === 'A' && (values[0] < 0 || values[1] < 0 || ![0, 1].includes(values[3]) || ![0, 1].includes(values[4]))) throw Error('Use valid SVG arc radii and flags.');
    index += length;
    if (key === 'M') mode = mode === 'M' ? 'L' : 'l';
  }
  try {
    const commands = normalize(absolutize(parsePath(d)));
    if (!commands.length || commands.length > 2048 || commands.some(command => !['M', 'L', 'C', 'Z'].includes(command.key) || command.data.some(value => !coordinate(value)))) fail();
    return commands;
  } catch { throw Error('The SVG path could not be interpreted. Check its commands.'); }
}

const transform = (point, matrix) => {
  const [a, b, c, d, e, f] = matrix, [x, y] = point, result = [a * x + c * y + e, b * x + d * y + f];
  if (!result.every(coordinate)) throw Error('Move or scale the vector artwork closer to its canvas.');
  return result;
};
export function flattenSvgPath(d, matrix = [1, 0, 0, 1, 0, 0]) {
  if (!Array.isArray(matrix) || matrix.length !== 6 || !matrix.every(coordinate)) throw Error('The SVG transform is invalid.');
  const paths = []; let current = null, count = 0;
  const add = point => {
    if (!current) throw Error('Start each vector subpath with a move command.');
    if (!current.points.length || !same(current.points[current.points.length - 1], point)) {
      current.points.push(point); if (++count > SVG_LIMITS.points) fail();
    }
  };
  for (const command of strictSvgPath(d)) {
    if (command.key === 'M') {
      if (paths.length >= SVG_LIMITS.rings) fail();
      current = { points: [], closed: false }; paths.push(current); add(transform(command.data, matrix));
    } else if (command.key === 'L') add(transform(command.data, matrix));
    else if (command.key === 'C') {
      if (!current?.points.length) fail();
      const controls = [current.points[current.points.length - 1], ...[0, 2, 4].map(offset => transform(command.data.slice(offset, offset + 2), matrix))];
      // Sample after transformation, so the approximation is in final drawing units.
      for (const point of pointsOnBezierCurves(controls, .15)) add(point);
    } else if (command.key === 'Z') { add([...current.points[0]]); current.closed = true; }
  }
  return paths.filter(path => path.points.length > 1);
}

const cross = (a, b, c) => (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
const onSegment = (a, b, p) => Math.abs(cross(a, b, p)) < 1e-8 && p[0] >= Math.min(a[0], b[0]) - 1e-9 && p[0] <= Math.max(a[0], b[0]) + 1e-9 && p[1] >= Math.min(a[1], b[1]) - 1e-9 && p[1] <= Math.max(a[1], b[1]) + 1e-9;
function intersects(a, b, c, d) {
  const ac = cross(a, b, c), ad = cross(a, b, d), ca = cross(c, d, a), cb = cross(c, d, b);
  return (ac * ad < 0 && ca * cb < 0) || onSegment(a, b, c) || onSegment(a, b, d) || onSegment(c, d, a) || onSegment(c, d, b);
}
function simpleRing(points) {
  const ring = points.map(point => [...point]);
  if (!same(ring[0], ring[ring.length - 1])) ring.push([...ring[0]]);
  if (ring.length < 4 || Math.abs(signedArea(ring)) < 1e-7) fail();
  for (let i = 0; i < ring.length - 1; i++) for (let j = i + 2; j < ring.length - 1; j++) {
    if (i === 0 && j === ring.length - 2) continue;
    if (intersects(ring[i], ring[i + 1], ring[j], ring[j + 1])) fail();
  }
  return ring;
}
function bounded(polygons) {
  if (polygons.length > 64 || polygons.reduce((n, polygon) => n + polygon.length, 0) > 128 || polygons.flat(2).length > SVG_LIMITS.points || polygons.flat(2).some(point => point.length !== 2 || !point.every(coordinate))) fail();
  return polygons;
}
const union = geometries => geometries.length ? bounded(clipping.union(...geometries)) : [];
export function svgFilledPolygons(paths, rule) {
  if (!['nonzero', 'evenodd'].includes(rule) || !Array.isArray(paths) || paths.length > SVG_LIMITS.rings) throw Error('The SVG fill rule or subpath count is unsupported.');
  const rings = paths.map(path => simpleRing(path.points));
  if (!rings.length) return [];
  if (rule === 'evenodd') return bounded(clipping.xor(...rings.map(ring => [ring])));
  // Keep disjoint regions by signed winding count. Union(positive)-Union(negative)
  // would incorrectly erase places covered twice positively and once negatively.
  let levels = new Map();
  for (const ring of rings) {
    const geometry = bounded(clipping.union([ring])), direction = signedArea(ring) > 0 ? 1 : -1;
    const coverage = union([...levels.values()]), next = new Map();
    const add = (level, value) => { if (level && value.length) next.set(level, union([...(next.has(level) ? [next.get(level)] : []), value])); };
    for (const [level, previous] of levels) {
      add(level, bounded(clipping.difference(previous, geometry)));
      add(level + direction, bounded(clipping.intersection(previous, geometry)));
    }
    add(direction, coverage.length ? bounded(clipping.difference(geometry, coverage)) : geometry);
    levels = next;
  }
  return union([...levels.values()]);
}
function pathElement(points, style, filled, strokeWidth) {
  const [x, y] = points[0], xs = points.map(point => point[0]), ys = points.map(point => point[1]);
  return { type: 'line', x, y, width: Math.max(...xs) - Math.min(...xs), height: Math.max(...ys) - Math.min(...ys), points: points.map(point => [point[0] - x, point[1] - y]), backgroundColor: filled ? style.fill : 'transparent', strokeColor: filled ? 'transparent' : style.stroke, strokeWidth: filled ? 1 : strokeWidth, strokeStyle: 'solid', fillStyle: 'solid', roughness: 0, angle: 0, opacity: style.opacity, roundness: null, startArrowhead: null, endArrowhead: null };
}
export function vectorArtwork(input) {
  if (!input || !Array.isArray(input.items) || !input.items.length || input.items.length > SVG_LIMITS.items || !Number.isInteger(input.existingCount) || input.existingCount < 0 || input.existingCount > 500 || new TextEncoder().encode(JSON.stringify(input.items)).length > SVG_LIMITS.bytes) throw Error('Use up to 128 vector shapes in a file under 64 KB.');
  if (input.viewport && ![input.viewport.width, input.viewport.height].every(value => Number.isFinite(value) && value >= 1 && value <= 512)) throw Error('The SVG canvas dimensions are unsupported.');
  const plan = []; let sampledPoints = 0, hasCurves = false, hasOutlines = false;
  for (const item of input.items) {
    if (!item || !['nonzero', 'evenodd'].includes(item.fillRule) || !Array.isArray(item.matrix) || item.matrix.length !== 6 || !item.matrix.every(coordinate) || ![item.fill, item.stroke].every(color => typeof color === 'string' && /^(?:#[\da-f]{3}|#[\da-f]{4}|#[\da-f]{6}|#[\da-f]{8}|transparent)$/.test(color)) || !Number.isFinite(item.opacity) || item.opacity <= 0 || item.opacity > 100 || !Number.isFinite(item.strokeWidth) || item.strokeWidth < 0 || item.strokeWidth > 64) throw Error('The SVG paint or opacity is unsupported.');
    const paths = flattenSvgPath(item.d, item.matrix); sampledPoints += paths.reduce((n, path) => n + path.points.length, 0);
    if (sampledPoints > SVG_LIMITS.points) fail();
    hasCurves ||= /[AaCcQqSsTt]/.test(item.d);
    const fill = visible(item.fill), stroke = visible(item.stroke) && item.strokeWidth > 0;
    if (!fill && !stroke) continue;
    if (item.opacity < 100 && stroke && (fill || paths.length > 1)) throw Error('Separate partly transparent fills and outlines before importing this SVG.');
    if (fill) for (const polygon of svgFilledPolygons(paths, item.fillRule)) plan.push(pathElement(connectedFill(polygon), item, true, 1));
    if (stroke) {
      const [a, b, c, d] = item.matrix, scale = Math.hypot(a, b);
      if (!(scale > 1e-9) || Math.abs(scale - Math.hypot(c, d)) > Math.max(1e-7, scale * 1e-8) || Math.abs(a * c + b * d) > Math.max(1e-7, scale * scale * 1e-8)) throw Error('Use uniform scaling for SVG outlines before importing.');
      const width = item.strokeWidth * scale;
      if (width <= 0 || width > 64) throw Error('The scaled SVG outline is too wide.');
      hasOutlines = true;
      for (const path of paths) plan.push(pathElement(path.points, item, false, width));
    }
    if (plan.length > SVG_LIMITS.paths || plan.length + input.existingCount > 500) throw Error('The imported paths would exceed the drawing object limit.');
  }
  if (!plan.length) throw Error('This SVG contains no supported visible artwork.');
  validateDrawingElements(plan.map((element, index) => ({ ...element, id: `vector-${index}` })));
  const points = plan.flatMap(element => element.points.map(([x, y]) => [x + element.x, y + element.y]));
  const bounds = [Math.min(...points.map(p => p[0])), Math.min(...points.map(p => p[1])), Math.max(...points.map(p => p[0])), Math.max(...points.map(p => p[1]))];
  if (input.viewport) for (const element of plan) {
    const margin = visible(element.strokeColor) ? element.strokeWidth / 2 : 0;
    for (const [x, y] of element.points) if (x + element.x - margin < -1e-6 || y + element.y - margin < -1e-6 || x + element.x + margin > input.viewport.width + 1e-6 || y + element.y + margin > input.viewport.height + 1e-6) throw Error('Artwork extends beyond its SVG canvas. Expand the canvas before importing.');
  }
  return { plan, bounds, width: bounds[2] - bounds[0], height: bounds[3] - bounds[1], sourceCount: input.items.length, sampledPoints, hasCurves, hasOutlines };
}
