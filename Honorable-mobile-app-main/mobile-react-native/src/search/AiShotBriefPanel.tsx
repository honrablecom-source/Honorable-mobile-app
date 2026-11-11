import React, { useEffect, useRef, useState } from 'react';
import {
  AppState,
  Image,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {
  honorableNative,
  type SearchResponse,
  type SearchResult,
} from '../native/HonorableNative';
import { modelCost } from '../passes/catalog';
import { colors } from '../design-system/tokens';
import {
  prepareAiBrief,
  attachAiCandidates,
  chooseAiCandidate,
  aiBriefCoverage,
  aiBriefCollectionItems,
  type AiBriefShot,
} from './AiShotBrief';
import type { AiSearchPhase, AvailableAiModel } from './OwnedAiSearch';
type Props = {
  owner: string;
  connected: boolean;
  model: AvailableAiModel;
  busy: boolean;
  phase: AiSearchPhase;
  onSearch: (query: string) => Promise<SearchResponse | undefined>;
  onCancel: () => boolean;
  onPreview: (result: SearchResult) => void;
};
export function AiShotBriefPanel(props: Props) {
  const [open, setOpen] = useState(false),
    [draft, setDraft] = useState(''),
    [shots, setShots] = useState<AiBriefShot[]>([]),
    [title, setTitle] = useState('AI shot picks'),
    [expanded, setExpanded] = useState(''),
    [searching, setSearching] = useState(''),
    [saving, setSaving] = useState(false),
    [notice, setNotice] = useState('');
  const latest = useRef(props);
  latest.current = props;
  const rows = useRef(shots);
  rows.current = shots;
  const lifecycle = useRef({
      active: true,
      foreground:
        AppState.currentState !== 'background' &&
        AppState.currentState !== 'inactive',
      revision: 0,
      owner: props.owner,
    }),
    pending = useRef<object | null>(null),
    savePending = useRef(false);
  useEffect(() => {
    const session = lifecycle.current;
    if (session.owner !== props.owner) {
      session.owner = props.owner;
      session.revision++;
      latest.current.onCancel();
      setOpen(false);
      setDraft('');
      setShots([]);
      setExpanded('');
      setTitle('AI shot picks');
      setNotice('');
    }
  }, [props.owner]);
  useEffect(() => {
    const session = lifecycle.current;
    session.active = true;
    const listener = AppState.addEventListener('change', state => {
      session.foreground = state === 'active';
      if (!session.foreground) {
        session.revision++;
        latest.current.onCancel();
        setOpen(false);
      }
    });
    return () => {
      session.active = false;
      session.revision++;
      latest.current.onCancel();
      listener.remove();
    };
  }, []);
  const available = !!props.owner && props.connected,
    locked = props.busy || saving || !!searching,
    cost = modelCost(props.model),
    coverage = aiBriefCoverage(shots);
  const prepare = () => {
    if (locked || !available) return;
    try {
      const next = prepareAiBrief(draft);
      lifecycle.current.revision++;
      setShots(next);
      setExpanded('');
      setNotice(
        'Brief ready. Find candidates for each shot, then choose what actually fits.',
      );
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Brief unavailable.');
    }
  };
  const find = async (shot: AiBriefShot) => {
    if (
      locked ||
      pending.current ||
      !rows.current.includes(shot) ||
      latest.current.owner !== props.owner ||
      !latest.current.connected ||
      !available ||
      !lifecycle.current.foreground ||
      !cost?.available
    )
      return;
    const job = {},
      session = lifecycle.current,
      revision = session.revision,
      owner = props.owner;
    pending.current = job;
    setSearching(shot.id);
    setExpanded(shot.id);
    setNotice('');
    const current = () =>
      session.active &&
      session.foreground &&
      session.revision === revision &&
      latest.current.owner === owner;
    try {
      const result = await latest.current.onSearch(shot.query);
      if (!current()) return;
      if (!result) {
        setNotice(
          'Search did not return a current result. Check the search status before retrying.',
        );
        return;
      }
      setShots(previous => attachAiCandidates(previous, shot.id, result));
      setNotice(
        result.results.length
          ? 'AI suggestions are ready. Review the original before choosing.'
          : 'No confident candidates for this shot. Refine the description or try VIDEO for video moments.',
      );
    } catch (error) {
      if (current())
        setNotice(
          error instanceof Error ? error.message : 'AI search unavailable.',
        );
    } finally {
      if (pending.current === job) {
        pending.current = null;
        if (session.active) setSearching('');
      }
    }
  };
  const stop = () => {
    if (latest.current.onCancel()) {
      lifecycle.current.revision++;
      setNotice(
        'Stopping this search. Wait for the native engine to finish before starting another.',
      );
    } else
      setNotice('The search has completed; account confirmation is finishing.');
  };
  const save = async () => {
    if (
      locked ||
      savePending.current ||
      !available ||
      !lifecycle.current.foreground
    )
      return;
    const session = lifecycle.current,
      revision = session.revision,
      owner = props.owner;
    const current = () => {
      if (
        !session.active ||
        !session.foreground ||
        session.revision !== revision ||
        latest.current.owner !== owner
      )
        throw Error(
          'Account or brief changed. Check your collections before retrying.',
        );
    };
    try {
      const name = title.trim();
      const points = Array.from(name);
      if (
        !name ||
        points.length > 40 ||
        points.some(character => {
          const code = character.codePointAt(0)!;
          return (
            code < 32 ||
            (code >= 127 && code <= 159) ||
            (code >= 0xd800 && code <= 0xdfff)
          );
        })
      )
        throw Error(
          'Use a collection name from 1 to 40 characters without control characters.',
        );
      const items = aiBriefCollectionItems(rows.current);
      savePending.current = true;
      setSaving(true);
      current();
      const shelf = await honorableNative.libraryCollections('list');
      current();
      if (!shelf.owner)
        throw Error('The private collection session is unavailable.');
      const saved = await honorableNative.libraryCollections(
        'create',
        shelf.owner,
        '',
        name,
        items,
      );
      current();
      if (saved.owner !== shelf.owner)
        throw Error('The collection account changed.');
      setNotice(
        'Saved chosen originals to your private collections. Open Library → Collections to use the set. Video moment suggestions stay in this brief; the collection stores original files.',
      );
    } catch (error) {
      if (session.active && latest.current.owner === owner)
        setNotice(
          error instanceof Error
            ? error.message
            : 'Collection could not be saved.',
        );
    } finally {
      savePending.current = false;
      if (session.active) setSaving(false);
    }
  };
  const renderRevision = lifecycle.current.revision;
  const action = (label: string, onPress: () => void, disabled = false) => (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={() => {
        if (
          disabled ||
          !lifecycle.current.active ||
          !lifecycle.current.foreground ||
          latest.current.owner !== props.owner ||
          (onPress !== stop &&
            (latest.current.busy ||
              pending.current !== null ||
              savePending.current)) ||
          lifecycle.current.revision !== renderRevision
        )
          return;
        onPress();
      }}
      style={[styles.button, disabled && styles.disabled]}
    >
      <Text style={styles.buttonText}>{label}</Text>
    </Pressable>
  );
  return (
    <View style={styles.card}>
      {action(
        open ? 'Hide AI shot brief' : 'AI shot brief: find shots for an idea',
        () => {
          if (locked) return;
          lifecycle.current.revision++;
          setOpen(!open);
        },
        locked || !available,
      )}
      {open && (
        <>
          <Text accessibilityRole="header" style={styles.heading}>
            Find the shots for your idea
          </Text>
          <Text style={styles.copy}>
            Describe 2–6 desired shots, one per line. Existing on-device SERAN
            AI finds candidates for each; you decide which ones fit. This is a
            reviewed collection builder, not an automatically generated video.
          </Text>
          <Text style={styles.copy}>
            {cost?.name} · {cost?.credits}{' '}
            {cost?.credits === 1 ? 'credit' : 'credits'} per successful search.
            Each Find button runs one search. Model V3 remains unavailable.
            Account credits are verified online; prompts and media use the
            existing on-device search path.
          </Text>
          <TextInput
            accessibilityLabel="AI shot brief"
            multiline
            maxLength={1450}
            value={draft}
            editable={!locked}
            onChangeText={setDraft}
            placeholder={
              'Birthday candles\nFriends laughing\nA wide group photo'
            }
            style={[styles.input, styles.multiline]}
          />
          {action(
            shots.length
              ? 'Replace brief and clear picks'
              : 'Prepare AI shot brief',
            prepare,
            locked,
          )}
          <Text style={styles.copy}>
            Reviewed picks: {coverage.reviewed}/{coverage.total} ·{' '}
            {coverage.uniqueMedia} unique originals. Coverage reflects your
            choices, not an AI guarantee.
          </Text>
          {shots.map((shot, index) => (
            <View key={shot.id} style={styles.shot}>
              <Text style={styles.label}>
                {index + 1}. {shot.query}
              </Text>
              {shot.selected && (
                <Text style={styles.copy}>
                  Chosen: {shot.selected.displayName || 'Original'}
                  {shot.selected.mediaType === 'VIDEO' &&
                  shot.selected.bestTimestampMs !== undefined
                    ? ` · suggested ${(
                        shot.selected.bestTimestampMs / 1000
                      ).toFixed(1)}s`
                    : ''}
                </Text>
              )}
              {action(
                `Find AI candidates for shot ${index + 1}`,
                () => {
                  void find(shot);
                },
                locked,
              )}
              {shot.searched &&
                action(
                  `${
                    expanded === shot.id ? 'Hide' : 'Review'
                  } candidates for shot ${index + 1}`,
                  () => setExpanded(expanded === shot.id ? '' : shot.id),
                  locked,
                )}
              {shot.selected &&
                action(
                  `Clear choice for shot ${index + 1}`,
                  () =>
                    setShots(previous => chooseAiCandidate(previous, shot.id)),
                  locked,
                )}
              {expanded === shot.id &&
                shot.candidates.map((candidate, position) => (
                  <View
                    key={`${candidate.mediaId}-${position}`}
                    style={styles.candidate}
                  >
                    {candidate.mediaType === 'IMAGE' &&
                      /^(content|file|ph):\/\//.test(candidate.mediaUri) && (
                        <Image
                          source={{ uri: candidate.mediaUri }}
                          style={styles.thumbnail}
                          accessibilityLabel={`AI candidate ${
                            position + 1
                          } for shot ${index + 1}`}
                        />
                      )}
                    <Text style={styles.label}>
                      {candidate.displayName ||
                        `${
                          candidate.mediaType === 'VIDEO' ? 'Video' : 'Photo'
                        } candidate ${position + 1}`}
                    </Text>
                    <Text style={styles.copy}>
                      Native match: {candidate.confidence.toLowerCase()}
                      {candidate.mediaType === 'VIDEO' &&
                      candidate.bestTimestampMs !== undefined
                        ? ` · suggested ${(
                            candidate.bestTimestampMs / 1000
                          ).toFixed(1)}s`
                        : ''}
                      . Review the original; match scores are not proof.
                    </Text>
                    {action(
                      `Preview candidate ${position + 1} for shot ${index + 1}`,
                      () => latest.current.onPreview(candidate),
                      locked,
                    )}
                    {action(
                      `${
                        shot.selected?.mediaId === candidate.mediaId
                          ? 'Chosen'
                          : 'Choose'
                      } candidate ${position + 1} for shot ${index + 1}`,
                      () =>
                        setShots(previous =>
                          chooseAiCandidate(
                            previous,
                            shot.id,
                            candidate.mediaId,
                          ),
                        ),
                      locked,
                    )}
                  </View>
                ))}
            </View>
          ))}
          {!!searching &&
            action(
              props.phase === 'FINALIZING'
                ? 'Finishing completed AI search'
                : 'Stop AI shot search',
              stop,
              props.phase === 'FINALIZING',
            )}
          {shots.length > 0 && (
            <>
              <TextInput
                accessibilityLabel="AI picks collection name"
                value={title}
                maxLength={80}
                editable={!locked}
                onChangeText={setTitle}
                style={styles.input}
              />
              {action(
                saving
                  ? 'Saving AI picks…'
                  : 'Save chosen originals as collection',
                () => {
                  void save();
                },
                locked || coverage.reviewed === 0,
              )}
              {action(
                'Clear AI brief',
                () => {
                  lifecycle.current.revision++;
                  setDraft('');
                  setShots([]);
                  setExpanded('');
                  setNotice(
                    'Brief cleared. Existing collections, originals and search history kept.',
                  );
                },
                locked,
              )}
            </>
          )}
          <Text
            accessibilityRole="summary"
            accessibilityLiveRegion="polite"
            style={styles.copy}
          >
            {notice}
          </Text>
          <Text style={styles.copy}>
            Brief and picks clear from this screen when the account changes.
            Search descriptions also enter the existing local search history;
            use Clear search history to remove them. Saving stores chosen
            original-file references in a private collection. Originals are
            kept on device.
          </Text>
        </>
      )}
    </View>
  );
}
const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 16,
    padding: 16,
    gap: 12,
    marginBottom: 20,
  },
  heading: { color: colors.textPrimary, fontSize: 19, fontWeight: '600' },
  copy: { color: colors.textSecondary, fontSize: 13, lineHeight: 19 },
  label: { color: colors.textPrimary, fontSize: 14, fontWeight: '600' },
  button: {
    minHeight: 44,
    borderWidth: 1,
    borderColor: colors.textPrimary,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 11,
    justifyContent: 'center',
    backgroundColor: colors.surface,
  },
  buttonText: { color: colors.textPrimary, fontSize: 13, fontWeight: '600' },
  disabled: { opacity: 0.45 },
  input: {
    minHeight: 44,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 10,
    padding: 12,
    color: colors.textPrimary,
    backgroundColor: colors.surface,
  },
  multiline: { minHeight: 110, textAlignVertical: 'top' },
  shot: {
    borderTopWidth: 1,
    borderColor: colors.border,
    paddingTop: 14,
    gap: 10,
  },
  candidate: {
    backgroundColor: colors.background,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 10,
    padding: 12,
    gap: 8,
  },
  thumbnail: { width: '100%', height: 130, resizeMode: 'contain' },
});
