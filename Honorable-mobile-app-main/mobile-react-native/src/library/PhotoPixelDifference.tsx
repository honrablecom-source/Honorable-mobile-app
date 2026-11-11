import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  AppState,
  Image,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useMemoryPass } from '../passes/MemoryPassContext';
import {
  honorableNative,
  type LibraryCollectionItem,
  type PhotoDifferencePreview,
} from '../native/HonorableNative';

let serial = 0;
type Props = {
  operation?:
    | 'difference'
    | 'clean-plate'
    | 'focus-stack'
    | 'retarget'
    | 'exposure-blend';
  items: LibraryCollectionItem[];
  disabled: boolean;
  canStart: () => boolean;
  onBusy: (value: boolean) => void;
};
export function differenceThreshold(text: string): number {
  if (!/^\d{1,3}$/.test(text) || Number(text) > 255)
    throw Error('Use a whole threshold from 0 to 255.');
  return Number(text);
}
export function narrowingPercent(text: string): number {
  if (!/^(?:[1-9]|1[0-2])$/.test(text))
    throw Error('Use a whole narrowing percent from 1 to 12.');
  return Number(text);
}
export function PhotoPixelDifferencePanel(props: Props) {
  const exposure = props.operation === 'exposure-blend';
  const retarget = props.operation === 'retarget';
  const focus = props.operation === 'focus-stack';
  const clean = props.operation === 'clean-plate';
  const stack = clean || focus || exposure;
  const auth = useMemoryPass();
  const owner = auth.signedIn ? auth.account?.accountId ?? '' : '';
  const selection =
    JSON.stringify({ clean, focus, items: props.items }) +
    (retarget ? ':retarget' : '') +
    (exposure ? ':exposure' : '');
  const ownerRef = useRef(owner),
    selectionRef = useRef(selection);
  ownerRef.current = owner;
  selectionRef.current = selection;
  const callbacks = useRef(props);
  callbacks.current = props;
  const active = useRef(true);
  const job = useRef<
    { id: string; owner: string; selection: string } | undefined
  >(undefined);
  const prepared = useRef<PhotoDifferencePreview | undefined>(undefined);
  const view = useRef<object | undefined>(undefined);
  const [viewToken, setViewToken] = useState<object>();
  const [open, setOpen] = useState(false),
    [busy, setBusy] = useState(false);
  const [threshold, setThreshold] = useState(retarget ? '10' : '0'),
    [error, setError] = useState('');
  const [preview, setPreview] = useState<PhotoDifferencePreview>();
  const release = (token: string) => {
    try {
      honorableNative.releasePhotoDifference(token);
    } catch {
      /* Native cleanup may already have happened. */
    }
  };
  const dropPreview = useCallback(() => {
    const value = prepared.current;
    prepared.current = undefined;
    if (value) {
      try {
        honorableNative.releasePhotoDifference(value.token);
      } catch {
        /* Native cleanup may already have happened. */
      }
    }
    setPreview(undefined);
  }, []);
  const stop = useCallback(() => {
    const current = job.current;
    job.current = undefined;
    if (current) {
      try {
        honorableNative.cancelLibraryDuplicateCheck(current.id);
      } catch {
        /* Native transport may have closed. */
      } finally {
        callbacks.current.onBusy(false);
      }
    }
  }, []);
  const close = useCallback(() => {
    view.current = undefined;
    stop();
    dropPreview();
    setViewToken(undefined);
    setOpen(false);
    setBusy(false);
    setError('');
    setThreshold(retarget ? '10' : '0');
  }, [stop, dropPreview, retarget]);
  useEffect(() => {
    close();
  }, [owner, selection, close]);
  useEffect(() => {
    active.current = true;
    const listener = AppState.addEventListener('change', state => {
      active.current = state === 'active';
      if (!active.current) close();
    });
    return () => {
      active.current = false;
      stop();
      const value = prepared.current;
      prepared.current = undefined;
      if (value) {
        try {
          honorableNative.releasePhotoDifference(value.token);
        } catch {
          /* Native host may already be gone. */
        }
      }
      listener.remove();
    };
  }, [close, stop]);
  const validCount = (count: number) =>
    retarget
      ? count === 1
      : focus
      ? count >= 2 && count <= 5
      : clean
      ? count === 3 || count === 5
      : count === 2;
  const validItems =
    validCount(props.items.length) &&
    props.items.every(item => item.kind === 'IMAGE');
  const show = () => {
    if (
      !active.current ||
      job.current ||
      props.disabled ||
      !props.canStart() ||
      !ownerRef.current ||
      ownerRef.current !== owner ||
      selectionRef.current !== selection ||
      !validItems
    )
      return;
    close();
    const token = {};
    view.current = token;
    setViewToken(token);
    setOpen(true);
  };
  const start = async (action: 'prepare' | 'open') => {
    const latest = callbacks.current;
    if (
      !active.current ||
      job.current ||
      latest.disabled ||
      !latest.canStart() ||
      !ownerRef.current ||
      ownerRef.current !== owner ||
      selectionRef.current !== selection ||
      view.current !== viewToken ||
      !open ||
      !validCount(latest.items.length) ||
      latest.items.some(item => item.kind !== 'IMAGE')
    )
      return;
    let value = 0;
    try {
      value = retarget
        ? narrowingPercent(threshold)
        : stack
        ? 0
        : differenceThreshold(threshold);
    } catch (reason) {
      setError((reason as Error).message);
      return;
    }
    const old = prepared.current;
    if (action === 'open' && !old) return;
    const request = {
      id: `noble-pixel-${action}-${Date.now()}-${++serial}`,
      owner: ownerRef.current,
      selection: selectionRef.current,
    };
    job.current = request;
    view.current = request;
    setViewToken(request);
    latest.onBusy(true);
    setBusy(true);
    setError('');
    if (action === 'prepare') dropPreview();
    const current = () =>
      active.current &&
      job.current === request &&
      ownerRef.current === request.owner &&
      selectionRef.current === request.selection;
    try {
      if (action === 'prepare') {
        const result = exposure
          ? await honorableNative.preparePhotoExposureBlend(
              latest.items.map(item => ({ ...item })),
              request.id,
            )
          : retarget
          ? await honorableNative.preparePhotoRetarget(
              latest.items.map(item => ({ ...item })),
              value,
              request.id,
            )
          : focus
          ? await honorableNative.preparePhotoFocusStack(
              latest.items.map(item => ({ ...item })),
              request.id,
            )
          : clean
          ? await honorableNative.preparePhotoCleanPlate(
              latest.items.map(item => ({ ...item })),
              request.id,
            )
          : await honorableNative.preparePhotoDifference(
              latest.items.map(item => ({ ...item })),
              value,
              request.id,
            );
        if (!current()) {
          release(result.token);
          return;
        }
        if (
          (exposure &&
            (result.operation !== 'exposure-blend' || result.frames !== 2)) ||
          (retarget &&
            (result.operation !== 'retarget' ||
              result.frames !== 1 ||
              !Number.isSafeInteger(result.removedColumns) ||
              (result.removedColumns ?? 0) < 1 ||
              (result.removedColumns ?? 129) > 128)) ||
          (focus && result.operation !== 'focus-stack') ||
          (clean &&
            (result.operation !== 'clean-plate' ||
              result.frames !== latest.items.length)) ||
          (focus && result.frames !== latest.items.length)
        ) {
          release(result.token);
          throw Error('Capture-set mismatch. Prepare again.');
        }
        prepared.current = result;
        setPreview(result);
      } else {
        const result = await honorableNative.openPhotoDifference(
          old!.token,
          request.id,
        );
        if (!current()) return;
        if (!result.opened)
          throw Error(
            'The separate photo workspace could not open. Prepare another map.',
          );
        prepared.current = undefined;
        setPreview(undefined);
        close();
      }
    } catch (reason) {
      if (current()) {
        if (action === 'open') dropPreview();
        setError(
          reason instanceof Error
            ? reason.message
            : 'The map could not be prepared. Originals were kept.',
        );
      }
    } finally {
      if (current()) {
        job.current = undefined;
        setBusy(false);
        callbacks.current.onBusy(false);
      }
    }
  };
  const closeCurrent = () => {
    if (view.current === viewToken) close();
  };
  const button = (label: string, action: () => void, disabled = false) => (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      disabled={disabled}
      accessibilityState={{ disabled }}
      onPress={action}
      style={[styles.button, disabled && styles.disabled]}
    >
      <Text style={styles.label}>{label}</Text>
    </Pressable>
  );
  return (
    <View>
      {button(
        exposure
          ? 'Blend exposure brackets'
          : retarget
          ? 'Create content-aware narrowed photo'
          : stack
          ? focus
            ? 'Create aligned focus stack'
            : 'Create aligned-burst clean plate'
          : 'Create photo pixel-difference map',
        show,
        props.disabled || !owner || !validItems,
      )}
      <Modal visible={open} animationType="slide" onRequestClose={closeCurrent}>
        <ScrollView
          contentContainerStyle={styles.sheet}
          keyboardShouldPersistTaps="handled"
        >
          <Text accessibilityRole="header" style={styles.title}>
            {exposure
              ? 'Blend exposure brackets'
              : retarget
              ? 'Content-aware photo narrowing'
              : stack
              ? focus
                ? 'Aligned focus stack'
                : 'Aligned-burst clean plate'
              : 'Photo pixel differences'}
          </Text>
          {exposure ? (
            <Text style={styles.copy}>
              Select two already aligned exposure-bracket photos with equal
              orientation-corrected dimensions. A per-pixel encoded-luminance
              confidence favors midtone samples, with alpha-correct color
              blending. This is an 8-bit blend, not HDR reconstruction: clipped
              detail cannot be recovered, and different light, white balance or
              motion can produce ghosts, halos or color shifts. No automatic
              alignment or subject recognition. Both originals remain protected.
              The separate PNG uses at most 1024 pixels per edge with readable
              EXIF orientation and nearest-neighbor normalization; animations
              use the first decoded image. Review before opening.
            </Text>
          ) : retarget ? (
            <Text style={styles.copy}>
              Select one photo. This removes connected vertical paths with low
              premultiplied RGB/alpha gradient energy, rather than simply
              cropping or scaling. It can distort subjects or remove important
              details; there is no subject recognition or protection guarantee.
              Narrow by 1–12% of the decoded grid width, rounded upward to whole
              columns (at least one). Height stays fixed. Processing is on a
              grid at most 1024 pixels per edge, with readable EXIF correction
              and nearest-neighbor normalization; animations use the first
              decoded image. Review the actual separate PNG before opening it.
              Originals stay protected.
            </Text>
          ) : focus ? (
            <Text style={styles.copy}>
              Select two to five already aligned focus-bracket photos with equal
              orientation-corrected dimensions. A local five-by-five contrast
              measure chooses sharper regions from actual source pixels; ties
              use selection order. No automatic alignment, depth inference,
              blending or guaranteed all-in-focus reconstruction. Motion,
              exposure differences, noise and focus breathing can cause seams or
              select the wrong source. Use a steady camera. The separate PNG is
              sampled to at most 1024 pixels per edge with nearest-neighbor
              normalization; readable EXIF applies and animations use their
              first decoded image.
            </Text>
          ) : clean ? (
            <Text style={styles.copy}>
              Select three or five aligned steady-camera photos with equal
              orientation-corrected dimensions. Background must dominate the
              captures to remove a transient object; persistent objects remain.
              No automatic alignment: camera motion causes artifacts. Each
              output pixel is the actual source pixel closest to the temporal
              median in premultiplied RGB and alpha; selection order resolves
              ties. Output is a separate PNG on a sampled grid at most 1024
              pixels per edge, normalized with nearest-neighbor sampling.
              Readable EXIF orientation applies; animated formats use the first
              decoded image.
            </Text>
          ) : (
            <>
              <Text style={styles.copy}>
                Compare exactly two selected photo originals with equal
                orientation-corrected source dimensions. No automatic alignment
                or crop is performed. Readable EXIF orientation is applied;
                otherwise the image is treated as upright. Animated formats use
                the decoder's first image only.
              </Text>
              <Text style={styles.copy}>
                This creates a separate black-and-white PNG on a decoded grid of
                at most 1024 pixels per edge. Large originals are sampled and
                decoder rounding is normalized with nearest-neighbor resampling.
                White marks premultiplied 8-bit RGB or alpha differences greater
                than your threshold; black means within that threshold. Hidden
                RGB under fully transparent pixels is ignored. Decoder,
                compression and color changes can appear; this is not a
                perceptual or forensic comparison.
              </Text>
            </>
          )}
          <Text style={styles.copy}>
            Processing reads locally with 64 MiB per stream, 256 MiB total and a
            checked 60-second deadline. Providers may delay cancellation.
            Originals are never written or uploaded. The staged PNG is removed
            on Close or backgrounding; Open transfers it into a separate local
            photo workspace, where Save keeps a copy and all originals remain
            protected.
          </Text>
          {!stack && (
            <TextInput
              accessibilityLabel={
                retarget ? 'Narrowing percent' : 'Pixel difference threshold'
              }
              value={threshold}
              editable={!busy}
              onChangeText={text => {
                if (
                  job.current ||
                  ownerRef.current !== owner ||
                  selectionRef.current !== selection ||
                  view.current !== viewToken
                )
                  return;
                dropPreview();
                setThreshold(text);
                setError('');
              }}
              keyboardType="number-pad"
              maxLength={retarget ? 2 : 3}
              style={styles.input}
            />
          )}
          {button(
            exposure
              ? 'Prepare exposure blend'
              : retarget
              ? 'Prepare narrowed photo'
              : stack
              ? focus
                ? 'Prepare focus stack'
                : 'Prepare clean plate'
              : 'Prepare difference map',
            () => {
              start('prepare');
            },
            busy,
          )}
          {busy && (
            <Text accessibilityLiveRegion="polite" style={styles.copy}>
              Working on this device… Cancel stops pending reads and processing.
              A workspace already opened cannot be recalled.
            </Text>
          )}
          {!!error && (
            <Text accessibilityRole="alert" style={styles.copy}>
              {error}
            </Text>
          )}
          {open && preview && (
            <>
              <Text accessibilityLiveRegion="polite" style={styles.copy}>
                {exposure ? (
                  `Two brackets · ${preview.width} × ${
                    preview.height
                  } output from ${preview.sourceWidth} × ${
                    preview.sourceHeight
                  } original dimensions${
                    preview.sampled ? ' · source sampled' : ''
                  }.`
                ) : retarget ? (
                  `${preview.width} × ${preview.height} output from ${
                    preview.sourceWidth
                  } × ${preview.sourceHeight} original pixels. Removed ${
                    preview.removedColumns
                  } columns from a ${
                    preview.width + (preview.removedColumns ?? 0)
                  } × ${preview.height} decoded grid${
                    preview.sampled ? ' · source sampled' : ''
                  }.`
                ) : stack ? (
                  `${preview.frames} captures · ${preview.width} × ${
                    preview.height
                  } grid from ${preview.sourceWidth} × ${
                    preview.sourceHeight
                  } source pixels. ${
                    preview.changedPixels
                  } cells differ from first capture${
                    preview.sampled ? ' · sampled' : ''
                  }.`
                ) : (
                  <>
                    {preview.width} × {preview.height} grid from{' '}
                    {preview.sourceWidth} × {preview.sourceHeight} source pixels
                    {preview.sampled ? ' · sampled' : ''}. Threshold{' '}
                    {preview.threshold}:{' '}
                    {preview.changedPixels.toLocaleString()} of{' '}
                    {(preview.width * preview.height).toLocaleString()} cells
                    marked. Raw maximum delta {preview.maximumDelta}; mean{' '}
                    {preview.meanDelta.toFixed(3)}.
                  </>
                )}
              </Text>
              <Image
                accessibilityLabel={
                  exposure
                    ? 'Exposure-bracket blend preview'
                    : retarget
                    ? 'Content-aware narrowed photo preview'
                    : stack
                    ? focus
                      ? 'Aligned focus-stack preview'
                      : 'Aligned-burst clean-plate preview'
                    : 'Black-and-white difference-mask preview'
                }
                source={{ uri: preview.previewUri }}
                resizeMode="contain"
                style={styles.preview}
                onError={() => {
                  if (prepared.current?.token !== preview.token) return;
                  dropPreview();
                  setError('Preview is unavailable. Prepare the map again.');
                }}
              />
              {button(
                exposure
                  ? 'Open exposure blend as separate photo'
                  : retarget
                  ? 'Open narrowed photo as separate photo'
                  : stack
                  ? focus
                    ? 'Open focus stack as separate photo'
                    : 'Open clean plate as separate photo'
                  : 'Open map as separate photo',
                () => {
                  start('open');
                },
                busy,
              )}
            </>
          )}
          {button(
            exposure
              ? busy
                ? 'Cancel exposure blending'
                : 'Close exposure blend'
              : retarget
              ? busy
                ? 'Cancel photo narrowing'
                : 'Close narrowed preview'
              : busy
              ? stack
                ? focus
                  ? 'Cancel focus-stack work'
                  : 'Cancel clean-plate work'
                : 'Cancel pixel-difference work'
              : stack
              ? focus
                ? 'Close focus-stack preview'
                : 'Close clean-plate preview'
              : 'Close pixel-difference map',
            closeCurrent,
          )}
        </ScrollView>
      </Modal>
    </View>
  );
}
export function PhotoExposureBlendPanel(props: Omit<Props, 'operation'>) {
  return <PhotoPixelDifferencePanel {...props} operation="exposure-blend" />;
}
export function PhotoRetargetPanel(props: Omit<Props, 'operation'>) {
  return <PhotoPixelDifferencePanel {...props} operation="retarget" />;
}
export function PhotoFocusStackPanel(props: Omit<Props, 'operation'>) {
  return <PhotoPixelDifferencePanel {...props} operation="focus-stack" />;
}
export function PhotoCleanPlatePanel(props: Omit<Props, 'operation'>) {
  return <PhotoPixelDifferencePanel {...props} operation="clean-plate" />;
}
const styles = StyleSheet.create({
  sheet: {
    backgroundColor: '#fff',
    flexGrow: 1,
    paddingHorizontal: 20,
    paddingTop: 32,
    paddingBottom: 48,
  },
  title: { color: '#000', fontSize: 24, fontWeight: '700', marginBottom: 10 },
  copy: { color: '#222', fontSize: 14, lineHeight: 21, marginVertical: 8 },
  input: {
    color: '#000',
    minHeight: 44,
    borderWidth: 1,
    borderColor: '#777',
    borderRadius: 8,
    padding: 10,
    marginVertical: 8,
  },
  button: {
    borderWidth: 1,
    borderColor: '#000',
    borderRadius: 12,
    minHeight: 44,
    justifyContent: 'center',
    padding: 12,
    marginVertical: 5,
  },
  label: { color: '#000', fontSize: 14, fontWeight: '600' },
  disabled: { opacity: 0.45 },
  preview: {
    width: '100%',
    height: 240,
    backgroundColor: '#eee',
    marginVertical: 12,
  },
});
