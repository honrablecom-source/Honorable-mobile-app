# Studio virtual file-tree validation — November 11

0 newly added user-facing features; 1 correctness fix; 0 scaffolding.

The Studio project model accepted file destinations such as `scripts/main.py/child`, treating an existing script as a folder. It also allowed a script or transferred folder to occupy `scripts` despite existing `scripts/main.py` descendants. These operations now refuse before modifying the cloned model. Implicit ancestor folders remain supported for normal nested script paths.

File presence uses own properties, allowing ordinary filenames such as `toString` and `valueOf` without treating inherited object members as files. Existing unsafe-path rules remain unchanged.

Validation: `node Honorable-mobile-app-main/product/studio/project.test.js` passes all 6 tests (3 retained, 3 new). Running this same test file against `git show HEAD:Honorable-mobile-app-main/product/studio/project.js` in an isolated temporary directory produces 2 expected regression failures; valid nested transfers pass both versions. Tests cover rejected create/folder/move/duplicate destinations, input preservation, script contents, serialization and original-media URI preservation. Scoped `git diff --check` passes.

No full app/native/APK build, mobile UI integration or physical-device validation was performed. Source workflow count is unchanged; this model fix is not a new feature. No dependencies, personal-media uploads, search/ranking or credit/ledger changes.
