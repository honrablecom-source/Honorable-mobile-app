import {
  prepareAiBrief,
  attachAiCandidates,
  chooseAiCandidate,
  aiBriefCoverage,
  aiBriefCollectionItems,
} from '../src/search/AiShotBrief';
import type { SearchResponse } from '../src/native/HonorableNative';
const result: SearchResponse = {
  confident: true,
  decision: 'MATCH',
  semantic: 0.8,
  margin: 0.1,
  results: Array.from({ length: 10 }, (_, i) => ({
    rank: 9 - i,
    mediaId: 100 + i,
    mediaUri: `content://synthetic/${i}`,
    mediaType: 'IMAGE' as const,
    displayName: 'Synthetic',
    score: 0.9 - i * 0.01,
    semantic: 0.8,
    confidence: 'STRONG' as const,
    evidence: ['semantic'],
  })),
};
test('lines remain explicit user prompts, with no inferred shot planning', () => {
  expect(
    prepareAiBrief('  candles on cake\r\nfriends laughing\n').map(x => x.query),
  ).toEqual(['candles on cake', 'friends laughing']);
  expect(prepareAiBrief('wide shot\nwide shot')).toHaveLength(2);
  for (const text of [
    'one',
    Array(7).fill('shot').join('\n'),
    'a'.repeat(241) + '\nsecond',
  ])
    expect(() => prepareAiBrief(text)).toThrow();
});
test('candidate head retains native order, owns evidence and never auto-selects', () => {
  const shots = prepareAiBrief('cake\nfriends'),
    updated = attachAiCandidates(shots, 'shot-1', result);
  expect(updated[0].candidates.map(x => x.rank)).toEqual([
    9, 8, 7, 6, 5, 4, 3, 2,
  ]);
  expect(updated[0].selected).toBeUndefined();
  expect(shots[0].candidates).toEqual([]);
  updated[0].candidates[0].evidence.push('local');
  expect(result.results[0].evidence).toEqual(['semantic']);
});
test('coverage means reviewed choices; reused originals deduplicate in brief order', () => {
  let shots = prepareAiBrief('cake\nfriends');
  shots = attachAiCandidates(shots, 'shot-1', result);
  shots = attachAiCandidates(shots, 'shot-2', result);
  shots = chooseAiCandidate(shots, 'shot-1', 100);
  shots = chooseAiCandidate(shots, 'shot-2', 100);
  expect(aiBriefCoverage(shots)).toEqual({
    reviewed: 2,
    total: 2,
    uniqueMedia: 1,
  });
  expect(aiBriefCollectionItems(shots)).toEqual([
    { id: '100', uri: 'content://synthetic/0', kind: 'IMAGE' },
  ]);
  shots = chooseAiCandidate(shots, 'shot-1');
  expect(aiBriefCoverage(shots).reviewed).toBe(1);
});
test('foreign choices and empty save refuse; rerunning a row clears its prior approval', () => {
  let shots = prepareAiBrief('cake\nfriends');
  expect(() => attachAiCandidates(shots, 'missing', result)).toThrow();
  expect(() => chooseAiCandidate(shots, 'shot-1', 100)).toThrow();
  expect(() => aiBriefCollectionItems(shots)).toThrow();
  shots = attachAiCandidates(shots, 'shot-1', result);
  shots = chooseAiCandidate(shots, 'shot-1', 100);
  expect(
    attachAiCandidates(shots, 'shot-1', result)[0].selected,
  ).toBeUndefined();
});
