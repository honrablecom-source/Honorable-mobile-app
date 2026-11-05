import { NativeModules, Platform } from 'react-native';

export type SearchResult = {
  rank: number;
  mediaId: number;
  mediaUri: string;
  mediaType: 'IMAGE' | 'VIDEO';
  displayName: string;
  score: number;
  semantic: number;
  bestTimestampMs?: number;
  confidence: 'STRONG' | 'POSSIBLE' | 'WEAK';
  evidence: string[];
};
export type SearchResponse = {
  confident: boolean;
  decision: string;
  semantic: number;
  margin: number;
  results: SearchResult[];
};
export type EntitlementState = {
  tier: 'FREE' | 'PLUS' | 'PRO' | 'SUPER' | 'ULTIMATE';
  verified: boolean;
  storageLimitBytes: number;
  videoMinutesPerWindow: number;
  videosPerWindow: number;
  familyMembersTotal: number;
  features: string[];
};
export type LibraryItem = {
  mediaId: number;
  mediaUri: string;
  thumbnailUri?: string;
  mediaType: 'IMAGE' | 'VIDEO';
  displayName: string;
  capturedAt: number;
  durationMs?: number;
};
export type LibraryPage = { total: number; items: LibraryItem[] };
export type IndexStatus = {
  engine: 'REAL';
  permissionGranted: boolean;
  indexedCount: number;
  status: string;
  processed?: number;
  total?: number;
  failed?: number;
  lastCompletedAt?: number;
};
export type SeranModelId = 'SERAN_V1' | 'SERAN_V2' | 'SERAN_V3' | 'SERAN_ULTRA';
export type SeranModelState = {
  selected: SeranModelId;
  available: SeranModelId[];
  authority: 'NATIVE_ENTITLEMENT';
  batchLimit: number;
  semanticVideo: boolean;
  enrichmentStatus:
    | 'IDLE'
    | 'PREPARING_VIDEO_INTELLIGENCE'
    | 'READY'
    | 'FAILED';
  enrichmentProcessed: number;
  enrichmentTotal: number;
};
export type ImprovementProgramState = {
  enabled: boolean;
  backendStatus: 'BACKEND_NOT_CONFIGURED';
  uploadActive: false;
  consentVersion?: string;
  policyVersion?: string;
  acceptedAt?: number;
  revokedAt?: number;
};
type NativeBoundary = {
  pickWorkspaceMedia(kind: 'IMAGE' | 'VIDEO'): Promise<{ uri: string } | null>;
  pickBetaScreenshot(): Promise<{ base64: string } | null>;
  restoreAccountSession(): Promise<{
    status: string;
    account?: import('../passes/MemoryPassClient').MemoryAccount;
  }>;
  signInAccountWithGoogle(): Promise<{
    status: string;
    account?: import('../passes/MemoryPassClient').MemoryAccount;
  }>;
  signOutAccountSession(): Promise<{ signedOut: boolean }>;
  accountSessionAction(
    route: string,
    body: string | null,
  ): Promise<import('../passes/MemoryPassClient').MemoryAccount>;
  getAccountConfiguration(): Promise<{
    googleConfigured: boolean;
    apiUrl: string;
  }>;
  signInWithGoogle(): Promise<{ idToken: string }>;
  signOutGoogle(): Promise<{ signedOut: boolean }>;
  search(query: string): Promise<SearchResponse>;
  cancelSearch(): void;
  getStatus(): Promise<IndexStatus>;
  getLibrary(
    limit: number,
    offset: number,
    kind: string | null,
  ): Promise<LibraryPage>;
  refreshIndex(): Promise<{
    added: number;
    updated: number;
    deleted: number;
    failed: number;
    skipped: number;
    indexedCount: number;
  }>;
  getEntitlementState(): Promise<EntitlementState>;
  getSeranModelState(): Promise<SeranModelState>;
  selectSeranModel(model: string): Promise<SeranModelState>;
  getImprovementProgramState(): Promise<ImprovementProgramState>;
  setImprovementProgramConsent(
    enabled: boolean,
    consentVersion: string,
    policyVersion: string,
  ): Promise<ImprovementProgramState>;
  getSearchHistory(): Promise<{ queries: string[] }>;
  clearSearchHistory(): Promise<{ cleared: boolean }>;
  editPhoto(uri: string): Promise<{ opened: boolean }>;
  editPhotoWithLook(uri: string, look: string): Promise<{ opened: boolean }>;
  editVideo(uri: string): Promise<{ opened: boolean }>;
  editPhotoGuest(uri: string): Promise<{ opened: boolean }>;
  editPhotoGuestWithLook(
    uri: string,
    look: string,
  ): Promise<{ opened: boolean }>;
  editPhotoWithQuickStart(
    uri: string,
    preset: string,
  ): Promise<{ opened: boolean }>;
  editVideoGuest(uri: string): Promise<{ opened: boolean }>;
  createPhotoVideoGuest(): Promise<{ opened: boolean }>;
  openPhotoVideoProjectsGuest(): Promise<{ opened: boolean }>;
  openCanvasProjectsGuest(): Promise<{ opened: boolean }>;
  createDrawingGuest(): Promise<{ opened: boolean }>;
  canvasSetups(
    action: 'list' | 'save' | 'delete',
    name: string,
    width: number,
    height: number,
    background: string,
  ): Promise<string>;
  createCustomImageGuest(
    width: number,
    height: number,
    background: 'white' | 'black' | 'transparent',
  ): Promise<{ opened: boolean }>;
  createImageGuest(
    format:
      | 'square'
      | 'portrait'
      | 'landscape'
      | 'feed_portrait'
      | 'story'
      | 'reels'
      | 'ultrawide'
      | 'postcard'
      | 'banner'
      | 'poster'
      | 'cover'
      | 'wallpaper',
  ): Promise<{ opened: boolean }>;
  editVideoAtMoment(
    uri: string,
    timestampMs: number,
  ): Promise<{ opened: boolean }>;
  shareMedia(
    uri: string,
    kind: 'IMAGE' | 'VIDEO',
  ): Promise<{ opened: boolean }>;
  openMedia(
    uri: string,
    kind: string,
    timestampMs?: number,
  ): Promise<{ opened: boolean }>;
};

