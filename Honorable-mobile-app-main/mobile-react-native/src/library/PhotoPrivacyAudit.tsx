import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  AppState,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useMemoryPass } from '../passes/MemoryPassContext';
import {
  honorableNative,
  type LibraryCollectionItem,
  type PhotoPrivacyField,
  type PhotoPrivacyReport,
} from '../native/HonorableNative';

export const privacyFieldLabels: Record<PhotoPrivacyField, string> = {
  GPS_LATITUDE: 'GPS latitude',
  GPS_LONGITUDE: 'GPS longitude',
  GPS_ALTITUDE: 'GPS altitude',
  GPS_DEST_LATITUDE: 'GPS destination latitude',
  GPS_DEST_LONGITUDE: 'GPS destination longitude',
  GPS_DATE: 'GPS date',
  GPS_TIME: 'GPS time',
  GPS_AREA: 'GPS area information',
  GPS_PROCESSING: 'GPS processing method',
  CAMERA_OWNER: 'Camera owner',
  BODY_SERIAL: 'Camera body serial number',
  ARTIST: 'Artist',
  COPYRIGHT: 'Copyright',
  DESCRIPTION: 'Image description',
  USER_COMMENT: 'User comment',
  DATE_TIME: 'Image date/time',
  DATE_ORIGINAL: 'Original date/time',
  DATE_DIGITIZED: 'Digitized date/time',
};
export function validPhotoPrivacyReport(
  report: PhotoPrivacyReport,
  items: LibraryCollectionItem[],
): boolean {
  return (
    !!report &&
    Array.isArray(report.entries) &&
    items.length >= 1 &&
    items.length <= 20 &&
    report.entries.length === items.length &&
    report.entries.every(
      (entry, index) =>
        !!entry &&
        entry.id === items[index].id &&
        entry.uri === items[index].uri &&
        (entry.status === 'READ' || entry.status === 'UNKNOWN') &&
        Array.isArray(entry.fields) &&
        entry.fields.length <= 18 &&
        new Set(entry.fields).size === entry.fields.length &&
        entry.fields.every(field =>
          Object.prototype.hasOwnProperty.call(privacyFieldLabels, field),
        ) &&
        (entry.status !== 'UNKNOWN' || entry.fields.length === 0),
    )
  );
}
type Props = {
  items: LibraryCollectionItem[];
  names?: Record<string, string>;
  disabled: boolean;
  canStart: () => boolean;
  selectionCurrent: () => boolean;
  onBusy: (value: boolean) => void;
  onSelectLocation: (ids: string[]) => boolean;
};
let serial = 0;
export function PhotoPrivacyAuditPanel(props: Props) {
  const auth = useMemoryPass(),
    owner = auth.signedIn ? auth.account?.accountId ?? '' : '';
  const selection = JSON.stringify(props.items);
  const latest = useRef(props),
    ownerRef = useRef(owner),
    selectionRef = useRef(selection);
  latest.current = props;
  ownerRef.current = owner;
  selectionRef.current = selection;
  const active = useRef(true),
    job = useRef<object | undefined>(undefined),
    view = useRef<object | undefined>(undefined);
  const requestId = useRef('');
  const [session, setSession] = useState<object>();
  const [visible, setVisible] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  const [result, setResult] = useState<{
    report: PhotoPrivacyReport;
    owner: string;
    selection: string;
    names: Record<string, string>;
  }>();
  const stop = useCallback(() => {
    const old = job.current;
    job.current = undefined;
    if (old) {
      try {
        honorableNative.cancelLibraryDuplicateCheck(requestId.current);
      } catch {
        /* Native host may have closed. */
      } finally {
        latest.current.onBusy(false);
      }
    }
  }, []);
  const close = useCallback(() => {
    view.current = undefined;
    stop();
    setSession(undefined);
    setVisible(false);
    setBusy(false);
    setResult(undefined);
    setError('');
  }, [stop]);
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
      view.current = undefined;
      stop();
      listener.remove();
    };
  }, [close, stop]);
  const valid =
    props.items.length >= 1 &&
    props.items.length <= 20 &&
    props.items.every(item => item.kind === 'IMAGE') &&
    new Set(props.items.map(item => item.id)).size === props.items.length &&
    new Set(props.items.map(item => item.uri)).size === props.items.length;
  const inspect = async (expectedSession?: object) => {
    const p = latest.current;
    if (expectedSession && (!visible || view.current !== expectedSession))
      return;
    if (
      !active.current ||
      job.current ||
      p.disabled ||
      !owner ||
      ownerRef.current !== owner ||
      selectionRef.current !== selection ||
      !valid ||
      !p.canStart() ||
      !p.selectionCurrent()
    )
      return;
    close();
    const request = {};
    const id = `noble-privacy-${Date.now()}-${++serial}`;
    job.current = request;
    requestId.current = id;
    view.current = request;
    setSession(request);
    p.onBusy(true);
    setVisible(true);
    setBusy(true);
    const originals = p.items.map(item => ({ ...item }));
    const names = Object.fromEntries(
      originals.map((item, index) => [
        item.id,
        p.names?.[item.id]?.replace(/\s+/g, ' ').slice(0, 120) ||
          `Photo ${index + 1}`,
      ]),
    );
    const current = () =>
      active.current &&
      job.current === request &&
      ownerRef.current === owner &&
      selectionRef.current === selection &&
      latest.current.selectionCurrent();
    try {
      const report = await honorableNative.inspectPhotoPrivacy(originals, id);
      if (!current()) return;
      if (!validPhotoPrivacyReport(report, originals))
        throw Error(
          'The metadata report did not match the selected originals. Inspect again.',
        );
      setResult({ report, owner, selection, names });
    } catch (reason) {
      if (current())
        setError(
          (reason as Error).message || 'Unable to inspect metadata. Try again.',
        );
    } finally {
      if (job.current === request) {
        job.current = undefined;
        latest.current.onBusy(false);
        if (active.current) setBusy(false);
      }
    }
  };
  const shown =
    result?.owner === owner && result.selection === selection
      ? result
      : undefined;
  const location =
    shown?.report.entries
      .filter(entry => entry.fields.some(field => field.startsWith('GPS_')))
      .map(entry => entry.id) ?? [];
  const selectLocation = () => {
    if (
      !active.current ||
      job.current ||
      !shown ||
      props.disabled ||
      !owner ||
      ownerRef.current !== owner ||
      selectionRef.current !== selection ||
      !visible ||
      !session ||
      view.current !== session ||
      !location.length ||
      !latest.current.canStart() ||
      !latest.current.selectionCurrent()
    )
      return;
    if (latest.current.onSelectLocation([...location])) close();
    else setError('The selection changed. Inspect the originals again.');
  };
  return (
    <View>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Inspect photo EXIF privacy"
        disabled={props.disabled || !owner || !valid}
        onPress={() => inspect()}
        style={styles.button}
      >
        <Text style={styles.label}>Inspect photo EXIF privacy</Text>
      </Pressable>
      <Modal visible={visible} animationType="slide" onRequestClose={close}>
        <ScrollView contentContainerStyle={styles.sheet}>
          <Text accessibilityRole="header" style={styles.title}>
            Photo EXIF privacy
          </Text>
          <Text style={styles.body}>
            Checks selected location, identifying/free-text and time fields.
            Values stay hidden. No files or metadata are changed. This does not
            check lens serial numbers, XMP, filenames or visible image content;
            no listed fields found does not prove a photo is metadata-free.
          </Text>
          {busy && (
            <Text style={styles.body}>
              Inspecting selected originals on this device…
            </Text>
          )}
          {shown?.report.entries.map(entry => (
            <View key={entry.id} style={styles.entry}>
              <Text style={styles.label}>{shown.names[entry.id]}</Text>
              <Text style={styles.body}>
                {entry.status === 'UNKNOWN'
                  ? 'Unknown — these EXIF fields could not be read.'
                  : entry.fields.length
                  ? `Fields present: ${entry.fields
                      .map(field => privacyFieldLabels[field])
                      .join(', ')}. Values not shown.`
                  : 'No fields from this list found. Other metadata may still exist.'}
              </Text>
            </View>
          ))}
          {!!error && (
            <Text accessibilityRole="alert" style={styles.body}>
              {error}
            </Text>
          )}
          {!!location.length && (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Select location-flagged photos"
              disabled={busy || props.disabled}
              onPress={selectLocation}
              style={styles.button}
            >
              <Text style={styles.label}>
                Select location-flagged photos ({location.length})
              </Text>
            </Pressable>
          )}
          {!busy && (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Inspect photo EXIF again"
              disabled={props.disabled}
              onPress={() => {
                if (session) return inspect(session);
              }}
              style={styles.button}
            >
              <Text style={styles.label}>Inspect again</Text>
            </Pressable>
          )}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={
              busy ? 'Stop EXIF inspection' : 'Close EXIF privacy report'
            }
            onPress={close}
            style={styles.button}
          >
            <Text style={styles.label}>{busy ? 'Stop' : 'Close'}</Text>
          </Pressable>
        </ScrollView>
      </Modal>
    </View>
  );
}
const styles = StyleSheet.create({
  button: {
    minHeight: 44,
    borderWidth: 1,
    borderColor: '#000',
    padding: 12,
    marginVertical: 5,
    justifyContent: 'center',
  },
  label: { color: '#000', fontSize: 14, fontWeight: '600' },
  sheet: {
    padding: 22,
    paddingTop: 48,
    paddingBottom: 40,
    backgroundColor: '#fff',
    flexGrow: 1,
  },
  title: { color: '#000', fontSize: 24, fontWeight: '700', marginBottom: 12 },
  body: { color: '#333', fontSize: 14, lineHeight: 21, marginVertical: 8 },
  entry: { borderBottomWidth: 1, borderColor: '#ddd', paddingVertical: 8 },
});
