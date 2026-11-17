"""Read-only check that a built full React Native APK contains these exact studios.

This verifies packaged bytes and native class descriptors, not signatures, a running
WebView, real-device behavior or store readiness. No APK extraction is performed.
"""
import hashlib
import gzip
import argparse
import json
from pathlib import Path
import re
import sys
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--assets-root', type=Path, default=Path(__file__).resolve().parent.parent / 'android/app/src/main/assets/creative-editors', help='Exact source-commit editor asset directory when newer development is in progress.')
parser.add_argument('--native-test-target', action='store_true', help='Explicit debug instrumentation target: verify identical studios, but do not claim a standalone React Native application or distribution acceptance.')
args = parser.parse_args()
apk = args.apk
assets = args.assets_root
report = {'version': 1, 'editors': {}, 'deviceTested': False, 'signatureVerified': False}
with zipfile.ZipFile(apk) as archive:
    members = archive.namelist()
    if len(members) != len(set(members)):
        raise SystemExit('APK contains ambiguous duplicate entries.')
    report['scope'] = 'NATIVE_ACTIVITY_INSTRUMENTATION_TARGET' if args.native_test_target else 'FULL_REACT_NATIVE_PACKAGE'
    report['reactNativeBundlePresent'] = 'assets/index.android.bundle' in members
    report['requiresMetroForReactNativeHome'] = not report['reactNativeBundlePresent']
    if not report['reactNativeBundlePresent'] and not args.native_test_target:
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
        # Verify the decoded stream too; a correctly pinned but invalid compressed
        # executable must never pass a publication gate.
        compiler = json.loads(archive.read(prefix + 'provenance.json'))['wasm']
        if compiler['compression'] != 'gzip' or not 1 <= compiler['bytes'] <= 96 * 1024 * 1024:
            raise SystemExit(f'{kind}: invalid compiler provenance.')
        digest, count = hashlib.sha256(), 0
        with archive.open(prefix + 'editor_bg.wasm.gzip') as packed, gzip.GzipFile(fileobj=packed) as decoded:
            while chunk := decoded.read(32768):
                count += len(chunk)
                if count > compiler['bytes']:
                    raise SystemExit(f'{kind}: oversized decoded runtime.')
                digest.update(chunk)
        if count != compiler['bytes'] or digest.hexdigest() != compiler['sha256']:
            raise SystemExit(f'{kind}: decoded runtime differs from original compiler output.')
        report['editors'][kind]['runtimeSha256'] = digest.hexdigest()
    # R8 may inline/rename the registration factory. Its source name is not a
    # runtime contract. The callable RN module and manifest activity are retained.
    wanted = {f'Lcom/honorablemobile/{name};'.encode() for name in ('NobleCreativeModule', 'NobleCreativeEditorActivity')}
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
