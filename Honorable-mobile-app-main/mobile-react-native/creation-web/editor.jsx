import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Excalidraw, MainMenu, exportToBlob, exportToSvg, newElementWith, restoreElements, convertToExcalidrawElements, CaptureUpdateAction, getCommonBounds } from '@excalidraw/excalidraw';
import '@excalidraw/excalidraw/index.css';
import { decodeDraft, encodeDraft, MAX_ELEMENTS } from './scene.mjs';
import { arrangeObjects, layerPatch, reorderObject, repeatGrid, repeatRing, captureComponent, insertComponent, compositionColors, replaceCompositionColor, blendShapes } from './composition.mjs';
import { PROJECT_KEY, readProjects, writeProjects, saveProject, exportProject, importProject, renameProject, duplicateProject, requireSavedVersion, MAX_PORTABLE_BYTES } from './projects.mjs';
import { filesForElements, mergePhotoFiles, photoBytes } from './image-assets.mjs';
import { prepareLocalPhoto, prepareNativePhoto, verifyPhotoCopies } from './photo-import.mjs';
import { chartPlan, calendarPlan, storyboardPlan } from './layouts.mjs';
import { palettePlan } from './palette.mjs';
import { qrPlan } from './qr.mjs';
import { barcodePlan } from './barcode.mjs';
import { tablePlan } from './table.mjs';
import { starterPlan } from './starters.mjs';
import { StarterPreview } from './StarterPreview.jsx';
import { VectorImportTool } from './VectorImportTool.jsx';
import { ShapeBooleanTool } from './ShapeBooleanTool.jsx';
import { captureBooleanSource } from './shape-boolean-source.mjs';
import { printArtworkSnapshot, sendPrintArtwork, MAX_PRINT_BYTES } from './print-artwork.mjs';
import { sendSvgArtwork } from './svg-share.mjs';
import { projectFilename, decodeProjectFile, sendProjectFile, receiveProjectFile, projectFileArtwork, recoveryFile, sendRecoveryFile } from './project-files.mjs';
import { findArtwork } from './object-finder.mjs';
import { TextContrastReview } from './TextContrastReview.jsx';
import { mergeProjectElements, validateProjectGraph } from './merge-project.mjs';
import { photoSheet } from './photo-sheet.mjs';
import { replaceablePhoto, replacementPatch } from './photo-replace.mjs';
import { framingPatch } from './photo-framing.mjs';
import { PhotoFramingPreview } from './PhotoFramingPreview.jsx';
import { PhotoRectifyTool } from './PhotoRectifyTool.jsx';
import { WordCloudTool } from './WordCloudTool.jsx';
import { PhotoWandTool } from './PhotoWandTool.jsx';
import { PhotoOutlineCutoutTool } from './PhotoOutlineCutoutTool.jsx';
import { PhotoMosaicTool } from './PhotoMosaicTool.jsx';
import { PersonalizeTool } from './PersonalizeTool.jsx';
import { dependencyPlan } from './dependencies.mjs';
import './honorable.css';

