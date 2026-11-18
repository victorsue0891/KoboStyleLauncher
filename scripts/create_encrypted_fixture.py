"""Optional fault fixture; requires pypdf 6.1.1 in the project-local test vendor dir."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path('.tools/acceptance/vendor').resolve()))
from pypdf import PdfReader, PdfWriter

root = Path('.tools/acceptance/fixtures/KoboStyleAcceptance-20261115')
reader = PdfReader(root / 'valid' / 'Acceptance-PDF-01.pdf')
writer = PdfWriter()
for page in reader.pages:
    writer.add_page(page)
writer.encrypt('kobostyle-test-only', algorithm='RC4-128')
with (root / 'invalid' / 'Encrypted.pdf').open('wb') as output:
    writer.write(output)
print('Created password-protected synthetic PDF; password: kobostyle-test-only')
