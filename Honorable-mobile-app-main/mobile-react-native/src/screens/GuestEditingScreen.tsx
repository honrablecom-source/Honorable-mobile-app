import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  BackHandler,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { honorableNative } from '../native/HonorableNative';
import { colors } from '../design-system/tokens';
import { useOpenWorkspace } from '../native/openWorkspace';
import { StudioStartGuide } from '../components/StudioStartGuide';
import { CanvasPreview } from '../components/CanvasPreview';
import {
  Image as Photo,
  Film,
  PenLine,
  Plus,
  ArrowUpRight,
} from 'lucide-react-native';

type CanvasSetup = { name: string; width: number; height: number; background: 'white' | 'black' | 'transparent' };
type SetupReview = { action: 'save' | 'delete'; saved: CanvasSetup; replacement?: CanvasSetup };

const canvasFormats = [
  ['square', 'Square', '1080 × 1080'],
  ['portrait', 'Portrait', '1080 × 1920'],
  ['landscape', 'Landscape', '1920 × 1080'],
  ['feed_portrait', 'Feed portrait', '1080 × 1350'],
  ['story', 'Story', '1080 × 1920'],
  ['reels', 'Reels', '1080 × 1920'],
  ['ultrawide', 'Ultrawide', '2560 × 1080'],
  ['postcard', 'Postcard', '1200 × 1500'],
  ['banner', 'Banner', '1500 × 500'],
  ['poster', 'Poster', '1080 × 1620'],
  ['cover', 'Cover', '1600 × 900'],
  ['wallpaper', 'Wallpaper', '1920 × 1200'],
] as const;

