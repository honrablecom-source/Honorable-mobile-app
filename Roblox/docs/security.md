# Security model

Deploy the control plane and desktop gateway behind HTTPS. Caddy terminates TLS and adds HSTS. The dashboard uses an HttpOnly, Secure, SameSite=Strict session cookie, an independent CSRF token, Argon2id password verification, strict origin CORS, request-size limits, login throttling, global rate limiting, schema validation, and audit records. Agent requests use HMAC-SHA256 over the exact JSON body plus a timestamp; timestamps older than 60 seconds are rejected.

Generate unrelated 32-byte-or-longer random values for `SESSION_SECRET`, `AGENT_SHARED_SECRET`, and `GUAC_DB_PASSWORD`. Keep `.env` out of source control. On Windows, store agent configuration in a file readable only by Administrators, SYSTEM, and its dedicated user, or inject secrets through a cloud secret manager. Rotate an agent secret by overlapping deployments behind a versioned secret in a production extension.

Guacamole has separate authentication because it controls the full Windows desktop. On first login, immediately remove/default credentials, create a named least-privilege user, enable TOTP using Guacamole's official extension, and assign only the intended RDP connection. Use a non-administrator Windows account for Roblox. Enable Network Level Authentication. Restrict the VM firewall so TCP 3389 accepts traffic only from the private Guacamole host/security group; never publish 3389.

Recommended production additions are identity-aware proxy/OIDC in front of both origins, phishing-resistant MFA, a 30-minute Guacamole inactivity timeout, restricted source IPs where practical, encrypted disks/backups, automated OS patches, and centralized immutable audit export. Do not place account passwords in game configuration or logs.

The agent does not inject into Roblox, inspect memory, manipulate packets, bypass anti-cheat, or automate activity. Automatic clicking to avoid inactivity limits is intentionally prohibited. Crash restart and user-driven remote input operate at normal application/desktop boundaries.
