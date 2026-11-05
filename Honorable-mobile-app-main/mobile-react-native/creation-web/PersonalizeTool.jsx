import React, { useEffect, useRef, useState } from 'react';
import { exportToSvg, getCommonBounds } from '@excalidraw/excalidraw';
import { personalizedCopies } from './personalize.mjs';
import { fitPersonalizedText } from './personalize-text.mjs';
import { verifyPhotoCopies } from './photo-import.mjs';
import { filesForElements } from './image-assets.mjs';

export function PersonalizeTool({ captureSource, onApply }) {
  const [input, setInput] = useState('name | message\nAva | Welcome!\nNoah | Glad you are here.');
  const [columns, setColumns] = useState('2'), [proposal, setProposal] = useState(null);
  const [url, setUrl] = useState(''), [busy, setBusy] = useState(false), [message, setMessage] = useState('');
  const alive = useRef(true), request = useRef(0);
  useEffect(() => { alive.current = true; return () => { alive.current = false; request.current++; }; }, []);
  useEffect(() => {
    if (!proposal) { setUrl(''); return; }
    const value = URL.createObjectURL(new Blob([proposal.svg], { type: 'image/svg+xml' })); setUrl(value);
    return () => URL.revokeObjectURL(value);
  }, [proposal]);
  const clear = () => { request.current++; setProposal(null); setMessage(''); };
  const preview = async () => {
    const ticket = ++request.current; setBusy(true); setProposal(null); setMessage('Preparing personalized copies on this device…');
    let source;
    const guard = () => {
      if (!alive.current || ticket !== request.current || !source?.isCurrent()) throw Error('The selection or drawing changed. Preview the copies again.');
    };
    try {
      source = captureSource(); guard();
      await document.fonts.ready; guard();
      const files = filesForElements(source.template, source.files);
      await verifyPhotoCopies(files); guard();
      const result = await personalizedCopies({
        elements: source.elements, template: source.template, input, columns: Number(columns),
        measure: getCommonBounds, fit: (elements, changed) => fitPersonalizedText(elements, changed, guard),
        makeId: source.makeId, requireCurrent: guard,
      });
      const ids = new Set(result.insertedIds), inserted = result.elements.filter(element => ids.has(element.id));
      const svg = await exportToSvg({ elements: inserted, files, appState: { exportBackground: true, viewBackgroundColor: '#ffffff', exportWithDarkMode: false, exportEmbedScene: false }, exportPadding: 10 });
      guard(); source.validate(result.elements);
      setProposal({ ...result, svg: svg.outerHTML, source });
      setMessage(`${result.copyCount} new editable copies. Review every row before inserting.`);
    } catch (error) { if (alive.current && ticket === request.current) setMessage(error.message); }
    finally { if (alive.current && ticket === request.current) setBusy(false); }
  };
  return <>
    <h3>Personalized copies</h3>
    <p>Select a design on the canvas, including its text. Edit its variable text to use fields such as <strong>{'Hi {{name}}'}</strong>. A grouped design or a shape with attached text can be selected together. The original stays unchanged.</p>
    <label>Rows for personalized copies<textarea aria-label="Rows for personalized copies" rows={6} maxLength={8192} value={input} disabled={busy} onChange={event => { setInput(event.target.value); clear(); }} /></label>
    <p>Paste tabs from a spreadsheet or separate columns with |. First row: field names. Each later row makes one copy. Use 1–8 columns, 1–24 value rows and up to 64 characters per value. Field names must match your design; values appear as plain text.</p>
    <label>Copies per row<select aria-label="Personalized copies per row" value={columns} disabled={busy} onChange={event => { setColumns(event.target.value); clear(); }}>{[1, 2, 3, 4].map(value => <option key={value} value={value}>{value}</option>)}</select></label>
    <button disabled={busy} onClick={preview}>{busy ? 'Preparing personalized copies…' : 'Preview personalized copies'}</button>
    {proposal && url && <>
      <img src={url} alt="Preview of personalized editable copies" style={{ display: 'block', width: '100%', maxHeight: 480, objectFit: 'contain', margin: '12px 0', border: '1px solid #ddd', background: '#fff' }} />
      <p>Fields used: {proposal.fields.join(', ')}.</p>
      <details><summary>Review all {proposal.copyCount} value rows</summary><ol>{proposal.rows.map((row, index) => <li key={index} style={{ overflowWrap: 'anywhere' }}>{row.join(' · ')}</li>)}</ol></details>
      <p>Text wraps and can shrink to 8 pixels to fit the source boxes. If it cannot fit, enlarge your design or shorten the values. Each row becomes its own editable group beside the drawing. One Undo removes the whole insertion.</p>
      <button disabled={busy} onClick={() => { try { if (!proposal.source.isCurrent()) throw Error('The selection or drawing changed. Preview the copies again.'); onApply(proposal); } catch (error) { setMessage(error.message); } }}>Insert personalized copies</button>
      <button disabled={busy} onClick={clear}>Discard copy preview</button>
    </>}
    <p aria-live="polite">{message}</p>
    <p>Inserted values stay editable in the drawing. Your pasted rows stay only in this open panel. Photos remain embedded local copies; nothing is uploaded. Previewing does not change your artwork.</p>
  </>;
}
