import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
import zlib

import protocol as p
import pack_theme as theme

ROOT = Path(__file__).resolve().parents[1]


def png():
    """Original 1x1 RGBA fixture, not extracted theme artwork."""
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind+data))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(b'\0\x12\x34\x56\xff')) + chunk(b'IEND', b'')


class ProtocolTests(unittest.TestCase):
    def test_known_device_info(self):
        self.assertEqual(p.sequence(p.command(5, 1)).hex(), '5aff14000000000000a55a0501000000000000fb')

    def test_ack_has_no_checksum(self):
        self.assertEqual(p.sequence(control=64, packet=1, ack=128).hex(), '5aff09004001800000')

    def test_checksum_and_length_rejected(self):
        frame = bytearray(p.sequence(p.command(5, 1)))
        frame[-1] ^= 1
        with self.assertRaises(ValueError): p.decode_sequence(frame)
        with self.assertRaises(ValueError): p.decode_sequence(frame[:-1])

    def test_padding_is_only_for_crc(self):
        source = b'abc'
        self.assertEqual(p.padded_crc32(source), zlib.crc32(b'abc\0'))
        self.assertEqual(p.file_data(1, 2, source), b'\x01\0\x02\0\x01\0abc')

    def test_gallery_slot_is_hashed(self):
        jpeg = b'\xff\xd8\xff\xd9'  # marker-only codec fixture, not a displayable photo
        first, name0 = p.gallery_payload(jpeg, 0)
        last, name5 = p.gallery_payload(jpeg, 5)
        self.assertEqual(last[-1], 5)
        self.assertNotEqual(name0, name5)
        self.assertEqual(name0, hashlib.md5(first).hexdigest()+'.jpg')

    def test_published_vectors(self):
        vectors = json.loads((ROOT/'protocol/test-vectors/stock.json').read_text())['vectors']
        for v in vectors:
            with self.subTest(v=v['name']):
                data = bytes.fromhex(v['payload'])
                inner = p.command(v['command'], v['attribute'], data)
                self.assertEqual(inner.hex(), v['inner'])
                outer = p.sequence(inner, v['control'], v['packet'], v['ack'], v['session'])
                self.assertEqual(outer.hex(), v['outer'])
                self.assertEqual(p.decode_sequence(outer)['payload'], v['inner'])
                self.assertEqual(f'{p.padded_crc32(data):08x}', v['padded_crc32'])


class ThemeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.assets = self.root/'assets'; self.assets.mkdir()
        (self.assets/'digit.png').write_bytes(png())
        self.cfg = {'widgets':[{'name':'ClockDigitWidget', 'format':'HH:mm',
                               'digitImages':['digit.png']*10}]}

    def tearDown(self): self.tmp.cleanup()

    def test_pack_hash_references_and_determinism(self):
        a,b = self.root/'a.zip', self.root/'b.zip'
        content = theme.pack(self.cfg, self.assets, a)
        theme.pack(self.cfg, self.assets, b)
        self.assertEqual(a.read_bytes(), b.read_bytes())
        self.assertEqual(theme.validate(a)['location'], 0x200)
        with zipfile.ZipFile(a) as z:
            cfg = json.loads(z.read(content+'.cfg'))
            self.assertEqual(cfg['configuration']['widgets'][0]['digitImages'][0], cfg['files'][0])

    def test_missing_reference_and_background_only_rejected(self):
        with self.assertRaises(ValueError): theme.pack({'widgets':[{'name':'BackgroundWidget'}]}, self.assets, self.root/'x.zip')
        self.cfg['widgets'][0]['digitImages']=['missing.png']
        with self.assertRaises(ValueError): theme.pack(self.cfg,self.assets,self.root/'x.zip')

    def test_tampered_archive_rejected(self):
        a=self.root/'a.zip'; theme.pack(self.cfg,self.assets,a)
        b=self.root/'bad.zip'
        with zipfile.ZipFile(a) as original, zipfile.ZipFile(b,'w') as damaged:
            for name in original.namelist():
                data=original.read(name)
                damaged.writestr(name,data+b'!' if name.endswith('.png') else data)
        with self.assertRaises(ValueError): theme.validate(b)


if __name__ == '__main__': unittest.main()
