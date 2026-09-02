import {conservativeEligibilityPolicy, disabledTrainingUploadApi, SERAN_BACKEND_STATUS} from '../src/seran/SeranImprovement';

test('keeps the training backend disabled until production infrastructure exists', async () => {
  expect(SERAN_BACKEND_STATUS).toBe('BACKEND_NOT_CONFIGURED');
  await expect(disabledTrainingUploadApi.createShortLivedUpload(1)).rejects.toThrow('BACKEND_NOT_CONFIGURED');
});

test('uses a conservative sensitive-media exclusion policy', () => {
  expect(conservativeEligibilityPolicy.excludeByDefault).toEqual(expect.arrayContaining([
    'identity_documents', 'financial_data', 'medical_data', 'intimate_imagery',
    'credential_screenshots', 'sensitive_minor_content', 'sensitive_ocr', 'deleted_media',
  ]));
});
