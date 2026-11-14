import gifWorkerSource from 'noble:model-gif-worker';
import { checkedGifOptions, sendGif } from './model-gif.mjs';
import { startGifWorker } from './model-gif-worker-client.mjs';
import { NeuralTextureStudio } from './NeuralTextureStudio.jsx';
import React, { useEffect, useRef, useState } from 'react';
import { loadDepthModel, disposeImportedModel } from './model-import.mjs';
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import {
  MODEL_KEY,
  defaultModel,
  checkedModel,
  readModel,
  buildModel,
  buildDepthModel,
  exportModel,
  sendModel,
} from './model-studio.mjs';

/** A small offline Three.js workspace. This performs ordinary 3D rendering, not AI inference. */
export function ModelStudio({
  onImage,
  captureTexture,
  textureSources = [],
  initialAsset = null,
}) {
  const [initial] = useState(() => {
    try {
      const raw = localStorage.getItem(MODEL_KEY);
      return { spec: raw ? readModel(raw) : defaultModel() };
    } catch {
      return {
        spec: defaultModel(),
        error:
          'The saved model could not be opened. It is preserved until you explicitly save a replacement.',
      };
    }
  });
  const [spec, setSpec] = useState(initial.spec),
    [message, setMessage] = useState(
      (initialAsset
        ? 'A local AI depth model is ready. Save your current model before opening the new relief.'
        : null) ||
        initial.error ||
        'Drag to orbit; pinch to zoom. Everything stays on this device.',
    );
  const [textureSource, setTextureSource] = useState('');
  const [neuralTexture, setNeuralTexture] = useState(null);
  const [ready, setReady] = useState(false),
    [busy, setBusy] = useState(false),
    [playing, setPlaying] = useState(false);
  const host = useRef(null),
    live = useRef(null),
    life = useRef({ mounted: true, revision: 0, busy: false, operation: 0 }),
    history = useRef([]),
    request = useRef(Math.floor(Math.random() * 900_000_000)),
    pending = useRef(null),
    pendingKind = useRef(null),
    gifJob = useRef(null);
  const specRef = useRef(spec);
  specRef.current = spec;
  const playingRef = useRef(playing);
  playingRef.current = playing;
  const status = value => {
    if (life.current.mounted) setMessage(value);
  };
  const releasePending = () => {
    const id = pending.current,
      kind = pendingKind.current;
    pending.current = null;
    pendingKind.current = null;
    if (id === null) return;
    try {
      if (kind === 'gif') window.HonorableDrawing?.releaseGifShare?.(id);
      else window.HonorableDrawing?.releaseModelShare?.(id);
    } catch {
      /* Native pause/close also invalidates retained ownership. */
    }
  };
  useEffect(() => {
    life.current.mounted = true;
    const reply = (kind, result) => {
      if (pending.current !== result?.requestId || pendingKind.current !== kind)
        return;
      pending.current = null;
      pendingKind.current = null;
      life.current.busy = false;
      setBusy(false);
      status(
        result.error ||
          (result.opened
            ? `Android share sheet opened. Choose a destination to keep the ${
                kind === 'gif' ? 'GIF' : 'GLB'
              }; sharing is not proof it was saved.`
            : 'Sharing did not complete.'),
      );
    };
    const modelReply = result => reply('model', result),
      gifReply = result => reply('gif', result);
    window.honorableModelShareResult = modelReply;
    window.honorableGifShareResult = gifReply;
    const hidden = () => {
      if (!document.hidden) return;
      gifJob.current?.stop();
      gifJob.current = null;
      setPlaying(false);
      playingRef.current = false;
      life.current.revision++;
      life.current.operation++;
      releasePending();
      life.current.busy = false;
      setBusy(false);
    };
    document.addEventListener('visibilitychange', hidden);
    return () => {
      gifJob.current?.stop();
      gifJob.current = null;
      life.current.mounted = false;
      life.current.revision++;
      document.removeEventListener('visibilitychange', hidden);
      if (window.honorableModelShareResult === modelReply)
        delete window.honorableModelShareResult;
      if (window.honorableGifShareResult === gifReply)
        delete window.honorableGifShareResult;
      releasePending();
    };
  }, []);
  useEffect(() => {
    const container = host.current;
    let renderer,
      controls,
      model,
      imported,
      texture,
      frame = 0,
      disposed = false;
    setReady(false);
    setPlaying(false);
    const lost = event => {
      event.preventDefault();
      setReady(false);
      status(
        '3D graphics were interrupted. Close and reopen 3D model; your saved project is preserved.',
      );
    };
    const start = async () => {
      try {
        if (spec.shape === 'depth') {
          imported = await loadDepthModel(spec.asset);
          if (disposed) {
            disposeImportedModel(imported);
            imported = null;
            return;
          }
        }
        if (spec.texture) {
          const image = await new Promise((resolve, reject) => {
            const img = new Image();
            img.onload = () => resolve(img);
            img.onerror = () =>
              reject(Error('The saved texture could not be opened.'));
            img.src = spec.texture;
          });
          if (disposed) return;
          texture = new THREE.Texture(image);
          texture.colorSpace = THREE.SRGBColorSpace;
          texture.needsUpdate = true;
        }
        if (disposed) return;
        renderer = new THREE.WebGLRenderer({
          antialias: true,
          preserveDrawingBuffer: true,
        });
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
        renderer.setSize(320, 280);
        renderer.setClearColor(0xf3f3f3, 1);
        renderer.domElement.setAttribute(
          'aria-label',
          'Interactive 3D model preview',
        );
        renderer.domElement.style.cssText =
          'display:block;width:100%;height:280px;touch-action:none';
        renderer.domElement.addEventListener('webglcontextlost', lost);
        container.appendChild(renderer.domElement);
        const scene = new THREE.Scene(),
          camera = new THREE.PerspectiveCamera(40, 320 / 280, 0.1, 100);
        camera.position.set(
          ...(spec.shape === 'depth' ? [0, 0, 7] : [6, 4, 7]),
        );
        scene.add(new THREE.HemisphereLight(0xffffff, 0x666666, 3));
        const light = new THREE.DirectionalLight(0xffffff, 3);
        light.position.set(3, 5, 4);
        scene.add(light);
        model =
          spec.shape === 'depth'
            ? buildDepthModel(spec, imported, texture)
            : buildModel(spec, texture);
        scene.add(model.root);
        controls = new OrbitControls(camera, renderer.domElement);
        controls.minDistance = 3;
        controls.maxDistance = 20;
        controls.enablePan = false;
        controls.target.set(0, 0, 0);
        controls.update();
        const mixer = new THREE.AnimationMixer(model.root);
        for (const clip of model.clips) mixer.clipAction(clip).play();
        const clock = new THREE.Clock();
        live.current = { renderer, scene, camera, model, mixer, controls };
        const tick = () => {
          if (disposed) return;
          const delta = Math.min(clock.getDelta(), 0.1);
          if (!document.hidden) {
            if (playingRef.current) mixer.update(delta);
            const width = Math.max(1, Math.min(container.clientWidth, 720));
            if (
              renderer.domElement.clientWidth !== width ||
              renderer.domElement.width !==
                Math.floor(width * renderer.getPixelRatio())
            ) {
              renderer.setSize(width, 280, false);
              camera.aspect = width / 280;
              camera.updateProjectionMatrix();
            }
            controls.update();
            renderer.render(scene, camera);
          }
          frame = requestAnimationFrame(tick);
        };
        tick();
        setReady(true);
      } catch (error) {
        if (!disposed)
          status(
            '3D preview unavailable: ' +
              error.message +
              ' Your drawing and saved model are unchanged.',
          );
      }
    };
    start();
    return () => {
      disposed = true;
      cancelAnimationFrame(frame);
      live.current = null;
      controls?.dispose();
      model?.dispose();
      if (imported && !model) disposeImportedModel(imported);
      texture?.dispose();
      if (renderer) {
        renderer.domElement.removeEventListener('webglcontextlost', lost);
        renderer.dispose();
        renderer.forceContextLoss();
        renderer.domElement.remove();
      }
    };
  }, [spec]);
  const change = patch => {
    if (life.current.busy) return;
    try {
      const next = checkedModel({ ...spec, ...patch });
      history.current = [...history.current.slice(-19), spec];
      life.current.revision++;
      setSpec(next);
      status('Model changed. Save model to keep it after closing.');
      return true;
    } catch (error) {
      status(error.message);
      return false;
    }
  };
  const run = async action => {
    if (life.current.busy || !ready || !live.current) return;
    const revision = life.current.revision,
      operation = ++life.current.operation;
    life.current.busy = true;
    setBusy(true);
    const current = () => {
      if (
        !life.current.mounted ||
        document.hidden ||
        revision !== life.current.revision
      )
        throw Error('Model operation stopped. Try again when active.');
    };
    try {
      await action(current);
    } catch (error) {
      if (life.current.operation === operation) status(error.message);
    } finally {
      if (
        life.current.mounted &&
        life.current.operation === operation &&
        pending.current === null
      ) {
        life.current.busy = false;
        setBusy(false);
      }
    }
  };
  const textureFile = async file => {
    if (!file || life.current.busy) return;
    const revision = life.current.revision,
      operation = ++life.current.operation;
    life.current.busy = true;
    setBusy(true);
    let url;
    try {
      if (
        !['image/png', 'image/jpeg'].includes(file.type) ||
        file.size > 8 * 1024 * 1024
      )
        throw Error('Choose a PNG or JPEG up to 8 MiB.');
      url = URL.createObjectURL(file);
      const img = await new Promise((resolve, reject) => {
        const image = new Image();
        image.onload = () => resolve(image);
        image.onerror = () => reject(Error('Photo could not be decoded.'));
        image.src = url;
      });
      if (
        !life.current.mounted ||
        document.hidden ||
        revision !== life.current.revision
      )
        return;
      if (img.width * img.height > 24 * 1024 * 1024)
        throw Error('Choose a texture photo up to 24 megapixels.');
      const canvas = document.createElement('canvas'),
        scale = Math.min(1, 1024 / Math.max(img.width, img.height));
      canvas.width = Math.max(1, Math.round(img.width * scale));
      canvas.height = Math.max(1, Math.round(img.height * scale));
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
      const value = canvas.toDataURL('image/png');
      const next = checkedModel({ ...specRef.current, texture: value });
      history.current = [...history.current.slice(-19), specRef.current];
      life.current.revision++;
      setSpec(next);
      status(
        'Local photo texture added. Save model to keep it. Original photo unchanged.',
      );
    } catch (error) {
      if (life.current.operation === operation) status(error.message);
    } finally {
      if (url) URL.revokeObjectURL(url);
      if (life.current.mounted && life.current.operation === operation) {
        life.current.busy = false;
        setBusy(false);
      }
    }
  };
  const share = () =>
    run(async current => {
      const bytes = await exportModel(
        live.current.model.root,
        live.current.model.clips,
      );
      current();
      if (window.HonorableDrawing) {
        const id = (request.current = (request.current % 1_000_000_000) + 1);
        pending.current = id;
        pendingKind.current = 'model';
        try {
          status('Preparing Android model share sheet…');
          sendModel(window.HonorableDrawing, id, bytes);
        } catch (error) {
          pending.current = null;
          pendingKind.current = null;
          throw error;
        }
      } else {
        const url = URL.createObjectURL(
          new Blob([bytes], { type: 'model/gltf-binary' }),
        );
        const link = document.createElement('a');
        link.href = url;
        link.download = 'Noble-model.glb';
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 60000);
        status('GLB download requested. Check your downloads.');
      }
    });
  const shareGif = () =>
    run(async current => {
      const source = live.current,
        dimensions = source.renderer.getSize(new THREE.Vector2()),
        scale = Math.min(1, 384 / dimensions.x, 384 / dimensions.y);
      const options = checkedGifOptions({
        width: Math.floor(dimensions.x * scale),
        height: Math.floor(dimensions.y * scale),
        duration: spec.duration,
      });
      if (!spec.animate)
        throw Error('Include an animation before sharing GIF.');
      const canvas = document.createElement('canvas');
      canvas.width = options.width;
      canvas.height = options.height;
      const context = canvas.getContext('2d');
      if (!context) throw Error('GIF frame capture is unavailable.');
      const camera = source.camera.clone(),
        time = source.mixer.time,
        quaternion = source.model.root.quaternion.clone(),
        enabled = source.controls.enabled;
      const controller = new AbortController();
      let worker,
        restored = false;
      const restore = () => {
        if (restored) return;
        restored = true;
        if (live.current === source && gifJob.current === task) {
          source.mixer.setTime(time);
          source.model.root.quaternion.copy(quaternion);
          source.controls.enabled = enabled;
        }
      };
      const task = {
        stop() {
          controller.abort();
          worker?.stop();
          restore();
        },
      };
      gifJob.current = task;
      setPlaying(false);
      playingRef.current = false;
      source.controls.enabled = false;
      let bytes;
      try {
        status(`Rendering GIF 0/${options.frames} on this device…`);
        worker = startGifWorker(gifWorkerSource, options, controller.signal);
        await worker.ready;
        for (let number = 0; number < options.frames; number++) {
          current();
          if (controller.signal.aborted || live.current !== source)
            throw Error('GIF export stopped. No GIF was shared.');
          source.mixer.setTime(number / 10);
          source.renderer.render(source.scene, camera);
          context.drawImage(
            source.renderer.domElement,
            0,
            0,
            options.width,
            options.height,
          );
          await worker.frame(
            number,
            context.getImageData(0, 0, options.width, options.height).data
              .buffer,
          );
          current();
          status(
            `Rendering GIF ${number + 1}/${options.frames} on this device…`,
          );
        }
        bytes = await worker.finish();
        current();
        if (controller.signal.aborted)
          throw Error('GIF export stopped. No GIF was shared.');
      } finally {
        worker?.stop();
        restore();
        if (gifJob.current === task) gifJob.current = null;
      }
      if (window.HonorableDrawing) {
        const id = (request.current = (request.current % 1_000_000_000) + 1);
        pending.current = id;
        pendingKind.current = 'gif';
        try {
          status('Preparing Android GIF share sheet…');
          sendGif(window.HonorableDrawing, id, bytes);
        } catch (error) {
          pending.current = null;
          pendingKind.current = null;
          throw error;
        }
      } else {
        const url = URL.createObjectURL(
            new Blob([bytes], { type: 'image/gif' }),
          ),
          link = document.createElement('a');
        link.href = url;
        link.download = 'Noble-animation.gif';
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 60000);
        status('GIF download requested. Check your downloads.');
      }
    });
  const stopGif = () => {
    gifJob.current?.stop();
    gifJob.current = null;
    if (pendingKind.current === 'gif') releasePending();
    life.current.revision++;
    life.current.operation++;
    life.current.busy = false;
    setBusy(false);
    status(
      'GIF export stopped. Check an already-open share destination before retrying.',
    );
  };
  const image = () =>
    run(async current => {
      const view = live.current;
      view.renderer.render(view.scene, view.camera);
      const blob = await new Promise(resolve =>
        view.renderer.domElement.toBlob(resolve, 'image/png'),
      );
      current();
      if (!blob) throw Error('The preview image could not be prepared.');
      const added = await onImage(
        new File([blob], 'Noble-model.png', { type: 'image/png' }),
      );
      if (added === false)
        throw Error(
          'Model preview was not added. Check drawing storage and try again.',
        );
      status(
        'Model preview added as a separate drawing image. Use drawing Undo to remove it.',
      );
    });
  return (
    <section aria-label="3D model studio">
      <h3>3D model</h3>
      <p>
        Open a locally estimated AI depth relief, or shape a simple object. Edit
        its material and depth, preview animation, and export GLB. Three.js
        powers the workspace; AI depth estimation happens in the photo editor.
      </p>
      {initialAsset && (
        <button
          disabled={busy || neuralTexture !== null}
          onClick={() =>
            change({
              ...defaultModel(),
              shape: 'depth',
              asset: initialAsset,
              width: 3,
              height: 3,
              depth: 1,
              color: '#ffffff',
              animate: true,
            })
          }
        >
          Open AI depth relief
        </button>
      )}
      {spec.shape === 'depth' && (
        <p>
          AI estimates relative depth from one view. This is a textured 2.5D
          relief, not complete hidden surfaces or measured 3D. Depth changes the
          relief strength; parallax is a gentle side-to-side motion. Large view
          changes can expose stretched edges.
        </p>
      )}
      <div
        ref={host}
        style={{
          minHeight: 280,
          background: '#f3f3f3',
          border: '1px solid #ccc',
        }}
      />
      <fieldset disabled={busy || neuralTexture !== null} style={{ border: 0, padding: 0 }}>
        <label>
          Shape
          <select
            aria-label="Model shape"
            value={spec.shape}
            onChange={event => change({ shape: event.target.value })}
          >
            {[
              ...(spec.asset ? ['depth'] : []),
              'box',
              'sphere',
              'cylinder',
              'cone',
              'torus',
            ].map(shape => (
              <option key={shape}>{shape}</option>
            ))}
          </select>
        </label>
        <div className="tool-grid">
          {['width', 'height', 'depth'].map(name => (
            <label key={name}>
              Model {name}
              <input
                aria-label={`Model ${name}`}
                type="range"
                min="0.1"
                max="5"
                step="0.1"
                value={spec[name]}
                onChange={event =>
                  change({ [name]: Number(event.target.value) })
                }
              />
              <output>{spec[name]}</output>
            </label>
          ))}
        </div>
        <label>
          Material color
          <input
            aria-label="Model color"
            type="color"
            value={spec.color}
            onChange={event => change({ color: event.target.value })}
          />
        </label>
        {['roughness', 'metalness'].map(name => (
          <label key={name}>
            Material {name}
            <input
              aria-label={`Model ${name}`}
              type="range"
              min="0"
              max="1"
              step="0.05"
              value={spec[name]}
              onChange={event => change({ [name]: Number(event.target.value) })}
            />
          </label>
        ))}
        <label>
          Drawing photo texture
          <select
            aria-label="Drawing photo texture"
            value={textureSource}
            onChange={event => setTextureSource(event.target.value)}
          >
            <option value="">Choose a drawing photo</option>
            {textureSources.map((source, index) => (
              <option key={source.id} value={source.id}>
                Photo {index + 1}
              </option>
            ))}
          </select>
        </label>
        <button
          disabled={!textureSource}
          onClick={() => {
            try {
              textureFile(captureTexture(textureSource));
            } catch (error) {
              status(error.message);
            }
          }}
        >
          Use drawing photo as texture
        </button>
        {!window.HonorableDrawing && (
          <label>
            Local photo texture
            <input
              aria-label="Model texture photo"
              type="file"
              accept="image/png,image/jpeg"
              onChange={event => {
                const file = event.target.files?.[0];
                event.target.value = '';
                textureFile(file);
              }}
            />
          </label>
        )}
        <p>
          Choose a visible, unlocked drawing photo above to use it as a texture.
          Use Add photo to import one on Android. The full embedded photo is
          copied before crop or rotation, at up to 1024 pixels; no upload. Color
          tints the texture. GLB embeds this copy, so share only photos you
          intend to include.
        </p>
        {spec.texture && (
          <button onClick={() => change({ texture: null })}>
            Remove texture
          </button>
        )}
        {[['style','Neural painted texture'],['denoise','Neural noise cleanup']].map(([kind,label]) => <button key={kind} disabled={!ready} onClick={() => {
          try {
            let image;
            live.current?.model.root.traverse(object => {
              if (image || !object.isMesh) return;
              for (const material of Array.isArray(object.material) ? object.material : [object.material])
                if (material?.map?.image) {image = material.map.image; break;}
            });
            if (!image) throw Error('Add a local photo texture or open an AI depth relief first.');
            const canvas = document.createElement('canvas');
            canvas.width = image.width; canvas.height = image.height;
            if (!canvas.width || !canvas.height || canvas.width > 1024 || canvas.height > 1024)
              throw Error('Use a prepared photo texture up to 1024 pixels per side.');
            const context = canvas.getContext('2d');
            if (!context) throw Error('Local texture preparation is unavailable.');
            context.drawImage(image, 0, 0);
            const value = canvas.toDataURL('image/png');
            if (value.length > 2 * 1024 * 1024) throw Error('The texture is too large. Choose a smaller local photo.');
            setPlaying(false); playingRef.current = false;
            setNeuralTexture({value,kind});
          } catch (error) {status(error.message);}
        }}>{label}</button>)}
        <label className="check-label">
          <input
            type="checkbox"
            checked={spec.animate}
            onChange={event => change({ animate: event.target.checked })}
          />
          {spec.shape === 'depth'
            ? 'Include parallax animation'
            : 'Include turntable animation'}
        </label>
        {spec.animate && (
          <>
            <label>
              {spec.shape === 'depth'
                ? 'Parallax seconds'
                : 'Turntable seconds'}
              <input
                aria-label="Turntable seconds"
                type="range"
                min="1"
                max="12"
                value={spec.duration}
                onChange={event =>
                  change({ duration: Number(event.target.value) })
                }
              />
              <output>{spec.duration}</output>
            </label>
            <button disabled={!ready} onClick={() => setPlaying(!playing)}>
              {spec.shape === 'depth'
                ? playing
                  ? 'Pause parallax'
                  : 'Play parallax'
                : playing
                ? 'Pause turntable'
                : 'Play turntable'}
            </button>
          </>
        )}
        <div className="tool-grid">
          <button
            disabled={!history.current.length}
            onClick={() => {
              life.current.revision++;
              setSpec(history.current.pop());
              status('Previous model edit restored. Save model to keep it.');
            }}
          >
            Undo model edit
          </button>
          <button
            onClick={() => {
              try {
                localStorage.setItem(
                  MODEL_KEY,
                  JSON.stringify(checkedModel(spec)),
                );
                status(
                  'Model saved on this device, including its texture and animation settings.',
                );
              } catch {
                status(
                  'Model could not be saved. Storage may be full. Your previous saved model is preserved.',
                );
              }
            }}
          >
            Save model
          </button>
          <button disabled={!ready} onClick={share}>
            Share GLB
          </button>
          <button disabled={!ready || !spec.animate} onClick={shareGif}>
            Share GIF animation
          </button>
          <button disabled={!ready} onClick={image}>
            Add preview to drawing
          </button>
        </div>
      </fieldset>
      {neuralTexture !== null && <NeuralTextureStudio texture={neuralTexture.value} kind={neuralTexture.kind}
        onClose={() => setNeuralTexture(null)}
        onApply={value => change({texture: value, color: '#ffffff'})}
        onArtwork={async (value, current) => {
          current();
          const data = value.slice(value.indexOf(',') + 1), decoded = atob(data);
          const bytes = Uint8Array.from(decoded, c => c.charCodeAt(0));
          return onImage(new File([bytes], 'Noble-neural-painted.png', {type: 'image/png'}), current);
        }} />}
      {busy && (gifJob.current || pendingKind.current === 'gif') && (
        <button onClick={stopGif}>Stop GIF export</button>
      )}

      <p role="status" aria-live="polite">
        {message}
      </p>
      <p>
        Saved models and drawing copies use shared local storage on this device,
        remain after logout, and are not protected by an account. One object per
        model. The model has its own saved draft and undo history; drawing Save
        draft does not save it. Animation exports inside GLB. GIF uses your
        framed preview and background, up to 384 px at 10 fps for 1–6 seconds.
        GIF has no audio and can show color banding. Pinch to frame your
        animation before sharing. Processing stays on your device. Unsaved edits
        are lost when closing this panel.
      </p>
    </section>
  );
}
