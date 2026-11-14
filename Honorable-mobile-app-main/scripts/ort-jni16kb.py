#!/usr/bin/env python3
"""Backport ORT's JNI-only 16KB linker fix; preserve published Java/core bytes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VENDOR = ROOT / 'third_party/onnxruntime-jni-1.20.0'
AAR_SHA = '07a8f71ef890afed8c6087a56220e6d558a492804276ee2dd7cb7f6262242027'
ARCHES = {'arm64-v8a': 'aarch64-linux-android21', 'x86_64': 'x86_64-linux-android21'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_hash(file):
    value = hashlib.sha256()
    with Path(file).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1048576), b''):
            value.update(chunk)
    return value.hexdigest()


def elf_info(data):
    require(len(data) >= 64 and data[:6] == b'\x7fELF\x02\x01', 'Expected little-endian ELF64')
    def unpack(fmt, at):
        require(0 <= at <= len(data) - struct.calcsize(fmt), 'Truncated ELF structure')
        return struct.unpack_from(fmt, data, at)
    phoff = unpack('<Q', 32)[0]
    phsize, phnum = unpack('<HH', 54)
    require(phsize >= 56 and phnum <= 128, 'Invalid ELF program table')
    loads = []
    for index in range(phnum):
        at = phoff + index * phsize
        if unpack('<I', at)[0] == 1:
            offset, address = unpack('<QQ', at + 8)
            loads.append((offset, address, unpack('<Q', at + 48)[0]))
    require(loads, 'Missing ELF load segments')
    shoff = unpack('<Q', 40)[0]
    shsize, shnum = unpack('<HH', 58)
    require(shsize >= 64 and shnum <= 4096, 'Invalid ELF section table')
    sections = [unpack('<IIQQQQIIQQ', shoff + i * shsize) for i in range(shnum)]
    def section_bytes(section):
        offset, size = section[4:6]
        require(offset <= len(data) and size <= len(data) - offset, 'Truncated ELF section')
        return data[offset:offset + size]
    def string(table, offset):
        require(0 <= offset < len(table), 'Invalid ELF string')
        end = table.find(b'\0', offset)
        require(end >= 0, 'Unterminated ELF string')
        return table[offset:end].decode('ascii')
    exports, ort_imports, needed = set(), set(), set()
    for section in sections:
        if section[1] not in (6, 11):
            continue
        require(section[6] < len(sections), 'Invalid ELF string-table link')
        strings = section_bytes(sections[section[6]])
        table = section_bytes(section)
        stride = section[9]
        require(stride >= (24 if section[1] == 11 else 16) and len(table) % stride == 0, 'Invalid ELF entries')
        for offset in range(0, len(table), stride):
            if section[1] == 6:
                tag, value = struct.unpack_from('<QQ', table, offset)
                if tag == 1:
                    needed.add(string(strings, value))
            else:
                name, info, visibility, index, _, _ = struct.unpack_from('<IBBHQQ', table, offset)
                if not name:
                    continue
                label = string(strings, name)
                if index == 0 and label.startswith('Ort'):
                    ort_imports.add(label)
                if index and info & 15 == 2 and info >> 4 in (1, 2) and visibility & 3 == 0:
                    exports.add(label)
    return {'aligned16KB': all(a >= 16384 and o % 16384 == v % 16384 for o, v, a in loads),
            'loadAlignments': [x[2] for x in loads], 'exports': sorted(exports),
            'ortImports': sorted(ort_imports), 'needed': sorted(needed)}


def native_methods(data):
    """Read actual class-file native descriptors, including static/instance ABI."""
    at = 0
    def take(n):
        nonlocal at
        require(n >= 0 and at + n <= len(data), 'Truncated Java class')
        value = data[at:at+n]; at += n
        return value
    def number(n):
        return int.from_bytes(take(n), 'big')
    require(take(4) == b'\xca\xfe\xba\xbe', 'Invalid Java class')
    take(4); count = number(2); pool = [None] * count; i = 1
    widths = {3: 4, 4: 4, 5: 8, 6: 8, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while i < count:
        tag = number(1)
        if tag == 1:
            pool[i] = take(number(2)).decode('utf8', errors='replace')
        elif tag == 7:
            pool[i] = number(2)
        else:
            require(tag in widths, 'Unknown Java constant kind')
            take(widths[tag])
            if tag in (5, 6):
                i += 1
        i += 1
    number(2); owner = pool[pool[number(2)]]; number(2); take(2 * number(2))
    def attributes():
        for _ in range(number(2)):
            number(2); take(number(4))
    for _ in range(number(2)):
        take(6); attributes()
    methods = {}
    for _ in range(number(2)):
        access, name, descriptor = number(2), pool[number(2)], pool[number(2)]
        if access & 0x100:
            methods[(owner, name, descriptor)] = bool(access & 8)
        attributes()
    return methods


def jar_natives(file):
    result = {}
    with zipfile.ZipFile(file) as jar:
        for name in jar.namelist():
            if name.endswith('.class'):
                result.update(native_methods(jar.read(name)))
    return result


def validate_vendor(vendor):
    metadata = json.loads((vendor / 'provenance.json').read_text())
    require(metadata['originalAarSha256'] == AAR_SHA, 'Unexpected original runtime artifact pin')
    actual = {p.relative_to(vendor).as_posix() for p in vendor.rglob('*') if p.is_file()}
    require(actual == set(metadata['files']) | {'README.md', 'provenance.json'}, 'Unexpected or missing vendored source')
    for name, value in metadata['files'].items():
        file = vendor / name
        require(not file.is_symlink() and file_hash(file) == value, 'Vendored source pin failed: ' + name)
    return metadata


def distribution_notices(vendor, metadata):
    copyrights = set()
    for name in metadata['files']:
        for line in (vendor / name).read_text().splitlines():
            if re.search(r'Copyright\s*(?:\(c\)|\d)', line, re.I):
                copyrights.add(line.strip().strip('/*').strip())
    notice = ('ONNX Runtime1.20.0 Java/JNI compatibility backport\n'
        'Official unmodified source commit: ' + metadata['sourceCommit'] + '\n'
        'JNI16KB linker fix: https://github.com/microsoft/onnxruntime/pull/24947\n'
        'Full MIT terms are in LICENSE.txt. Original source copyright notices follow.\n\n'
        + '\n'.join(sorted(copyrights)) + '\n')
    return {'assets/ort-jni16kb/LICENSE.txt': (vendor / 'LICENSE').read_bytes(),
            'assets/ort-jni16kb/NOTICE.txt': notice.encode('utf8')}


def build_aar(original, ndk, output, javac=None, vendor=VENDOR):
    original, ndk, output = Path(original), Path(ndk), Path(output)
    require(file_hash(original) == AAR_SHA, 'Original ORT1.20 AAR integrity check failed')
    metadata = validate_vendor(vendor)
    require('Pkg.Revision = ' + metadata['ndkVersion'] in (ndk / 'source.properties').read_text(), 'Use the declared NDK27.1.12297006')
    host = 'windows-x86_64' if sys.platform == 'win32' else 'darwin-x86_64' if sys.platform == 'darwin' else 'linux-x86_64'
    toolchain = ndk / 'toolchains/llvm/prebuilt' / host
    compiler = toolchain / 'bin' / ('clang.exe' if sys.platform == 'win32' else 'clang')
    javac = javac or shutil.which('javac')
    require(javac and compiler.is_file(), 'Java compiler and declared Android NDK are required')
    output.parent.mkdir(parents=True, exist_ok=True)
    report = {'sourceCommit': metadata['sourceCommit'], 'originalAarSha256': AAR_SHA,
              'inferenceEngineChanged': False, 'javaApiChanged': False, 'bridges': {}}
    with tempfile.TemporaryDirectory(prefix='ort-jni16kb-', dir=output.parent) as directory:
        work = Path(directory); headers = work / 'headers'; classes = work / 'classes'
        headers.mkdir(); classes.mkdir()
        with zipfile.ZipFile(original) as archive:
            classes_jar = work / 'classes.jar'; classes_jar.write_bytes(archive.read('classes.jar'))
            java = [vendor / name for name in metadata['files'] if name.endswith('.java')]
            subprocess.run([str(javac), '--release', '8', '-Xlint:-options', '-classpath', str(classes_jar), '-h', str(headers), '-d', str(classes), *map(str, java)], check=True, capture_output=True)
            generated = {}
            for file in classes.rglob('*.class'):
                generated.update(native_methods(file.read_bytes()))
            require(generated == jar_natives(classes_jar), 'Generated JNI declarations differ from the published Java API')
            report['nativeMethodsMatched'] = len(generated)
            (headers / 'onnxruntime_config.h').write_text('#pragma once\n#define ORT_VERSION "1.20.0"\n')
            native = vendor / 'java/src/main/native'; replacements = {}
            for arch, target in ARCHES.items():
                folder = work / arch; folder.mkdir()
                core_name, jni_name = f'jni/{arch}/libonnxruntime.so', f'jni/{arch}/libonnxruntime4j_jni.so'
                core, baseline = archive.read(core_name), archive.read(jni_name)
                require(elf_info(core)['aligned16KB'], 'Published core is not16KB aligned')
                (folder / 'libonnxruntime.so').write_bytes(core)
                result = folder / 'libonnxruntime4j_jni.so'
                command = [str(compiler), '--target=' + target, '--sysroot=' + str(toolchain / 'sysroot'),
                    '-shared', '-O2', '-DNDEBUG', '-DANDROID', '-D_FORTIFY_SOURCE=2', '-fstack-protector-strong', '-fPIC', '-std=c11', '-DUSE_NNAPI',
                    '-Wl,-z,max-page-size=16384', '-Wl,--no-undefined', '-Wl,-soname,libonnxruntime4j_jni.so',
                    '-ffile-prefix-map=' + str(vendor.resolve()) + '=onnxruntime-1.20.0',
                    '-I' + str(headers), '-I' + str(vendor / 'include'), '-I' + str(vendor / 'include/onnxruntime/core/session'),
                    '-I' + str(native), '-I' + str(vendor / 'orttraining/orttraining/training_api/include'),
                    *[str(vendor / name) for name in metadata['files'] if name.endswith('.c')],
                    '-L' + str(folder), '-lonnxruntime', '-lm', '-ldl', '-o', str(result)]
                subprocess.run(command, check=True, capture_output=True)
                rebuilt = result.read_bytes(); before, after = elf_info(baseline), elf_info(rebuilt)
                require(after['aligned16KB'], 'Rebuilt JNI bridge is not16KB aligned')
                for field in ['exports', 'ortImports', 'needed']:
                    require(before[field] == after[field], 'JNI ABI/dependencies changed: ' + field)
                replacements[jni_name] = rebuilt
                report['bridges'][arch] = {'sha256': digest(rebuilt), 'bytes': len(rebuilt), 'exportsMatched': len(after['exports']),
                    'originalSha256': digest(baseline), 'coreSha256': digest(core), 'loadAlignments': after['loadAlignments']}
            notices = distribution_notices(vendor, metadata)
            require(not (set(notices) & set(archive.namelist())), 'Original runtime unexpectedly already has the backport notices')
            candidate = work / 'patched.aar'
            with zipfile.ZipFile(candidate, 'w') as out:
                for info in archive.infolist():
                    out.writestr(info, replacements.get(info.filename, archive.read(info)))
                for name, data in notices.items():
                    out.writestr(zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)), data)
            verify_delta(original, candidate, set(replacements), added=set(notices))
            report['noticesAdded'] = {name: digest(data) for name, data in notices.items()}
            shutil.copyfile(candidate, output)
    report['patchedAarSha256'] = file_hash(output)
    output.with_suffix('.receipt.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


def verify_delta(before, after, changed, removed=frozenset(), added=frozenset()):
    with zipfile.ZipFile(before) as source, zipfile.ZipFile(after) as target:
        original, names = source.namelist(), target.namelist()
        require(len(original) == len(set(original)) and len(names) == len(set(names)), 'Duplicate archive entry')
        require(set(names) == (set(original) - set(removed)) | set(added), 'Unexpected archive file-set change: added=' + repr(sorted(set(names) - set(original))[:8]) + ', missing=' + repr(sorted((set(original) - set(removed)) - set(names))[:8]))
        require(set(changed) <= set(names) and set(removed) <= set(original) and not (set(added) & set(original)), 'Invalid declared archive delta')
        for name in names:
            if name not in changed and name not in added:
                require(source.read(name) == target.read(name), 'Unchanged archive bytes differ: ' + name)
        require(target.testzip() is None, 'Archive CRC failed')


def patch_internal_apk(base, manifest_file, original_aar, fixed_aar, tools, output, fix_source_sha, arm64_only=False, ndk=None):
    """Explicit derived tester artifact: Java, model, resource and core bytes stay exact."""
    base, original_aar, fixed_aar, tools, output = map(Path, (base, original_aar, fixed_aar, tools, output))
    manifest = json.loads(Path(manifest_file).read_text())
    require(manifest['variant'] == 'internal' and manifest['requiresMetro'] is False, 'Use the self-contained internal CI artifact')
    require(file_hash(base) == manifest['apk']['sha256'] and base.stat().st_size == manifest['apk']['sizeBytes'], 'Base APK differs from CI identity')
    require(re.fullmatch(r'[0-9a-f]{40}', manifest['commit']) and re.fullmatch(r'[0-9a-f]{40}', fix_source_sha), 'Exact base/fix source identities required')
    require(file_hash(original_aar) == AAR_SHA, 'Unexpected runtime AAR')
    changed_aar = {f'jni/{arch}/libonnxruntime4j_jni.so' for arch in ARCHES}
    notices = distribution_notices(VENDOR, validate_vendor(VENDOR))
    verify_delta(original_aar, fixed_aar, changed_aar, added=set(notices))
    with zipfile.ZipFile(fixed_aar) as fixed:
        require(all(fixed.read(name) == data for name, data in notices.items()), 'Runtime copyright/license notices differ')
    suffix = '.exe' if sys.platform == 'win32' else ''
    require(ndk, 'Declared NDK is required to reproduce Android Gradle stripping')
    ndk = Path(ndk)
    metadata = validate_vendor(VENDOR)
    require('Pkg.Revision = ' + metadata['ndkVersion'] in (ndk / 'source.properties').read_text(), 'Use the declared NDK27.1.12297006')
    host = 'windows-x86_64' if sys.platform == 'win32' else 'darwin-x86_64' if sys.platform == 'darwin' else 'linux-x86_64'
    strip_tool = ndk / 'toolchains/llvm/prebuilt' / host / 'bin' / ('llvm-strip' + suffix)
    java = shutil.which('java'); require(java, 'Java is required for APK signatures')
    signer = [java, '-jar', str(tools / 'lib/apksigner.jar')]
    def cert(file):
        result = subprocess.run([*signer, 'verify', '--print-certs', str(file)], check=True, capture_output=True, text=True)
        matches = re.findall(r'certificate SHA-256 digest: ([a-fA-F0-9]{64})', result.stdout)
        require(len(matches) == 1, 'Expected exactly one APK signer')
        return matches[0].lower()
    badging = subprocess.run([str(tools / ('aapt' + suffix)), 'dump', 'badging', str(base)], check=True, capture_output=True, text=True).stdout
    minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
    require(minimum and int(minimum[1]) >= 24, 'This v2-only tester derivation requires Android API24 or newer')
    require("package: name='com.honorablemobile'" in badging and 'application-debuggable' not in badging, 'Expected the non-debuggable Noble internal app')
    original_cert = cert(base)
    require(original_cert == 'fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c', 'Only the existing public debug tester key is supported')
    gradle = (ROOT / 'mobile-react-native/android/app/build.gradle').read_text()
    block = re.search(r'signingConfigs\s*\{\s*debug\s*\{(.*?)\}', gradle, re.S)
    require(block, 'Public debug signing configuration missing')
    values = {}
    for key in ['storePassword', 'keyAlias', 'keyPassword']:
        match = re.search(key + r"\s+['\"]([^'\"\r\n]+)['\"]", block[1])
        require(match, 'Public debug signing field missing')
        values[key] = match[1]
    keystore = ROOT / 'mobile-react-native/android/app/debug.keystore'
    require(keystore.is_file() and not keystore.is_symlink(), 'Public debug keystore missing')
    output.parent.mkdir(parents=True, exist_ok=True)
    report = {'buildMethod': 'CI full app plus exact-version JNI-only alignment backport',
        'baseFullBuildSource': manifest['commit'], 'jniFixSource': fix_source_sha,
        'baseApkSha256': file_hash(base), 'baseFullBuildPassed': True, 'derivedApk': True,
        'requiresMetro': False, 'signing': 'PUBLIC_DEBUG_KEY', 'signerSha256': original_cert, 'minimumSdk': int(minimum[1]),
        'signInConfigured': manifest['signInConfigured'], 'deviceTested': False, 'storeReady': False}
    with tempfile.TemporaryDirectory(prefix='noble-apk-jni-', dir=output.parent) as folder:
        folder = Path(folder); unsigned = folder / 'unsigned.apk'; aligned = folder / 'aligned.apk'; signed = folder / 'signed.apk'
        strip_count = 0
        def stripped(data):
            nonlocal strip_count
            source = folder / f'strip-{strip_count}.in'; target = folder / f'strip-{strip_count}.so'; strip_count += 1
            source.write_bytes(data)
            subprocess.run([str(strip_tool), '--strip-unneeded', '-o', str(target), str(source)], check=True, capture_output=True)
            return target.read_bytes()
        with zipfile.ZipFile(original_aar) as original, zipfile.ZipFile(fixed_aar) as fixed, zipfile.ZipFile(base) as source:
            names = source.namelist(); require(len(names) == len(set(names)), 'Duplicate base APK entry')
            abis = {name.split('/')[1] for name in names if name.startswith('lib/') and name.endswith('.so')}
            require(set(ARCHES) <= abis, 'Base internal APK lacks the expected64-bit variants')
            require(not (set(notices) & set(names)), 'Base APK already has backport notice paths')
            replacements = {}
            for arch in ARCHES:
                if arm64_only and arch != 'arm64-v8a':
                    continue
                name = f'lib/{arch}/libonnxruntime4j_jni.so'; core = f'lib/{arch}/libonnxruntime.so'
                require(source.read(name) == stripped(original.read('jni/' + name[4:])), 'Base APK JNI differs from official1.20 plus exact Gradle stripping')
                require(source.read(core) == stripped(original.read('jni/' + core[4:])), 'Base APK core differs from official1.20 plus exact Gradle stripping')
                replacement = stripped(fixed.read('jni/' + name[4:])); old, new = elf_info(source.read(name)), elf_info(replacement)
                require(new['aligned16KB'], 'Replacement JNI is not16KB aligned')
                for field in ['exports', 'ortImports', 'needed']:
                    require(old[field] == new[field], 'Replacement JNI ABI differs')
                replacements[name] = replacement
            removed = {name for name in names if name.startswith('lib/') and arm64_only and name.split('/')[1] != 'arm64-v8a'}
            require(not any(re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA|EC)', name, re.I) for name in names), 'Unexpected v1 signature in this v2 base')
            with zipfile.ZipFile(unsigned, 'w') as target:
                for info in source.infolist():
                    if info.filename not in removed:
                        target.writestr(info, replacements.get(info.filename, source.read(info)))
                for name, data in notices.items():
                    target.writestr(zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)), data)
        verify_delta(base, unsigned, set(replacements), removed, set(notices))
        subprocess.run([str(tools / ('zipalign' + suffix)), '-f', '-P', '16', '4', str(unsigned), str(aligned)], check=True, capture_output=True)
        store_pass, key_pass = folder / 'store.pass', folder / 'key.pass'
        store_pass.write_text(values['storePassword']); key_pass.write_text(values['keyPassword'])
        store_pass.chmod(0o600); key_pass.chmod(0o600)
        subprocess.run([*signer, 'sign', '--ks', str(keystore), '--ks-key-alias', values['keyAlias'],
            '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'false', '--v4-signing-enabled', 'false',
            '--ks-pass', 'file:' + str(store_pass), '--key-pass', 'file:' + str(key_pass), '--out', str(signed), str(aligned)], check=True, capture_output=True)
        require(cert(signed) == original_cert, 'Derived APK signer differs from the base tester artifact')
        subprocess.run([str(tools / ('zipalign' + suffix)), '-c', '-P', '16', '4', str(signed)], check=True, capture_output=True)
        verify_delta(base, signed, set(replacements), removed, set(notices))
        with zipfile.ZipFile(signed) as archive:
            for name in archive.namelist():
                if name.startswith(('lib/arm64-v8a/', 'lib/x86_64/')) and name.endswith('.so'):
                    require(elf_info(archive.read(name))['aligned16KB'], 'A64-bit native library is still misaligned: ' + name)
            report['abis'] = sorted({name.split('/')[1] for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')})
        shutil.copyfile(signed, output)
    report.update({'sizeBytes': output.stat().st_size, 'sha256': file_hash(output), 'nativeElf16KBAlignmentVerified': True,
        'zipAlignment16KBVerified': True, 'allOtherRetainedEntryBytesIdentical': True, 'jniEntriesRebuilt': sorted(replacements),
        'abiEntriesRemoved': len(removed), 'noticesAdded': {name: digest(data) for name, data in notices.items()}, 'baseAndroidGradleStrippingExactlyReproduced': True, 'accountSearchConfigurationComplete': manifest['signInConfigured']})
    output.with_suffix('.manifest.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--aar', type=Path, required=True)
    parser.add_argument('--ndk', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--javac')
    parser.add_argument('--base-apk', type=Path)
    parser.add_argument('--base-manifest', type=Path)
    parser.add_argument('--fixed-aar', type=Path)
    parser.add_argument('--build-tools', type=Path)
    parser.add_argument('--fix-source-sha')
    parser.add_argument('--arm64-only', action='store_true')
    args = parser.parse_args()
    try:
        if args.base_apk:
            require(all([args.base_manifest, args.fixed_aar, args.build_tools, args.fix_source_sha]), 'APK derivation needs its CI manifest, rebuilt AAR, build tools and exact repair source')
            result = patch_internal_apk(args.base_apk, args.base_manifest, args.aar, args.fixed_aar, args.build_tools, args.out, args.fix_source_sha, args.arm64_only, args.ndk)
            print('Derived internal APK verified: exact retained bytes, original public tester certificate and16KB ELF/ZIP alignment; no device/store claim.')
        else:
            require(args.ndk, 'The declared Android NDK is required')
            result = build_aar(args.aar, args.ndk, args.out, args.javac)
            print(f"Exact1.20 JNI16KB backport built;{result['nativeMethodsMatched']} native methods/core/Java bytes preserved; no device/store claim.")
    except subprocess.CalledProcessError as failure:
        error = failure.stderr or b''
        sys.stderr.write((error.decode(errors='replace') if isinstance(error, bytes) else error)[-12000:])
        raise SystemExit('JNI build/packaging failed; no current artifact published')
    except ValueError as failure:
        raise SystemExit(str(failure))


if __name__ == '__main__':
    main()
