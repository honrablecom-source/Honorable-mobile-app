import hashlib
import struct
import tempfile
import unittest
import zipfile
import zlib
from pathlib import Path
from check_room_startup_apk import REQUIRED, verify


def uleb(value):
    result = bytearray()
    while True:
        byte = value & 127
        value >>= 7
        result.append(byte | (128 if value else 0))
        if not value:
            return result


def dex(*, constructor=True, public=True, code=True, argument=False, include_class=True):
    """Small encoded-table fixture; this is a parser fixture, not a VM assertion."""
    strings = [REQUIRED, b'V', b'<init>', b'Largument;']
    blob = bytearray(192)
    string_offsets = []
    for value in strings:
        string_offsets.append(len(blob))
        blob.extend(uleb(len(value)) + value + b'\0')
    while len(blob) % 4:
        blob.append(0)
    params = len(blob) if argument else 0
    if argument:
        blob.extend(struct.pack('<IH', 1, 2))
        while len(blob) % 4:
            blob.append(0)
    code_offset = len(blob)
    blob.extend(struct.pack('<HHHHIIH', 1, 1, 0, 0, 0, 1, 14))
    class_data = len(blob)
    blob.extend(bytes([0, 0, int(constructor), 0]))
    if constructor:
        blob.extend(uleb(0) + uleb(0x10000 | int(public)) + uleb(code_offset if code else 0))
    blob[:8] = b'dex\n035\0'
    for offset, value in [(32,len(blob)),(36,112),(40,0x12345678),
                          (56,4),(60,112),(64,3),(68,128),(72,1),(76,140),
                          (88,1),(92,152),(96,int(include_class)),(100,160),(104,len(blob)-192),(108,192)]:
        struct.pack_into('<I',blob,offset,value)
    for index, offset in enumerate(string_offsets):
        struct.pack_into('<I',blob,112 + index * 4,offset)
    struct.pack_into('<III',blob,128,0,1,3)
    struct.pack_into('<III',blob,140,1,1,params)
    struct.pack_into('<HHI',blob,152,0,0,2)
    struct.pack_into('<IIIIIIII',blob,160,0,1,0xffffffff,0,0xffffffff,0,class_data,0)
    blob[12:32] = hashlib.sha1(blob[32:]).digest()
    struct.pack_into('<I',blob,8,zlib.adler32(blob[12:]) & 0xffffffff)
    return blob


class RoomStartupGateTest(unittest.TestCase):
    def apk(self, entries):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        filename = Path(temp.name) / 'fixture.apk'
        with zipfile.ZipFile(filename,'w') as package:
            for name, value in entries:
                package.writestr(name,value)
        return filename

    def test_real_encoded_public_constructor_in_secondary_dex(self):
        result = verify(self.apk([('classes.dex',dex(include_class=False)),('classes2.dex',dex())]))
        self.assertTrue(result['workDatabaseConstructorVerified'])
        self.assertFalse(result['deviceVerified'])

    def test_retained_descriptor_and_method_reference_are_not_an_executable_constructor(self):
        with self.assertRaisesRegex(ValueError,'constructor'):
            verify(self.apk([('classes.dex',dex(constructor=False))]))

    def test_nonpublic_nonexecutable_and_argument_constructors_reject(self):
        for options in [{'public':False},{'code':False},{'argument':True},{'include_class':False}]:
            with self.subTest(options=options), self.assertRaisesRegex(ValueError,'constructor'):
                verify(self.apk([('classes.dex',dex(**options))]))

    def test_duplicate_class_rejects_even_when_both_have_constructors(self):
        with self.assertRaisesRegex(ValueError,'unique'):
            verify(self.apk([('classes.dex',dex()),('classes2.dex',dex())]))

    def test_corrupt_dex_rejects_before_scanning(self):
        data = dex()
        data[-1] ^= 1
        with self.assertRaisesRegex(ValueError,'checksum'):
            verify(self.apk([('classes.dex',data)]))

    def test_no_dex_or_malformed_header_rejects(self):
        for entries in [[],[('classes.dex',b'not dex')]]:
            with self.subTest(entries=len(entries)), self.assertRaises(ValueError):
                verify(self.apk(entries))


if __name__ == '__main__':
    unittest.main()
