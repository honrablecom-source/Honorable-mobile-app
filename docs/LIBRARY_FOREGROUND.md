# Recheck library access when Noble returns — November 6

0 new features, 1 core permission-freshness fix, 0 scaffolding. This improves an
existing library flow and does not add a creative workflow to the source register.

The React Native library provider previously refreshed at startup and explicit
actions, without a foreground listener. Returning after external permission
changes could leave the prior permission state and source list on screen.
Background/inactive → active now invokes the existing guarded status/page reload.
Repeated active notifications and startup do not add extra reloads. The provider
removes its listener on unmount; retained callbacks cannot start new native reads.

Reload invalidates old page generations, so a page started before the return
cannot repopulate items after access is observed revoked. Newly granted access
uses the existing library page read. Foreground handling does not call the
permission prompt, indexing or search APIs.

An explicit permission dialog now has a separate single-use owner. A foreground
reload can advance library generations without discarding that dialog's final
refresh. Duplicate permission requests are blocked and unmount invalidates the
owner. The existing platform-specific permission list is unchanged.

Five new before tests reproduce the missing foreground behavior. The current
actual-provider suite has 17 passing cases, including seven new foreground and
permission-dialog cases. It uses controlled AppState events, deferred bridge
responses and synthetic media. Coverage includes grants/revocations, stale page
rejection, repeated active events, background silence, listener cleanup, dialog
completion across reload and late completion after unmount.

Full validation results and exact source/test hashes are recorded under
.context/library-foreground. Temporary dependencies were restored from the
unchanged package lock after environment loss. The local shared-board server was
also restored after its HTTP endpoint was confirmed absent.

Final checks on the current source all exit 0: typecheck; lint with 0 errors and
38 warnings; and 183 Jest tests across 18 suites, including the seven new cases.
The source register remains 83 workflows (45 beyond baseline 38) at this milestone.

These are React/provider tests with mocked native lifecycle and permission
responses. They do not execute Android Settings, real permission dialogs,
providers, editor transitions, process recreation or device rendering. Native
on-device search/indexing/ranking, server ledger and media-upload policy are not
changed. No full native/Gradle/APK build, packaged drawing asset refresh or Seran
holdout/expensive benchmark is part of this milestone.
