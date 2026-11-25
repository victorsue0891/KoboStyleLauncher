"""Synthetic fixtures only; no upstream agent or network."""
import importlib.util
import json
import hashlib
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('converter', Path(__file__).with_name('convert_skill_to_pack.py'))
converter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(converter)


class ConverterTests(unittest.TestCase):
    def test_cjk_and_astral_chunks_fit_android_bytes_and_hashes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'chapters').mkdir()
            (root / 'chapters' / '01.md').write_text('# Synthetic\n' + '\u5b57' * 9000 + '\U0001f408' * 9000, encoding='utf-8')
            output = root / 'pack.json'
            converter.convert(root, 'Synthetic', 'Fixture', output)
            data = json.loads(output.read_text(encoding='utf-8'))
            self.assertGreater(len(data['sections']), 1)
            for section in data['sections']:
                raw = section['content'].encode('utf-8')
                self.assertLessEqual(len(raw), 20000)
                self.assertEqual(hashlib.sha256(raw).hexdigest(), section['contentSha256'])
            with self.assertRaises(FileExistsError):
                converter.convert(root, 'Synthetic', 'Fixture', output)

    def test_missing_empty_and_oversized_inputs_refuse(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with self.assertRaises(ValueError):
                converter.convert(root, 'Synthetic', '', root / 'out.json')
            (root / 'chapters').mkdir()
            (root / 'chapters' / '01.md').write_text('x' * 1000001, encoding='utf-8')
            with self.assertRaises(ValueError):
                converter.convert(root, 'Synthetic', '', root / 'out.json')
            self.assertFalse((root / 'out.json').exists())


if __name__ == '__main__':
    unittest.main()
