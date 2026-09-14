# Per-build release checklist

Record build/version/commit/channel, internal notes, tester notes, update policy and an evidence reference for every check in the Beta console. Do not reuse a prior build's results as validation of changed native code.

- [ ] Code committed; exact source commit recorded.
- [ ] Server, beta, web and React Native tests pass.
- [ ] Android Actions succeeds for that commit.
- [ ] APK artifact exists; package/signing match intended distribution.
- [ ] Version/build bumped and recorded.
- [ ] Internal/tester release notes reviewed.
- [ ] Global/cohort/account flags reviewed, with no accidental entitlement bypass.
- [ ] Required secrets configured in deployment/CI without printing their values.
- [ ] Production account mode exposes no development auth/purchase endpoints.
- [ ] V3 remains locked.
- [ ] Google auth and returning session verified on a physical device.
- [ ] Memory Pass catalog, restore and balances verified.
- [ ] Usage and 15-credit free allowance verified unchanged.
- [ ] Real photo search demo works on the candidate build.
- [ ] Trusted completion, debit, failure/cancel and replay reconciliation verified.
- [ ] Save As Copy verified; original hash/content preserved.
- [ ] Privacy checks and feedback consent reviewed.
- [ ] Private console, audit and service health verified.

NO-GO: missing core evidence, auth/ledger/search/startup/privacy failure, or open S1 bug/issue. CONDITIONAL: core evidence passes but non-core checklist items remain. GO: all checks pass and no critical blocker remains. Only operator-reviewed evidence changes the gate.

For LOW_END_DEVICE: cold/warm startup, indexing, V1/V2 latency, editor response, save, memory pressure and crash behavior. Record device family and version without unique IDs. Unknown coverage is not a pass.

For VIDEO_TESTERS: index time, explicit correctness, timestamp correctness, playback, edit entry and performance. Use consented beta reports only. Do not change benchmark holdout or ranking.
