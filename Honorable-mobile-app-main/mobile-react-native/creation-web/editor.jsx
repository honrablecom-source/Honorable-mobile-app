import React, { useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Excalidraw, MainMenu, exportToBlob } from '@excalidraw/excalidraw';
import '@excalidraw/excalidraw/index.css';
import { decodeDraft, encodeDraft } from './scene.mjs';
import './honorable.css';

const KEY = 'honorable-drawing-v1';
function initial() {
  try { return { data: decodeDraft(localStorage.getItem(KEY)), error: false }; } catch { return { data: null, error: true }; }
}
function Editor() {
  const api = useRef(null), timer = useRef(null), latest = useRef(null);
  const [{ data: restored, error: restoreError }] = useState(initial);
  const [status, setStatus] = useState(restoreError ? 'Saved draft could not be read. It is preserved. Choose New drawing to replace it.' : restored ? 'Local drawing restored.' : 'Draw, add shapes or write text.');
  const [busy, setBusy] = useState(false);
  const save = () => {
    try {
      if (!latest.current) return;
      if (restoreError) { setStatus('Saved draft is preserved. Choose New drawing before saving another draft.'); return; }
      localStorage.setItem(KEY, encodeDraft(...latest.current));
      setStatus('Drawing saved on this device.');
    } catch { setStatus('Could not save this drawing. Use a smaller scene or export it.'); }
  };
  window.honorableDrawingBusy = busy;
  window.honorableSaveDraft = save;
  window.honorableExportResult = message => { setBusy(false); setStatus(message); };
  const useImage = async () => {
    if (!api.current || busy) return;
    setBusy(true); setStatus('Preparing image...');
    try {
      const elements = api.current.getSceneElements();
      encodeDraft(elements, api.current.getAppState()); // Apply the same scene bounds before rasterizing.
      if (!elements.some(element => !element.isDeleted)) throw Error('Draw something first.');
      save();
      const blob = await exportToBlob({ elements, files: {}, appState: { ...api.current.getAppState(), exportBackground: true, viewBackgroundColor: '#ffffff', exportWithDarkMode: false }, mimeType: 'image/png', maxWidthOrHeight: 2048 });
      if (!blob || blob.size > 8 * 1024 * 1024) throw Error('The image is too large. Use a smaller drawing.');
      const reader = new FileReader();
      reader.onerror = () => { setBusy(false); setStatus('Could not prepare the image.'); };
      reader.onload = () => {
        if (!window.HonorableDrawing?.usePng) { setBusy(false); setStatus('Open this workspace in the Android app to use the image.'); return; }
        window.HonorableDrawing.usePng(String(reader.result));
      };
      reader.readAsDataURL(blob);
    } catch (error) { setBusy(false); setStatus(error.message || 'Image creation failed. Your drawing is retained.'); }
  };
  return <main>
    <header><strong>Noble <span>draw</span></strong><button onClick={save} disabled={busy}>Save draft</button><button className="primary" onClick={useImage} disabled={busy}>{busy ? 'Preparing...' : 'Use image'}</button></header>
    <section className="canvas" aria-label="Offline drawing workspace">
      <Excalidraw excalidrawAPI={value => { api.current = value; }} theme="light" langCode="en" name="Noble drawing" isCollaborating={false}
        initialData={restored || { appState: { viewBackgroundColor: '#ffffff', currentItemStrokeColor: '#111111', currentItemFontFamily: 2 } }}
        UIOptions={{ tools: { image: false }, canvasActions: { loadScene: false, saveToActiveFile: false, export: false, saveAsImage: false, toggleTheme: false, clearCanvas: false } }}
        validateEmbeddable={false} onLinkOpen={(_, event) => event.preventDefault()} onPaste={() => false}
        onChange={(elements, state) => { latest.current = [elements, state]; clearTimeout(timer.current); timer.current = setTimeout(save, 700); }}>
        <MainMenu><MainMenu.ItemCustom><small>Offline creation powered by Excalidraw.<br/>MIT and font notices are bundled.</small></MainMenu.ItemCustom></MainMenu>
      </Excalidraw>
    </section>
    <footer role="status">{status}<small>Local draft on this device. Use image opens the photo editor; export a copy there to keep the PNG.</small></footer>
  </main>;
}
createRoot(document.getElementById('root')).render(<Editor />);
