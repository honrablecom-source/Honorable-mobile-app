import {receiveDepthModel} from './model-import.mjs';
import {PhotoStereogramTool} from './PhotoStereogramTool.jsx';
import {stereogramSettings,stereogramSeparation,DEPTH_UNITS} from './photo-stereogram.mjs';
import {PhotoGradientTool} from './PhotoGradientTool.jsx';
import {PhotoCarveTool} from './PhotoCarveTool.jsx';
import {carveSettings} from './photo-carve.mjs';
import { ModelStudio } from './ModelStudio.jsx';
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
import { schedulePlan } from './schedule.mjs';
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
import {PictureLogicTool} from './PictureLogicTool.jsx';
import {pictureWorksheet} from './nonogram.mjs';
import {MazeTool} from './MazeTool.jsx';
import {JigsawTool} from './JigsawTool.jsx';
import {BoxNetTool} from './BoxNetTool.jsx';
import {jigsawArtwork} from './jigsaw.mjs';
import {boxNetArtwork} from './box-net.mjs';
import {mazeWorksheet} from './maze.mjs';
import { WordCloudTool } from './WordCloudTool.jsx';
import { PhotoWandTool } from './PhotoWandTool.jsx';
import { PhotoOutlineCutoutTool } from './PhotoOutlineCutoutTool.jsx';
import { PhotoSurfaceMockupTool } from './PhotoSurfaceMockupTool.jsx';
import { PhotoObjectTools } from './PhotoObjectTools.jsx';
import { PhotoPanoramaTool } from './PhotoPanoramaTool.jsx';
import { PhotoReferenceToneTool } from './PhotoReferenceToneTool.jsx';
import { PhotoNineSliceTool } from './PhotoNineSliceTool.jsx';
import { PhotoCubeLutTool } from './PhotoCubeLutTool.jsx';
import { PhotoAnaglyphTool } from './PhotoAnaglyphTool.jsx';
import { PhotoSpriteSliceTool } from './PhotoSpriteSliceTool.jsx';
import { spriteFramePlan } from './photo-sprite-slice-plan.mjs';
import { PhotoChannelSplitTool } from './PhotoChannelSplitTool.jsx';
import { channelSplitPlan } from './photo-channel-split-plan.mjs';
import { PhotoChannelPackTool } from './PhotoChannelPackTool.jsx';
import { PhotoPaletteImageTool } from './PhotoPaletteImageTool.jsx';
import { PhotoDistanceFieldTool } from './PhotoDistanceFieldTool.jsx';
import { PhotoNormalMapTool } from './PhotoNormalMapTool.jsx';
import {PhotoContoursTool} from './PhotoContoursTool.jsx';
import {contourArtwork} from './photo-contours.mjs';
import {PhotoAsciiTool} from './PhotoAsciiTool.jsx';
import {PhotoStitchTool} from './PhotoStitchTool.jsx';
import {PhotoHalftoneTool} from './PhotoHalftoneTool.jsx';
import {PhotoBlendTool} from './PhotoBlendTool.jsx';
import {PhotoChromaKeyTool} from './PhotoChromaKeyTool.jsx';
import {PhotoRelightTool} from './PhotoRelightTool.jsx';
import {PhotoDocumentTool} from './PhotoDocumentTool.jsx';
import {PhotoRegistrationTool} from './PhotoRegistrationTool.jsx';
import {PhotoRadialTool} from './PhotoRadialTool.jsx';
import {PhotoFrequencyTool} from './PhotoFrequencyTool.jsx';
import {frequencySourcePhoto} from './photo-frequency-source.mjs';
import {frequencyLayerPlan} from './photo-frequency.mjs';
import {PhotoBarrierTool} from './PhotoBarrierTool.jsx';
import {PhotoVisionTool} from './PhotoVisionTool.jsx';
import {visionSheetPlan,visionSettings,VISION_MODELS,VISION_CONVENTION} from './photo-vision.mjs';
import {PhotoCubeFacesTool} from './PhotoCubeFacesTool.jsx';
import {barrierFramePlan,barrierSettings} from './photo-barrier.mjs';
import {cubeFramePlan,CUBE_FACES,CUBE_CONVENTION} from './photo-cube-faces.mjs';
import {asciiArtwork} from './photo-ascii.mjs';
import {stitchArtwork} from './photo-stitch.mjs';
import {halftoneArtwork} from './photo-halftone.mjs';
import {PhotoIslandsTool} from './PhotoIslandsTool.jsx';
import {islandFramePlan} from './photo-islands.mjs';
import {PhotoAnnulusTool} from './PhotoAnnulusTool.jsx';
import {PhotoAtlasTool} from './PhotoAtlasTool.jsx';
import {savedAtlasMap} from './photo-atlas.mjs';
import { prepareExactPhotoCopy, readExactPhotoPng } from './photo-exact-copy.mjs';
import { PhotoSeamlessTool } from './PhotoSeamlessTool.jsx';
import { prepareSeamlessCopy } from './photo-seamless-image.mjs';
import { PhotoDisplacementTool } from './PhotoDisplacementTool.jsx';
import { PhotoLuminanceMaskTool } from './PhotoLuminanceMaskTool.jsx';
import { PhotoRedactionTool } from './PhotoRedactionTool.jsx';
import { PhotoDespeckleTool } from './PhotoDespeckleTool.jsx';
import { PhotoCloneRepairTool } from './PhotoCloneRepairTool.jsx';
import { DimensionCalloutTool } from './DimensionCalloutTool.jsx';
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
  const [nativeDepth] = useState(()=>{try{return {asset:receiveDepthModel(window.HonorableDrawing)};}catch(error){return {error:error.message};}});
  const depthOpened=useRef(false);
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
  useEffect(()=>{if(ready&&!depthOpened.current&&(nativeDepth.asset||nativeDepth.error)){depthOpened.current=true;if(nativeDepth.asset)setPanel('model');else setStatus(nativeDepth.error);}},[ready,nativeDepth]);
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
  const [surfaceTarget,setSurfaceTarget]=useState(null);
  const surfaceOwner=useRef(null);
  const dimensionOwner=useRef(null);
  const frequencyOwner=useRef(null);
  const barrierOwner=useRef(null);
  const stereogramOwner=useRef(null);
  const [stereogramTarget,setStereogramTarget]=useState(null);
  const gradientOwner=useRef(null);
  const [gradientTarget,setGradientTarget]=useState(null);
  const carveOwner=useRef(null);
  const [carveTarget,setCarveTarget]=useState(null);
  const visionOwner=useRef(null);
  const [visionTarget,setVisionTarget]=useState(null);
  const cubeOwner=useRef(null);
  const [frequencyTarget,setFrequencyTarget]=useState(null);
  const [barrierTarget,setBarrierTarget]=useState(null);
  const [cubeTarget,setCubeTarget]=useState(null);
  const documentOwner=useRef(null);
  const registrationOwner=useRef(null);
  const radialOwner=useRef(null);
  const [documentTarget,setDocumentTarget]=useState(null);
  const [registrationTarget,setRegistrationTarget]=useState(null);
  const [radialTarget,setRadialTarget]=useState(null);
  const relightOwner=useRef(null);
  const [relightTarget,setRelightTarget]=useState(null);
  const chromaOwner=useRef(null);
  const [chromaTarget,setChromaTarget]=useState(null);
  const blendOwner=useRef(null);
  const [blendTarget,setBlendTarget]=useState(null);
  const asciiOwner=useRef(null);
  const stitchOwner=useRef(null);
  const halftoneOwner=useRef(null);
  const [asciiTarget,setAsciiTarget]=useState(null);
  const [stitchTarget,setStitchTarget]=useState(null);
  const [halftoneTarget,setHalftoneTarget]=useState(null);
  const contoursOwner=useRef(null);
  const [contoursTarget,setContoursTarget]=useState(null);
  const islandsOwner=useRef(null);
  const [islandsTarget,setIslandsTarget]=useState(null);
  const pictureOwner=useRef(null);
  const mazeOwner=useRef(null);
  const jigsawOwner=useRef(null);
  const boxNetOwner=useRef(null);
  const annulusOwner=useRef(null);
  const [annulusTarget,setAnnulusTarget]=useState(null);
  const atlasOwner=useRef(null);
  const [atlasTarget,setAtlasTarget]=useState(null);
  const panoramaOwner=useRef(null);
  const [panoramaTarget,setPanoramaTarget]=useState(null);
  const referenceToneOwner=useRef(null);
  const [referenceToneTarget,setReferenceToneTarget]=useState(null);
  const nineSliceOwner=useRef(null);
  const [nineSliceTarget,setNineSliceTarget]=useState(null);
  const cubeLutOwner=useRef(null);
  const [cubeLutTarget,setCubeLutTarget]=useState(null);
  const anaglyphOwner=useRef(null);
  const [anaglyphTarget,setAnaglyphTarget]=useState(null);
  const spriteSliceOwner=useRef(null);
  const [spriteSliceTarget,setSpriteSliceTarget]=useState(null);
  const channelSplitOwner=useRef(null);
  const [channelSplitTarget,setChannelSplitTarget]=useState(null);
  const channelPackOwner=useRef(null);
  const [channelPackTarget,setChannelPackTarget]=useState(null);
  const paletteImageOwner=useRef(null);
  const [paletteImageTarget,setPaletteImageTarget]=useState(null);
  const distanceFieldOwner=useRef(null);
  const [distanceFieldTarget,setDistanceFieldTarget]=useState(null);
  const normalMapOwner=useRef(null);
  const [normalMapTarget,setNormalMapTarget]=useState(null);
  const seamlessOwner=useRef(null);
  const [seamlessTarget,setSeamlessTarget]=useState(null);
  const displacementOwner=useRef(null);
  const [displacementTarget,setDisplacementTarget]=useState(null);
  const luminanceMaskOwner=useRef(null);
  const [luminanceMaskTarget,setLuminanceMaskTarget]=useState(null);
  const redactionOwner=useRef(null);
  const [redactionTarget,setRedactionTarget]=useState(null);
  const despeckleOwner=useRef(null);
  const [despeckleTarget,setDespeckleTarget]=useState(null);
  const [cloneTarget,setCloneTarget]=useState(null);
  const cloneOwner=useRef(null);
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
  const [scheduleTitle, setScheduleTitle] = useState('Production schedule'), [scheduleRows, setScheduleRows] = useState('Prepare | 2026-11-01 | 2026-11-03\nShoot | 2026-11-03 | 2026-11-05\nEdit | 2026-11-06 | 2026-11-10');
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
  const openRelight=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const editor=api.current,elements=editor.getSceneElements(),photo=replaceablePhoto(elements,id),file=editor.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a photo at least 8 pixels on each side for Normal-map relight.');
      const maps=[],seen=new Set([photo.fileId]);elements.forEach((element,index)=>{if(element.id===id||element.type!=='image')return;try{const candidate=replaceablePhoto(elements,element.id),copy=editor.getFiles()[candidate.fileId];if(!copy||seen.has(candidate.fileId))return;const info=photoBytes(copy.dataURL);if(info.width===width&&info.height===height){seen.add(candidate.fileId);maps.push({id:candidate.id,dataURL:copy.dataURL,label:`Photo ${index+1} · ${width} × ${height}`});}}catch{}});
      if(!maps.length)throw Error('Add another visible, unlocked, ungrouped photo with matching embedded pixel dimensions to use as a normal map.');
      if(relightOwner.current)relightOwner.current.valid=false;
      const target={editor,photo,dataURL:file.dataURL,width,height,maps,snapshot:encodeDraft(...scene()),valid:true};relightOwner.current=target;setRelightTarget(target);setPanel('relight');
    }catch(failure){error(failure);}
  };
  const openBlend=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const editor=api.current,elements=editor.getSceneElements(),photo=replaceablePhoto(elements,id),file=editor.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a photo at least 8 pixels on each side for Photo blend.');
      const partners=[],seen=new Set([photo.fileId]);elements.forEach((element,index)=>{if(element.id===id||element.type!=='image')return;try{const candidate=replaceablePhoto(elements,element.id),copy=editor.getFiles()[candidate.fileId];if(!copy||seen.has(candidate.fileId))return;const info=photoBytes(copy.dataURL);if(info.width===width&&info.height===height){seen.add(candidate.fileId);partners.push({id:candidate.id,dataURL:copy.dataURL,label:`Photo ${index+1} · ${width} × ${height}`});}}catch{}});
      if(!partners.length)throw Error('Add another visible, unlocked, ungrouped photo with matching embedded pixel dimensions to use for blending.');
      if(blendOwner.current)blendOwner.current.valid=false;
      const target={editor,photo,dataURL:file.dataURL,width,height,partners,snapshot:encodeDraft(...scene()),valid:true};blendOwner.current=target;setBlendTarget(target);setPanel('blend');
    }catch(failure){error(failure);}
  };
  const openDisplacement=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const editor=api.current,elements=editor.getSceneElements(),photo=replaceablePhoto(elements,id),file=editor.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a photo at least 8 pixels on each side for Displacement map.');
      const maps=[];elements.forEach((element,index)=>{if(element.id===id||element.type!=='image')return;try{const candidate=replaceablePhoto(elements,element.id),copy=editor.getFiles()[candidate.fileId];if(!copy)return;const info=photoBytes(copy.dataURL);if(info.width===width&&info.height===height)maps.push({id:candidate.id,dataURL:copy.dataURL,label:`Photo ${index+1} · ${width} × ${height}`});}catch{}});
      if(!maps.length)throw Error('Add another visible, unlocked, ungrouped photo with matching embedded pixel dimensions to use as a displacement map.');
      if(displacementOwner.current)displacementOwner.current.valid=false;
      const target={editor,photo,dataURL:file.dataURL,width,height,maps,snapshot:encodeDraft(...scene()),valid:true};displacementOwner.current=target;setDisplacementTarget(target);setPanel('displacement');
    }catch(failure){error(failure);}
  };
  const openLuminanceMask=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const editor=api.current,elements=editor.getSceneElements(),photo=replaceablePhoto(elements,id),file=editor.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a photo at least 8 pixels on each side for Photo mask.');
      const masks=[];elements.forEach((element,index)=>{if(element.id===id||element.type!=='image')return;try{const candidate=replaceablePhoto(elements,element.id),copy=editor.getFiles()[candidate.fileId];if(!copy)return;const info=photoBytes(copy.dataURL);if(info.width===width&&info.height===height)masks.push({id:candidate.id,dataURL:copy.dataURL,label:`Photo ${index+1} · ${width} × ${height}`});}catch{}});
      if(!masks.length)throw Error('Add another visible, unlocked, ungrouped photo with matching embedded pixel dimensions to use as a mask.');
      if(luminanceMaskOwner.current)luminanceMaskOwner.current.valid=false;
      const target={editor,photo,dataURL:file.dataURL,width,height,masks,snapshot:encodeDraft(...scene()),valid:true};luminanceMaskOwner.current=target;setLuminanceMaskTarget(target);setPanel('luminanceMask');
    }catch(failure){error(failure);}
  };
  const openRedaction=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a photo at least 8 pixels on each side for Privacy blocks.');
      if(redactionOwner.current)redactionOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};redactionOwner.current=target;setRedactionTarget(target);setPanel('redaction');
    }catch(failure){error(failure);}
  };
  const openDistanceField=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a photo at least 8 pixels on each side for Distance field.');
      if(distanceFieldOwner.current)distanceFieldOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};distanceFieldOwner.current=target;setDistanceFieldTarget(target);setPanel('distanceField');
    }catch(failure){error(failure);}
  };
  const openDocument=id=>{if(busyRef.current)return;try{const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded document artwork is unavailable.');const{width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use document artwork at least8pixels per side.');if(documentOwner.current)documentOwner.current.valid=false;const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};documentOwner.current=target;setDocumentTarget(target);setPanel('document');}catch(failure){error(failure);}};
  const openRadial=id=>{if(busyRef.current)return;try{const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded lens artwork is unavailable.');const{width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use lens artwork at least8pixels per side.');if(radialOwner.current)radialOwner.current.valid=false;const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};radialOwner.current=target;setRadialTarget(target);setPanel('radial');}catch(failure){error(failure);}};
  const openChroma=id=>{if(busyRef.current)return;try{const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded key artwork is unavailable.');const{width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use key artwork at least8pixels per side.');if(chromaOwner.current)chromaOwner.current.valid=false;const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};chromaOwner.current=target;setChromaTarget(target);setPanel('chroma');}catch(failure){error(failure);}};
  const openFrequency=id=>{if(busyRef.current||preservedDraft.current)return;try{const editor=api.current,elements=editor.getSceneElements(),photo=frequencySourcePhoto(elements,id),file=editor.getFiles()[photo.fileId];if(!file)throw Error('The source frequency artwork is unavailable.');const{width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use photos at least8pixels per side.');const partners=[];elements.forEach((element,index)=>{if(element.id===id||element.type!=='image')return;try{const candidate=frequencySourcePhoto(elements,element.id),copy=editor.getFiles()[candidate.fileId];if(!copy)return;const info=photoBytes(copy.dataURL);if(info.width===width&&info.height===height)partners.push({id:candidate.id,photo:candidate,dataURL:copy.dataURL,label:`Photo ${index+1} · ${candidate.customData?.nobleFrequency?.role||'unassigned'} · ${width} × ${height}`});}catch{}});if(frequencyOwner.current)frequencyOwner.current.valid=false;const target={editor,photo,dataURL:file.dataURL,width,height,partners,snapshot:encodeDraft(...scene()),valid:true};frequencyOwner.current=target;setFrequencyTarget(target);setPanel('frequency');}catch(failure){error(failure);}};
  const openBarrier=id=>{if(busyRef.current)return;try{const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded barrier artwork is unavailable.');const{width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use barrier artwork at least8pixels per side.');if(barrierOwner.current)barrierOwner.current.valid=false;const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};barrierOwner.current=target;setBarrierTarget(target);setPanel('barrier');}catch(failure){error(failure);}};
  const openAscii=id=>{if(busyRef.current)return;try{const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded ascii artwork is unavailable.');const{width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use ascii artwork at least8pixels per side.');if(asciiOwner.current)asciiOwner.current.valid=false;const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};asciiOwner.current=target;setAsciiTarget(target);setPanel('ascii');}catch(failure){error(failure);}};
  const openStitch=id=>{if(busyRef.current)return;try{const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded stitch artwork is unavailable.');const{width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use stitch artwork at least8pixels per side.');if(stitchOwner.current)stitchOwner.current.valid=false;const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};stitchOwner.current=target;setStitchTarget(target);setPanel('stitch');}catch(failure){error(failure);}};
  const openHalftone=id=>{if(busyRef.current)return;try{const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded halftone artwork is unavailable.');const{width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use halftone artwork at least8pixels per side.');if(halftoneOwner.current)halftoneOwner.current.valid=false;const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};halftoneOwner.current=target;setHalftoneTarget(target);setPanel('halftone');}catch(failure){error(failure);}};
  const openContours=id=>{if(busyRef.current)return;try{const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded height artwork is unavailable.');const{width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use height artwork at least8pixels per side.');if(contoursOwner.current)contoursOwner.current.valid=false;const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};contoursOwner.current=target;setContoursTarget(target);setPanel('contours');}catch(failure){error(failure);}};
  const openIslands=id=>{if(busyRef.current)return;try{const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded artwork is unavailable.');const{width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use artwork at least8pixels per side.');if(islandsOwner.current)islandsOwner.current.valid=false;const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};islandsOwner.current=target;setIslandsTarget(target);setPanel('islands');}catch(failure){error(failure);}};
  const openAnnulus=id=>{
    if(busyRef.current)return;
    try{const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded circular artwork is unavailable.');const{width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use circular artwork at least8pixels on each side.');if(annulusOwner.current)annulusOwner.current.valid=false;const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};annulusOwner.current=target;setAnnulusTarget(target);setPanel('annulus');}catch(failure){error(failure);}
  };
  const openAtlas=id=>{
    if(busyRef.current)return;
    try{const editor=api.current,elements=editor.getSceneElements(),photo=replaceablePhoto(elements,id),file=editor.getFiles()[photo.fileId];if(!file)throw Error('The embedded atlas source is unavailable.');const info=photoBytes(file.dataURL),sources=[],seen=new Set();
      for(const element of [photo,...elements.filter(e=>e.id!==id)]){if(element.type!=='image')continue;try{const candidate=replaceablePhoto(elements,element.id),copy=editor.getFiles()[candidate.fileId];if(!copy||seen.has(candidate.fileId))continue;const size=photoBytes(copy.dataURL);if(size.width>=8&&size.height>=8){seen.add(candidate.fileId);sources.push({id:candidate.fileId,dataURL:copy.dataURL,width:size.width,height:size.height,label:`Photo ${elements.findIndex(e=>e.id===candidate.id)+1}`});}}catch{}}
      if(atlasOwner.current)atlasOwner.current.valid=false;const target={editor,photo,dataURL:file.dataURL,width:info.width,height:info.height,sources,savedMap:savedAtlasMap(photo.customData?.nobleAtlas,photo.fileId,info.width,info.height),snapshot:encodeDraft(...scene()),valid:true};atlasOwner.current=target;setAtlasTarget(target);setPanel('atlas');
    }catch(failure){error(failure);}
  };
  const openStereogram=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);stereogramSettings(width,height,Math.min(64,Math.floor(width/4)),1,false);
      if(stereogramOwner.current)stereogramOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};stereogramOwner.current=target;setStereogramTarget(target);setPanel('stereogram');
    }catch(failure){error(failure);}
  };
  const openCarve=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);carveSettings(width,height,width>8?'width':'height',1);
      if(carveOwner.current)carveOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};carveOwner.current=target;setCarveTarget(target);setPanel('carve');
    }catch(failure){error(failure);}
  };
  const openVision=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);visionSettings(width,height);
      if(visionOwner.current)visionOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};visionOwner.current=target;setVisionTarget(target);setPanel('vision');
    }catch(failure){error(failure);}
  };
  const openCube=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded panorama is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(height<8||width!==height*2)throw Error('Use a 2:1 equirectangular panorama at least 16 × 8 pixels.');
      if(cubeOwner.current)cubeOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};cubeOwner.current=target;setCubeTarget(target);setPanel('cube');
    }catch(failure){error(failure);}
  };
  const openPanorama=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded panorama is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(height<8||width!==height*2)throw Error('Use a 2:1 equirectangular panorama at least 16 × 8 pixels.');
      if(panoramaOwner.current)panoramaOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};panoramaOwner.current=target;setPanoramaTarget(target);setPanel('panorama');
    }catch(failure){error(failure);}
  };
  const openGradient=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const editor=api.current,elements=editor.getSceneElements(),photo=replaceablePhoto(elements,id),file=editor.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use photos at least 8 pixels on each side.');
      const references=[],seen=new Set([photo.fileId]);elements.forEach((element,index)=>{if(element.id===id||element.type!=='image')return;try{const candidate=replaceablePhoto(elements,element.id),copy=editor.getFiles()[candidate.fileId];if(!copy||seen.has(candidate.fileId))return;const info=photoBytes(copy.dataURL);if(info.width>=8&&info.height>=8){seen.add(candidate.fileId);references.push({width:info.width,height:info.height,photo:candidate,id:candidate.id,dataURL:copy.dataURL,label:`Photo ${index+1} · ${info.width} × ${info.height}`});}}catch{}});
      if(!references.length)throw Error('Add another distinct visible, unlocked, ungrouped photo as a gradient-paste destination.');
      if(gradientOwner.current)gradientOwner.current.valid=false;
      const target={editor,photo,dataURL:file.dataURL,width,height,references,snapshot:encodeDraft(...scene()),valid:true};gradientOwner.current=target;setGradientTarget(target);setPanel('gradient');
    }catch(failure){error(failure);}
  };
  const openRegistration=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const editor=api.current,elements=editor.getSceneElements(),photo=replaceablePhoto(elements,id),file=editor.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use photos at least 8 pixels on each side.');
      const references=[],seen=new Set([photo.fileId]);elements.forEach((element,index)=>{if(element.id===id||element.type!=='image')return;try{const candidate=replaceablePhoto(elements,element.id),copy=editor.getFiles()[candidate.fileId];if(!copy||seen.has(candidate.fileId))return;const info=photoBytes(copy.dataURL);if(info.width>=8&&info.height>=8){seen.add(candidate.fileId);references.push({width:info.width,height:info.height,photo:candidate,id:candidate.id,dataURL:copy.dataURL,label:`Photo ${index+1} · ${info.width} × ${info.height}`});}}catch{}});
      if(!references.length)throw Error('Add another distinct visible, unlocked, ungrouped photo as a registration reference.');
      if(registrationOwner.current)registrationOwner.current.valid=false;
      const target={editor,photo,dataURL:file.dataURL,width,height,references,snapshot:encodeDraft(...scene()),valid:true};registrationOwner.current=target;setRegistrationTarget(target);setPanel('registration');
    }catch(failure){error(failure);}
  };
  const openReferenceTone=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const editor=api.current,elements=editor.getSceneElements(),photo=replaceablePhoto(elements,id),file=editor.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use photos at least 8 pixels on each side.');
      const references=[],seen=new Set([photo.fileId]);elements.forEach((element,index)=>{if(element.id===id||element.type!=='image')return;try{const candidate=replaceablePhoto(elements,element.id),copy=editor.getFiles()[candidate.fileId];if(!copy||seen.has(candidate.fileId))return;const info=photoBytes(copy.dataURL);if(info.width>=8&&info.height>=8){seen.add(candidate.fileId);references.push({id:candidate.id,dataURL:copy.dataURL,label:`Photo ${index+1} · ${info.width} × ${info.height}`});}}catch{}});
      if(!references.length)throw Error('Add another distinct visible, unlocked, ungrouped photo as a tone reference.');
      if(referenceToneOwner.current)referenceToneOwner.current.valid=false;
      const target={editor,photo,dataURL:file.dataURL,width,height,references,snapshot:encodeDraft(...scene()),valid:true};referenceToneOwner.current=target;setReferenceToneTarget(target);setPanel('referenceTone');
    }catch(failure){error(failure);}
  };
  const openNineSlice=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded artwork is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use artwork at least 8 pixels on each side.');
      if(nineSliceOwner.current)nineSliceOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};nineSliceOwner.current=target;setNineSliceTarget(target);setPanel('nineSlice');
    }catch(failure){error(failure);}
  };
  const openCubeLut=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a photo at least 8 pixels on each side for Cube LUT.');
      if(cubeLutOwner.current)cubeLutOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};cubeLutOwner.current=target;setCubeLutTarget(target);setPanel('cubeLut');
    }catch(failure){error(failure);}
  };
  const openAnaglyph=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const editor=api.current,elements=editor.getSceneElements(),photo=replaceablePhoto(elements,id),file=editor.getFiles()[photo.fileId];if(!file)throw Error('The embedded stereo view is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use stereo views at least 8 pixels on each side.');
      const pairs=[],seen=new Set([photo.fileId]);elements.forEach((element,index)=>{if(element.id===id||element.type!=='image')return;try{const candidate=replaceablePhoto(elements,element.id),copy=editor.getFiles()[candidate.fileId];if(!copy||seen.has(candidate.fileId))return;const info=photoBytes(copy.dataURL);if(info.width===width&&info.height===height){seen.add(candidate.fileId);pairs.push({id:candidate.id,dataURL:copy.dataURL,label:`Photo ${index+1} · ${width} × ${height}`});}}catch{}});
      if(!pairs.length)throw Error('Add another distinct visible, unlocked, ungrouped stereo view with matching embedded pixel dimensions.');
      if(anaglyphOwner.current)anaglyphOwner.current.valid=false;
      const target={editor,photo,dataURL:file.dataURL,width,height,pairs,snapshot:encodeDraft(...scene()),valid:true};anaglyphOwner.current=target;setAnaglyphTarget(target);setPanel('anaglyph');
    }catch(failure){error(failure);}
  };
  const openSpriteSlice=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded sprite sheet is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a sheet at least 8 pixels on each side.');
      if(spriteSliceOwner.current)spriteSliceOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};spriteSliceOwner.current=target;setSpriteSliceTarget(target);setPanel('spriteSlice');
    }catch(failure){error(failure);}
  };
  const openChannelSplit=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded RGB source is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use an RGB texture at least 8 pixels on each side.');
      if(channelSplitOwner.current)channelSplitOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};channelSplitOwner.current=target;setChannelSplitTarget(target);setPanel('channelSplit');
    }catch(failure){error(failure);}
  };
  const openChannelPack=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const editor=api.current,elements=editor.getSceneElements(),photo=replaceablePhoto(elements,id),file=editor.getFiles()[photo.fileId];if(!file)throw Error('The embedded red-source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use channel maps at least 8 pixels on each side.');
      const maps=[],seen=new Set([photo.fileId]);elements.forEach((element,index)=>{if(element.id===id||element.type!=='image')return;try{const candidate=replaceablePhoto(elements,element.id),copy=editor.getFiles()[candidate.fileId];if(!copy||seen.has(candidate.fileId))return;const info=photoBytes(copy.dataURL);if(info.width===width&&info.height===height){seen.add(candidate.fileId);maps.push({id:candidate.id,dataURL:copy.dataURL,label:`Photo ${index+1} · ${width} × ${height}`});}}catch{}});
      if(maps.length<2)throw Error('Add two other distinct visible, unlocked, ungrouped photos with matching embedded pixel dimensions for Channel pack.');
      if(channelPackOwner.current)channelPackOwner.current.valid=false;
      const target={editor,photo,dataURL:file.dataURL,width,height,maps,label:`Photo ${elements.findIndex(element=>element.id===id)+1}`,snapshot:encodeDraft(...scene()),valid:true};channelPackOwner.current=target;setChannelPackTarget(target);setPanel('channelPack');
    }catch(failure){error(failure);}
  };
  const openPaletteImage=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a photo at least 8 pixels on each side for Palette image.');
      if(paletteImageOwner.current)paletteImageOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};paletteImageOwner.current=target;setPaletteImageTarget(target);setPanel('paletteImage');
    }catch(failure){error(failure);}
  };
  const openNormalMap=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a photo at least 8 pixels on each side for Normal map.');
      if(normalMapOwner.current)normalMapOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};normalMapOwner.current=target;setNormalMapTarget(target);setPanel('normalMap');
    }catch(failure){error(failure);}
  };
  const openSeamless=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a photo at least 8 pixels on each side for Texture tile.');
      if(seamlessOwner.current)seamlessOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};seamlessOwner.current=target;setSeamlessTarget(target);setPanel('seamless');
    }catch(failure){error(failure);}
  };
  const openDespeckle=id=>{
    if(busyRef.current||preservedDraft.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a photo at least 8 pixels on each side for Speckle cleanup.');
      if(despeckleOwner.current)despeckleOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};despeckleOwner.current=target;setDespeckleTarget(target);setPanel('despeckle');
    }catch(failure){error(failure);}
  };
  const openClone=id=>{
    if(busyRef.current)return;
    try{
      const photo=replaceablePhoto(api.current.getSceneElements(),id),file=api.current.getFiles()[photo.fileId];if(!file)throw Error('The embedded source photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL);if(width<8||height<8)throw Error('Use a photo at least 8 pixels on each side for Clone repair.');
      if(cloneOwner.current)cloneOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,snapshot:encodeDraft(...scene()),valid:true};cloneOwner.current=target;setCloneTarget(target);setPanel('clone');
    }catch(failure){error(failure);}
  };
  const openSurface = id => {
    if(busyRef.current)return;
    try {
      const elements=api.current.getSceneElements(),files=api.current.getFiles(),photo=replaceablePhoto(elements,id),file=files[photo.fileId];
      if(!file)throw Error('The embedded background photo is unavailable.');
      const {width,height}=photoBytes(file.dataURL),artworks=[];
      elements.forEach((element,index)=>{if(element.id===id||element.type!=='image'||element.isDeleted)return;try{const candidate=replaceablePhoto(elements,element.id),source=files[candidate.fileId];if(source){const info=photoBytes(source.dataURL);if(info.width>=8&&info.height>=8)artworks.push({photo:candidate,dataURL:source.dataURL,width:info.width,height:info.height,label:`Photo ${index+1}`});}}catch{}});
      if(surfaceOwner.current)surfaceOwner.current.valid=false;
      const target={editor:api.current,photo,dataURL:file.dataURL,width,height,artworks,snapshot:encodeDraft(...scene()),valid:true};surfaceOwner.current=target;setSurfaceTarget(target);setPanel('surface');
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
  const captureDimensions=()=>{
    const editor=api.current;if(!editor||busyRef.current||preservedDraft.current)throw Error('Finish the current drawing operation first.');
    const elements=editor.getSceneElements(),selected=editor.getAppState().selectedElementIds||{};
    if(!Object.values(selected).some(Boolean))throw Error('Select visible artwork on the canvas first.');
    const template=captureComponent(elements,selected,getCommonBounds),ids=new Set(template.map(element=>element.id)),originals=elements.filter(element=>ids.has(element.id));
    if(originals.length>50||originals.some(element=>element.type==='frame'||element.frameId||element.opacity===0||element.customData?.nobleHidden))throw Error('Select up to 50 visible objects outside frames. Bound labels are included.');
    const bounds=getCommonBounds(originals),files=filesForElements(template,editor.getFiles());
    if(dimensionOwner.current)dimensionOwner.current.valid=false;
    const owner={valid:true,snapshot:encodeDraft(...scene())};dimensionOwner.current=owner;
    return{editor,existingCount:elements.length,bounds:[...bounds],elements:template.map(element=>({...element,x:element.x+bounds[0],y:element.y+bounds[1]})),files,ids,
      current:()=>owner.valid&&mounted.current&&api.current===editor&&!busyRef.current&&!preservedDraft.current&&encodeDraft(...scene())===owner.snapshot};
  };
  const insertDimensions=proposal=>{
    proposal.current();const {source,elements}=proposal,editor=source.editor,group=makeObjectId(),created=elements.map(element=>({...element,groupIds:[group]}));
    const merged=[...editor.getSceneElementsIncludingDeleted(),...created];encodeDraft(merged,editor.getAppState(),editor.getFiles());proposal.current();
    publishPhotoScene(editor,merged,{...editor.getAppState(),selectedElementIds:Object.fromEntries(created.map(element=>[element.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null},editor.getFiles());
    setPanel(null);setStatus('Editable dimension callouts inserted. Original kept; Undo removes this static snapshot.');
    const ids=new Set([...source.ids,...created.map(element=>element.id)]);requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(editor.getSceneElements().filter(element=>ids.has(element.id)),{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
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
  const insertDocument=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!documentTarget?.valid||!framingCurrent(documentTarget))throw Error('The source photo or drawing changed. Open Document cleanup again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([documentTarget.photo]);photoTarget.current=null;const ownership={prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current),current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The document copy was not inserted. The original is kept; check the source and available storage.');
  };
  const insertFrequency=async(proposal,requireCurrent)=>{if(proposal.kind==='split')return insertFrequencyKit(proposal,requireCurrent);const current=()=>{requireCurrent();if(!frequencyTarget?.valid||!framingCurrent(frequencyTarget))throw Error('The photos or drawing changed. Open Frequency separation again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');const partner=frequencyTarget.partners.find(p=>p.id===proposal.partnerId);if(!partner||proposal.blobs?.length!==1)throw Error('Choose and preview both frequency layers again.');const[,top,right]=getCommonBounds([frequencyTarget.photo,partner.photo]),baseFile=proposal.swap?partner.photo.fileId:frequencyTarget.photo.fileId,detailFile=proposal.swap?frequencyTarget.photo.fileId:partner.photo.fileId;photoTarget.current=null;const ownership={prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current),current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,customData:{nobleFrequency:{role:'combined',width:proposal.width,height:proposal.height,baseFile,detailFile,convention:'noble-frequency-v1',formula:'base+2*(detail-128)'}}};if(!await addPhoto(proposal.blobs[0],false,{x:right+32,y:top},ownership))throw Error('The recombined copy was not inserted. Originals kept; check storage or source.');};
  const insertGradient=async(proposal,requireCurrent)=>{
    const destination=gradientTarget?.references.find(p=>p.id===proposal.destinationId),current=()=>{requireCurrent();if(!destination||!gradientTarget?.valid||!framingCurrent(gradientTarget))throw Error('The photos or drawing changed. Open Gradient paste again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([gradientTarget.photo,destination.photo]);photoTarget.current=null;const ownership={prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current),current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,customData:{nobleGradient:{sourceFile:gradientTarget.photo.fileId,destinationFile:destination.photo.fileId,sourceWidth:proposal.sourceWidth,sourceHeight:proposal.sourceHeight,width:proposal.width,height:proposal.height,rect:[...proposal.rect],placement:[...proposal.placement],iterations:[...proposal.iterations],residuals:[...proposal.residuals],tolerance:proposal.tolerance,solutionErrorBound:proposal.solutionErrorBound,clippedChannels:proposal.clippedChannels,convention:proposal.convention,alpha:'white-composite-opaque'}}};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The gradient-paste copy was not inserted. Both originals kept; check storage or source.');
  };
  const insertStereogram=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!stereogramTarget?.valid||!framingCurrent(stereogramTarget))throw Error('The source photo or drawing changed. Open Depth stereogram again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([stereogramTarget.photo]);photoTarget.current=null;const ownership={prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current),current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,customData:{nobleStereogram:{sourceFile:stereogramTarget.photo.fileId,width:proposal.width,height:proposal.height,period:proposal.period,seed:proposal.seed,invert:proposal.invert,mu:'1/3',virtualEyePixels:proposal.period*2,nearSpacing:stereogramSeparation(DEPTH_UNITS,proposal.period),constraints:proposal.constraints,occluded:proposal.occluded,clippedPairs:proposal.clippedPairs,components:proposal.components,depth:'encoded-luma-invert-before-alpha-over-black',png:'grayscale-1bit-stored',convention:proposal.convention}}};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The stereogram copy was not inserted. Original kept; check storage or source.');
  };
  const insertCarve=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!carveTarget?.valid||!framingCurrent(carveTarget))throw Error('The source photo or drawing changed. Open Content-aware resize again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([carveTarget.photo]);photoTarget.current=null;const ownership={prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current),current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,customData:{nobleCarve:{sourceFile:carveTarget.photo.fileId,sourceWidth:proposal.sourceWidth,sourceHeight:proposal.sourceHeight,width:proposal.width,height:proposal.height,axis:proposal.axis,remove:proposal.remove,protect:proposal.protect&&[...proposal.protect],convention:proposal.convention}}};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The content-aware copy was not inserted. Original kept; check storage or source.');
  };
  const insertRegistration=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!registrationTarget?.valid||!framingCurrent(registrationTarget))throw Error('The source photo or drawing changed. Open Photo registration again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const reference=registrationTarget.references.find(p=>p.id===proposal.referenceId);if(!reference)throw Error('The registration reference changed. Preview again.');const [,top,right]=getCommonBounds([registrationTarget.photo,reference.photo]);photoTarget.current=null;const ownership={prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current),current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,customData:{nobleRegistration:{version:1,sourceFile:registrationTarget.photo.fileId,referenceFile:reference.photo.fileId,sourceWidth:proposal.sourceWidth,sourceHeight:proposal.sourceHeight,width:proposal.width,height:proposal.height,sourcePoints:proposal.sourcePoints.map(p=>[...p]),referencePoints:proposal.referencePoints.map(p=>[...p]),matrix:[...proposal.matrix],mirrored:proposal.mirrored,coordinates:'pixel-centers'}}};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The registration copy was not inserted. The original is kept; check the source and available storage.');
  };
  const insertRadial=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!radialTarget?.valid||!framingCurrent(radialTarget))throw Error('The source photo or drawing changed. Open Radial lens again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([radialTarget.photo]);photoTarget.current=null;const ownership={prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current),current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The radial copy was not inserted. The original is kept; check the source and available storage.');
  };
  const insertChroma=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!chromaTarget?.valid||!framingCurrent(chromaTarget))throw Error('The source photo or drawing changed. Open Chroma key again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([chromaTarget.photo]);photoTarget.current=null;const ownership={prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current),current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The keyed copy was not inserted. The original is kept; check the source and available storage.');
  };
  const insertRelight=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!relightTarget?.valid||!framingCurrent(relightTarget)||!relightTarget.maps.some(p=>p.id===proposal.normalId))throw Error('The source photos or drawing changed. Open Normal-map relight again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([relightTarget.photo]);photoTarget.current=null;const ownership={prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current),current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The relit copy was not inserted. Both originals are kept; check the source and available storage.');
  };
  const insertBlend=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!blendTarget?.valid||!framingCurrent(blendTarget)||!blendTarget.partners.some(p=>p.id===proposal.partnerId))throw Error('The source photos or drawing changed. Open Photo blend again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([blendTarget.photo]);photoTarget.current=null;const ownership={prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current),current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The blended copy was not inserted. Both originals are kept; check the source and available storage.');
  };
  const insertDisplacement=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!displacementTarget?.valid||!framingCurrent(displacementTarget)||!displacementTarget.maps.some(mask=>mask.id===proposal.mapId))throw Error('The source photos or drawing changed. Open Displacement map again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([displacementTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The warped copy was not inserted. Both originals are kept; check the source and available storage.');
  };
  const insertLuminanceMask=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!luminanceMaskTarget?.valid||!framingCurrent(luminanceMaskTarget)||!luminanceMaskTarget.masks.some(mask=>mask.id===proposal.maskId))throw Error('The source photos or drawing changed. Open Photo mask again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([luminanceMaskTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The masked copy was not inserted. Both originals are kept; check the source and available storage.');
  };
  const insertRedaction=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!redactionTarget?.valid||!framingCurrent(redactionTarget))throw Error('The source photo or drawing changed. Open Privacy blocks again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([redactionTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The redacted copy was not inserted. Your original is kept; check the source and available storage.');
  };
  const useExactPhotoImage=async(proposal,requireCurrent,target,label)=>{
    requireCurrent();if(!target?.valid||!framingCurrent(target))throw Error(`The source photo or drawing changed. Open ${label} again.`);
    if(!beginBusy())throw Error('Finish the current drawing operation first.');const editor=api.current,attempt={};pendingImage.current=attempt;
    const current=()=>{requireCurrent();if(!mounted.current||pendingImage.current!==attempt||api.current!==editor||!target.valid||!framingCurrent(target))throw Error('The image source or drawing changed. Preview again.');};
    try{
      const snapshot={current:candidate=>candidate===editor&&framingCurrent(target)};captureOutputSelection(editor,snapshot);
      if(!save(false))throw Error('The drawing could not be saved. Keep the current preview and try again.');
      const value=await readExactPhotoPng(proposal.blob,current);current();
      if(typeof window.HonorableDrawing?.usePng!=='function')throw Error('Open Noble on Android to use the exact PNG.');
      if(window.HonorableDrawing.usePng(value)===false)throw Error('The drawing is not active or another operation is running. Try again.');
      if(pendingImage.current===attempt)setStatus(`Exact ${label} PNG sent to the photo editor without padding or resizing. Export a PNG copy there.`);
    }catch(failure){if(pendingImage.current===attempt){pendingImage.current=null;finishOutput();}throw failure;}
  };
  const useSeamlessImage=(proposal,current)=>useExactPhotoImage(proposal,current,seamlessTarget,'texture tile');
  const useNormalMapImage=(proposal,current)=>useExactPhotoImage(proposal,current,normalMapTarget,'normal map');
  const useIslandsImage=(proposal,current)=>useExactPhotoImage(proposal,current,islandsTarget,'separated item');
  const useAnnulusImage=(proposal,current)=>useExactPhotoImage(proposal,current,annulusTarget,'straight circular band');
  const insertAnnulus=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!annulusTarget?.valid||!framingCurrent(annulusTarget))throw Error('The circular artwork or drawing changed. Open Unwrap circular band again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([annulusTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current)};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The straight band was not inserted. The original is kept; check source and available storage.');
  };
  const useAtlasImage=(proposal,current)=>useExactPhotoImage(proposal,current,atlasTarget,'texture atlas');
  const insertAtlas=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!atlasTarget?.valid||!framingCurrent(atlasTarget))throw Error('The atlas photos or drawing changed. Open Texture atlas again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([atlasTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current),customData:{nobleAtlas:JSON.parse(JSON.stringify(proposal.map))}};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The atlas and map were not inserted. All originals are kept; check source and available storage.');
  };
  const usePanoramaImage=(proposal,current)=>useExactPhotoImage(proposal,current,panoramaTarget,'panorama view');
  const useReferenceToneImage=(proposal,current)=>useExactPhotoImage(proposal,current,referenceToneTarget,'reference tones');
  const useNineSliceImage=(proposal,current)=>useExactPhotoImage(proposal,current,nineSliceTarget,'nine-slice artwork');
  const useCubeLutImage=(proposal,current)=>useExactPhotoImage(proposal,current,cubeLutTarget,'Cube LUT grade');
  const useAnaglyphImage=(proposal,current)=>useExactPhotoImage(proposal,current,anaglyphTarget,'anaglyph');
  const useSpriteFrameImage=(proposal,current)=>useExactPhotoImage(proposal,current,spriteSliceTarget,'sprite frame');
  const useChannelSplitImage=(proposal,current)=>useExactPhotoImage(proposal,current,channelSplitTarget,'extracted channel map');
  const useChannelPackImage=(proposal,current)=>useExactPhotoImage(proposal,current,channelPackTarget,'channel pack');
  const usePaletteImage=(proposal,current)=>useExactPhotoImage(proposal,current,paletteImageTarget,'palette image');
  const useDistanceFieldImage=(proposal,current)=>useExactPhotoImage(proposal,current,distanceFieldTarget,'distance field');
  const insertDistanceField=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!distanceFieldTarget?.valid||!framingCurrent(distanceFieldTarget))throw Error('The source photo or drawing changed. Open Distance field again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([distanceFieldTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current)};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The distance-field copy was not inserted. Your original is kept; check the source and available storage.');
  };
  const insertPanorama=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!panoramaTarget?.valid||!framingCurrent(panoramaTarget))throw Error('The panorama or drawing changed. Open Panorama view again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([panoramaTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current)};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The panorama view was not inserted. Your original is kept; check the source and available storage.');
  };
  const insertReferenceTone=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!referenceToneTarget?.valid||!framingCurrent(referenceToneTarget))throw Error('The source photos or drawing changed. Open Reference tones again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([referenceToneTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current)};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The reference-tone copy was not inserted. Your originals are kept; check the sources and available storage.');
  };
  const insertNineSlice=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!nineSliceTarget?.valid||!framingCurrent(nineSliceTarget))throw Error('The artwork or drawing changed. Open Nine-slice resize again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([nineSliceTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current)};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The nine-slice copy was not inserted. Your original is kept; check the source and available storage.');
  };
  const insertCubeLut=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!cubeLutTarget?.valid||!framingCurrent(cubeLutTarget))throw Error('The source photo or drawing changed. Open Cube LUT again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([cubeLutTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current)};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The LUT-graded copy was not inserted. Your original is kept; check the source and available storage.');
  };
  const insertAnaglyph=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!anaglyphTarget?.valid||!framingCurrent(anaglyphTarget))throw Error('The stereo views or drawing changed. Open Anaglyph again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([anaglyphTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current)};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The anaglyph copy was not inserted. Your originals are kept; check the sources and available storage.');
  };
  const insertAscii=(result,requireCurrent)=>{
    const editor=api.current,current=()=>{requireCurrent();if(busyRef.current||!mounted.current||api.current!==editor||!asciiTarget?.valid||!framingCurrent(asciiTarget)||preservedDraft.current)throw Error('The photo or drawing changed. Open ASCII art again.');};current();if(!result.visible)throw Error('Only visible ASCII artwork can be inserted.');const rebuilt=asciiArtwork(result.title,result.proof);if(!rebuilt.visible)throw Error('Only visible ASCII artwork can be inserted.');if(JSON.stringify(rebuilt.plan)!==JSON.stringify(result.plan))throw Error('The ascii review changed. Preview again.');const previous=editor.getSceneElementsIncludingDeleted(),state=editor.getAppState(),[,top,right]=getCommonBounds([asciiTarget.photo]),group=makeObjectId();
    const created=convertToExcalidrawElements(rebuilt.plan.map(p=>({...p,x:p.x+right+48,y:p.y+top}))).map((e,i)=>({...e,customData:{...e.customData,...rebuilt.plan[i].customData},groupIds:[group]})),elements=[...previous,...created].map(e=>newElementWith(e,{},true)),next={...state,selectedElementIds:Object.fromEntries(created.map(e=>[e.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null};encodeDraft(elements,next,editor.getFiles());current();try{publishPhotoScene(editor,elements,next,editor.getFiles());}catch(failure){if(failure?.name==='QuotaExceededError')throw Error('The ascii was not inserted. Check local storage. Original artwork kept.');throw failure;}asciiTarget.valid=false;setPanel(null);setStatus('Editable ascii chart inserted. Original kept; one Undo removes the group.');requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
  };
  const insertStitch=(result,requireCurrent)=>{
    const editor=api.current,current=()=>{requireCurrent();if(busyRef.current||!mounted.current||api.current!==editor||!stitchTarget?.valid||!framingCurrent(stitchTarget)||preservedDraft.current)throw Error('The photo or drawing changed. Open Stitch chart again.');};current();const rebuilt=stitchArtwork(result.title,result.proof,result.fabricCount);if(JSON.stringify(rebuilt.plan)!==JSON.stringify(result.plan))throw Error('The stitch review changed. Preview again.');const previous=editor.getSceneElementsIncludingDeleted(),state=editor.getAppState(),[,top,right]=getCommonBounds([stitchTarget.photo]),group=makeObjectId();
    const created=convertToExcalidrawElements(rebuilt.plan.map(p=>({...p,x:p.x+right+48,y:p.y+top}))).map((e,i)=>({...e,customData:{...e.customData,...rebuilt.plan[i].customData},groupIds:[group]})),elements=[...previous,...created].map(e=>newElementWith(e,{},true)),next={...state,selectedElementIds:Object.fromEntries(created.map(e=>[e.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null};encodeDraft(elements,next,editor.getFiles());current();try{publishPhotoScene(editor,elements,next,editor.getFiles());}catch(failure){if(failure?.name==='QuotaExceededError')throw Error('The stitch was not inserted. Check local storage. Original artwork kept.');throw failure;}stitchTarget.valid=false;setPanel(null);setStatus('Editable stitch chart inserted. Original kept; one Undo removes the chart.');requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
  };
  const insertHalftone=(result,requireCurrent)=>{
    const editor=api.current,current=()=>{requireCurrent();if(busyRef.current||!mounted.current||api.current!==editor||!halftoneTarget?.valid||!framingCurrent(halftoneTarget)||preservedDraft.current)throw Error('The photo or drawing changed. Open One-ink halftone again.');};current();const rebuilt=halftoneArtwork(result.title,result.proof);if(JSON.stringify(rebuilt.plan)!==JSON.stringify(result.plan))throw Error('The halftone review changed. Preview again.');const previous=editor.getSceneElementsIncludingDeleted(),state=editor.getAppState(),[,top,right]=getCommonBounds([halftoneTarget.photo]),group=makeObjectId();
    const created=convertToExcalidrawElements(rebuilt.plan.map(p=>({...p,x:p.x+right+48,y:p.y+top}))).map((e,i)=>({...e,customData:{...e.customData,...rebuilt.plan[i].customData},groupIds:[group]})),elements=[...previous,...created].map(e=>newElementWith(e,{},true)),next={...state,selectedElementIds:Object.fromEntries(created.map(e=>[e.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null};encodeDraft(elements,next,editor.getFiles());current();try{publishPhotoScene(editor,elements,next,editor.getFiles());}catch(failure){if(failure?.name==='QuotaExceededError')throw Error('The halftone was not inserted. Check local storage. Original artwork kept.');throw failure;}halftoneTarget.valid=false;setPanel(null);setStatus('Editable one-ink halftone inserted. Original kept; one Undo removes the plate.');requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
  };
  const insertContours=(result,requireCurrent)=>{
    const editor=api.current,current=()=>{requireCurrent();if(busyRef.current||!mounted.current||api.current!==editor||!contoursTarget?.valid||!framingCurrent(contoursTarget)||preservedDraft.current)throw Error('The height artwork or drawing changed. Open Height contours again.');};current();const rebuilt=contourArtwork(result.title,result.proof);if(JSON.stringify(rebuilt.plan)!==JSON.stringify(result.plan))throw Error('The contour review changed. Preview again.');const previous=editor.getSceneElementsIncludingDeleted(),state=editor.getAppState(),[,top,right]=getCommonBounds([contoursTarget.photo]),outer=makeObjectId(),groups=new Map(result.proof.levels.map(l=>[l,makeObjectId()]));
    const created=convertToExcalidrawElements(rebuilt.plan.map(p=>({...p,x:p.x+right+48,y:p.y+top}))).map((e,i)=>{const meta=rebuilt.plan[i].customData;return{...e,...(meta?{customData:{...e.customData,...meta}}:{}),groupIds:meta?[groups.get(meta.nobleContourLevel),outer]:[outer]};}),elements=[...previous,...created].map(e=>newElementWith(e,{},true)),next={...state,selectedElementIds:Object.fromEntries(created.map(e=>[e.id,true])),selectedGroupIds:{[outer]:true},editingGroupId:null};encodeDraft(elements,next,editor.getFiles());current();try{publishPhotoScene(editor,elements,next,editor.getFiles());}catch(failure){if(failure?.name==='QuotaExceededError')throw Error('The contour map was not inserted. Check local storage. Original artwork kept.');throw failure;}contoursTarget.valid=false;setPanel(null);setStatus('Editable height contours inserted in nested level groups. Original kept; one Undo removes the map.');requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
  };
  const insertFrequencyKit=async(proposal,requireCurrent)=>{
    const editor=api.current,current=()=>{requireCurrent();if(!mounted.current||api.current!==editor||!frequencyTarget?.valid||!framingCurrent(frequencyTarget)||preservedDraft.current)throw Error('The artwork or drawing changed. Open Frequency separation again.');};current();if(!beginBusy())throw Error('Finish the current drawing operation first.');
    try{if(proposal.kind!=='split'||!Array.isArray(proposal.blobs)||proposal.blobs.length!==2)throw Error('Preview both frequency layers again.');const previous=editor.getSceneElementsIncludingDeleted(),state=editor.getAppState();if(previous.filter(e=>!e.isDeleted).length+proposal.blobs.length*2>MAX_ELEMENTS)throw Error('Remove objects before inserting both kit PNGs and labels.');const copies=[];for(let i=0;i<proposal.blobs.length;i++){const file=await prepareExactPhotoCopy(proposal.blobs[i],()=>true,current);current();const info=photoBytes(file.dataURL);if(info.width!==proposal.width||info.height!==proposal.height)throw Error('A frequency-layer pixel grid changed. Preview again.');copies.push(file);}current();const files=mergePhotoFiles(editor.getFiles(),Object.fromEntries(copies.map(f=>[f.id,f]))),[,top,right]=getCommonBounds([frequencyTarget.photo]),side=Math.min(240,Math.max(80,state.width*.4)),group=makeObjectId(),created=restoreElements(frequencyLayerPlan(proposal,copies.map(f=>f.id),frequencyTarget.photo.fileId,right+32,top,side).map(e=>({...e,id:makeObjectId()})),null).map(e=>({...e,groupIds:[group]})),elements=[...previous,...created].map(e=>newElementWith(e,{},true)),nextState={...state,selectedElementIds:Object.fromEntries(created.map(e=>[e.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null};encodeDraft(elements,nextState,files);current();publishPhotoScene(editor,elements,nextState,files);setPanel(null);setStatus(`${copies.length} exact kit PNGs and labels inserted. Original kept; one Undo removes the set.`);requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
    }catch(failure){if(failure?.name==='QuotaExceededError'||failure?.message==='Drawing draft is too large.')throw Error('The frequency kit was not inserted. Check storage or remove artwork. Original kept.');throw failure;}finally{finishBusy();}
  };
  const insertBarrier=async(proposal,requireCurrent)=>{
    const editor=api.current,current=()=>{requireCurrent();if(!mounted.current||api.current!==editor||!barrierTarget?.valid||!framingCurrent(barrierTarget)||preservedDraft.current)throw Error('The artwork or drawing changed. Open Barrier-grid kit again.');};current();if(!beginBusy())throw Error('Finish the current drawing operation first.');
    try{const settings=barrierSettings(proposal.width,proposal.height,proposal.columns,proposal.rows,proposal.margin,proposal.gap,proposal.stripe,proposal.axis);if(!Array.isArray(proposal.blobs)||proposal.blobs.length!==2||settings.frameWidth!==proposal.frameWidth||settings.frameHeight!==proposal.frameHeight)throw Error('Preview the complete barrier kit again.');const previous=editor.getSceneElementsIncludingDeleted(),state=editor.getAppState();if(previous.filter(e=>!e.isDeleted).length+proposal.blobs.length*2>MAX_ELEMENTS)throw Error('Remove objects before inserting both kit PNGs and labels.');const copies=[];for(let i=0;i<proposal.blobs.length;i++){const file=await prepareExactPhotoCopy(proposal.blobs[i],()=>true,current);current();const info=photoBytes(file.dataURL);if(info.width!==settings.frameWidth||info.height!==settings.frameHeight)throw Error('A barrier-kit pixel grid changed. Preview again.');copies.push(file);}current();const files=mergePhotoFiles(editor.getFiles(),Object.fromEntries(copies.map(f=>[f.id,f]))),[,top,right]=getCommonBounds([barrierTarget.photo]),side=Math.min(240,Math.max(80,state.width*.4)),group=makeObjectId(),created=restoreElements(barrierFramePlan(settings,copies.map(f=>f.id),right+32,top,side).map(e=>({...e,id:makeObjectId()})),null).map(e=>({...e,groupIds:[group]})),elements=[...previous,...created].map(e=>newElementWith(e,{},true)),nextState={...state,selectedElementIds:Object.fromEntries(created.map(e=>[e.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null};encodeDraft(elements,nextState,files);current();publishPhotoScene(editor,elements,nextState,files);setPanel(null);setStatus(`${copies.length} exact kit PNGs and labels inserted. Original kept; one Undo removes the set.`);requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
    }catch(failure){if(failure?.name==='QuotaExceededError'||failure?.message==='Drawing draft is too large.')throw Error('The barrier kit was not inserted. Check storage or remove artwork. Original kept.');throw failure;}finally{finishBusy();}
  };
  const insertVision=async(proposal,requireCurrent)=>{
    const editor=api.current,current=()=>{requireCurrent();if(!mounted.current||api.current!==editor||!visionTarget?.valid||!framingCurrent(visionTarget)||preservedDraft.current)throw Error('The artwork or drawing changed. Open Color-vision proof sheet again.');};current();if(!beginBusy())throw Error('Finish the current drawing operation first.');
    try{visionSettings(proposal.width,proposal.height,proposal.backdrop);if(!Array.isArray(proposal.blobs)||proposal.blobs.length!==4||proposal.convention!==VISION_CONVENTION||proposal.severity!==1||JSON.stringify(proposal.views?.map(view=>view.id))!==JSON.stringify(VISION_MODELS.map(view=>view.id)))throw Error('Preview the complete color-vision sheet again.');const previous=editor.getSceneElementsIncludingDeleted(),state=editor.getAppState();if(previous.filter(e=>!e.isDeleted).length+proposal.blobs.length*2>MAX_ELEMENTS)throw Error('Remove objects before inserting all color-vision views and labels.');const copies=[];for(let i=0;i<proposal.blobs.length;i++){const file=await prepareExactPhotoCopy(proposal.blobs[i],()=>true,current);current();const info=photoBytes(file.dataURL);if(info.width!==proposal.width||info.height!==proposal.height)throw Error('A color-vision pixel grid changed. Preview again.');copies.push(file);}current();const files=mergePhotoFiles(editor.getFiles(),Object.fromEntries(copies.map(f=>[f.id,f]))),[,top,right]=getCommonBounds([visionTarget.photo]),side=Math.min(240,Math.max(80,state.width*.4)),group=makeObjectId(),created=restoreElements(visionSheetPlan(proposal.width,proposal.height,proposal.backdrop,copies.map(f=>f.id),right+32,top).map(e=>e.type==='image'?{...e,customData:{...e.customData,nobleVision:{...e.customData.nobleVision,sourceFile:visionTarget.photo.fileId}}}:e).map(e=>({...e,id:makeObjectId()})),null).map(e=>({...e,groupIds:[group]})),elements=[...previous,...created].map(e=>newElementWith(e,{},true)),nextState={...state,selectedElementIds:Object.fromEntries(created.map(e=>[e.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null};encodeDraft(elements,nextState,files);current();publishPhotoScene(editor,elements,nextState,files);setPanel(null);setStatus(`${copies.length} exact color-vision views and labels inserted. Original kept; one Undo removes the set.`);requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
    }catch(failure){if(failure?.name==='QuotaExceededError'||failure?.message==='Drawing draft is too large.')throw Error('The color-vision views were not inserted. Check storage or remove artwork. Original kept.');throw failure;}finally{finishBusy();}
  };
  const insertCube=async(proposal,requireCurrent)=>{
    const editor=api.current,current=()=>{requireCurrent();if(!mounted.current||api.current!==editor||!cubeTarget?.valid||!framingCurrent(cubeTarget)||preservedDraft.current)throw Error('The artwork or drawing changed. Open Cube faces again.');};current();if(!beginBusy())throw Error('Finish the current drawing operation first.');
    try{if(!Array.isArray(proposal.blobs)||proposal.blobs.length!==6||!Number.isInteger(proposal.size)||proposal.size<8||proposal.size>384||proposal.convention!==CUBE_CONVENTION||JSON.stringify(proposal.faces?.map(f=>f.id))!==JSON.stringify(CUBE_FACES.map(f=>f.id)))throw Error('Preview the complete cube set again.');const previous=editor.getSceneElementsIncludingDeleted(),state=editor.getAppState();if(previous.filter(e=>!e.isDeleted).length+proposal.blobs.length*2>MAX_ELEMENTS)throw Error('Remove objects before inserting all cube faces and labels.');const copies=[];for(let i=0;i<proposal.blobs.length;i++){const file=await prepareExactPhotoCopy(proposal.blobs[i],()=>true,current);current();const info=photoBytes(file.dataURL);if(info.width!==proposal.size||info.height!==proposal.size)throw Error('A cube-face pixel grid changed. Preview again.');copies.push(file);}current();const files=mergePhotoFiles(editor.getFiles(),Object.fromEntries(copies.map(f=>[f.id,f]))),[,top,right]=getCommonBounds([cubeTarget.photo]),side=Math.min(240,Math.max(80,state.width*.4)),group=makeObjectId(),created=restoreElements(cubeFramePlan(proposal.size,copies.map(f=>f.id),right+32,top,side).map(e=>({...e,id:makeObjectId()})),null).map(e=>({...e,groupIds:[group]})),elements=[...previous,...created].map(e=>newElementWith(e,{},true)),nextState={...state,selectedElementIds:Object.fromEntries(created.map(e=>[e.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null};encodeDraft(elements,nextState,files);current();publishPhotoScene(editor,elements,nextState,files);setPanel(null);setStatus(`${copies.length} exact cube faces and labels inserted. Original kept; one Undo removes the set.`);requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
    }catch(failure){if(failure?.name==='QuotaExceededError'||failure?.message==='Drawing draft is too large.')throw Error('The cube faces were not inserted. Check storage or remove artwork. Original kept.');throw failure;}finally{finishBusy();}
  };
  const insertIslands=async(proposal,requireCurrent)=>{
    const editor=api.current,current=()=>{requireCurrent();if(!mounted.current||api.current!==editor||!islandsTarget?.valid||!framingCurrent(islandsTarget)||preservedDraft.current)throw Error('The artwork or drawing changed. Open Separate transparent items again.');};current();if(!beginBusy())throw Error('Finish the current drawing operation first.');
    try{if(!Array.isArray(proposal.blobs)||proposal.blobs.length<2||proposal.blobs.length>6||proposal.frames?.length!==proposal.blobs.length)throw Error('Preview the complete item set again.');const previous=editor.getSceneElementsIncludingDeleted(),state=editor.getAppState();if(previous.filter(e=>!e.isDeleted).length+proposal.blobs.length*2>MAX_ELEMENTS)throw Error('Remove objects before inserting all items and labels.');const copies=[];for(let i=0;i<proposal.blobs.length;i++){const file=await prepareExactPhotoCopy(proposal.blobs[i],()=>true,current);current();const info=photoBytes(file.dataURL);if(info.width!==proposal.frames[i].width||info.height!==proposal.frames[i].height)throw Error('An item pixel grid changed. Preview again.');copies.push(file);}current();const files=mergePhotoFiles(editor.getFiles(),Object.fromEntries(copies.map(f=>[f.id,f]))),[,top,right]=getCommonBounds([islandsTarget.photo]),side=Math.min(240,Math.max(80,state.width*.4)),group=makeObjectId(),created=restoreElements(islandFramePlan(proposal.frames,copies.map(f=>f.id),right+32,top,side).map(e=>({...e,id:makeObjectId()})),null).map(e=>({...e,groupIds:[group]})),elements=[...previous,...created].map(e=>newElementWith(e,{},true)),nextState={...state,selectedElementIds:Object.fromEntries(created.map(e=>[e.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null};encodeDraft(elements,nextState,files);current();publishPhotoScene(editor,elements,nextState,files);setPanel(null);setStatus(`${copies.length} exact separated items and labels inserted. Original kept; one Undo removes the set.`);requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
    }catch(failure){if(failure?.name==='QuotaExceededError'||failure?.message==='Drawing draft is too large.')throw Error('The separated items were not inserted. Check storage or remove artwork. Original kept.');throw failure;}finally{finishBusy();}
  };
  const insertSpriteFrames=async(proposal,requireCurrent)=>{
    const editor=api.current,current=()=>{requireCurrent();if(!mounted.current||api.current!==editor||!spriteSliceTarget?.valid||!framingCurrent(spriteSliceTarget)||preservedDraft.current)throw Error('The sheet or drawing changed. Open Slice sprite sheet again.');};current();
    if(!beginBusy())throw Error('Finish the current drawing operation first.');
    try{
      if(!Array.isArray(proposal.blobs)||proposal.blobs.length<2||proposal.blobs.length>6)throw Error('Preview a complete set of 2–6 sprite frames again.');
      const previous=editor.getSceneElementsIncludingDeleted(),state=editor.getAppState();if(previous.filter(element=>!element.isDeleted).length+proposal.blobs.length*2>MAX_ELEMENTS)throw Error('Remove objects before inserting the complete frame set and labels.');
      const copies=[];for(const blob of proposal.blobs){const file=await prepareExactPhotoCopy(blob,()=>true,current);current();const info=photoBytes(file.dataURL);if(info.width!==proposal.width||info.height!==proposal.height)throw Error('A sprite-frame pixel grid changed. Preview again.');copies.push(file);}
      current();const files=mergePhotoFiles(editor.getFiles(),Object.fromEntries(copies.map(file=>[file.id,file]))),[,top,right]=getCommonBounds([spriteSliceTarget.photo]),side=Math.min(240,Math.max(80,state.width*.4)),group=makeObjectId();
      const created=restoreElements(spriteFramePlan(copies.map(file=>file.id),proposal.width,proposal.height,right+32,top,side).map(element=>({...element,id:makeObjectId()})),null).map(element=>({...element,groupIds:[group]}));
      const elements=[...previous,...created].map(element=>newElementWith(element,{},true)),nextState={...state,selectedElementIds:Object.fromEntries(created.map(element=>[element.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null};
      encodeDraft(elements,nextState,files);current();publishPhotoScene(editor,elements,nextState,files);setPanel(null);setStatus(`${copies.length} exact sprite frames and labels inserted together. Original sheet kept; one Undo removes the set.`);
      requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
    }catch(failure){if(failure?.name==='QuotaExceededError'||failure?.message==='Drawing draft is too large.')throw Error('The sprite frames were not inserted. Check available storage or remove artwork, then try again. Your sheet is kept.');throw failure;}finally{finishBusy();}
  };
  const insertChannelSplit=async(proposal,requireCurrent)=>{
    const editor=api.current,current=()=>{requireCurrent();if(!mounted.current||api.current!==editor||!channelSplitTarget?.valid||!framingCurrent(channelSplitTarget)||preservedDraft.current)throw Error('The source texture or drawing changed. Open Extract channels again.');};current();
    if(!beginBusy())throw Error('Finish the current drawing operation first.');
    try{
      if(!Array.isArray(proposal.blobs)||proposal.blobs.length!==3)throw Error('Preview all three channel maps again.');
      const previous=editor.getSceneElementsIncludingDeleted(),state=editor.getAppState();if(previous.filter(element=>!element.isDeleted).length+6>MAX_ELEMENTS)throw Error('Remove objects before inserting all three maps and labels.');
      const copies=[];for(const blob of proposal.blobs){const file=await prepareExactPhotoCopy(blob,()=>true,current);current();const info=photoBytes(file.dataURL);if(info.width!==channelSplitTarget.width||info.height!==channelSplitTarget.height)throw Error('A channel-map pixel grid changed. Preview again.');copies.push(file);}
      current();const files=mergePhotoFiles(editor.getFiles(),Object.fromEntries(copies.map(file=>[file.id,file]))),[,top,right]=getCommonBounds([channelSplitTarget.photo]),side=Math.min(480,Math.max(80,state.width*.75)),group=makeObjectId();
      const created=restoreElements(channelSplitPlan(copies.map(file=>file.id),channelSplitTarget.width,channelSplitTarget.height,right+32,top,side).map(element=>({...element,id:makeObjectId()})),null).map(element=>({...element,groupIds:[group]}));
      const elements=[...previous,...created].map(element=>newElementWith(element,{},true)),nextState={...state,selectedElementIds:Object.fromEntries(created.map(element=>[element.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null};
      encodeDraft(elements,nextState,files);current();publishPhotoScene(editor,elements,nextState,files);setPanel(null);setStatus('Three exact channel maps and labels inserted together. Original kept; one Undo removes the set.');
      requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
    }catch(failure){if(failure?.name==='QuotaExceededError'||failure?.message==='Drawing draft is too large.')throw Error('The channel maps were not inserted. Check available storage or remove artwork, then try again. Your original is kept.');throw failure;}finally{finishBusy();}
  };
  const insertChannelPack=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!channelPackTarget?.valid||!framingCurrent(channelPackTarget))throw Error('The channel maps or drawing changed. Open Channel pack again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([channelPackTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current)};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The channel-pack copy was not inserted. Your originals are kept; check the sources and available storage.');
  };
  const insertPaletteImage=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!paletteImageTarget?.valid||!framingCurrent(paletteImageTarget))throw Error('The source photo or drawing changed. Open Palette image again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([paletteImageTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current)};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The palette image copy was not inserted. Your original is kept; check the source and available storage.');
  };
  const insertNormalMap=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!normalMapTarget?.valid||!framingCurrent(normalMapTarget))throw Error('The source photo or drawing changed. Open Normal map again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([normalMapTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,prepare:(blob,accepts)=>prepareExactPhotoCopy(blob,accepts,current)};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The normal-map copy was not inserted. Your original is kept; check the source and available storage.');
  };
  const insertSeamless=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!seamlessTarget?.valid||!framingCurrent(seamlessTarget))throw Error('The source photo or drawing changed. Open Texture tile again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([seamlessTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy,prepare:(blob,accepts)=>prepareSeamlessCopy(blob,accepts,current)};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The texture tile was not inserted. Your original is kept; check the source and available storage.');
  };
  const insertDespeckle=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!despeckleTarget?.valid||!framingCurrent(despeckleTarget))throw Error('The source photo or drawing changed. Open Speckle cleanup again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([despeckleTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The cleaned copy was not inserted. Your original is kept; check the source and available storage.');
  };
  const insertClone=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!cloneTarget?.valid||!framingCurrent(cloneTarget))throw Error('The source photo or drawing changed. Open Clone repair again.');};current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([cloneTarget.photo]);photoTarget.current=null;const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The repaired copy was not inserted. Your original is kept; check the source and available storage.');
  };
  const insertSurface=async(proposal,requireCurrent)=>{
    const current=()=>{requireCurrent();if(!surfaceTarget?.valid||!framingCurrent(surfaceTarget)||api.current.getFiles()[proposal.artwork.photo.fileId]?.dataURL!==proposal.artwork.dataURL)throw Error('The source photos or drawing changed. Open Surface mockup again.');};
    current();if(busyRef.current)throw Error('Finish the current drawing operation first.');
    const [,top,right]=getCommonBounds([surfaceTarget.photo]);photoTarget.current=null;
    const ownership={current,owns:()=>{try{current();return true;}catch{return false;}},finish:finishBusy};
    if(!await addPhoto(proposal.blob,false,{x:right+32,y:top},ownership))throw Error('The mockup was not inserted. Both source photos are kept; check the drawing and available storage.');
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
        const image = target ? newElementWith(replaceablePhoto(previous, target.id), changes) : ownership?.customData ? newElementWith(added,{customData:ownership.customData}) : added;
        const elements = (target ? previous.map(element => element.id === target.id ? image : element) : [...previous, image]).map(element => newElementWith(element, {}, true));
        try { encodeDraft(elements, state, files); }
        catch (failure) { if (failure.message === 'Drawing draft is too large.') return false; throw failure; }
        proposed = { elements, image, files, info }; return true;
      };
      setStatus('Preparing a local photo copy…');
      if (ownership?.prepare) await ownership.prepare(source, accepts); else if (native) await prepareNativePhoto(source, accepts); else await prepareLocalPhoto(source, accepts);
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
  const capturePictureSource=()=>{if(!mounted.current||!api.current||busyRef.current||resetPending.current||preservedDraft.current)throw Error('Wait for the drawing to be ready.');if(pictureOwner.current)pictureOwner.current.valid=false;const source={editor:api.current,snapshot:encodeDraft(...scene()),valid:true};pictureOwner.current=source;return source;};
  const pictureCurrent=source=>{try{return Boolean(source?.valid&&pictureOwner.current===source&&mounted.current&&api.current===source.editor&&encodeDraft(...scene())===source.snapshot);}catch{return false;}};
  const captureJigsawSource=()=>{if(!mounted.current||!api.current||busyRef.current||resetPending.current||preservedDraft.current)throw Error('Wait for the drawing to be ready.');if(jigsawOwner.current)jigsawOwner.current.valid=false;const source={editor:api.current,snapshot:encodeDraft(...scene()),valid:true};jigsawOwner.current=source;return source;};
  const captureBoxNetSource=()=>{if(!mounted.current||!api.current||busyRef.current||resetPending.current||preservedDraft.current)throw Error('Wait for the drawing to be ready.');if(boxNetOwner.current)boxNetOwner.current.valid=false;const source={editor:api.current,snapshot:encodeDraft(...scene()),valid:true};boxNetOwner.current=source;return source;};
  const jigsawCurrent=source=>{try{return Boolean(source?.valid&&jigsawOwner.current===source&&mounted.current&&api.current===source.editor&&encodeDraft(...scene())===source.snapshot);}catch{return false;}};
  const boxNetCurrent=source=>{try{return Boolean(source?.valid&&boxNetOwner.current===source&&mounted.current&&api.current===source.editor&&encodeDraft(...scene())===source.snapshot);}catch{return false;}};
  const captureMazeSource=()=>{if(!mounted.current||!api.current||busyRef.current||resetPending.current||preservedDraft.current)throw Error('Wait for the drawing to be ready.');if(mazeOwner.current)mazeOwner.current.valid=false;const source={editor:api.current,snapshot:encodeDraft(...scene()),valid:true};mazeOwner.current=source;return source;};
  const mazeCurrent=source=>{try{return Boolean(source?.valid&&mazeOwner.current===source&&mounted.current&&api.current===source.editor&&encodeDraft(...scene())===source.snapshot);}catch{return false;}};
  const insertReviewedWorksheet=(result,source,isCurrent,rebuild,label)=>{
    const current=()=>{if(busyRef.current||!isCurrent(source))throw Error('The drawing changed. Preview the worksheet again.');};current();const rebuilt=rebuild(result);if(JSON.stringify(result.plan)!==JSON.stringify(rebuilt.plan))throw Error('The worksheet preview changed. Preview again.');const editor=api.current,previous=editor.getSceneElementsIncludingDeleted(),visible=previous.filter(e=>!e.isDeleted&&e.opacity!==0&&!e.customData?.nobleHidden),bounds=visible.length?getCommonBounds(visible):[0,0,-48,0],group=makeObjectId();
    const created=convertToExcalidrawElements(rebuilt.plan.map(shape=>({...shape,x:shape.x+bounds[2]+48,y:shape.y+bounds[1]}))).map(e=>({...e,groupIds:[group]})),elements=[...previous,...created].map(e=>newElementWith(e,{},true)),state={...editor.getAppState(),selectedElementIds:Object.fromEntries(created.map(e=>[e.id,true])),selectedGroupIds:{[group]:true},editingGroupId:null};encodeDraft(elements,state,editor.getFiles());current();try{publishPhotoScene(editor,elements,state,editor.getFiles());}catch(failure){if(failure.name==='QuotaExceededError')throw Error(`Not enough local storage for the ${label}. Existing artwork is kept.`);throw failure;}source.valid=false;setPanel(null);setStatus(`Editable ${label} inserted. Original artwork kept; one Undo removes the new group.`);requestAnimationFrame(()=>requestAnimationFrame(()=>{if(mounted.current&&api.current===editor)editor.scrollToContent(created,{fitToViewport:true,viewportZoomFactor:.85,canvasOffsets:{top:80,bottom:100,left:24,right:48}});}));
  };
  const insertJigsaw=(result,source)=>insertReviewedWorksheet(result,source,jigsawCurrent,r=>jigsawArtwork(r.options),'jigsaw guide');
  const insertBoxNet=(result,source)=>insertReviewedWorksheet(result,source,boxNetCurrent,r=>boxNetArtwork(r.options),'box net');
  const insertMaze=(result,source)=>insertReviewedWorksheet(result,source,mazeCurrent,r=>mazeWorksheet(r.options),'maze worksheet');
  const insertPicture=(result,source)=>insertReviewedWorksheet(result,source,pictureCurrent,r=>pictureWorksheet(r.options,r.proof),'picture-logic worksheet');
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
      const skeleton = kind === 'schedule' ? schedulePlan(scheduleTitle, scheduleRows) : kind === 'starter' ? starterPlan(starterStyle, starterHeadline, starterDetails, starterSignoff) : kind === 'table' ? tablePlan(tableTitle, tableRows) : kind === 'qr' ? qrPlan(qrText, qrCaption) : kind === 'barcode' ? barcodePlan(barcodeText, barcodeCaption) : kind === 'dependencies' ? dependencyPlan(dependencyTitle, dependencyRows) : kind === 'palette' ? palettePlan(api.current.getSceneElements(), paletteTitle) : kind === 'chart' ? chartPlan(layoutTitle, chartRows) : kind === 'calendar' ? calendarPlan(calendarMonth, mondayFirst) : storyboardPlan(storyTitle, storyBriefs);
      const state = api.current.getAppState(), previous = api.current.getSceneElementsIncludingDeleted();
      const x = state.width / 2 / state.zoom.value - state.scrollX, y = state.height / 2 / state.zoom.value - state.scrollY;
      let created = convertToExcalidrawElements(skeleton.map(shape => ({ ...shape, x: shape.x + x, y: shape.y + y })));
      if (kind === 'qr' || kind === 'barcode' || kind === 'starter' || kind === 'schedule') {
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
    <nav aria-label="Composition tools" style={{flexWrap:'wrap'}}>{['objects', 'arrange', 'layouts', 'model', 'projects'].map(value => <button key={value} data-panel-trigger={value} aria-expanded={panel === value} aria-controls="workspace-panel" onClick={() => showPanel(value)} disabled={busy || !ready}>{value[0].toUpperCase() + value.slice(1)}</button>)}</nav>
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
        onChange={(elements, state, files) => { if (resetPending.current) return; if(frequencyOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==frequencyOwner.current.snapshot)frequencyOwner.current.valid=false;}catch{frequencyOwner.current.valid=false;}} if(barrierOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==barrierOwner.current.snapshot)barrierOwner.current.valid=false;}catch{barrierOwner.current.valid=false;}} if(stereogramOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==stereogramOwner.current.snapshot)stereogramOwner.current.valid=false;}catch{stereogramOwner.current.valid=false;}} if(gradientOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==gradientOwner.current.snapshot)gradientOwner.current.valid=false;}catch{gradientOwner.current.valid=false;}} if(carveOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==carveOwner.current.snapshot)carveOwner.current.valid=false;}catch{carveOwner.current.valid=false;}} if(visionOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==visionOwner.current.snapshot)visionOwner.current.valid=false;}catch{visionOwner.current.valid=false;}} if(cubeOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==cubeOwner.current.snapshot)cubeOwner.current.valid=false;}catch{cubeOwner.current.valid=false;}} if(documentOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==documentOwner.current.snapshot)documentOwner.current.valid=false;}catch{documentOwner.current.valid=false;}} if(registrationOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==registrationOwner.current.snapshot)registrationOwner.current.valid=false;}catch{registrationOwner.current.valid=false;}} if(radialOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==radialOwner.current.snapshot)radialOwner.current.valid=false;}catch{radialOwner.current.valid=false;}} if(relightOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==relightOwner.current.snapshot)relightOwner.current.valid=false;}catch{relightOwner.current.valid=false;}} if(chromaOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==chromaOwner.current.snapshot)chromaOwner.current.valid=false;}catch{chromaOwner.current.valid=false;}} if(blendOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==blendOwner.current.snapshot)blendOwner.current.valid=false;}catch{blendOwner.current.valid=false;}} if(asciiOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==asciiOwner.current.snapshot)asciiOwner.current.valid=false;}catch{asciiOwner.current.valid=false;}} if(stitchOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==stitchOwner.current.snapshot)stitchOwner.current.valid=false;}catch{stitchOwner.current.valid=false;}} if(halftoneOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==halftoneOwner.current.snapshot)halftoneOwner.current.valid=false;}catch{halftoneOwner.current.valid=false;}} if(jigsawOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==jigsawOwner.current.snapshot)jigsawOwner.current.valid=false;}catch{jigsawOwner.current.valid=false;}} if(boxNetOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==boxNetOwner.current.snapshot)boxNetOwner.current.valid=false;}catch{boxNetOwner.current.valid=false;}} if(contoursOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==contoursOwner.current.snapshot)contoursOwner.current.valid=false;}catch{contoursOwner.current.valid=false;}} if(islandsOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==islandsOwner.current.snapshot)islandsOwner.current.valid=false;}catch{islandsOwner.current.valid=false;}} if(pictureOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==pictureOwner.current.snapshot)pictureOwner.current.valid=false;}catch{pictureOwner.current.valid=false;}} if(mazeOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==mazeOwner.current.snapshot)mazeOwner.current.valid=false;}catch{mazeOwner.current.valid=false;}} if(annulusOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==annulusOwner.current.snapshot)annulusOwner.current.valid=false;}catch{annulusOwner.current.valid=false;}} if(atlasOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==atlasOwner.current.snapshot)atlasOwner.current.valid=false;}catch{atlasOwner.current.valid=false;}} if(cutoutOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==cutoutOwner.current.snapshot)cutoutOwner.current.valid=false;}catch{cutoutOwner.current.valid=false;}} if(surfaceOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==surfaceOwner.current.snapshot)surfaceOwner.current.valid=false;}catch{surfaceOwner.current.valid=false;}} if(dimensionOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==dimensionOwner.current.snapshot)dimensionOwner.current.valid=false;}catch{dimensionOwner.current.valid=false;}} if(panoramaOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==panoramaOwner.current.snapshot)panoramaOwner.current.valid=false;}catch{panoramaOwner.current.valid=false;}} if(referenceToneOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==referenceToneOwner.current.snapshot)referenceToneOwner.current.valid=false;}catch{referenceToneOwner.current.valid=false;}} if(nineSliceOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==nineSliceOwner.current.snapshot)nineSliceOwner.current.valid=false;}catch{nineSliceOwner.current.valid=false;}} if(cubeLutOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==cubeLutOwner.current.snapshot)cubeLutOwner.current.valid=false;}catch{cubeLutOwner.current.valid=false;}} if(anaglyphOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==anaglyphOwner.current.snapshot)anaglyphOwner.current.valid=false;}catch{anaglyphOwner.current.valid=false;}} if(spriteSliceOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==spriteSliceOwner.current.snapshot)spriteSliceOwner.current.valid=false;}catch{spriteSliceOwner.current.valid=false;}} if(channelSplitOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==channelSplitOwner.current.snapshot)channelSplitOwner.current.valid=false;}catch{channelSplitOwner.current.valid=false;}} if(channelPackOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==channelPackOwner.current.snapshot)channelPackOwner.current.valid=false;}catch{channelPackOwner.current.valid=false;}} if(paletteImageOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==paletteImageOwner.current.snapshot)paletteImageOwner.current.valid=false;}catch{paletteImageOwner.current.valid=false;}} if(distanceFieldOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==distanceFieldOwner.current.snapshot)distanceFieldOwner.current.valid=false;}catch{distanceFieldOwner.current.valid=false;}} if(normalMapOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==normalMapOwner.current.snapshot)normalMapOwner.current.valid=false;}catch{normalMapOwner.current.valid=false;}} if(seamlessOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==seamlessOwner.current.snapshot)seamlessOwner.current.valid=false;}catch{seamlessOwner.current.valid=false;}} if(displacementOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==displacementOwner.current.snapshot)displacementOwner.current.valid=false;}catch{displacementOwner.current.valid=false;}} if(luminanceMaskOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==luminanceMaskOwner.current.snapshot)luminanceMaskOwner.current.valid=false;}catch{luminanceMaskOwner.current.valid=false;}} if(redactionOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==redactionOwner.current.snapshot)redactionOwner.current.valid=false;}catch{redactionOwner.current.valid=false;}} if(despeckleOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==despeckleOwner.current.snapshot)despeckleOwner.current.valid=false;}catch{despeckleOwner.current.valid=false;}} if(cloneOwner.current?.valid){try{if(encodeDraft(elements,state,files)!==cloneOwner.current.snapshot)cloneOwner.current.valid=false;}catch{cloneOwner.current.valid=false;}} setHasArtwork(elements.some(element => !element.isDeleted)); setReady(Boolean(api.current) && !state.isLoading); invalidatePreparedSvg(elements, files); latest.current = [elements, state, files]; if (panel === 'objects' || panel === 'arrange') { const next = elements.filter(element => !element.isDeleted); setObjects(current => current.length === next.length && current.every((element, index) => element === next[index]) ? current : next); } clearTimeout(timer.current); timer.current = setTimeout(() => save(false), 700); }}>
        <MainMenu><MainMenu.ItemCustom><small>Offline creation powered by Excalidraw.<br/>Open-source and font notices are bundled.</small></MainMenu.ItemCustom></MainMenu>
      </Excalidraw>
    </section>
    {panel && <section key={panel} id="workspace-panel" className="workspace-panel" onKeyDownCapture={event => { if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); if (vectorFileOwner.current) event.currentTarget.querySelector('button[data-vector-file-cancel="true"]')?.click(); if(panel==='cutout')event.currentTarget.querySelector('button[data-photo-cutout-cancel="true"]')?.click(); if(panel==='surface')event.currentTarget.querySelector('button[data-surface-mockup-cancel="true"]')?.click(); if(panel==='clone')event.currentTarget.querySelector('button[data-clone-repair-cancel="true"]')?.click(); if(panel==='despeckle')event.currentTarget.querySelector('button[data-despeckle-cancel="true"]')?.click(); if(panel==='redaction')event.currentTarget.querySelector('button[data-redaction-cancel="true"]')?.click(); if(panel==='luminanceMask')event.currentTarget.querySelector('button[data-luminance-mask-cancel="true"]')?.click(); if(panel==='displacement')event.currentTarget.querySelector('button[data-displacement-cancel="true"]')?.click(); if(panel==='seamless')event.currentTarget.querySelector('button[data-seamless-cancel="true"]')?.click(); if(panel==='normalMap')event.currentTarget.querySelector('button[data-normal-map-cancel="true"]')?.click(); if(panel==='distanceField')event.currentTarget.querySelector('button[data-distance-field-cancel="true"]')?.click(); if(panel==='frequency')event.currentTarget.querySelector('button[data-frequency-cancel="true"]')?.click(); if(panel==='barrier')event.currentTarget.querySelector('button[data-barrier-cancel="true"]')?.click(); if(panel==='stereogram')event.currentTarget.querySelector('button[data-stereogram-cancel="true"]')?.click(); if(panel==='gradient')event.currentTarget.querySelector('button[data-gradient-cancel="true"]')?.click(); if(panel==='carve')event.currentTarget.querySelector('button[data-carve-cancel="true"]')?.click(); if(panel==='vision')event.currentTarget.querySelector('button[data-vision-cancel="true"]')?.click(); if(panel==='cube')event.currentTarget.querySelector('button[data-cube-faces-cancel="true"]')?.click(); if(panel==='document')event.currentTarget.querySelector('button[data-document-cancel="true"]')?.click(); if(panel==='registration')event.currentTarget.querySelector('button[data-registration-cancel="true"]')?.click(); if(panel==='radial')event.currentTarget.querySelector('button[data-radial-cancel="true"]')?.click(); if(panel==='relight')event.currentTarget.querySelector('button[data-relight-cancel="true"]')?.click(); if(panel==='chroma')event.currentTarget.querySelector('button[data-chroma-key-cancel="true"]')?.click(); if(panel==='blend')event.currentTarget.querySelector('button[data-photo-blend-cancel="true"]')?.click(); if(panel==='ascii')event.currentTarget.querySelector('button[data-ascii-cancel="true"]')?.click(); if(panel==='stitch')event.currentTarget.querySelector('button[data-stitch-cancel="true"]')?.click(); if(panel==='halftone')event.currentTarget.querySelector('button[data-halftone-cancel="true"]')?.click(); if(panel==='contours')event.currentTarget.querySelector('button[data-contours-cancel="true"]')?.click(); if(panel==='islands')event.currentTarget.querySelector('button[data-islands-cancel="true"]')?.click(); if(panel==='annulus')event.currentTarget.querySelector('button[data-annulus-cancel="true"]')?.click(); if(panel==='atlas')event.currentTarget.querySelector('button[data-atlas-cancel="true"]')?.click(); if(panel==='panorama')event.currentTarget.querySelector('button[data-panorama-cancel="true"]')?.click(); if(panel==='referenceTone')event.currentTarget.querySelector('button[data-reference-tone-cancel="true"]')?.click(); if(panel==='nineSlice')event.currentTarget.querySelector('button[data-nine-slice-cancel="true"]')?.click(); if(panel==='cubeLut')event.currentTarget.querySelector('button[data-cube-lut-cancel="true"]')?.click(); if(panel==='anaglyph')event.currentTarget.querySelector('button[data-anaglyph-cancel="true"]')?.click(); if(panel==='spriteSlice')event.currentTarget.querySelector('button[data-sprite-slice-cancel="true"]')?.click(); if(panel==='channelSplit')event.currentTarget.querySelector('button[data-channel-split-cancel="true"]')?.click(); if(panel==='channelPack')event.currentTarget.querySelector('button[data-channel-pack-cancel="true"]')?.click(); if(panel==='paletteImage')event.currentTarget.querySelector('button[data-palette-image-cancel="true"]')?.click(); closePanel(); } }} aria-label={panel === 'spriteSlice' ? 'slice sprite sheet panel' : panel === 'channelSplit' ? 'extract channels panel' : panel === 'channelPack' ? 'channel pack panel' : panel === 'luminanceMask' ? 'photo mask panel' : panel === 'normalMap' ? 'normal map panel' : panel === 'distanceField' ? 'distance field panel' : panel === 'paletteImage' ? 'palette image panel' : `${panel} panel`} onClickCapture={event => { const vectorCancel = vectorFileOwner.current && event.target instanceof Element && event.target.closest('button[data-vector-file-cancel="true"]'); const cutoutCancel=panel==='cutout'&&event.target instanceof Element&&event.target.closest('button[data-photo-cutout-cancel="true"]'); const surfaceCancel=panel==='surface'&&event.target instanceof Element&&event.target.closest('button[data-surface-mockup-cancel="true"]'); const cloneCancel=panel==='clone'&&event.target instanceof Element&&event.target.closest('button[data-clone-repair-cancel="true"]'); const despeckleCancel=panel==='despeckle'&&event.target instanceof Element&&event.target.closest('button[data-despeckle-cancel="true"]'); const redactionCancel=panel==='redaction'&&event.target instanceof Element&&event.target.closest('button[data-redaction-cancel="true"]'); const luminanceMaskCancel=panel==='luminanceMask'&&event.target instanceof Element&&event.target.closest('button[data-luminance-mask-cancel="true"]'); const displacementCancel=panel==='displacement'&&event.target instanceof Element&&event.target.closest('button[data-displacement-cancel="true"]'); const seamlessCancel=panel==='seamless'&&event.target instanceof Element&&event.target.closest('button[data-seamless-cancel="true"]'); const normalMapCancel=panel==='normalMap'&&event.target instanceof Element&&event.target.closest('button[data-normal-map-cancel="true"]'); const distanceFieldCancel=panel==='distanceField'&&event.target instanceof Element&&event.target.closest('button[data-distance-field-cancel="true"]'); const frequencyCancel=panel==='frequency'&&event.target instanceof Element&&event.target.closest('button[data-frequency-cancel="true"]'); const barrierCancel=panel==='barrier'&&event.target instanceof Element&&event.target.closest('button[data-barrier-cancel="true"]'); const stereogramCancel=panel==='stereogram'&&event.target instanceof Element&&event.target.closest('button[data-stereogram-cancel="true"]'); const gradientCancel=panel==='gradient'&&event.target instanceof Element&&event.target.closest('button[data-gradient-cancel="true"]'); const carveCancel=panel==='carve'&&event.target instanceof Element&&event.target.closest('button[data-carve-cancel="true"]'); const visionCancel=panel==='vision'&&event.target instanceof Element&&event.target.closest('button[data-vision-cancel="true"]'); const cubeCancel=panel==='cube'&&event.target instanceof Element&&event.target.closest('button[data-cube-faces-cancel="true"]'); const documentCancel=panel==='document'&&event.target instanceof Element&&event.target.closest('button[data-document-cancel="true"]'); const registrationCancel=panel==='registration'&&event.target instanceof Element&&event.target.closest('button[data-registration-cancel="true"]'); const radialCancel=panel==='radial'&&event.target instanceof Element&&event.target.closest('button[data-radial-cancel="true"]'); const relightCancel=panel==='relight'&&event.target instanceof Element&&event.target.closest('button[data-relight-cancel="true"]'); const chromaCancel=panel==='chroma'&&event.target instanceof Element&&event.target.closest('button[data-chroma-key-cancel="true"]'); const blendCancel=panel==='blend'&&event.target instanceof Element&&event.target.closest('button[data-photo-blend-cancel="true"]'); const asciiCancel=panel==='ascii'&&event.target instanceof Element&&event.target.closest('button[data-ascii-cancel="true"]'); const stitchCancel=panel==='stitch'&&event.target instanceof Element&&event.target.closest('button[data-stitch-cancel="true"]'); const halftoneCancel=panel==='halftone'&&event.target instanceof Element&&event.target.closest('button[data-halftone-cancel="true"]'); const contoursCancel=panel==='contours'&&event.target instanceof Element&&event.target.closest('button[data-contours-cancel="true"]'); const islandsCancel=panel==='islands'&&event.target instanceof Element&&event.target.closest('button[data-islands-cancel="true"]'); const annulusCancel=panel==='annulus'&&event.target instanceof Element&&event.target.closest('button[data-annulus-cancel="true"]'); const atlasCancel=panel==='atlas'&&event.target instanceof Element&&event.target.closest('button[data-atlas-cancel="true"]'); const panoramaCancel=panel==='panorama'&&event.target instanceof Element&&event.target.closest('button[data-panorama-cancel="true"]'); const referenceToneCancel=panel==='referenceTone'&&event.target instanceof Element&&event.target.closest('button[data-reference-tone-cancel="true"]'); const nineSliceCancel=panel==='nineSlice'&&event.target instanceof Element&&event.target.closest('button[data-nine-slice-cancel="true"]'); const cubeLutCancel=panel==='cubeLut'&&event.target instanceof Element&&event.target.closest('button[data-cube-lut-cancel="true"]'); const anaglyphCancel=panel==='anaglyph'&&event.target instanceof Element&&event.target.closest('button[data-anaglyph-cancel="true"]'); const spriteSliceCancel=panel==='spriteSlice'&&event.target instanceof Element&&event.target.closest('button[data-sprite-slice-cancel="true"]'); const channelSplitCancel=panel==='channelSplit'&&event.target instanceof Element&&event.target.closest('button[data-channel-split-cancel="true"]'); const channelPackCancel=panel==='channelPack'&&event.target instanceof Element&&event.target.closest('button[data-channel-pack-cancel="true"]'); const paletteImageCancel=panel==='paletteImage'&&event.target instanceof Element&&event.target.closest('button[data-palette-image-cancel="true"]'); if (busyRef.current && !frequencyCancel && !barrierCancel && !stereogramCancel && !gradientCancel && !carveCancel && !visionCancel && !cubeCancel && !documentCancel && !registrationCancel && !radialCancel && !relightCancel && !chromaCancel && !blendCancel && !asciiCancel && !stitchCancel && !halftoneCancel && !contoursCancel && !islandsCancel && !annulusCancel && !atlasCancel && !panoramaCancel && !referenceToneCancel && !nineSliceCancel && !cubeLutCancel && !anaglyphCancel && !spriteSliceCancel && !channelSplitCancel && !channelPackCancel && !paletteImageCancel && !distanceFieldCancel && !normalMapCancel && !seamlessCancel && !displacementCancel && !luminanceMaskCancel && !despeckleCancel && !redactionCancel && !vectorCancel && !cutoutCancel && !surfaceCancel && !cloneCancel) { event.preventDefault(); event.stopPropagation(); } }}>
      <div className="panel-title"><h2 ref={panelHeading} tabIndex={-1}>{panel === 'frequency' ? 'Frequency separation' : panel === 'barrier' ? 'Barrier-grid kit' : panel === 'stereogram' ? 'Depth stereogram' : panel === 'gradient' ? 'Gradient paste' : panel === 'carve' ? 'Content-aware resize' : panel === 'vision' ? 'Color-vision proof sheet' : panel === 'cube' ? 'Cube faces' : panel === 'document' ? 'Document cleanup' : panel === 'registration' ? 'Photo registration' : panel === 'radial' ? 'Radial lens' : panel === 'relight' ? 'Normal-map relight' : panel === 'chroma' ? 'Chroma key' : panel === 'blend' ? 'Photo blend' : panel === 'ascii' ? 'ASCII art' : panel === 'stitch' ? 'Stitch chart' : panel === 'halftone' ? 'One-ink halftone' : panel === 'contours' ? 'Height contours' : panel === 'islands' ? 'Separate transparent items' : panel === 'annulus' ? 'Unwrap circular band' : panel === 'atlas' ? 'Texture atlas' : panel === 'panorama' ? 'Panorama view' : panel === 'referenceTone' ? 'Reference tones' : panel === 'nineSlice' ? 'Nine-slice resize' : panel === 'cubeLut' ? 'Cube LUT' : panel === 'spriteSlice' ? 'Slice sprite sheet' : panel === 'channelSplit' ? 'Extract channels' : panel === 'channelPack' ? 'Channel pack' : panel === 'luminanceMask' ? 'Photo mask' : panel === 'normalMap' ? 'Normal map' : panel === 'distanceField' ? 'Distance field' : panel === 'paletteImage' ? 'Palette image' : panel[0].toUpperCase() + panel.slice(1)}</h2><button disabled={busy} onClick={closePanel}>Close panel</button></div>
      {panel === 'layouts' && <><p>Choose what to make. Each result stays editable, and Undo removes one insertion.</p>
        <div role="group" aria-label="Layout tools" className="tool-grid layout-tools">{[["starter","Starter design"],["maze","Maze worksheet"],["jigsaw","Jigsaw guide"],["boxNet","Box net"],["picture","Picture logic"],["cloud","Word cloud"],["table","Table"],["qr","QR artwork"],["barcode","Barcode label"],["dependencies","Dependency diagram"],["palette","Artwork color key"],["chart","Data chart"],["schedule","Production schedule"],["calendar","Month calendar"],["storyboard","Storyboard sheet"]].map(([key,label]) => <button key={key} aria-pressed={layoutTool === key} onClick={() => setLayoutTool(key)}>{label}</button>)}</div>
        {layoutTool === 'picture' && <PictureLogicTool captureSource={capturePictureSource} isCurrent={pictureCurrent} onApply={insertPicture} />}
        {layoutTool === 'jigsaw' && <JigsawTool captureSource={captureJigsawSource} isCurrent={jigsawCurrent} onApply={insertJigsaw} />}
        {layoutTool === 'boxNet' && <BoxNetTool captureSource={captureBoxNetSource} isCurrent={boxNetCurrent} onApply={insertBoxNet} />}
        {layoutTool === 'maze' && <MazeTool captureSource={captureMazeSource} isCurrent={mazeCurrent} onApply={insertMaze} />}
        {layoutTool === 'cloud' && <WordCloudTool captureCurrent={() => { const editor=api.current,snapshot=encodeDraft(...scene());return () => mounted.current && api.current===editor && !busyRef.current && encodeDraft(...scene())===snapshot; }} onApply={insertCloud} />}
        {layoutTool === 'starter' && <><h3>Make a design your own</h3><p>Choose a starting layout and personalize its words. Creates a grouped white card with editable text and shapes. Double-tap a text block to edit it; Ungroup lets you rearrange individual parts. Nothing is uploaded.</p><label>Starter design style<select aria-label="Starter design style" value={starterStyle} onChange={event => setStarterStyle(event.target.value)}><option value="quote">Quote card</option><option value="announcement">Announcement</option><option value="flyer">Simple flyer</option></select></label><label>Design headline<input maxLength={120} value={starterHeadline} onChange={event => setStarterHeadline(event.target.value)} /></label><label>Design details<textarea aria-label="Design details" rows={3} maxLength={360} value={starterDetails} onChange={event => setStarterDetails(event.target.value)} /></label><label>Design sign-off<input maxLength={120} value={starterSignoff} onChange={event => setStarterSignoff(event.target.value)} /></label><p>Headline and sign-off: up to 60 characters each. Optional details: up to 180 characters across 3 lines. Long lines wrap. Insert keeps your existing artwork; Undo removes the new design.</p><StarterPreview style={starterStyle} headline={starterHeadline} details={starterDetails} signoff={starterSignoff} /><button onClick={() => insertLayout('starter')}>Insert editable starter design</button></>}
        {layoutTool === 'table' && <><h3>Editable comparison table</h3><p>Paste tab-separated cells from a spreadsheet, or separate cells with |. The first row is the header. Use 2–6 columns and 2–10 rows, up to 64 characters per cell. Creates editable cells and bound text; formulas and links remain plain text.</p><label>Table title<input maxLength={60} value={tableTitle} onChange={event => setTableTitle(event.target.value)} /></label><label>Table rows<textarea aria-label="Table rows" rows={5} maxLength={4096} value={tableRows} onChange={event => setTableRows(event.target.value)} /></label><button onClick={() => insertLayout('table')}>Insert editable table</button></>}
        {layoutTool === 'qr' && <><h3>QR artwork</h3><p>Turn a short link or message into grouped QR shapes with a white border. Generated on your device; no link is opened or uploaded.</p><label>QR link or text<textarea aria-label="QR link or text" rows={2} maxLength={192} placeholder="Paste a short link or write a message" value={qrText} onChange={event => setQrText(event.target.value)} /></label><label>QR caption<input maxLength={60} value={qrCaption} onChange={event => setQrCaption(event.target.value)} /></label><button onClick={() => insertLayout('qr')}>Insert editable QR code</button></>}
        {layoutTool === 'barcode' && <><h3>Barcode label</h3><p>Create grouped, editable Code 128 bars locally. Use up to 80 printable English letters, numbers or symbols. Keep bar and white border proportions; check a scan before printing.</p><label>Barcode text<input aria-label="Barcode text" maxLength={80} placeholder="Example: NOBLE-ITEM-042" value={barcodeText} onChange={event => setBarcodeText(event.target.value)} /></label><label>Barcode caption<input maxLength={60} value={barcodeCaption} onChange={event => setBarcodeCaption(event.target.value)} /></label><button onClick={() => insertLayout('barcode')}>Insert editable barcode</button><p>No identifier is uploaded. Code 128 is different from retail EAN/UPC codes.</p></>}
        {layoutTool === 'dependencies' && <><h3>Dependency diagram</h3><p>Write one Label -&gt; Label connection per line, or a standalone label. Up to 20 nodes and 32 connections; return cycles are rejected. Creates editable connected nodes from left to right, entirely on your device.</p><label>Dependency diagram title<input maxLength={60} value={dependencyTitle} onChange={event => setDependencyTitle(event.target.value)} /></label><label>Dependency rows<textarea aria-label="Dependency rows" rows={4} maxLength={4096} value={dependencyRows} onChange={event => setDependencyRows(event.target.value)} /></label><button onClick={() => insertLayout('dependencies')}>Insert editable dependency diagram</button></>}
        {layoutTool === 'palette' && <><h3>Artwork color key</h3><p>Create editable swatches and hex labels from visible stroke and fill colors, up to 32 colors. Includes object opacity; this reads assigned colors, not rendered pixels. Hidden objects are excluded.</p><label>Color-key title<input maxLength={60} value={paletteTitle} onChange={event => setPaletteTitle(event.target.value)} /></label><button onClick={() => insertLayout('palette')}>Insert editable color key</button></>}
        {layoutTool === 'chart' && <><h3>Data chart</h3><label>Chart title<input maxLength={60} value={layoutTitle} onChange={event => setLayoutTitle(event.target.value)} /></label>
        <label>Chart rows<textarea aria-label="Chart rows" rows={4} maxLength={1600} value={chartRows} onChange={event => setChartRows(event.target.value)} /></label><p>One label | number per line, up to 12 rows. Positive and negative bars use one linear scale from zero.</p><button onClick={() => insertLayout('chart')}>Insert editable chart</button></>}
        {layoutTool === 'schedule' && <><h3>Production schedule</h3><p>Turn dated tasks into editable timeline artwork. Bar positions and lengths use inclusive calendar days; weekends count and overlapping tasks stay on separate rows.</p><label>Schedule title<input maxLength={60} value={scheduleTitle} onChange={event => setScheduleTitle(event.target.value)} /></label><label>Schedule tasks<textarea aria-label="Schedule tasks" rows={6} maxLength={4096} value={scheduleRows} onChange={event => setScheduleRows(event.target.value)} /></label><p>One task | YYYY-MM-DD | YYYY-MM-DD per line. Use 1–16 tasks, names up to 48 characters and a total span up to 366 days. Real dates from 1900–2299 are supported. Input order is kept.</p><p>This creates a static planning snapshot, with no reminders or automatic rescheduling. Ungroup to edit labels and bars. Existing artwork stays; Undo removes the new schedule.</p><button onClick={() => insertLayout('schedule')}>Insert editable production schedule</button></>}
        {layoutTool === 'calendar' && <><h3>Month calendar</h3><label>Calendar month<input type="month" min="1900-01" max="2299-12" value={calendarMonth} onChange={event => setCalendarMonth(event.target.value)} /></label><label className="check-label"><input type="checkbox" checked={mondayFirst} onChange={event => setMondayFirst(event.target.checked)} />Monday starts the week</label><button onClick={() => insertLayout('calendar')}>Insert editable calendar</button></>}
        {layoutTool === 'storyboard' && <><h3>Storyboard sheet</h3><label>Storyboard title<input maxLength={60} value={storyTitle} onChange={event => setStoryTitle(event.target.value)} /></label><label>Scene briefs<textarea aria-label="Scene briefs" rows={4} maxLength={1600} value={storyBriefs} onChange={event => setStoryBriefs(event.target.value)} /></label><p>One scene brief per line, up to 12 scenes. Empty frames are ready for your own artwork. This creates a planning sheet.</p><button onClick={() => insertLayout('storyboard')}>Insert editable storyboard</button></>}
      </>}
      {panel === 'objects' && <><TextContrastReview elements={objects} expanded={contrastOpen} onToggle={() => setContrastOpen(value => !value)} background={contrastBackground} onBackgroundChange={setContrastBackground} onLocate={locateObject} /><label>Find artwork<input type="search" maxLength={100} value={objectQuery} onChange={event => setObjectQuery(event.target.value)} placeholder="Text or object type" /></label><label>Object type<select aria-label="Object type" value={objectType} onChange={event => setObjectType(event.target.value)}><option value="all">All objects</option><option value="text">Text</option><option value="photos">Photos</option><option value="shapes">Shapes, lines & frames</option></select></label><p>{foundObjects.length} of {objects.length} objects. Searches this drawing only; hidden objects must be shown before locating.</p>{!!(objectQuery || objectType !== 'all') && <button onClick={() => { setObjectQuery(''); setObjectType('all'); }}>Clear object filters</button>}{!!objects.length && !foundObjects.length && <p>No matching objects. Try another word or clear the filters.</p>}<p>Top objects appear in front. Changes share the drawing Undo control. Replace photo fits a new local copy inside the old photo’s box, keeping its center, rotation and flip; any old crop is cleared. Originals stay unchanged.</p>{!objects.length && <p>Add a shape or text to start a composition.</p>}
        {foundObjects.map(({ element, number, hidden, label }) => <article key={element.id} className="object-row"><strong>{label}{hidden ? ' · hidden' : element.locked ? ' · locked' : ''}</strong><div className="tool-grid"><button disabled={hidden} aria-label={`Find object ${number} on canvas`} onClick={() => locateObject(element.id)}>Find on canvas</button>
          {element.type === 'image' && <PhotoObjectTools number={number} disabled={element.locked || !!element.customData?.nobleHidden} onAction={action => ({frequency:() => openFrequency(element.id),barrier:() => openBarrier(element.id),stereogram:() => openStereogram(element.id),gradient:() => openGradient(element.id),carve:() => openCarve(element.id),vision:() => openVision(element.id),cube:() => openCube(element.id),document:() => openDocument(element.id),registration:() => openRegistration(element.id),radial:() => openRadial(element.id),relight:() => openRelight(element.id),chroma:() => openChroma(element.id),blend:() => openBlend(element.id),ascii:() => openAscii(element.id),stitch:() => openStitch(element.id),halftone:() => openHalftone(element.id),contours:() => openContours(element.id),islands:() => openIslands(element.id),annulus:() => openAnnulus(element.id),atlas:() => openAtlas(element.id),panorama:() => openPanorama(element.id),referenceTone:() => openReferenceTone(element.id),nineSlice:() => openNineSlice(element.id),cubeLut:() => openCubeLut(element.id),anaglyph:() => openAnaglyph(element.id),spriteSlice:() => openSpriteSlice(element.id),channelSplit:() => openChannelSplit(element.id),channelPack:() => openChannelPack(element.id),paletteImage:() => openPaletteImage(element.id),distanceField:() => openDistanceField(element.id),normalMap:() => openNormalMap(element.id),seamless:() => openSeamless(element.id),displacement:() => openDisplacement(element.id),luminanceMask:() => openLuminanceMask(element.id),redaction:() => openRedaction(element.id),despeckle:() => openDespeckle(element.id),clone:() => openClone(element.id),replace:() => choosePhoto(element.id),framing:() => openFraming(element.id),rectify:() => openRectify(element.id),surface:() => openSurface(element.id),outline:() => openCutout(element.id),wand:() => openWand(element.id),trace:() => openWand(element.id,true),mosaic:() => openMosaic(element.id)})[action]?.()} />}
          <button onClick={() => objectAction(element.id, 'visibility')}>{element.customData?.nobleHidden ? 'Show' : 'Hide'}</button>
          <button disabled={!!element.customData?.nobleHidden} onClick={() => objectAction(element.id, 'lock')}>{element.locked ? 'Unlock' : 'Lock'}</button>
          <button disabled={element.locked} onClick={() => objectAction(element.id, 'up')}>Forward</button><button disabled={element.locked} onClick={() => objectAction(element.id, 'down')}>Backward</button>
          <button disabled={element.locked} onClick={() => objectAction(element.id, 'front')}>To front</button><button disabled={element.locked} onClick={() => objectAction(element.id, 'back')}>To back</button>
        </div></article>)}</>}
      {panel === 'mosaic' && mosaicTarget && <PhotoMosaicTool target={mosaicTarget} isCurrent={() => framingCurrent(mosaicTarget)} onApply={insertMosaic} onCancel={closePanel} />}
      {panel === 'trace' && wandTarget && <PhotoWandTool target={wandTarget} mode="trace" isCurrent={() => framingCurrent(wandTarget)} onApply={insertTrace} onCancel={closePanel} />}
      {panel === 'surface' && surfaceTarget && <PhotoSurfaceMockupTool target={surfaceTarget} isCurrent={artwork => surfaceTarget.valid && framingCurrent(surfaceTarget) && !!artwork && api.current.getFiles()[artwork.photo.fileId]?.dataURL === artwork.dataURL} onApply={insertSurface} onCancel={() => {surfaceTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'displacement' && displacementTarget && <PhotoDisplacementTool target={displacementTarget} isCurrent={() => displacementTarget.valid && framingCurrent(displacementTarget)} onApply={insertDisplacement} onCancel={() => {displacementTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'luminanceMask' && luminanceMaskTarget && <PhotoLuminanceMaskTool target={luminanceMaskTarget} isCurrent={() => luminanceMaskTarget.valid && framingCurrent(luminanceMaskTarget)} onApply={insertLuminanceMask} onCancel={() => {luminanceMaskTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'redaction' && redactionTarget && <PhotoRedactionTool target={redactionTarget} isCurrent={() => redactionTarget.valid && framingCurrent(redactionTarget)} onApply={insertRedaction} onCancel={() => {redactionTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'distanceField' && distanceFieldTarget && <PhotoDistanceFieldTool target={distanceFieldTarget} isCurrent={() => distanceFieldTarget.valid && framingCurrent(distanceFieldTarget)} onApply={insertDistanceField} onUseExact={useDistanceFieldImage} onCancel={() => {distanceFieldTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'frequency' && frequencyTarget && <PhotoFrequencyTool target={frequencyTarget} isCurrent={() => frequencyTarget.valid && framingCurrent(frequencyTarget)} onApply={insertFrequency} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,frequencyTarget,'frequency layer')} onCancel={() => {frequencyTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'barrier' && barrierTarget && <PhotoBarrierTool target={barrierTarget} isCurrent={() => barrierTarget.valid && framingCurrent(barrierTarget)} onApply={insertBarrier} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,barrierTarget,'barrier kit')} onCancel={() => {barrierTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'gradient' && gradientTarget && <PhotoGradientTool target={gradientTarget} isCurrent={() => gradientTarget.valid && framingCurrent(gradientTarget)} onApply={insertGradient} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,gradientTarget,'gradient-paste photo')} onCancel={() => {gradientTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'stereogram' && stereogramTarget && <PhotoStereogramTool target={stereogramTarget} isCurrent={() => stereogramTarget.valid && framingCurrent(stereogramTarget)} onApply={insertStereogram} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,stereogramTarget,'stereogram photo')} onCancel={() => {stereogramTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'carve' && carveTarget && <PhotoCarveTool target={carveTarget} isCurrent={() => carveTarget.valid && framingCurrent(carveTarget)} onApply={insertCarve} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,carveTarget,'content-aware photo')} onCancel={() => {carveTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'vision' && visionTarget && <PhotoVisionTool target={visionTarget} isCurrent={() => visionTarget.valid && framingCurrent(visionTarget)} onApply={insertVision} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,visionTarget,'color-vision proof')} onCancel={() => {visionTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'cube' && cubeTarget && <PhotoCubeFacesTool target={cubeTarget} isCurrent={() => cubeTarget.valid && framingCurrent(cubeTarget)} onApply={insertCube} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,cubeTarget,'cube face')} onCancel={() => {cubeTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'document' && documentTarget && <PhotoDocumentTool target={documentTarget} isCurrent={() => documentTarget.valid && framingCurrent(documentTarget)} onApply={insertDocument} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,documentTarget,'document photo')} onCancel={() => {documentTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'registration' && registrationTarget && <PhotoRegistrationTool target={registrationTarget} isCurrent={() => registrationTarget.valid && framingCurrent(registrationTarget)} onApply={insertRegistration} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,registrationTarget,'registration photo')} onCancel={() => {registrationTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'radial' && radialTarget && <PhotoRadialTool target={radialTarget} isCurrent={() => radialTarget.valid && framingCurrent(radialTarget)} onApply={insertRadial} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,radialTarget,'radial photo')} onCancel={() => {radialTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'relight' && relightTarget && <PhotoRelightTool target={relightTarget} isCurrent={() => relightTarget.valid && framingCurrent(relightTarget)} onApply={insertRelight} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,relightTarget,'relit photo')} onCancel={() => {relightTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'chroma' && chromaTarget && <PhotoChromaKeyTool target={chromaTarget} isCurrent={() => chromaTarget.valid && framingCurrent(chromaTarget)} onApply={insertChroma} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,chromaTarget,'keyed photo')} onCancel={() => {chromaTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'blend' && blendTarget && <PhotoBlendTool target={blendTarget} isCurrent={() => blendTarget.valid && framingCurrent(blendTarget)} onApply={insertBlend} onUseExact={(proposal,current)=>useExactPhotoImage(proposal,current,blendTarget,'blended photo')} onCancel={() => {blendTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'ascii' && asciiTarget && <PhotoAsciiTool target={asciiTarget} isCurrent={() => asciiTarget.valid && framingCurrent(asciiTarget)} onApply={insertAscii} onCancel={() => {asciiTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'stitch' && stitchTarget && <PhotoStitchTool target={stitchTarget} isCurrent={() => stitchTarget.valid && framingCurrent(stitchTarget)} onApply={insertStitch} onCancel={() => {stitchTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'halftone' && halftoneTarget && <PhotoHalftoneTool target={halftoneTarget} isCurrent={() => halftoneTarget.valid && framingCurrent(halftoneTarget)} onApply={insertHalftone} onCancel={() => {halftoneTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'contours' && contoursTarget && <PhotoContoursTool target={contoursTarget} isCurrent={() => contoursTarget.valid && framingCurrent(contoursTarget)} onApply={insertContours} onCancel={() => {contoursTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'islands' && islandsTarget && <PhotoIslandsTool target={islandsTarget} isCurrent={() => islandsTarget.valid && framingCurrent(islandsTarget)} onApply={insertIslands} onUseExact={useIslandsImage} onCancel={() => {islandsTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'annulus' && annulusTarget && <PhotoAnnulusTool target={annulusTarget} isCurrent={() => annulusTarget.valid && framingCurrent(annulusTarget)} onApply={insertAnnulus} onUseExact={useAnnulusImage} onCancel={() => {annulusTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'atlas' && atlasTarget && <PhotoAtlasTool target={atlasTarget} isCurrent={() => atlasTarget.valid && framingCurrent(atlasTarget)} onApply={insertAtlas} onUseExact={useAtlasImage} onCancel={() => {atlasTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'panorama' && panoramaTarget && <PhotoPanoramaTool target={panoramaTarget} isCurrent={() => panoramaTarget.valid && framingCurrent(panoramaTarget)} onApply={insertPanorama} onUseExact={usePanoramaImage} onCancel={() => {panoramaTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'referenceTone' && referenceToneTarget && <PhotoReferenceToneTool target={referenceToneTarget} isCurrent={() => referenceToneTarget.valid && framingCurrent(referenceToneTarget)} onApply={insertReferenceTone} onUseExact={useReferenceToneImage} onCancel={() => {referenceToneTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'nineSlice' && nineSliceTarget && <PhotoNineSliceTool target={nineSliceTarget} isCurrent={() => nineSliceTarget.valid && framingCurrent(nineSliceTarget)} onApply={insertNineSlice} onUseExact={useNineSliceImage} onCancel={() => {nineSliceTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'cubeLut' && cubeLutTarget && <PhotoCubeLutTool target={cubeLutTarget} isCurrent={() => cubeLutTarget.valid && framingCurrent(cubeLutTarget)} onApply={insertCubeLut} onUseExact={useCubeLutImage} onCancel={() => {cubeLutTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'anaglyph' && anaglyphTarget && <PhotoAnaglyphTool target={anaglyphTarget} isCurrent={() => anaglyphTarget.valid && framingCurrent(anaglyphTarget)} onApply={insertAnaglyph} onUseExact={useAnaglyphImage} onCancel={() => {anaglyphTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'spriteSlice' && spriteSliceTarget && <PhotoSpriteSliceTool target={spriteSliceTarget} isCurrent={() => spriteSliceTarget.valid && framingCurrent(spriteSliceTarget)} onApply={insertSpriteFrames} onUseExact={useSpriteFrameImage} onCancel={() => {spriteSliceTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'channelSplit' && channelSplitTarget && <PhotoChannelSplitTool target={channelSplitTarget} isCurrent={() => channelSplitTarget.valid && framingCurrent(channelSplitTarget)} onApply={insertChannelSplit} onUseExact={useChannelSplitImage} onCancel={() => {channelSplitTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'channelPack' && channelPackTarget && <PhotoChannelPackTool target={channelPackTarget} isCurrent={() => channelPackTarget.valid && framingCurrent(channelPackTarget)} onApply={insertChannelPack} onUseExact={useChannelPackImage} onCancel={() => {channelPackTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'paletteImage' && paletteImageTarget && <PhotoPaletteImageTool target={paletteImageTarget} isCurrent={() => paletteImageTarget.valid && framingCurrent(paletteImageTarget)} onApply={insertPaletteImage} onUseExact={usePaletteImage} onCancel={() => {paletteImageTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'normalMap' && normalMapTarget && <PhotoNormalMapTool target={normalMapTarget} isCurrent={() => normalMapTarget.valid && framingCurrent(normalMapTarget)} onApply={insertNormalMap} onUseExact={useNormalMapImage} onCancel={() => {normalMapTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'seamless' && seamlessTarget && <PhotoSeamlessTool target={seamlessTarget} isCurrent={() => seamlessTarget.valid && framingCurrent(seamlessTarget)} onApply={insertSeamless} onUseExact={useSeamlessImage} onCancel={() => {seamlessTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'despeckle' && despeckleTarget && <PhotoDespeckleTool target={despeckleTarget} isCurrent={() => despeckleTarget.valid && framingCurrent(despeckleTarget)} onApply={insertDespeckle} onCancel={() => {despeckleTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'clone' && cloneTarget && <PhotoCloneRepairTool target={cloneTarget} isCurrent={() => cloneTarget.valid && framingCurrent(cloneTarget)} onApply={insertClone} onCancel={() => {cloneTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'cutout' && cutoutTarget && <PhotoOutlineCutoutTool target={cutoutTarget} isCurrent={() => cutoutTarget.valid && framingCurrent(cutoutTarget)} onApply={insertCutout} onCancel={() => {cutoutTarget.valid=false;setPanel(null);document.querySelector('[data-panel-trigger="objects"]')?.focus({preventScroll:true});}} />}
      {panel === 'wand' && wandTarget && <PhotoWandTool target={wandTarget} isCurrent={() => framingCurrent(wandTarget)} onApply={insertWand} onCancel={closePanel} />}
      {panel === 'rectify' && rectifyTarget && <PhotoRectifyTool target={rectifyTarget} isCurrent={() => framingCurrent(rectifyTarget)} onApply={insertRectified} onCancel={closePanel} />}
      {panel === 'framing' && framingTarget && <PhotoFramingPreview target={framingTarget} isCurrent={() => framingCurrent(framingTarget)} onApply={applyFraming} onCancel={closePanel} />}
      {panel === 'arrange' && <><div className="tool-grid layout-tools" role="group" aria-label="Arrangement tool">{[['align','Align & space'],['photos','Photo sheet'],['blend','Shape blend'],['grid','Grid repeat'],['personalize','Personalized copies'],['ring','Ring repeat'],['recolor','Recolor'],['boolean','Combine shapes'],['dimensions','Dimension callouts']].map(([value,label]) => <button key={value} aria-pressed={arrangeTool === value} onClick={() => setArrangeTool(value)}>{label}</button>)}</div>{arrangeTool === 'dimensions' && <DimensionCalloutTool captureSource={captureDimensions} onApply={insertDimensions} onCancel={closePanel} />}{arrangeTool === 'boolean' && <ShapeBooleanTool captureSource={() => captureBooleanSource(api.current)} isCurrent={source => mounted.current && source.current(api.current)} onApply={insertBoolean} onCancel={closePanel} />}{arrangeTool === 'personalize' && <PersonalizeTool captureSource={capturePersonalization} onApply={insertPersonalization} />}{arrangeTool === 'photos' && <><h3>Photo contact sheet</h3><p>Fit 2–8 existing photos into equal cells without cropping. Order follows the drawing’s back-to-front object list, left to right then down. Unlock, ungroup and straighten photos first. Other artwork stays in place.</p><label className="check-label"><input type="checkbox" checked={sheetAll} onChange={event => setSheetAll(event.target.checked)} />Use all visible unlocked photos</label><p>{sheetAll ? 'All eligible photos will move and resize.' : 'Select only the photos to arrange on the canvas first.'}</p><div className="number-fields"><label>Photo sheet columns<input inputMode="numeric" maxLength={1} value={sheetColumns} onChange={event => setSheetColumns(event.target.value)} /></label><label>Photo cell size<input inputMode="numeric" maxLength={4} value={sheetCell} onChange={event => setSheetCell(event.target.value)} /></label><label>Photo sheet gap<input inputMode="decimal" maxLength={5} value={sheetGap} onChange={event => setSheetGap(event.target.value)} /></label></div><button onClick={arrangePhotos}>Arrange photos as contact sheet</button></>}{arrangeTool === 'align' && <><p>Select objects on the canvas first. Unlock and ungroup them before arranging.</p><h3>Align selected objects</h3><div className="tool-grid">{['left', 'center', 'right', 'top', 'middle', 'bottom'].map(operation => <button key={operation} onClick={() => arrange(operation)}>{operation}</button>)}</div><h3>Space three or more objects evenly</h3><div className="tool-grid"><button onClick={() => arrange('horizontal')}>Horizontal spacing</button><button onClick={() => arrange('vertical')}>Vertical spacing</button></div></>}{arrangeTool === 'blend' && <><h3>Blend endpoint shapes</h3><p>Select exactly two matching rectangle, ellipse or diamond shapes. Insert editable intermediate positions, sizes, rotations and colors; endpoint shapes stay unchanged.</p><label>Blend intermediate steps<input inputMode="numeric" maxLength={2} value={blendSteps} onChange={event => setBlendSteps(event.target.value)} /></label><button onClick={() => repeatSelection('blend')}>Blend selected endpoint shapes</button></>}{arrangeTool === 'grid' && <><h3>Editable grid repeat</h3><p>Repeat the selected composition as independent objects. The original is the first grid placement.</p><div className="number-fields"><label>Grid columns<input inputMode="numeric" maxLength={2} value={gridColumns} onChange={event => setGridColumns(event.target.value)} /></label><label>Grid rows<input inputMode="numeric" maxLength={2} value={gridRows} onChange={event => setGridRows(event.target.value)} /></label><label>Grid gap<input inputMode="decimal" maxLength={5} value={gridGap} onChange={event => setGridGap(event.target.value)} /></label></div><button onClick={() => repeatSelection('grid')}>Create editable grid copies</button></>}{arrangeTool === 'ring' && <><h3>Editable ring repeat</h3><p>Add upright copies around the selected composition. The original stays at the center.</p><div className="number-fields"><label>Ring copies<input inputMode="numeric" maxLength={2} value={ringCount} onChange={event => setRingCount(event.target.value)} /></label><label>Ring radius<input inputMode="decimal" maxLength={5} value={ringRadius} onChange={event => setRingRadius(event.target.value)} /></label></div><button onClick={() => repeatSelection('ring')}>Create editable ring copies</button></>}{arrangeTool === 'recolor' && <><h3>Replace a composition color</h3><p>Change matching strokes and fills together. Locked and hidden objects stay protected unless included.</p><label>Source composition color<select value={sourceColor} onChange={event => setSourceColor(event.target.value)}>{!compositionColors(objects).some(entry => entry.color === sourceColor) && <option value={sourceColor}>{sourceColor} · no matches</option>}{compositionColors(objects).map(entry => <option key={entry.color} value={entry.color}>{entry.color} · {entry.count} uses</option>)}</select></label><label>Replacement hex color<input value={targetColor} maxLength={9} onChange={event => setTargetColor(event.target.value)} /></label><label className="check-label"><input type="checkbox" checked={includeLocked} onChange={event => setIncludeLocked(event.target.checked)} />Include locked and hidden objects</label><button onClick={recolor}>Replace matching colors</button></>}</>}
      {panel === 'model' && <ModelStudio initialAsset={nativeDepth.asset} onImage={file => addPhoto(file)} textureSources={api.current.getSceneElements().filter(element=>{try{return !element.isDeleted&&element.type==='image'&&Boolean(replaceablePhoto(api.current.getSceneElements(),element.id));}catch{return false;}}).map(photo=>({id:photo.id}))} captureTexture={id => {
        const editor=api.current;
        if(!editor||busyRef.current||preservedDraft.current)throw Error('Finish the current drawing operation first.');
        const photo=replaceablePhoto(editor.getSceneElements(),id),file=editor.getFiles()[photo.fileId];
        if(!file)throw Error('The selected photo is unavailable.');
        const {bytes,mimeType}=photoBytes(file.dataURL);return new File([bytes],'Noble-texture',{type:mimeType});
      }} />}
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
