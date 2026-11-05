import React, { useEffect, useState } from 'react';
import { convertToExcalidrawElements, exportToSvg } from '@excalidraw/excalidraw';
import { starterPlan } from './starters.mjs';

/** Read-only local rendering of the same plan used by insertion. Never accesses the scene. */
export function StarterPreview({ style, headline, details, signoff }) {
  const [preview, setPreview] = useState({ source: '', error: '', pending: true });
  useEffect(() => {
    let active = true, source = '';
    setPreview({ source: '', error: '', pending: true });
    const timer = setTimeout(async () => {
      try {
        const elements = convertToExcalidrawElements(starterPlan(style, headline, details, signoff));
        const svg = await exportToSvg({ elements, files: {}, appState: { exportBackground: true, viewBackgroundColor: '#ffffff', exportWithDarkMode: false, exportEmbedScene: false }, exportPadding: 10 });
        if (!active) return;
        source = URL.createObjectURL(new Blob([svg.outerHTML], { type: 'image/svg+xml' }));
        setPreview({ source, error: '', pending: false });
      } catch (failure) {
        if (active) setPreview({ source: '', error: failure.message || 'Preview unavailable. Your drawing is unchanged.', pending: false });
      }
    }, 200);
    return () => { active = false; clearTimeout(timer); if (source) URL.revokeObjectURL(source); };
  }, [style, headline, details, signoff]);
  return <section className="starter-preview" aria-label="Starter design preview" aria-busy={preview.pending}>
    <strong>Design preview</strong>
    {preview.source && <img src={preview.source} alt="Preview of your editable starter design" />}
    {preview.pending && <p>Updating preview…</p>}
    {preview.error && <p aria-live="polite">{preview.error}</p>}
    <p>Your canvas stays unchanged until you insert the design.</p>
  </section>;
}
