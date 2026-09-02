# Seran P0 #2 confidence calibration

Scope: 105 development cases only. Holdout touched: NO. Candidate ranking changed: NO.

## V2

No-match accuracy: 77.8% → 100.0%; false-positive rate: 22.2% → 0.0%. Newly rejected previously accepted correct Top1 results: 0.

| Query | Top1 | Score | Margin | Agreement | Before | After | Reason |
|---|---|---:|---:|---:|---|---|---|
| the photo of a birthday cake with candles | `photos/photo-019.jpg` | 4.644409183853821 | 0.010 | 0.75 | NO_MATCH | NO_MATCH | LOW_ABSOLUTE_EVIDENCE |
| the screenshot saying package delivered | `none` | n/a | 0.000 | 0.00 | NO_MATCH | NO_MATCH | LOW_ABSOLUTE_EVIDENCE |
| the picture of a cat sleeping on a sofa | `none` | n/a | 0.000 | 0.00 | NO_MATCH | NO_MATCH | LOW_ABSOLUTE_EVIDENCE |
| the video of people swimming in a pool | `none` | n/a | 0.000 | 0.00 | NO_MATCH | NO_MATCH | LOW_ABSOLUTE_EVIDENCE |
| the photo of a blue motorcycle in snow | `photos/photo-036.jpg` | 4.895923507081559 | 0.000 | 0.80 | NO_MATCH | NO_MATCH | INSUFFICIENT_SIGNAL_AGREEMENT |
| the receipt with a total of 99.99 | `controls/ocr-payment.png` | 1.0 | 0.008 | 0.50 | ACCEPT | NO_MATCH | INSUFFICIENT_SIGNAL_AGREEMENT |
| the beach photo with a red umbrella | `controls/color-black-white.png` | 4.986096582925932 | 0.036 | 1.00 | ACCEPT | NO_MATCH | INSUFFICIENT_SIGNAL_AGREEMENT |
| the video where a dog catches a frisbee | `videos/video-001.mp4` | 8.930279425724795 | 0.001 | 1.00 | NO_MATCH | NO_MATCH | LOW_MARGIN |
| the document mentioning passport number ZX 999 | `photos/photo-007.jpg` | 1.0 | 0.000 | 0.75 | NO_MATCH | NO_MATCH | LOW_MARGIN |

## V3

No-match accuracy: 66.7% → 88.9%; false-positive rate: 33.3% → 11.1%. Newly rejected previously accepted correct Top1 results: 0.

| Query | Top1 | Score | Margin | Agreement | Before | After | Reason |
|---|---|---:|---:|---:|---|---|---|
| the photo of a birthday cake with candles | `photos/photo-019.jpg` | 4.644409183853821 | 0.010 | 0.75 | NO_MATCH | NO_MATCH | LOW_ABSOLUTE_EVIDENCE |
| the screenshot saying package delivered | `none` | n/a | 0.000 | 0.00 | NO_MATCH | NO_MATCH | LOW_ABSOLUTE_EVIDENCE |
| the picture of a cat sleeping on a sofa | `none` | n/a | 0.000 | 0.00 | NO_MATCH | NO_MATCH | LOW_ABSOLUTE_EVIDENCE |
| the video of people swimming in a pool | `none` | n/a | 0.000 | 0.00 | NO_MATCH | NO_MATCH | LOW_ABSOLUTE_EVIDENCE |
| the photo of a blue motorcycle in snow | `photos/photo-036.jpg` | 4.895923507081559 | 0.000 | 0.80 | NO_MATCH | NO_MATCH | INSUFFICIENT_SIGNAL_AGREEMENT |
| the receipt with a total of 99.99 | `controls/ocr-payment.png` | 1.0 | 0.028 | 0.50 | ACCEPT | NO_MATCH | INSUFFICIENT_SIGNAL_AGREEMENT |
| the beach photo with a red umbrella | `controls/color-black-white.png` | 4.986096582925932 | 0.036 | 1.00 | ACCEPT | NO_MATCH | INSUFFICIENT_SIGNAL_AGREEMENT |
| the video where a dog catches a frisbee | `videos/video-001.mp4` | 10.495668863197517 | 0.027 | 1.00 | ACCEPT | ACCEPT | CALIBRATED_EVIDENCE_ACCEPTED |
| the document mentioning passport number ZX 999 | `photos/photo-007.jpg` | 1.0 | 0.000 | 0.75 | NO_MATCH | NO_MATCH | LOW_MARGIN |
