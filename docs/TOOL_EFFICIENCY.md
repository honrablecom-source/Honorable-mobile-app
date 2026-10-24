# Configured efficiency tools

Updated October23,2026. All run locally; no computer-use tools or provider proxy interception.

- Caveman: installed user skill at C:/Users/Shadow/.codex/skills/caveman. Available on the next turn; concise output guidance used now. No login-discovery/proxy CLI installed.
- Headroom0.41.0: isolated core-only venv. Use C:/Users/Shadow/Desktop/honrabl/.tools/headroom-local/headroom-local.ps1 -InputFile <absolute-log-or-json> -Kind log|json -OutputDir <directory>. Offline, telemetry/beacon off, originals retained; JSON roundtrip guarded.
- Repomix1.18.1: repomix.cmd in .tools/bin, added to user PATH. Run repomix from this repo; repomix.config.json allows only five overview source files and enables compression/security scanning. Generated .context output is ignored. No private media, holdouts or credentials packed.
- RTK1.0.1: installed/user PATH, scoped read/log usage. Keep complete logs for errors; keyword filters can misclassify skipped error-check tasks.

Measured samples: Repomix38,860 to15,320 bytes (60.58 percent); Headroom current build-log sample11,552 to2,291 bytes (80.168 percent); sample JSON24.016 percent. These are sample byte reductions, not measured total conversation or credit savings. Some JSON is passed through unchanged. Compression may omit details; read full relevant source before edits.

Sources: https://github.com/JuliusBrussee/caveman , https://github.com/headroomlabs-ai/headroom , https://repomix.com/guide/code-compress . Installation/sample receipts are retained in task outputs/headroom and Honorable-efficiency-tools.json.

## Recheck October 24

RTK 1.0.1 command filtering works. Caveman remains active for concise responses. Repomix 1.18.1 regenerated the compressed five-file allowlist and passed its security scan. Headroom 0.41.0 runs offline with telemetry disabled. Its local wrapper now falls back to lossless JSON minification when SmartCrusher cannot round-trip a document; the original is retained. A real 7930-byte validation JSON became 6526 bytes (17.705% smaller), with decoded data equality checked. Logs that lose critical diagnostic lines still fall back to the original. These tools run explicitly, not as an automatic provider proxy; these measurements do not establish conversation-credit savings.
