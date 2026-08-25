# Local SearXNG for Honorable

This development instance binds only to `127.0.0.1:8080` and enables SearXNG's JSON search format. It uses a deliberately small upstream-engine set and does not bypass CAPTCHA, rate-limit, robots, login, or platform controls.

The untracked `.env` contains the generated local secret. Start it with:

```bash
cd searxng
docker compose --env-file .env up -d
```

Stop it with `docker compose --env-file .env down`. The Marketing AI URL is `http://127.0.0.1:8080`. If an upstream engine returns CAPTCHA, 429, or another anti-abuse response, do not retry aggressively or bypass it.
