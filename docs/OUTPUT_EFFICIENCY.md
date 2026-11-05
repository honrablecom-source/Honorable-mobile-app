# Local output efficiency — November 4, 2026

RTK OSS 1.0.1 is installed for this Linux x86-64 workspace in `.tools/rtk-oss-v1.0.1/`. The earlier Windows path does not apply here. Binary and bundled Apache-2.0 LICENSE were extracted from the official release. `.tools/` and `.context/` are ignored; the app has no new dependency.

Use `bash scripts/rtk.sh --version`, `bash scripts/rtk.sh git status`, `bash scripts/rtk.sh read --level minimal path` or `bash scripts/rtk.sh log .context/logs/example.log`. Scoped native `rg` remains useful. Save complete build/test logs first; read complete relevant errors before edits. RTK's generic log summary can omit Jest pass totals, so inspect the log tail for actual suite results and retain the command exit status.

No global hooks, proxy interception, account connection, or shell-profile changes were installed. Commands use RTK explicitly. Reduced output is not a guarantee of reduced total conversation cost.

Official source: https://github.com/rtk-ai/rtk/releases/tag/v1.0.1

Installation reproduction (Linux x86-64 only): download `rtk-oss-x86_64-unknown-linux-musl.tar.gz` from that release into temporary storage; inspect the archive, compare SHA-256 below, then extract into `.tools/rtk-oss-v1.0.1/`. This locally recorded digest identifies the downloaded bytes; it is not independent publisher signature verification.

- Archive SHA-256: `a44e8ffc43981100b4ddf1710399a1649a6c874591aa6d0c0727fe6cbd983c41`
- Extracted binary SHA-256: `38b915f2a52ce4b7ef7d6dc3966aaeccb9f754731b2933571bf85026bcba8677`
- Verified command: `rtk 1.0.1`.

## Linux Repomix companion verified November 4

Pinned MIT-licensed Repomix 1.18.1 installed under ignored `.tools/output-efficiency/` using `npm install --prefix .tools/output-efficiency --ignore-scripts --no-audit --no-fund --save-exact repomix@1.18.1`. The local package lock preserves npm integrity metadata. Registry metadata identifies `github.com/yamadashy/repomix`; no global hooks or app dependencies added.

Run `bash scripts/repomix.sh --version`, then `bash scripts/repomix.sh`. The wrapper uses the root allowlist config with security checks, excluding protected benchmark/holdout paths, environment files and generated/dependency directories. Verified five files packed to ignored `.context/honorable-overview.xml`, 4,031 estimated tokens at this checkpoint. Do not replace scoped source reading with repeated whole-repository packing.

RTK `log` classifies strings heuristically: a passing test titled with “errors” can be counted as an error. Original logs, actual exit status and test summaries remain authoritative. Neither output compression nor token estimates guarantee total conversation savings.

## Temporary-disk dependency layout — November 4 continuation

The checkout reached 261MB free. Generated Repomix tooling now lives at `/tmp/noble-output-efficiency`, with `.tools/output-efficiency` pointing there. Mobile dependencies live at `/tmp/noble-mobile-dependencies/node_modules`, with `mobile-react-native/node_modules` pointing there. No lockfile/package/source changes. Keep the destination directory named `node_modules`: a first arbitrary directory name broke transitive Node/TypeScript resolution and was corrected before acceptance. Retained diagnostic: `.context/native-integration/dependency-relocation-initial-path.log`.

This recovered about 569MB (workspace 830MB free at verification), while temporary storage retained about 36GB free. `/tmp` is ephemeral: after workspace recreation, verify symlink targets and reinstall from the existing lockfile if missing. Claim dependency scopes on the agent board before reinstalling or moving them.

## Android configured compilation storage

Official SDK37.0/BuildTools37.0.0/NDK27.1.12297006 are installed under `/tmp/noble-android-sdk`; pinned wrapper Gradle9.4.1 uses `/tmp/noble-build-gradle`; generated root/app/.gradle/.cxx outputs link into `/tmp/noble-android-build-output`. Use process-local SDK/JDK/Gradle variables as recorded in `.context/native-build/compile-verification.json` and work log. No APK was packaged. Exact root ignore rules cover symlink paths, since directory-only ignore patterns do not ignore symlinks. Temporary paths are ephemeral.

Standalone configured validation uses the same `/tmp/noble-android-sdk` (SDK35 plus AGP8.7 default Build Tools34) and `/tmp/noble-build-gradle` (pinned Gradle8.9 distribution). Generated standalone outputs link to `/tmp/noble-compose-build-output`. Process-local compiler strategy `-Pkotlin.compiler.execution.strategy=in-process` avoids the observed Kotlin daemon disconnect; no repository compiler settings changed. Both configured native compilation paths and335 selected native JVM tests pass in retained logs; no APK packaging/device claim.
