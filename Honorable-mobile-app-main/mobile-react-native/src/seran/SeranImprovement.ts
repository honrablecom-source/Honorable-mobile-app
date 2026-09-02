export const SERAN_BACKEND_STATUS = 'BACKEND_NOT_CONFIGURED' as const;

export type TrainingContribution = {
  contributionId: string;
  anonymousContributorId: string;
  mediaType: 'IMAGE'|'VIDEO';
  storageObjectId: string;
  createdAt: string;
  consentVersion: string;
  policyVersion: string;
  seranSourceModel: string;
  analysisVersion: string;
  processingStatus: 'PENDING'|'VALIDATED'|'REJECTED'|'DELETION_REQUESTED'|'DELETED';
  split?: 'TRAIN'|'VALIDATION'|'TEST';
  annotationProvenance?: 'MODEL_GENERATED'|'HUMAN_VERIFIED';
};

export type EligibilityDecision = {eligible: boolean; reasons: string[]};
export interface SensitiveMediaFilter {evaluate(localMediaId: number): Promise<EligibilityDecision>}
export interface TrainingContributionQueue {enqueue(localMediaId: number): Promise<void>; cancelAll(): Promise<void>}
export interface TrainingUploadApi {createShortLivedUpload(localMediaId: number): Promise<never>; requestDeletion(): Promise<void>}

/** Production-safe default: it cannot enqueue or upload any media. */
export const disabledTrainingUploadApi: TrainingUploadApi = {
  async createShortLivedUpload() {throw new Error(SERAN_BACKEND_STATUS)},
  async requestDeletion() {throw new Error(SERAN_BACKEND_STATUS)},
};

export const conservativeEligibilityPolicy = {
  policyVersion: 'privacy-draft-v1',
  excludeByDefault: ['sensitive_documents','identity_documents','financial_data','medical_data','intimate_imagery','credential_screenshots','sensitive_minor_content','private_documents','sensitive_ocr','hidden_albums','deleted_media'],
} as const;