/** Guest editing never mounts the account, search or paid-feature navigation. */
type CreationRoute = {onSignIn: () => void; onCanvasClose?: never} | {onCanvasClose: () => void; onSignIn?: never};
export function GuestEditingScreen({ onSignIn, onCanvasClose }: CreationRoute) {
  const [setups, setSetups] = useState<CanvasSetup[]>([]);
  const [setupName, setSetupName] = useState('');
  const [setupError, setSetupError] = useState('');
  const [setupBusy, setSetupBusy] = useState(false);
  const [setupReview, setSetupReview] = useState<SetupReview | null>(null);
  const setupReviewRef = useRef<SetupReview | null>(null);
  const setupPending = useRef(false);
  const setupActive = useRef(false);
  useEffect(() => {
    setupActive.current = true;
    return () => {
      setupActive.current = false;
      setupReviewRef.current = null;
    };
  }, []);
  const [choosingCanvas, setChoosingCanvas] = useState(!!onCanvasClose);
  const initialCanvasRead = useRef(!!onCanvasClose);
  const [canvasMode, setCanvasMode] = useState<'formats' | 'custom'>('formats');
  const [canvasQuery, setCanvasQuery] = useState('');
  const [background, setBackground] = useState<'white' | 'black' | 'transparent'>('white');
  const [canvasWidth, setCanvasWidth] = useState('1080');
  const [canvasHeight, setCanvasHeight] = useState('1080');
  const dimensionsValid = /^\d+$/.test(canvasWidth) && /^\d+$/.test(canvasHeight) &&
    Number(canvasWidth) >= 64 && Number(canvasWidth) <= 4096 &&
    Number(canvasHeight) >= 64 && Number(canvasHeight) <= 4096;
  const canvasPixels = Number(canvasWidth) * Number(canvasHeight);
  const customValid = dimensionsValid && canvasPixels <= 4194304;
  const { busy, open, create, createCustom, draw, projects, photoVideo, videoProjects } = useOpenWorkspace('guest');
  useEffect(() => {
    setupReviewRef.current = null;
    setSetupReview(null);
  }, [canvasWidth, canvasHeight, background, setupName, choosingCanvas, setups]);
  useEffect(() => {
    if (!choosingCanvas || Platform.OS !== 'android') return;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (!busy) { if (onCanvasClose) onCanvasClose(); else setChoosingCanvas(false); }
      return true;
    });
    return () => subscription.remove();
  }, [choosingCanvas, busy, onCanvasClose]);
  const fitCanvas = () => {
    if (busy || !dimensionsValid || customValid) return;
    const scale = Math.sqrt(4194304 / canvasPixels);
    setCanvasWidth(String(Math.floor(Number(canvasWidth) * scale)));
    setCanvasHeight(String(Math.floor(Number(canvasHeight) * scale)));
  };
  const updateSetups = useCallback(async (action: 'list' | 'save' | 'delete', name = '', replacement?: CanvasSetup) => {
    if (!setupActive.current || setupPending.current || busy) return;
    if (action === 'list') { setupReviewRef.current = null; setSetupReview(null); }
    setupPending.current = true;
    setSetupBusy(true);
    setSetupError('');
    try {
      const raw = await honorableNative.canvasSetups(action, name, replacement?.width ?? Number(canvasWidth), replacement?.height ?? Number(canvasHeight), replacement?.background ?? background);
      if (!setupActive.current) return;
      if (raw.length > 16384) throw new Error('Saved setups exceed the supported size.');
      const saved: unknown = JSON.parse(raw);
      if (!Array.isArray(saved) || saved.length > 20 || !saved.every(item =>
        item && typeof item.name === 'string' && item.name.length <= 40 &&
        Number.isInteger(item.width) && item.width >= 64 && item.width <= 4096 &&
        Number.isInteger(item.height) && item.height >= 64 && item.height <= 4096 &&
        item.width * item.height <= 4194304 && ['white', 'black', 'transparent'].includes(item.background),
      )) throw new Error('Saved setups could not be read.');
      setSetups(saved);
    } catch (error) {
      if (setupActive.current) setSetupError(error instanceof Error ? error.message : 'Canvas setups are unavailable.');
    } finally {
      setupPending.current = false;
      if (setupActive.current) setSetupBusy(false);
    }
  }, [busy, canvasWidth, canvasHeight, background]);
  useEffect(() => {
    if (!initialCanvasRead.current) return;
    initialCanvasRead.current = false;
    updateSetups('list');
  }, [updateSetups]);
  const leaveCanvas = () => {
    if (busy) return;
    if (onCanvasClose) onCanvasClose(); else setChoosingCanvas(false);
  };
  const reviewSetup = (action: 'save' | 'delete', saved: CanvasSetup, replacement?: CanvasSetup) => {
    if (!setupActive.current || busy || setupPending.current) return;
    const review = { action, saved, replacement };
    setupReviewRef.current = review;
    setSetupReview(review);
  };
  const saveSetup = () => {
    if (!setupActive.current || busy || setupPending.current || !customValid || !setupName.trim()) return;
    const replacement: CanvasSetup = { name: setupName.trim(), width: Number(canvasWidth), height: Number(canvasHeight), background };
    const saved = setups.find(item => item.name === replacement.name);
    if (saved && (saved.width !== replacement.width || saved.height !== replacement.height || saved.background !== replacement.background)) {
      reviewSetup('save', saved, replacement);
    } else void updateSetups('save', replacement.name);
  };
  const cancelSetupReview = () => { setupReviewRef.current = null; if (setupActive.current) setSetupReview(null); };
  const confirmSetupReview = (review: SetupReview) => {
    if (!setupActive.current || busy || setupPending.current || setupReviewRef.current !== review) return;
    const saved = setups.find(item => item.name === review.saved.name);
    if (!saved || saved.width !== review.saved.width || saved.height !== review.saved.height || saved.background !== review.saved.background) {
      cancelSetupReview();
      setSetupError('Saved setup changed. Review the current version again.');
      return;
    }
    cancelSetupReview();
    void updateSetups(review.action, review.saved.name, review.replacement);
  };
  const chooseCanvas = () => {
    if (!setupActive.current || busy) return;
    setCanvasQuery('');
    setCanvasMode('formats');
    setChoosingCanvas(true);
    void updateSetups('list');
  };
  return (
    <SafeAreaView style={s.safe}>
      <ScrollView contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
        <Text style={s.wordmark}>Noble</Text>
        {!choosingCanvas && <><Text accessibilityRole="header" style={s.title}>
          {'Your ideas.\nYour studio.'}
        </Text>
        <Text style={s.copy}>
          Make something from a moment you already have.
        </Text>
        </>}
        {!choosingCanvas && <StudioStartGuide busy={busy} onPhoto={() => open('IMAGE')} onVideo={() => open('VIDEO')} onStory={photoVideo} onDrawing={draw} onCanvas={chooseCanvas} />}
        {!choosingCanvas && <View style={s.actions}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Draw an image"
            accessibilityHint="Resume your local drawing draft. Use New drawing inside the workspace to start fresh."
            accessibilityState={{ disabled: busy }}
            disabled={busy}
            onPress={draw}
            style={[s.button, busy && s.disabled]}
          >
            <View style={s.cardTop}>
              <PenLine size={25} color={colors.textPrimary} strokeWidth={1.5} />
              <ArrowUpRight size={16} color={colors.textSecondary} />
            </View>
            <Text style={s.buttonText}>Draw an image</Text>
            <Text style={s.cardDetail}>Resume your drawing or start fresh</Text>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Create an image"
            accessibilityState={{ disabled: busy }}
            disabled={busy}
            style={[s.button, busy && s.disabled]}
            onPress={chooseCanvas}
          >
            <View style={s.cardTop}>
              <Plus size={25} color={colors.textPrimary} strokeWidth={1.5} />
              <ArrowUpRight size={16} color={colors.textSecondary} />
            </View>
            <Text style={s.buttonText}>Create an image</Text>
            <Text style={s.cardDetail}>Choose a canvas. Make it yours.</Text>
          </Pressable>
          {(['IMAGE', 'VIDEO'] as const).map(kind => {
            const label = kind === 'IMAGE' ? 'Edit a photo' : 'Edit a video';
            return (
              <Pressable
                key={kind}
                accessibilityRole="button"
                accessibilityLabel={label}
                accessibilityState={{ disabled: busy }}
                disabled={busy}
                onPress={() => open(kind)}
                style={[s.button, busy && s.disabled]}
              >
                <View style={s.cardTop}>
                  {kind === 'IMAGE' ? (
                    <Photo
                      size={25}
                      color={colors.textPrimary}
                      strokeWidth={1.5}
                    />
                  ) : (
                    <Film
                      size={25}
                      color={colors.textPrimary}
                      strokeWidth={1.5}
                    />
                  )}
                  <ArrowUpRight size={16} color={colors.textSecondary} />
                </View>
                <Text style={s.buttonText}>{label}</Text>
                <Text style={s.cardDetail}>
                  {kind === 'IMAGE'
                    ? 'Light, color & a fresh look'
                    : 'Clips, captions & sound'}
                </Text>
              </Pressable>
            );
          })}
        </View>}
        {!choosingCanvas && <Pressable accessibilityRole="button" accessibilityLabel="Create a video" accessibilityState={{ disabled: busy }} disabled={busy} style={[s.canvasChoice, busy && s.disabled]} onPress={photoVideo}><Text style={s.buttonText}>Create a video</Text><Text style={s.cardDetail}>Photos into an editable local timeline</Text></Pressable>}
        {!choosingCanvas && <Pressable accessibilityRole="button" accessibilityLabel="Saved photo videos" accessibilityState={{ disabled: busy }} disabled={busy} style={[s.canvasChoice, busy && s.disabled]} onPress={videoProjects}><Text style={s.buttonText}>Saved photo videos</Text><Text style={s.cardDetail}>Reopen prepared clips and your saved timeline</Text></Pressable>}
        {!choosingCanvas && <Pressable accessibilityRole="button" accessibilityLabel="Saved image projects" accessibilityState={{ disabled: busy }} disabled={busy} style={[s.canvasChoice, busy && s.disabled]} onPress={projects}><Text style={s.buttonText}>Saved image projects</Text><Text style={s.cardDetail}>Reopen editable canvases kept on this device</Text></Pressable>}
        {choosingCanvas && (
          <View style={s.canvasChoices}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={onCanvasClose ? "Back to Studio" : "Close canvas choices"}
              accessibilityState={{ disabled: busy }}
              disabled={busy}
              style={s.canvasChoice}
              onPress={leaveCanvas}
            >
              <Text style={s.returnText}>{onCanvasClose ? "Back to Studio" : "Close canvas choices"}</Text>
            </Pressable>
            <Text accessibilityRole="header" style={s.buttonText}>Choose a canvas</Text>
            <View style={s.canvasTabs}>
              {(['formats', 'custom'] as const).map(mode => <Pressable key={mode} accessibilityRole="tab" accessibilityLabel={mode === 'formats' ? 'Canvas formats' : 'Custom canvas and setups'} accessibilityState={{ selected: canvasMode === mode, disabled: busy }} disabled={busy} style={[s.canvasTab, canvasMode === mode && s.canvasTabSelected]} onPress={() => setCanvasMode(mode)}><Text style={s.buttonText}>{mode === 'formats' ? 'Formats' : 'Custom & saved'}</Text></Pressable>)}
            </View>
            {canvasMode === 'custom' && <>
            <Text style={s.copy}>Start with a custom canvas. Export a new copy when finished.</Text>
            <Text style={s.copy}>Custom size: 64–4096 pixels per side, up to 4,194,304 pixels total.</Text>
            <Text style={s.buttonText}>Width (pixels)</Text>
            <TextInput accessibilityLabel="Canvas width in pixels" keyboardType="number-pad" maxLength={4} value={canvasWidth} onChangeText={setCanvasWidth} editable={!busy} style={[s.canvasChoice, s.buttonText]} />
            <Text style={s.buttonText}>Height (pixels)</Text>
            <TextInput accessibilityLabel="Canvas height in pixels" keyboardType="number-pad" maxLength={4} value={canvasHeight} onChangeText={setCanvasHeight} editable={!busy} style={[s.canvasChoice, s.buttonText]} />
            {dimensionsValid && <CanvasPreview width={Number(canvasWidth)} height={Number(canvasHeight)} background={background} />}
            <Pressable accessibilityRole="button" accessibilityLabel="Swap width and height"
              accessibilityState={{ disabled: busy }} disabled={busy} style={[s.canvasChoice, busy && s.disabled]}
              onPress={() => { if (!busy) { setCanvasWidth(canvasHeight); setCanvasHeight(canvasWidth); } }}>
              <Text style={s.buttonText}>Swap width and height</Text>
            </Pressable>
            {dimensionsValid && <Text accessibilityLiveRegion="polite" style={s.copy}>
              {`${canvasPixels.toLocaleString('en-US')} of 4,194,304 pixels used`}
            </Text>}
            {!customValid && <Text accessibilityRole="alert" style={s.copy}>{dimensionsValid
              ? 'This canvas is too large. Reduce the width or height to use at most 4,194,304 pixels.'
              : 'Enter a whole number from 64 to 4096 for both width and height.'}</Text>}
            {dimensionsValid && !customValid && <>
              <Pressable accessibilityRole="button" accessibilityLabel="Fit within pixel limit"
                accessibilityHint="Reduces both dimensions proportionally without creating an image."
                accessibilityState={{ disabled: busy }} disabled={busy} style={[s.canvasChoice, busy && s.disabled]} onPress={fitCanvas}>
                <Text style={s.buttonText}>Fit within pixel limit</Text>
              </Pressable>
              <Text style={s.copy}>Reduce both dimensions to fit. Sizes round down to whole pixels.</Text>
            </>}
            <TextInput accessibilityLabel="Canvas setup name" placeholder="Name this setup" maxLength={40} value={setupName} onChangeText={setSetupName} editable={!busy && !setupBusy} style={[s.canvasChoice, s.buttonText]} />
            <Pressable accessibilityRole="button" accessibilityLabel="Save canvas setup" disabled={busy || setupBusy || !customValid || !setupName.trim()} style={s.canvasChoice} onPress={saveSetup}>
              <Text style={s.buttonText}>Save setup on this device</Text>
            </Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel="Refresh saved setups"
              accessibilityState={{ disabled: busy || setupBusy, busy: setupBusy }} aria-disabled={busy || setupBusy} aria-busy={setupBusy}
              disabled={busy || setupBusy} style={[s.canvasChoice, (busy || setupBusy) && s.disabled]}
              onPress={() => { if (!busy) return updateSetups('list'); }}>
              <Text style={s.buttonText}>Refresh saved setups</Text>
            </Pressable>
            {setupReview && <View style={s.setupReview}>
              <Text accessibilityRole="header" style={s.buttonText}>{setupReview.action === 'save' ? 'Replace saved setup?' : 'Delete saved setup?'}</Text>
              <Text accessibilityLiveRegion="polite" style={s.copy}>{`${setupReview.saved.name}: ${setupReview.saved.width} × ${setupReview.saved.height} · ${setupReview.saved.background}`}</Text>
              {setupReview.replacement && <Text style={s.copy}>{`Replace with ${setupReview.replacement.width} × ${setupReview.replacement.height} · ${setupReview.replacement.background}. Use another name to keep both.`}</Text>}
              <Text style={s.copy}>{setupReview.action === 'delete' ? 'Only the saved size and background are removed. Your images and current canvas settings stay.' : 'Your images stay unchanged. Changing the settings or refreshing saved setups cancels this review.'}</Text>
              <Pressable accessibilityRole="button" accessibilityLabel={setupReview.action === 'save' ? 'Replace saved canvas setup' : 'Confirm delete saved canvas setup'} disabled={busy || setupBusy} accessibilityState={{ disabled: busy || setupBusy }} style={s.canvasChoice} onPress={() => confirmSetupReview(setupReview)}><Text style={s.buttonText}>{setupReview.action === 'save' ? 'Replace saved setup' : 'Delete saved setup'}</Text></Pressable>
              <Pressable accessibilityRole="button" accessibilityLabel="Keep saved canvas setup" disabled={busy || setupBusy} accessibilityState={{ disabled: busy || setupBusy }} style={s.returnButton} onPress={cancelSetupReview}><Text style={s.returnText}>Keep saved setup</Text></Pressable>
            </View>}
            {setupBusy && <Text accessibilityLiveRegion="polite" aria-live="polite" style={s.copy}>Updating saved setups…</Text>}
            {!!setupError && <Text accessibilityRole="alert" style={s.copy}>{setupError}</Text>}
            {!setupBusy && !setupError && setups.length === 0 && <Text style={s.copy}>No saved setups yet. Name your custom canvas and save it on this device.</Text>}
            {setups.map(setup => <View key={setup.name}>
              <Pressable accessibilityRole="button" accessibilityLabel={`Load canvas setup ${setup.name}`} disabled={busy || setupBusy} style={s.canvasChoice} onPress={() => { setCanvasWidth(String(setup.width)); setCanvasHeight(String(setup.height)); setBackground(setup.background); setSetupName(setup.name); }}>
                <Text style={s.buttonText}>{setup.name}</Text><Text style={s.cardDetail}>{setup.width} × {setup.height} · {setup.background}</Text>
              </Pressable>
              <Pressable accessibilityRole="button" accessibilityLabel={`Delete canvas setup ${setup.name}`} disabled={busy || setupBusy} style={s.returnButton} onPress={() => reviewSetup('delete', setup)}><Text style={s.returnText}>Delete {setup.name}</Text></Pressable>
            </View>)}
            <Text style={s.copy}>Custom canvas background. Choose PNG when exporting to retain transparency.</Text>
            {(['white', 'black', 'transparent'] as const).map(value => (
              <Pressable key={value} accessibilityRole="radio" accessibilityLabel={`${value} canvas background`} accessibilityState={{ checked: background === value, disabled: busy }} disabled={busy} style={s.canvasChoice} onPress={() => setBackground(value)}>
                <Text style={s.buttonText}>{background === value ? '● ' : '○ '}{value}</Text>
              </Pressable>
            ))}
            <Pressable accessibilityRole="button" accessibilityLabel="Create custom canvas" disabled={busy || !customValid} accessibilityState={{ disabled: busy || !customValid }} style={[s.canvasChoice, (busy || !customValid) && s.disabled]} onPress={() => createCustom(Number(canvasWidth), Number(canvasHeight), background)}>
              <Text style={s.buttonText}>Create custom canvas</Text>
            </Pressable>
            </>}
            {canvasMode === 'formats' && <>
            <Text style={s.copy}>Choose a ready-made white canvas or search by format and dimensions.</Text>
            <TextInput
              accessibilityLabel="Search canvas formats"
              placeholder="Search formats or dimensions"
              value={canvasQuery}
              onChangeText={setCanvasQuery}
              editable={!busy}
              style={[s.canvasChoice, s.buttonText]}
            />
            {canvasFormats.filter(([, label, dimensions]) =>
              `${label} ${dimensions}`.toLowerCase().includes(canvasQuery.trim().toLowerCase()),
            ).map(([format, label, dimensions]) => (
              <Pressable
                key={format}
                accessibilityRole="button"
                accessibilityLabel={`Create ${label} canvas`}
                accessibilityState={{ disabled: busy }}
                disabled={busy}
                style={[s.canvasChoice, busy && s.disabled]}
                onPress={async () => {
                  await create(format);
                }}
              >
                <Text style={s.buttonText}>{label}</Text>
                <Text style={s.cardDetail}>{dimensions}</Text>
              </Pressable>
            ))}
            {!canvasFormats.some(([, label, dimensions]) =>
              `${label} ${dimensions}`.toLowerCase().includes(canvasQuery.trim().toLowerCase()),
            ) && <Text style={s.copy}>No canvas formats match. Try another name or dimension.</Text>}
            </>}

          </View>
        )}
        <Text style={s.note}>
          {onCanvasClose ? 'Your files stay here. Exports create a new copy.' : 'Your files stay here. Exports create a new copy. Free on-device AI is included; search and account features require sign-in.'}
        </Text>
        {onSignIn && <Pressable
          accessibilityRole="button"
          accessibilityLabel="Back to sign in"
          disabled={busy}
          accessibilityState={{ disabled: busy }}
          onPress={onSignIn}
          style={[s.returnButton, busy && s.disabled]}
        >
          <Text style={s.returnText}>Back to sign in</Text>
        </Pressable>}
      </ScrollView>
    </SafeAreaView>
  );
}