const native = NativeModules.HonorableSearchModule as
  | NativeBoundary
  | undefined;
function boundary(): NativeBoundary {
  if (!native)
    throw new Error(`Noble native engine is unavailable on ${Platform.OS}`);
  return native;
}
export const honorableNative = {
  pickWorkspaceMedia: (kind: 'IMAGE' | 'VIDEO') =>
    boundary().pickWorkspaceMedia(kind),
  restoreAccountSession: () => boundary().restoreAccountSession(),
  signInAccountWithGoogle: () => boundary().signInAccountWithGoogle(),
  signOutAccountSession: () => boundary().signOutAccountSession(),
  pickBetaScreenshot: () => boundary().pickBetaScreenshot(),
  accountSessionAction: (route: string, body: string | null) =>
    boundary().accountSessionAction(route, body),
  getAccountConfiguration: () => boundary().getAccountConfiguration(),
  signInWithGoogle: () => boundary().signInWithGoogle(),
  signOutGoogle: () => boundary().signOutGoogle(),
  search: (query: string) => boundary().search(query),
  cancelSearch: () => boundary().cancelSearch(),
  getStatus: () => boundary().getStatus(),
  getLibrary: (
    limit = 60,
    offset = 0,
    kind: 'ALL' | 'IMAGE' | 'VIDEO' = 'ALL',
  ) => boundary().getLibrary(limit, offset, kind === 'ALL' ? null : kind),
  refreshIndex: () => boundary().refreshIndex(),
  getEntitlementState: () => boundary().getEntitlementState(),
  getSeranModelState: () => boundary().getSeranModelState(),
  selectSeranModel: (model: SeranModelId) => boundary().selectSeranModel(model),
  getImprovementProgramState: () => boundary().getImprovementProgramState(),
  setImprovementProgramConsent: (enabled: boolean) =>
    boundary().setImprovementProgramConsent(
      enabled,
      'seran-improvement-v1',
      'privacy-draft-v1',
    ),
  getSearchHistory: () => boundary().getSearchHistory(),
  clearSearchHistory: () => boundary().clearSearchHistory(),
  editPhoto: (uri: string) => boundary().editPhoto(uri),
  editPhotoWithLook: (uri: string, look: string) =>
    boundary().editPhotoWithLook(uri, look),
  editPhotoWithQuickStart: (uri: string, preset: string) =>
    boundary().editPhotoWithQuickStart(uri, preset),
  editPhotoGuestWithLook: (uri: string, look: string) =>
    boundary().editPhotoGuestWithLook(uri, look),
  editVideo: (uri: string, timestampMs?: number) =>
    timestampMs === undefined
      ? boundary().editVideo(uri)
      : boundary().editVideoAtMoment(uri, timestampMs),
  editPhotoGuest: (uri: string) => boundary().editPhotoGuest(uri),
  editVideoGuest: (uri: string) => boundary().editVideoGuest(uri),
  canvasSetups: (
    action: 'list' | 'save' | 'delete',
    name: string,
    width: number,
    height: number,
    background: string,
  ) => boundary().canvasSetups(action, name, width, height, background),
  createPhotoVideoGuest: () => boundary().createPhotoVideoGuest(),
  openPhotoVideoProjectsGuest: () => boundary().openPhotoVideoProjectsGuest(),
  openCanvasProjectsGuest: () => boundary().openCanvasProjectsGuest(),
  createDrawingGuest: () => boundary().createDrawingGuest(),
  createCustomImageGuest: (
    width: number,
    height: number,
    background: 'white' | 'black' | 'transparent',
  ) => boundary().createCustomImageGuest(width, height, background),
  createImageGuest: (
    format:
      | 'square'
      | 'portrait'
      | 'landscape'
      | 'feed_portrait'
      | 'story'
      | 'reels'
      | 'ultrawide'
      | 'postcard'
      | 'banner'
      | 'poster'
      | 'cover'
      | 'wallpaper',
  ) => boundary().createImageGuest(format),
  shareMedia: (item: { mediaUri: string; mediaType: 'IMAGE' | 'VIDEO' }) =>
    boundary().shareMedia(item.mediaUri, item.mediaType),
  openMedia: (item: {
    mediaUri: string;
    mediaType: 'IMAGE' | 'VIDEO';
    bestTimestampMs?: number;
  }) =>
    boundary().openMedia(item.mediaUri, item.mediaType, item.bestTimestampMs),
};
