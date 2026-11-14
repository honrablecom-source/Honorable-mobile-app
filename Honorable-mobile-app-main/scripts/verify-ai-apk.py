#!/usr/bin/env python3
"""Verify pinned AI/license bytes and64-bit ELF16KB alignment; no device/signing claim."""
import argparse
import hashlib
import struct
from pathlib import Path
import zipfile

# Pinned upstream artifacts and full redistribution notices, not local LFS-pointer comparisons.
EXPECTED = {'assets/depth/LICENSE.txt': (11357,
                              'c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4'),
 'assets/depth/NOTICE.txt': (1033,
                             '10ededaa0f59bf780005b7abdac8ba81587cdeca480548ee146716e2cdc9f081'),
 'assets/depth/model_quantized.onnx': (27258801,
                                       'fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d'),
 'assets/inpaint/LICENSE-WEIGHTS.txt': (1083,
                                        '674e33456b8d03693b84ab305aa7372d93c91e68b234651b0adad106d518e6eb'),
 'assets/inpaint/NOTICE.txt': (886,
                               '112ba0310c808a4f3c7694bc7aaaa585280d35f9ebc389bdebe8decfcce90bfa'),
 'assets/inpaint/migan_pipeline_v2.onnx': (28079181,
                                           '6f1f3530a1a2324b19752018ce756088b07973cda8d7d890034ace5c8a48c40b'),
 'assets/ort-jni16kb/LICENSE.txt': (1073,
                                    '2f07c72751aed99790b8a4869cf2311df85a860b22ded05fa22803587a48922c'),
 'assets/ort-jni16kb/NOTICE.txt': (1784,
                                   'a8982a910df9a775cf7a6faf6e45e19160334ad618d960b9f6d55c2e410b7d7f')}


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
        verify_native_16kb(package)


def verify_native_16kb(package):
    names = package.namelist()
    if len(names) != len(set(names)):
        raise ValueError("Duplicate APK entry")
    for name in names:
        if not name.startswith(('lib/arm64-v8a/', 'lib/x86_64/')) or not name.endswith('.so'):
            continue
        with package.open(name) as stream:
            header = stream.read(65536)
        if len(header) < 64 or header[:6] != b'\x7fELF\x02\x01':
            raise ValueError("Invalid64-bit ELF library: " + name)
        offset = struct.unpack_from('<Q', header, 32)[0]
        stride, count = struct.unpack_from('<HH', header, 54)
        if stride < 56 or not 1 <= count <= 128 or offset + stride * count > len(header):
            raise ValueError("Invalid ELF program table: " + name)
        loads = 0
        for index in range(count):
            at = offset + index * stride
            if struct.unpack_from('<I', header, at)[0] == 1:
                loads += 1
                file_offset, address = struct.unpack_from('<QQ', header, at + 8)
                alignment = struct.unpack_from('<Q', header, at + 48)[0]
                if alignment < 16384 or file_offset % 16384 != address % 16384:
                    raise ValueError("Native ELF16KB alignment mismatch: " + name)
        if not loads:
            raise ValueError("ELF load segments missing: " + name)


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
        print('Pinned depth/eraser weights, licenses and64-bit ELF16KB alignment verified in ' + apk.name + '; device acceptance is separate.')


if __name__ == '__main__':
    main()
