---
project: Honorable
type: team-coordination
updated: 2026-10-21
---
# Live team work

Current milestone: offline guest editing and local-access/permission fixes; see [[LOCAL_EDITING_READINESS_WORKLOG]]. [[CAPTIONS_WORKLOG]] holds implementation evidence; [[WORK_QUEUE]] holds acceptance status. Engineering checks and APK verification are complete; physical acceptance is still pending. See [[HONORABLE_CAPTIONS_CHECKPOINT]]. Manual captions and SRT are not automatic speech transcription.

## Assignments and ownership

| Agent | Assignment | Write boundary |
| --- | --- | --- |
| Director (`root`) | Integrate caption controls, timing, preview/export, history and recovery; reconcile findings and final evidence. | App source and milestone notes, including [[SESSION_HANDOFF]], [[WORK_QUEUE]], [[CAPTIONS_WORKLOG]] and [[DECISIONS]]. |
| `caption_review` | Implement native guest policy, isolated recovery and subtitle permission correctness; report review findings. | Native editor/bridge source and relevant tests, coordinated with director. |
| `android_build` | Own native/Gradle validation and the internal APK; report actual test results, source identity and artifact verification. | Exclusive Gradle/native build execution and build outputs; coordinate any source changes with the director. |
| `obsidian_notes` | Implement RN guest entry and denied/empty-library access with regression tests and work log. | RN source/tests and READINESS_WORKLOG. |

Keep Gradle work with `android_build` to avoid competing builds. Agents report findings and completion directly to the director; unresolved review findings return to the source owner before packaging is accepted.

## Evidence boundaries

- **Source:** an implemented change or passing test proves only the paths actually inspected or executed. Record the tested source snapshot and test scope.
- **Build/artifact:** a successful compile and a verified APK are separate results. Record the full build result, artifact path, SHA-256 and matching source inputs. Public debug signing is for internal testing.
- **Device:** compiled instrumentation is not executed instrumentation. No Android phone is available; rendered media, playback/export, lifecycle and physical acceptance remain unverified. Use [[HONORABLE_ANDROID_DEVICE_CHECKS]] for that later work. Store readiness requires its own evidence.

## Next handoff

1. Reviewer sends reproducible findings; director resolves them and tells the build owner when source is ready for final checks.
2. Build owner reports pass/fail counts, compilation scope and verified APK evidence; any later source change must be reconciled with that evidence.
3. Director updates [[CAPTIONS_WORKLOG]], [[WORK_QUEUE]] and [[SESSION_HANDOFF]] with verified results, remaining device gates and the next bounded task. Follow [[DECISIONS]] for scope and persistent constraints.
4. Resume through [[START_HERE]] and the compact handoff, then open the linked work log as needed.

The active vault is this repository's `docs` directory, opened in installed Obsidian with core plugins. These notes are coordination scaffolding: **0 new user-facing app features**, and no measured token-savings claim.

## Recorded outcome

The reviewer found a repeated-reference recovery overflow; the director fixed it and the reviewer verified the byte-budget math. The documentation agent validated all initial wiki links. The build agent completed fresh native checks, integration compilation and full APK verification. Final source publication and compact handoff belong to the director. Scope: two built caption/SRT features, zero new AI engines; device acceptance remains open.

## October 23 outcome

Native and RN owners implemented explicit guest access with preserved account/AI boundaries. Independent integration review completed; the director took over after the build agent reached its usage limit. Fresh JVM/RN checks and the full APK were verified against frozen inputs. One new guest workflow and two fixes; no AI engine or physical-device acceptance. Final evidence: [[HONORABLE_LOCAL_EDITING_READINESS_CHECKPOINT]].
