import test from 'node:test';
import assert from 'node:assert/strict';
import {
  defaultModel,
  checkedModel,
  readModel,
  buildModel,
  exportModel,
  checkGlb,
  sendModel,
  MAX_GLB,
} from '../model-studio.mjs';
import * as THREE from 'three';
class Reader {
  readAsArrayBuffer(blob) {
    blob.arrayBuffer().then(value => {
      this.result = value;
      this.onloadend?.();
    });
  }
  readAsDataURL(blob) {
    blob.arrayBuffer().then(value => {
      this.result = `data:${blob.type};base64,${Buffer.from(value).toString(
        'base64',
      )}`;
      this.onloadend?.();
    });
  }
}
globalThis.FileReader = Reader;
const glbJson = bytes => {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.length);
  return JSON.parse(
    new TextDecoder()
      .decode(bytes.subarray(20, 20 + view.getUint32(12, true)))
      .trim(),
  );
};
test('saved model roundtrip retains material, dimensions and animation without accepting arbitrary fields', () => {
  const spec = {
    ...defaultModel(),
    shape: 'torus',
    animate: true,
    duration: 7,
    metalness: 0.8,
    width: 2,
  };
  assert.deepEqual(readModel(JSON.stringify(spec)), spec);
  for (const patch of [
    { shape: 'unknown' },
    { version: 2 },
    { width: NaN },
    { height: Infinity },
    { depth: 0 },
    { width: 5.1 },
    { metalness: -1 },
    { roughness: 2 },
    { color: 'red' },
    { animate: 1 },
    { duration: 13 },
    { texture: 'https://private.example/photo' },
    { texture: 'data:image/svg+xml;base64,AA==' },
    { extra: true },
  ])
    assert.throws(() => checkedModel({ ...spec, ...patch }));
  assert.throws(() => readModel('{'));
  assert.throws(() => readModel(' '.repeat(2 * 1024 * 1024 + 1025)));
});
test('all five bounded shapes produce real mesh geometry and disposable material', () => {
  for (const shape of ['box', 'sphere', 'cylinder', 'cone', 'torus']) {
    const model = buildModel({
      ...defaultModel(),
      shape,
      width: 2,
      height: 3,
      depth: 4,
    });
    const mesh = model.root.children[0];
    assert(mesh.geometry.attributes.position.count > 0);
    assert.deepEqual(mesh.scale.toArray(), [2, 3, 4]);
    assert.equal(mesh.material.roughness, 0.6);
    model.dispose();
  }
});
test('actual Three exporter creates self contained GLB with the requested turntable animation', async () => {
  const model = buildModel({ ...defaultModel(), animate: true, duration: 6 });
  model.root.rotation.y = 1.2;
  const original = model.root.quaternion.clone();
  const bytes = await exportModel(model.root, model.clips);
  const json = glbJson(bytes);
  assert.equal(json.asset.version, '2.0');
  assert.equal(json.animations.length, 1);
  assert.equal(json.animations[0].name, 'Turntable');
  assert(json.animations[0].channels.some(c => c.target.path === 'rotation'));
  assert.equal(json.buffers[0].uri, undefined);
  assert.equal(
    json.nodes.find(n => n.name === 'NobleModel').rotation,
    undefined,
  );
  assert(model.root.quaternion.equals(original));
  const mixer = new THREE.AnimationMixer(model.root);
  mixer.clipAction(model.clips[0]).play();
  mixer.setTime(1.5);
  assert(Math.abs(model.root.quaternion.y - Math.SQRT1_2) < 0.00001);
  mixer.setTime(6);
  assert(Math.abs(model.root.quaternion.y) < 0.00001);
  model.dispose();
});
test('static GLB does not claim animation', async () => {
  const model = buildModel(defaultModel());
  const json = glbJson(await exportModel(model.root, model.clips));
  assert.equal(json.animations, undefined);
  model.dispose();
});
test('binary transport copies every byte once across chunk boundaries and releases failed transfers', async () => {
  const model = buildModel({ ...defaultModel(), shape: 'sphere' });
  const bytes = await exportModel(model.root, model.clips);
  const chunks = [];
  let finished = 0,
    released = 0;
  const bridge = {
    beginModelShare: (id, length) => id === 5 && length === bytes.length,
    appendModelShareChunk: (id, chunk) => {
      chunks.push(Buffer.from(chunk, 'base64'));
      return true;
    },
    finishModelShare: () => finished++,
    releaseModelShare: () => released++,
  };
  sendModel(bridge, 5, bytes);
  assert(Buffer.concat(chunks).equals(Buffer.from(bytes)));
  assert.equal(finished, 1);
  assert.equal(released, 0);
  assert(chunks.length > 1);
  bridge.appendModelShareChunk = () => false;
  assert.throws(() => sendModel(bridge, 5, bytes));
  assert.equal(released, 1);
  assert.equal(finished, 1);
  bridge.beginModelShare = () => false;
  assert.throws(() => sendModel(bridge, 5, bytes));
  assert.equal(released, 1);
  model.dispose();
});
test('empty, corrupt, oversized, unavailable and invalid-id exports fail before transport', () => {
  for (const bytes of [
    new Uint8Array(0),
    new Uint8Array(20),
    new Uint8Array(MAX_GLB + 1),
  ])
    assert.throws(() => checkGlb(bytes));
  const bytes = new Uint8Array(20);
  const view = new DataView(bytes.buffer);
  view.setUint32(0, 0x46546c67, true);
  view.setUint32(4, 2, true);
  view.setUint32(8, 20, true);
  assert.throws(() => sendModel({}, 1, bytes));
  assert.throws(() => sendModel({}, 0, bytes));
  view.setUint32(8, 24, true);
  assert.throws(() => checkGlb(bytes));
});
