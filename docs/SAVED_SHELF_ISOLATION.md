# Independent saved-work shelves — November 5

0 new features, 1 core correctness fix, 0 scaffolding. Source count remains 82
workflows, 44 beyond the batch baseline of 38.

Opening Projects previously read saved designs and reusable parts inside one
try/catch. A damaged saved-design shelf prevented the healthy reusable-parts shelf
from loading. A failed read could also leave previously displayed actions stale.

Each shelf now loads independently. A failed shelf read clears only its displayed
list and names the unavailable shelf in the status message. Stored shelf text is
preserved, including malformed data. Healthy saved designs can still Open and
healthy reusable parts can still Insert. Existing save/delete validation is
unchanged; this does not repair or automatically replace damaged saved data.

The browser regression uses the production shelf serializer, real Projects task
switcher and actual canvas Open/Insert actions. Its before run reproduces the
missing healthy Insert button when saved designs are malformed. After checks cover
each failure direction, original raw-text preservation, stale actions disappearing
when both shelves become unreadable, and restored shelf availability on reopen.
Only synthetic local artwork is used. Evidence lives in .context/shelf-isolation.

The drawing Node suite has 167 passing tests. The isolated web bundle compiles
(with the existing missing React Native TypeScript base-config warning). Source
and 266 generated files match. No dependency, schema, native bridge or account,
search, billing or media-upload behavior changed. No packaged Android asset refresh,
full native/APK build or Android device acceptance was performed. Browser recovery
does not prove real WebView storage/lifecycle behavior or store readiness.
