import test from 'node:test';
import assert from 'node:assert/strict';
import { dependencyPlan } from '../dependencies.mjs';
const nodes = plan => plan.filter(shape => shape.type === 'rectangle');
const edges = plan => plan.filter(shape => shape.type === 'arrow');

test('dependency diagram lays out branching and merging nodes without losing connections', () => {
  const plan = dependencyPlan('Launch', 'Draft -> Edit\nDraft -> Artwork\nEdit -> Review\nArtwork -> Review\nReview -> Publish');
  const boxes = nodes(plan), arrows = edges(plan);
  assert.equal(boxes.length, 5); assert.equal(arrows.length, 5);
  const byId = new Map(boxes.map(shape => [shape.id, shape]));
  for (const arrow of arrows) {
    const from = byId.get(arrow.start.id), to = byId.get(arrow.end.id);
    assert(from && to); assert(to.x > from.x);
    assert.equal(arrow.x, from.x + from.width);
    assert.equal(arrow.x + arrow.points[1][0], to.x);
    assert.equal(arrow.y + arrow.points[1][1], to.y + to.height / 2);
  }
  const review = boxes.find(shape => shape.label.text === 'Review');
  assert.equal(review.x, 640);
  assert.equal(new Set(boxes.map(shape => `${shape.x},${shape.y}`)).size, boxes.length);
  assert.deepEqual(dependencyPlan('Launch', 'Draft -> Edit\nDraft -> Artwork\nEdit -> Review\nArtwork -> Review\nReview -> Publish'), plan);
});

test('duplicate rows collapse and disconnected standalone nodes remain editable', () => {
  const plan = dependencyPlan('  Plan  ', '  Start -> Finish  \nStart -> Finish\n\nNotes\r\n');
  assert.equal(edges(plan).length, 1); assert.equal(nodes(plan).length, 3);
  assert.equal(plan[0].text, 'Plan');
  assert(nodes(plan).some(shape => shape.label.text === 'Notes'));
  assert.equal(new Set(plan.filter(shape => shape.id).map(shape => shape.id)).size, 4);
});

test('cycles and invalid dependency text fail before any drawing insertion', () => {
  for (const input of ['A -> A', 'A -> B\nB -> A', 'A -> B\nB -> C\nC -> A', 'A -> B -> C', 'A -> ', '-> B', '', 'bad\tlabel', 'x'.repeat(41)]) assert.throws(() => dependencyPlan('Plan', input));
  for (const title of ['', ' ', 'x'.repeat(61), 'bad\nname']) assert.throws(() => dependencyPlan(title, 'A -> B'));
  assert.throws(() => dependencyPlan('Plan', 'x'.repeat(4097)));
  assert.throws(() => dependencyPlan('Plan', Array(65).fill('A').join('\n')));
});

test('graph node and edge limits are enforced with bounded geometry', () => {
  const isolated = Array.from({ length: 20 }, (_, i) => `Node ${i}`);
  assert.equal(nodes(dependencyPlan('Limit', isolated.join('\n'))).length, 20);
  assert.throws(() => dependencyPlan('Too many', [...isolated, 'Extra'].join('\n')), /20/);
  const connections = [];
  for (let from = 0; from < 9; from++) for (let to = from + 1; to < 9; to++) connections.push(`Node ${from} -> Node ${to}`);
  const limit = dependencyPlan('Edges', connections.slice(0, 32).join('\n'));
  assert.equal(edges(limit).length, 32);
  assert.throws(() => dependencyPlan('Too many edges', connections.slice(0, 33).join('\n')), /32/);
  assert(limit.every(shape => Number.isFinite(shape.x) && Number.isFinite(shape.y) && Math.abs(shape.x) < 10000 && Math.abs(shape.y) < 10000));
});
