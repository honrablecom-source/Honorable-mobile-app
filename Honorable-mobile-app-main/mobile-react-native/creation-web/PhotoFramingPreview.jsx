import React, { useEffect, useRef, useState } from 'react';
import { suggestFraming, drawFramingPreview } from './photo-framing.mjs';

export function PhotoFramingPreview({ target, isCurrent, onApply, onCancel }) {
  const [format, setFormat] = useState('square'), [proposal, setProposal] = useState(null), [busy, setBusy] = useState(false), [message, setMessage] = useState('');
  const request = useRef(0), alive = useRef(true), canvas = useRef(null);
  useEffect(() => { alive.current = true; return () => { alive.current = false; request.current++; }; }, []);
  useEffect(() => { if (proposal && canvas.current) { try { drawFramingPreview(canvas.current, proposal.image, proposal.crop, target.photo); } catch (failure) { setMessage(failure.message); } } }, [proposal, target]);
  const find = async () => {
    const ticket = ++request.current; setProposal(null); setMessage('Finding framing on this device…'); setBusy(true);
    const requireCurrent = () => { if (!alive.current || ticket !== request.current || !isCurrent()) throw Error('The photo or drawing changed. Open suggested framing again.'); };
    try { const next = await suggestFraming(target.dataURL, format, requireCurrent); requireCurrent(); setProposal(next); setMessage('Review this photo-only preview. No edit has been applied.'); }
    catch (failure) { if (alive.current && ticket === request.current) setMessage(failure.message); }
    finally { if (alive.current && ticket === request.current) setBusy(false); }
  };
  return <><h3>Suggested photo framing</h3><p>Find a crop from the full embedded photo copy on your device. Review it before applying. The source bytes and other artwork stay unchanged. Show, unlock and ungroup a photo before using this tool.</p>
    <label>Crop format<select aria-label="Crop format" value={format} disabled={busy} onChange={event => { request.current++; setFormat(event.target.value); setProposal(null); setMessage(''); }}><option value="square">Square 1:1</option><option value="portrait">Portrait 4:5</option><option value="wide">Wide 16:9</option><option value="vertical">Vertical 9:16</option></select></label>
    <button disabled={busy} onClick={find}>{busy ? 'Finding framing…' : 'Find framing'}</button>
    {proposal && <><canvas ref={canvas} role="img" aria-label="Suggested crop preview" style={{ display: 'block', maxWidth: '100%', height: 'auto', margin: '12px auto', border: '1px solid #ddd' }} /><p>Projects and SVG retain the full embedded photo. PNG contains the visible flattened artwork.</p><button onClick={() => { try { if (!isCurrent()) throw Error('The photo or drawing changed. Open suggested framing again.'); onApply(proposal); } catch (failure) { setMessage(failure.message); } }}>Apply suggested crop</button></>}
    <button onClick={onCancel}>Keep current framing</button><p aria-live="polite">{message}</p><p>Applying is one Undo edit. The suggestion is a visual heuristic, not a guarantee that the subject is framed well. Your other artwork stays in place.</p></>;
}
