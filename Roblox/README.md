# Roblox Cloud Manager

A secure browser control plane for a normal Roblox Player running on a persistent Windows cloud VM. Codespaces hosts development and control-plane services only; it never runs Roblox.

## Quick start

1. Copy `.env.example` to `.env`, replace every secret, configure one or more numeric Place IDs, and set the public HTTPS and Guacamole URLs.
2. Run `corepack enable && corepack prepare pnpm@10.15.1 --activate && pnpm install`.
3. Generate the administrator hash with `pnpm --filter @roblox-cloud/api hash-password` and put it in `.env`.
4. Run `pnpm check`, then `pnpm dev` for local dashboard/API development.
5. Follow [deployment](docs/deployment.md) for the Linux control plane, Guacamole, private networking, and Windows agent.

The dashboard is at `http://localhost:5173`; the API is at `http://127.0.0.1:4000`. Production requires HTTPS.

## Commands

- `pnpm dev` — API and dashboard only, with no duplicate servers.
- `pnpm check` — lint, strict types, tests, and production builds.
- `pnpm --filter @roblox-cloud/windows-agent build` — compile the Windows package.

Automated input intended to defeat Roblox inactivity enforcement is deliberately not included. Input is provided through the normal browser remote-desktop session.
