#!/usr/bin/env python3
"""Compare packaged drawing bytes with a locally verified generated manifest; no signing/device claim."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

MANIFEST = 'noble-drawing-manifest.json'


def verify_drawing_apk(apk, assets):
    manifest_file = assets / MANIFEST
    if manifest_file.is_symlink() or not manifest_file.is_file():
        raise ValueError('Drawing manifest must be a regular file')
    if manifest_file.stat().st_size > 4 * 1024 * 1024:
        raise ValueError('Drawing manifest is too large')
    raw = manifest_file.read_bytes()
    if len(raw) > 4 * 1024 * 1024:
        raise ValueError('Drawing manifest is too large')
    manifest = json.loads(raw)
    if manifest.get('version') != 1 or not isinstance(manifest.get('assets'), dict):
        raise ValueError('Unsupported drawing manifest')
    if not {'index.html', 'config.js', 'editor.js', 'editor.css'}.issubset(manifest['assets']):
        raise ValueError('Drawing manifest omits core assets')
    prefix = 'assets/drawing/'
    with zipfile.ZipFile(apk) as package:
        names = package.namelist()
        drawing_names = [name for name in names if name.startswith(prefix) and not name.endswith('/')]
        expected = {prefix + name for name in manifest['assets']} | {prefix + MANIFEST}
        if len(drawing_names) != len(set(drawing_names)) or set(drawing_names) != expected:
            raise ValueError('Packaged drawing file set differs from generated assets')
        if package.read(prefix + MANIFEST) != raw:
            raise ValueError('Packaged drawing manifest differs from generated assets')
        for name, description in manifest['assets'].items():
            info = package.getinfo(prefix + name)
            if info.file_size != description['bytes']:
                raise ValueError('Packaged drawing size mismatch: ' + name)
            digest = hashlib.sha256()
            with package.open(info) as stream:
                for chunk in iter(lambda: stream.read(65536), b''):
                    digest.update(chunk)
            if digest.hexdigest() != description['sha256']:
                raise ValueError('Packaged drawing digest mismatch: ' + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets', type=Path, required=True)
    parser.add_argument('--scan', type=Path)
    parser.add_argument('apks', type=Path, nargs='*')
    args = parser.parse_args()
    apks = sorted(set(args.apks + (list(args.scan.rglob('*.apk')) if args.scan else [])))
    if not apks:
        parser.error('No APK files found')
    for apk in apks:
        verify_drawing_apk(apk, args.assets)
        print('Drawing bytes verified in ' + apk.name + '; signing and device acceptance are separate.')


if __name__ == '__main__':
    main()
