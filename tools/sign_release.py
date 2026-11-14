#!/usr/bin/env python3
"""Sign an aligned Gradle release APK with the pinned permanent certificate.

Required env: FIREGAME_KEYSTORE, FIREGAME_PASSWORD_FILE, APKSIGNER_JAR.
The private key is never generated here and must never be committed.
"""
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
source, destination = map(Path, sys.argv[1:3])
key = os.environ['FIREGAME_KEYSTORE']
password = os.environ['FIREGAME_PASSWORD_FILE']
signer = os.environ['APKSIGNER_JAR']
expected = (root / 'tools/signing-certificate.sha256').read_text().strip()
with tempfile.TemporaryDirectory() as folder:
    certificate = Path(folder) / 'certificate.der'
    subprocess.run(['keytool', '-exportcert', '-keystore', key,
        '-storepass:file', password, '-alias', 'firegame', '-file', str(certificate)],
        check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if hashlib.sha256(certificate.read_bytes()).hexdigest() != expected:
        raise SystemExit('Wrong signing key: release refused.')
destination.parent.mkdir(parents=True, exist_ok=True)
subprocess.run(['java', '-jar', signer, 'sign', '--ks', key,
    '--ks-key-alias', 'firegame', '--ks-pass', 'file:' + password,
    '--key-pass', 'file:' + password, '--out', str(destination), str(source)], check=True)
verification = subprocess.check_output(['java', '-jar', signer, 'verify',
    '--verbose', '--print-certs', str(destination)], text=True)
if 'Signer #1 certificate SHA-256 digest: ' + expected not in verification:
    raise SystemExit('Unexpected signed certificate.')
print(verification)
