import React, {useEffect, useRef, useState} from 'react';
import source from 'noble:neural-style-worker';
import {blendStyle, STYLE_SIDE} from './neural-style.mjs';
import {startNeuralStyle} from './neural-style-client.mjs';
import {DENOISE_MAX} from './neural-denoise.mjs';

function png(image, pixels = image.rgba) {
  const canvas = document.createElement('canvas');
  canvas.width = image.width; canvas.height = image.height;
  const context = canvas.getContext('2d');
  if (!context) throw Error('Texture image preparation is unavailable.');
  context.putImageData(new ImageData(pixels, image.width, image.height), 0, 0);
  return canvas.toDataURL('image/png');
}
async function prepare(url, maximum) {
  if (typeof url !== 'string' || url.length > 2 * 1024 * 1024 || !/^data:image\/(png|jpeg);base64,/.test(url))
    throw Error('Add a local photo texture first.');
  const image = await new Promise((resolve, reject) => {
    const next = new Image(); next.onload = () => resolve(next);
    next.onerror = () => reject(Error('The texture could not be opened.'));
    next.src = url;
  });
  if (!image.width || !image.height || image.width * image.height > 1024 * 1024)
    throw Error('Use a prepared texture up to 1024 pixels per side.');
  const scale = Math.min(1, maximum / Math.max(image.width, image.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(image.width * scale));
  canvas.height = Math.max(1, Math.round(image.height * scale));
  const context = canvas.getContext('2d', {willReadFrequently: true});
  if (!context) throw Error('Texture image preparation is unavailable.');
  context.drawImage(image, 0, 0, canvas.width, canvas.height);
  return {width: canvas.width, height: canvas.height, rgba: context.getImageData(0, 0, canvas.width, canvas.height).data};
}

/** Actual BSD Mosaic model inference, separate from ordinary Three materials. */
export function NeuralTextureStudio({texture, kind = 'style', onApply, onArtwork, onClose}) {
  const denoise = kind === 'denoise', title = denoise ? 'Neural noise cleanup' : 'Neural painted texture';
  const resultAlt = denoise ? 'Reviewed neural noise cleanup' : 'Reviewed neural painted texture';
  const [input, setInput] = useState(null), [output, setOutput] = useState(null),
    [strength, setStrength] = useState(1), [approved, setApproved] = useState(false),
    [busy, setBusy] = useState(false), [message, setMessage] = useState('Preparing your local texture…');
  const life = useRef({mounted: true, generation: 0, busy: false}), task = useRef(null);
  useEffect(() => {
    life.current.mounted = true;
    const hidden = () => {
      if (!document.hidden) return;
      life.current.generation++; life.current.busy = false;
      task.current?.stop(); task.current = null;
      setBusy(false); setApproved(false); setOutput(null);
      setMessage('Neural painting stopped when the app left the foreground. Your texture is unchanged.');
    };
    document.addEventListener('visibilitychange', hidden);
    const generation = life.current.generation;
    prepare(texture, denoise ? DENOISE_MAX : STYLE_SIDE).then(value => {
      if (!life.current.mounted || generation !== life.current.generation) return;
      setInput(value); setMessage('Ready to paint locally. Your original texture is kept.');
    }).catch(error => {if (life.current.mounted && generation === life.current.generation) setMessage(error.message);});
    return () => {life.current.mounted = false; life.current.generation++; task.current?.stop(); task.current = null; document.removeEventListener('visibilitychange', hidden);};
  }, [texture, kind]);
  const stop = () => {
    life.current.generation++; life.current.busy = false;
    task.current?.stop(); task.current = null; setBusy(false);
    setOutput(null); setApproved(false); setMessage('Neural painting stopped. Your original texture is unchanged.');
  };
  const paint = async () => {
    if (!input || life.current.busy || !life.current.mounted || document.hidden) return;
    life.current.busy = true; const generation = ++life.current.generation;
    setBusy(true); setApproved(false); setOutput(null);
    setMessage('Starting the local neural model…');
    const current = () => life.current.mounted && generation === life.current.generation && !document.hidden;
    let job;
    try {
      job = startNeuralStyle(source, input, new URL('/drawing/', location.origin).href,
        value => {if (current()) setMessage(value);}, undefined, kind);
      task.current = job;
      const pixels = await job.promise;
      if (!current()) return;
      setOutput(pixels); setStrength(1);
      setMessage('Review the real neural result below. Nothing is applied or saved yet.');
    } catch (error) {if (current()) setMessage(error.message);}
    finally {
      job?.stop(); if (task.current === job) task.current = null;
      if (current()) {life.current.busy = false; setBusy(false);}
    }
  };
  let original, painted;
  try {
    if (input) original = png(input);
    if (input && output) painted = png(input, blendStyle(input, output, strength));
  } catch { /* A failed canvas cannot enable either apply button. */ }
  const commit = async artwork => {
    if (!painted || !approved || life.current.busy || document.hidden || !life.current.mounted) return;
    life.current.busy = true; setBusy(true);
    const generation = ++life.current.generation;
    try {
      const current = () => {
        if (!life.current.mounted || generation !== life.current.generation || document.hidden)
          throw Error('The reviewed neural copy was stopped before applying.');
      };
      current();
      const accepted = await (artwork ? onArtwork(painted, current) : onApply(painted));
      if (!life.current.mounted || generation !== life.current.generation || document.hidden) return;
      if (accepted === false) throw Error('The reviewed copy could not be added. Your original is unchanged.');
      setApproved(false); setMessage(artwork ? 'A separate painted image was added to the drawing. Drawing Undo removes it.' : 'Reviewed texture applied. Model Undo restores the previous texture; Save model keeps the change.');
      onClose();
    } catch (error) {if (life.current.mounted && generation === life.current.generation) setMessage(error.message);}
    finally {if (life.current.mounted && generation === life.current.generation) {life.current.busy = false; setBusy(false);}}
  };
  return <section aria-label={title} style={{border: '1px solid #999', padding: 12}}>
    <h3>{title}</h3>
    <p>{denoise ? 'A real local DnCNN model reduces luminance noise while retaining the original color differences and transparency. It was trained for Gaussian noise, not every camera artifact or lost detail. Output is at most 256 pixels per side. Overlapped tiles protect seams; processing stays on this device.' : 'A real local Mosaic neural model reinterprets your texture. It does not generate arbitrary prompts or restore detail. Output is at most 224 pixels per side; no upload. Transparent pixels stay transparent.'}</p>
    <button disabled={!input || busy} onClick={paint}>{denoise ? 'Clean noise with local AI' : 'Paint with local AI'}</button>
    {busy && <button onClick={stop}>{denoise ? 'Stop neural cleanup' : 'Stop neural painting'}</button>}
    <div className="tool-grid">
      {original && <figure><img src={original} alt={denoise ? 'Original texture before neural cleanup' : 'Original texture before neural painting'} style={{maxWidth: '100%', imageRendering: 'auto'}} /><figcaption>Original working copy</figcaption></figure>}
      {painted && <figure><img src={painted} alt={resultAlt} style={{maxWidth: '100%'}} /><figcaption>New reviewed copy · {input.width} × {input.height}</figcaption></figure>}
    </div>
    {painted && <>
      <label>{denoise ? 'Noise cleanup strength' : 'Neural paint strength'}<input aria-label={denoise ? 'Noise cleanup strength' : 'Neural paint strength'} type="range" min="0" max="1" step="0.05" value={strength} disabled={busy} onChange={event => {setStrength(Number(event.target.value)); setApproved(false);}} /><output>{Math.round(strength * 100)}%</output></label>
      <label className="check-label"><input type="checkbox" disabled={busy} checked={approved} onChange={event => setApproved(event.target.checked)} />I reviewed this new copy and want to use it.</label>
      <div className="tool-grid"><button disabled={busy || !approved} onClick={() => commit(false)}>Apply reviewed model texture</button><button disabled={busy || !approved} onClick={() => commit(true)}>{denoise ? 'Add cleaned image to drawing' : 'Add painted image to drawing'}</button></div>
    </>}
    <p role="status" aria-live="polite">{message}</p>
    <button onClick={() => {stop(); onClose();}}>{denoise ? 'Close neural cleanup' : 'Close neural texture'}</button>
    <p>Original media stays unchanged. Saved models and drawing copies remain in shared local storage after logout; they are not protected by an account. Applying a texture resets its material tint to white so you can see the painted colors.</p>
  </section>;
}