const KEY = 'honorable-drawing-v1';
const COMPONENT_KEY = 'noble-drawing-components-v1';
const svgStamp = elements => JSON.stringify(elements.filter(element => !element.isDeleted).map(element => [element.id, element.version, element.versionNonce]));
const makeObjectId = () => Array.from(crypto.getRandomValues(new Uint32Array(4)), value => value.toString(16).padStart(8, '0')).join('');
function initial() {
  try {
    const data = decodeDraft(localStorage.getItem(KEY));
    if (data) validateProjectGraph(data.elements);
    return { data, error: false };
  } catch { return { data: null, error: true }; }
}
function Editor() {
  const api = useRef(null), timer = useRef(null), latest = useRef(null), portable = useRef(null), panelHeading = useRef(null);
  const photoInput = useRef(null), photoRequest = useRef(0), pendingPhoto = useRef(null), photoTarget = useRef(null), busyRef = useRef(false), mounted = useRef(true);
  const [ready, setReady] = useState(false);
  const resetPending = useRef(false);
  const vectorFileOwner = useRef(null);
  const [transparentImage, setTransparentImage] = useState(false);
  const [selectionFile, setSelectionFile] = useState(false);
  const [projectTool, setProjectTool] = useState('saved');
  const projectTaskHeading = useRef(null), previousProjectTool = useRef('saved');
  const projectViews = { finish: 'Finish options', saved: 'Saved designs', files: 'Project files', images: 'Image copies', print: 'Print or PDF', parts: 'Reusable parts', vectors: 'Vector artwork', advanced: 'Advanced transfer' };
  const printRequest = useRef(0), pendingPrint = useRef(null), svgRequest = useRef(0), pendingSvg = useRef(null), pendingImage = useRef(null), outputSelection = useRef(null);
  const projectFileInput = useRef(null), projectFileRequest = useRef(0), pendingProjectFile = useRef(null), projectJson = useRef('');
  const [preparedProjectFile, setPreparedProjectFile] = useState(null);
  const [pendingOpen, setPendingOpen] = useState(null);
  const openReviewHeading = useRef(null);
  const bindEditor = useCallback(value => { api.current = value; setReady(Boolean(value) && !value.getAppState().isLoading); }, []);
  const [{ data: restored, error: restoreError }] = useState(initial);
  const preservedDraft = useRef(restoreError);
  const [hasArtwork, setHasArtwork] = useState(Boolean(restored?.elements?.some(element => !element.isDeleted)));
  const [startDismissed, setStartDismissed] = useState(false);
  const canvasRegion = useRef(null), finishTrigger = useRef(null), finishOpened = useRef(false);
  const [status, setStatus] = useState(restoreError ? 'Saved draft could not be read. It is preserved. Start a new drawing to replace it.' : restored ? 'Local drawing restored.' : 'Draw, add shapes or write text.');
  const [busy, setBusy] = useState(false), [panel, setPanel] = useState(null);
  const [outputUnlocking, setOutputUnlocking] = useState(false);
  useLayoutEffect(() => {
    if (!outputUnlocking) return;
    const captured = outputSelection.current;
    let active = true, frame, attempts = 0;
    const restore = () => {
      if (!active || !mounted.current) return;
      // Excalidraw clears selection while entering/leaving view mode. Wait for
      // that transition while controls remain locked, then restore once.
      if (captured && api.current === captured.editor && captured.editor.getAppState().viewModeEnabled && attempts++ < 60) {
        frame = requestAnimationFrame(restore); return;
      }
      outputSelection.current = null;
      if (captured && captured.snapshot.current(api.current)) {
        try { captured.editor.updateScene({ appState: captured.selection, captureUpdate: CaptureUpdateAction.NEVER }); }
        catch { /* Keep the drawing; the user can choose the selection again. */ }
      }
      setOutputUnlocking(false); finishBusy();
    };
    frame = requestAnimationFrame(restore);
    return () => { active = false; cancelAnimationFrame(frame); };
  }, [outputUnlocking]);

  useEffect(() => { if (pendingOpen && panel === 'projects') openReviewHeading.current?.focus(); }, [pendingOpen, panel]);
  const [framingTarget, setFramingTarget] = useState(null);
  const [rectifyTarget, setRectifyTarget] = useState(null);
  const [wandTarget,setWandTarget]=useState(null);
  const [cutoutTarget,setCutoutTarget]=useState(null);
  const cutoutOwner=useRef(null);
  const [mosaicTarget,setMosaicTarget]=useState(null);
  useEffect(() => { if (restoreError) { setPanel('projects'); setProjectTool('files'); } }, [restoreError]);
  useEffect(() => {
    const changed = previousProjectTool.current !== projectTool; previousProjectTool.current = projectTool;
    if (changed && panel === 'projects') projectTaskHeading.current?.focus();
  }, [projectTool, panel]);
  useEffect(() => { if (panel) panelHeading.current?.focus({ preventScroll: true }); }, [panel]);
  useEffect(() => { if (panel === 'projects' && projectTool === 'finish') projectTaskHeading.current?.focus(); }, [panel, projectTool]);
  const [objects, setObjects] = useState([]), [projects, setProjects] = useState([]);
  const [objectQuery, setObjectQuery] = useState(''), [objectType, setObjectType] = useState('all');
  const [contrastOpen, setContrastOpen] = useState(false), [contrastBackground, setContrastBackground] = useState('#ffffff');
  const [name, setName] = useState('Untitled composition'), [json, setJson] = useState('');
  projectJson.current = json;
  const [svg, setSvg] = useState('');
  const svgSource = useRef(null);
  const [pendingSave, setPendingSave] = useState(null);
  const saveReviewHeading = useRef(null);
  useEffect(() => { if (pendingSave && panel === 'projects') saveReviewHeading.current?.focus(); }, [pendingSave, panel]);
  const [layoutTitle, setLayoutTitle] = useState('My chart'), [chartRows, setChartRows] = useState('First | 12\nSecond | 24\nThird | 18');
  const [dependencyTitle, setDependencyTitle] = useState('My workflow'), [dependencyRows, setDependencyRows] = useState('Idea -> Draft\nDraft -> Review\nReview -> Publish');
  const [paletteTitle, setPaletteTitle] = useState('Artwork color key');
  const [layoutTool, setLayoutTool] = useState('chart');
  const [starterStyle, setStarterStyle] = useState('quote'), [starterHeadline, setStarterHeadline] = useState('Make room for your next idea');
  const [starterDetails, setStarterDetails] = useState('Start small. Make something that feels like you.'), [starterSignoff, setStarterSignoff] = useState('Made with Noble');
  const [tableTitle, setTableTitle] = useState('Compare options'), [tableRows, setTableRows] = useState('Option | Price | Notes\nBasic | 10 | A simple start\nPlus | 20 | More room');
  const [arrangeTool, setArrangeTool] = useState('align');
  const [qrText, setQrText] = useState(''), [qrCaption, setQrCaption] = useState('QR code');
  const [barcodeText, setBarcodeText] = useState(''), [barcodeCaption, setBarcodeCaption] = useState('Item label');
  const [calendarMonth, setCalendarMonth] = useState('2026-11'), [mondayFirst, setMondayFirst] = useState(true);
  const [storyTitle, setStoryTitle] = useState('My storyboard'), [storyBriefs, setStoryBriefs] = useState('Opening scene\nA closer look\nClosing scene');
  const [gridColumns, setGridColumns] = useState('3'), [gridRows, setGridRows] = useState('2'), [gridGap, setGridGap] = useState('20');
  const [blendSteps, setBlendSteps] = useState('5');
  const [sheetAll, setSheetAll] = useState(true), [sheetColumns, setSheetColumns] = useState('2'), [sheetCell, setSheetCell] = useState('240'), [sheetGap, setSheetGap] = useState('20');
  const [ringCount, setRingCount] = useState('6'), [ringRadius, setRingRadius] = useState('160');
  const [deletedProject, setDeletedProject] = useState(null);
  const [managed, setManaged] = useState(null), [nextName, setNextName] = useState('');
  const [projectUndo, setProjectUndo] = useState(null);
  const [components, setComponents] = useState([]), [componentName, setComponentName] = useState('My component');
  const [componentUndo, setComponentUndo] = useState(null);
  const [sourceColor, setSourceColor] = useState('#111111'), [targetColor, setTargetColor] = useState('#333333'), [includeLocked, setIncludeLocked] = useState(false);
  const error = failure => setStatus(failure.message || 'This action could not complete. Your drawing is retained.');
  const beginBusy = () => {
    if (busyRef.current || !api.current) return false;
    busyRef.current = true; window.honorableDrawingBusy = true; setBusy(true); return true;
  };
  const finishBusy = () => { if (resetPending.current) return; photoTarget.current = null; busyRef.current = false; window.honorableDrawingBusy = false; if (mounted.current) setBusy(false); };
  const completePhotoAttempt = attempt => {
    if (pendingPhoto.current !== attempt) return false;
    pendingPhoto.current = null;
    if (attempt.guarded && attempt.admitted) { try { window.HonorableDrawing?.releasePhotoImport?.(attempt.id); } catch { /* Native lifecycle cleanup also releases ownership. */ } }
    finishBusy(); return true;
  };
  const finishOutput = () => { if (outputSelection.current) setOutputUnlocking(true); else finishBusy(); };
  const captureOutputSelection = (editor, snapshot) => {
    const state = editor.getAppState();
    outputSelection.current = { editor, snapshot, selection: { selectedElementIds: { ...state.selectedElementIds }, selectedGroupIds: { ...state.selectedGroupIds }, editingGroupId: state.editingGroupId || null } };
  };
  const save = (announce = true) => {
    if (resetPending.current) return false;
    try {
      if (!latest.current) return true;
      if (preservedDraft.current) throw Error(restoreError ? 'The saved draft is preserved but could not be safely opened. It has not been replaced.' : 'The complete drawing is preserved in your saved draft. Reopen the drawing to restore it.');
      localStorage.setItem(KEY, encodeDraft(...latest.current));
      if (announce) setStatus('Drawing saved on this device.');
      return true;
    } catch (failure) { error(failure); return false; }
  };
  // Native back/lifecycle hooks always read current refs. Clear delayed work on teardown.
  window.honorableDrawingBusy = busy || resetPending.current;
  window.honorableSaveDraft = () => save(false);
  window.honorableSvgShareResult = result => {
    const pending = pendingSvg.current;
    if (!mounted.current || !pending || result?.requestId !== pending.id) return;
    pendingSvg.current = null; finishOutput();
    setStatus(result.error || (result.opened === true ? 'SVG share sheet opened. Saving or sending depends on the app you choose.' : 'The SVG share sheet did not open. Try again.'));
  };
  window.honorablePrintResult = result => {
    const pending = pendingPrint.current;
    if (!mounted.current || !pending || result?.requestId !== pending.id || (!result.error && result.closed !== true)) return;
    pendingPrint.current = null; finishOutput();
    setStatus(result.error || 'Print dialog closed. Check Android print status or your saved PDF; saving or delivery is not confirmed here.');
  };
  window.honorableExportResult = message => {
    if (resetPending.current || !pendingImage.current) return;
    pendingImage.current = null; finishOutput(); setStatus(message);
  };
  window.honorableNewDrawing = () => {
    if (!mounted.current || !api.current || busyRef.current || resetPending.current) return false;
    try {
      // Remove first: storage failure must leave the existing editor and draft usable.
      localStorage.removeItem(KEY);
      resetPending.current = true;
      clearTimeout(timer.current);
      latest.current = null;
      busyRef.current = true; window.honorableDrawingBusy = true; setBusy(true);
      setStatus('Starting a new drawing…');
      return true;
    } catch { setStatus('Could not start a new drawing. Your current work was kept. Try again.'); return false; }
  };
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; pendingPrint.current = null; pendingSvg.current = null; pendingImage.current = null; pendingPhoto.current = null; photoRequest.current++; clearTimeout(timer.current); }; }, []);
  const scene = () => [api.current.getSceneElements(), api.current.getAppState(), api.current.getFiles()];
  const commit = elements => {
    if (busyRef.current) throw Error('Wait for the current image operation to finish.');
    encodeDraft(elements, api.current.getAppState(), api.current.getFiles());
    api.current.updateScene({ elements, captureUpdate: CaptureUpdateAction.IMMEDIATELY });
    setStatus('Composition updated. Undo restores the previous arrangement.');
  };
  const patchObjects = patches => {
    const byId = new Map(patches.map(patch => [patch.id, patch]));
    commit(api.current.getSceneElementsIncludingDeleted().map(element => {
      const patch = byId.get(element.id);
      if (!patch) return element;
      const { id, ...changes } = patch;
      return newElementWith(element, changes);
    }));
  };
  const framingCurrent = target => {
    try { return mounted.current && api.current === target.editor && encodeDraft(...scene()) === target.snapshot && api.current.getFiles()[target.photo.fileId]?.dataURL === target.dataURL; } catch { return false; }
  };
  const openFraming = id => {
    if (busyRef.current) return;
    try {
      const photo = replaceablePhoto(api.current.getSceneElements(), id), file = api.current.getFiles()[photo.fileId];
      if (!file) throw Error('The embedded photo copy is unavailable.');
      photoBytes(file.dataURL);
      setFramingTarget({ editor: api.current, photo, dataURL: file.dataURL, snapshot: encodeDraft(...scene()) }); setPanel('framing');
    } catch (failure) { error(failure); }
  };
  const openRectify = id => {
    if (busyRef.current) return;
    try {
      const photo = replaceablePhoto(api.current.getSceneElements(), id), file = api.current.getFiles()[photo.fileId];
      if (!file) throw Error('The embedded source copy is unavailable.');
      const { width, height } = photoBytes(file.dataURL);
      setRectifyTarget({ editor: api.current, photo, dataURL: file.dataURL, width, height, snapshot: encodeDraft(...scene()) });setPanel('rectify');
    } catch (failure) { error(failure); }
  };
  const openWand = (id,tracing=false) => {
    if(busyRef.current)return;
    try{const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded source copy is unavailable.');const{width,height}=photoBytes(file.dataURL);setWandTarget({editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene())});setPanel(tracing?'trace':'wand');}catch(failure){error(failure);}
  };
  const openCutout = id => {
    if(busyRef.current)return;
    try {
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];
      if(!file)throw Error('The embedded source copy is unavailable.');
      const {width,height}=photoBytes(file.dataURL);
      if(cutoutOwner.current)cutoutOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};
      cutoutOwner.current=target;setCutoutTarget(target);setPanel('cutout');
    }catch(failure){error(failure);}
  };
  const openMosaic = id => {
    if (busyRef.current) return;
    try {
      const photo = replaceablePhoto(api.current.getSceneElements(), id), file = api.current.getFiles()[photo.fileId];
      if (!file) throw Error('The embedded source copy is unavailable.');
      photoBytes(file.dataURL);
      setMosaicTarget({ editor: api.current, photo, dataURL: file.dataURL, snapshot: encodeDraft(...scene()) });
      setPanel('mosaic');
    } catch (failure) { error(failure); }
  };
  const captureVectorSource = () => {
    const editor = api.current;
    if (!mounted.current || !editor || busyRef.current || resetPending.current || preservedDraft.current) throw Error('Wait for the drawing to be ready.');
    const raw = encodeDraft(editor.getSceneElements(), {}, editor.getFiles());
    return { existingCount: editor.getSceneElements().length, current(candidate) { try { return mounted.current && !resetPending.current && !preservedDraft.current && candidate === editor && encodeDraft(candidate.getSceneElements(), {}, candidate.getFiles()) === raw; } catch { return false; } } };
  };
  const beginVectorFile = () => {
    if (!beginBusy()) return null;
    const owner = {}; vectorFileOwner.current = owner;
    return () => { if (vectorFileOwner.current === owner) { vectorFileOwner.current = null; finishBusy(); } };
  };
  const insertVectorArtwork = (result, source) => {
    if (busyRef.current || !source.current(api.current)) throw Error('The drawing changed. Preview the SVG again.');
    const editor = api.current, elements = editor.getSceneElementsIncludingDeleted(), active = elements.filter(element => !element.isDeleted);
    const visible = active.filter(element => element.opacity !== 0 && !element.customData?.nobleHidden);
    const bounds = visible.length ? getCommonBounds(visible) : [0, 0, -48, 0], group = makeObjectId();
    const created = convertToExcalidrawElements(result.plan.map(element => ({ ...element, x: element.x + bounds[2] + 48 - result.bounds[0], y: element.y + bounds[1] - result.bounds[1] }))).map(element => ({ ...element, groupIds: [group] }));
    const merged = [...elements, ...created]; encodeDraft(merged, editor.getAppState(), editor.getFiles());
    if (!source.current(api.current)) throw Error('The drawing changed. Preview the SVG again.');
    commit(merged.map(element => newElementWith(element, {}, true)));
    editor.updateScene({ appState: { selectedElementIds: Object.fromEntries(created.map(element => [element.id, true])), selectedGroupIds: { [group]: true }, editingGroupId: null }, captureUpdate: CaptureUpdateAction.NEVER });
    const ids = new Set(created.map(element => element.id)); setPanel(null); setStatus('Editable SVG artwork inserted beside your drawing. Originals kept; Undo removes the new group.');
    requestAnimationFrame(() => requestAnimationFrame(() => { if (!mounted.current || api.current !== editor || resetPending.current) return; const visible = editor.getSceneElements().filter(element => ids.has(element.id)); if (visible.length === created.length) editor.scrollToContent(visible, { fitToViewport: true, viewportZoomFactor: .85, canvasOffsets: { top: 80, bottom: 100, left: 24, right: 48 } }); }));
  };
  const insertBoolean = (result, source) => {
    if (busyRef.current || !source.current(api.current)) throw Error('The drawing changed. Preview the selected shapes again.');
    const editor = api.current, group = makeObjectId();
    const created = convertToExcalidrawElements(result.plan).map(element => ({ ...element, groupIds: [group] }));
    const merged = [...editor.getSceneElementsIncludingDeleted(), ...created];
    encodeDraft(merged, editor.getAppState(), editor.getFiles());
    if (!source.current(api.current)) throw Error('The drawing changed. Preview again.');
    commit(merged.map(element => newElementWith(element, {}, true)));
    editor.updateScene({ appState: { selectedElementIds: Object.fromEntries(created.map(element => [element.id, true])), selectedGroupIds: { [group]: true }, editingGroupId: null }, captureUpdate: CaptureUpdateAction.NEVER });
    const insertedIds = new Set(created.map(element => element.id));
    requestAnimationFrame(() => requestAnimationFrame(() => {
      if (!mounted.current || api.current !== editor || resetPending.current) return;
      const visible = editor.getSceneElements().filter(element => insertedIds.has(element.id));
      if (visible.length === created.length) editor.scrollToContent(visible, { fitToViewport: true, viewportZoomFactor: .85, canvasOffsets: { top: 80, bottom: 100, left: 24, right: 48 } });
    }));
    setPanel(null); setStatus(`Combined shape inserted as ${created.length} editable paths. Originals kept; Undo removes the result.`);
  };
  const insertMosaic = proposal => {
    if (!mosaicTarget || !framingCurrent(mosaicTarget) || busyRef.current) throw Error('The source or drawing changed. Open Photo mosaic again.');
    const [, top, right] = getCommonBounds([mosaicTarget.photo]), group = makeObjectId();
    const created = convertToExcalidrawElements(proposal.plan.map(shape => ({ ...shape, x: shape.x + right + 32, y: shape.y + top })))
      .map(element => ({ ...element, groupIds: [group] }));
    commit([...api.current.getSceneElementsIncludingDeleted(), ...created].map(element => newElementWith(element, {}, true)));
    api.current.updateScene({ appState: { selectedElementIds: Object.fromEntries(created.map(element => [element.id, true])), selectedGroupIds: { [group]: true }, editingGroupId: null }, captureUpdate: CaptureUpdateAction.NEVER });
    setPanel(null);
    const editor = api.current;
    requestAnimationFrame(() => requestAnimationFrame(() => { if (mounted.current && api.current === editor) editor.scrollToContent(created, { fitToViewport: true, viewportZoomFactor: .85, canvasOffsets: { top: 80, bottom: 80, left: 8, right: 40 } }); }));
    setStatus('Editable mosaic inserted. Ungroup to edit each triangle; Undo removes the mosaic. Original photo preserved.');
  };
  const capturePersonalization = () => {
    if (busyRef.current || preservedDraft.current) throw Error('Finish or reopen the current drawing before making copies.');
    const editor = api.current, snapshot = encodeDraft(...scene());
    const selection = () => JSON.stringify(Object.keys(editor.getAppState().selectedElementIds || {}).filter(id => editor.getAppState().selectedElementIds[id]).sort());
    const selected = selection(), template = captureComponent(editor.getSceneElements(), editor.getAppState().selectedElementIds, getCommonBounds);
    return {
      editor, template, elements: editor.getSceneElementsIncludingDeleted(), files: editor.getFiles(), makeId: makeObjectId,
      isCurrent: () => mounted.current && api.current === editor && !busyRef.current && !preservedDraft.current && selection() === selected && encodeDraft(...scene()) === snapshot,
      validate: elements => encodeDraft(elements, editor.getAppState(), editor.getFiles()),
    };
  };
  const insertPersonalization = proposal => {
    if (!proposal.source.isCurrent()) throw Error('The selection or drawing changed. Preview the copies again.');
    const editor = proposal.source.editor, ids = new Set(proposal.insertedIds);
    const elements = proposal.elements.map(element => ids.has(element.id) ? newElementWith(element, {}, true) : element);
    publishPhotoScene(editor, elements, { ...editor.getAppState(), selectedElementIds: Object.fromEntries(proposal.insertedIds.map(id => [id, true])), selectedGroupIds: Object.fromEntries(proposal.groups.map(id => [id, true])), editingGroupId: null }, editor.getFiles());
    setPanel(null);
    const inserted = elements.filter(element => ids.has(element.id));
    requestAnimationFrame(() => requestAnimationFrame(() => { if (mounted.current && api.current === editor) editor.scrollToContent(inserted, { fitToViewport: true, viewportZoomFactor: .85, canvasOffsets: { top: 80, bottom: 80, left: 8, right: 40 } }); }));
    setStatus(`${proposal.copyCount} personalized copies inserted. Original kept; Undo removes this batch.`);
  };
  const insertTrace=proposal=>{
    if(!wandTarget||!framingCurrent(wandTarget)||busyRef.current)throw Error('The source or drawing changed. Preview again.');
    const[,top,right]=getCommonBounds([wandTarget.photo]),group=makeObjectId();
    const created=convertToExcalidrawElements(proposal.plan.map(shape=>({...shape,x:shape.x+right+32,y:shape.y+top}))).map(element=>({...element,groupIds:[group]}));
    commit([...api.current.getSceneElementsIncludingDeleted(),...created].map(element=>newElementWith(element,{},true)));
    api.current.updateScene({appState:{selectedElementIds:Object.fromEntries(created.map(element=>[element.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null},captureUpdate:CaptureUpdateAction.NEVER});setPanel(null);
    requestAnimationFrame(()=>requestAnimationFrame(()=>api.current?.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:1,canvasOffsets:{top:80,bottom:80,left:8,right:40}})));
    setStatus('Editable outlines inserted. Ungroup to edit each path; Undo removes the trace.');
  };
  const insertCutout=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!cutoutTarget?.valid||!framingCurrent(cutoutTarget))throw Error('The source or drawing changed. Open Outline cutout again.');};
    current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([cutoutTarget.photo]);photoTarget.current=null;
    const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy};
    const inserted=await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership);
    if(!inserted)throw Error('The cutout was not inserted. Your original drawing is kept; check the source and available storage.');
  };
  const insertWand=async proposal=>{
    if(!wandTarget||!framingCurrent(wandTarget)||busyRef.current)throw Error('The source or drawing changed. Open Remove connected color again.');
    const[,top,right]=getCommonBounds([wandTarget.photo]);photoTarget.current=null;const inserted=await addPhoto(proposal.blob,false,{x:right+32,y:top});
    if(!inserted)throw Error('The new copy could not be kept. Your drawing is unchanged; check available storage.');setPanel(null);
  };
  const insertRectified = async proposal => {
    if (!rectifyTarget || !framingCurrent(rectifyTarget) || busyRef.current) throw Error('The source or drawing changed. Open Straighten photo again.');
    const [, top, right] = getCommonBounds([rectifyTarget.photo]);
    photoTarget.current = null;
    const inserted = await addPhoto(proposal.blob, false, { x: right + 32, y: top });
    if (!inserted) throw Error('The new copy could not be kept. Your drawing is unchanged; check available storage or use a smaller selection.');
    setPanel(null);
  };
  const applyFraming = proposal => {
    if (!framingTarget || !framingCurrent(framingTarget)) throw Error('The photo or drawing changed. Open suggested framing again.');
    const patch = framingPatch(api.current.getSceneElementsIncludingDeleted(), framingTarget.photo.id, framingTarget.photo.fileId, proposal.width, proposal.height, proposal.crop);
    if (patch) patchObjects([patch]);
    setPanel(null); setStatus(patch ? 'Suggested crop applied. Undo restores the previous framing; source bytes are unchanged.' : 'This photo already uses the suggested crop.');
  };
  const closePanel = () => {
    if (busyRef.current) return;
    const trigger = (finishOpened.current && finishTrigger.current) || document.querySelector(`[data-panel-trigger="${panel}"]`);
    finishOpened.current = false;
    setPendingOpen(null); setPanel(null);
    trigger?.focus({ preventScroll: true });
  };
  const showPanel = value => {
    finishOpened.current = false;
    if (value === 'projects' && projectTool === 'finish') setProjectTool('saved');
    setPendingOpen(null);
    if (panel === value) { setPanel(null); return; }
    if (value === 'objects' || value === 'arrange') setObjects(api.current.getSceneElements());
    if (value === 'projects') {
      const unavailable = [];
      for (const [key, update, label] of [[PROJECT_KEY, setProjects, 'Saved designs'], [COMPONENT_KEY, setComponents, 'Reusable parts']]) {
        try { update(readProjects(localStorage.getItem(key))); }
        catch { update([]); unavailable.push(label); }
      }
      if (unavailable.length) setStatus(`${unavailable.join(' and ')} could not be read. Stored data is preserved. Other available project tools still work.`);
    }
    setPanel(value);
  };
  const storeProjects = (previous, next, label) => {
    const raw = writeProjects(next);
    localStorage.setItem(PROJECT_KEY, raw);
    setProjectUndo({ previous, after: raw, label });
    setProjects(next);
  };
  const publishPhotoScene = (editor, elements, state, files) => {
    if (!mounted.current || api.current !== editor) throw Error('The drawing was closed.');
    const raw = encodeDraft(elements, state, files);
    localStorage.setItem(KEY, raw); // Durable before renderer mutation; quota failure leaves the current drawing untouched.
    clearTimeout(timer.current);
    try {
      editor.addFiles(Object.values(files));
      editor.updateScene({ elements, appState: state, captureUpdate: CaptureUpdateAction.IMMEDIATELY });
      latest.current = [elements, state, files];
    } catch {
      preservedDraft.current = true;
      throw Error('The complete drawing is in your saved draft. Reopen the drawing to restore it.');
    }
  };
  const addPhoto = async (source, native = false, placement = null, ownership = null) => {
    if (!native && !beginBusy()) return;
    const editor = api.current, target = photoTarget.current;
    try {
      ownership?.current();
      if (target && encodeDraft(...scene()) !== target.scene) throw Error('The drawing changed. Choose Replace photo again.');
      if (preservedDraft.current) throw Error('Reopen the preserved drawing before adding a photo.');
      const original = encodeDraft(...scene()), previous = editor.getSceneElementsIncludingDeleted(), state = editor.getAppState();
      if (!target && previous.filter(element => !element.isDeleted).length >= MAX_ELEMENTS) throw Error('Remove an object before adding another photo.');
      const objectId = target ? target.id : makeObjectId(); let proposed;
      const accepts = file => {
        ownership?.current();
        if (!mounted.current || api.current !== editor) throw Error('The drawing was closed.');
        const info = photoBytes(file.dataURL), files = mergePhotoFiles(editor.getFiles(), { [file.id]: file });
        const factor = Math.min(1, Math.min(480, Math.max(80, state.width * 0.75)) / Math.max(info.width, info.height));
        const width = info.width * factor, height = info.height * factor;
        const [added] = restoreElements([{ id: objectId, type: 'image', x: placement ? placement.x : state.width / 2 / state.zoom.value - state.scrollX - width / 2, y: placement ? placement.y : state.height / 2 / state.zoom.value - state.scrollY - height / 2,
          width, height, angle: 0, opacity: 100, strokeColor: 'transparent', backgroundColor: 'transparent', strokeWidth: 0, roughness: 0,
          fileId: file.id, scale: [1, 1], status: 'saved', crop: null, groupIds: [], boundElements: null, link: null, locked: false }], null);
        const changes = target ? replacementPatch(previous, target.id, file.id, info.width, info.height) : null;
        const image = target ? newElementWith(replaceablePhoto(previous, target.id), changes) : added;
        const elements = (target ? previous.map(element => element.id === target.id ? image : element) : [...previous, image]).map(element => newElementWith(element, {}, true));
        try { encodeDraft(elements, state, files); }
        catch (failure) { if (failure.message === 'Drawing draft is too large.') return false; throw failure; }
        proposed = { elements, image, files, info }; return true;
      };
      setStatus('Preparing a local photo copy…');
      if (native) await prepareNativePhoto(source, accepts); else await prepareLocalPhoto(source, accepts);
      if (!mounted.current || api.current !== editor || encodeDraft(...scene()) !== original) throw Error('The drawing changed. Add the photo again.');
      ownership?.current();
      publishPhotoScene(editor, proposed.elements, { ...state, selectedElementIds: { [objectId]: true } }, proposed.files);
      setPanel(null);
      requestAnimationFrame(() => requestAnimationFrame(() => { if (mounted.current && api.current === editor) editor.scrollToContent([proposed.image], { fitToViewport: true, viewportZoomFactor: 0.85, canvasOffsets: { top: 80, bottom: 100, left: 24, right: 48 } }); }));
      setStatus(`Photo ${target ? 'replaced' : 'added'} as a local ${proposed.info.width} × ${proposed.info.height} copy. Original preserved.`);
      return true;
    } catch (failure) { if (mounted.current && (!ownership || ownership.owns())) error(failure); return false; }
    finally { if (ownership) ownership.finish(); else finishBusy(); }
  };
  const choosePhoto = (targetId = null) => {
    if (busyRef.current || !api.current) return;
    try {
      photoTarget.current = targetId ? { id: replaceablePhoto(api.current.getSceneElements(), targetId).id, scene: encodeDraft(...scene()) } : null;
    } catch (failure) { photoTarget.current = null; error(failure); return; }
    const bridge = window.HonorableDrawing;
    const guarded = ['choosePhotoOwned', 'isPhotoImportCurrent', 'releasePhotoImport'].every(name => typeof bridge?.[name] === 'function');
    if (guarded || typeof bridge?.choosePhoto === 'function') {
      if (!beginBusy()) return;
      const attempt = { id: ++photoRequest.current, phase: 'picker', guarded, admitted: false, cancelled: false };
      pendingPhoto.current = attempt;
      setStatus('Choose a photo. Its original stays unchanged.');
      try {
        if (guarded) { if (bridge.choosePhotoOwned(attempt.id) !== true) throw Error('Open the drawing and finish the current operation before choosing a photo.'); attempt.admitted = true; }
        else bridge.choosePhoto(attempt.id);
      } catch (failure) { if (completePhotoAttempt(attempt)) error(failure); }
    } else photoInput.current?.click();
  };
  window.honorablePhotoImportResult = result => {
    const attempt = pendingPhoto.current;
    if (!mounted.current || !busyRef.current || !attempt || result?.requestId !== attempt.id) return;
    if (attempt.phase === 'applying') {
      if (result.cancelled || result.error) { attempt.cancelled = true; setStatus(result.error || 'Photo selection cancelled. Drawing unchanged.'); }
      return;
    }
    if (result.cancelled || result.error || typeof result.dataURL !== 'string') {
      if (completePhotoAttempt(attempt)) setStatus(result.error || (result.cancelled ? 'Photo selection cancelled. Drawing unchanged.' : 'The photo could not be prepared.'));
      return;
    }
    attempt.phase = 'applying';
    const owns = () => mounted.current && pendingPhoto.current === attempt;
    const current = () => {
      if (!owns() || attempt.cancelled || (attempt.guarded && window.HonorableDrawing.isPhotoImportCurrent(attempt.id) !== true)) throw Error('Photo import stopped. Your drawing stays here; choose the photo again when active.');
    };
    try { current(); } catch (failure) { if (completePhotoAttempt(attempt)) error(failure); return; }
    addPhoto(result.dataURL, true, null, { owns, current, finish: () => completePhotoAttempt(attempt) });
  };
  const undoProjectChange = () => {
    try {
      if (localStorage.getItem(PROJECT_KEY) !== projectUndo.after) throw Error('Saved projects changed elsewhere. Reopen Projects before editing.');
      localStorage.setItem(PROJECT_KEY, writeProjects(projectUndo.previous));
      setProjects(projectUndo.previous); setProjectUndo(null); setDeletedProject(null); setManaged(null);
      setStatus('Previous saved project state restored.');
    } catch (failure) { error(failure); }
  };
  const manageSaved = operation => {
    try {
      const previous = readProjects(localStorage.getItem(PROJECT_KEY));
      requireSavedVersion(previous, projects.find(project => project.name === managed));
      const next = (operation === 'rename' ? renameProject : duplicateProject)(previous, managed, nextName);
      storeProjects(previous, next, operation);
      setManaged(null); setDeletedProject(null);
      setStatus(operation === 'rename' ? 'Saved project renamed.' : 'Independent editable project copy saved.');
    } catch (failure) { error(failure); }
  };
  const shelfSource = kind => kind === 'component' ? [captureComponent(api.current.getSceneElements(), api.current.getAppState().selectedElementIds, getCommonBounds), {}, api.current.getFiles()] : scene();
  const dismissSaveReview = kind => {
    setPendingSave(null);
    document.querySelector(`[data-shelf-save="${kind}"]`)?.focus();
  };
  const applyShelfSave = change => {
    if (busyRef.current) throw Error('Wait for the current image operation to finish.');
    if (localStorage.getItem(change.key) !== change.before) { dismissSaveReview(change.kind); throw Error('Saved work changed elsewhere. Save again to review the latest version.'); }
    if ((change.kind === 'component' ? componentName : name) !== change.requestedName || encodeDraft(...shelfSource(change.kind)) !== change.source) { dismissSaveReview(change.kind); throw Error('The drawing, selection or name changed. Save again to review this version.'); }
    if (change.kind === 'component') {
      const after = writeProjects(change.next);
      localStorage.setItem(COMPONENT_KEY, after); setComponents(change.next); setComponentUndo({ previous: change.previous, after });
      setStatus('Reusable component saved. Undo component change restores the previous shelf during this session.');
    } else {
      storeProjects(change.previous, change.next, 'save'); setDeletedProject(null);
      setStatus('Named composition saved. Undo project save restores the previous shelf during this session.');
    }
    dismissSaveReview(change.kind);
  };
  const prepareShelfSave = kind => {
    try {
      if (busyRef.current) return;
      setPendingOpen(null);
      const key = kind === 'component' ? COMPONENT_KEY : PROJECT_KEY, requestedName = kind === 'component' ? componentName : name;
      const before = localStorage.getItem(key), previous = readProjects(before), data = shelfSource(kind);
      const next = saveProject(previous, requestedName, ...data), candidate = next[next.length - 1];
      const existing = previous.find(project => project.name.toLowerCase() === candidate.name.toLowerCase());
      if (existing && existing.scene === candidate.scene && existing.name === candidate.name) { setPendingSave(null); setStatus('This saved version is already up to date.'); return; }
      const change = { kind, key, before, previous, next, requestedName, source: encodeDraft(...data), savedName: existing?.name };
      if (existing) { setPendingSave(change); setStatus('Review replacement before changing saved work.'); }
      else applyShelfSave(change);
    } catch (failure) { error(failure); }
  };
  const saveNamed = () => prepareShelfSave('project');
  const loadScene = async (data, projectName, reviewedSnapshot = null) => {
    if (!beginBusy()) return;
    const editor = api.current;
    try {
      const graph = validateProjectGraph(data.elements);
      setPendingSave(null);
      if (!save(false)) return;
      const snapshot = encodeDraft(...scene());
      if (reviewedSnapshot !== null && reviewedSnapshot !== snapshot) {
        setPendingOpen(null);
        throw Error('Your drawing changed after the review. Open the project again to review this version.');
      }
      if (reviewedSnapshot === null && editor.getSceneElements().some(element => !element.isDeleted)) {
        setPendingOpen({ data, name: projectName, snapshot }); setPanel('projects');
        setStatus('Review before replacing your drawing. Your current work stays here until you choose Replace current drawing.');
        return;
      }
      setPendingOpen(null);
      const elements = restoreElements(graph, null), incoming = filesForElements(elements, data.files);
      const files = mergePhotoFiles(editor.getFiles(), incoming);
      await verifyPhotoCopies(incoming);
      if (!mounted.current || api.current !== editor || encodeDraft(...scene()) !== snapshot) throw Error('Your drawing changed while opening the project. Open it again.');
      publishPhotoScene(editor, elements, { ...data.appState, selectedElementIds: {} }, files);
      editor.scrollToContent(elements, { fitToContent: true });
      setName(projectName); setStatus('Composition loaded. Undo returns to the previous drawing.');
    } catch (failure) { if (mounted.current) error(failure); }
    finally { finishBusy(); }
  };
  const insertProject = async data => {
    if (!beginBusy()) return;
    const editor = api.current;
    try {
      if (preservedDraft.current) throw Error('Reopen the preserved drawing before inserting artwork.');
      const original = encodeDraft(...scene()), previous = editor.getSceneElementsIncludingDeleted(), state = editor.getAppState();
      const graph = validateProjectGraph(data.elements);
      if (!graph.length) throw Error('This project has no artwork to insert.');
      const incomingElements = restoreElements(graph, null), incoming = filesForElements(incomingElements, data.files);
      await verifyPhotoCopies(incoming);
      if (!mounted.current || api.current !== editor || encodeDraft(...scene()) !== original) throw Error('The drawing changed. Insert the project again.');
      const files = mergePhotoFiles(editor.getFiles(), incoming), visible = incomingElements.filter(element => !element.isDeleted && element.opacity !== 0);
      const [left, top, right, bottom] = getCommonBounds(visible.length ? visible : incomingElements);
      const existingVisible = previous.filter(element => !element.isDeleted && element.opacity !== 0);
      const existingBounds = existingVisible.length ? getCommonBounds(existingVisible) : null;
      const x = existingBounds ? existingBounds[2] + 40 - left : state.width / 2 / state.zoom.value - state.scrollX - (left + right) / 2;
      const y = existingBounds ? existingBounds[1] - top : state.height / 2 / state.zoom.value - state.scrollY - (top + bottom) / 2;
      const merged = mergeProjectElements(previous, incomingElements, x, y, makeObjectId), ids = new Set(merged.insertedIds);
      const elements = merged.elements.map(element => newElementWith(element, {}, true));
      const selectedElementIds = Object.fromEntries(elements.filter(element => ids.has(element.id) && element.opacity !== 0 && !element.locked).map(element => [element.id, true]));
      publishPhotoScene(editor, elements, { ...state, selectedElementIds, selectedGroupIds: {}, editingGroupId: null }, files);
      setPanel(null);
      if (visible.length) requestAnimationFrame(() => requestAnimationFrame(() => { if (mounted.current && api.current === editor) editor.scrollToContent(elements.filter(element => ids.has(element.id) && element.opacity !== 0), { fitToViewport: true, viewportZoomFactor: 0.85, canvasOffsets: { top: 80, bottom: 100, left: 24, right: 48 } }); }));
      setStatus(visible.length ? 'Project artwork inserted. Existing drawing kept; Undo removes this insertion.' : 'Project artwork inserted but hidden. Use Objects to show it; Undo removes this insertion.');
    } catch (failure) { if (mounted.current) error(failure); }
    finally { finishBusy(); }
  };
  const removeProject = projectName => {
    try {
      const previous = readProjects(localStorage.getItem(PROJECT_KEY));
      const removed = requireSavedVersion(previous, projects.find(project => project.name === projectName));
      const next = previous.filter(project => project.name !== projectName);
      storeProjects(previous, next, 'delete');
      setDeletedProject(removed); setStatus('Saved project removed. Undo delete is available until the next save or deletion.');
    } catch (failure) { error(failure); }
  };
  const undoDelete = () => {
    try {
      const current = readProjects(localStorage.getItem(PROJECT_KEY));
      if (current.some(project => project.name.toLowerCase() === deletedProject.name.toLowerCase())) throw Error('A project with that name already exists.');
      const next = [...current, deletedProject];
      localStorage.setItem(PROJECT_KEY, writeProjects(next));
      setProjects(next); setDeletedProject(null); setProjectUndo(null); setStatus('Saved project restored.');
    } catch (failure) { error(failure); }
  };
  const arrange = operation => {
    try { patchObjects(arrangeObjects(api.current.getSceneElements(), api.current.getAppState().selectedElementIds, operation)); } catch (failure) { error(failure); }
  };
  const arrangePhotos = () => {
    try {
      const patches = photoSheet(api.current.getSceneElements(), sheetAll ? null : api.current.getAppState().selectedElementIds, Number(sheetColumns), Number(sheetCell), Number(sheetGap));
      patchObjects(patches);
      setPanel(null);
      const changed = new Set(patches.map(patch => patch.id));
      requestAnimationFrame(() => requestAnimationFrame(() => api.current?.scrollToContent(api.current.getSceneElements().filter(element => changed.has(element.id)), { fitToViewport: true, viewportZoomFactor: 1, canvasOffsets: { top: 80, bottom: 80, left: 8, right: 40 } })));
      setStatus('Photo contact sheet arranged. Proportions and photo copies kept; Undo restores the previous layout.');
    } catch (failure) { error(failure); }
  };
  const repeatSelection = kind => {
    try {
      const elements = api.current.getSceneElementsIncludingDeleted(), ids = api.current.getAppState().selectedElementIds;
      const makeId = makeObjectId;
      const next = kind === 'blend' ? blendShapes(elements, ids, Number(blendSteps), makeId) : kind === 'grid' ? repeatGrid(elements, ids, Number(gridColumns), Number(gridRows), Number(gridGap), makeId) : repeatRing(elements, ids, Number(ringCount), Number(ringRadius), makeId);
      commit(next.map(element => newElementWith(element, {}, true)));
      if (kind === 'blend') {
        setPanel(null);
        api.current.updateScene({ appState: { selectedElementIds: {} }, captureUpdate: CaptureUpdateAction.NEVER });
        requestAnimationFrame(() => requestAnimationFrame(() => api.current?.scrollToContent(next.filter(element => !element.isDeleted), { fitToViewport: true, viewportZoomFactor: 1, canvasOffsets: { top: 80, bottom: 80, left: 8, right: 40 } })));
      }
      setStatus('Editable copies created. Each object can be edited independently; Undo removes this repeat.');
    } catch (failure) { error(failure); }
  };
  const insertCloud = skeleton => {
    const state=api.current.getAppState(), previous=api.current.getSceneElementsIncludingDeleted();
    const x=state.width/2/state.zoom.value-state.scrollX-480,y=state.height/2/state.zoom.value-state.scrollY-320;
    const group=makeObjectId();const created=convertToExcalidrawElements(skeleton.map(shape=>({...shape,x:shape.x+x,y:shape.y+y}))).map(element=>({...element,groupIds:[group]}));
    commit([...previous,...created].map(element=>newElementWith(element,{},true)));setPanel(null);
    requestAnimationFrame(()=>requestAnimationFrame(()=>api.current?.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:1,canvasOffsets:{top:80,bottom:80,left:8,right:40}})));
    setStatus('Editable word cloud inserted. Ungroup to edit each word; Undo removes this insertion.');
  };
  const insertLayout = kind => {
    try {
      const skeleton = kind === 'starter' ? starterPlan(starterStyle, starterHeadline, starterDetails, starterSignoff) : kind === 'table' ? tablePlan(tableTitle, tableRows) : kind === 'qr' ? qrPlan(qrText, qrCaption) : kind === 'barcode' ? barcodePlan(barcodeText, barcodeCaption) : kind === 'dependencies' ? dependencyPlan(dependencyTitle, dependencyRows) : kind === 'palette' ? palettePlan(api.current.getSceneElements(), paletteTitle) : kind === 'chart' ? chartPlan(layoutTitle, chartRows) : kind === 'calendar' ? calendarPlan(calendarMonth, mondayFirst) : storyboardPlan(storyTitle, storyBriefs);
      const state = api.current.getAppState(), previous = api.current.getSceneElementsIncludingDeleted();
      const x = state.width / 2 / state.zoom.value - state.scrollX, y = state.height / 2 / state.zoom.value - state.scrollY;
      let created = convertToExcalidrawElements(skeleton.map(shape => ({ ...shape, x: shape.x + x, y: shape.y + y })));
      if (kind === 'qr' || kind === 'barcode' || kind === 'starter') {
        const group = makeObjectId();
        created = created.map(element => ({ ...element, groupIds: [group] }));
      }
      commit([...previous, ...created].map(element => newElementWith(element, {}, true)));
      setPanel(null);
      requestAnimationFrame(() => requestAnimationFrame(() => api.current?.scrollToContent(created, { fitToViewport: true, viewportZoomFactor: 1, canvasOffsets: { top: 80, bottom: 80, left: 8, right: 40 } })));
      setStatus('Editable layout inserted. Edit its shapes and text on the canvas; Undo removes this insertion.');
    } catch (failure) { error(failure); }
  };
  const recolor = () => {
    try { patchObjects(replaceCompositionColor(api.current.getSceneElementsIncludingDeleted(), sourceColor, targetColor, includeLocked)); } catch (failure) { error(failure); }
  };
  const saveComponent = () => prepareShelfSave('component');
  const useComponent = async component => {
    if (!beginBusy()) return;
    const editor = api.current;
    try {
      const original = encodeDraft(...scene()), elements = editor.getSceneElementsIncludingDeleted(), state = editor.getAppState();
      const data = decodeDraft(component.scene), componentElements = data.elements;
      const incoming = filesForElements(componentElements, data.files), files = mergePhotoFiles(editor.getFiles(), incoming);
      await verifyPhotoCopies(incoming);
      if (!mounted.current || api.current !== editor || encodeDraft(...scene()) !== original) throw Error('The drawing changed. Insert the component again.');
      const next = insertComponent(elements, componentElements, state.width / 2 / state.zoom.value - state.scrollX, state.height / 2 / state.zoom.value - state.scrollY, makeObjectId, getCommonBounds);
      const inserted = new Set(next.slice(elements.length).map(element => element.id));
      const selectedElementIds = Object.fromEntries(next.filter(element => inserted.has(element.id) && element.opacity !== 0 && !element.locked).map(element => [element.id, true]));
      publishPhotoScene(editor, next.map(element => newElementWith(element, {}, true)), { ...state, selectedElementIds, selectedGroupIds: {}, editingGroupId: null }, files);
      setPanel(null);
      requestAnimationFrame(() => requestAnimationFrame(() => { if (mounted.current && api.current === editor) { const visible = editor.getSceneElements().filter(element => inserted.has(element.id) && element.opacity !== 0); if (visible.length) editor.scrollToContent(visible, { fitToViewport: true, viewportZoomFactor: 0.85, canvasOffsets: { top: 80, bottom: 100, left: 24, right: 48 } }); } }));
      setStatus('Editable component inserted. Groups and internal connections kept; Undo removes this insertion.');
    } catch (failure) { if (mounted.current) error(failure); }
    finally { finishBusy(); }
  };
  const deleteComponent = component => {
    try {
      const previous = readProjects(localStorage.getItem(COMPONENT_KEY));
      requireSavedVersion(previous, component);
      const next = previous.filter(saved => saved.name !== component.name), after = writeProjects(next);
      localStorage.setItem(COMPONENT_KEY, after); setComponents(next); setComponentUndo({ previous, after });
      setStatus('Reusable component deleted. Undo component change can restore it.');
    } catch (failure) { error(failure); }
  };
  const undoComponentChange = () => {
    try {
      if (localStorage.getItem(COMPONENT_KEY) !== componentUndo.after) throw Error('Components changed elsewhere. Reopen Projects before editing.');
      localStorage.setItem(COMPONENT_KEY, writeProjects(componentUndo.previous));
      setComponents(componentUndo.previous); setComponentUndo(null); setStatus('Previous component shelf restored.');
    } catch (failure) { error(failure); }
  };
  const objectAction = (id, action) => {
    try {
      const elements = api.current.getSceneElementsIncludingDeleted();
      // updateScene normalizes indices in place; detach all objects from the current history snapshot.
      if (['front', 'back', 'up', 'down'].includes(action)) commit(reorderObject(elements, id, action).map(element => newElementWith(element, {}, true)));
      else patchObjects([{ id, ...layerPatch(elements.find(element => element.id === id), action) }]);
    } catch (failure) { error(failure); }
  };
  const locateObject = id => {
    try {
      if (busyRef.current) return;
      const editor = api.current, element = editor.getSceneElements().find(item => item.id === id && !item.isDeleted);
      if (!element) throw Error('This object is no longer available.');
      if (element.opacity === 0 || element.customData?.nobleHidden) throw Error('Show this object before locating it on the canvas.');
      editor.updateScene({ appState: { selectedElementIds: {}, selectedGroupIds: {}, editingGroupId: null }, captureUpdate: CaptureUpdateAction.NEVER });
      closePanel();
      requestAnimationFrame(() => requestAnimationFrame(() => { if (mounted.current && api.current === editor) editor.scrollToContent([element], { fitToViewport: true, viewportZoomFactor: 0.85, canvasOffsets: { top: 80, bottom: 100, left: 24, right: 48 } }); }));
      setStatus(element.locked ? 'Locked object located. Unlock it in Objects before editing.' : 'Object located on the canvas. Artwork unchanged.');
    } catch (failure) { error(failure); }
  };
  const invalidatePreparedSvg = (elements, files) => {
    const source = svgSource.current;
    if (!source) return;
    const stamp = svgStamp(elements);
    if (stamp === source.stamp && files === source.files) return;
    try {
      // Full validation/serialization is only needed after a document revision, never for ordinary pan/zoom.
      if (encodeDraft(elements, {}, files) === source.raw) { source.stamp = stamp; source.files = files; return; }
    } catch { /* Invalid new artwork must never leave an old export available to copy. */ }
    svgSource.current = null; setSvg('');
  };
  const prepareVector = async () => {
    if (!beginBusy()) return;
    const editor = api.current;
    svgSource.current = null; setSvg('');
    try {
      const [elements, appState, files] = scene();
      encodeDraft(elements, appState, files);
      const raw = encodeDraft(elements, {}, files);
      captureOutputSelection(editor, printArtworkSnapshot(editor, false, getCommonBounds));
      const visible = elements.filter(element => element.opacity !== 0);
      if (!visible.length) throw Error('Add a visible object first.');
      const result = await exportToSvg({ elements: visible, files: filesForElements(visible, files), appState: { ...appState, exportBackground: true, viewBackgroundColor: '#ffffff', exportWithDarkMode: false, exportEmbedScene: false }, exportPadding: 10 });
      const value = result.outerHTML;
      if (new TextEncoder().encode(value).length > 2 * 1024 * 1024) throw Error('Vector output is too large. Simplify the composition.');
      if (!mounted.current || api.current !== editor || encodeDraft(editor.getSceneElements(), {}, editor.getFiles()) !== raw) throw Error('The drawing changed while preparing SVG. Prepare it again.');
      svgSource.current = { raw, stamp: svgStamp(editor.getSceneElements()), files: editor.getFiles() };
      setSvg(value); setStatus('Vector SVG ready. Share the SVG file or copy the text. Editing the drawing clears this output; prepare it again after changes.');
    } catch (failure) { error(failure); } finally { finishOutput(); }
  };
  const shareVector = () => {
    if (!beginBusy()) return;
    const editor = api.current;
    let handedOff = false, id;
    try {
      const prepared = svgSource.current;
      if (!svg || !prepared || encodeDraft(editor.getSceneElements(), {}, editor.getFiles()) !== prepared.raw) throw Error('Prepare SVG from the current drawing before sharing.');
      captureOutputSelection(editor, printArtworkSnapshot(editor, false, getCommonBounds));
      id = svgRequest.current = svgRequest.current >= 1_000_000_000 ? 1 : svgRequest.current + 1;
      pendingSvg.current = { id };
      sendSvgArtwork(window.HonorableDrawing, id, svg);
      handedOff = true;
      if (pendingSvg.current?.id === id) setStatus('Preparing a private SVG file for the share sheet…');
    } catch (failure) {
      if (pendingSvg.current?.id === id) pendingSvg.current = null;
      error(failure);
    } finally { if (!handedOff) finishOutput(); }
  };
  const useImage = async (selectionOnly = false) => {
    if (!beginBusy()) return;
    const editor = api.current, attempt = {};
    pendingImage.current = attempt;
    const failed = failure => {
      if (!mounted.current || pendingImage.current !== attempt) return;
      pendingImage.current = null; finishOutput(); error(failure);
    };
    setStatus('Preparing image...');
    try {
      const snapshot = printArtworkSnapshot(editor, selectionOnly, getCommonBounds);
      captureOutputSelection(editor, snapshot);
      const requireCurrent = () => {
        if (!mounted.current || pendingImage.current !== attempt || !snapshot.current(api.current)) throw Error('The drawing changed while preparing the image. Try again from the current drawing.');
      };
      if (!save(false)) { pendingImage.current = null; finishOutput(); return; }
      const blob = await exportToBlob({ elements: snapshot.elements, files: snapshot.files, appState: { ...editor.getAppState(), exportBackground: !transparentImage, viewBackgroundColor: '#ffffff', exportWithDarkMode: false, exportEmbedScene: false }, mimeType: 'image/png', maxWidthOrHeight: 2048 });
      requireCurrent();
      if (!blob || !blob.size || blob.size > 8 * 1024 * 1024) throw Error('The image is too large. Use a smaller drawing.');
      const reader = new FileReader();
      reader.onerror = () => failed(Error('Could not prepare the image. Try again.'));
      reader.onabort = () => failed(Error('Image preparation stopped. Try again.'));
      reader.onload = () => {
        try {
          requireCurrent();
          if (typeof reader.result !== 'string') throw Error('Could not read the prepared image. Try again.');
          if (typeof window.HonorableDrawing?.usePng !== 'function') throw Error('Open this workspace in the Android app to use the image.');
          // Earlier app versions return undefined; current native code returns false on refused admission.
          if (window.HonorableDrawing.usePng(reader.result) === false) throw Error('The drawing is not active or another operation is running. Try again when ready.');
        } catch (failure) { failed(failure); }
      };
      reader.readAsDataURL(blob);
    } catch (failure) { failed(failure); }
  };
  const printArtwork = async (selectionOnly = false) => {
    if (!beginBusy()) return;
    const editor = api.current;
    let handedOff = false, id;
    setStatus('Preparing artwork for one white printed page…');
    try {
      if (!window.HonorableDrawing?.beginPrintImage) throw Error('Open Noble on Android to print or save a PDF.');
      const snapshot = printArtworkSnapshot(editor, selectionOnly, getCommonBounds);
      captureOutputSelection(editor, snapshot);
      if (!save(false)) return;
      const blob = await exportToBlob({ elements: snapshot.elements, files: snapshot.files, appState: { ...editor.getAppState(), exportBackground: true, viewBackgroundColor: '#ffffff', exportWithDarkMode: false, exportEmbedScene: false }, mimeType: 'image/png', maxWidthOrHeight: 2048 });
      if (!blob || !blob.size || blob.size > MAX_PRINT_BYTES) throw Error('Artwork is too large to print. Use a smaller drawing.');
      const value = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onerror = () => reject(Error('Artwork could not be prepared for printing.'));
        reader.onload = () => resolve(String(reader.result));
        reader.readAsDataURL(blob);
      });
      if (!mounted.current) return;
      if (!snapshot.current(api.current)) throw Error('The drawing changed while preparing print output. Try again.');
      id = printRequest.current = printRequest.current >= 1_000_000_000 ? 1 : printRequest.current + 1;
      pendingPrint.current = { id };
      sendPrintArtwork(window.HonorableDrawing, id, value);
      handedOff = true;
      if (pendingPrint.current?.id === id) setStatus('Choose Save as PDF or a printer in Android’s print options. Your drawing stays editable; only confirm a destination you want to use.');
    } catch (failure) {
      if (pendingPrint.current?.id === id) pendingPrint.current = null;
      if (mounted.current) error(failure);
    } finally {
      if (!handedOff && mounted.current) {
        finishOutput();
      }
    }
  };
  const stageProjectFile = (value, previous) => {
    if (projectJson.current !== previous) throw Error('Project text changed. Choose the file again to keep your changes.');
    setPendingOpen(null); setJson(value.raw); setPreparedProjectFile({ name: value.project.name, raw: value.raw }); setPendingSave(null); setProjectTool('files'); setPanel('projects');
    setStatus(`Project file ready: ${value.project.name}. Your drawing is unchanged. Choose Open chosen project or Insert chosen artwork.`);
  };
  window.honorableProjectFileResult = result => {
    const pending = pendingProjectFile.current;
    if (!mounted.current || !pending || result?.requestId !== pending.id) return;
    try {
      if (result.cancelled) { setStatus('Project file selection cancelled. Your drawing is unchanged.'); return; }
      if (result.error) throw Error(result.error);
      if (pending.kind === 'open') stageProjectFile(receiveProjectFile(window.HonorableDrawing, pending.id, result.length), pending.previous);
      else if (result.saved === true) setStatus(pending.kind === 'recovery' ? 'Recovery copy saved. The original stored draft is unchanged. Keep the copy for repair before starting fresh.' : 'Editable project file saved. Your drawing stays here.');
      else throw Error('The project file operation did not complete. Try again.');
    } catch (failure) { error(failure); }
    finally { pendingProjectFile.current = null; finishBusy(); }
  };
  const saveProjectFile = () => {
    if (preservedDraft.current) { error(Error('Cannot export this canvas while the stored draft is preserved and not safely open. No project file was created.')); return; }
    if (!beginBusy()) return;
    try {
      const raw = projectFileArtwork(name, ...scene(), selectionFile ? (api.current.getAppState().selectedElementIds || {}) : null, getCommonBounds);
      if (window.HonorableDrawing?.beginProjectFileSave) {
        const id = projectFileRequest.current = projectFileRequest.current % 1000000000 + 1;
        pendingProjectFile.current = { id, kind: 'save' };
        setStatus('Choose where to keep your editable project file.');
        sendProjectFile(window.HonorableDrawing, id, name, raw);
      } else {
        if (location.hostname === 'honorable-drawing.local') throw Error('Update Noble to use project files. Your editable drawing stays here.');
        const url = URL.createObjectURL(new Blob([raw], { type: 'application/json;charset=utf-8' }));
        const link = document.createElement('a'); link.href = url; link.download = projectFilename(name); document.body.appendChild(link); link.click(); link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
        finishBusy(); setStatus('Editable project download requested. Check your browser downloads; your drawing stays here.');
      }
    } catch (failure) { pendingProjectFile.current = null; finishBusy(); error(failure); }
  };
  const keepRecoveryFile = () => {
    if (!preservedDraft.current || !beginBusy()) return;
    try {
      const raw = recoveryFile(localStorage.getItem(KEY));
      if (window.HonorableDrawing?.beginProjectFileSave) {
        const id = projectFileRequest.current = projectFileRequest.current % 1000000000 + 1;
        pendingProjectFile.current = { id, kind: 'recovery' };
        setStatus('Choose where to keep the preserved drawing recovery copy. The original stays unchanged.');
        sendRecoveryFile(window.HonorableDrawing, id, raw);
      } else {
        if (location.hostname === 'honorable-drawing.local') throw Error('Update Noble to keep a recovery file. The stored draft is unchanged.');
        const url = URL.createObjectURL(new Blob([raw], { type: 'application/json;charset=utf-8' }));
        const link = document.createElement('a'); link.href = url; link.download = projectFilename('Preserved drawing recovery'); document.body.appendChild(link); link.click(); link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
        finishBusy(); setStatus('Recovery download requested. Check Downloads before clearing the stored draft. This copy is for repair, not an editable project yet.');
      }
    } catch (failure) { pendingProjectFile.current = null; finishBusy(); error(failure); }
  };
  const chooseProjectFile = () => {
    if (busyRef.current) return;
    if (window.HonorableDrawing?.chooseProjectFile) {
      if (!beginBusy()) return;
      const id = projectFileRequest.current = projectFileRequest.current % 1000000000 + 1;
      pendingProjectFile.current = { id, kind: 'open', previous: projectJson.current };
      setStatus('Choose a Noble project file. Your drawing stays unchanged until you open or insert it.');
      try { window.HonorableDrawing.chooseProjectFile(id); }
      catch (failure) { pendingProjectFile.current = null; finishBusy(); error(failure); }
    } else if (location.hostname === 'honorable-drawing.local') error(Error('Update Noble to choose project files.'));
    else projectFileInput.current?.click();
  };
  const readBrowserProject = async file => {
    if (!file || !beginBusy()) return;
    const previous = projectJson.current;
    try {
      if (file.size > MAX_PORTABLE_BYTES) throw Error('Project file is too large.');
      const value = decodeProjectFile(await file.arrayBuffer());
      if (mounted.current) stageProjectFile(value, previous);
    } catch (failure) { if (mounted.current) error(failure); }
    finally { finishBusy(); }
  };
  const usePreparedProject = insert => {
    try {
      if (!preparedProjectFile || projectJson.current !== preparedProjectFile.raw) throw Error('Project text changed. Choose the file again.');
      const value = importProject(preparedProjectFile.raw);
      if (insert) insertProject(value); else loadScene(value, value.name);
    } catch (failure) { error(failure); }
  };
  const chooseProjectView = value => {
    if (busyRef.current || value === projectTool || !Object.prototype.hasOwnProperty.call(projectViews, value)) return;
    if (pendingSave || pendingOpen) setStatus('Replacement review dismissed. Your drawing and saved work are unchanged.');
    setPendingOpen(null); setPendingSave(null); setProjectTool(value);
  };
  const openFinish = () => {
    if (busyRef.current || !ready || preservedDraft.current) return;
    if (panel === 'projects' && projectTool === 'finish') { finishOpened.current = true; closePanel(); return; }
    if (panel !== 'projects') showPanel('projects');
    if (pendingSave || pendingOpen) setStatus('Replacement review dismissed. Your drawing and saved work are unchanged.');
    setPendingOpen(null); setPendingSave(null); setProjectTool('finish'); finishOpened.current = true;
  };
  const foundObjects = panel === 'objects' ? findArtwork(objects, objectQuery, objectType) : [];
  return <main onKeyDownCapture={event => { if (outputUnlocking) { event.preventDefault(); event.stopPropagation(); } }}>
    <header><strong>Noble <span>draw</span></strong><button onClick={() => save()} disabled={busy || !ready}>Save draft</button><button className="primary" onClick={() => useImage()} disabled={busy || !ready}>{busy ? 'Preparing...' : 'Use image'}</button></header>
    <nav aria-label="Composition tools">{['objects', 'arrange', 'layouts', 'projects'].map(value => <button key={value} data-panel-trigger={value} aria-expanded={panel === value} aria-controls="workspace-panel" onClick={() => showPanel(value)} disabled={busy || !ready}>{value[0].toUpperCase() + value.slice(1)}</button>)}</nav>
    <input ref={photoInput} aria-label="Choose a local photo" type="file" accept="image/png,image/jpeg" hidden onChange={event => { const file = event.target.files?.[0]; event.target.value = ''; if (file) addPhoto(file); }} />
    <input ref={projectFileInput} aria-label="Choose a Noble project file" type="file" accept="application/json,.json" hidden onChange={event => { const file = event.target.files?.[0]; event.target.value = ''; readBrowserProject(file); }} />
    {ready && !hasArtwork && !startDismissed && !preservedDraft.current && !panel && <section className="first-design" aria-label="Start creating">
      <strong>What would you like to make?</strong><p>Start with editable words and shapes, or add your own photo. Everything stays on this device.</p>
      <div><button className="primary" disabled={busy} onClick={() => { if (busyRef.current) return; setLayoutTool('starter'); showPanel('layouts'); }}>Start a design</button><button disabled={busy} onClick={() => choosePhoto()}>Choose a photo</button><button disabled={busy} onClick={() => { if (busyRef.current) return; setStartDismissed(true); canvasRegion.current?.focus({ preventScroll: true }); }}>Explore canvas</button></div>
    </section>}
    <section ref={canvasRegion} tabIndex={-1} className="canvas" style={outputUnlocking ? { pointerEvents: 'none' } : undefined} aria-label="Offline drawing workspace" onDragOverCapture={event => event.preventDefault()} onDropCapture={event => { event.preventDefault(); event.stopPropagation(); setStatus('Use Add photo to import a local copy.'); }}>
      <button className="photo-add" onClick={() => choosePhoto()} disabled={busy || !ready}>Add photo</button>
      <Excalidraw excalidrawAPI={bindEditor} theme="light" langCode="en" name="Noble drawing" isCollaborating={false} viewModeEnabled={busy && !outputUnlocking}
        initialData={restored || { appState: { viewBackgroundColor: '#ffffff', currentItemStrokeColor: '#111111', currentItemFontFamily: 2 } }}
        UIOptions={{ tools: { image: false }, canvasActions: { loadScene: false, saveToActiveFile: false, export: false, saveAsImage: false, toggleTheme: false, clearCanvas: false } }}
        validateEmbeddable={false} onLinkOpen={(_, event) => event.preventDefault()} onPaste={() => false}
        onChange={(elements, state, files) => { if (resetPending.current) return; if(cutoutOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==cutoutOwner.current.snapshot)cutoutOwner.current.valid=false;}catch{cutoutOwner.current.valid=false;}} setHasArtwork(elements.some(element => !element.isDeleted)); setReady(Boolean(api.current) && !state.isLoading); invalidatePreparedSvg(elements, files); latest.current = [elements, state, files]; if (panel === 'objects' || panel === 'arrange') { const next = elements.filter(element => !element.isDeleted); setObjects(current => current.length === next.length && current.every((element, index) => element === next[index]) ? current : next); } clearTimeout(timer.current); timer.current = setTimeout(() => save(false), 700); }}>
        <MainMenu><MainMenu.ItemCustom><small>Offline creation powered by Excalidraw.<br/>Open-source and font notices are bundled.</small></MainMenu.ItemCustom></MainMenu>
      </Excalidraw>
    </section>
    {panel && <section key={panel} id="workspace-panel" className="workspace-panel" onKeyDownCapture={event => { if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); if (vectorFileOwner.current) event.currentTarget.querySelector('button[data-vector-file-cancel="true"]')?.click(); if(panel==='cutout')event.currentTarget.querySelector('button[data-photo-cutout-cancel="true"]')?.click(); closePanel(); } }} aria-label={`${panel} panel`} onClickCapture={event => { const vectorCancel = vectorFileOwner.current && event.target instanceof Element && event.target.closest('button[data-vector-file-cancel="true"]'); const cutoutCancel=panel==='cutout'&&event.target instanceof Element&&event.target.closest('button[data-photo-cutout-cancel="true"]'); if (busyRef.current && !vectorCancel && !cutoutCancel) { event.preventDefault(); event.stopPropagation(); } }}>
      <div className="panel-title"><h2 ref={panelHeading} tabIndex={-1}>{panel[0].toUpperCase() + panel.slice(1)}</h2><button disabled={busy} onClick={closePanel}>Close panel</button></div>
      {panel === 'layouts' && <><p>Choose what to make. Each result stays editable, and Undo removes one insertion.</p>
        <div role="group" aria-label="Layout tools" className="tool-grid layout-tools">{[["starter","Starter design"],["cloud","Word cloud"],["table","Table"],["qr","QR artwork"],["barcode","Barcode label"],["dependencies","Dependency diagram"],["palette","Artwork color key"],["chart","Data chart"],["calendar","Month calendar"],["storyboard","Storyboard sheet"]].map(([key,label]) => <button key={key} aria-pressed={layoutTool === key} onClick={() => setLayoutTool(key)}>{label}</button>)}</div>
        {layoutTool === 'cloud' && <WordCloudTool captureCurrent={() => { const editor=api.current,snapshot=encodeDraft(...scene());return () => mounted.current && api.current===editor && !busyRef.current && encodeDraft(...scene())===snapshot; }} onApply={insertCloud} />}
        {layoutTool === 'starter' && <><h3>Make a design your own</h3><p>Choose a starting layout and personalize its words. Creates a grouped white card with editable text and shapes. Double-tap a text block to edit it; Ungroup lets you rearrange individual parts. Nothing is uploaded.</p><label>Starter design style<select aria-label="Starter design style" value={starterStyle} onChange={event => setStarterStyle(event.target.value)}><option value="quote">Quote card</option><option value="announcement">Announcement</option><option value="flyer">Simple flyer</option></select></label><label>Design headline<input maxLength={120} value={starterHeadline} onChange={event => setStarterHeadline(event.target.value)} /></label><label>Design details<textarea aria-label="Design details" rows={3} maxLength={360} value={starterDetails} onChange={event => setStarterDetails(event.target.value)} /></label><label>Design sign-off<input maxLength={120} value={starterSignoff} onChange={event => setStarterSignoff(event.target.value)} /></label><p>Headline and sign-off: up to 60 characters each. Optional details: up to 180 characters across 3 lines. Long lines wrap. Insert keeps your existing artwork; Undo removes the new design.</p><StarterPreview style={starterStyle} headline={starterHeadline} details={starterDetails} signoff={starterSignoff} /><button onClick={() => insertLayout('starter')}>Insert editable starter design</button></>}
        {layoutTool === 'table' && <><h3>Editable comparison table</h3><p>Paste tab-separated cells from a spreadsheet, or separate cells with |. The first row is the header. Use 2–6 columns and 2–10 rows, up to 64 characters per cell. Creates editable cells and bound text; formulas and links remain plain text.</p><label>Table title<input maxLength={60} value={tableTitle} onChange={event => setTableTitle(event.target.value)} /></label><label>Table rows<textarea aria-label="Table rows" rows={5} maxLength={4096} value={tableRows} onChange={event => setTableRows(event.target.value)} /></label><button onClick={() => insertLayout('table')}>Insert editable table</button></>}
        {layoutTool === 'qr' && <><h3>QR artwork</h3><p>Turn a short link or message into grouped QR shapes with a white border. Generated on your device; no link is opened or uploaded.</p><label>QR link or text<textarea aria-label="QR link or text" rows={2} maxLength={192} placeholder="Paste a short link or write a message" value={qrText} onChange={event => setQrText(event.target.value)} /></label><label>QR caption<input maxLength={60} value={qrCaption} onChange={event => setQrCaption(event.target.value)} /></label><button onClick={() => insertLayout('qr')}>Insert editable QR code</button></>}
        {layoutTool === 'barcode' && <><h3>Barcode label</h3><p>Create grouped, editable Code 128 bars locally. Use up to 80 printable English letters, numbers or symbols. Keep bar and white border proportions; check a scan before printing.</p><label>Barcode text<input aria-label="Barcode text" maxLength={80} placeholder="Example: NOBLE-ITEM-042" value={barcodeText} onChange={event => setBarcodeText(event.target.value)} /></label><label>Barcode caption<input maxLength={60} value={barcodeCaption} onChange={event => setBarcodeCaption(event.target.value)} /></label><button onClick={() => insertLayout('barcode')}>Insert editable barcode</button><p>No identifier is uploaded. Code 128 is different from retail EAN/UPC codes.</p></>}
        {layoutTool === 'dependencies' && <><h3>Dependency diagram</h3><p>Write one Label -&gt; Label connection per line, or a standalone label. Up to 20 nodes and 32 connections; return cycles are rejected. Creates editable connected nodes from left to right, entirely on your device.</p><label>Dependency diagram title<input maxLength={60} value={dependencyTitle} onChange={event => setDependencyTitle(event.target.value)} /></label><label>Dependency rows<textarea aria-label="Dependency rows" rows={4} maxLength={4096} value={dependencyRows} onChange={event => setDependencyRows(event.target.value)} /></label><button onClick={() => insertLayout('dependencies')}>Insert editable dependency diagram</button></>}
        {layoutTool === 'palette' && <><h3>Artwork color key</h3><p>Create editable swatches and hex labels from visible stroke and fill colors, up to 32 colors. Includes object opacity; this reads assigned colors, not rendered pixels. Hidden objects are excluded.</p><label>Color-key title<input maxLength={60} value={paletteTitle} onChange={event => setPaletteTitle(event.target.value)} /></label><button onClick={() => insertLayout('palette')}>Insert editable color key</button></>}
        {layoutTool === 'chart' && <><h3>Data chart</h3><label>Chart title<input maxLength={60} value={layoutTitle} onChange={event => setLayoutTitle(event.target.value)} /></label>
        <label>Chart rows<textarea aria-label="Chart rows" rows={4} maxLength={1600} value={chartRows} onChange={event => setChartRows(event.target.value)} /></label><p>One label | number per line, up to 12 rows. Positive and negative bars use one linear scale from zero.</p><button onClick={() => insertLayout('chart')}>Insert editable chart</button></>}
        {layoutTool === 'calendar' && <><h3>Month calendar</h3><label>Calendar month<input type="month" min="1900-01" max="2299-12" value={calendarMonth} onChange={event => setCalendarMonth(event.target.value)} /></label><label className="check-label"><input type="checkbox" checked={mondayFirst} onChange={event => setMondayFirst(event.target.checked)} />Monday starts the week</label><button onClick={() => insertLayout('calendar')}>Insert editable calendar</button></>}
        {layoutTool === 'storyboard' && <><h3>Storyboard sheet</h3><label>Storyboard title<input maxLength={60} value={storyTitle} onChange={event => setStoryTitle(event.target.value)} /></label><label>Scene briefs<textarea aria-label="Scene briefs" rows={4} maxLength={1600} value={storyBriefs} onChange={event => setStoryBriefs(event.target.value)} /></label><p>One scene brief per line, up to 12 scenes. Empty frames are ready for your own artwork. This creates a planning sheet.</p><button onClick={() => insertLayout('storyboard')}>Insert editable storyboard</button></>}
      </>}
      {panel === 'objects' && <><TextContrastReview elements={objects} expanded={contrastOpen} onToggle={() => setContrastOpen(value => !value)} background={contrastBackground} onBackgroundChange={setContrastBackground} onLocate={locateObject} /><label>Find artwork<input type="search" maxLength={100} value={objectQuery} onChange={event => setObjectQuery(event.target.value)} placeholder="Text or object type" /></label><label>Object type<select aria-label="Object type" value={objectType} onChange={event => setObjectType(event.target.value)}><option value="all">All objects</option><option value="text">Text</option><option value="photos">Photos</option><option value="shapes">Shapes, lines & frames</option></select></label><p>{foundObjects.length} of {objects.length} objects. Searches this drawing only; hidden objects must be shown before locating.</p>{!!(objectQuery || objectType !== 'all') && <button onClick={() => { setObjectQuery(''); setObjectType('all'); }}>Clear object filters</button>}{!!objects.length && !foundObjects.length && <p>No matching objects. Try another word or clear the filters.</p>}<p>Top objects appear in front. Changes share the drawing Undo control. Replace photo fits a new local copy inside the old photo’s box, keeping its center, rotation and flip; any old crop is cleared. Originals stay unchanged.</p>{!objects.length && <p>Add a shape or text to start a composition.</p>}
        {foundObjects.map(({ element, number, hidden, label }) => <article key={element.id} className="object-row"><strong>{label}{hidden ? ' · hidden' : element.locked ? ' · locked' : ''}</strong><div className="tool-grid"><button disabled={hidden} aria-label={`Find object ${number} on canvas`} onClick={() => locateObject(element.id)}>Find on canvas</button>
          {element.type === 'image' && <><button disabled={element.locked || !!element.customData?.nobleHidden} aria-label={`Replace photo ${number}`} onClick={() => choosePhoto(element.id)}>Replace photo</button><button disabled={element.locked || !!element.customData?.nobleHidden} aria-label={`Suggest crop for photo ${number}`} onClick={() => openFraming(element.id)}>Suggest crop</button><button disabled={element.locked || !!element.customData?.nobleHidden} aria-label={`Straighten photo ${number}`} onClick={() => openRectify(element.id)}>Straighten photo</button><button disabled={element.locked || !!element.customData?.nobleHidden} aria-label={`Outline cutout from photo ${number}`} onClick={() => openCutout(element.id)}>Outline cutout</button><button disabled={element.locked || !!element.customData?.nobleHidden} aria-label={`Remove connected color from photo ${number}`} onClick={() => openWand(element.id)}>Remove connected color</button><button disabled={element.locked || !!element.customData?.nobleHidden} aria-label={`Trace connected outline from photo ${number}`} onClick={() => openWand(element.id,true)}>Trace connected outline</button><button disabled={element.locked || !!element.customData?.nobleHidden} aria-label={`Make mosaic from photo ${number}`} onClick={() => openMosaic(element.id)}>Photo mosaic</button></>}
          <button onClick={() => objectAction(element.id, 'visibility')}>{element.customData?.nobleHidden ? 'Show' : 'Hide'}</button>
          <button disabled={!!element.customData?.nobleHidden} onClick={() => objectAction(element.id, 'lock')}>{element.locked ? 'Unlock' : 'Lock'}</button>
          <button disabled={element.locked} onClick={() => objectAction(element.id, 'up')}>Forward</button><button disabled={element.locked} onClick={() => objectAction(element.id, 'down')}>Backward</button>
          <button disabled={element.locked} onClick={() => objectAction(element.id, 'front')}>To front</button><button disabled={element.locked} onClick={() => objectAction(element.id, 'back')}>To back</button>
        </div></article>)}</>}
      {panel === 'mosaic' && mosaicTarget && <PhotoMosaicTool target={mosaicTarget} isCurrent={() => framingCurrent(mosaicTarget)} onApply={insertMosaic} onCancel={closePanel} />}
      {panel === 'trace' && wandTarget && <PhotoWandTool target={wandTarget} mode="trace" isCurrent={() => framingCurrent(wandTarget)} onApply={insertTrace} onCancel={closePanel} />}
      {panel === 'cutout' && cutoutTarget && <PhotoOutlineCutoutTool target={cutoutTarget} isCurrent={() => cutoutTarget.valid && framingCurrent(cutoutTarget)} onApply={insertCutout} onCancel={() => {cutoutTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'wand' && wandTarget && <PhotoWandTool target={wandTarget} isCurrent={() => framingCurrent(wandTarget)} onApply={insertWand} onCancel={closePanel} />}
      {panel === 'rectify' && rectifyTarget && <PhotoRectifyTool target={rectifyTarget} isCurrent={() => framingCurrent(rectifyTarget)} onApply={insertRectified} onCancel={closePanel} />}
      {panel === 'framing' && framingTarget && <PhotoFramingPreview target={framingTarget} isCurrent={() => framingCurrent(framingTarget)} onApply={applyFraming} onCancel={closePanel} />}
      {panel === 'arrange' && <><div className="tool-grid layout-tools" role="group" aria-label="Arrangement tool">{[['align','Align & space'],['photos','Photo sheet'],['blend','Shape blend'],['grid','Grid repeat'],['personalize','Personalized copies'],['ring','Ring repeat'],['recolor','Recolor'],['boolean','Combine shapes']].map(([value,label]) => <button key={value} aria-pressed={arrangeTool === value} onClick={() => setArrangeTool(value)}>{label}</button>)}</div>{arrangeTool === 'boolean' && <ShapeBooleanTool captureSource={() => captureBooleanSource(api.current)} isCurrent={source => mounted.current && source.current(api.current)} onApply={insertBoolean} onCancel={closePanel} />}{arrangeTool === 'personalize' && <PersonalizeTool captureSource={capturePersonalization} onApply={insertPersonalization} />}{arrangeTool === 'photos' && <><h3>Photo contact sheet</h3><p>Fit 2–8 existing photos into equal cells without cropping. Order follows the drawing’s back-to-front object list, left to right then down. Unlock, ungroup and straighten photos first. Other artwork stays in place.</p><label className="check-label"><input type="checkbox" checked={sheetAll} onChange={event => setSheetAll(event.target.checked)} />Use all visible unlocked photos</label><p>{sheetAll ? 'All eligible photos will move and resize.' : 'Select only the photos to arrange on the canvas first.'}</p><div className="number-fields"><label>Photo sheet columns<input inputMode="numeric" maxLength={1} value={sheetColumns} onChange={event => setSheetColumns(event.target.value)} /></label><label>Photo cell size<input inputMode="numeric" maxLength={4} value={sheetCell} onChange={event => setSheetCell(event.target.value)} /></label><label>Photo sheet gap<input inputMode="decimal" maxLength={5} value={sheetGap} onChange={event => setSheetGap(event.target.value)} /></label></div><button onClick={arrangePhotos}>Arrange photos as contact sheet</button></>}{arrangeTool === 'align' && <><p>Select objects on the canvas first. Unlock and ungroup them before arranging.</p><h3>Align selected objects</h3><div className="tool-grid">{['left', 'center', 'right', 'top', 'middle', 'bottom'].map(operation => <button key={operation} onClick={() => arrange(operation)}>{operation}</button>)}</div><h3>Space three or more objects evenly</h3><div className="tool-grid"><button onClick={() => arrange('horizontal')}>Horizontal spacing</button><button onClick={() => arrange('vertical')}>Vertical spacing</button></div></>}{arrangeTool === 'blend' && <><h3>Blend endpoint shapes</h3><p>Select exactly two matching rectangle, ellipse or diamond shapes. Insert editable intermediate positions, sizes, rotations and colors; endpoint shapes stay unchanged.</p><label>Blend intermediate steps<input inputMode="numeric" maxLength={2} value={blendSteps} onChange={event => setBlendSteps(event.target.value)} /></label><button onClick={() => repeatSelection('blend')}>Blend selected endpoint shapes</button></>}{arrangeTool === 'grid' && <><h3>Editable grid repeat</h3><p>Repeat the selected composition as independent objects. The original is the first grid placement.</p><div className="number-fields"><label>Grid columns<input inputMode="numeric" maxLength={2} value={gridColumns} onChange={event => setGridColumns(event.target.value)} /></label><label>Grid rows<input inputMode="numeric" maxLength={2} value={gridRows} onChange={event => setGridRows(event.target.value)} /></label><label>Grid gap<input inputMode="decimal" maxLength={5} value={gridGap} onChange={event => setGridGap(event.target.value)} /></label></div><button onClick={() => repeatSelection('grid')}>Create editable grid copies</button></>}{arrangeTool === 'ring' && <><h3>Editable ring repeat</h3><p>Add upright copies around the selected composition. The original stays at the center.</p><div className="number-fields"><label>Ring copies<input inputMode="numeric" maxLength={2} value={ringCount} onChange={event => setRingCount(event.target.value)} /></label><label>Ring radius<input inputMode="decimal" maxLength={5} value={ringRadius} onChange={event => setRingRadius(event.target.value)} /></label></div><button onClick={() => repeatSelection('ring')}>Create editable ring copies</button></>}{arrangeTool === 'recolor' && <><h3>Replace a composition color</h3><p>Change matching strokes and fills together. Locked and hidden objects stay protected unless included.</p><label>Source composition color<select value={sourceColor} onChange={event => setSourceColor(event.target.value)}>{!compositionColors(objects).some(entry => entry.color === sourceColor) && <option value={sourceColor}>{sourceColor} · no matches</option>}{compositionColors(objects).map(entry => <option key={entry.color} value={entry.color}>{entry.color} · {entry.count} uses</option>)}</select></label><label>Replacement hex color<input value={targetColor} maxLength={9} onChange={event => setTargetColor(event.target.value)} /></label><label className="check-label"><input type="checkbox" checked={includeLocked} onChange={event => setIncludeLocked(event.target.checked)} />Include locked and hidden objects</label><button onClick={recolor}>Replace matching colors</button></>}</>}
      {panel === 'projects' && <>
        {pendingOpen && <section className="object-row" aria-label="Review drawing replacement"><h3 ref={openReviewHeading} tabIndex={-1}>Open “{pendingOpen.name}” instead?</h3><p>This replaces your current drawing and its local draft. To keep both, choose Keep current drawing, then save a named design or project file first. Undo restores the previous drawing only during this session.</p><div className="tool-grid"><button disabled={busy} onClick={() => { setPendingOpen(null); projectTaskHeading.current?.focus(); setStatus('Current drawing kept. The project was not opened.'); }}>Keep current drawing</button><button disabled={busy} onClick={() => loadScene(pendingOpen.data, pendingOpen.name, pendingOpen.snapshot)}>Replace current drawing</button></div></section>}
        {pendingSave && <section className="object-row" aria-label="Review saved replacement"><h3 ref={saveReviewHeading} tabIndex={-1}>Replace saved {pendingSave.kind === 'component' ? 'component' : 'composition'} “{pendingSave.savedName}”?</h3><p>The saved version will be replaced. Undo is available in this session until another shelf change; use a different name if you need to keep both versions. Your current drawing stays open.</p><div className="tool-grid"><button disabled={busy} onClick={() => { try { applyShelfSave(pendingSave); } catch (failure) { error(failure); } }}>Replace saved version</button><button disabled={busy} onClick={() => { dismissSaveReview(pendingSave.kind); setStatus('Saved version kept. Your current drawing is unchanged.'); }}>Keep saved version</button></div></section>}

        {projectTool !== 'finish' && <div role="group" aria-label="Project tasks" className="tool-grid layout-tools project-tools">{Object.entries(projectViews).filter(([value]) => value !== 'finish').map(([value, label]) => <button key={value} aria-pressed={projectTool === value} aria-controls="project-task-view" disabled={busy} onClick={() => chooseProjectView(value)}>{label}</button>)}</div>}
        <section id="project-task-view" key={projectTool} className="project-task-view" aria-label={projectViews[projectTool]}>
          <h3 id="project-task-heading" ref={projectTaskHeading} tabIndex={-1}>{projectViews[projectTool]}</h3>
        {projectTool === 'finish' && <>
          <p>Your drawing stays editable here. Choose what you want to keep or make next.</p>
          <div className="finish-choices" aria-label="Ways to finish your drawing">
            <article><h4>Find it here later</h4><p>Give your design a name in Saved designs on this device. This does not create an external backup file.</p><button disabled={busy} onClick={() => chooseProjectView('saved')}>Save a named design</button></article>
            <article><h4>Keep an editable copy</h4><p>Save a project file with editable objects, hidden artwork and embedded photo copies. Choose where to keep it.</p><button disabled={busy} onClick={() => chooseProjectView('files')}>Editable project file</button></article>
            <article><h4>Make an image</h4><p>Turn visible artwork into one image, then use the photo editor for the final export. Hidden objects stay out.</p><button disabled={busy} onClick={() => chooseProjectView('images')}>Image copy options</button></article>
            <article><h4>Print or save a PDF</h4><p>Fit visible artwork onto a white page. Android lets you choose Save as PDF or a printer.</p><button disabled={busy} onClick={() => chooseProjectView('print')}>Print and PDF options</button></article>
          </div>
          <button className="finish-browse" disabled={busy} onClick={() => chooseProjectView('saved')}>Browse all project tools</button>
        </>}

        {projectTool === 'saved' && <>
        <label>Project name<input value={name} maxLength={40} onChange={event => setName(event.target.value)} /></label>
        <button className="primary" data-shelf-save="project" onClick={saveNamed}>Save named composition</button><p>Up to 12 editable projects on this device. Saving an existing name asks you to review replacement first.</p>
        {projects.map(project => <div className="project-row" key={project.name}><button onClick={() => { try { loadScene(decodeDraft(project.scene), project.name); } catch (failure) { error(failure); } }}>Open {project.name}</button><button aria-label={`Manage ${project.name}`} onClick={() => { setManaged(project.name); setNextName(`${project.name} copy`.slice(0, 40)); }}>Manage</button><button aria-label={`Delete ${project.name}`} onClick={() => removeProject(project.name)}>Delete</button></div>)}
        {managed && <div className="object-row"><strong>Manage {managed}</strong><label>New saved project name<input value={nextName} maxLength={40} onChange={event => setNextName(event.target.value)} /></label><div className="tool-grid"><button onClick={() => manageSaved('rename')}>Rename saved project</button><button onClick={() => manageSaved('duplicate')}>Duplicate saved project</button><button onClick={() => setManaged(null)}>Cancel changes</button></div></div>}
        {projectUndo && <button onClick={undoProjectChange}>Undo project {projectUndo.label}</button>}
        {deletedProject && <button onClick={undoDelete}>Undo delete {deletedProject.name}</button>}
        </>}
        {projectTool === 'files' && <>
        {preservedDraft.current && <section className="object-row" aria-label="Preserved draft recovery"><strong>Keep the stored drawing before starting fresh</strong><p>The stored draft could not be safely opened. A recovery copy keeps its exact text, including any stored photo data and metadata, for repair. It does not repair or open the drawing automatically. Choose where to keep it; the original stays here.</p><button disabled={busy || !ready} onClick={keepRecoveryFile}>Keep recovery copy</button></section>}
        <label>Project name for file<input value={name} maxLength={40} onChange={event => setName(event.target.value)} /></label>
        <label className="check-label"><input type="checkbox" aria-label="Save selected artwork only" checked={selectionFile} disabled={busy} onChange={event => { if (!busyRef.current) setSelectionFile(event.target.checked); }} />Save selected artwork only</label><p>{selectionFile ? 'Select on the canvas first. Attached labels, frame contents and hidden children stay editable; their photo copies travel with the file. The rest of your drawing stays here.' : 'Save includes the whole editable drawing, including hidden objects and embedded photo copies.'}</p>
        <p>Save a file to keep your objects and imported photo copies editable. Choose a Noble project file to review it first; your drawing changes only when you choose Open or Insert. The file includes any embedded photo copies, so choose where to keep it.</p><div className="tool-grid"><button onClick={saveProjectFile}>Save project file</button><button onClick={chooseProjectFile}>Choose project file</button></div>
        {preparedProjectFile && preparedProjectFile.raw === json && <section className="object-row" aria-label="Chosen project file"><strong>{preparedProjectFile.name}</strong><p>Your current drawing is unchanged. Open replaces it with recoverable Undo; Insert adds this artwork beside it.</p><div className="tool-grid"><button onClick={() => usePreparedProject(false)}>Open chosen project</button><button onClick={() => usePreparedProject(true)}>Insert chosen artwork</button></div></section>}
        </>}
        {projectTool === 'vectors' && <VectorImportTool captureSource={captureVectorSource} isCurrent={source => source.current(api.current)} beginFile={beginVectorFile} onApply={insertVectorArtwork} onCancel={closePanel} />}
        {projectTool === 'images' && <>
        <label className="check-label"><input type="checkbox" aria-label="Use transparent image background" checked={transparentImage} disabled={busy} onChange={event => { if (!busyRef.current) setTransparentImage(event.target.checked); }} />Use transparent image background</label><p>{transparentImage ? 'Empty canvas areas stay transparent. Photos and filled shapes keep their backgrounds. Choose PNG when exporting from the photo editor to keep transparency.' : 'Empty canvas areas become white. Turn on transparency for artwork you want to place over another image.'} This choice applies to both image buttons for this session; SVG and editable project saves keep their own settings.</p><p>Select artwork on the canvas, then open only that part in the photo editor. Attached labels and selected frame contents are included; hidden objects are excluded. Outside connections are detached in the image copy. Your editable drawing stays here. The header Use image button includes the whole visible drawing.</p><button onClick={() => useImage(true)}>Use selected artwork</button>
        </>}
        {projectTool === 'print' && <>
        <p>Fit your design onto one white page without cropping. In Android’s print options, choose Save as PDF or a printer, then review the page and destination before confirming. Your drawing stays editable.</p>
        <p>Prints a flattened image up to 2048 pixels; large prints may look soft. Empty and transparent areas become white. Hidden objects are left out. Selected artwork includes attached labels and frame contents.</p>
        <div className="tool-grid"><button disabled={busy} onClick={() => printArtwork(false)}>Print whole drawing</button><button disabled={busy} onClick={() => printArtwork(true)}>Print selected artwork</button></div>
        </>}
        {projectTool === 'parts' && <>
        <p>Select artwork on the canvas, then save a component. Attached labels and frame contents travel with the selection. Connections to unselected objects are detached; the original stays unchanged. Keep up to 12 local components. Inserted groups and internal connections stay editable.</p><label>Component name<input value={componentName} maxLength={40} onChange={event => setComponentName(event.target.value)} /></label><button data-shelf-save="component" onClick={saveComponent}>Save selected as component</button>
        {components.map(component => <div className="project-row" key={component.name}><button onClick={() => useComponent(component)}>Insert {component.name}</button><button aria-label={`Delete component ${component.name}`} onClick={() => deleteComponent(component)}>Delete</button></div>)}
        {componentUndo && <button onClick={undoComponentChange}>Undo component change</button>}
        </>}
        {projectTool === 'advanced' && <>
        <h3>Editable project transfer</h3><p>Project JSON includes editable objects and any imported photo copies. Original file names, original URIs and account data are excluded.</p><p>Import opens the saved composition. Insert project artwork places its objects beside your visible drawing and selects the movable ones. Undo restores the previous drawing in either case.</p>
        <div className="tool-grid"><button onClick={() => { try { const raw = exportProject(name, ...scene()); setPendingOpen(null); setJson(raw); setStatus('Project JSON ready. Select and copy it to keep a portable editable copy.'); } catch (failure) { error(failure); } }}>Export project JSON</button><button onClick={() => { try { const imported = importProject(json); loadScene(imported, imported.name); } catch (failure) { error(failure); } }}>Import project JSON</button><button onClick={() => { try { insertProject(importProject(json)); } catch (failure) { error(failure); } }}>Insert project artwork</button></div>
        <h3>Scalable vector artwork</h3><p>Export the visible composition as SVG. Hidden objects are excluded. Editing clears prepared output; prepare it again after changes. Keep project JSON separately for editing in Noble.</p><button onClick={prepareVector}>Prepare vector SVG</button>
        {!!svg && <><button disabled={busy} onClick={shareVector}>Share SVG file</button><p>The SVG file includes visible artwork and embedded photo copies. Choose the app and destination to share it with. Your drawing stays here.</p><label htmlFor="vector-svg">Vector SVG</label><textarea id="vector-svg" value={svg} readOnly spellCheck={false} onFocus={event => event.target.select()} /></>}
        <label htmlFor="portable-project-json">Project JSON</label><textarea id="portable-project-json" ref={portable} value={json} maxLength={MAX_PORTABLE_BYTES} spellCheck={false} onChange={event => { setPendingOpen(null); setJson(event.target.value); }} /><button onClick={() => { portable.current.focus(); portable.current.select(); }}>Select JSON to copy</button>        </>}
        </section>
      </>}
    </section>}
    <footer className="drawing-footer"><div className="drawing-status" role="status" aria-live="polite">{status}<small>Local draft on this device. Use image opens the photo editor; export a copy there to keep the PNG.</small></div>{hasArtwork && !preservedDraft.current && <button ref={finishTrigger} className="finish-drawing" disabled={busy || !ready} aria-expanded={panel === 'projects' && projectTool === 'finish'} aria-controls="project-task-view" onClick={openFinish}>Finish drawing</button>}</footer>
  </main>;
}
createRoot(document.getElementById('root')).render(<Editor />);
