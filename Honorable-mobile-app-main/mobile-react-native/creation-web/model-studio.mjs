import { decodedDepthGlb, disposeImportedModel } from './model-import.mjs';
import * as THREE from 'three';
import { GLTFExporter } from 'three/addons/exporters/GLTFExporter.js';
export const MODEL_KEY = 'noble-model-studio-v1';
export const MAX_GLB = 8 * 1024 * 1024;
export const defaultModel = () => ({
  version: 1,
  shape: 'box',
  width: 1.5,
  height: 1.5,
  depth: 1.5,
  color: '#999999',
  roughness: 0.6,
  metalness: 0,
  duration: 4,
  animate: false,
  texture: null,
  asset: null,
});
const fields = Object.keys(defaultModel()).sort().join(',');
export function checkedModel(value) {
  if (
    !value ||
    typeof value !== 'object' ||
    Array.isArray(value) ||
    Object.keys(value).sort().join(',') !== fields ||
    value.version !== 1
  )
    throw Error('Unsupported model project.');
  if (
    !['box', 'sphere', 'cylinder', 'cone', 'torus', 'depth'].includes(
      value.shape,
    )
  )
    throw Error('Choose a supported model shape.');
  for (const name of ['width', 'height', 'depth'])
    if (!Number.isFinite(value[name]) || value[name] < 0.1 || value[name] > 5)
      throw Error('Use model dimensions from 0.1 to 5.');
  for (const name of ['roughness', 'metalness'])
    if (!Number.isFinite(value[name]) || value[name] < 0 || value[name] > 1)
      throw Error('Use material values from 0 to 1.');
  if (
    !/^#[0-9a-f]{6}$/i.test(value.color) ||
    typeof value.animate !== 'boolean' ||
    !Number.isFinite(value.duration) ||
    value.duration < 1 ||
    value.duration > 12
  )
    throw Error('Invalid model material or animation.');
  if (
    value.texture !== null &&
    (typeof value.texture !== 'string' ||
      value.texture.length > 2 * 1024 * 1024 ||
      !/^data:image\/(?:png|jpeg);base64,[A-Za-z0-9+/]+=*$/.test(value.texture))
  )
    throw Error('Use a locally prepared photo texture.');
  if (value.asset !== null) decodedDepthGlb(value.asset);
  if (value.shape === 'depth' && !value.asset)
    throw Error('A local depth model is required.');
  return { ...value };
}
export function readModel(raw) {
  if (typeof raw !== 'string' || raw.length > 7 * 1024 * 1024)
    throw Error('Model project is too large.');
  return checkedModel(JSON.parse(raw));
}
export function modelGeometry(shape) {
  switch (shape) {
    case 'box':
      return new THREE.BoxGeometry(1, 1, 1);
    case 'sphere':
      return new THREE.SphereGeometry(0.5, 32, 24);
    case 'cylinder':
      return new THREE.CylinderGeometry(0.5, 0.5, 1, 32);
    case 'cone':
      return new THREE.ConeGeometry(0.5, 1, 32);
    case 'torus':
      return new THREE.TorusGeometry(0.35, 0.15, 16, 48);
    default:
      throw Error('Unsupported model geometry.');
  }
}
export function buildModel(value, texture = null) {
  const spec = checkedModel(value),
    root = new THREE.Group();
  root.name = 'NobleModel';
  const geometry = modelGeometry(spec.shape);
  const material = new THREE.MeshStandardMaterial({
    color: spec.color,
    roughness: spec.roughness,
    metalness: spec.metalness,
    map: texture,
  });
  const mesh = new THREE.Mesh(geometry, material);
  mesh.name = 'Model';
  mesh.scale.set(spec.width, spec.height, spec.depth);
  root.add(mesh);
  return modelWithAnimation(spec, root, () => {
    geometry.dispose();
    material.dispose();
  });
}
function modelWithAnimation(spec, root, dispose) {
  const times = [
    0,
    spec.duration / 4,
    spec.duration / 2,
    (spec.duration * 3) / 4,
    spec.duration,
  ];
  const values = times.flatMap(t =>
    new THREE.Quaternion()
      .setFromAxisAngle(
        new THREE.Vector3(0, 1, 0),
        spec.shape === 'depth'
          ? Math.sin((2 * Math.PI * t) / spec.duration) * 0.2
          : (2 * Math.PI * t) / spec.duration,
      )
      .toArray(),
  );
  const clips = spec.animate
    ? [
        new THREE.AnimationClip(
          spec.shape === 'depth' ? 'Depth parallax' : 'Turntable',
          spec.duration,
          [
            new THREE.QuaternionKeyframeTrack(
              'NobleModel.quaternion',
              times,
              values,
            ),
          ],
        ),
      ]
    : [];
  return {
    root,
    clips,
    dispose,
  };
}
export function buildDepthModel(value, source, texture = null) {
  const spec = checkedModel(value),
    root = new THREE.Group();
  root.name = 'NobleModel';
  const bounds = new THREE.Box3().setFromObject(source),
    center = bounds.getCenter(new THREE.Vector3()),
    size = bounds.getSize(new THREE.Vector3());
  const centerer = new THREE.Group();
  centerer.add(source);
  centerer.position.copy(center).multiplyScalar(-1);
  const normalized = new THREE.Group();
  normalized.add(centerer);
  normalized.scale.setScalar(1 / Math.max(size.x, size.y));
  const holder = new THREE.Group();
  holder.name = 'DepthSurface';
  holder.scale.set(spec.width, spec.height, spec.depth);
  holder.add(normalized);
  root.add(holder);
  const color = new THREE.Color(spec.color),
    replacedTextures = new Set();
  source.traverse(object => {
    if (!object.isMesh) return;
    for (const material of Array.isArray(object.material)
      ? object.material
      : [object.material]) {
      material.color?.copy(color);
      if ('roughness' in material) material.roughness = spec.roughness;
      if ('metalness' in material) material.metalness = spec.metalness;
      if (texture) {
        if (material.map && material.map !== texture)
          replacedTextures.add(material.map);
        material.map = texture;
      }
      material.needsUpdate = true;
    }
  });
  return modelWithAnimation(spec, root, () => {
    disposeImportedModel(source);
    for (const old of replacedTextures) {
      old.dispose();
      old.image?.close?.();
    }
  });
}
export async function exportModel(root, clips) {
  // Export from a neutral orientation, independent of the currently playing frame.
  const copy = root.clone(true);
  copy.quaternion.identity();
  const result = await new GLTFExporter().parseAsync(copy, {
    binary: true,
    animations: clips,
    onlyVisible: true,
    maxTextureSize: 1024,
  });
  const bytes = new Uint8Array(result);
  checkGlb(bytes);
  return bytes;
}
export function checkGlb(bytes) {
  if (
    !(bytes instanceof Uint8Array) ||
    bytes.length < 20 ||
    bytes.length > MAX_GLB
  )
    throw Error('Model export is empty or exceeds 8 MiB.');
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (
    view.getUint32(0, true) !== 0x46546c67 ||
    view.getUint32(4, true) !== 2 ||
    view.getUint32(8, true) !== bytes.length
  )
    throw Error('Model export is incomplete.');
  return bytes;
}
export function sendModel(bridge, id, bytes) {
  checkGlb(bytes);
  if (!Number.isInteger(id) || id < 1 || id > 1_000_000_000)
    throw Error('Model request unavailable.');
  for (const name of [
    'beginModelShare',
    'appendModelShareChunk',
    'finishModelShare',
    'releaseModelShare',
  ])
    if (typeof bridge?.[name] !== 'function')
      throw Error('Update Noble to share 3D models.');
  let began = false;
  try {
    if (bridge.beginModelShare(id, bytes.length) !== true)
      throw Error('Another operation is running. Try sharing again.');
    began = true;
    for (let i = 0; i < bytes.length; i += 24576) {
      const value = btoa(String.fromCharCode(...bytes.subarray(i, i + 24576)));
      if (bridge.appendModelShareChunk(id, value) !== true)
        throw Error('Model transfer stopped. Try again while active.');
    }
    bridge.finishModelShare(id);
  } catch (error) {
    if (began) bridge.releaseModelShare(id);
    throw error;
  }
}
