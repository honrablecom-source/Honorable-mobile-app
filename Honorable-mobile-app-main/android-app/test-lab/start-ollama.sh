#!/usr/bin/env bash
set -euo pipefail
if ! command -v ollama >/dev/null; then
  echo "Ollama is not installed. Install it from https://ollama.com/download/linux" >&2
  exit 1
fi
if ! curl -fsS --max-time 2 http://127.0.0.1:11434/api/version >/dev/null; then
  nohup env OLLAMA_HOST=127.0.0.1:11434 OLLAMA_NO_CLOUD=true OLLAMA_KEEP_ALIVE=2m ollama serve > /tmp/honorable-ollama.log 2>&1 &
  for _ in {1..20};do curl -fsS --max-time 2 http://127.0.0.1:11434/api/version >/dev/null&&break;sleep .5;done
fi
curl -fsS --max-time 5 http://127.0.0.1:11434/api/version >/dev/null || { echo "Ollama did not become ready on localhost" >&2; exit 1; }
ollama show moondream:1.8b >/dev/null 2>&1 || ollama pull moondream:1.8b
echo "Ollama localhost ready: moondream:1.8b"
