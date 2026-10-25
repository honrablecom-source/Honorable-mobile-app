"""Inspect real APK outputs before publishing the Noble ABI matrix."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1] / 'mobile-react-native'
outputs = root / 'android/app/build/outputs'
stage = outputs / 'noble'
stage.mkdir(exist_ok=True)
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / os.environ['HONORABLE_ANDROID_BUILD_TOOLS']
abis = {'arm64-v8a', 'armeabi-v7a', 'x86_64'}
records = []
for variant in ('debug', 'release'):
    for abi in ('universal', *sorted(abis)):
        candidates = list((outputs / 'apk' / variant).glob(f'app-{abi}-{variant}*.apk'))
        if len(candidates) != 1:
            raise RuntimeError(f'Expected one {variant}/{abi} APK, found {len(candidates)}')
        apk = candidates[0]
        badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
        assert "package: name='com.honorablemobile'" in badging, 'Package changed'
        assert "application-label:'Noble'" in badging, 'Wrong launcher name'
        assert re.search(r"application:.*icon='[^']+'", badging), 'Missing launcher icon metadata'
        with zipfile.ZipFile(apk) as package:
            names = package.namelist()
            libs = {a: {n.split('/')[-1] for n in names if n.startswith(f'lib/{a}/') and n.endswith('.so')} for a in abis}
            actual = {n.split('/')[1] for n in names if n.startswith('lib/') and n.endswith('.so')}
            expected = abis if abi == 'universal' else {abi}
            assert actual == expected, f'{apk.name}: expected {expected}, got {actual}'
            required = {'libonnxruntime.so', 'libonnxruntime4j_jni.so', 'libhermes.so', 'libreactnative.so'}
            for arch in expected:
                assert required <= libs[arch], f'{apk.name}: missing native dependency for {arch}: {required - libs[arch]}'
            if abi == 'universal':
                assert len({frozenset(libs[a]) for a in abis}) == 1, 'Native library ABI coverage differs'
            # Release resource shrinking can rename resource files; inspect the resource table too.
            resources = subprocess.check_output([str(tools / 'aapt'), 'dump', 'resources', str(apk)], text=True)
            assert 'ic_launcher' in resources and 'ic_noble_foreground' in resources, 'Noble icon resources missing'
            for asset in ('assets/tinyclip/model_int8.onnx', 'assets/tinyclip/tokenizer.json'):
                assert package.getinfo(asset).file_size > 0, f'Missing search asset: {asset}'
            if variant == 'release':
                assert package.getinfo('assets/index.android.bundle').file_size > 0
        signed = subprocess.run([str(tools / 'apksigner'), 'verify', str(apk)], capture_output=True).returncode == 0
        if variant == 'debug':
            assert signed, 'Debug APK is unsigned'
        suffix = '-unsigned' if not signed else ''
        target = stage / f'Noble-{abi}-{variant}{suffix}.apk'
        shutil.copy2(apk, target)
        records.append(dict(filename=target.name, sizeBytes=target.stat().st_size,
                            sha256=hashlib.sha256(target.read_bytes()).hexdigest(),
                            abis=sorted(actual), signed=signed, variant=variant,
                            requiresMetro=variant == 'debug'))
report = dict(commit=os.environ.get('GITHUB_SHA') or subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
              applicationId='com.honorablemobile', launcherName='Noble', deviceTested=False, apks=records)
(stage / 'apk-report.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
