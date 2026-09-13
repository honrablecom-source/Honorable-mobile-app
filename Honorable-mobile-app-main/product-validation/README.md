# Product integration validation

Scope: 11 actual images and 2 actual videos copied from the existing local test media, indexed with the shared Kotlin adapter. This is not the Seran benchmark. No holdout access or ranking changes. `manifest.json` records queries, expected assets and original hashes before running search. Media remain local and are excluded from Git; source locations are recorded in the manifest.

The two sample videos show similar park scenes and have no independently annotated action moment. Video moment Demo B must remain NOT READY unless a distinct timestamp is verified. A matching video alone is not moment validation.

UI fixture screenshots in `ui-preview-gallery/` are separate from real-engine evidence. Production Android currently fails closed when the account server lacks a trusted native completion provider; the local gateway verifier does not solve device attestation.
