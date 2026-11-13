#!/usr/bin/env python3
"""Verify exact bundled creative-AI weights and license bytes; no device/signing claim."""
import argparse
import hashlib
from pathlib import Path
import zipfile

# Pinned upstream artifacts and full redistribution notices, not local LFS-pointer comparisons.
EXPECTED = {'assets/depth/LICENSE.txt': (11357, 'c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4'), 'assets/depth/NOTICE.txt': (1033, '10ededaa0f59bf780005b7abdac8ba81587cdeca480548ee146716e2cdc9f081'), 'assets/depth/model_quantized.onnx': (27258801, 'fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d'), 'assets/inpaint/LICENSE-WEIGHTS.txt': (1083, '674e33456b8d03693b84ab305aa7372d93c91e68b234651b0adad106d518e6eb'), 'assets/inpaint/NOTICE.txt': (886, '112ba0310c808a4f3c7694bc7aaaa585280d35f9ebc389bdebe8decfcce90bfa'), 'assets/inpaint/migan_pipeline_v2.onnx': (28079181, '6f1f3530a1a2324b19752018ce756088b07973cda8d7d890034ace5c8a48c40b')}


def verify_ai_apk(apk, expected=None):
    requirements = EXPECTED if expected is None else expected
    if not requirements:
        raise ValueError("No required AI assets")
    with zipfile.ZipFile(apk) as package:
        names = package.namelist()
        for name, (size, digest) in requirements.items():
            if names.count(name) != 1:
                raise ValueError("Missing or duplicate AI asset: " + name)
            info = package.getinfo(name)
            if info.file_size != size:
                raise ValueError("AI asset size mismatch: " + name)
            actual = hashlib.sha256()
            with package.open(info) as stream:
                for chunk in iter(lambda: stream.read(65536), b''):
                    actual.update(chunk)
            if actual.hexdigest() != digest:
                raise ValueError("AI asset digest mismatch: " + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scan', type=Path)
    parser.add_argument('apks', type=Path, nargs='*')
    args = parser.parse_args()
    apks = sorted(set(args.apks + (list(args.scan.rglob('*.apk')) if args.scan else [])))
    if not apks:
        parser.error('No APK files found')
    for apk in apks:
        verify_ai_apk(apk)
        print('Pinned depth/eraser weights and licenses verified in ' + apk.name + '; device acceptance is separate.')


if __name__ == '__main__':
    main()
