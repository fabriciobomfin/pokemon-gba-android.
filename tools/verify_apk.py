#!/usr/bin/env python3
"""Fail CI if a required offline file is missing or a game was packaged."""
import hashlib
import pathlib
import sys
import zipfile
root = pathlib.Path(__file__).resolve().parents[1]
with zipfile.ZipFile(sys.argv[1]) as apk:
    names = apk.namelist()
    for name in names:
        assert not name.lower().endswith(('.nds', '.dsv', '.gba', '.gb', '.gbc', '.rom', '.sav', '.srm')), 'Unexpected game/save: ' + name
    for line in (root / 'tools/emulator.sha256').read_text().splitlines():
        sha, name = line.split('  ', 1)
        assert hashlib.sha256(apk.read('assets/data/' + name)).hexdigest() == sha, name
    for name in ['index.html', 'play.html', 'licenses.html', 'GPL-3.0.txt', 'MPL-2.0.txt']:
        assert apk.read('assets/' + name), name
print('APK verified: complete offline GBA and DS runtimes; no ROM or personal save.')
