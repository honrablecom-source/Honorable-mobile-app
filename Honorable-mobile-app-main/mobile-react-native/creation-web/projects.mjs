import { validateProjectGraph } from './merge-project.mjs';
import { decodeDraft, encodeDraft, MAX_DRAFT_BYTES } from './scene.mjs';
export const PROJECT_KEY = 'noble-drawing-projects-v1';
export const MAX_PROJECTS = 12, MAX_SHELF_BYTES = 2 * 1024 * 1024;
export const MAX_PORTABLE_BYTES = MAX_DRAFT_BYTES * 2 + 4096;
const bytes = value => new TextEncoder().encode(value).length;
function validName(name) {
  if (typeof name !== 'string' || !name.trim() || name.trim().length > 40 || /[\u0000-\u001f\u007f]/.test(name)) throw Error('Use a project name of 1–40 characters.');
  return name.trim();
}
export function readProjects(raw) {
  if (!raw) return [];
  if (bytes(raw) > MAX_SHELF_BYTES) throw Error('Project shelf is too large.');
  const parsed = JSON.parse(raw);
  if (parsed.version !== 1 || !Array.isArray(parsed.projects) || parsed.projects.length > MAX_PROJECTS) throw Error('Unsupported project shelf.');
  const names = new Set();
  return parsed.projects.map(project => {
    const name = validName(project.name);
    if (names.has(name.toLowerCase())) throw Error('Duplicate project names.');
    names.add(name.toLowerCase());
    const scene = decodeDraft(project.scene);
    if (!scene) throw Error('Project scene is missing.');
    return { name, scene: encodeDraft(scene.elements, scene.appState, scene.files) };
  });
}
export function writeProjects(projects) {
  const raw = JSON.stringify({ version: 1, projects });
  readProjects(raw);
  return raw;
}
export function saveProject(projects, name, elements, state, files) {
  name = validName(name);
  const next = projects.filter(project => project.name.toLowerCase() !== name.toLowerCase());
  next.push({ name, scene: encodeDraft(elements, state, files) });
  writeProjects(next);
  return next;
}
/** Explicit local transfer includes referenced photo copies, never account state, source names or original URIs. */
export function exportProject(name, elements, state, files) {
  const raw = JSON.stringify({ format: 'noble-drawing', version: 1, name: validName(name), scene: encodeDraft(elements, state, files) });
  if (bytes(raw) > MAX_PORTABLE_BYTES) throw Error('Project export is too large.');
  return raw;
}
export function importProject(raw) {
  if (typeof raw !== 'string' || bytes(raw) > MAX_PORTABLE_BYTES) throw Error('Project import is too large.');
  const value = JSON.parse(raw);
  if (value.format !== 'noble-drawing' || value.version !== 1) throw Error('Unsupported Noble project.');
  const scene = decodeDraft(value.scene);
  if (!scene) throw Error('Project scene is missing.');
  validateProjectGraph(scene.elements);
  return { name: validName(value.name), ...scene };
}

function destination(projects, sourceName, nextName) {
  const source = projects.find(project => project.name === sourceName);
  if (!source) throw Error('Saved project is unavailable.');
  const name = validName(nextName);
  if (projects.some(project => project.name.toLowerCase() === name.toLowerCase())) throw Error('Choose an unused project name.');
  return { source, name };
}
export function renameProject(projects, sourceName, nextName) {
  const { source, name } = destination(projects, sourceName, nextName);
  const next = projects.map(project => project === source ? { ...project, name } : project);
  writeProjects(next);
  return next;
}
export function duplicateProject(projects, sourceName, nextName) {
  const { source, name } = destination(projects, sourceName, nextName);
  const next = [...projects, { name, scene: source.scene }];
  writeProjects(next);
  return next;
}

/** Only operate on the exact saved entry presented to the user; unrelated entries may change. */
export function requireSavedVersion(projects, expected) {
  const current = expected && projects.find(project => project.name === expected.name);
  if (!current || typeof expected.scene !== 'string' || current.scene !== expected.scene) throw Error('Saved work changed. Reopen Projects before changing this version.');
  return current;
}