/** Reuse the full existing canvas workflow without guest/account navigation chrome. */
export function CanvasCreationScreen({onClose}: {onClose: () => void}) {
  return <GuestEditingScreen onCanvasClose={onClose} />;
}
const s = StyleSheet.create({
  setupReview: { padding: 12, gap: 8, borderWidth: 1, borderColor: colors.textPrimary, borderRadius: 12 },
  canvasTabs: { flexDirection: 'row', gap: 8 },
  canvasTab: { flex: 1, minHeight: 48, borderWidth: 1, borderColor: colors.border, borderRadius: 12, padding: 10, justifyContent: 'center' },
  canvasTabSelected: { borderColor: colors.textPrimary, borderWidth: 2 },
  canvasChoices: { gap: 8, marginBottom: 24 },
  canvasChoice: { minHeight: 56, padding: 12, borderWidth: 1, borderColor: colors.border, borderRadius: 12 },
  safe: { flex: 1, backgroundColor: colors.canvas },
  content: { flexGrow: 1, padding: 24, paddingBottom: 40 },
  wordmark: {
    color: colors.textPrimary,
    fontSize: 23,
    fontWeight: '700',
    letterSpacing: -0.6,
  },
  title: {
    color: colors.textPrimary,
    fontSize: 36,
    lineHeight: 42,
    fontWeight: '500',
    letterSpacing: -1.2,
    marginTop: 28,
  },
  copy: {
    color: colors.textSecondary,
    fontSize: 14,
    lineHeight: 21,
    marginTop: 12,
  },
  actions: {
    gap: 12,
    marginVertical: 26,
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  button: {
    minHeight: 148,
    width: '47%',
    flexGrow: 1,
    backgroundColor: colors.canvas,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 15,
    alignItems: 'stretch',
    justifyContent: 'center',
  },
  cardTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 24,
  },
  cardDetail: {
    fontSize: 11,
    lineHeight: 16,
    color: colors.textSecondary,
    marginTop: 5,
  },
  buttonText: { fontSize: 15, fontWeight: '600', color: colors.textPrimary },
  note: { color: colors.textTertiary, fontSize: 13, lineHeight: 20 },
  disabled: { opacity: 0.45 },
  returnButton: {
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 24,
  },
  returnText: { fontSize: 15, fontWeight: '600', color: colors.textPrimary },
});
