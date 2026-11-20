"""No network or user credentials. Verify the live bridge with injected upstream responses."""
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('travel_ai', Path(__file__).resolve().parents[1] / 'desktop-preview/scrapbook/travel_ai.py')
ai = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ai)


class TravelBridgeTests(unittest.TestCase):
    def setUp(self):
        self.request = {'action': 'recommend', 'needs': '看海，少走路，不吃海鲜', 'duration': 2,
                        'config': {'endpoint': 'https://example.com/v1', 'model': 'test-model', 'apiKey': 'test-only-ai', 'searchKey': 'test-only-search', 'consent': True}}
        self.calls = []
        self.answer = {'destinations': [{'name': '青岛', 'region': '山东', 'reason': '临海', 'sourceIds': ['s1']}]}

    def transport(self, url, key, body):
        self.calls.append((url, key, body))
        if 'tavily.com' in url:
            return {'results': [{'title': '青岛旅行', 'url': 'https://example.com/qingdao', 'content': '青岛海边散步，可乘公共交通。'}]}
        return {'choices': [{'message': {'content': json.dumps(self.answer)}}]}

    def run_request(self):
        return ai.run_travel(self.request, self.transport, lambda x: x + '/chat/completions')

    def test_search_precedes_model_and_keeps_full_needs(self):
        out = self.run_request()
        self.assertEqual(out['destinations'][0]['name'], '青岛')
        self.assertEqual(len(self.calls), 2)
        self.assertIn('tavily.com', self.calls[0][0])
        self.assertIn('不吃海鲜', self.calls[1][2]['messages'][1]['content'])
        self.assertNotIn('test-only', json.dumps(out))

    def test_consent_required_before_network(self):
        self.request['config']['consent'] = False
        with self.assertRaises(ai.TravelError): self.run_request()
        self.assertEqual(self.calls, [])

    def test_missing_credentials(self):
        self.request['config']['searchKey'] = ''
        with self.assertRaises(ai.TravelError): self.run_request()
        self.assertEqual(self.calls, [])

    def test_reject_hallucinated_destination(self):
        self.answer['destinations'][0]['name'] = '不存在的海湾'
        with self.assertRaises(ai.TravelError): self.run_request()

    def test_reject_unknown_source(self):
        self.answer['destinations'][0]['sourceIds'] = ['fake']
        with self.assertRaises(ai.TravelError): self.run_request()

    def test_plan_has_exact_days_and_sources(self):
        self.request.update(action='plan', destination='青岛')
        self.answer = {'title': '海边两天', 'summary': '慢慢走', 'days': [{'title': '看海', 'activities': [{'place': '青岛', 'description': '看海', 'sourceIds': ['s1']}]} for _ in range(2)]}
        self.assertEqual(len(self.run_request()['plan']['days']), 2)
        self.answer['days'].pop()
        with self.assertRaises(ai.TravelError): self.run_request()

    def test_untrusted_source_url(self):
        self.assertFalse(ai.source_url('javascript:alert(1)'))
        self.assertFalse(ai.source_url('https://user:secret@example.com/'))

    def test_private_endpoint_rejected(self):
        with self.assertRaises(ai.TravelError): ai.endpoint_url('https://127.0.0.1/v1')
        with self.assertRaises(ai.TravelError): ai.endpoint_url('http://example.com/v1')
        with self.assertRaises(ai.TravelError): ai.endpoint_url('https://example.com/v1?api_key=secret')

    def test_normalization(self):
        with patch.object(ai.socket, 'getaddrinfo', return_value=[(2, 1, 6, '', ('1.1.1.1', 443))]):
            self.assertEqual(ai.endpoint_url('https://example.com/v1'), 'https://example.com/v1/chat/completions')
            self.assertEqual(ai.endpoint_url('https://example.com/api/v1/chat/completions'), 'https://example.com/api/v1/chat/completions')


if __name__ == '__main__': unittest.main()
