# Seran development failure analysis

Scope: **105 development cases only**. Holdout executed: **NO**. Holdout used for tuning: **NO**. Ranking behavior modified: **NO**.

## Development metrics

| Model | Overall T1/T3/T5 | Photo T1/T3 | Video T1/T3 | Moment hit/correct-video | No-match accuracy / FP |
|---|---:|---:|---:|---:|---:|
| V1 | 29.2% / 36.5% / 43.8% | 49.1% / 61.4% | 0.0% / 0.0% | 0.0% / 0.0% | 77.8% / 22.2% |
| V2 | 13.5% / 31.2% / 37.5% | 14.0% / 21.1% | 17.4% / 47.8% | 6.2% / 6.2% | 66.7% / 33.3% |
| V3 | 15.6% / 31.2% / 36.5% | 14.0% / 19.3% | 17.4% / 43.5% | 0.0% / 18.8% | 55.6% / 44.4% |

## Highest-priority finding: V2 photo collapse

V2 restores semantic/frame evidence to videos for media-ambiguous photo queries. Those videos enter the same uncalibrated global score ranking and take Top1. Explicit image-filtered queries retain the V1 ordering.

Measured on 57 positive photo-like cases: 20 were correct in V1 and wrong in V2; 20 of those regressions had a video at Top1. Of 10 explicitly image-parsed queries, 10 retained identical V1/V2 Top10 ordering.

Ruled out:
- ANN dilution: candidate limit is 500 while the immutable index has 60 asset records, so every asset vector can enter retrieval.
- Per-frame ANN domination: LocalVectorIndex stores one asset embedding per video; frame vectors are scored only inside the asset record.
- Different photo weights/path: V1 and V2 transform only VIDEO records; IMAGE records and query decomposition are unchanged.

## Failure-class counts

| Class | V1 | V2 | V3 |
|---|---:|---:|---:|
| CANDIDATE_MISS | 3 | 3 | 3 |
| COLOR_MISS | 5 | 1 | 1 |
| CORRECT_IN_TOP5_BAD_RERANK | 6 | 3 | 3 |
| FALSE_POSITIVE_NO_MATCH | 2 | 3 | 4 |
| MULTI_CONDITION_COVERAGE_FAILURE | 8 | 5 | 4 |
| NEGATIVE_CONDITION_FAILURE | 4 | 2 | 2 |
| OCR_MISS | 2 | 2 | 2 |
| OTHER | 1 | 0 | 0 |
| PHOTO_VIDEO_SCORE_INTERFERENCE | 0 | 33 | 34 |
| VIDEO_SELECTION_FAILURE | 39 | 34 | 32 |

## Candidate recall

Counts are positive cases whose expected asset appears in the scored Top5/Top10. `Absent` means absent from the returned scored Top10; because 60 asset vectors fit in the 500-record ANN cap, it is not capacity dilution.

| Model / group | Cases | Top5 | Top10 | Absent |
|---|---:|---:|---:|---:|
| V1 / OCR | 7 | 5 | 5 | 2 |
| V1 / moment | 16 | 0 | 0 | 16 |
| V1 / multi-condition | 8 | 4 | 5 | 2 |
| V1 / negative | 9 | 6 | 7 | 2 |
| V1 / photo | 33 | 27 | 28 | 4 |
| V1 / video | 23 | 0 | 0 | 23 |
| V2 / OCR | 7 | 5 | 5 | 2 |
| V2 / moment | 16 | 11 | 15 | 1 |
| V2 / multi-condition | 8 | 1 | 1 | 2 |
| V2 / negative | 9 | 0 | 0 | 2 |
| V2 / photo | 33 | 7 | 7 | 4 |
| V2 / video | 23 | 12 | 13 | 10 |
| V3 / OCR | 7 | 5 | 5 | 2 |
| V3 / moment | 16 | 11 | 15 | 1 |
| V3 / multi-condition | 8 | 1 | 1 | 2 |
| V3 / negative | 9 | 0 | 0 | 2 |
| V3 / photo | 33 | 6 | 7 | 4 |
| V3 / video | 23 | 12 | 13 | 10 |

## Video and moment findings

V2/V3 improve video retrieval over V1, but most development video failures are selection/reranking failures; sparse 3-frame coverage on five short clips and 14-35 frames spaced roughly 14-25 seconds on long clips limit moment evidence.

