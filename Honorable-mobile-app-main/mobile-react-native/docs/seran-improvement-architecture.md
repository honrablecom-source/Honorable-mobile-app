# Seran Improvement Program architecture

Status: `BACKEND_NOT_CONFIGURED`. Actual media upload is disabled.

Normal indexing and search remain entirely local. The optional contribution path is separate and may run only after a versioned, explicit consent record is active. Revocation immediately stops future enqueueing and cancels queued work where practical; it never removes the local search index.

## Isolated contribution path

`opt in → local eligibility check → sensitive-data filter → contribution queue → authenticated upload authorization → private object storage → server validation → dataset record → annotation/quality control → versioned training pipeline`

The conservative initial policy excludes sensitive documents, identity documents, banking and medical information, intimate imagery, credentials, sensitive content involving minors, private documents, highly sensitive OCR, hidden/private albums, and deleted media. Normal search never waits for this path.

## Server contract

Binary media belongs in private object storage, never relational columns or public URLs. A `TrainingContribution` record contains a contribution ID, pseudonymous contributor ID, media type, storage object reference, timestamps, consent/policy/model/analysis versions, processing status, dataset split, and annotation provenance. Optional minimized metadata may include dimensions, duration, quality measurements, labels, captions, permitted OCR, embeddings, and representative timestamps.

Do not attach names, email, account profile, original filenames, exact GPS, contacts, unrelated EXIF, or privileged credentials. The app must never receive database administration or storage master secrets.

Uploads require TLS, authenticated requests, short-lived authorization, server-side type/size validation, rate limiting, and malware/file validation. A deletion API separately tracks raw-object deletion, dataset-record deletion, future-training exclusion, and completed-training limitations.

## Dataset and model lifecycle

Ingestion performs exact/near duplicate and contributor-skew detection, quality filtering, and annotation-provenance tracking. Dataset versions deterministically isolate TRAIN, VALIDATION, and protected TEST splits. Collection, training, evaluation, registry publication, release approval, deployment, and rollback are separate stages. Raw uploads never update production weights live.

Seran can also use appropriately licensed, synthetic, internally created, and suitable public datasets; private user contribution is not required.

Before activation, provision and security-review the ingestion API, private object storage, contribution database, deletion workflow, retention rules, consent service, monitoring, model registry, and legal policy.
