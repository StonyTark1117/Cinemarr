#!/usr/bin/env python3
import io
import unittest
import zipfile
from native_payloads import inspect_archive


def jar(entries):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        for name, content in entries.items():
            archive.writestr(name, content)
    return buffer.getvalue()


class NativePayloadTests(unittest.TestCase):
    def test_java_and_metadata_pass(self):
        payload = jar({'Example.class': bytes.fromhex('cafebabe000000340002') + b'class-data',
                       'settings.properties': b'mode=local-only', 'ffmpeg/': b''})
        self.assertEqual([], inspect_archive(io.BytesIO(payload), 'mod.jar'))

    def test_renamed_nested_archive_and_binary_detected(self):
        payload = jar({'data.blob': jar({'renamed.dat': b'\x7fELF' + b'\0' * 20})})
        self.assertEqual(['mod.jar!/data.blob!/renamed.dat'], inspect_archive(io.BytesIO(payload), 'mod.jar'))

    def test_native_signatures_and_versioned_extensions(self):
        for name, data in [('renamed', b'MZbinary'), ('renamed', bytes.fromhex('cffaedfe')),
                           ('lib.so.62', b'anything'), ('library.lib', b'anything'),
                           ('ffprobe', b'anything'), ('blob', b'!<arch>\n'),
                           ('renamed', bytes.fromhex('cafebabe0000000200000007'))]:
            with self.subTest(name=name, data=data):
                self.assertTrue(inspect_archive(io.BytesIO(jar({name: data}))))

    def test_corrupt_nested_archive_fails_closed(self):
        with self.assertRaises(zipfile.BadZipFile):
            inspect_archive(io.BytesIO(jar({'dependency.jar': b'invalid'})))


if __name__ == '__main__':
    unittest.main()
