"""Create an isolated synthetic scale set: 40 pairs -> 100 normal books in total;
490 pairs -> 1000 normal books. The base 20 and three fault files are separate.
"""
import argparse
import hashlib
import json
from pathlib import Path
from create_acceptance_fixtures import epub, pdf

parser = argparse.ArgumentParser()
parser.add_argument('--pairs', type=int, choices=(40, 490), required=True)
parser.add_argument('--output', type=Path, default=Path('.tools/acceptance/scale-acceptance-20261115'))
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
for number in range(11, 11 + args.pairs):
    for extension, create in (('epub', epub), ('pdf', pdf)):
        path = args.output / f'Scale-{extension.upper()}-{number:04d}.{extension}'
        if not path.exists():
            create(path, number)
manifest = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in args.output.iterdir() if p.is_file()}
(args.output.parent / 'scale-manifest.json').write_text(json.dumps(manifest), encoding='utf-8')
print(f'Prepared {len(manifest)} synthetic scale files')
