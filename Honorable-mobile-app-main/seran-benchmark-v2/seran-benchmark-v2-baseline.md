# Seran Benchmark V2 baseline

The new corpus is complete and reproducible: **50 photos, 10 videos, 150 cases**.
It has 105 development cases and 45 holdout cases. The holdout split remains
reserved from tuning. Assets and generated controls passed SHA-256 verification.

The index contains all 60 assets. TinyCLIP, OCR, color evidence, and video-frame
evidence were active; live VLM was disabled. Indexing took 164.822 seconds and
the index is 433,061 bytes.

| Metric | Seran V1 | Seran V2 | Seran V3 |
|---|---:|---:|---:|
| Overall Top1 | 29.0% | 15.9% | 16.7% |
| Overall Top3 | 37.0% | 33.3% | 33.3% |
| Overall Top5 | 44.9% | 39.1% | 38.4% |
| Photo Top1 | 47.1% | 15.3% | 15.3% |
| Photo Top3 | 60.0% | 22.4% | 21.2% |
| OCR Top1 | 70.0% | 70.0% | 70.0% |
| Video Top1 | 0.0% | 23.3% | 23.3% |
| Video Top3 | 0.0% | 56.7% | 50.0% |
| Moment hit rate | 0.0% | 4.3% | 0.0% |
| Moment correct-video rate | 0.0% | 8.7% | 13.0% |
| Median timestamp error | N/A | 190927.0 ms | 274612.0 ms |
| No-match accuracy | 83.3% | 58.3% | 50.0% |
| False-positive rate | 16.7% | 41.7% | 50.0% |
| Median query latency | 130.2 ms | 126.9 ms | 144.0 ms |
| P95 query latency | 387.2 ms | 371.9 ms | 424.8 ms |

## V2 versus V3

| Metric | V2 | V3 | Difference | Result |
|---|---:|---:|---:|---|
| videoTop1 | 0.233 | 0.233 | +0.000 | UNCHANGED |
| videoTop3 | 0.567 | 0.500 | -0.067 | REGRESSED |
| momentCorrectVideoRate | 0.087 | 0.130 | +0.043 | IMPROVED |
| momentHitRate | 0.043 | 0.000 | -0.043 | REGRESSED |
| medianTimestampErrorMs | 190927.000 ms | 274612.000 ms | +83685.000 ms | REGRESSED |
| falsePositiveRate | 0.417 | 0.500 | +0.083 | REGRESSED |
| medianQueryLatencyMs | 126.858 ms | 143.967 ms | +17.109 ms | REGRESSED |
| p95QueryLatencyMs | 371.917 ms | 424.753 ms | +52.836 ms | REGRESSED |
| indexingTimeMs | 164822.000 ms | 164822.000 ms | +0.000 ms | UNCHANGED |
| indexSizeBytes | 433061.000 | 433061.000 | +0.000 | UNCHANGED |

V3 is **not better than V2** on this baseline. It improved moment correct-video
rate, but exact-moment hit rate fell to zero, timestamp error increased, video
Top3 regressed, false positives increased, and latency worsened. V3 remains
**EXPERIMENTAL / LOCKED**. No weights or thresholds were tuned after measurement.

The old 69-case benchmark remains **HISTORICAL / CORPUS INCOMPLETE** and its
numbers are not directly comparable with this corpus.
