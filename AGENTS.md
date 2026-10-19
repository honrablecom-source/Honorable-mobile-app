# Honorable project guidance

- This Git repository is the authoritative source. The app lives under Honorable-mobile-app-main/; GitHub workflows belong in the outer .github/workflows/. Ignore adjacent extracted ZIP copies.
- Honorable is a privacy-first AI photo/video search and editing app. Preserve the native on-device search engine. The owner's latest UI direction (October 19) supersedes earlier dark references: white-majority mobile UI with black typography, outlines and controls; neutral grays only, original media colors retained. Blend calm conversational input with compact creative-workspace organization; no desktop sidebar as the primary phone layout. Do not upload personal media or queries without explicit owner authorization.
- Read docs/HONORABLE_CURRENT_STATUS.md before continuing. Historical handoffs describe unverified work; confirm it in source.
- Never inspect or tune on Seran benchmark holdout. Do not run the expensive benchmark during build validation. Preserve search ranking unless the task requires it.
- Preserve V3 locked/unchargeable, V1/V2 credit costs 1/3, 15 free monthly credits, free-first deductions, no free rollover, and nonexpiring purchases. No charge for failed/cancelled searches. Keep the existing catalog and server ledger authority.
- Never print or commit credentials, tokens, environment values or private media. Store releases need private signing; internal APKs deliberately use the public debug key and are not store-ready.
- Preserve uncommitted work. Do not reset or merge different project copies blindly.
- Validation: Node tests in dev-server/test/*.test.js, product/studio/project.test.js, scripts/android-sdk-config.test.cjs and scripts/release-manifest.test.cjs. Run npm ci, typecheck, lint and Jest in mobile-react-native/ when dependencies are available. Android CI builds the full React Native app and checks the standalone Compose compilation.
- Full build success, artifact presence, real-device behavior and store readiness are separate claims. Record actual evidence; never promote readiness based on previews or an isolated compile.
