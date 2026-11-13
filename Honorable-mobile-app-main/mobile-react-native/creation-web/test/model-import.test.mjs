import test from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import {
  defaultModel,
  buildDepthModel,
  exportModel,
  readModel,
} from '../model-studio.mjs';
import {
  inspectDepthGlb,
  encodedDepthGlb,
  decodedDepthGlb,
  receiveDepthModel,
  loadDepthModel,
  disposeImportedModel,
  MAX_DEPTH_GLB,
} from '../model-import.mjs';
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
async function fixture() {
  const root = new THREE.Group();
  const geometry = new THREE.PlaneGeometry(2, 1, 4, 3),
    positions = geometry.attributes.position;
  for (let i = 0; i < positions.count; i++) positions.setZ(i, (i % 5) / 20);
  geometry.computeVertexNormals();
  root.add(
    new THREE.Mesh(
      geometry,
      new THREE.MeshStandardMaterial({ color: '#ffffff' }),
    ),
  );
  const bytes = await exportModel(root, []);
  disposeImportedModel(root);
  return bytes;
}
function patched(bytes, change) {
  const json = inspectDepthGlb(bytes);
  change(json);
  const length = new DataView(
      bytes.buffer,
      bytes.byteOffset,
      bytes.byteLength,
    ).getUint32(12, true),
    binary = bytes.subarray(20 + length);
  let raw = new TextEncoder().encode(JSON.stringify(json));
  const padding = (4 - (raw.length % 4)) % 4;
  const output = new Uint8Array(20 + raw.length + padding + binary.length),
    view = new DataView(output.buffer);
  view.setUint32(0, 0x46546c67, true);
  view.setUint32(4, 2, true);
  view.setUint32(8, output.length, true);
  view.setUint32(12, raw.length + padding, true);
  view.setUint32(16, 0x4e4f534a, true);
  output.set(raw, 20);
  output.fill(32, 20 + raw.length, 20 + raw.length + padding);
  output.set(binary, 20 + raw.length + padding);
  return output;
}
test('real GLTFLoader preserves generated depth vertices and parallax GLB re-export', async () => {
  const bytes = await fixture(),
    encoded = encodedDepthGlb(bytes),
    source = await loadDepthModel(encoded);
  let mesh;
  source.traverse(object => {
    if (object.isMesh) mesh = object;
  });
  assert.equal(mesh.geometry.attributes.position.count, 20);
  assert(Math.abs(mesh.geometry.attributes.position.getZ(4) - 0.2) < 0.00001);
  const spec = {
    ...defaultModel(),
    shape: 'depth',
    asset: encoded,
    width: 3,
    height: 3,
    depth: 2,
    color: '#ffffff',
    animate: true,
  };
  assert.deepEqual(readModel(JSON.stringify(spec)), spec);
  const model = buildDepthModel(spec, source),
    mixer = new THREE.AnimationMixer(model.root);
  mixer.clipAction(model.clips[0]).play();
  mixer.setTime(spec.duration / 4);
  assert(
    Math.abs(
      new THREE.Euler().setFromQuaternion(model.root.quaternion).y - 0.2,
    ) < 0.00001,
  );
  const exported = await exportModel(model.root, model.clips);
  const json = JSON.parse(
    new TextDecoder()
      .decode(
        exported.subarray(
          20,
          20 + new DataView(exported.buffer).getUint32(12, true),
        ),
      )
      .trim(),
  );
  assert.equal(json.animations[0].name, 'Depth parallax');
  assert.equal(json.buffers[0].uri, undefined);
  assert(json.nodes.some(n => n.name === 'DepthSurface' && n.scale[2] === 2));
  model.dispose();
});
test('remote buffers/textures, compressed or excessive geometry and malformed binary fail before loader', async () => {
  const bytes = await fixture();
  const changes = [
    j => (j.buffers[0].uri = 'https://private.invalid/photo'),
    j =>
      (j.images = [
        { uri: 'https://private.invalid/photo', mimeType: 'image/png' },
      ]),
    j => (j.extensionsUsed = ['KHR_draco_mesh_compression']),
    j => (j.animations = [{}]),
    j =>
      (j.accessors[
        j.meshes[0].primitives[0].attributes.POSITION
      ].count = 20001),
    j => (j.accessors[0].sparse = {}),
    j => (j.bufferViews[0].byteLength = MAX_DEPTH_GLB),
    j => (j.meshes[0].primitives[0].mode = 0),
  ];
  for (const change of changes)
    assert.throws(() => inspectDepthGlb(patched(bytes, change)));
  assert.throws(() => inspectDepthGlb(bytes.subarray(0, -1)));
  assert.throws(() => inspectDepthGlb(new Uint8Array(MAX_DEPTH_GLB + 1)));
  assert.throws(() => decodedDepthGlb('not base64'));
});
test('complete native bridge chunks roundtrip and incomplete handoffs always release RAM lease', async () => {
  const bytes = await fixture(),
    encoded = encodedDepthGlb(bytes);
  let released = 0;
  const bridge = {
    getDepthModelLength: () => encoded.length,
    readDepthModelChunk: offset => encoded.slice(offset, offset + 32768),
    releaseDepthModel: () => released++,
  };
  assert.equal(receiveDepthModel(bridge), encoded);
  assert.deepEqual(decodedDepthGlb(encoded), bytes);
  assert.equal(released, 1);
  bridge.readDepthModelChunk = () => '';
  assert.throws(() => receiveDepthModel(bridge));
  assert.equal(released, 2);
  bridge.getDepthModelLength = () => 5 * 1024 * 1024;
  assert.throws(() => receiveDepthModel(bridge));
  assert.equal(released, 3);
  assert.equal(receiveDepthModel({}), null);
});
test('nonfinite depth positions are rejected by the actual loader ownership check', async () => {
  const bytes = await fixture(),
    json = inspectDepthGlb(bytes);
  const a = json.accessors[json.meshes[0].primitives[0].attributes.POSITION],
    v = json.bufferViews[a.bufferView],
    bin = 20 + new DataView(bytes.buffer).getUint32(12, true) + 8;
  new DataView(bytes.buffer).setFloat32(
    bin + (v.byteOffset || 0) + (a.byteOffset || 0),
    NaN,
    true,
  );
  await assert.rejects(
    () => loadDepthModel(encodedDepthGlb(bytes)),
    /vertices are invalid/,
  );
});
