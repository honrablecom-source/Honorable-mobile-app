/** A local directed acyclic graph becomes ordinary bound drawing shapes/text/arrows. */
export const MAX_DEPENDENCY_NODES = 20, MAX_DEPENDENCY_EDGES = 32;
export function dependencyPlan(heading, input) {
  if (typeof heading !== 'string' || !heading.trim() || heading.length > 60 || /[\x00-\x1f\x7f]/.test(heading)) throw Error('Use a diagram title of 1–60 characters on one line.');
  if (typeof input !== 'string' || input.length > 4096) throw Error('Use up to 4,096 characters of dependency rows.');
  const lines = input.split('\n').map(line => line.trim()).filter(Boolean);
  if (!lines.length || lines.length > 64) throw Error('Add a label or a Label -> Label row, up to 64 rows.');
  const nodes = new Map(), edges = new Map();
  const node = value => {
    const label = value.trim();
    if (!label || label.length > 40 || /[\x00-\x1f\x7f]/.test(label)) throw Error('Use node labels of 1–40 characters on one line.');
    if (!nodes.has(label)) nodes.set(label, { id: `dependency-node-${nodes.size}`, label, children: [], incoming: 0, level: 0 });
    if (nodes.size > MAX_DEPENDENCY_NODES) throw Error('Use up to 20 distinct diagram nodes.');
    return nodes.get(label);
  };
  for (const line of lines) {
    const parts = line.split('->');
    if (parts.length > 2) throw Error('Use one dependency per line: Label -> Label.');
    const from = node(parts[0]);
    if (parts.length === 1) continue;
    const to = node(parts[1]);
    if (from === to) throw Error('A node cannot depend on itself.');
    const key = JSON.stringify([from.label, to.label]);
    if (edges.has(key)) continue;
    if (edges.size >= MAX_DEPENDENCY_EDGES) throw Error('Use up to 32 distinct diagram connections.');
    edges.set(key, { from, to });
    from.children.push(to); to.incoming++;
  }
  const queue = [...nodes.values()].filter(node => node.incoming === 0);
  let visited = 0;
  while (queue.length) {
    const current = queue.shift(); visited++;
    for (const child of current.children) {
      child.level = Math.max(child.level, current.level + 1);
      if (--child.incoming === 0) queue.push(child);
    }
  }
  if (visited !== nodes.size) throw Error('This diagram contains a cycle. Remove a return connection and try again.');
  const columns = new Map();
  for (const current of nodes.values()) {
    const row = columns.get(current.level) || 0;
    current.x = current.level * 320; current.y = 88 + row * 140;
    columns.set(current.level, row + 1);
  }
  const common = { strokeColor: '#111111', strokeWidth: 1, roughness: 0 };
  const arrows = [...edges.values()].map(({ from, to }, index) => ({
    type: 'arrow', id: `dependency-edge-${index}`, x: from.x + 220, y: from.y + 40,
    points: [[0, 0], [to.x - from.x - 220, to.y - from.y]],
    start: { id: from.id }, end: { id: to.id }, endArrowhead: 'arrow', ...common,
  }));
  const boxes = [...nodes.values()].map(current => ({
    type: 'rectangle', id: current.id, x: current.x, y: current.y, width: 220, height: 80,
    backgroundColor: '#ffffff', fillStyle: 'solid', ...common,
    label: { text: current.label, fontSize: 16, fontFamily: 2, textAlign: 'center', verticalAlign: 'middle' },
  }));
  return [
    { type: 'text', x: 0, y: 0, text: heading.trim(), fontSize: 28, fontFamily: 2, strokeColor: '#111111' },
    { type: 'text', x: 0, y: 42, text: 'Dependencies · left to right', fontSize: 16, fontFamily: 2, strokeColor: '#111111' },
    ...arrows, ...boxes,
  ];
}
