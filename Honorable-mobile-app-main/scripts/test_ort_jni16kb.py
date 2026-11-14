#!/usr/bin/env python3
"""Independent archive/class/ELF contract tests for the exact-runtime JNI backport."""
import importlib.util
import json
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest
import warnings
import zipfile

spec = importlib.util.spec_from_file_location('ort_jni', Path(__file__).with_name('ort-jni16kb.py'))
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


class JniContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def archive(self, name, entries):
        file = self.root / name
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(file, 'w') as out:
                for key, data in entries:
                    out.writestr(key, data)
        return file

    def test_declared_jni_delta_keeps_actual_core_classes_and_other_payload_exact(self):
        entries = [('classes.jar', b'published-api'), ('jni/core.so', b'original-engine'), ('jni/glue.so', b'old-glue')]
        before = self.archive('before.zip', entries)
        after = self.archive('after.zip', entries[:2] + [('jni/glue.so', b'new-glue')])
        helper.verify_delta(before, after, {'jni/glue.so'})
        changed_core = self.archive('bad.zip', [('classes.jar', b'published-api'), ('jni/core.so', b'changed-engine'), ('jni/glue.so', b'new-glue')])
        with self.assertRaisesRegex(ValueError, 'Unchanged archive bytes'):
            helper.verify_delta(before, changed_core, {'jni/glue.so'})

    def test_only_declared_notices_can_be_added_without_changing_original_bytes(self):
        before = self.archive('before.zip', [('core', b'original')])
        after = self.archive('after.zip', [('core', b'original'), ('LICENSE', b'MIT')])
        helper.verify_delta(before, after, set(), added={'LICENSE'})
        with self.assertRaises(ValueError):
            helper.verify_delta(before, after, set())
        changed = self.archive('changed.zip', [('core', b'changed'), ('LICENSE', b'MIT')])
        with self.assertRaisesRegex(ValueError, 'Unchanged archive bytes'):
            helper.verify_delta(before, changed, set(), added={'LICENSE'})

    def test_duplicates_undeclared_addition_and_removal_fail(self):
        before = self.archive('before.zip', [('a', b'one'), ('b', b'two')])
        for index, entries in enumerate([[('a', b'one')], [('a', b'one'), ('b', b'two'), ('c', b'three')], [('a', b'one'), ('a', b'one')]]):
            with self.assertRaises(ValueError):
                helper.verify_delta(before, self.archive(str(index)+'.zip', entries), set())
        after = self.archive('filtered.zip', [('a', b'one')])
        helper.verify_delta(before, after, set(), {'b'})

    def test_elf_requires_real_load_alignment_and_offset_congruence(self):
        data = bytearray(120)
        data[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<Q', data, 32, 64)
        struct.pack_into('<HH', data, 54, 56, 1)
        struct.pack_into('<HH', data, 58, 64, 0)
        struct.pack_into('<I', data, 64, 1)
        struct.pack_into('<Q', data, 64+48, 16384)
        self.assertTrue(helper.elf_info(data)['aligned16KB'])
        struct.pack_into('<Q', data, 64+48, 4096)
        self.assertFalse(helper.elf_info(data)['aligned16KB'])
        struct.pack_into('<Q', data, 64+48, 16384)
        struct.pack_into('<Q', data, 64+8, 4096)
        self.assertFalse(helper.elf_info(data)['aligned16KB'])
        with self.assertRaises(ValueError):
            helper.elf_info(data[:80])

    def test_actual_java_compiler_native_descriptors_preserve_static_and_instance_abi(self):
        javac = shutil.which('javac')
        self.assertIsNotNone(javac, 'JNI build requires a Java compiler')
        source = self.root / 'NativeFixture.java'
        source.write_text('public class NativeFixture { public static native long first(int x); private native byte[] second(long x); public void ordinary() {} }')
        subprocess.run([javac, '-d', str(self.root), str(source)], check=True, capture_output=True)
        methods = helper.native_methods((self.root/'NativeFixture.class').read_bytes())
        self.assertEqual({('NativeFixture', 'first', '(I)J'): True, ('NativeFixture', 'second', '(J)[B'): False}, methods)
        with self.assertRaises(ValueError):
            helper.native_methods(b'not a class')

    def test_exact_vendor_source_pins_refuse_tampering_and_unlisted_files(self):
        vendor = self.root/'vendor'
        shutil.copytree(helper.VENDOR, vendor)
        helper.validate_vendor(vendor)
        (vendor/'unexpected.c').write_text('unlisted')
        with self.assertRaisesRegex(ValueError, 'Unexpected or missing'):
            helper.validate_vendor(vendor)
        (vendor/'unexpected.c').unlink()
        metadata = json.loads((vendor/'provenance.json').read_text())
        (vendor/next(iter(metadata['files']))).write_text('modified')
        with self.assertRaisesRegex(ValueError, 'source pin failed'):
            helper.validate_vendor(vendor)

    def test_modified_runtime_aar_refuses_before_building_or_publishing(self):
        bad = self.root/'bad.aar'; bad.write_bytes(b'wrong runtime')
        output = self.root/'out.aar'
        with self.assertRaisesRegex(ValueError, 'integrity check'):
            helper.build_aar(bad, self.root, output)
        self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
