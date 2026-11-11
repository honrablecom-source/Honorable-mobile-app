import type {
  LibraryCollectionItem,
  SearchResponse,
  SearchResult,
} from '../native/HonorableNative';
export type AiBriefShot = {
  id: string;
  query: string;
  candidates: SearchResult[];
  selected?: SearchResult;
  searched: boolean;
};
export function prepareAiBrief(text: string): AiBriefShot[] {
  const lines = text
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean);
  if (
    lines.length < 2 ||
    lines.length > 6 ||
    lines.some(line => line.length > 240)
  )
    throw Error(
      'Describe 2–6 desired shots, one per line, up to 240 characters each.',
    );
  return lines.map((query, index) => ({
    id: `shot-${index + 1}`,
    query,
    candidates: [],
    searched: false,
  }));
}
/** Native AI supplies candidates and rank. Coverage is explicitly the user's reviewed selection. */
export function attachAiCandidates(
  shots: readonly AiBriefShot[],
  id: string,
  response: SearchResponse,
): AiBriefShot[] {
  if (!shots.some(shot => shot.id === id))
    throw Error('This shot is no longer in the brief.');
  return shots.map(shot =>
    shot.id !== id
      ? shot
      : {
          ...shot,
          candidates: response.results
            .slice(0, 8)
            .map(result => ({ ...result, evidence: [...result.evidence] })),
          searched: true,
          selected: undefined,
        },
  );
}
export function chooseAiCandidate(
  shots: readonly AiBriefShot[],
  id: string,
  mediaId?: number,
): AiBriefShot[] {
  if (!shots.some(shot => shot.id === id))
    throw Error('This shot is no longer in the brief.');
  return shots.map(shot => {
    if (shot.id !== id) return shot;
    const candidate =
      mediaId === undefined
        ? undefined
        : shot.candidates.find(item => item.mediaId === mediaId);
    if (mediaId !== undefined && !candidate)
      throw Error('Choose a candidate from this shot’s reviewed AI results.');
    return {
      ...shot,
      selected: candidate && {
        ...candidate,
        evidence: [...candidate.evidence],
      },
    };
  });
}
export function aiBriefCoverage(shots: readonly AiBriefShot[]) {
  const chosen = shots.flatMap(shot => (shot.selected ? [shot.selected] : []));
  return {
    reviewed: chosen.length,
    total: shots.length,
    uniqueMedia: new Set(chosen.map(result => result.mediaId)).size,
  };
}
export function aiBriefCollectionItems(
  shots: readonly AiBriefShot[],
): LibraryCollectionItem[] {
  const seen = new Set<number>(),
    items: LibraryCollectionItem[] = [];
  for (const shot of shots)
    if (shot.selected && !seen.has(shot.selected.mediaId)) {
      const result = shot.selected;
      seen.add(result.mediaId);
      items.push({
        id: String(result.mediaId),
        uri: result.mediaUri,
        kind: result.mediaType,
      });
    }
  if (!items.length)
    throw Error('Review and choose at least one AI candidate first.');
  return items;
}
