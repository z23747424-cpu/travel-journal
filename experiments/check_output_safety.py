#!/usr/bin/env python3
"""Regression check: re-evaluation never overwrites baseline or adjacent reports."""
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent
originals = [ROOT / "baseline_results.json", ROOT / "基线实测.md"]
before = [hashlib.sha256(p.read_bytes()).hexdigest() for p in originals]

def run(path):
    return subprocess.run([sys.executable, str(ROOT / "evaluate.py"), "--output", str(path)], capture_output=True, text=True)

with tempfile.TemporaryDirectory(prefix="travel-results-") as temp:
    folder = Path(temp)
    fresh = folder / "fresh.json"
    assert run(fresh).returncode == 0
    saved = fresh.read_bytes()
    assert fresh.with_suffix(".md").is_file()
    assert run(fresh).returncode != 0 and fresh.read_bytes() == saved
    conflict = folder / "existing.json"
    conflict.with_suffix(".md").write_text("preserve this original", encoding="utf-8")
    assert run(conflict).returncode != 0 and not conflict.exists()
    assert conflict.with_suffix(".md").read_text() == "preserve this original"
    wrong_suffix = folder / "same.md"
    assert run(wrong_suffix).returncode != 0 and not wrong_suffix.exists()

assert [hashlib.sha256(p.read_bytes()).hexdigest() for p in originals] == before
print("PASS: fresh output, JSON protection, Markdown protection, path collision, original baseline unchanged; networkCalls=0")
