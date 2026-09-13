# Architecture

The browser reaches two HTTPS origins: the dashboard/API and Apache Guacamole. The API persists sessions, commands, audit records, status, and recent events in SQLite/WAL. The Windows agent makes authenticated outbound HTTPS requests; no inbound agent port or arbitrary command endpoint exists. Guacamole reaches Windows RDP only over a private network or VPN. Port 3389 must never have an internet firewall rule.

```text
Browser ─HTTPS─> Caddy ─> Dashboard / API
   └────HTTPS─> Caddy ─> Guacamole ─private RDP─> Windows VM
                                      Windows agent ─outbound HTTPS─> API
```

The command schema admits only `LAUNCH_GAME`, `STOP_ROBLOX`, `RESTART_ROBLOX`, and `GET_STATUS`. The API creates numeric-only `roblox://placeId=...` URIs. It cannot forward a URI, executable, or shell string supplied by a browser.

Agent status is sampled every 10 seconds in the current implementation and backs off to the configured idle interval after API failures. Browser updates are server-sent events with 25-second keepalives. Crash recovery uses 5, 10, 20, 40, then 60-second delays and a 15-minute capped restart window. A healthy five-minute run clears the crash budget. State is persisted centrally, allowing API, dashboard, and agent restarts without coupling to one browser.

## Runtime sizing

Start with Windows 11, 4 vCPU, 8 GiB RAM, a modest DirectX-capable virtual GPU, 80 GiB persistent SSD, and at least 15 Mbps low-latency network capacity. Roblox itself should have CPU/GPU priority; the agent uses short process/metrics samples and sleeps between cycles. A GPU is strongly recommended for reliable interactive rendering. Benchmark the actual experience before reducing to a shared/virtual GPU.

The API/dashboard/Guacamole host typically needs 2 vCPU, 4 GiB RAM, and 20 GiB disk for a single user. Codespaces is development-only; the provided container requests 2 vCPU, 4 GiB RAM, and 16 GiB storage.

## Extension boundaries

Commands, status, and storage are keyed by `agentId`; games are data, not branches in code. A production multi-machine extension can add agent ownership and per-user authorization without changing the Windows process manager. Notifications, screenshots, scheduling, session history, and cost telemetry belong behind event subscribers rather than inside the watchdog.
