"""Exercise actual loopback HTTP boundaries without calling any external service."""
import http.client
import json
from pathlib import Path
import sys
import threading
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'desktop-preview/scrapbook'))
from server import Handler, ThreadingHTTPServer


class QuietHandler(Handler):
    def log_message(self, *args): pass


class LocalTravelServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), QuietHandler)
        cls.port = cls.server.server_port
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def request(self, method, url, value=None, headers=None):
        connection = http.client.HTTPConnection('127.0.0.1', self.port, timeout=3)
        connection.request(method, url, json.dumps(value).encode() if value is not None else None, headers or {})
        response = connection.getresponse()
        result = response.status, response.read()
        connection.close()
        return result

    def test_cross_origin_rejected(self):
        status, _ = self.request('POST', '/api/travel', {}, {'Content-Type': 'application/json', 'Origin': 'https://other.example'})
        self.assertEqual(status, 403)

    def test_missing_consent_returns_readable_error(self):
        status, body = self.request('POST', '/api/travel', {'action': 'recommend', 'config': {'consent': False, 'apiKey': 'must-not-echo'}},
                                    {'Content-Type': 'application/json', 'Origin': f'http://127.0.0.1:{self.port}'})
        self.assertEqual(status, 400)
        self.assertIn('同意', json.loads(body)['error'])
        self.assertNotIn(b'must-not-echo', body)

    def test_python_source_not_public(self):
        self.assertEqual(self.request('GET', '/travel_ai.py')[0], 404)
        self.assertEqual(self.request('GET', '/%2e%2e/%2e%2e/PRODUCT.md')[0], 404)


if __name__ == '__main__': unittest.main()
