# Local search test media

> **HISTORICAL / CORPUS INCOMPLETE** — The original 69-case corpus is missing
> media payloads. Preserve its manifests and reports for history only. Do not
> compare its scores directly with `seran-benchmark-v2`.

Place your own photos and videos in this directory, then run the lab commands from
`android-app/` (see `test-lab/README.md`). Media and generated indexes are ignored
by Git; only this guide and the example evaluation file are tracked.

Supported image inputs depend on Java ImageIO (`jpg`, `jpeg`, `png`; WebP/HEIC only
when a compatible ImageIO provider is installed). Video frame analysis requires
local `ffmpeg` and `ffprobe`. Nothing is uploaded and the server exposes only files
under this directory.