V2's principal bottleneck is retrieving the correct video. V3 raises correct-video retrieval but its temporal ranker then selects wrong windows or suppresses timestamps as low-confidence; development moment hit rate remains zero.

### Indexed video frames

| Video | Frames | Timestamps (ms) |
|---|---:|---|
| `videos/video-001.mp4` | 3 | 0, 1919, 3838 |
| `videos/video-002.mp4` | 3 | 0, 3405, 6810 |
| `videos/video-003.mp4` | 3 | 0, 5208, 10417 |
| `videos/video-004.mp4` | 3 | 0, 6764, 13528 |
| `videos/video-005.mp4` | 3 | 0, 10146, 20292 |
| `videos/video-006.mov` | 35 | 0, 18809, 37618, 56427, 75236, 94045, 112854, 131663, 150472, 169282, 188091, 206900, 225709, 244518, 263327, 282136, 300945, 319754, 338564, 357373, 376182, 394991, 413800, 432609, 451418, 470227, 489036, 507846, 526655, 545464, 564273, 583082, 601891, 620700, 639509 |
| `videos/video-007.mov` | 35 | 0, 20976, 41952, 62928, 83904, 104880, 125856, 146833, 167809, 188785, 209761, 230737, 251713, 272689, 293666, 314642, 335618, 356594, 377570, 398546, 419522, 440499, 461475, 482451, 503427, 524403, 545379, 566355, 587332, 608308, 629284, 650260, 671236, 692212, 713188 |
| `videos/video-008.mkv` | 35 | 0, 25372, 50744, 76116, 101489, 126861, 152233, 177606, 202978, 228350, 253723, 279095, 304467, 329840, 355212, 380584, 405957, 431329, 456701, 482073, 507446, 532818, 558190, 583563, 608935, 634307, 659680, 685052, 710424, 735797, 761169, 786541, 811914, 837286, 862658 |
| `videos/video-009.mov` | 14 | 0, 14288, 28576, 42864, 57152, 71440, 85728, 100016, 114304, 128592, 142880, 157168, 171456, 185744 |
| `videos/video-010.mov` | 6 | 0, 14199, 28399, 42599, 56799, 70999 |

## No-match and score calibration

Absolute-score plus margin confidence accepts semantically plausible nearest neighbors even when the query's requested combination is absent. V2 video evidence and V3 deep window score inflate scores on negatives; margins alone do not measure signal agreement.

Correct and incorrect score distributions overlap; video frame/window additions raise a different evidence scale than image scores. Raw global scores are therefore not safely comparable across media types or as a no-match probability.

Full per-negative Top1/Top2 scores, margins, candidate type, specificity, evidence, and confidence reason are in the JSON report.

## OCR protection

Development OCR Top1 is 71.4% in all three models. OCR evidence is stable and should remain protected by regression tests.

## Targeted recommendations (not implemented)

1. **P0 — For non-video-intent queries, preserve a V1-compatible image lane and union it with at most one calibrated candidate per video before final ranking.**
   Addresses: PHOTO_VIDEO_SCORE_INTERFERENCE. Metric: photo Top1/Top5. Risk: May reduce valid ambiguous video recall. Independently testable: yes. Likely files/functions: android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt:search, android-app/app/src/main/java/app/honorable/search/SearchCore.kt:HybridSearchEngine.
2. **P0 — Calibrate confidence by media type and require multi-signal agreement for no-match acceptance; fit only on development negatives.**
   Addresses: FALSE_POSITIVE_NO_MATCH and OVERCONFIDENT_LOW_SIGNAL_RESULT. Metric: no-match accuracy/false-positive rate. Risk: False negatives on weak but valid semantic queries. Independently testable: yes. Likely files/functions: android-app/app/src/main/java/app/honorable/search/SearchCore.kt:confidenceDecision.
3. **P1 — Separate video selection from temporal-window selection and retain the correct-video candidate before V3 window reranking.**
   Addresses: VIDEO_SELECTION_FAILURE. Metric: video Top1/Top3 and moment correct-video rate. Risk: Two-stage calibration may add latency or lock in a wrong video. Independently testable: yes. Likely files/functions: android-app/app/src/main/java/app/honorable/search/TemporalSearch.kt:V3DeepReranker.
