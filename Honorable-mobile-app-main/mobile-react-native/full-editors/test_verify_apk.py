"""Exercise the APK gate with actual editor assets, never a device-readiness claim."""
import json
from pathlib import Path
import shutil
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
    def check_package(self, *, rename=False, corrupt=False, missing_host=False, bundle=True, native_target=False, stale_patch=False, archived_assets=False):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'fixture.apk'
            with zipfile.ZipFile(apk, 'w', compression=zipfile.ZIP_STORED) as package:
                if bundle:
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
            verifier = ROOT / 'verify-apk.py'
            if stale_patch:
                sandbox = Path(directory) / 'checkout'
                editor = sandbox / 'full-editors'
                shutil.copytree(ROOT / 'patches', editor / 'patches')
                shutil.copyfile(ROOT / 'upstreams.json', editor / 'upstreams.json')
                verifier = editor / 'verify-apk.py'
                shutil.copyfile(ROOT / 'verify-apk.py', verifier)
                shutil.copytree(ASSETS, sandbox / 'android/app/src/main/assets/creative-editors')
                with (editor / 'patches/filmcraft-mobile.patch').open('ab') as patch:
                    patch.write(b'\n# A newer reviewed source change\n')
            command = [sys.executable, str(verifier), str(apk)]
            if native_target:
                command.append('--native-test-target')
            if archived_assets:
                command.extend(['--assets-root', str(ASSETS)])
            return subprocess.run(command, capture_output=True, text=True)

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

    def test_old_bundle_with_matching_assets_but_a_stale_source_patch_is_rejected(self):
        result = self.check_package(stale_patch=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('video: packaged engine predates the reviewed source or patch', result.stderr)

    def test_historical_apk_can_be_checked_against_explicit_archived_assets(self):
        result = self.check_package(stale_patch=True, archived_assets=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_missing_native_host_is_rejected(self):
        result = self.check_package(missing_host=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('missing full-editor native', result.stderr)

    def test_default_distribution_gate_still_rejects_missing_rn_bundle(self):
        result = self.check_package(bundle=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Expected the full React Native application', result.stderr)

    def test_explicit_native_target_retains_metro_and_device_limits(self):
        result = self.check_package(bundle=False, native_target=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        report = json.loads(result.stdout)
        self.assertEqual(report['scope'], 'NATIVE_ACTIVITY_INSTRUMENTATION_TARGET')
        self.assertTrue(report['requiresMetroForReactNativeHome'])
        self.assertFalse(report['reactNativeBundlePresent'])
        self.assertFalse(report['deviceTested'])
        self.assertEqual(report['nativeDescriptors'], 2)

    def test_native_target_still_rejects_corrupt_editor_bytes(self):
        result = self.check_package(bundle=False, native_target=True, corrupt=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('wrong packaged bytes', result.stderr)

    def test_native_target_still_requires_real_native_host(self):
        result = self.check_package(bundle=False, native_target=True, missing_host=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('missing full-editor native', result.stderr)


if __name__ == '__main__':
    unittest.main()
