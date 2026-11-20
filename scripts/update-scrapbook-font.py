"""Refresh the self-hosted display subset with fixed UI Chinese and punctuation."""
import hashlib
from io import BytesIO
import json
from pathlib import Path
import re
from urllib.parse import quote
from urllib.request import Request, urlopen

root = Path(__file__).resolve().parents[1] / 'desktop-preview/scrapbook'
manifest = root / 'assets/font-source.json'
metadata = json.loads(manifest.read_text())
characters = set(metadata['subset_characters'])
for filename in ('app.js', 'planner.js', 'markers.js', 'map.js', 'index.html'):
    characters.update(re.findall(r'[\u4e00-\u9fff]', (root / filename).read_text()))
characters.update('0123456789，。·？！：、（）「」—…')
subset = ''.join(sorted(characters))
url = 'https://fonts.googleapis.com/css2?family=Ma+Shan+Zheng&text=' + quote(subset)
request = Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36'})
with urlopen(request, timeout=40) as response:
    css = response.read().decode()
match = re.search(r'url\((https://fonts\.gstatic\.com/[^)]+)\)', css)
if not match:
    raise RuntimeError('Google Fonts did not return the expected WOFF2')
with urlopen(match[1], timeout=40) as response:
    data = response.read()
if not data.startswith(b'wOF2'):
    from fontTools.ttLib import TTFont
    font = TTFont(BytesIO(data)); font.flavor = 'woff2'
    output = BytesIO(); font.save(output); data = output.getvalue()
(root / 'assets/handwriting.woff2').write_bytes(data)
metadata.update(source_css=url, font_url=match[1], sha256=hashlib.sha256(data).hexdigest(), subset_characters=subset,
                coverage='Fixed Chinese UI labels and punctuation; arbitrary user text uses the existing fallback.')
manifest.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({'characters': len(subset), 'bytes': len(data), 'sha256': metadata['sha256']}))
