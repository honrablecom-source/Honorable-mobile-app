# Noble

Privacy-first photo/video search, editing and local creation. This repository preserves the original app and its native on-device search architecture.

## Start here

- [Current project home](docs/START_HERE.md)
- [Simple product update](docs/releases/Honorable-Creative-Guide.docx)
- [Latest Android build evidence and limitations](docs/HOME_STUDIOS_READINESS.md)
- [Working versus planned features](docs/HONORABLE_FEATURE_STATUS.md)
- [Engineering handoff](docs/SESSION_HANDOFF.md)

- [Android executable and iOS build status](executable/README.md)
- [Current owner task](docs/OWNER_TASK_2026-10-24.md)

## Repository map

| Folder | Purpose |
| --- | --- |
| `Honorable-mobile-app-main/` | Application source, native projects, web tools and build scripts |
| `.github/workflows/` | Android and iOS CI workflows |
| `docs/` | Current engineering notes and Obsidian project vault |
| `docs/releases/` | Current product report and release evidence |
| `docs/archive/` | Superseded reports and historical design references |

The React Native app is in `Honorable-mobile-app-main/mobile-react-native/`. Follow its README and the engineering handoff for setup. Older UI previews are design references, not proof of working native features.

## Readiness

The current Android candidate includes offline guest photo/video editing, local video projects, precise trim, drawing, editable drawing drafts, a balanced montage builder and script-to-captions. It is an internal build using the public debug certificate. Physical-device acceptance remains open. Native iOS editors, hosted accounts and AI image/video generation remain unfinished. See the linked evidence before making release claims.
