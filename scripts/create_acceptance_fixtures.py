"""Create synthetic, non-private EPUB/PDF acceptance books using only stdlib."""
import argparse
import json
import struct
import zlib
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile


def png():
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    pixels = b''.join(b'\0' + bytes(([50, 90, 180] if y < 90 else [220, 130, 30]) * 120) for y in range(180))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 120, 180, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b'')


def epub(path, number):
    title = f'驗收 EPUB {number:02d}：繁體中文長書名與封面測試'
    with ZipFile(path, 'w', ZIP_DEFLATED) as book:
        book.writestr('mimetype', 'application/epub+zip', compress_type=ZIP_STORED)
        book.writestr('META-INF/container.xml', '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
        book.writestr('OPS/book.opf', f'''<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:kobostyle:test:{number}</dc:identifier><dc:title>{title}</dc:title><dc:creator>合成測試作者</dc:creator><dc:language>zh-TW</dc:language><meta property="dcterms:modified">2026-11-15T00:00:00Z</meta></metadata><manifest><item id="page" href="page.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/></manifest><spine><itemref idref="page"/></spine></package>''')
        book.writestr('OPS/nav.xhtml', '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>目錄</title></head><body><nav epub:type="toc"><ol><li><a href="page.xhtml">驗收內容</a></li></ol></nav></body></html>')
        paragraphs = ''.join(f'<p>測試段落 {i}：這是公開合成的驗收文字。請確認能夠翻頁，然後使用系統返回鍵回到書房。</p>' for i in range(1, 41))
        book.writestr('OPS/page.xhtml', f'<html xmlns="http://www.w3.org/1999/xhtml"><head><title>{title}</title></head><body><h1>KoboStyle EPUB {number:02d}</h1><p>SAF CONTENT URI ACCEPTANCE — NO PRIVATE CONTENT</p>{paragraphs}</body></html>')
        book.writestr('OPS/cover.png', png())


def pdf(path, number):
    objects = [b'<< /Type /Catalog /Pages 2 0 R >>', b'<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>']
    for stream in (6, 7):
        objects.append(f'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 420 595] /Resources << /Font << /F1 5 0 R >> >> /Contents {stream} 0 R >>'.encode())
    objects.append(b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>')
    for page in (1, 2):
        content = f'BT /F1 22 Tf 36 530 Td (KoboStyle PDF {number:02d} PAGE {page}) Tj 0 -45 Td /F1 14 Tf (SAF CONTENT URI ACCEPTANCE) Tj 0 -30 Td (Synthetic book - no private content) Tj ET'.encode()
        objects.append(f'<< /Length {len(content)} >>\nstream\n'.encode() + content + b'\nendstream')
    data = bytearray(b'%PDF-1.4\n')
    offsets = [0]
    for i, obj in enumerate(objects, 1):
        offsets.append(len(data)); data.extend(f'{i} 0 obj\n'.encode() + obj + b'\nendobj\n')
    start = len(data)
    data.extend(f'xref\n0 {len(offsets)}\n0000000000 65535 f \n'.encode())
    for offset in offsets[1:]:
        data.extend(f'{offset:010d} 00000 n \n'.encode())
    data.extend(f'trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{start}\n%%EOF\n'.encode())
    path.write_bytes(data)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=Path('.tools/acceptance/fixtures/KoboStyleAcceptance-20261115'))
    args = parser.parse_args()
    for name in ('valid', 'invalid'):
        (args.output / name).mkdir(parents=True, exist_ok=True)
    for number in range(1, 11):
        epub(args.output / 'valid' / f'Acceptance-EPUB-{number:02d}.epub', number)
        pdf(args.output / 'valid' / f'Acceptance-PDF-{number:02d}.pdf', number)
    (args.output / 'invalid' / 'Corrupt.epub').write_bytes(b'not a zip')
    (args.output / 'invalid' / 'Corrupt.pdf').write_bytes(b'%PDF-1.4\nbroken')
    print(json.dumps({'valid': 20, 'invalid': 2, 'output': str(args.output)}))