4. **P1 — Use overlapping/adaptive temporal windows around sampled frames and compare neighboring windows before emitting or suppressing timestamps.**
   Addresses: TEMPORAL_WINDOW_FAILURE and TIMESTAMP_CONFIDENCE_FAILURE. Metric: moment hit rate/timestamp error. Risk: More index/compute cost; correlated windows can shrink margins. Independently testable: yes. Likely files/functions: android-app/app/src/main/java/app/honorable/search/TemporalSearch.kt:TemporalWindowBuilder,V3MomentRanker.
5. **P2 — Add explicit development regressions that freeze OCR Top1/evidence while testing any candidate or confidence calibration.**
   Addresses: OCR protection. Metric: OCR Top1 remains >= current 71.4% development result. Risk: None beyond test maintenance. Independently testable: yes. Likely files/functions: android-app/test-lab/src/test/kotlin/app/honorable/testlab.

## Per-failure index

Every detailed failure record—including query interpretation, candidates/scores, evidence channels, temporal evidence, confidence, margin, candidate presence, and failure stage—is stored in `seran-development-failure-analysis.json`.

| Query | Model | Category | Class | Expected rank | Top1 |
|---|---|---|---|---:|---|
| case-004 | V1 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | 3 | `controls/color-green-yellow.png` |
| case-006 | V1 | OBJECT | CORRECT_IN_TOP5_BAD_RERANK | 4 | `controls/color-green-yellow.png` |
| case-008 | V1 | SCENE | CORRECT_IN_TOP5_BAD_RERANK | 2 | `photos/photo-005.jpg` |
| case-011 | V1 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | absent | `none` |
| case-016 | V1 | COLOR | COLOR_MISS | 4 | `photos/photo-014.jpg` |
| case-020 | V1 | INDOOR | CORRECT_IN_TOP5_BAD_RERANK | 2 | `photos/photo-041.jpg` |
| case-025 | V1 | NEGATIVE CONDITION | NEGATIVE_CONDITION_FAILURE | 3 | `photos/photo-036.jpg` |
| case-026 | V1 | NEGATIVE CONDITION | NEGATIVE_CONDITION_FAILURE | absent | `none` |
| case-027 | V1 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | 10 | `photos/photo-019.jpg` |
| case-028 | V1 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | 3 | `controls/ocr-flight.png` |
| case-029 | V1 | PEOPLE | CANDIDATE_MISS | absent | `none` |
| case-030 | V1 | PEOPLE | CORRECT_IN_TOP5_BAD_RERANK | 2 | `photos/photo-006.jpg` |
| case-032 | V1 | PEOPLE | CANDIDATE_MISS | absent | `none` |
| case-033 | V1 | PEOPLE | OTHER | 39 | `photos/photo-021.jpg` |
| case-034 | V1 | PEOPLE | CORRECT_IN_TOP5_BAD_RERANK | 4 | `photos/photo-021.jpg` |
| case-036 | V1 | COLOR | COLOR_MISS | 6 | `photos/photo-006.jpg` |
| case-038 | V1 | COLOR | COLOR_MISS | 4 | `photos/photo-020.jpg` |
| case-040 | V1 | PEOPLE | CANDIDATE_MISS | absent | `none` |
| case-045 | V1 | COLOR | COLOR_MISS | absent | `none` |
| case-052 | V1 | ANIMAL | CORRECT_IN_TOP5_BAD_RERANK | 2 | `controls/ocr-flight.png` |
| case-059 | V1 | COLOR | COLOR_MISS | 5 | `controls/color-black-white.png` |
| case-061 | V1 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | 4 | `controls/color-green-yellow.png` |
| case-062 | V1 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | 4 | `controls/color-green-yellow.png` |
| case-063 | V1 | NEGATIVE CONDITION | NEGATIVE_CONDITION_FAILURE | 6 | `photos/photo-003.jpg` |
| case-065 | V1 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | absent | `none` |
| case-066 | V1 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | 43 | `photos/photo-020.jpg` |
| case-074 | V1 | OCR | OCR_MISS | absent | `none` |
| case-079 | V1 | OCR | OCR_MISS | absent | `none` |
| case-085 | V1 | NEGATIVE CONDITION | NEGATIVE_CONDITION_FAILURE | absent | `none` |
| case-088 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-089 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-090 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-091 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-093 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-094 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-095 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-097 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-098 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-099 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-100 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `photos/photo-037.jpg` |
| case-101 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-102 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-103 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-104 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `photos/photo-036.jpg` |
| case-105 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-106 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-107 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `controls/color-black-white.png` |
| case-110 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `photos/photo-035.jpg` |
| case-111 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-112 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-113 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-114 | V1 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-117 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `photos/photo-042.jpg` |
| case-118 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `photos/photo-031.jpg` |
| case-120 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `photos/photo-037.jpg` |
| case-121 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `controls/ocr-payment.png` |
| case-122 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-125 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `photos/photo-011.jpg` |
| case-126 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `photos/photo-037.jpg` |
| case-127 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `controls/color-red-blue.png` |
| case-130 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `photos/photo-019.jpg` |
| case-132 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `controls/color-purple-orange.png` |
| case-133 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `photos/photo-011.jpg` |
| case-134 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `photos/photo-029.jpg` |
| case-135 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `photos/photo-019.jpg` |
| case-136 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `photos/photo-037.jpg` |
| case-137 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `photos/photo-039.jpg` |
| case-138 | V1 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `photos/photo-036.jpg` |
| case-145 | V1 | NO-MATCH | FALSE_POSITIVE_NO_MATCH | absent | `controls/ocr-payment.png` |
| case-146 | V1 | NO-MATCH | FALSE_POSITIVE_NO_MATCH | absent | `controls/color-black-white.png` |
| case-002 | V2 | SPORTS | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-004.mp4` |
| case-004 | V2 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | 3 | `controls/color-green-yellow.png` |
| case-005 | V2 | OBJECT | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-002.mp4` |
| case-006 | V2 | OBJECT | CORRECT_IN_TOP5_BAD_RERANK | 4 | `controls/color-green-yellow.png` |
| case-007 | V2 | SCENE | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-008 | V2 | SCENE | CORRECT_IN_TOP5_BAD_RERANK | 2 | `photos/photo-005.jpg` |
| case-011 | V2 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | absent | `none` |
| case-014 | V2 | OBJECT | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-015 | V2 | COLOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-016 | V2 | COLOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 14 | `videos/video-006.mov` |
| case-019 | V2 | INDOOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-009.mov` |
| case-020 | V2 | INDOOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 12 | `videos/video-009.mov` |
| case-021 | V2 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-009.mov` |
| case-022 | V2 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-009.mov` |
| case-024 | V2 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-025 | V2 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 13 | `videos/video-008.mkv` |
| case-026 | V2 | NEGATIVE CONDITION | NEGATIVE_CONDITION_FAILURE | absent | `none` |
| case-027 | V2 | MULTI-CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 20 | `videos/video-007.mov` |
| case-028 | V2 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | 13 | `controls/ocr-flight.png` |
| case-029 | V2 | PEOPLE | CANDIDATE_MISS | absent | `none` |
| case-030 | V2 | PEOPLE | CORRECT_IN_TOP5_BAD_RERANK | 2 | `photos/photo-006.jpg` |
| case-032 | V2 | PEOPLE | CANDIDATE_MISS | absent | `none` |
| case-033 | V2 | PEOPLE | PHOTO_VIDEO_SCORE_INTERFERENCE | 49 | `videos/video-008.mkv` |
| case-034 | V2 | PEOPLE | PHOTO_VIDEO_SCORE_INTERFERENCE | 14 | `videos/video-006.mov` |
| case-035 | V2 | COLOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-009.mov` |
| case-036 | V2 | COLOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 16 | `videos/video-009.mov` |
| case-038 | V2 | COLOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 14 | `videos/video-008.mkv` |
| case-040 | V2 | PEOPLE | CANDIDATE_MISS | absent | `none` |
| case-044 | V2 | CLOTHING | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-007.mov` |
| case-045 | V2 | COLOR | COLOR_MISS | absent | `none` |
| case-047 | V2 | FOOD | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-010.mov` |
| case-049 | V2 | FOOD | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-010.mov` |
| case-050 | V2 | FOOD | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-051 | V2 | ANIMAL | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-001.mp4` |
| case-052 | V2 | ANIMAL | PHOTO_VIDEO_SCORE_INTERFERENCE | 12 | `videos/video-001.mp4` |
| case-053 | V2 | ANIMAL | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-055 | V2 | CITY | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-004.mp4` |
| case-059 | V2 | COLOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 15 | `videos/video-005.mp4` |
| case-061 | V2 | MULTI-CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 14 | `videos/video-004.mp4` |
| case-062 | V2 | MULTI-CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 14 | `videos/video-004.mp4` |
| case-063 | V2 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 16 | `videos/video-010.mov` |
| case-064 | V2 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-004.mp4` |
| case-065 | V2 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | absent | `none` |
| case-066 | V2 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | 43 | `photos/photo-020.jpg` |
| case-068 | V2 | ACTIVITY | PHOTO_VIDEO_SCORE_INTERFERENCE | 2 | `videos/video-004.mp4` |
| case-069 | V2 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-074 | V2 | OCR | OCR_MISS | absent | `none` |
| case-079 | V2 | OCR | OCR_MISS | absent | `none` |
| case-085 | V2 | NEGATIVE CONDITION | NEGATIVE_CONDITION_FAILURE | absent | `none` |
| case-088 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 2 | `videos/video-004.mp4` |
| case-089 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 3 | `videos/video-005.mp4` |
| case-091 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-093 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 3 | `videos/video-005.mp4` |
| case-094 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-097 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-098 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 2 | `videos/video-003.mp4` |
| case-099 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-100 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 2 | `photos/photo-037.jpg` |
| case-101 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-103 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-104 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 2 | `photos/photo-036.jpg` |
| case-105 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-106 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-107 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 2 | `videos/video-006.mov` |
| case-110 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 7 | `videos/video-005.mp4` |
| case-111 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 5 | `videos/video-005.mp4` |
| case-112 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-113 | V2 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-117 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 8 | `videos/video-005.mp4` |
| case-118 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 2 | `videos/video-005.mp4` |
| case-120 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 2 | `photos/photo-037.jpg` |
| case-121 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 3 | `controls/ocr-payment.png` |
| case-122 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-125 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 4 | `videos/video-008.mkv` |
| case-126 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 2 | `videos/video-001.mp4` |
| case-127 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 4 | `videos/video-009.mov` |
| case-130 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 2 | `photos/photo-019.jpg` |
| case-132 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 5 | `videos/video-008.mkv` |
| case-133 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 2 | `videos/video-010.mov` |
| case-134 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 6 | `videos/video-005.mp4` |
| case-135 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 5 | `videos/video-010.mov` |
| case-136 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 6 | `photos/photo-037.jpg` |
| case-137 | V2 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 6 | `videos/video-006.mov` |
| case-145 | V2 | NO-MATCH | FALSE_POSITIVE_NO_MATCH | absent | `controls/ocr-payment.png` |
| case-146 | V2 | NO-MATCH | FALSE_POSITIVE_NO_MATCH | absent | `controls/color-black-white.png` |
| case-149 | V2 | NO-MATCH | FALSE_POSITIVE_NO_MATCH | absent | `videos/video-009.mov` |
| case-002 | V3 | SPORTS | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-004.mp4` |
| case-004 | V3 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | 3 | `controls/color-green-yellow.png` |
| case-005 | V3 | OBJECT | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-006.mov` |
| case-006 | V3 | OBJECT | CORRECT_IN_TOP5_BAD_RERANK | 4 | `controls/color-green-yellow.png` |
| case-007 | V3 | SCENE | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-006.mov` |
| case-008 | V3 | SCENE | CORRECT_IN_TOP5_BAD_RERANK | 2 | `photos/photo-005.jpg` |
| case-011 | V3 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | absent | `none` |
| case-014 | V3 | OBJECT | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-006.mov` |
| case-015 | V3 | COLOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-006.mov` |
| case-016 | V3 | COLOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 14 | `videos/video-006.mov` |
| case-019 | V3 | INDOOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-009.mov` |
| case-020 | V3 | INDOOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 12 | `videos/video-007.mov` |
| case-021 | V3 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-022 | V3 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-024 | V3 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-006.mov` |
| case-025 | V3 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 13 | `videos/video-008.mkv` |
| case-026 | V3 | NEGATIVE CONDITION | NEGATIVE_CONDITION_FAILURE | absent | `none` |
| case-027 | V3 | MULTI-CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 20 | `videos/video-007.mov` |
| case-028 | V3 | MULTI-CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 13 | `videos/video-006.mov` |
| case-029 | V3 | PEOPLE | CANDIDATE_MISS | absent | `none` |
| case-030 | V3 | PEOPLE | CORRECT_IN_TOP5_BAD_RERANK | 2 | `photos/photo-006.jpg` |
| case-032 | V3 | PEOPLE | CANDIDATE_MISS | absent | `none` |
| case-033 | V3 | PEOPLE | PHOTO_VIDEO_SCORE_INTERFERENCE | 49 | `videos/video-006.mov` |
| case-034 | V3 | PEOPLE | PHOTO_VIDEO_SCORE_INTERFERENCE | 14 | `videos/video-006.mov` |
| case-035 | V3 | COLOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-036 | V3 | COLOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 16 | `videos/video-009.mov` |
| case-038 | V3 | COLOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 14 | `videos/video-006.mov` |
| case-040 | V3 | PEOPLE | CANDIDATE_MISS | absent | `none` |
| case-044 | V3 | CLOTHING | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-006.mov` |
| case-045 | V3 | COLOR | COLOR_MISS | absent | `none` |
| case-047 | V3 | FOOD | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-010.mov` |
| case-049 | V3 | FOOD | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-010.mov` |
| case-050 | V3 | FOOD | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-051 | V3 | ANIMAL | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-001.mp4` |
| case-052 | V3 | ANIMAL | PHOTO_VIDEO_SCORE_INTERFERENCE | 12 | `videos/video-006.mov` |
| case-053 | V3 | ANIMAL | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-055 | V3 | CITY | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-008.mkv` |
| case-059 | V3 | COLOR | PHOTO_VIDEO_SCORE_INTERFERENCE | 15 | `videos/video-006.mov` |
| case-061 | V3 | MULTI-CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 14 | `videos/video-004.mp4` |
| case-062 | V3 | MULTI-CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 14 | `videos/video-004.mp4` |
| case-063 | V3 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 16 | `videos/video-010.mov` |
| case-064 | V3 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-004.mp4` |
| case-065 | V3 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | absent | `none` |
| case-066 | V3 | MULTI-CONDITION | MULTI_CONDITION_COVERAGE_FAILURE | 43 | `photos/photo-020.jpg` |
| case-068 | V3 | ACTIVITY | PHOTO_VIDEO_SCORE_INTERFERENCE | 6 | `videos/video-008.mkv` |
| case-069 | V3 | NEGATIVE CONDITION | PHOTO_VIDEO_SCORE_INTERFERENCE | 11 | `videos/video-006.mov` |
| case-074 | V3 | OCR | OCR_MISS | absent | `none` |
| case-079 | V3 | OCR | OCR_MISS | absent | `none` |
| case-085 | V3 | NEGATIVE CONDITION | NEGATIVE_CONDITION_FAILURE | absent | `none` |
| case-088 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 2 | `videos/video-004.mp4` |
| case-089 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 5 | `videos/video-006.mov` |
| case-091 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-093 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 4 | `videos/video-006.mov` |
| case-094 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-097 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-098 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 2 | `videos/video-003.mp4` |
| case-099 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-100 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 2 | `photos/photo-037.jpg` |
| case-101 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-102 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 3 | `videos/video-007.mov` |
| case-103 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-104 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 2 | `photos/photo-036.jpg` |
| case-105 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-106 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-107 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 2 | `videos/video-006.mov` |
| case-110 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | 6 | `videos/video-006.mov` |
| case-112 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-113 | V3 | VIDEO RETRIEVAL | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-117 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 6 | `videos/video-006.mov` |
| case-118 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 2 | `videos/video-005.mp4` |
| case-120 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 2 | `videos/video-006.mov` |
| case-122 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | absent | `none` |
| case-126 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 2 | `videos/video-006.mov` |
| case-130 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 3 | `videos/video-006.mov` |
| case-132 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 4 | `videos/video-006.mov` |
| case-133 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 3 | `videos/video-006.mov` |
| case-134 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 9 | `videos/video-005.mp4` |
| case-135 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 5 | `videos/video-008.mkv` |
| case-136 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 6 | `videos/video-006.mov` |
| case-137 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 6 | `videos/video-006.mov` |
| case-138 | V3 | EXACT VIDEO MOMENT | VIDEO_SELECTION_FAILURE | 2 | `videos/video-006.mov` |
| case-145 | V3 | NO-MATCH | FALSE_POSITIVE_NO_MATCH | absent | `controls/ocr-payment.png` |
| case-146 | V3 | NO-MATCH | FALSE_POSITIVE_NO_MATCH | absent | `controls/color-black-white.png` |
| case-147 | V3 | NO-MATCH | FALSE_POSITIVE_NO_MATCH | absent | `videos/video-001.mp4` |
| case-149 | V3 | NO-MATCH | FALSE_POSITIVE_NO_MATCH | absent | `videos/video-009.mov` |

V3 public status: **EXPERIMENTAL / LOCKED**.
