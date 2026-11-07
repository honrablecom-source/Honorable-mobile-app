import clipping from 'polygon-clipping';
import earcut, { flatten, deviation } from 'earcut';
import { validateBooleanInput, shapeRing } from './shape-boolean-source.mjs';
export const BOOLEAN_OPERATIONS = ['union', 'difference', 'intersection', 'xor'];
export const MAX_RESULT_VERTICES = 2048;
export const signedArea = ring => ring.reduce((sum, point, index) => { const next = ring[(index + 1) % ring.length]; return sum + point[0] * next[1] - next[0] * point[1]; }, 0) / 2;
const fail = () => { throw Error('These shapes could not be combined safely. Move or simplify them and try again.'); };

/** Bridge holes along a spanning tree of non-crossing triangulation edges, without rendering triangles/seams. */
export function connectedFill(polygon) {
  const rings = polygon.map(ring => ring.slice(0, -1));
  const data = flatten(rings), triangles = earcut(data.vertices, data.holes, 2);
  if (!triangles.length || triangles.length % 3 || !Number.isFinite(deviation(data.vertices, data.holes, 2, triangles)) || deviation(data.vertices, data.holes, 2, triangles) > 1e-8) fail();
  const vertices = rings.flat(), owner = rings.flatMap((ring, ringIndex) => ring.map((_, localIndex) => ({ ringIndex, localIndex })));
  const candidates = [], seen = new Set();
  for (let index = 0; index < triangles.length; index += 3) {
    const ids = triangles.slice(index, index + 3);
    if (ids.some(id => !Number.isInteger(id) || id < 0 || id >= vertices.length)) fail();
    if (Math.abs(signedArea(ids.map(id => vertices[id]))) <= 1e-12) fail();
    for (let edge = 0; edge < 3; edge++) {
      const a = ids[edge], b = ids[(edge + 1) % 3], key = [a, b].sort((x, y) => x - y).join(':');
      if (owner[a].ringIndex === owner[b].ringIndex || seen.has(key)) continue;
      seen.add(key); candidates.push({ a, b, distance: (vertices[a][0] - vertices[b][0]) ** 2 + (vertices[a][1] - vertices[b][1]) ** 2 });
    }
  }
  candidates.sort((a, b) => a.distance - b.distance || a.a - b.a || a.b - b.b);
  const parent = rings.map((_, index) => index), edges = rings.map(() => []);
  const root = index => { while (parent[index] !== index) index = parent[index]; return index; };
  let joined = 0;
  for (const { a, b } of candidates) {
    const A = owner[a], B = owner[b], x = root(A.ringIndex), y = root(B.ringIndex);
    if (x === y) continue;
    parent[x] = y; joined++;
    edges[A.ringIndex].push({ at: A.localIndex, to: B.ringIndex, entry: B.localIndex });
    edges[B.ringIndex].push({ at: B.localIndex, to: A.ringIndex, entry: A.localIndex });
  }
  if (joined !== rings.length - 1) fail();
  const walk = (ringIndex, start, previous) => {
    const ring = rings[ringIndex], path = [];
    for (let step = 0; step < ring.length; step++) {
      const index = (start + step) % ring.length, point = ring[index]; path.push([...point]);
      for (const edge of edges[ringIndex]) if (edge.to !== previous && edge.at === index) { path.push(...walk(edge.to, edge.entry, ringIndex)); path.push([...point]); }
    }
    path.push([...ring[start]]); return path;
  };
  const path = walk(0, 0, -1), expected = polygon.reduce((sum, ring) => sum + signedArea(ring), 0);
  if (Math.abs(signedArea(path) - expected) > Math.max(1e-7, Math.abs(expected) * 1e-8)) fail();
  return path;
}
function pathElement(points, style, filled) {
  const [x, y] = points[0];
  const width = Math.max(...points.map(p => p[0])) - Math.min(...points.map(p => p[0])), height = Math.max(...points.map(p => p[1])) - Math.min(...points.map(p => p[1]));
  if (!(width > .000001 && height > .000001)) fail();
  return { type: 'line', x, y, width, height, points: points.map(point => [point[0] - x, point[1] - y]), backgroundColor: filled ? style.backgroundColor : 'transparent', strokeColor: filled ? 'transparent' : style.strokeColor, strokeWidth: filled ? 1 : style.strokeWidth, strokeStyle: style.strokeStyle, roughness: 0, fillStyle: 'solid', opacity: style.opacity, angle: 0, roundness: null, startArrowhead: null, endArrowhead: null };
}
export function combineShapes(input, operation) {
  const { shapes, existingCount } = validateBooleanInput(input);
  if (!BOOLEAN_OPERATIONS.includes(operation)) throw Error('Choose a supported shape operation.');
  const sourceRings = shapes.map(shapeRing), all = sourceRings.flat(), origin = [Math.min(...all.map(p => p[0])), Math.min(...all.map(p => p[1]))];
  const rings = sourceRings.map(ring => ring.map(([x, y]) => [x - origin[0], y - origin[1]]));
  let polygons;
  try {
    const geometries = rings.map(ring => [ring]);
    polygons = operation === 'difference' ? clipping.difference(clipping.union(...geometries.slice(0, -1)), geometries[geometries.length - 1]) : clipping[operation](...geometries);
  } catch { fail(); }
  if (!polygons.length) throw Error('This operation leaves no filled area. Move the shapes or choose another operation.');
  if (polygons.length > 32 || polygons.reduce((n, polygon) => n + polygon.length, 0) > 64 || polygons.flat(2).length > MAX_RESULT_VERTICES) throw Error('The result is too detailed. Use fewer or simpler shapes.');
  if (polygons.flat(2).some(point => point.length !== 2 || point.some(value => !Number.isFinite(value)))) fail();
  const area = polygons.reduce((sum, polygon) => sum + polygon.reduce((n, ring) => n + signedArea(ring), 0), 0);
  if (!Number.isFinite(area) || area <= 1e-7) fail();
  const styleIndex = operation === 'difference' ? shapes.length - 2 : shapes.length - 1, style = shapes[styleIndex];
  const visibleColor = value => value !== 'transparent' && !(value.length === 5 && value.endsWith('0')) && !(value.length === 9 && value.endsWith('00'));
  if (!visibleColor(style.backgroundColor) && (!visibleColor(style.strokeColor) || style.strokeWidth === 0)) throw Error('Give the result-style shape a visible fill or outline first.');
  const plan = [];
  for (const polygon of polygons) {
    plan.push(pathElement(connectedFill(polygon), style, true));
    if (visibleColor(style.strokeColor) && style.strokeWidth > 0) for (const ring of polygon) plan.push(pathElement(ring, style, false));
  }
  if (existingCount + plan.length > 500) throw Error('The new paths would exceed 500 drawing objects. Remove some objects first.');
  const points = polygons.flat(2), left = Math.min(...points.map(p => p[0])), top = Math.min(...points.map(p => p[1])), right = Math.max(...points.map(p => p[0])), bottom = Math.max(...points.map(p => p[1]));
  const placement = { x: Math.max(...all.map(p => p[0])) + 48 - left, y: origin[1] - top };
  for (const shape of plan) {
    shape.x += placement.x; shape.y += placement.y;
    if (![shape.x, shape.y, shape.width, shape.height].every(value => Number.isFinite(value) && Math.abs(value) <= 1e6)) throw Error('Move these shapes closer to the canvas origin before combining.');
  }
  return { operation, polygons, plan, area, width: right - left, height: bottom - top, bounds: [left, top, right, bottom], sourceCount: shapes.length, styleIndex, ellipseApproximation: shapes.some(s => s.type === 'ellipse') };
}
