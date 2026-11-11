import type { SearchResponse } from '../native/HonorableNative';
import { modelCost } from '../passes/catalog';

export type AvailableAiModel = 'SERAN_V1' | 'SERAN_V2';
export type AiSearchPhase =
  | 'SELECTING'
  | 'STARTING'
  | 'SEARCHING'
  | 'FINALIZING'
  | 'IDLE';
export type AiSearchDependencies = {
  selectModel: (model: AvailableAiModel) => Promise<unknown>;
  start: (model: AvailableAiModel, requestId: string) => Promise<void>;
  search: (query: string) => Promise<SearchResponse>;
  finish: (requestId: string, outcome: 'SUCCESS' | 'FAILED') => Promise<void>;
  cancelNative: () => void;
  isCurrent: () => boolean;
  requestId: () => string;
  onPhase?: (phase: AiSearchPhase) => void;
};
type Job = {
  id: string;
  phase: AiSearchPhase;
  cancelled: boolean;
  nativeStarted: boolean;
};
export class OwnedAiSearchCancelled extends Error {
  constructor() {
    super('AI search stopped. No successful-search completion was requested.');
    this.name = 'OwnedAiSearchCancelled';
  }
}
export class AiCreditStatusUnknown extends Error {
  constructor() {
    super(
      'Search credit status could not be confirmed. Reconnect and check your account before retrying.',
    );
    this.name = 'AiCreditStatusUnknown';
  }
}
function snapshot(value: SearchResponse): SearchResponse {
  if (
    !value ||
    !Array.isArray(value.results) ||
    typeof value.confident !== 'boolean' ||
    typeof value.decision !== 'string' ||
    !Number.isFinite(value.semantic) ||
    !Number.isFinite(value.margin)
  )
    throw Error('The AI search response was incomplete.');
  return {
    ...value,
    results: value.results.map(result => {
      if (
        !result ||
        !Number.isSafeInteger(result.mediaId) ||
        result.mediaId < 0 ||
        !Number.isInteger(result.rank) ||
        result.rank < 0 ||
        !result.mediaUri ||
        !['IMAGE', 'VIDEO'].includes(result.mediaType) ||
        !Number.isFinite(result.score) ||
        !Number.isFinite(result.semantic) ||
        !['STRONG', 'POSSIBLE', 'WEAK'].includes(result.confidence) ||
        !Array.isArray(result.evidence) ||
        !result.evidence.every(item => typeof item === 'string')
      )
        throw Error('An AI candidate was incomplete.');
      return { ...result, evidence: [...result.evidence] };
    }),
  };
}
/** One native operation at a time. Only server start/complete routes determine credit accounting. */
export class OwnedAiSearch {
  private job?: Job;
  private usedIds = new Set<string>();
  constructor(private readonly dependencies: AiSearchDependencies) {}
  get busy() {
    return !!this.job;
  }
  get phase(): AiSearchPhase {
    return this.job?.phase ?? 'IDLE';
  }
  private notify(phase: AiSearchPhase) {
    try {
      this.dependencies.onPhase?.(phase);
    } catch {
      /* UI observers cannot change ledger settlement. */
    }
  }
  cancel(): boolean {
    const job = this.job;
    // Inference has completed once finalization starts. Never report a late cancel as accepted.
    if (!job || job.phase === 'FINALIZING') return false;
    if (job.cancelled) return true;
    job.cancelled = true;
    if (job.phase === 'SEARCHING' && job.nativeStarted) {
      try {
        this.dependencies.cancelNative();
      } catch {
        /* Keep ownership until native settles. */
      }
    }
    return true;
  }
  async run(
    raw: string,
    model: AvailableAiModel,
  ): Promise<SearchResponse | undefined> {
    if (this.job) throw Error('Finish the current AI search first.');
    const query = typeof raw === 'string' ? raw.trim() : '';
    if (!query) throw Error('Describe the shot you want to find.');
    if (
      (model !== 'SERAN_V1' && model !== 'SERAN_V2') ||
      !modelCost(model)?.available
    )
      throw Error('This AI model is locked.');
    if (!this.dependencies.isCurrent()) throw new OwnedAiSearchCancelled();
    const id = this.dependencies.requestId();
    if (
      typeof id !== 'string' ||
      !id ||
      id.length > 128 ||
      this.usedIds.has(id)
    )
      throw Error('A fresh search request is required.');
    this.usedIds.add(id);
    const job: Job = {
      id,
      phase: 'SELECTING',
      cancelled: false,
      nativeStarted: false,
    };
    this.job = job;
    const current = () => {
      if (job.cancelled || !this.dependencies.isCurrent())
        throw new OwnedAiSearchCancelled();
    };
    const phase = (next: AiSearchPhase) => {
      job.phase = next;
      this.notify(next);
    };
    let startAttempted = false,
      finalized = false,
      failureAttempted = false;
    try {
      phase('SELECTING');
      current();
      await this.dependencies.selectModel(model);
      current();
      phase('STARTING');
      current();
      startAttempted = true;
      await this.dependencies.start(model, id);
      current();
      phase('SEARCHING');
      current();
      job.nativeStarted = true;
      const response = snapshot(await this.dependencies.search(query));
      current();
      phase('FINALIZING');
      const outcome = response.results.length ? 'SUCCESS' : 'FAILED';
      failureAttempted = outcome === 'FAILED';
      await this.dependencies.finish(id, outcome);
      finalized = true;
      // A completed search can become stale while the server confirms it; do not leak it to a new owner.
      return this.dependencies.isCurrent() ? response : undefined;
    } catch (error) {
      if (startAttempted && !finalized && !failureAttempted) {
        failureAttempted = true;
        try {
          await this.dependencies.finish(id, 'FAILED');
        } catch {
          throw new AiCreditStatusUnknown();
        }
      } else if (failureAttempted && !finalized) {
        throw new AiCreditStatusUnknown();
      }
      throw error;
    } finally {
      if (this.job === job) {
        this.job = undefined;
        this.notify('IDLE');
      }
    }
  }
}
