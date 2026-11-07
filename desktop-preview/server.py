"""Loopback-only desktop design preview. No phone data or AI services are used."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
import argparse
import hashlib
import json

ROOT = Path(__file__).resolve().parent
ATLAS = ROOT.parent / "app/src/main/assets/atlas/china-cities.json"
FILES = {"/": (ROOT / "index.html", "text/html; charset=utf-8"),
         "/index.html": (ROOT / "index.html", "text/html; charset=utf-8"),
         "/preview.css": (ROOT / "preview.css", "text/css; charset=utf-8"),
         "/journal.css": (ROOT / "journal.css", "text/css; charset=utf-8"),
         "/preview.js": (ROOT / "preview.js", "application/javascript; charset=utf-8"),
         "/journal.js": (ROOT / "journal.js", "application/javascript; charset=utf-8"),
         "/atlas.json": (ATLAS, "application/json; charset=utf-8")}


class PreviewHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/health":
            stamps = ";".join(str(p.stat().st_mtime_ns) for p in [ROOT / "index.html", ROOT / "preview.css", ROOT / "journal.css", ROOT / "preview.js", ROOT / "journal.js", ATLAS])
            body = json.dumps({"app": "travel-journal-desktop-preview", "version": 1,
                               "revision": hashlib.sha256(stamps.encode()).hexdigest()[:16]}).encode()
            mime = "application/json; charset=utf-8"
        elif path in FILES:
            file, mime = FILES[path]
            body = file.read_bytes()
        elif path == "/favicon.ico":
            self.send_response(204)
            self.end_headers()
            return
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'")
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8766)
    args = parser.parse_args()
    print(f"Travel Journal preview: http://127.0.0.1:{args.port}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", args.port), PreviewHandler).serve_forever()
