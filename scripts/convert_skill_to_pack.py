#!/usr/bin/env python3
"""Offline adapter: Book-to-Skill's generated chapters/*.md -> KnowledgePack v1 JSON.

This is a deliberately conservative interoperability helper, not upstream Book-to-Skill.
It does NOT call any APIs or create the upstream skill. It does NOT upload files.
Only run on content you own/are entitled to process. Check resulting summaries manually.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path
from datetime import datetime, timezone

MAX_CHAPTER_FILES = 200
MAX_SECTION_CHARS = 6000  # <= 18 KB for BMP CJK; also bound UTF-8 bytes below.
MAX_SECTIONS = 500
MAX_SOURCE_FILE_BYTES = 1_000_000
MAX_PACK_BYTES = 10_000_000


def chunk_text(text, max_length=MAX_SECTION_CHARS):
    """Bound memory and pack schema; prefer paragraph boundaries where possible."""
    text = text.strip()
    while text:
        end = min(len(text), max_length)
        while len(text[:end].encode('utf-8')) > 20000:
            end -= 1
        if len(text) <= end:
            yield text
            break
        pos = text.rfind('\n\n', 0, end)
        if pos < max_length // 3:
            pos = end
        yield text[:pos].strip()
        text = text[pos:].strip()


def convert(skill_dir, title, author, output, fingerprint=None):
    skill_dir = Path(skill_dir).resolve()
    chapters = skill_dir / 'chapters'
    if chapters.is_symlink() or (hasattr(chapters, 'is_junction') and chapters.is_junction()):
        raise ValueError('Linked chapters directories are not accepted')
    if not chapters.is_dir():
        raise ValueError('No chapters/ directory: supply a generated Book-to-Skill skill folder')
    inputs = sorted(p for p in chapters.glob('*.md') if p.is_file() and not p.is_symlink())
    if not 1 <= len(inputs) <= MAX_CHAPTER_FILES:
        raise ValueError(f'Expected 1..{MAX_CHAPTER_FILES} chapter Markdown files; got {len(inputs)}')
    sections=[]
    for chapter_number, p in enumerate(inputs, 1):
        if not p.resolve().is_relative_to(skill_dir):
            raise ValueError('Chapter outside the selected skill directory')
        if p.stat().st_size > MAX_SOURCE_FILE_BYTES:
            raise ValueError(f'Source file too big: {p.name}')
        raw = p.read_text(encoding='utf-8-sig')
        # Keep the generated knowledge as data, never execute markdown links/code.
        match = re.search(r'^#{1,3}\s+(.+)$', raw, flags=re.M)
        chapter_title = match.group(1).strip()[:300] if match else p.stem[:300]
        for chunk_number, chunk in enumerate(chunk_text(raw), 1):
            if not chunk: continue
            sid=f'c{chapter_number:03d}s{chunk_number:03d}'
            sections.append({
                'sectionId':sid, 'chapterOrder':chapter_number,
                'title':chapter_title, 'content':chunk,
                'sourceLocator':f'chapters/{p.name}#{chunk_number}',
                'contentSha256':hashlib.sha256(chunk.encode('utf-8')).hexdigest()
            })
            if len(sections) > MAX_SECTIONS:
                raise ValueError(f'Too many sections; max {MAX_SECTIONS}')
    if not sections: raise ValueError('Empty generated chapters')
    canonical='\n'.join(s['contentSha256'] for s in sections)
    pack_id='bts-'+hashlib.sha256(canonical.encode()).hexdigest()[:20]
    payload={
        'schemaVersion':1, 'packId':pack_id,
        'source':{'type':'book-to-skill','toolVersion':'unverified-local-upstream',
                  'generatedAtUtc':datetime.now(timezone.utc).isoformat(timespec='seconds')},
        'bookMatch':{'title':title[:400], 'author':author[:200], 'fingerprint':fingerprint},
        'sections':sections
    }
    data=(json.dumps(payload,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    if len(data)>MAX_PACK_BYTES: raise ValueError('KnowledgePack too big; max 10 MB')
    output=Path(output)
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open('xb') as stream:
        stream.write(data)
    print(f'Wrote {len(sections)} sections to {output}, {len(data)} bytes; no API calls')


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--skill-dir',required=True)
    ap.add_argument('--title',required=True)
    ap.add_argument('--author',default='')
    ap.add_argument('--fingerprint',default=None)
    ap.add_argument('--output',required=True)
    args=ap.parse_args()
    convert(args.skill_dir,args.title,args.author,args.output,args.fingerprint)

if __name__=='__main__': main()
