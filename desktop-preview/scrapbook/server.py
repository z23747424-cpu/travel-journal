"""Serve only this local prototype, its public assets and the bundled atlas."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, unquote
import argparse
import hashlib
import json
import mimetypes
from travel_ai import run_travel, TravelError

ROOT = Path(__file__).resolve().parent
ATLAS = ROOT.parent.parent / 'app/src/main/assets/atlas/china-cities.json'

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != '/api/travel':
            self.send_error(404); return
        expected = f'http://127.0.0.1:{self.server.server_port}'
        if self.headers.get('Origin') != expected or self.headers.get('Host') != expected.removeprefix('http://') or self.headers.get('Content-Type','').split(';')[0] != 'application/json':
            self.send_error(403); return
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length < 50000:
                raise TravelError('请求内容过大或为空。')
            result = run_travel(json.loads(self.rfile.read(length)))
            status = 200
        except TravelError as error:
            result = {'error': str(error)}; status = 400
        except Exception:
            result = {'error': '旅行服务暂时无法处理，请检查配置或稍后重试。'}; status = 502
        body = json.dumps(result, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        path = unquote(urlsplit(self.path).path)
        if path == '/health':
            files = [p for p in ROOT.rglob('*') if p.is_file() and p.suffix in {'.html','.css','.js','.png','.woff2'}]
            revision = hashlib.sha256(';'.join(f'{p.name}:{p.stat().st_mtime_ns}' for p in sorted(files)).encode()).hexdigest()[:16]
            body = json.dumps({'app':'lvye-province-scrapbook','version':1,'revision':revision}).encode()
            mime = 'application/json'
        elif path == '/atlas.json':
            body = ATLAS.read_bytes(); mime = 'application/json'
        elif path == '/favicon.ico':
            self.send_response(204); self.end_headers(); return
        else:
            file = (ROOT / ('index.html' if path == '/' else path.lstrip('/'))).resolve()
            if not file.is_relative_to(ROOT) or not file.is_file() or file.suffix not in {'.html','.css','.js','.png','.jpg','.webp','.svg','.woff2','.txt'}:
                self.send_error(404); return
            body = file.read_bytes(); mime = mimetypes.guess_type(file.name)[0] or 'application/octet-stream'
        self.send_response(200)
        self.send_header('Content-Type',mime)
        self.send_header('Content-Length',str(len(body)))
        self.send_header('Cache-Control','no-store')
        self.send_header('X-Content-Type-Options','nosniff')
        self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'")
        self.end_headers(); self.wfile.write(body)

if __name__ == '__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--port',type=int,default=8778)
    args=parser.parse_args()
    print(f'Lvye scrapbook: http://127.0.0.1:{args.port}',flush=True)
    ThreadingHTTPServer(('127.0.0.1',args.port),Handler).serve_forever()
