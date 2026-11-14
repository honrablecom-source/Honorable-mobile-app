"""Read-only check that a built full React Native APK contains these exact studios.

This verifies packaged bytes and native class descriptors, not signatures, a running
WebView, real-device behavior or store readiness. No APK extraction is performed.
"""
import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile

if len(sys.argv) != 2:
    raise SystemExit('Usage: python3 full-editors/verify-apk.py PATH_TO_FULL_REACT_NATIVE_APK')
apk = Path(sys.argv[1])
assets = Path(__file__).resolve().parent.parent / 'android/app/src/main/assets/creative-editors'
report = {'version': 1, 'editors': {}, 'deviceTested': False, 'signatureVerified': False}
with zipfile.ZipFile(apk) as archive:
    members = archive.namelist()
    if len(members) != len(set(members)):
        raise SystemExit('APK contains ambiguous duplicate entries.')
    if 'assets/index.android.bundle' not in members:
        raise SystemExit('Expected the full React Native application, not a standalone shell.')
    for kind in ('image', 'video'):
        source_receipt = (assets / kind / 'bundle.json').read_bytes()
        receipt_name = f'assets/creative-editors/{kind}/bundle.json'
        if archive.getinfo(receipt_name).file_size != len(source_receipt) or archive.read(receipt_name) != source_receipt:
            raise SystemExit(f'{kind}: packaged receipt differs from current source.')
        receipt = json.loads(source_receipt)
        if receipt['version'] != 1 or receipt['kind'] != kind:
            raise SystemExit('Invalid source editor receipt.')
        prefix = f'assets/creative-editors/{kind}/'
        actual = {name[len(prefix):] for name in members if name.startswith(prefix) and not name.endswith('/')}
        if actual != set(receipt['files']) | {'bundle.json'}:
            raise SystemExit(f'{kind}: stale or unexpected editor files are packaged.')
        total = 0
        for name, pin in receipt['files'].items():
            if not re.fullmatch(r'[A-Za-z0-9_./-]+', name) or any(part in ('', '.', '..') for part in name.split('/')):
                raise SystemExit('Invalid editor asset path.')
            if not 1 <= pin['bytes'] <= 96 * 1024 * 1024:
                raise SystemExit('Unexpected editor asset size.')
            entry = archive.getinfo(f'assets/creative-editors/{kind}/{name}')
            if entry.file_size != pin['bytes']:
                raise SystemExit(f'{kind}/{name}: wrong packaged size.')
            digest, count = hashlib.sha256(), 0
            with archive.open(entry) as stream:
                while chunk := stream.read(32768):
                    count += len(chunk)
                    if count > pin['bytes']:
                        raise SystemExit('Oversized packaged asset.')
                    digest.update(chunk)
            if count != pin['bytes'] or digest.hexdigest() != pin['sha256']:
                raise SystemExit(f'{kind}/{name}: wrong packaged bytes.')
            total += count
        report['editors'][kind] = {'files': len(receipt['files']), 'bytes': total, 'sourceRevision': receipt['sourceRevision'], 'receiptSha256': hashlib.sha256(source_receipt).hexdigest()}
    wanted = {f'Lcom/honorablemobile/{name};'.encode() for name in ('NobleCreativeModule', 'NobleCreativePackage', 'NobleCreativeEditorActivity')}
    found = set()
    overlap = max(map(len, wanted)) - 1
    for name in members:
        if not re.fullmatch(r'classes(?:[0-9]+)?\.dex', name):
            continue
        with archive.open(name) as stream:
            tail = b''
            while chunk := stream.read(1024 * 1024):
                data = tail + chunk
                found.update(marker for marker in wanted if marker in data)
                tail = data[-overlap:]
    if found != wanted:
        raise SystemExit('Built APK is missing full-editor native host/module/package descriptors.')
    report['nativeDescriptors'] = len(found)
print(json.dumps(report, indent=2))
