# Candidate 1.0.2 / build 3

## Tester notes

Feedback now supports a screenshot you explicitly choose and consent to send, plus optional safe diagnostic JSON. Nothing is captured automatically. Selected images are resized and re-encoded; review private content before sending. Attachments expire after 30 days and are available only to authorized support operators.

On Android 11+, the next authenticated beta launch can report an OS crash/ANR reason code for the previous observed beta session. No stack traces, exception messages, queries or private media are included. Optional analytics opt-out also stops this collection. Editor open/save/failure/undo/duration and bounded performance observations help locate beta regressions. Reported crash-free percentages cover observed supported sessions only.

Test attachment cancellation and consent, beta sign-in, photo search, Pass/Usage and Save As Copy. Editor entry and native saves respect server disable/update notices; network access is needed for these beta authorization checks. Advanced Studio limitations remain unchanged.

## Internal notes

Adds strict attachment ownership, size/type/consent checks; protected audited downloads; opaque attachment filenames; 30-day expiry; private diagnostic schema; replay deduplication. Adds a bounded native telemetry executor and encrypted local previous-session descriptor, with same-account checking before transmission. Uses Android ApplicationExitInfo reason enums; does not install an exception handler or external analytics SDK. Android <11 does not contribute a falsely crash-free denominator.

Version/build increased for new native inputs. A fresh Android build and physical-device validation are required. Production Google configuration and the trusted native search completion integration remain release blockers. No ranking, benchmark holdout, pass prices, allowance or model cost changed.
