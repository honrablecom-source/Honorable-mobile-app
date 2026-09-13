# Troubleshooting

- `ROBLOX_NOT_INSTALLED`: sign into the intended interactive Windows account, install Roblox only through its supported installer, launch once, and restart the agent. Discovery checks current version directories dynamically.
- `DISCONNECTED`: status is more than two minutes old. Check the scheduled task, Windows networking, clock synchronization, `API_URL`, TLS trust, and the agent log in `data/windows-agent.log`.
- `AGENT_AUTH_INVALID`: ensure API and agent secrets match, system time is synchronized, and no proxy rewrites the JSON request body.
- Crash loop stops: five restarts inside 15 minutes exhaust the default budget. Diagnose Roblox/driver/network errors, then restart the agent or wait for the window to expire.
- Desktop unavailable: verify Guacamole is healthy and can route privately to the VM. Check NLA credentials and Windows firewall. Do not solve this by opening public 3389.
- Roblox launches outside the visible desktop: run the scheduled task as the same logged-on user with `Interactive` logon type, not as SYSTEM or a Windows service.
- Duplicate players: STOP before PLAY. The watchdog tracks the first normal Roblox player process and the fixed taskkill operation closes all duplicate player processes during STOP/RESTART.
- Roblox updated: restart the agent; executable discovery sorts valid versioned installations by modification time and does not hardcode a version directory.
- Backend restarted: the agent retries with exponential network backoff, and SQLite/WAL retains state and queued commands.
- VM was shut down: start it through the cloud console/provider automation and wait for Windows login plus the scheduled task. A browser closure alone should never shut down the VM.
