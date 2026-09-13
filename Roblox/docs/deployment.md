# Deployment

## 1. Control plane

Provision a small Linux host with Docker Engine/Compose, public TCP 80/443, and private reachability to the Windows VM. Clone this repository, create `.env`, set `DASHBOARD_DOMAIN`, `DESKTOP_DOMAIN`, and a random `GUAC_DB_PASSWORD`, then run `docker compose -f deploy/docker-compose.yml build` and `docker compose -f deploy/docker-compose.yml up -d`. The pinned Guacamole image generates its official PostgreSQL schema before the database first starts.

Create DNS records for both domains. Confirm valid TLS before entering credentials. Configure Guacamole with an RDP connection targeting the Windows VM's private address, NLA security, server-certificate verification, and a dedicated non-administrator Windows user. Do not add a public 3389 firewall rule.

## 2. Windows VM

Use Windows 11 where provider licensing permits; Windows 10/11 client gives the most representative Roblox compatibility. Windows Server may work but is not the primary recommendation and must be tested with the current Roblox client and GPU driver. Install current vendor GPU drivers, Windows updates, Node.js 22 LTS from its official installer, and Roblox through Roblox's standard website/Microsoft Store flow while signed in as the dedicated user.

Build on the development machine with `pnpm --filter @roblox-cloud/windows-agent... build`, copy the deployable workspace package to Windows, then run elevated:

```powershell
.\scripts\install-agent.ps1 -PackagePath C:\staging\windows-agent
.\scripts\create-startup-task.ps1 -RunAsUser 'COMPUTER\RobloxUser'
```

Protect the agent environment file with NTFS ACLs. Sign out and back in or start `RobloxCloudAgent` from Task Scheduler. The at-logon trigger guarantees an interactive desktop; configure the Windows user for provider-supported automatic console login only after weighing the credential-at-rest risk. A normal RDP disconnect leaves applications running; signing out terminates them.

Boot recovery order is: Windows networking, interactive user session, scheduled agent start, dynamic Roblox discovery, optional reopening of the locally persisted last allowed game, and authenticated API registration. Set `REOPEN_LAST_GAME=false` when startup should wait for an explicit PLAY command.

## 3. Cloud lifecycle

Use a provider scheduler/API or console to start the Windows VM on demand and stop it after a deliberate idle timeout. Do not stop it merely because the browser disconnects—the primary feature depends on the VM continuing. Preserve the OS disk across stops. Reserve a static private address. Place Guacamole and Windows in the same region/VPC or connect them with WireGuard/Tailscale and still restrict RDP to the gateway.

## 4. Verification

Run `pnpm check`, `scripts/health-check.ps1`, and verify login failure throttling, CSRF rejection, agent auth failure, PLAY/STOP/RESTART, browser reconnect, RDP keyboard/mouse/touch, VM stop/start, Roblox crash recovery, restart-limit behavior, disk persistence, and that an external port scan cannot reach 3389. Test phone controls before relying on them; Guacamole touch gestures are practical for navigation but do not match a physical keyboard/mouse for all Roblox experiences.
