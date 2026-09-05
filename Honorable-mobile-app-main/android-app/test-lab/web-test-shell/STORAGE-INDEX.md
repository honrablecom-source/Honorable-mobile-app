# Web test storage index

`storage/` is the development browser shell's simulated Android media storage.
It does not alter Android MediaStore, Room, authentication, or production code.

1. Put JPG, JPEG, PNG, WebP, MP4, MOV, M4V, WebM, or MKV files anywhere under
   `web-test-shell/storage/`.
2. From `Honorable-mobile-app-main`, run `./linux-demo.sh index`.
3. Start or restart the shell with `./linux-demo.sh start`.

The shared Kotlin test adapter recursively discovers the files, writes
`storage/.memories-test-index`, and serves the same indexed records to Gallery,
Files, and Honorable search. Search ranking remains in the shared Android search
core; the browser does not contain a separate JavaScript ranking engine.
