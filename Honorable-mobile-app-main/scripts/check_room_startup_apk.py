#!/usr/bin/env python3
"""Require an executable Room constructor in DEX, not just a retained class name."""
import argparse
import hashlib
import re
import struct
import zipfile
import zlib

REQUIRED = b'Landroidx/work/impl/WorkDatabase_Impl;'


class Dex:
    def __init__(self, data):
        self.data = data
        if len(data) < 112 or data[:4] != b'dex\n' or data[7] != 0:
            raise ValueError('Invalid DEX header')
        if self.u32(32) != len(data) or self.u32(36) != 112 or self.u32(40) != 0x12345678:
            raise ValueError('Invalid DEX size or byte order')
        if hashlib.sha1(data[32:]).digest() != data[12:32] or zlib.adler32(data[12:]) & 0xffffffff != self.u32(8):
            raise ValueError('Invalid DEX checksum')
        self.strings = []
        for offset in self.table(56, 4):
            _, start = self.uleb(self.u32(offset))
            end = data.find(b'\0', start)
            if end < 0:
                raise ValueError('Unterminated DEX string')
            self.strings.append(data[start:end])
        self.types = [self.strings[self.u32(offset)] for offset in self.table(64, 4)]
        self.protos = list(self.table(72, 12))
        self.methods = list(self.table(88, 8))
        self.classes = list(self.table(96, 32))

    def u32(self, offset):
        if offset < 0 or offset + 4 > len(self.data):
            raise ValueError('DEX offset out of bounds')
        return struct.unpack_from('<I', self.data, offset)[0]

    def table(self, header, width):
        count, offset = self.u32(header), self.u32(header + 4)
        if count and (offset < 112 or offset + count * width > len(self.data)):
            raise ValueError('DEX table out of bounds')
        return range(offset, offset + count * width, width)

    def uleb(self, offset):
        value = 0
        for index in range(5):
            if offset >= len(self.data):
                raise ValueError('Truncated DEX class data')
            byte = self.data[offset]
            offset += 1
            if index == 4 and byte > 15:
                raise ValueError('Invalid DEX integer')
            value |= (byte & 127) << (index * 7)
            if byte < 128:
                return value, offset
        raise ValueError('Invalid DEX integer')

    def constructors(self, descriptor):
        found = []
        for entry in self.classes:
            class_index = self.u32(entry)
            if self.types[class_index] != descriptor:
                continue
            position = self.u32(entry + 24)
            valid = False
            if position:
                counts = []
                for _ in range(4):
                    count, position = self.uleb(position)
                    counts.append(count)
                for _ in range(counts[0] + counts[1]):
                    _, position = self.uleb(position)
                    _, position = self.uleb(position)
                method_index = 0
                for _ in range(counts[2]):
                    difference, position = self.uleb(position)
                    access, position = self.uleb(position)
                    code, position = self.uleb(position)
                    method_index += difference
                    method = self.methods[method_index]
                    owner, proto = struct.unpack_from('<HH', self.data, method)
                    name = self.strings[self.u32(method + 4)]
                    prototype = self.protos[proto]
                    returns = self.types[self.u32(prototype + 4)]
                    parameters = self.u32(prototype + 8)
                    no_args = parameters == 0 or self.u32(parameters) == 0
                    if owner == class_index and name == b'<init>' and returns == b'V' and no_args and access & 1 and access & 0x10000 and not access & (8 | 0x400) and code:
                        instructions = self.u32(code + 12)
                        if instructions and code + 16 + instructions * 2 <= len(self.data):
                            valid = True
            found.append(valid)
        return found


def verify(apk):
    found = []
    try:
        with zipfile.ZipFile(apk) as package:
            names = [name for name in package.namelist() if re.fullmatch(r'classes(?:[0-9]+)?\.dex', name)]
            if not names or len(names) != len(set(names)):
                raise ValueError('Missing or duplicate DEX entries')
            for name in names:
                if package.getinfo(name).file_size > 64 * 1024 * 1024:
                    raise ValueError('DEX exceeds inspection bound')
                found.extend(Dex(package.read(name)).constructors(REQUIRED))
    except (IndexError, struct.error) as error:
        raise ValueError('Malformed DEX structure') from error
    if len(found) != 1 or found != [True]:
        raise ValueError('Missing unique public executable WorkDatabase_Impl no-argument constructor')
    return {'workDatabaseConstructorVerified': True, 'deviceVerified': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    args = parser.parse_args()
    try:
        verify(args.apk)
    except (ValueError, OSError, zipfile.BadZipFile) as error:
        parser.exit(1, str(error) + '\n')
    print('Verified executable WorkDatabase_Impl default constructor; device startup remains a separate gate.')
