"""Exercise the APK gate with actual editor assets, never a device-readiness claim."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT.parent / 'android/app/src/main/assets/creative-editors'
DESCRIPTORS = b'\0'.join(
    f'Lcom/honorablemobile/{name};'.encode()
    for name in ('NobleCreativeModule', 'NobleCreativeEditorActivity')
)


class ApkGateTest(unittest.TestCase):
    def check_package(self, *, rename=False, corrupt=False, missing_host=False):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'fixture.apk'
            with zipfile.ZipFile(apk, 'w', compression=zipfile.ZIP_STORED) as package:
                package.writestr('assets/index.android.bundle', b'test fixture, not executable')
                # Multidex matters: no desired descriptors in classes.dex.
                package.writestr('classes.dex', b'other classes')
                package.writestr('classes2.dex', b'absent' if missing_host else DESCRIPTORS)
                for path in sorted(ASSETS.rglob('*')):
                    if not path.is_file():
                        continue
                    name = 'assets/creative-editors/' + path.relative_to(ASSETS).as_posix()
                    if rename and name.endswith('.wasm.gzip'):
                        name = name.removesuffix('.gzip')
                    if corrupt and name.endswith('/image/editor_bg.wasm.gzip'):
                        data = bytearray(path.read_bytes())
                        data[-1] ^= 1
                        package.writestr(name, data)
                    else:
                        package.write(path, name)
            return subprocess.run(
                [sys.executable, str(ROOT / 'verify-apk.py'), str(apk)],
                capture_output=True, text=True,
            )

    def test_real_assets_and_multidex_descriptors_pass(self):
        result = self.check_package()
        self.assertEqual(result.returncode, 0, result.stderr)
        report = json.loads(result.stdout)
        self.assertEqual(report['nativeDescriptors'], 2)
        self.assertFalse(report['deviceTested'])
        self.assertFalse(report['signatureVerified'])
        self.assertEqual(set(report['editors']), {'image', 'video'})

    def test_packager_filename_rewrite_is_rejected(self):
        result = self.check_package(rename=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('stale or unexpected editor files', result.stderr)

    def test_changed_compressed_runtime_is_rejected(self):
        result = self.check_package(corrupt=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('wrong packaged bytes', result.stderr)

    def test_missing_native_host_is_rejected(self):
        result = self.check_package(missing_host=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('missing full-editor native', result.stderr)


if __name__ == '__main__':
    unittest.main()
