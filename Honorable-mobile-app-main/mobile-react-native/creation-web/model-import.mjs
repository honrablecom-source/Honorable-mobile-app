import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
export const MAX_DEPTH_GLB = 3 * 1024 * 1024;
export const MAX_DEPTH_TEXT = 4 * 1024 * 1024;
export function inspectDepthGlb(bytes) {
  if (
    !(bytes instanceof Uint8Array) ||
    bytes.length < 24 ||
    bytes.length > MAX_DEPTH_GLB
  )
    throw Error('Use an embedded depth GLB up to 3 MiB.');
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (
    view.getUint32(0, true) !== 0x46546c67 ||
    view.getUint32(4, true) !== 2 ||
    view.getUint32(8, true) !== bytes.length
  )
    throw Error('Depth model header is incomplete.');
  const length = view.getUint32(12, true);
  if (
    view.getUint32(16, true) !== 0x4e4f534a ||
    length < 4 ||
    length % 4 ||
    length > 1024 * 1024 ||
    20 + length + 8 > bytes.length
  )
    throw Error('Depth model JSON is unsupported.');
  const json = JSON.parse(
    new TextDecoder('utf-8', { fatal: true })
      .decode(bytes.subarray(20, 20 + length))
      .trim(),
  );
  const bin = 20 + length;
  if (
    view.getUint32(bin + 4, true) !== 0x004e4942 ||
    view.getUint32(bin, true) % 4 ||
    bin + 8 + view.getUint32(bin, true) !== bytes.length
  )
    throw Error('Depth model binary is incomplete.');
  if (
    json.asset?.version !== '2.0' ||
    json.buffers?.length !== 1 ||
    json.buffers[0].uri !== undefined ||
    !Number.isInteger(json.buffers[0].byteLength) ||
    json.buffers[0].byteLength < 1 ||
    json.buffers[0].byteLength > view.getUint32(bin, true)
  )
    throw Error('Depth model must embed its buffer.');
  for (const [key, max] of Object.entries({
    meshes: 4,
    nodes: 12,
    materials: 4,
    images: 2,
    textures: 2,
    accessors: 32,
    bufferViews: 32,
    scenes: 2,
  }))
    if (
      json[key] !== undefined &&
      (!Array.isArray(json[key]) || json[key].length > max)
    )
      throw Error('Depth model exceeds its scene budget.');
  if (
    !json.meshes?.length ||
    !json.nodes?.length ||
    json.skins?.length ||
    json.animations?.length ||
    json.extensionsRequired?.some(value => value !== 'KHR_materials_unlit') ||
    json.extensionsUsed?.some(value => value !== 'KHR_materials_unlit')
  )
    throw Error('Use a simple embedded depth mesh.');
  for (const image of json.images || [])
    if (
      image.uri !== undefined ||
      !Number.isInteger(image.bufferView) ||
      !['image/png', 'image/jpeg'].includes(image.mimeType)
    )
      throw Error('Depth model textures must be embedded PNG or JPEG.');
  for (const entry of json.bufferViews || [])
    if (
      entry.buffer !== 0 ||
      !Number.isInteger(entry.byteLength) ||
      entry.byteLength < 1 ||
      !Number.isInteger(entry.byteOffset || 0) ||
      (entry.byteOffset || 0) < 0 ||
      (entry.byteOffset || 0) + entry.byteLength > json.buffers[0].byteLength
    )
      throw Error('Depth buffer view is outside its binary.');
  for (const entry of json.accessors || [])
    if (
      !Number.isInteger(entry.count) ||
      entry.count < 1 ||
      entry.count > 120000 ||
      entry.sparse ||
      !Number.isInteger(entry.bufferView)
    )
      throw Error('Depth accessor is unsupported.');
  let vertices = 0;
  for (const mesh of json.meshes) {
    if (
      !Array.isArray(mesh.primitives) ||
      mesh.primitives.length < 1 ||
      mesh.primitives.length > 4
    )
      throw Error('Depth primitives exceed their budget.');
    for (const primitive of mesh.primitives) {
      const accessor = json.accessors?.[primitive.attributes?.POSITION];
      if (
        !accessor ||
        accessor.type !== 'VEC3' ||
        accessor.componentType !== 5126 ||
        primitive.targets ||
        (primitive.mode !== undefined && primitive.mode !== 4)
      )
        throw Error('Depth model must contain ordinary triangle meshes.');
      vertices += accessor.count;
    }
  }
  if (vertices > 20000) throw Error('Depth model exceeds 20,000 vertices.');
  return json;
}
export function encodedDepthGlb(bytes) {
  inspectDepthGlb(bytes);
  const chunks = [];
  for (let i = 0; i < bytes.length; i += 24576)
    chunks.push(btoa(String.fromCharCode(...bytes.subarray(i, i + 24576))));
  return chunks.join('');
}
export function decodedDepthGlb(value) {
  if (
    typeof value !== 'string' ||
    !value.length ||
    value.length > MAX_DEPTH_TEXT ||
    value.length % 4 ||
    !/^[A-Za-z0-9+/]+={0,2}$/.test(value)
  )
    throw Error('Depth model copy is unavailable or too large.');
  const text = atob(value),
    bytes = Uint8Array.from(text, c => c.charCodeAt(0));
  inspectDepthGlb(bytes);
  return bytes;
}
export function receiveDepthModel(bridge) {
  if (typeof bridge?.getDepthModelLength !== 'function') return null;
  const length = bridge.getDepthModelLength();
  if (!length) return null;
  try {
    if (!Number.isInteger(length) || length < 32 || length > MAX_DEPTH_TEXT)
      throw Error('Depth model handoff is too large.');
    const chunks = [];
    let offset = 0;
    while (offset < length) {
      const value = bridge.readDepthModelChunk(offset);
      if (
        typeof value !== 'string' ||
        !value.length ||
        value.length > 32768 ||
        value.length > length - offset
      )
        throw Error('Depth model handoff is incomplete.');
      chunks.push(value);
      offset += value.length;
    }
    const encoded = chunks.join('');
    decodedDepthGlb(encoded);
    return encoded;
  } finally {
    bridge.releaseDepthModel?.();
  }
}
export function disposeImportedModel(root) {
  const geometries = new Set(),
    materials = new Set(),
    textures = new Set(),
    images = new Set();
  root.traverse(object => {
    if (object.geometry) geometries.add(object.geometry);
    for (const material of Array.isArray(object.material)
      ? object.material
      : object.material
      ? [object.material]
      : []) {
      materials.add(material);
      for (const value of Object.values(material))
        if (value?.isTexture) textures.add(value);
    }
  });
  for (const geometry of geometries) geometry.dispose();
  for (const material of materials) material.dispose();
  for (const texture of textures) {
    if (texture.image) images.add(texture.image);
    texture.dispose();
  }
  for (const image of images) image.close?.();
}
export async function loadDepthModel(encoded) {
  const bytes = decodedDepthGlb(encoded),
    buffer = bytes.buffer.slice(
      bytes.byteOffset,
      bytes.byteOffset + bytes.byteLength,
    );
  const expectedImages = inspectDepthGlb(bytes).images?.length || 0;
  const gltf = await new GLTFLoader().parseAsync(buffer, '');
  const root = gltf.scene;
  try {
    let meshes = 0,
      vertices = 0,
      mapped = 0;
    root.traverse(object => {
      if (object.isMesh) {
        meshes++;
        vertices += object.geometry.attributes.position?.count || 0;
        const values = object.geometry.attributes.position?.array;
        if (
          !values ||
          values.some(
            value => !Number.isFinite(value) || Math.abs(value) > 10000,
          )
        )
          throw Error('Depth vertices are invalid.');
        for (const material of Array.isArray(object.material)
          ? object.material
          : [object.material]) {
          material.side = THREE.DoubleSide;
          if (material.map) mapped++;
          for (const value of Object.values(material))
            if (
              value?.isTexture &&
              (value.image?.width > 1024 || value.image?.height > 1024)
            )
              throw Error('Depth textures exceed 1024 pixels.');
        }
      }
    });
    if (expectedImages && !mapped)
      throw Error('The embedded depth texture could not be decoded.');
    if (!meshes || meshes > 4 || vertices > 20000)
      throw Error('Depth model exceeds its mobile geometry budget.');
    const bounds = new THREE.Box3().setFromObject(root),
      size = bounds.getSize(new THREE.Vector3());
    if (
      !Number.isFinite(size.length()) ||
      size.x <= 0 ||
      size.y <= 0 ||
      size.length() > 10000
    )
      throw Error('Depth model bounds are invalid.');
    return root;
  } catch (error) {
    disposeImportedModel(root);
    throw error;
  }
}
