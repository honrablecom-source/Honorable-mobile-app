"""Verify an instrumentation APK against the exact prior successful debug target.

This produces package provenance, never a physical-execution or store-ready claim.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

SOURCE = '945d046ee0b1284efa9eac73e89f2d94745d5abb'
PARENT_RUN = 44419363984
CERT = 'fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c'

def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--target', type=Path, required=True)
    p.add_argument('--test', type=Path, required=True)
    p.add_argument('--aapt', required=True)
    p.add_argument('--apksigner', required=True)
    p.add_argument('--parent-run', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    run = json.loads(args.parent_run.read_text())
    assert run['id'] == PARENT_RUN and run['head_sha'] == SOURCE
    assert run['status'] == 'completed' and run['conclusion'] == 'success'
    records = {}
    for name, path, package in [('target', args.target, 'com.honorablemobile'), ('instrumentation', args.test, 'com.honorablemobile.test')]:
        with zipfile.ZipFile(path) as archive:
            assert archive.testzip() is None
            assert archive.getinfo('AndroidManifest.xml').file_size > 0
            if name == 'instrumentation':
                dex = b''.join(archive.read(n) for n in archive.namelist() if re.fullmatch(r'classes\d*\.dex', n))
                assert b'Lcom/honorablemobile/FullStudioDeviceAcceptanceTest;' in dex
        badging = subprocess.check_output([args.aapt, 'dump', 'badging', str(path)], text=True)
        assert f"package: name='{package}'" in badging
        certificates = subprocess.check_output([args.apksigner, 'verify', '--print-certs', str(path)], text=True)
        assert set(re.findall(r'certificate SHA-256 digest: ([0-9a-fA-F]{64})', certificates)) == {CERT}
        records[name] = {'file': path.name, 'package': package, 'sha256': sha(path), 'sizeBytes': path.stat().st_size, 'signing': 'PUBLIC_DEBUG_KEY'}
    manifest = subprocess.check_output([args.aapt, 'dump', 'xmltree', str(args.test), 'AndroidManifest.xml'], text=True)
    assert re.search(r'android:targetPackage\([^)]*\)="com\.honorablemobile"', manifest)
    assert re.search(r'android:name\([^)]*\)="androidx\.test\.runner\.AndroidJUnitRunner"', manifest)
    records.update(parentSource=SOURCE, parentRunId=PARENT_RUN, physicalExecutionVerified=False, storeReady=False,
        note='Native-activity instrumentation target, not the distributable internal APK. React Native debug home needs Metro; tests launch existing private studios directly. No VM or emulator is launched by this workflow.')
    args.output.write_text(json.dumps(records, indent=2)+'\n')
    print('Verified APK pair identity, public certificates, test class and runner; physical execution remains required.')

if __name__ == '__main__':
    main()
