# Honorable project guidance

- For compact project resumption, read docs/SESSION_HANDOFF.md and docs/WORK_QUEUE.md. Use the linked current work log during implementation and update these notes with verified results at each milestone.
- Output efficiency: RTK 1.0.1 is installed at C:/Users/Shadow/Desktop/honrabl/.tools/rtk-oss-v1.0.1/rtk.exe and added to user PATH. Use the absolute executable in existing agent sessions if PATH is stale. Prefer scoped rg searches, `rtk log <file>` for build-log summaries and `rtk read --level minimal <file>` for source previews; default `rtk read` does not filter. Retain full logs and inspect complete relevant errors/context before changing code. RTK output savings are not total conversation/credit savings.

- This Git repository is the authoritative source. The app lives under Honorable-mobile-app-main/; GitHub workflows belong in the outer .github/workflows/. Ignore adjacent extracted ZIP copies.
- Honorable is a privacy-first AI photo/video search and editing app. Preserve the native on-device search engine. The owner's latest UI direction (October 19) supersedes earlier dark references: white-majority mobile UI with black typography, outlines and controls; neutral grays only, original media colors retained. Blend calm conversational input with compact creative-workspace organization; no desktop sidebar as the primary phone layout. Do not upload personal media or queries without explicit owner authorization.
- Read docs/HONORABLE_CURRENT_STATUS.md before continuing. Historical handoffs describe unverified work; confirm it in source.
- Never inspect or tune on Seran benchmark holdout. Do not run the expensive benchmark during build validation. Preserve search ranking unless the task requires it.
- Preserve V3 locked/unchargeable, V1/V2 credit costs 1/3, 15 free monthly credits, free-first deductions, no free rollover, and nonexpiring purchases. No charge for failed/cancelled searches. Keep the existing catalog and server ledger authority.
- Never print or commit credentials, tokens, environment values or private media. Store releases need private signing; internal APKs deliberately use the public debug key and are not store-ready.
- Preserve uncommitted work. Do not reset or merge different project copies blindly.
- Validation: Node tests in dev-server/test/*.test.js, product/studio/project.test.js, scripts/android-sdk-config.test.cjs and scripts/release-manifest.test.cjs. Run npm ci, typecheck, lint and Jest in mobile-react-native/ when dependencies are available. Android CI builds the full React Native app and checks the standalone Compose compilation.
- Full build success, artifact presence, real-device behavior and store readiness are separate claims. Record actual evidence; never promote readiness based on previews or an isolated compile.
- In every completion update, give the number of newly added user-facing features. Count fixes, scaffolding and unfinished work separately; state whether a feature is built, device-tested or still experimental.

- Efficiency tools installed October 23: Caveman skill at C:/Users/Shadow/.codex/skills/caveman (concise output; keep facts and full code intact), Repomix1.18.1 via .tools/bin/repomix.cmd with the root allowlist config, Headroom0.41.0 via .tools/headroom-local/headroom-local.ps1 (offline log/JSON compression; originals retained). Use them only where useful; no proxy interception or guaranteed credit-savings claim. No computer-use tools per owner instruction.

- Owner instruction October 24: work solo. Do not use subagents without new explicit user authorization. No computer-use tools. Preserve this preference across turns.
