# Seran Benchmark V2

Permanent, reproducible local-search benchmark for Seran V1, V2, and V3.

The historical `test-media/evaluation.json` suite is **HISTORICAL / CORPUS
INCOMPLETE**. Its reports are preserved, but its scores are not comparable to
this benchmark.

## Reconstruct and verify

```bash
cd android-app
./test-lab.sh setup-python
cd ..
android-app/test-lab/.venv/bin/python seran-benchmark-v2/setup.py
android-app/test-lab/.venv/bin/python seran-benchmark-v2/setup.py --verify-only
```

`assets.json` is the source and integrity manifest. Downloads are accepted only
when their SHA-256 matches the pinned value. Synthetic OCR/color controls are
generated deterministically and checked the same way. A mismatch is fatal; the
script never selects a replacement asset.

Media lives below `seran-benchmark-v2/media/`. Filenames are stable dataset
identifiers only and are not evaluation evidence. Run the lab with
`HONORABLE_TEST_MEDIA_ROOT=seran-benchmark-v2/media`.

`evaluation.json` contains a deterministic 70/30 development/holdout split.
The holdout cases must not be used for ranking or threshold tuning.

No live VLM is required or permitted for baseline generation.

## Index and evaluate

From `android-app/`, set `HONORABLE_TEST_MEDIA_ROOT=seran-benchmark-v2/media`
and `HONORABLE_EVAL_LABELS` to the absolute path of this directory's
`evaluation.json`. Build with `./test-lab.sh indexTestMedia`, then use the
`benchmarkSeran` task with `-PseranModel=SERAN_V1`, `SERAN_V2`, or `SERAN_V3`.
Set `HONORABLE_EVAL_OUTPUT` to keep the per-model JSON result.
