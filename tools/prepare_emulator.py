#!/usr/bin/env python3
"""Fetch pinned, checksummed EmulatorJS 4.2.3 assets at build time; never ROMs."""
import hashlib
import pathlib
import subprocess
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/src/main/assets/data'
BASE = 'https://cdn.emulatorjs.org/4.2.3/data/'

def main():
    for line in (ROOT / 'tools/emulator.sha256').read_text().splitlines():
        sha, name = line.split('  ', 1)
        path = DEST / name
        path.parent.mkdir(parents=True, exist_ok=True)
        if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() == sha:
            continue
        for attempt in range(3):
            try:
                data = subprocess.check_output(["curl", "--fail", "--silent", "--show-error", "--location", "--retry", "3", "--max-time", "120", BASE + name])
                if hashlib.sha256(data).hexdigest() != sha:
                    raise ValueError('Checksum mismatch: ' + name)
                path.write_bytes(data)
                print('Verified', name, len(data), flush=True)
                break
            except Exception:
                if attempt == 2:
                    raise
                time.sleep(2)
    print('All offline runtime assets verified.')

if __name__ == '__main__':
    main()
