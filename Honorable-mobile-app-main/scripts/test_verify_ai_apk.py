#!/usr/bin/env python3
"""Independent synthetic archive tests for packaged AI identity enforcement."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import struct
import unittest
import warnings
import zipfile

spec = importlib.util.spec_from_file_location('verify_ai_apk', Path(__file__).with_name('verify-ai-apk.py'))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class PackagedAiTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.apk = Path(self.folder.name) / 'fixture.apk'
        self.name = 'assets/inpaint/test-model.onnx'
        self.data = b'generated-test-weights'
        self.expected = {self.name: (len(self.data), hashlib.sha256(self.data).hexdigest())}

    def archive(self, entries):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(self.apk, 'w') as package:
                for name, data in entries:
                    package.writestr(name, data)

    def test_exact_bytes_pass_without_a_device_claim(self):
        self.archive([(self.name, self.data), ('unrelated', b'other')])
        self.assertIsNone(verifier.verify_ai_apk(self.apk, self.expected))

    def test_missing_duplicate_and_empty_requirements_refuse(self):
        for entries in [[], [(self.name, self.data), (self.name, self.data)]]:
            self.archive(entries)
            with self.assertRaisesRegex(ValueError, 'Missing or duplicate'):
                verifier.verify_ai_apk(self.apk, self.expected)
        with self.assertRaisesRegex(ValueError, 'No required'):
            verifier.verify_ai_apk(self.apk, {})

    def test_lfs_pointer_or_changed_size_refuses(self):
        self.archive([(self.name, b'version https://git-lfs.github.com/spec/v1')])
        with self.assertRaisesRegex(ValueError, 'size mismatch'):
            verifier.verify_ai_apk(self.apk, self.expected)

    def test_same_size_mutation_refuses(self):
        self.archive([(self.name, b'x' * len(self.data))])
        with self.assertRaisesRegex(ValueError, 'digest mismatch'):
            verifier.verify_ai_apk(self.apk, self.expected)

    def test_real_elf_load_alignment_checks_more_than_zip_padding(self):
        binary = bytearray(120); binary[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<Q', binary, 32, 64)
        struct.pack_into('<HH', binary, 54, 56, 1)
        struct.pack_into('<I', binary, 64, 1)
        struct.pack_into('<Q', binary, 112, 16384)
        name = 'lib/arm64-v8a/generated.so'
        self.archive([(self.name, self.data), (name, binary)])
        verifier.verify_ai_apk(self.apk, self.expected)
        struct.pack_into('<Q', binary, 112, 4096)
        self.archive([(self.name, self.data), (name, binary)])
        with self.assertRaisesRegex(ValueError, 'Native ELF16KB'):
            verifier.verify_ai_apk(self.apk, self.expected)

    def test_malformed_native_header_and_duplicate_entries_refuse(self):
        name = 'lib/x86_64/generated.so'
        for entries in [[(self.name, self.data), (name, b'not ELF')], [(self.name, self.data), ('extra', b'a'), ('extra', b'a')]]:
            self.archive(entries)
            with self.assertRaises(ValueError):
                verifier.verify_ai_apk(self.apk, self.expected)


if __name__ == '__main__':
    unittest.main()
