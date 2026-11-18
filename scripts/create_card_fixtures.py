"""Prepare only synthetic card fixtures; --push requires one mounted card and a new folder."""
import argparse
import subprocess
from pathlib import Path
from create_acceptance_fixtures import epub, pdf

parser = argparse.ArgumentParser()
parser.add_argument('--push', action='store_true')
args = parser.parse_args()
folder = 'KoboStyleCardTest-20261115'
local = Path('.tools/acceptance') / folder
local.mkdir(parents=True, exist_ok=True)
epub(local / 'Card-Test.epub', 101)
pdf(local / 'Card-Test.pdf', 101)
if args.push:
    listing = subprocess.run(['adb', 'devices'], capture_output=True, text=True, check=True).stdout
    devices = [line for line in listing.splitlines() if '\t' in line]
    if len(devices) != 1 or not devices[0].endswith('\tdevice'):
        raise SystemExit('Exactly one authorized device is required; identifiers withheld.')
    volumes = subprocess.run(['adb', 'shell', 'sm', 'list-volumes', 'public'], capture_output=True, text=True, check=True).stdout
    mounted = [line.split()[-1] for line in volumes.splitlines() if ' mounted ' in line]
    if len(mounted) != 1:
        raise SystemExit('Exactly one mounted card is required; do not guess the destination.')
    import re
    if not re.fullmatch(r'[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}', mounted[0]):
        raise SystemExit('Unrecognized card identifier format; inspect manually.')
    destination = '/storage/' + mounted[0] + '/' + folder
    exists = subprocess.run(['adb', 'shell', 'test', '-e', destination], capture_output=True)
    if exists.returncode != 1:
        raise SystemExit('Destination exists or cannot be checked; no overwrite allowed.')
    result = subprocess.run(['adb', 'push', str(local), '/storage/' + mounted[0] + '/'], capture_output=True)
    if result.returncode:
        raise SystemExit('Fixture transfer failed; raw device paths withheld.')
    print('Copied 2 synthetic books into the dedicated card folder; card identifier withheld.')
else:
    print('Created 2 local synthetic books. Pass --push only when physical testing is authorized.')
