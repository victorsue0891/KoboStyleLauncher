"""Remove only our verified synthetic scale files; never recursively delete a device folder."""
import hashlib
import json
import re
import subprocess
from pathlib import Path

remote = '/sdcard/Download/KoboStyleAcceptance-20261115/valid/scale-acceptance-20261115'
manifest = json.loads(Path('.tools/acceptance/scale-manifest.json').read_text(encoding='utf-8'))
assert manifest and all(re.fullmatch(r'Scale-(EPUB|PDF)-\d{4}\.(epub|pdf)', name) for name in manifest)
lines = subprocess.check_output(['adb', 'shell', 'sha256sum', remote + '/*'], text=True).splitlines()
observed = {}
for line in lines:
    digest, path = line.split(maxsplit=1)
    path = path.lstrip('*')
    assert path.startswith(remote + '/') and '/' not in path[len(remote) + 1:]
    observed[path[len(remote) + 1:]] = digest
assert observed == manifest, 'Device scale files changed or contain unexpected files; nothing deleted.'
names = sorted(manifest)
for start in range(0, len(names), 40):
    subprocess.run(['adb', 'shell', 'rm', '--'] + [remote + '/' + name for name in names[start:start + 40]], check=True)
subprocess.run(['adb', 'shell', 'rmdir', remote], check=True)
print(f'Removed {len(names)} verified synthetic files and their now-empty scale directory.')
