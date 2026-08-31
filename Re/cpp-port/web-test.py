#!/usr/bin/env python3
"""Local browser test launcher for display-less Codespaces environments."""

from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import argparse
import os


PORT_DIR = Path(__file__).resolve().parent
RE_DIR = PORT_DIR.parent
GAME_FILE = "ri game main 5.html"


class GameHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(RE_DIR), **kwargs)

    def do_GET(self):
        if self.path in ("", "/"):
            self.send_response(302)
            self.send_header("Location", "/ri%20game%20main%205.html?build=v6.130.0")
            self.end_headers()
            return
        super().do_GET()

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("X-Content-Type-Options", "nosniff")
        super().end_headers()

    def log_message(self, fmt, *args):
        print("[RI test port] " + fmt % args, flush=True)


def main():
    parser = argparse.ArgumentParser(description="Serve the Reverend Insanity browser test build")
    parser.add_argument("--port", type=int, default=int(os.environ.get("RI_TEST_PORT", "8080")))
    parser.add_argument("--bind", default="0.0.0.0")
    args = parser.parse_args()

    game_path = RE_DIR / GAME_FILE
    if not game_path.is_file():
        raise SystemExit(f"Game file is missing: {game_path}")

    server = ThreadingHTTPServer((args.bind, args.port), GameHandler)
    print("\nReverend Insanity test port is running.", flush=True)
    print(f"Local URL: http://127.0.0.1:{args.port}/", flush=True)
    print(f"Codespaces: open the forwarded port {args.port}, then choose Qing Mao Mountain.", flush=True)
    print("Press Ctrl+C to stop the server.\n", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nTest port stopped.", flush=True)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
